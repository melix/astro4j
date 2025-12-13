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
import me.champeau.a4j.math.opencl.OpenCLSupport;

import java.util.Arrays;

import static java.lang.Math.round;

/**
 * Interface for image processing operations with multiple backend implementations.
 * Implementations may use OpenCL, Vector API, or fallback scalar operations.
 */
public interface ImageMath {

    /**
     * Maximum pixel value for 16-bit images.
     */
    int MAX_VALUE = 65535;

    /**
     * Creates a new instance with the best available backend implementation.
     *
     * @return an ImageMath instance
     */
    static ImageMath newInstance() {
        // OpenCL requires explicit opt-in via OPENCL_ENABLED=true
        if (OpenCLSupport.isEnabled()) {
            return new OpenCLImageMath(OpenCLSupport.getContext());
        }
        // Vector API enabled by default when available
        if (VectorApiSupport.isEnabled()) {
            return new VectorApiImageMath();
        }
        return new FallbackImageMath();
    }

    /**
     * Rotates an image 90 degrees counter-clockwise.
     *
     * @param image the source image
     * @return the rotated image
     */
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

    /**
     * Rotates an image 90 degrees clockwise.
     *
     * @param image the source image
     * @return the rotated image
     */
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

    /**
     * Computes the average value for each row in the image.
     *
     * @param image the source image
     * @return an array of row averages
     */
    default double[] lineAverages(Image image) {
        var height = image.height();
        double[] result = new double[height];
        for (int y = 0; y < height; y++) {
            result[y] = averageOf(image, y);
        }
        return result;
    }

    /**
     * Computes the average value of a specific row in the image.
     *
     * @param image the source image
     * @param lineNb the row number
     * @return the average value of the row
     */
    default double averageOf(Image image, int lineNb) {
        var data = image.data();
        var width = image.width();
        double sum = 0;
        for (int x = 0; x < width; x++) {
            sum += data[lineNb][x];
        }
        return sum / width;
    }

    /**
     * Computes the average value of an array.
     *
     * @param data the input array
     * @return the average value
     */
    default double averageOf(double[] data) {
        double sum = 0;
        int max = data.length;
        for (double datum : data) {
            sum += datum;
        }
        return sum / max;
    }

    /**
     * Computes the average value of a 2D array.
     *
     * @param data the input 2D array
     * @return the average value
     */
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

    /**
     * Updates a running average with a new data point using incremental formula.
     *
     * @param current the current data values
     * @param average the running average (modified in place)
     * @param n the count of values including this one
     */
    default void incrementalAverage(float[][] current, float[][] average, int n) {
        for (int j = 0; j < current.length; j++) {
            var averageLine = average[j];
            var currentLine = current[j];
            for (int i = 0; i < currentLine.length; i++) {
                averageLine[i] = averageLine[i] + (currentLine[i] - averageLine[i]) / n;
            }
        }
    }

    /**
     * Rotates and scales an image in one operation.
     *
     * @param image the source image
     * @param angle the rotation angle in radians
     * @param blackpoint the value to use for pixels outside the source
     * @param scaleX the horizontal scale factor
     * @param scaleY the vertical scale factor
     * @return the transformed image
     */
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

    /**
     * Rotates an image by an arbitrary angle with optional resizing.
     *
     * @param image the source image
     * @param angle the rotation angle in radians
     * @param blackpoint the value to use for pixels outside the source (negative for adaptive)
     * @param resize if true, resize output to fit entire rotated image
     * @return the rotated image
     */
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

    /**
     * Rescales an image to new dimensions using bilinear interpolation.
     *
     * @param image the source image
     * @param newWidth the target width
     * @param newHeight the target height
     * @return the rescaled image
     */
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

    /**
     * Mirrors an image horizontally and/or vertically.
     *
     * @param source the source image
     * @param horizontalMirror if true, mirror horizontally
     * @param verticalMirror if true, mirror vertically
     * @return the mirrored image
     */
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

    /**
     * Computes the integral image for fast area sum queries.
     *
     * @param source the source image
     * @return the integral image
     */
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

    /**
     * Computes the sum of pixel values in a rectangular area using an integral image.
     *
     * @param integralImage the precomputed integral image
     * @param x the left coordinate of the area
     * @param y the top coordinate of the area
     * @param width the width of the area
     * @param height the height of the area
     * @return the sum of values in the area
     */
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

    /**
     * Computes the average pixel value in a rectangular area using an integral image.
     *
     * @param integralImage the precomputed integral image
     * @param x the left coordinate of the area
     * @param y the top coordinate of the area
     * @param width the width of the area
     * @param height the height of the area
     * @return the average value in the area
     */
    default float areaAverage(Image integralImage, int x, int y, int width, int height) {
        return areaSum(integralImage, x, y, width, height) / (width * height);
    }

    /**
     * Applies a convolution kernel to an image.
     *
     * @param image the source image
     * @param kernel the convolution kernel
     * @return the convolved image
     */
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

    /**
     * Computes the gradient using left-top Sobel operators.
     *
     * @param image the source image
     * @return the gradient magnitude and direction
     */
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

    /**
     * Computes the gradient using right-bottom Sobel operators.
     *
     * @param image the source image
     * @return the gradient magnitude and direction
     */
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

    /**
     * Multiplies all pixel values by a scalar.
     *
     * @param source the source image
     * @param f the multiplication factor
     * @return the result image
     */
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

    /**
     * Adds a scalar to all pixel values.
     *
     * @param source the source image
     * @param f the value to add
     * @return the result image
     */
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

    /**
     * Divides corresponding pixels of two images.
     *
     * @param first the numerator image
     * @param second the denominator image
     * @return the result image
     */
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

    /**
     * Multiplies corresponding pixels of two images.
     *
     * @param first the first image
     * @param second the second image
     * @return the result image
     */
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

    /**
     * Applies Laplacian edge detection to an image.
     *
     * @param image the source image
     * @return the Laplacian result
     */
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

    /**
     * Applies a distortion map to warp an image using Lanczos interpolation.
     * The distortion grid is a sparse representation of dx/dy displacements
     * sampled at regular intervals (gridStep).
     *
     * @param source the source image to warp
     * @param gridDx the x-displacement grid (gridHeight x gridWidth)
     * @param gridDy the y-displacement grid (gridHeight x gridWidth)
     * @param gridStep the step size between grid samples in pixels
     * @param useLanczos if true, use Lanczos-3 interpolation; otherwise use bilinear
     * @return the warped image
     */
    default Image dedistort(Image source, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos) {
        var data = source.data();
        var width = source.width();
        var height = source.height();
        var gridHeight = gridDx.length;
        var gridWidth = gridDx[0].length;
        var result = new float[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Convert pixel coords to grid coords
                double ax = (double) x / gridStep;
                double ay = (double) y / gridStep;

                // Bicubic interpolation of displacement from grid
                double dx = 0.0;
                double dy = 0.0;

                if (ax >= 0 && ax < gridWidth - 1 && ay >= 0 && ay < gridHeight - 1) {
                    dx = bicubicGridSample(gridDx, ax, ay, gridWidth, gridHeight);
                    dy = bicubicGridSample(gridDy, ax, ay, gridWidth, gridHeight);
                }

                double srcX = x + dx;
                double srcY = y + dy;

                if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
                    if (useLanczos) {
                        result[y][x] = lanczos2D(data, srcX, srcY, width, height);
                    } else {
                        result[y][x] = bilinear2D(data, srcX, srcY, width, height);
                    }
                } else {
                    result[y][x] = 0.0f;
                }
            }
        }

        return new Image(width, height, result);
    }

    private static double bicubicGridSample(float[][] grid, double x, double y, int gridWidth, int gridHeight) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double dx = x - x0;
        double dy = y - y0;

        double result = 0.0;
        for (int i = 0; i < 4; i++) {
            double wy = cubicWeight(dy - (i - 1));
            int yi = Math.min(Math.max(y0 - 1 + i, 0), gridHeight - 1);
            for (int j = 0; j < 4; j++) {
                int xi = Math.min(Math.max(x0 - 1 + j, 0), gridWidth - 1);
                double wx = cubicWeight(dx - (j - 1));
                result += grid[yi][xi] * wx * wy;
            }
        }
        return result;
    }

    private static double cubicWeight(double t) {
        double a = -0.5;
        double absT = Math.abs(t);
        if (absT <= 1.0) {
            return (a + 2.0) * absT * absT * absT - (a + 3.0) * absT * absT + 1.0;
        } else if (absT < 2.0) {
            return a * absT * absT * absT - 5.0 * a * absT * absT + 8.0 * a * absT - 4.0 * a;
        } else {
            return 0.0;
        }
    }

    private static float lanczos2D(float[][] image, double xx, double yy, int width, int height) {
        int LANCZOS_A = 3;
        int x0 = (int) Math.floor(xx);
        int y0 = (int) Math.floor(yy);

        double sum = 0.0;
        double weightSum = 0.0;

        for (int j = y0 - LANCZOS_A + 1; j <= y0 + LANCZOS_A; j++) {
            for (int i = x0 - LANCZOS_A + 1; i <= x0 + LANCZOS_A; i++) {
                if (i >= 0 && i < width && j >= 0 && j < height) {
                    double wx = lanczosKernel(xx - i, LANCZOS_A);
                    double wy = lanczosKernel(yy - j, LANCZOS_A);
                    double w = wx * wy;
                    sum += image[j][i] * w;
                    weightSum += w;
                }
            }
        }

        return weightSum > 0 ? (float) (sum / weightSum) : 0f;
    }

    private static double lanczosKernel(double x, int a) {
        if (x == 0) {
            return 1.0;
        }
        double absX = Math.abs(x);
        if (absX >= a) {
            return 0.0;
        }
        double pix = Math.PI * x;
        return (a * Math.sin(pix) * Math.sin(pix / a)) / (pix * pix);
    }

    private static float bilinear2D(float[][] image, double xx, double yy, int width, int height) {
        int x0 = (int) Math.floor(xx);
        int y0 = (int) Math.floor(yy);
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);

        x0 = Math.max(0, Math.min(x0, width - 1));
        y0 = Math.max(0, Math.min(y0, height - 1));

        double fx = xx - Math.floor(xx);
        double fy = yy - Math.floor(yy);

        float v00 = image[y0][x0];
        float v10 = image[y0][x1];
        float v01 = image[y1][x0];
        float v11 = image[y1][x1];

        double i0 = v00 + fx * (v10 - v00);
        double i1 = v01 + fx * (v11 - v01);

        return (float) (i0 + fy * (i1 - i0));
    }
}
