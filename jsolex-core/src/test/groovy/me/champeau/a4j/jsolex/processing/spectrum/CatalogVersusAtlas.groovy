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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.params.SpectralRay
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * Evaluation only: compares the wavelengths of the spectral line database with
 * the position of the corresponding line core in the reference atlas, both on
 * the raw atlas and after the broadening used for candidate extraction.
 * Emission lines are excluded: they have no absorption core to measure.
 * Skipped unless AUTO_EVAL is set.
 */
@IgnoreIf({ System.getenv('AUTO_EVAL') == null })
class CatalogVersusAtlas extends Specification {

    private static final double STEP = 0.01
    private static final double SEARCH_HALF_WIDTH = 0.6

    def "locates every catalog line in the reference atlas"() {
        expect:
        println '\n=== database wavelength versus the atlas line core ==='
        println String.format('%-22s %10s %10s %8s %10s %8s %8s',
                'line', 'database', 'raw core', 'delta', 'broad core', 'delta', 'depth')
        SpectralRay.predefined().findAll { it.wavelength().angstroms() > 0 && !it.emission() }.each { ray ->
            var database = ray.wavelength().angstroms()
            var raw = coreNear(database, 0d)
            var broad = coreNear(database, 0.15d)
            println String.format(Locale.US, '%-22s %10.3f %10.3f %+8.3f %10.3f %+8.3f %8.3f',
                    ray.label(), database, raw.position, raw.position - database,
                    broad.position, broad.position - database, raw.depth)
        }

        and: 'the interpolated minimum is quoted to better than the atlas sampling'
        println String.format(Locale.US,
                '\natlas sampling is %.2f A, so a core measured on it carries at least that quantisation', STEP)
        true
    }

    /**
     * Parabolic minimum of the atlas within a narrow window around the given
     * wavelength, with the depth relative to the local continuum.
     */
    private static Core coreNear(double around, double sigma) {
        int samples = (int) (2 * SEARCH_HALF_WIDTH / STEP) + 1
        var values = new double[samples]
        for (int i = 0; i < samples; i++) {
            var wavelength = around - SEARCH_HALF_WIDTH + i * STEP
            values[i] = sigma > 0 ? broadenedAt(wavelength, sigma) : intensityAt(wavelength)
        }
        int best = 0
        for (int i = 1; i < samples; i++) {
            if (values[i] < values[best]) {
                best = i
            }
        }
        double position = around - SEARCH_HALF_WIDTH + best * STEP
        if (best > 0 && best < samples - 1) {
            double denominator = values[best - 1] - 2 * values[best] + values[best + 1]
            if (denominator > 1e-12) {
                position += STEP * 0.5d * (values[best - 1] - values[best + 1]) / denominator
            }
        }
        // local continuum from a wider window, to express the depth
        var wide = new double[401]
        for (int i = 0; i < 401; i++) {
            wide[i] = intensityAt(around - 2 + i * STEP)
        }
        var sorted = wide.clone()
        Arrays.sort(sorted)
        double continuum = sorted[(int) (sorted.length * 0.9d)]
        return new Core(position, continuum > 1e-9 ? 1d - values[best] / continuum : 0d)
    }

    private static double intensityAt(double wavelength) {
        return ReferenceIntensities.intensityAt(Wavelen.ofAngstroms(wavelength))
    }

    private static double broadenedAt(double wavelength, double sigma) {
        double accumulator = 0
        double weights = 0
        double radius = 3 * sigma
        for (double d = -radius; d <= radius; d += STEP) {
            double w = Math.exp(-0.5d * (d / sigma) * (d / sigma))
            accumulator += w * intensityAt(wavelength + d)
            weights += w
        }
        return accumulator / weights
    }

    private record Core(double position, double depth) {
    }
}
