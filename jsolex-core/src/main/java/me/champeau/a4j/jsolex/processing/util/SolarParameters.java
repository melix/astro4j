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
package me.champeau.a4j.jsolex.processing.util;

import static java.lang.Math.toDegrees;

/**
 * The Carrington rotation, B0, L0 and P angles.
 * @param carringtonRotation the Carrington rotation number
 * @param b0 the B0 angle in radians
 * @param l0 the L0 angle in radians
 * @param p the P angle in radians
 * @param apparentSize the apparent solar size
 */
public record SolarParameters(
        int carringtonRotation,
        double b0,
        double l0,
        double p,
        double apparentSize
) {

    private static final String ANGLE = "%.2f°";

    public String toString() {
        return "Carrington rotation = " + carringtonRotation +
               ", B0 = " + String.format(ANGLE, toDegrees(b0)) +
               ", L0 = " + String.format(ANGLE, toDegrees(l0)) +
               ", P = " + String.format(ANGLE, toDegrees(p));
    }
}
