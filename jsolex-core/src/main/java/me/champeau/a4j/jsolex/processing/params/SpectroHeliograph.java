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

public record SpectroHeliograph(
    String label,
    double totalAngleDegrees,
    int focalLength,
    int collimatorFocalLength,
    int density,
    int order,
    double slitWidthMicrons,
    double slitHeightMillimeters
) {
    public static final SpectroHeliograph SOLEX = new SpectroHeliograph("Sol'Ex", 34, 125, 80, 2400, 1, 10, 4.5);

    public double totalAngleRadians() {
        return Math.toRadians(totalAngleDegrees);
    }

    public SpectroHeliograph withLabel(String label) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slitHeightMillimeters);
    }

    public SpectroHeliograph withCollimatorFocalLength(int collimatorFocalLength) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slitHeightMillimeters);
    }

    public SpectroHeliograph withDensity(int density) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slitHeightMillimeters);
    }

    public SpectroHeliograph withOrder(int order) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slitHeightMillimeters);
    }

    public SpectroHeliograph withSlitWidthMicrons(double slitWidthMicrons) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slitHeightMillimeters);
    }

    public SpectroHeliograph withSlitHeightMillimeters(double slideHeightMillimeters) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slideHeightMillimeters);
    }

    public SpectroHeliograph withCameraFocalLength(int focalLength) {
        return new SpectroHeliograph(label, totalAngleDegrees, focalLength, collimatorFocalLength, density, order, slitWidthMicrons, slitHeightMillimeters);
    }
}
