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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class ReferenceIntensities {
    public static final ReferenceIntensities INSTANCE = new ReferenceIntensities();

    private final double minWavelength;
    private final short[] intensities;

    private ReferenceIntensities() {
        try (var reader = new BufferedReader(new InputStreamReader(ReferenceIntensities.class.getResourceAsStream("/atlasvi.txt")))) {
            String line;
            double minWl = Double.MAX_VALUE;
            var intensities = new ArrayList<Short>();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                var parts = line.split("\\s+");
                var wl = Double.parseDouble(parts[0]);
                short intensity = Short.parseShort(parts[1]);
                intensities.add(intensity);
                minWl = Math.min(minWl, wl);
            }
            this.intensities = new short[intensities.size()];
            for (int i = 0; i < intensities.size(); i++) {
                this.intensities[i] = intensities.get(i);
            }
            this.minWavelength = minWl;
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private short intensity(double wavelength) {
        if (wavelength < minWavelength) {
            return 0;
        }

        // Calculate exact index position and floor it to find the lower bound
        var exactIndex = (wavelength - minWavelength) * 10;
        var lowerIndex = (int) exactIndex;
        var upperIndex = lowerIndex + 1;

        if (lowerIndex < 0 || lowerIndex >= intensities.length) {
            return 0;
        }

        if (upperIndex >= intensities.length) {
            return intensities[lowerIndex];
        }

        var lowerIntensity = intensities[lowerIndex];
        var upperIntensity = intensities[upperIndex];
        var interpolation = lowerIntensity + (upperIntensity - lowerIntensity) * (exactIndex - lowerIndex);

        return (short) interpolation;
    }


    public static double intensityAt(double wavelength) {
        return INSTANCE.intensity(wavelength);
    }
}
