/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.cli;

import ch.qos.logback.classic.Level;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.core.annotation.ReflectiveAccess;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Command(name = "jsolex", description = "Sol'Ex spectroheliograph video processing",
    mixinStandardHelpOptions = true)
@ReflectiveAccess
public class ScriptingEntryPoint implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptingEntryPoint.class);

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    public boolean verbose;

    @Option(names = {"-p", "--param"}, description = "Parameter to pass to the script")
    public Map<String, Object> params;

    @Option(names = {"-s", "--script"}, description = "Path to the script to execute", required = true)
    public File scriptPath;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    public File outputDir;

    @Option(names = {"-f", "--format"}, description = "Image formats (jpg, png, fits, tif)")
    public List<String> formats = List.of(ImageFormat.FITS.name(), ImageFormat.JPG.name(), ImageFormat.PNG.name(), ImageFormat.TIF.name());

    @Option(names = {"-d", "--debug"}, description = "Debug mode", defaultValue = "false")
    public boolean debug;
    
    private ProgressListener progressListener;

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public static void main(String[] args) {
        PicocliRunner.run(ScriptingEntryPoint.class, args);
    }

    public void run() {
        if (verbose) {
            logger(LoggingListener.class.getName()).setLevel(Level.DEBUG);
        } else {
            logger("me.champeau.a4j.jsolex.processing").setLevel(Level.ERROR);
            logger("me.champeau.a4j.math").setLevel(Level.ERROR);
        }
        var processParams = ProcessParams.loadDefaults();
        var formats = this.formats.stream().map(String::toUpperCase).map(ImageFormat::valueOf).collect(Collectors.toSet());
        processParams = processParams.withExtraParams(processParams.extraParams().withImageFormats(formats));
        if (debug) {
            var newImages = new HashSet<>(processParams.requestedImages().images());
            newImages.add(GeneratedImageKind.DEBUG);
            processParams = processParams.withRequestedImages(processParams.requestedImages().withImages(newImages));
        } else {
            var newImages = new HashSet<>(processParams.requestedImages().images());
            newImages.remove(GeneratedImageKind.DEBUG);
            processParams = processParams.withRequestedImages(processParams.requestedImages().withImages(newImages));
        }
        var saver = new ImageSaver(CutoffStretchingStrategy.DEFAULT, processParams);
        var outputDirectory = outputDir == null ? new File(".") : outputDir;
        var scriptExecutor = new DefaultImageScriptExecutor(
            d -> {
                throw new IllegalStateException("image not available in standalone mode");
            },
            Map.of(ProcessParams.class, processParams),
            createBroadcaster(saver, outputDirectory)
        );
        try {
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    scriptExecutor.putVariable(entry.getKey(), entry.getValue());
                }
            }
            var sourceScriptPath = scriptPath.toPath();
            if (!Files.exists(sourceScriptPath)) {
                System.err.println("Script " + sourceScriptPath + " not found");
                System.exit(-1);
            }
            var result = scriptExecutor.execute(sourceScriptPath, ImageMathScriptExecutor.SectionKind.SINGLE);
            result.imagesByLabel().forEach((name, image) -> {
                var saved = saver.save(image, new File(outputDirectory, name + ".png"));
                saved.forEach(f -> System.out.println("Saved " + f));
            });
            result.filesByLabel().forEach((name, path) -> {
                try {
                    var target = outputDirectory.toPath().resolve(path.toFile().getName());
                    Files.move(path, target);
                    System.out.println("Saved " + target);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            result.invalidExpressions().forEach(error -> {
                System.err.println("Error when evaluating '" + error.expression() + "' : " + error.error().getMessage());
                error.error().printStackTrace();
                propapateErrorIfNeeded(error.error());
            });
        } catch (Exception e) {
            LOGGER.error("Unable to execute script", e);
            propapateErrorIfNeeded(e);
        }
    }

    private void propapateErrorIfNeeded(Exception error) {
        if (progressListener != null) {
            var writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            progressListener.onError(writer.toString());
        }
    }

    private Broadcaster createBroadcaster(ImageSaver saver, File outputDirectory) {
        return new Broadcaster() {
            final Map<String, Integer> lastProgress = new ConcurrentHashMap<>();

            @Override
            public void broadcast(ProcessingEvent<?> event) {
                if (event instanceof ProgressEvent e) {
                    logProgress(e);
                } else if (event instanceof ImageGeneratedEvent e) {
                    writeImage(e.getPayload());
                }
            }

            private void writeImage(GeneratedImage payload) {
                saver.save(payload.image(), new File(outputDirectory, payload.title() + ".png"));
            }

            private void logProgress(ProgressEvent e) {
                var payload = e.getPayload();
                var progress = (int) (100 * payload.progress());
                var task = payload.task();
                if (progress % 10 == 0) {
                    var last = lastProgress.getOrDefault(task, -1);
                    if (!last.equals(progress)) {
                        lastProgress.put(task, progress);
                        System.out.println(task + " " + progress + "%");
                        if (progressListener != null) {
                            progressListener.onProgress(task, progress);
                        }
                    }
                }
                if (progress == 100) {
                    lastProgress.remove(task);
                }
            }

        };
    }

    public interface ProgressListener {
        void onProgress(String task, double progressPercent);

        void onError(String error);
    }
    
    private static ch.qos.logback.classic.Logger logger(String name) {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
    }
}
