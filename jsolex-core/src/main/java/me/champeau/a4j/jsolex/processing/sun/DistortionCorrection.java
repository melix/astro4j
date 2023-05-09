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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.math.tuples.DoubleTriplet;

public class DistortionCorrection {
    private final float[] data;
    private final int width;
    private final int height;

    public DistortionCorrection(float[] data, int width, int height) {
        this.data = data;
        this.width = width;
        this.height = height;
    }

    /**
     * Corrects the image for distortion. The image represents a vertical spectrum, with each
     * spectrum line found horizontally in the image. The main spectrum line that we care about
     * should be perfectly horizontal, at the middle of height of the image.
     * The polynomial that we pass as parameter represents the function which describes curvature of
     * the spectrum line that we want to correct so that it is centered on the image.
     *
     * The distortion is representable by a second-order polynomial (a*x^2 + b*x + c).
     *
     * @param p the coefficients of the polynomial
     * @return the corrected image
     */
    public float[] secondOrderPolynomialCorrection(DoubleTriplet p) {
        float[] correctedImage = new float[data.length];
        double middle = height / 2.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // The amount of pixels that we need to shift the spectrum line vertically
                double yCorrection = -p.a() * x * x - p.b() * x - p.c() + middle;

                correctedImage[y * width + x] = (float) bilinearInterpolation(x, y - yCorrection);
            }
        }

        return correctedImage;
    }

    /**
     * Interpolates the pixel value at the given position using bilinear interpolation.
     *
     * @param x the x-coordinate of the position
     * @param y the y-coordinate of the position
     * @return the interpolated pixel value
     */
    private double bilinearInterpolation(double x, double y) {
        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = x1 + 1;
        int y2 = y1 + 1;

        double p1 = (x2 - x) * valueAt(x1, y1) + (x - x1) * valueAt(x2, y1);
        double p2 = (x2 - x) * valueAt(x1, y2) + (x - x1) * valueAt(x2, y2);
        return (y2 - y) * p1 + (y - y1) * p2;
    }

    /**
     * Returns the pixel value at the given position using nearest-neighbor interpolation.
     *
     * @param x the x-coordinate of the position
     * @param y the y-coordinate of the position
     * @return the pixel value at the given position
     */
    private double valueAt(int x, int y) {
        if (x < 0) {
            x = 0;
        } else if (x >= width) {
            x = width - 1;
        }
        if (y < 0) {
            y = 0;
        } else if (y >= height) {
            y = height - 1;
        }
        return data[y * width + x];
    }


}
