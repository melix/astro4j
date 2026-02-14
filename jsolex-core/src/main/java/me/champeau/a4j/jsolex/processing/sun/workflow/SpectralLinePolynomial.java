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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.math.tuples.DoubleQuadruplet;

/**
 * Wraps the polynomial that describes the spectral line position across frames.
 * Given an x position in a frame, the polynomial returns the y position of the
 * spectral line center at that x coordinate.
 * <p>
 * The polynomial is stored as coefficients (a, b, c, d) representing ax³ + bx² + cx + d,
 * which allows for serialization.
 *
 * @param coefficients the polynomial coefficients (a, b, c, d)
 */
public record SpectralLinePolynomial(
    DoubleQuadruplet coefficients
) {
    /**
     * Computes the y position of the spectral line for a given x position.
     *
     * @param x the x position within the frame
     * @return the y position of the spectral line at x
     */
    public double applyAsDouble(double x) {
        return coefficients.a() * x * x * x
             + coefficients.b() * x * x
             + coefficients.c() * x
             + coefficients.d();
    }

    /**
     * Computes the y position within a frame for a given x position and pixel shift.
     *
     * @param xInFrame   the x position within the frame
     * @param pixelShift the pixel shift from the spectral line center
     * @return the y position within the frame
     */
    public double computeYInFrame(double xInFrame, double pixelShift) {
        return applyAsDouble(xInFrame) + pixelShift;
    }
}
