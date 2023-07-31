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

    int MAX_VALUE = 65535;

    static ImageMath newInstance() {
        if (VectorApiSupport.isEnabled()) {
            return new VectorApiImageMath();
        }
        return new FallbackImageMath();
    }

    default Image rotateLeft(Image image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[(width - x - 1) * height + y] = data[y * width + x];
            }
        }
        return new Image(height, width, output);
    }

    default Image rotateRight(Image image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[x * height + (height - y - 1)] = data[y * width + x];
            }
        }
        return new Image(height, width, output);
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
                if (sourceX1 >= 0 && sourceX2 < width && sourceY1 >= 0 && sourceY2 < height && fracX >= 0 && fracY >= 0) {
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

    default Image rotate(Image image, double angle, float blackpoint, boolean resize) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int newWidth = resize ? (int) (round(Math.abs(width * cos) + Math.abs(height * sin))) : width;
        int newHeight = resize ? (int) (round(Math.abs(height * cos) + Math.abs(width * sin))) : height;
        int centerX = width / 2;
        int centerY = height / 2;
        int newCenterX = newWidth / 2;
        int newCenterY = newHeight / 2;
        float[] output = new float[newWidth * newHeight];
        Arrays.fill(output, blackpoint);
        double meanFill = 0;
        double missFill = 0;
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                var rx = (x - newCenterX);
                var ry = (y - newCenterY);
                var sx = rx * cos + ry * sin + centerX;
                var sy = -rx * sin + ry * cos + centerY;
                var sourceX1 = (int) sx;
                var sourceY1 = (int) sy;
                var sourceX2 = sourceX1 + 1;
                var sourceY2 = sourceY1 + 1;
                var fracX = sx - sourceX1;
                var fracY = sy - sourceY1;
                if (sourceX1 >= 0 && sourceX2 < width && sourceY1 >= 0 && sourceY2 < height && fracX >= 0 && fracY >= 0) {
                    var val11 = data[sourceX1 + sourceY1 * width];
                    var val12 = data[sourceX1 + sourceY2 * width];
                    var val21 = data[sourceX2 + sourceY1 * width];
                    var val22 = data[sourceX2 + sourceY2 * width];
                    var interpVal = (float) ((1 - fracX) * (1 - fracY) * val11 +
                                             fracX * (1 - fracY) * val21 +
                                             (1 - fracX) * fracY * val12 +
                                             fracX * fracY * val22);
                    output[x + newWidth * y] = interpVal;
                } else {
                    int prevSX = Math.min(Math.max(0, sourceX1), width - 1);
                    int prevSY = Math.min(Math.max(0, sourceY1), height - 1);
                    var cur = data[prevSX + prevSY * width];
                    meanFill = meanFill + ((cur - meanFill) / (++missFill));
                }
            }
        }
        if (blackpoint < 0) {
            // fill "missing" pixels with average computed value
            for (int y = 0; y < newHeight; y++) {
                for (int x = 0; x < newWidth; x++) {
                    var rx = (x - newCenterX);
                    var ry = (y - newCenterY);
                    var sx = rx * cos + ry * sin + centerX;
                    var sy = -rx * sin + ry * cos + centerY;
                    var sourceX1 = (int) sx;
                    var sourceY1 = (int) sy;
                    var sourceX2 = sourceX1 + 1;
                    var sourceY2 = sourceY1 + 1;
                    var fracX = sx - sourceX1;
                    var fracY = sy - sourceY1;
                    if (sourceX1 < 0 || sourceX2 >= width || sourceY1 < 0 || sourceY2 >= height || (fracX < 0) || (fracY < 0)) {
                        output[x + newWidth * y] = (float) meanFill;
                    }
                }
            }
        }

        return new Image(newWidth, newHeight, output);
    }

    default Image rescale(Image image, int newWidth, int newHeight) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        int centerX = width / 2;
        int centerY = height / 2;
        int newCenterX = newWidth / 2;
        int newCenterY = newHeight / 2;
        double scaleX = (double) newWidth / width;
        double scaleY = (double) newHeight / height;
        float[] output = new float[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                var rx = (x - newCenterX) / scaleX;
                var ry = (y - newCenterY) / scaleY;
                var sx = rx + centerX;
                var sy = ry + centerY;
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

    default Image mirror(Image source, boolean horizontalMirror, boolean verticalMirror) {
        if (!horizontalMirror && !verticalMirror) {
            return source;
        }
        var data = source.data();
        var length = data.length;
        var width = source.width();
        var height = source.height();
        float[] mirrored = new float[length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var sx = horizontalMirror ? (width - x - 1) : x;
                var sy = verticalMirror ? (height - y - 1) : y;
                mirrored[x + y * width] = data[sx + sy * width];
            }
        }
        return new Image(width, height, mirrored);
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
        return source.withData(integral);
    }

    default float areaSum(Image integralImage, int x, int y, int width, int height) {
        var imageWidth = integralImage.width();
        var imageHeight = integralImage.height();
        var data = integralImage.data();
        int x0 = Math.min(x, imageWidth) - 1;
        int y0 = Math.min(y, imageHeight) - 1;
        int x1 = Math.min(x + width, imageWidth) - 1;
        int y1 = Math.min(y + height, imageHeight) - 1;

        float topLeft = safeGet(x0, y0, data, imageWidth);
        float topRight = safeGet(x1, y0, data, imageWidth);
        float bottomLeft = safeGet(x0, y1, data, imageWidth);
        float bottomRight = safeGet(x1, y1, data, imageWidth);
        return Math.max(0f, bottomRight - topRight - bottomLeft + topLeft);
    }

    private static float safeGet(int x, int y, float[] integralImage, int width) {
        if (x >= 0 && y >= 0) {
            return integralImage[x + y * width];
        }
        return 0;
    }

    default float areaAverage(Image integralImage, int x, int y, int width, int height) {
        return areaSum(integralImage, x, y, width, height) / (width * height);
    }

    default Image convolve(Image image, Kernel kernel) {
        var source = image.data();
        var height = image.height();
        var width = image.width();
        var maxX = width - 1;
        var maxY = height - 1;
        float[] convolved = new float[source.length];
        float[][] krows = kernel.kernel();
        int kcx = kernel.cols() / 2;
        int kcy = kernel.rows() / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int ky = 0; ky < krows.length; ky++) {
                    float[] kernelRow = krows[ky];
                    int cy = Math.min(Math.max(y + ky - kcy, 0), maxY);
                    for (int kx = 0; kx < kernelRow.length; kx++) {
                        float coef = kernelRow[kx];
                        int cx = Math.min(Math.max(x + kx - kcx, 0), maxX);
                        sum += coef * source[cx + cy * width];
                    }
                }
                var val = Math.min(Math.max(0, sum * kernel.factor()), MAX_VALUE);
                convolved[x + y * width] = val;
            }
        }
        return image.withData(convolved);
    }

    default Gradient gradient(Image image) {
        var gx = convolve(image, Kernel33.SOBEL_X).data();
        var gy = convolve(image, Kernel33.SOBEL_Y).data();
        var length = image.length();
        float[] mag = new float[length];
        float[] dir = new float[length];
        for (int i = 0; i < length; i++) {
            var x = gx[i];
            var y = gy[i];
            mag[i] = (float) Math.sqrt(x * x + y * y);
            dir[i] = (float) Math.atan2(y, x);
        }
        return new Gradient(image.withData(mag), image.withData(dir));
    }
}
