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

import me.champeau.a4j.math.VectorApiSupport;

import java.util.Arrays;

import static java.lang.Math.round;

public interface ImageMath {

    static ImageMath newInstance() {
        if (VectorApiSupport.isEnabled()) {
            return new VectorApiImageMath();
        }
        return new FallbackImageMath();
    }

    default float[] rotateLeft(Image image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[(width - x - 1) * height + y] = data[y * width + x];
            }
        }
        return output;
    }

    default float[] rotateRight(Image image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[x * height + (height - y - 1)] = data[y * width + x];
            }
        }
        return output;
    }

    default double[] lineAverages(Image image) {
        var height = image.height();
        double[] result = new double[height];
        for (int y = 0; y < height; y++) {
            result[y] = averageOf(image, y);
        }
        return result;
    }

    default double averageOf(Image image, int lineNb) {
        var data = image.data();
        var width = image.width();
        double sum = 0;
        int offset = lineNb * width;
        for (int x = 0; x < width; x++) {
            sum += data[offset + x];
        }
        return sum / width;
    }

    default double averageOf(double[] data) {
        double sum = 0;
        int max = data.length;
        for (double datum : data) {
            sum += datum;
        }
        return sum / max;
    }

    default void incrementalAverage(float[] current, float[] average, int n) {
        for (int j = 0; j < current.length; j++) {
            average[j] = average[j] + (current[j] - average[j]) / n;
        }
    }

    default Image rotateAndScale(Image image, double angle, float blackpoint, double scaleX, double scaleY) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int newWidth = (int) ((round(Math.abs(width * cos) + Math.abs(height * sin))) * scaleX);
        int newHeight = (int) ((round(Math.abs(height * cos) + Math.abs(width * sin))) * scaleY);
        int centerX = width / 2;
        int centerY = height / 2;
        int newCenterX = newWidth / 2;
        int newCenterY = newHeight / 2;
        float[] output = new float[newWidth * newHeight];
        Arrays.fill(output, blackpoint);
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                var rx = (x - newCenterX) / scaleX;
                var ry = (y - newCenterY) / scaleY;
                var sx = rx * cos + ry * sin + centerX;
                var sy = -rx * sin + ry * cos + centerY;
                var sourceX1 = (int) sx;
                var sourceY1 = (int) sy;
                var sourceX2 = sourceX1 + 1;
                var sourceY2 = sourceY1 + 1;
                var fracX = sx - sourceX1;
                var fracY = sy - sourceY1;
                if (sourceX1 >= 0 && sourceX2 < width && sourceY1 >= 0 && sourceY2 < height) {
                    var val11 = data[sourceX1 + sourceY1 * width];
                    var val12 = data[sourceX1 + sourceY2 * width];
                    var val21 = data[sourceX2 + sourceY1 * width];
                    var val22 = data[sourceX2 + sourceY2 * width];
                    var interpVal = (float) ((1 - fracX) * (1 - fracY) * val11 +
                                             fracX * (1 - fracY) * val21 +
                                             (1 - fracX) * fracY * val12 +
                                             fracX * fracY * val22);
                    output[x + newWidth * y] = interpVal;
                }
            }
        }

        return new Image(newWidth, newHeight, output);
    }

    default Image integralImage(Image source) {
        var data = source.data();
        var width = source.width();
        var height = source.height();
        float[] integral = new float[width * height];
        integral[0] = data[0];
        for (int x = 1; x < width; x++) {
            integral[x] = integral[x - 1] + data[x];
        }
        for (int y = 1; y < height; y++) {
            int rowStart = y * width;
            integral[rowStart] = integral[rowStart - width] + data[rowStart];
        }
        for (int y = 1; y < height; y++) {
            int rowStart = y * width;
            for (int x = 1; x < width; x++) {
                int index = rowStart + x;
                integral[index] = data[index] + integral[index - 1] + integral[index - width] - integral[index - width - 1];
            }
        }
        return new Image(width, height, integral);
    }

    default float areaSum(Image integralImage, int x, int y, int width, int height) {
        var imageWidth = integralImage.width();
        var data = integralImage.data();
        int xo = width - 1;
        int yo = height - 1;
        float topLeft;
        float topRight;
        float bottomLeft;
        float bottomRight;
        if (x == 0 && y == 0) {
            topLeft = 0;
            topRight = 0;
            bottomLeft = 0;
            bottomRight = data[x + xo + imageWidth * (y + yo)];
        } else if (x == 0) {
            topLeft = 0;
            topRight = data[x + xo + imageWidth * (y - 1)];
            bottomLeft = 0;
            bottomRight = data[x + xo + imageWidth * (y + yo)];
        } else if (y == 0) {
            topLeft = 0;
            topRight = 0;
            bottomLeft = data[x - 1 + imageWidth * (y + yo)];
            bottomRight = data[x + xo + imageWidth * (y + yo)];
        } else {
            topLeft = data[x - 1 + imageWidth * (y - 1)];
            topRight = data[x + xo + imageWidth * (y - 1)];
            bottomLeft = data[x - 1 + imageWidth * (y + yo)];
            bottomRight = data[x + xo + imageWidth * (y + yo)];
        }
        return bottomRight - topRight - bottomLeft + topLeft;
    }

    default float areaAverage(Image integralImage, int x, int y, int width, int height) {
        return areaSum(integralImage, x, y, width, height) / (width * height);
    }

    record Image(int width, int height, float[] data) {
        @Override
        public String toString() {
            return "{ width = " + width + ", height = " + height + ", data = " + Arrays.toString(data) + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Image image = (Image) o;

            if (width != image.width) {
                return false;
            }
            if (height != image.height) {
                return false;
            }
            return Arrays.equals(data, image.data);
        }

        @Override
        public int hashCode() {
            int result = width;
            result = 31 * result + height;
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }
    }
}
