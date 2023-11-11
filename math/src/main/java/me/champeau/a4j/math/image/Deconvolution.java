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

    public static final double DEFAULT_RADIUS = 2.5;
    public static final double DEFAULT_SIGMA = 2.5;
    public static final int DEFAULT_ITERATIONS = 5;

    private final ImageMath imageMath;

    public Deconvolution(ImageMath imageMath) {
        this.imageMath = imageMath;
    }

    public static Image generateGaussianPSF(double radius, double sigma) {
        int width = (int) (2 * (radius + .5));
        int height = width;
        var psf = new float[width * height];
        double cx = width / 2.0;
        double cy = height / 2.0;
        double sigmaFactor = radius / sigma;

        for (int y = 0; y < height; y++) {
            double dy = y - cy;
            for (int x = 0; x < width; x++) {
                double dx = x - cx;
                double distanceSquared = dx * dx + dy * dy;
                double value = Math.exp(-distanceSquared / (2 * sigmaFactor * sigmaFactor));
                psf[y * width + x] = (float) (MAX_VALUE * value);
            }
        }
        return new Image(width, height, psf);
    }

    public Image richardsonLucy(Image image, Image psf, int iterations) {
        var estimate = image;
        var psfFlipped = flip(psf);
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
        for (int j = 0; j < data.length; j++) {
            if (data[j] > MAX_VALUE) {
                data[j] = MAX_VALUE;
            } else if (data[j] < 0) {
                data[j] = 0;
            }
        }
    }

    private static Image flip(Image image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        var flipped = new float[width * height];
        for (int y = 0; y < height; y++) {
            var row = new float[width];
            System.arraycopy(data, y * width, row, 0, width);
            for (int x = 0; x < width; x++) {
                flipped[y * width + x] = row[width - x - 1];
            }
        }
        return new Image(width, height, flipped);
    }

}
