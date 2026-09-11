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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * The transmission of the Earth's atmosphere, as deduced at Kitt Peak by Wallace, Hinkle
 * and Livingston for their atlas of the solar photosphere (NSO/Kitt Peak FTS data
 * produced by NSF/NOAO). The solar atlas the software uses as its reference has these
 * absorption lines removed, but a spectrum observed from the ground has them, and around
 * H-alpha the water lines are as deep as the solar ones: a comparison which ignores them
 * cannot tell one part of that region from another.
 * <p>
 * The depth of the water lines varies with the humidity and the elevation of the Sun, so
 * what is known here is where they are and how deep they typically are, not how deep they
 * are today.
 */
public final class TelluricTransmission {
    private static final TelluricTransmission INSTANCE = new TelluricTransmission();
    private static final double UNIT = 10000;

    private final double minWavelength;
    private final double maxWavelength;
    private final short[] transmissions;

    private TelluricTransmission() {
        try (var reader = new BufferedReader(new InputStreamReader(TelluricTransmission.class.getResourceAsStream("/telluric.txt")))) {
            String line;
            double minWl = Double.MAX_VALUE;
            double maxWl = 0;
            var values = new ArrayList<Short>();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                var parts = line.split("\\s+");
                var wl = Double.parseDouble(parts[0]);
                values.add(Short.parseShort(parts[1]));
                minWl = Math.min(minWl, wl);
                maxWl = Math.max(maxWl, wl);
            }
            this.transmissions = new short[values.size()];
            for (int i = 0; i < values.size(); i++) {
                this.transmissions[i] = values.get(i);
            }
            this.minWavelength = minWl;
            this.maxWavelength = maxWl;
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    /**
     * The transmission of the atmosphere at a wavelength, between 0 and 1. Outside the
     * range the atlas covers the atmosphere is taken as transparent, which it is for all
     * practical purposes below 5000 angstroms.
     *
     * @param wavelength the wavelength, in air
     * @return the transmission
     */
    public static double transmissionAt(Wavelen wavelength) {
        return INSTANCE.transmission(wavelength.angstroms());
    }

    private double transmission(double wavelength) {
        if (wavelength < minWavelength || wavelength > maxWavelength) {
            return 1;
        }
        var exactIndex = (wavelength - minWavelength) * 100;
        var lowerIndex = (int) Math.floor(exactIndex);
        var upperIndex = (int) Math.ceil(exactIndex);
        if (upperIndex >= transmissions.length) {
            return transmissions[transmissions.length - 1] / UNIT;
        }
        if (lowerIndex == upperIndex) {
            return transmissions[lowerIndex] / UNIT;
        }
        var lower = transmissions[lowerIndex];
        var upper = transmissions[upperIndex];
        return (lower + (upper - lower) * (exactIndex - lowerIndex)) / UNIT;
    }
}
