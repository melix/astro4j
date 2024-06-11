/*
 * Copyright 2023-2024 the original author or authors.
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
package me.champeau.astro4j;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Converts the BASS2000 spectral line database file to a format
 * that can be used by the application without consuming too much memory.
 */
@CacheableTask
public abstract class SpectrumFileConverter extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void convert() throws IOException {
        var inputFile = getInputFile().get().getAsFile().toPath();
        var outputFile = getOutputFile().get().getAsFile().toPath();
        try (var writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            try (var reader = Files.newBufferedReader(inputFile)) {
                // skip first 8 lines
                for (int i = 0; i < 8; i++) {
                    reader.readLine();
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    var parts = line.split("\\s+");
                    // wavelength is the first part and increases by 1 every line
                    var wavelength = Double.parseDouble(parts[0]);
                    if (wavelength < 1000) {
                        continue;
                    }
                    var intensity = parts[parts.length - 1];
                    // intensity is a list of 500 measurements grouped by 4 digits
                    // e.g 12345678901234567890... -> 1234 5678 9012 3456 7890 ...
                    var wlIncrement = 2 / 1000.0;
                    int previous = 0;
                    for (int i = 0; i < intensity.length(); i += 4) {
                        var value = Integer.parseInt(intensity.substring(i, i + 4));
                        var out = String.format(Locale.US, "%.2f %d", wavelength, value);
                        var key = (int) (wavelength * 100);
                        if (key != previous) {
                            previous = key;
                            writer.println(out);
                        }
                        wavelength += wlIncrement;
                    }
                }
            }
        }
    }
}
