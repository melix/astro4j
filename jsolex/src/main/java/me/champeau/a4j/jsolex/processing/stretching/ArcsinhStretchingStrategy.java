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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.app.util.Constants;

import static org.apache.commons.math3.util.FastMath.asinh;

/**
 * Implements arcsinh stretching, as described in SIRIL docs:
 * https://free-astro.org/siril_doc-en/co/AsinhTransformation.html
 */
public class ArcsinhStretchingStrategy implements StretchingStrategy {
    private final double blackPoint;
    private final double stretch;

    public ArcsinhStretchingStrategy(float blackPoint, float stretch) {
        this.blackPoint = blackPoint / Constants.MAX_PIXEL_VALUE;
        this.stretch = stretch;
    }

    @Override
    public void stretch(float[] data) {
        for (int i = 0; i < data.length; i++) {
            double original = data[i] / Constants.MAX_PIXEL_VALUE;
            if (original > 0) {
                double stretched = ((original - blackPoint) * asinh(original * stretch)) / (original * asinh(stretch));
                data[i] = (float) stretched*Constants.MAX_PIXEL_VALUE;
            }
        }
    }

}
