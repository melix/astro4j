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
        var newWidth = height;
        var newHeight = width;
        float[][] output = new float[newHeight][newWidth];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[width - x - 1][y] = data[y][x];
            }
        }
        return new Image(newWidth, newHeight, output);
    }

    default Image rotateRight(Image image) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        var newWidth = height;
        var newHeight = width;
        float[][] output = new float[newHeight][newWidth];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[x][height - y - 1] = data[y][x];
            }
        }
        return new Image(newWidth, newHeight, output);
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
        for (int x = 0; x < width; x++) {
            sum += data[lineNb][x];
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

    default float averageOf(float[][] data) {
        float sum = 0;
        int cpt = 0;
        for (float[] line : data) {
            for (float v : line) {
                sum += v;
                cpt++;
            }
        }
        return sum / cpt;
    }

    default void incrementalAverage(float[][] current, float[][] average, int n) {
        for (int j = 0; j < current.length; j++) {
            var averageLine = average[j];
            var currentLine = current[j];
            for (int i = 0; i < currentLine.length; i++) {
                averageLine[i] = averageLine[i] + (currentLine[i] - averageLine[i]) / n;
            }
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
        float[][] output = new float[newHeight][newWidth];
        for (float[] line : output) {
            Arrays.fill(line, blackpoint);
        }
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
                    var val11 = data[sourceY1][sourceX1];
                    var val12 = data[sourceY2][sourceX1];
                    var val21 = data[sourceY1][sourceX2];
                    var val22 = data[sourceY2][sourceX2];
                    var interpVal = (float) ((1 - fracX) * (1 - fracY) * val11 +
                            fracX * (1 - fracY) * val21 +
                            (1 - fracX) * fracY * val12 +
                            fracX * fracY * val22);
                    output[y][x] = interpVal;
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
        float[][] output = new float[newHeight][newWidth];
        for (float[] line : output) {
            Arrays.fill(line, blackpoint);
        }
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
                    var val11 = data[sourceY1][sourceX1];
                    var val12 = data[sourceY2][sourceX1];
                    var val21 = data[sourceY1][sourceX2];
                    var val22 = data[sourceY2][sourceX2];
                    var interpVal = (float) ((1 - fracX) * (1 - fracY) * val11 +
                            fracX * (1 - fracY) * val21 +
                            (1 - fracX) * fracY * val12 +
                            fracX * fracY * val22);
                    output[y][x] = interpVal;
                } else {
                    int prevSX = Math.min(Math.max(0, sourceX1), width - 1);
                    int prevSY = Math.min(Math.max(0, sourceY1), height - 1);
                    var cur = data[prevSY][prevSX];
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
                        output[y][x] = (float) meanFill;
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
        float[][] output = new float[newHeight][newWidth];
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
                    var val11 = data[sourceY1][sourceX1];
                    var val12 = data[sourceY2][sourceX1];
                    var val21 = data[sourceY1][sourceX2];
                    var val22 = data[sourceY2][sourceX2];
                    var interpVal = (float) ((1 - fracX) * (1 - fracY) * val11 +
                            fracX * (1 - fracY) * val21 +
                            (1 - fracX) * fracY * val12 +
                            fracX * fracY * val22);
                    output[y][x] = interpVal;
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
        var width = source.width();
        var height = source.height();
        float[][] mirrored = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var sx = horizontalMirror ? (width - x - 1) : x;
                var sy = verticalMirror ? (height - y - 1) : y;
                mirrored[y][x] = data[sy][sx];
            }
        }

        return new Image(width, height, mirrored);
    }

    default Image integralImage(Image source) {
        var data = source.data();
        var width = source.width();
        var height = source.height();
        float[][] integral = new float[height][width];

        // Initialize the first cell
        integral[0][0] = data[0][0];

        // Fill the first row
        for (int x = 1; x < width; x++) {
            integral[0][x] = integral[0][x - 1] + data[0][x];
        }

        // Fill the first column
        for (int y = 1; y < height; y++) {
            integral[y][0] = integral[y - 1][0] + data[y][0];
        }

        // Fill the rest of the integral image
        for (int y = 1; y < height; y++) {
            for (int x = 1; x < width; x++) {
                integral[y][x] = data[y][x]
                        + integral[y - 1][x]
                        + integral[y][x - 1]
                        - integral[y - 1][x - 1];
            }
        }

        return new Image(width, height, integral);
    }

    default float areaSum(Image integralImage, int x, int y, int width, int height) {
        var imageWidth = integralImage.width();
        var imageHeight = integralImage.height();
        var data = integralImage.data();
        int x0 = Math.min(x, imageWidth) - 1;
        int y0 = Math.min(y, imageHeight) - 1;
        int x1 = Math.min(x + width, imageWidth) - 1;
        int y1 = Math.min(y + height, imageHeight) - 1;

        float topLeft = safeGet(x0, y0, data);
        float topRight = safeGet(x1, y0, data);
        float bottomLeft = safeGet(x0, y1, data);
        float bottomRight = safeGet(x1, y1, data);
        return Math.max(0f, bottomRight - topRight - bottomLeft + topLeft);
    }

    private static float safeGet(int x, int y, float[][] integralImage) {
        if (x >= 0 && y >= 0) {
            return integralImage[y][x];
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
        var convolved = new float[height][width];

        var krows = kernel.kernel();
        var kHeight = krows.length;
        var kWidth = krows[0].length;
        var kcx = kWidth / 2;
        var kcy = kHeight / 2;
        var factor = kernel.factor();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int ky = 0; ky < kHeight; ky++) {
                    int sy = y + ky - kcy;
                    sy = Math.clamp(sy, 0, height - 1);
                    var kernelRow = krows[ky];
                    var sourceRow = source[sy];
                    for (int kx = 0; kx < kWidth; kx++) {
                        int sx = x + kx - kcx;
                        sx = Math.clamp(sx, 0, width - 1);
                        sum += kernelRow[kx] * sourceRow[sx];
                    }
                }

                var val = sum * factor;
                convolved[y][x] = Math.clamp(val, 0, MAX_VALUE);
            }
        }

        return image.withData(convolved);
    }


    default Gradient gradientLT(Image image) {
        var gx = convolve(image, Kernel33.SOBEL_LEFT).data();
        var gy = convolve(image, Kernel33.SOBEL_TOP).data();
        var width = image.width();
        var height = image.height();

        float[][] mag = new float[height][width];
        float[][] dir = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var xVal = gx[y][x];
                var yVal = gy[y][x];
                mag[y][x] = (float) Math.sqrt(xVal * xVal + yVal * yVal);
                dir[y][x] = (float) Math.atan2(yVal, xVal);
            }
        }

        return new Gradient(new Image(width, height, mag), new Image(width, height, dir));
    }

    default Gradient gradientRB(Image image) {
        var gx = convolve(image, Kernel33.SOBEL_RIGHT).data();
        var gy = convolve(image, Kernel33.SOBEL_BOTTOM).data();
        var width = image.width();
        var height = image.height();

        float[][] mag = new float[height][width];
        float[][] dir = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var xVal = gx[y][x];
                var yVal = gy[y][x];
                mag[y][x] = (float) Math.sqrt(xVal * xVal + yVal * yVal);
                dir[y][x] = (float) Math.atan2(yVal, xVal);
            }
        }

        return new Gradient(new Image(width, height, mag), new Image(width, height, dir));
    }


    default Image multiply(Image source, float f) {
        var firstData = source.data();
        var height = source.height();
        var width = source.width();
        float[][] result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = firstData[y][x] * f;
            }
        }
        return new Image(width, height, result);
    }

    default Image add(Image source, float f) {
        var firstData = source.data();
        var height = source.height();
        var width = source.width();
        float[][] result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = firstData[y][x] + f;
            }
        }
        return new Image(width, height, result);
    }

    default Image divide(Image first, Image second) {
        var firstData = first.data();
        var secondData = second.data();
        var height = first.height();
        var width = first.width();
        float[][] result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = firstData[y][x] / secondData[y][x];
            }
        }
        return new Image(width, height, result);
    }

    default Image multiply(Image first, Image second) {
        var firstData = first.data();
        var secondData = second.data();
        var height = first.height();
        var width = first.width();
        float[][] result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = firstData[y][x] * secondData[y][x];
            }
        }
        return new Image(width, height, result);
    }

    /**
     * Returns an estimate of the image sharpness. The closer to 0, the blurrier the image is.
     *
     * @param image the image to estimate
     * @return the estimated sharpness
     */
    default double estimateSharpness(Image image) {
        var laplacian = laplacian(image);
        var data = laplacian.data();
        var width = laplacian.width();
        var height = laplacian.height();
        double sumOfSquares = 0.0;
        double count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var magnitude = data[y][x];
                count++;
                sumOfSquares += magnitude;
            }
        }
        if (count == 0) {
            return 0;
        }
        return Math.sqrt(sumOfSquares) / count;
    }

    default Image laplacian(Image image) {
        var a = multiply(convolve(image, Kernel33.LAPLACIAN), 2 / 3f).data();
        var b = multiply(convolve(image, Kernel33.LAPLACIAN_B), 1 / 3f).data();
        var height = image.height();
        var width = image.width();
        var sum = new float[height][width];
        for (int y = 0; y < height; y++) {
            sum[y] = new float[width];
            for (int x = 0; x < width; x++) {
                sum[y][x] = a[y][x] + b[y][x];
            }
        }
        return new Image(width, height, sum);
    }
}
