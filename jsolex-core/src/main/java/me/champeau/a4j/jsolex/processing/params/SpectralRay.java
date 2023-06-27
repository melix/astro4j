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
package me.champeau.a4j.jsolex.processing.params;

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.color.KnownCurves;

import java.util.List;
import java.util.Optional;

/**
 * Defines the most common spectral lines used with Sol'Ex and when possible,
 * defines a color curve to perform automatic coloring of images.
 * See https://en.wikipedia.org/wiki/Fraunhofer_lines for wavelenths
 */
public record SpectralRay(String label, ColorCurve colorCurve, double wavelength) {
    public static final SpectralRay H_ALPHA = new SpectralRay("H-alpha", KnownCurves.H_ALPHA, 656.281d);
    public static final SpectralRay H_BETA = new SpectralRay("H-beta", null, 486.134);
    public static final SpectralRay CALCIUM_K = new SpectralRay("Calcium (K)", KnownCurves.CALCIUM, 393.366);
    public static final SpectralRay CALCIUM_H = new SpectralRay("Calcium (H)", KnownCurves.CALCIUM, 396.847);
    public static final SpectralRay HELIUM_D3 = new SpectralRay("Helium (D3)", KnownCurves.HELIUM, 587.562);
    public static final SpectralRay SODIUM_D1 = new SpectralRay("Sodium (D1)", null, 589.592);
    public static final SpectralRay SODIUM_D2 = new SpectralRay("Sodium (D2)", null, 588.995);
    public static final SpectralRay OTHER = new SpectralRay("Other", null, 0);

    public Optional<ColorCurve> getColorCurve() {
        return Optional.ofNullable(colorCurve);
    }

    @Override
    public String toString() {
        return label;
    }

    public static List<SpectralRay> predefined() {
        return List.of(
                H_ALPHA,
                H_BETA,
                CALCIUM_K,
                CALCIUM_H,
                SODIUM_D1,
                SODIUM_D2,
                HELIUM_D3,
                OTHER
        );
    }
}
