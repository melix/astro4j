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
package me.champeau.a4j.jsolex.processing.sun.align;

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

public class HessianBoxFilter {
    private final ImageMath imageMath;
    private final Image integralImage;

    /**
     * Creates a Hessian box filter on the underlying
     * integral image
     *
     * @param integralImage the integral image
     * @return the box filter
     */
    public static HessianBoxFilter onIntegralImage(Image integralImage) {
        var imageMath = ImageMath.newInstance();
        return new HessianBoxFilter(integralImage, imageMath);
    }

    /**
     * Creates a Hessian box filter on a regular image.
     *
     * @param source the image
     * @return the box filter
     */
    public static HessianBoxFilter forImage(Image source) {
        var imageMath = ImageMath.newInstance();
        return new HessianBoxFilter(imageMath.integralImage(source), imageMath);
    }

    private HessianBoxFilter(Image integralImage, ImageMath imageMath) {
        this.integralImage = integralImage;
        this.imageMath = imageMath;
    }

    public Image getIntegralImage() {
        return integralImage;
    }

    /**
     * Applies the XX box filter on the image, for a given box size.
     *
     * @param x the x coordinate of the pixel for which we need the output value
     * @param y the y coordinate of the pixel for which we need the output value
     * @param boxSize the box size (e.g 9 for a 9x9 filter, 15 for the 15x15 filter, etc..)
     * @return the output of the filter
     */
    private float computeXX(int x, int y, int boxSize) {
        int center = boxSize / 2;
        int startX = x - center;
        int endX = x + center;
        if (startX < 0 || endX >= integralImage.width()) {
            return 0;
        }
        int startY = y - center;
        int endY = y + center;
        if (startY < 0 || endY >= integralImage.height()) {
            return 0;
        }
        // The filter consists of 3 bands, each same width and height
        // with coefficients 1, -2 and 1
        int bandWidth = boxSize / 3;
        int bandHeight = 2 * boxSize / 3 - 1;
        startY += (boxSize - bandHeight) / 2;
        var area = boxSize * boxSize;
        return (imageMath.areaSum(integralImage, startX, startY, boxSize, bandHeight)
                - 3 * imageMath.areaSum(integralImage, startX + bandWidth, startY, bandWidth, bandHeight)) / area;
    }

    /**
     * Applies the YY box filter on the image, for a given box size.
     *
     * @param x the x coordinate of the pixel for which we need the output value
     * @param y the y coordinate of the pixel for which we need the output value
     * @param boxSize the box size (e.g 9 for a 9x9 filter, 15 for the 15x15 filter, etc..)
     * @return the output of the filter
     */
    private float computeYY(int x, int y, int boxSize) {
        int center = boxSize / 2;
        int startX = x - center;
        int endX = x + center;
        if (startX < 0 || endX >= integralImage.width()) {
            return 0;
        }
        int startY = y - center;
        int endY = y + center;
        if (startY < 0 || endY >= integralImage.height()) {
            return 0;
        }
        // The filter consists of 3 bands, each same width and height
        // with coefficients 1, -2, and 1
        int bandWidth = 2 * boxSize / 3 - 1;
        int bandHeight = boxSize / 3;
        startX += (boxSize - bandWidth) / 2;
        var area = boxSize * boxSize;
        return (imageMath.areaSum(integralImage, startX, startY, bandWidth, boxSize)
                - 3 * imageMath.areaSum(integralImage, startX, startY + bandHeight, bandWidth, bandHeight)) / area;
    }

    /**
     * Applies the XY box filter on the image, for a given box size.
     *
     * @param x the x coordinate of the pixel for which we need the output value
     * @param y the y coordinate of the pixel for which we need the output value
     * @param boxSize the box size (e.g 9 for a 9x9 filter, 15 for the 15x15 filter, etc..)
     * @return the output of the filter
     */
    private float computeXY(int x, int y, int boxSize) {
        int center = boxSize / 2;
        int startX = x - center;
        int endX = x + center;
        if (startX < 0 || endX >= integralImage.width()) {
            return 0;
        }
        int startY = y - center;
        int endY = y + center;
        if (startY < 0 || endY >= integralImage.height()) {
            return 0;
        }
        // The diagonal filter consists of 4 squares with coefficients
        // 1, -1, -1, 1
        var sqSize = boxSize / 3;
        var lx = x - sqSize;
        var ly = y - sqSize;
        var mx = x + 1;
        var my = y + 1;
        var area = boxSize * boxSize;
        return (imageMath.areaSum(integralImage, lx, ly, sqSize, sqSize) - imageMath.areaSum(integralImage, mx, ly, sqSize, sqSize)
                - imageMath.areaSum(integralImage, lx, my, sqSize, sqSize) + imageMath.areaSum(integralImage, mx, my, sqSize, sqSize)) / area;
    }

    private DeterminantAndLaplacian determinantAndLaplacian(int x, int y, int boxSize) {
        var dxx = computeXX(x, y, boxSize);
        var dyy = computeYY(x, y, boxSize);
        var dxy = computeXY(x, y, boxSize);
        var det = dxx * dyy - 0.81f * (dxy * dxy);
        var lap = dxx + dyy >= 0 ? 1 : 0;
        return new DeterminantAndLaplacian(det, (byte) lap);
    }

    public HessianFilterResponse apply(int boxSize) {
        var width = integralImage.width();
        var height = integralImage.height();
        var determinants = new float[height][width];
        var laplacians = new byte[height][width];
        float max = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var current = determinantAndLaplacian(x, y, boxSize);
                determinants[y][x] = current.determinant;
                laplacians[y][x] = current.laplacian;
                if (current.determinant > max) {
                    max = current.determinant;
                }
            }
        }
        return new HessianFilterResponse(boxSize, new Image(width, height, determinants), max, laplacians);
    }

    private record DeterminantAndLaplacian(float determinant, byte laplacian) {
    }
}
