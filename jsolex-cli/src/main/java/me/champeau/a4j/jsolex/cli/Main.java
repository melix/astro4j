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
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;


@Command(name = "jsolex", description = "Sol'Ex spectroheliograph video processing",
        mixinStandardHelpOptions = true)
@ReflectiveAccess
public class Main implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;

    @Option(names = {"-i", "--input" }, description = "Input SER file", required = true)
    File inputFile;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    File outputDir;

    @Option(names = {"-q", "--quick-mode"}, description = "Quick mode")
    boolean quickMode;

    public static void main(String[] args)  {
        PicocliRunner.run(Main.class, args);
    }

    public void run() {
        if (verbose) {
            logger(LoggingListener.class.getName()).setLevel(Level.DEBUG);
        } else {
            logger("me.champeau.a4j.jsolex.processing").setLevel(Level.ERROR);
            logger("me.champeau.a4j.math").setLevel(Level.ERROR);
        }
        var processParams = ProcessParams.loadDefaults();
        if (outputDir == null) {
            var baseName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf("."));
            var outputDirName = baseName;
            outputDir = new File(inputFile.getParentFile(), outputDirName);
            int i = 0;
            while (Files.exists(outputDir.toPath())) {
                String suffix = String.format("-%04d", i++);
                outputDir = new File(inputFile.getParentFile(), outputDirName + suffix);
            }
        }
        var processor = new SolexVideoProcessor(
                inputFile,
                outputDir,
                processParams,
                quickMode
        );
        processor.addEventListener(new LoggingListener(processParams));
        processor.process();
    }

    private static ch.qos.logback.classic.Logger logger(String name) {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
    }

}
