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
import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Command(name = "jsolex", description = "Sol'Ex spectroheliograph video processing",
    mixinStandardHelpOptions = true)
@ReflectiveAccess
public class ScriptingEntryPoint implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptingEntryPoint.class);

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;

    @Option(names = {"-p", "--param"}, description = "Parameter to pass to the script")
    Map<String, Object> params;

    @Option(names = {"-s", "--script"}, description = "Path to the script to execute", required = true)
    File scriptPath;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    File outputDir;

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
        var scriptExecutor = new DefaultImageScriptExecutor(
            d -> {
                throw new IllegalStateException("image not available in standalone mode");
            },
            Map.of(),
            createBroadcaster()
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
            var processParams = ProcessParams.loadDefaults();
            processParams = processParams.withExtraParams(processParams.extraParams().withImageFormats(Set.of(ImageFormat.FITS, ImageFormat.JPG, ImageFormat.PNG, ImageFormat.TIF)));
            var saver = new ImageSaver(LinearStrechingStrategy.DEFAULT, processParams);
            var directory = outputDir == null ? new File(".") : outputDir;
            result.imagesByLabel().forEach((name, image) -> {
                var saved = saver.save(image, new File(directory, name + ".png"));
                saved.forEach(f -> System.out.println("Saved " + f));
            });
            result.filesByLabel().forEach((name, path) -> {
                try {
                    var target = directory.toPath().resolve(path.toFile().getName());
                    Files.move(path, target);
                    System.out.println("Saved " + target);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            result.invalidExpressions().forEach(error -> {
                System.err.println("Error when evaluating '" + error.expression() + "' : " + error.error().getMessage());
                error.error().printStackTrace();
            });
            if (!result.invalidExpressions().isEmpty()) {
                System.exit(-1);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to execute script", e);
        }
    }

    private static Broadcaster createBroadcaster() {
        return new Broadcaster() {
            final Map<String, Integer> lastProgress = new ConcurrentHashMap<>();

            @Override
            public void broadcast(ProcessingEvent<?> event) {
                if (event instanceof ProgressEvent e) {
                    var payload = e.getPayload();
                    var progress = (int) (100 * payload.progress());
                    var task = payload.task();
                    if (progress % 10 == 0) {
                        var last = lastProgress.getOrDefault(task, -1);
                        if (!last.equals(progress)) {
                            lastProgress.put(task, progress);
                            System.out.println(task + " " + progress + "%");
                        }
                    }
                    if (progress == 100) {
                        lastProgress.remove(task);
                    }
                }
            }
        };
    }

    private static ch.qos.logback.classic.Logger logger(String name) {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
    }
}
