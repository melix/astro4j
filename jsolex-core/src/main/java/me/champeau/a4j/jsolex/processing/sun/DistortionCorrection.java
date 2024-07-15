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

import me.champeau.a4j.math.image.Image;

import java.util.function.DoubleUnaryOperator;

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
    public float[] polynomialCorrection(DoubleUnaryOperator p) {
        return polynomialCorrection2(p, false);
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
     * @return the corrected image, restricted to the pixels which are found in the original image
     */
    public Image polynomicalCorrectionHeightRestricted(DoubleUnaryOperator p) {
        var corrected = polynomialCorrection2(p, true);
        int minY = 0;
        for (int y = 0; y < height; y++) {
            boolean allPositive = true;
            for (int x = 0; x < width; x++) {
                if (corrected[x + y * width] < 0) {
                    allPositive = false;
                    break;
                }
            }
            if (allPositive) {
                minY = y;
                break;
            }
        }
        int maxY = height - 1;
        for (int y = height - 1; y >= 0; y--) {
            boolean allPositive = true;
            for (int x = 0; x < width; x++) {
                if (corrected[x + y * width] < 0) {
                    allPositive = false;
                    break;
                }
            }
            if (allPositive) {
                maxY = y;
                break;
            }
        }
        int newHeight = maxY - minY;
        float[] result = new float[width * newHeight];
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < width; x++) {
                result[x + (y - minY) * width] = corrected[x + y * width];
            }
        }
        return new Image(width, newHeight, result);
    }

    private float[] polynomialCorrection2(DoubleUnaryOperator p, boolean mark) {
        float[] correctedImage = new float[data.length];
        double middle = height / 2.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // The amount of pixels that we need to shift the spectrum line vertically
                double yCorrection = -p.applyAsDouble(x) + middle;

                correctedImage[y * width + x] = (float) bilinearInterpolation(x, y - yCorrection, mark);
            }
        }

        return correctedImage;
    }

    public double correctY(DoubleUnaryOperator p, double x, double y) {
        double middle = height / 2.0;
        double yCorrection = -p.applyAsDouble(x) + middle;
        return y + yCorrection;
    }

    /**
     * Interpolates the pixel value at the given position using bilinear interpolation.
     *
     * @param x the x-coordinate of the position
     * @param y the y-coordinate of the position
     * @return the interpolated pixel value
     */
    private double bilinearInterpolation(double x, double y, boolean mark) {
        int x1 = (int) Math.floor(x);
        int y1 = (int) Math.floor(y);
        int x2 = x1 + 1;
        int y2 = y1 + 1;

        double p1 = (x2 - x) * valueAt(x1, y1, mark) + (x - x1) * valueAt(x2, y1, mark);
        double p2 = (x2 - x) * valueAt(x1, y2, mark) + (x - x1) * valueAt(x2, y2, mark);
        return (y2 - y) * p1 + (y - y1) * p2;
    }

    /**
     * Returns the pixel value at the given position using nearest-neighbor interpolation.
     *
     * @param x the x-coordinate of the position
     * @param y the y-coordinate of the position
     * @return the pixel value at the given position
     */
    private double valueAt(int x, int y, boolean mark) {
        if (x < 0) {
            if (mark) {
                return -1;
            }
            x = 0;
        } else if (x >= width) {
            if (mark) {
                return -1;
            }
            x = width - 1;
        }
        if (y < 0) {
            if (mark) {
                return -1;
            }
            y = 0;
        } else if (y >= height) {
            if (mark) {
                return -1;
            }
            y = height - 1;
        }
        return data[y * width + x];
    }


}
