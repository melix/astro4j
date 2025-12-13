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
package me.champeau.a4j.math.image;

/**
 * This class implements deconvolution algorithms.
 * In particular, it adds support for Richardson-Lucy deconvolution.
 */
public class Deconvolution {
    private static final float EPSILON = 1e-7f;
    private static final int MAX_VALUE = 65535;

    /** Default PSF radius. */
    public static final double DEFAULT_RADIUS = 2.5;
    /** Default PSF sigma. */
    public static final double DEFAULT_SIGMA = 2.5;
    /** Default number of iterations. */
    public static final int DEFAULT_ITERATIONS = 5;

    private final ImageMath imageMath;

    /**
     * Creates a new deconvolution processor.
     * @param imageMath the image math operations provider
     */
    public Deconvolution(ImageMath imageMath) {
        this.imageMath = imageMath;
    }

    /**
     * Generates a Gaussian point spread function.
     * @param radius the PSF radius
     * @param sigma the Gaussian sigma
     * @return the PSF image
     */
    public static Image generateGaussianPSF(double radius, double sigma) {
        int width = (int) (2 * (radius + .5));
        int height = width;
        var psf = new float[height][width];
        double cx = width / 2.0;
        double cy = height / 2.0;
        double sigmaFactor = radius / sigma;

        for (int y = 0; y < height; y++) {
            double dy = y - cy;
            for (int x = 0; x < width; x++) {
                double dx = x - cx;
                double distanceSquared = dx * dx + dy * dy;
                double value = Math.exp(-distanceSquared / (2 * sigmaFactor * sigmaFactor));
                psf[y][x] = (float) (MAX_VALUE * value);
            }
        }
        return new Image(width, height, psf);
    }

    /**
     * Performs Richardson-Lucy deconvolution.
     * @param image the input image
     * @param psf the point spread function
     * @param iterations the number of iterations
     * @return the deconvolved image
     */
    public Image richardsonLucy(Image image, Image psf, int iterations) {
        var estimate = image;
        var psfFlipped = imageMath.mirror(psf, false, true);
        var psfKernel = ImageKernel.of(psf);
        var psfFlippedKernel = ImageKernel.of(psfFlipped);
        for (int i = 0; i < iterations; i++) {
            // Avoid division by zero by adding a small epsilon
            var estimateConvolved = imageMath.add(
                imageMath.convolve(estimate, psfKernel),
                EPSILON);
            var ratio = imageMath.divide(image, estimateConvolved);
            var ratioConvolved = imageMath.convolve(ratio, psfFlippedKernel);
            estimate = imageMath.multiply(estimate, ratioConvolved);
            clampData(estimate);
        }
        return estimate;
    }

    private static void clampData(Image image) {
        var data = image.data();
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x];
                if (v > MAX_VALUE) {
                    data[y][x] = MAX_VALUE;
                } else if (v < 0) {
                    data[y][x] = 0;
                }
            }
        }
    }

}
