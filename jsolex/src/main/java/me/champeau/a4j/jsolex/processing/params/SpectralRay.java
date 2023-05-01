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

public enum SpectralRay {
    H_ALPHA("Hα", KnownCurves.H_ALPHA, 0.25d),
    CALCIUM("Calcium", KnownCurves.CALCIUM, 0.7d),
    OTHER("Other", null, 0.5d);

    private final String label;
    private final ColorCurve colorCurve;
    private final double detectionThreshold;

    SpectralRay(String label, ColorCurve colorCurve, double detectionThreshold) {
        this.label = label;
        this.colorCurve = colorCurve;
        this.detectionThreshold = detectionThreshold;
    }

    public Optional<ColorCurve> getColorCurve() {
        return Optional.ofNullable(colorCurve);
    }

    public double getDetectionThreshold() {
        return detectionThreshold;
    }

    @Override
    public String toString() {
        return label;
    }
}
