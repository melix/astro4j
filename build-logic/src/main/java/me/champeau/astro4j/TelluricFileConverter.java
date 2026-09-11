/*
 * Copyright 2026-2026 the original author or authors.
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
import java.util.ArrayList;
import java.util.Locale;

/**
 * Converts the telluric spectrum of the NSO/Kitt Peak atlas, given against observed
 * wavenumbers, into transmissions on the same grid as the solar atlas: air wavelengths
 * in angstroms, every hundredth of an angstrom, scaled to ten thousand.
 */
@CacheableTask
public abstract class TelluricFileConverter extends DefaultTask {
    /**
     * The factors the atlas gives to bring its observed wavenumbers to the laboratory
     * scale, below and above 16,000 cm-1.
     */
    private static final double LOW_SCALE = 1.0000013;
    private static final double HIGH_SCALE = 0.9999981;
    private static final double SCALE_BOUNDARY = 16000;
    private static final double STEP = 0.01;
    private static final int UNIT = 10000;

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void convert() throws IOException {
        var inputFile = getInputFile().get().getAsFile().toPath();
        var outputFile = getOutputFile().get().getAsFile().toPath();
        var wavelengths = new ArrayList<Double>();
        var transmissions = new ArrayList<Double>();
        try (var reader = Files.newBufferedReader(inputFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                var parts = line.trim().split("\\s+");
                var wavenumber = Double.parseDouble(parts[0]) * (Double.parseDouble(parts[0]) < SCALE_BOUNDARY ? LOW_SCALE : HIGH_SCALE);
                wavelengths.add(airWavelength(1e8 / wavenumber));
                transmissions.add(Double.parseDouble(parts[1]));
            }
        }
        // Wavenumbers increase along the file, so wavelengths decrease: put them in order.
        var count = wavelengths.size();
        var wavelength = new double[count];
        var transmission = new double[count];
        for (int i = 0; i < count; i++) {
            wavelength[i] = wavelengths.get(count - 1 - i);
            transmission[i] = transmissions.get(count - 1 - i);
        }
        fillUndetermined(transmission);
        try (var writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            var first = Math.ceil(wavelength[0] / STEP) * STEP;
            var last = Math.floor(wavelength[count - 1] / STEP) * STEP;
            var index = 0;
            for (var w = first; w <= last + STEP / 2; w += STEP) {
                while (index < count - 2 && wavelength[index + 1] <= w) {
                    index++;
                }
                var span = wavelength[index + 1] - wavelength[index];
                var t = span > 0 ? transmission[index] + (transmission[index + 1] - transmission[index]) * (w - wavelength[index]) / span : transmission[index];
                // Noise takes the transmission above one here and there; nothing physical does.
                var value = (int) Math.round(Math.max(0, Math.min(1, t)) * UNIT);
                writer.println(String.format(Locale.US, "%.2f %d", w, value));
            }
        }
    }

    /**
     * The points where the atlas could not deduce the telluric spectrum, marked by a negative
     * value, are bridged linearly from their valid neighbours. They sit inside the deepest
     * solar lines, where the solar atlas carries the absorption anyway.
     */
    private static void fillUndetermined(double[] transmission) {
        var count = transmission.length;
        for (int i = 0; i < count; i++) {
            if (transmission[i] >= 0) {
                continue;
            }
            var before = i - 1;
            var after = i;
            while (after < count && transmission[after] < 0) {
                after++;
            }
            var left = before >= 0 ? transmission[before] : (after < count ? transmission[after] : 1);
            var right = after < count ? transmission[after] : left;
            for (int k = i; k < after; k++) {
                transmission[k] = left + (right - left) * (k - before) / (double) (after - before);
            }
            i = after;
        }
    }

    /**
     * Converts a vacuum wavelength to the wavelength in standard air, by the dispersion
     * formula of Edlén (1966) which the atlases of the solar spectrum use.
     *
     * @param vacuum the wavelength in vacuum, in angstroms
     * @return the wavelength in air, in angstroms
     */
    private static double airWavelength(double vacuum) {
        var s = 1e4 / vacuum;
        var n = 1 + 6.4328e-5 + 2.94981e-2 / (146 - s * s) + 2.5540e-4 / (41 - s * s);
        return vacuum / n;
    }
}
