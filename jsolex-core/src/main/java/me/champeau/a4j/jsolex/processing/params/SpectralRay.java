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

import java.util.Optional;

/**
 * Defines the most common spectral lines used with Sol'Ex and when possible,
 * defines a color curve to perform automatic coloring of images.
 * See https://en.wikipedia.org/wiki/Fraunhofer_lines for wavelenths
 */
public enum SpectralRay {
    H_ALPHA("H-alpha", KnownCurves.H_ALPHA, 0.25d, 656.281d),
    H_BETA("H-beta", null, 0.25d, 486.134),
    CALCIUM_K("Calcium (K)", KnownCurves.CALCIUM, 0.7d, 393.366),
    CALCIUM_H("Calcium (H)", KnownCurves.CALCIUM, 0.7d, 396.847),
    SODIUM_D1("Sodium (D1)", null, 0.25d, 589.592),
    SODIUM_D2("Sodium (D2)", null, 0.25d, 588.995),
    OTHER("Other", null, 0.5d, 0);

    private final String label;
    private final ColorCurve colorCurve;
    private final double detectionThreshold;
    private final double wavelength;

    SpectralRay(String label, ColorCurve colorCurve, double detectionThreshold, double wavelength) {
        this.label = label;
        this.colorCurve = colorCurve;
        this.detectionThreshold = detectionThreshold;
        this.wavelength = wavelength;
    }

    public Optional<ColorCurve> getColorCurve() {
        return Optional.ofNullable(colorCurve);
    }

    public double getDetectionThreshold() {
        return detectionThreshold;
    }

    public double getWavelength() {
        return wavelength;
    }

    @Override
    public String toString() {
        return label;
    }
}
