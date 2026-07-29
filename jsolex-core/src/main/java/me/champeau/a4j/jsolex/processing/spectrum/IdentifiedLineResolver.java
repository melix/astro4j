/*
 * Copyright 2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.util.List;
import java.util.Locale;

/**
 * Turns a wavelength found by the free search into the spectral ray to process with.
 * <p>
 * A known line is preferred, so that its colour curve, its scripts and everything the
 * user configured for it keep applying. When the wavelength matches no known line, a
 * ray is created on the fly, named after the line catalog when it knows one.
 */
public final class IdentifiedLineResolver {
    /**
     * Tolerance when matching an identified wavelength against a known line. It has to
     * absorb the difference between the reference wavelength of a line and the position
     * of its core in the solar spectrum, which reaches a tenth of an angstrom on blends,
     * while staying far below the separation of the closest known lines.
     */
    private static final double MATCH_TOLERANCE_ANGSTROMS = 0.5;
    /**
     * Prefix of the name given to a line the catalog does not know and which was found by
     * the search. It is deliberately not translated: the name ends up in file names and in
     * saved parameters, where it has to stay the same whichever language the software runs in.
     */
    private static final String AUTOMATIC_PREFIX = "Auto ";

    private IdentifiedLineResolver() {
    }

    /**
     * Resolves the ray to use for a wavelength found by the free search.
     *
     * @param wavelength the identified wavelength
     * @param knownRays the rays the user has configured
     * @return an existing ray when one matches, a newly created one otherwise
     */
    public static SpectralRay resolve(Wavelen wavelength, List<SpectralRay> knownRays) {
        return resolve(wavelength, knownRays, AUTOMATIC_PREFIX);
    }

    /**
     * Resolves the ray to use for a wavelength the user entered. The name carries no mark of
     * an automatic search, since the line was chosen rather than found.
     *
     * @param wavelength the entered wavelength
     * @param knownRays the rays the user has configured
     * @return an existing ray when one matches, a newly created one otherwise
     */
    public static SpectralRay resolveEntered(Wavelen wavelength, List<SpectralRay> knownRays) {
        return resolve(wavelength, knownRays, "");
    }

    private static SpectralRay resolve(Wavelen wavelength, List<SpectralRay> knownRays, String prefix) {
        var angstroms = wavelength.angstroms();
        SpectralRay closest = null;
        var closestDistance = Double.MAX_VALUE;
        for (var ray : knownRays) {
            if (ray.wavelength().angstroms() <= 0) {
                continue;
            }
            var distance = Math.abs(ray.wavelength().angstroms() - angstroms);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = ray;
            }
        }
        if (closest != null && closestDistance <= MATCH_TOLERANCE_ANGSTROMS) {
            return closest;
        }
        return new SpectralRay(nameOf(wavelength, prefix), null, wavelength, false, List.of());
    }

    /**
     * The name to give to a ray created on the fly. The catalog name of the line is used
     * when it knows one, so that the user recognises it; otherwise the name falls back on
     * the given prefix followed by the wavelength.
     */
    private static String nameOf(Wavelen wavelength, String prefix) {
        var angstroms = String.format(Locale.US, "%.2f", wavelength.angstroms());
        return SpectralLineCatalog.findClosest(wavelength, MATCH_TOLERANCE_ANGSTROMS)
                .map(line -> line.shortName() + " " + angstroms)
                .orElse(prefix + angstroms);
    }
}
