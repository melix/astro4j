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

import me.champeau.a4j.math.RowStrips;
import me.champeau.a4j.math.VectorApiSupport;
import me.champeau.a4j.math.opencl.OpenCLSupport;

import java.util.Arrays;

/**
 * Interface for image processing operations with multiple backend implementations.
 * Implementations may use OpenCL, Vector API, or fallback scalar operations.
 */
public interface ImageMath {

    /**
     * Maximum pixel value for 16-bit images.
     */

    /**
     * Creates a new instance with the best available backend implementation.
     *
     * @return an ImageMath instance
     */
    static ImageMath newInstance() {
        if (OpenCLSupport.isEnabled()) {
            return new OpenCLImageMath(OpenCLSupport.getContext());
        }
        return newCpuInstance();
    }

    /**
     * Creates a CPU-only instance, bypassing GPU acceleration.
     * Use this in parallel processing paths where GPU lock contention
     * would negate the benefits of GPU acceleration.
     *
     * @return a CPU-only ImageMath instance
     */
    static ImageMath newCpuInstance() {
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

        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    output[width - x - 1][y] = data[y][x];
                }
            }
        });
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

        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    output[x][height - y - 1] = data[y][x];
                }
            }
        });
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
     * @param image  the source image
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
     * @param n       the count of values including this one
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
     * @param image      the source image
     * @param angle      the rotation angle in radians
     * @param blackpoint the value to use for pixels outside the source
     * @param scaleX     the horizontal scale factor
     * @param scaleY     the vertical scale factor
     * @return the transformed image
     */
    default Image rotateAndScale(Image image, double angle, float blackpoint, double scaleX, double scaleY) {
        var sampling = PlaneOperations.rotationAndScale(image.data(), image.width(), image.height(), angle, scaleX, scaleY);
        var newWidth = sampling.newWidth();
        var newHeight = sampling.newHeight();
        float[][] output = new float[newHeight][newWidth];
        for (int y = 0; y < newHeight; y++) {
            Arrays.fill(output[y], blackpoint);
            sampling.rotateAndScaleRow(scaleX, scaleY, y, output[y]);
        }
        return new Image(newWidth, newHeight, output);
    }

    /**
     * Rotates an image by an arbitrary angle with optional resizing.
     *
     * @param image      the source image
     * @param angle      the rotation angle in radians
     * @param blackpoint the value to use for pixels outside the source (negative for adaptive)
     * @param resize     if true, resize output to fit entire rotated image
     * @return the rotated image
     */
    default Image rotate(Image image, double angle, float blackpoint, boolean resize) {
        var sampling = PlaneOperations.rotation(image.data(), image.width(), image.height(), angle, resize);
        var newWidth = sampling.newWidth();
        var newHeight = sampling.newHeight();
        float[][] output = new float[newHeight][newWidth];
        var fill = blackpoint < 0 ? new double[2] : null;
        for (int y = 0; y < newHeight; y++) {
            Arrays.fill(output[y], blackpoint);
            sampling.rotateRow(y, output[y], fill);
        }
        if (fill != null) {
            var meanFill = (float) fill[0];
            for (int y = 0; y < newHeight; y++) {
                sampling.fillMissingRow(y, output[y], meanFill);
            }
        }
        return new Image(newWidth, newHeight, output);
    }

    /**
     * Rescales an image to new dimensions using bilinear interpolation.
     *
     * @param image     the source image
     * @param newWidth  the target width
     * @param newHeight the target height
     * @return the rescaled image
     */
    default Image rescale(Image image, int newWidth, int newHeight) {
        var sampling = PlaneOperations.rescaling(image.data(), image.width(), image.height(), newWidth, newHeight);
        float[][] output = new float[newHeight][newWidth];
        for (int y = 0; y < newHeight; y++) {
            sampling.rescaleRow(y, output[y]);
        }
        return new Image(newWidth, newHeight, output);
    }

    /**
     * Mirrors an image horizontally and/or vertically.
     *
     * @param source           the source image
     * @param horizontalMirror if true, mirror horizontally
     * @param verticalMirror   if true, mirror vertically
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
     * @param x             the left coordinate of the area
     * @param y             the top coordinate of the area
     * @param width         the width of the area
     * @param height        the height of the area
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
     * @param x             the left coordinate of the area
     * @param y             the top coordinate of the area
     * @param width         the width of the area
     * @param height        the height of the area
     * @return the average value in the area
     */
    default float areaAverage(Image integralImage, int x, int y, int width, int height) {
        return areaSum(integralImage, x, y, width, height) / (width * height);
    }

    /**
     * Applies a box blur using a sliding window in two separable passes.
     *
     * @param image      the source image
     * @param kernelSize the blur kernel size (must be odd)
     * @return the blurred image
     */
    default Image boxBlur(Image image, int kernelSize) {
        var source = image.data();
        var height = image.height();
        var width = image.width();
        var halfK = kernelSize / 2;
        float invN2 = 1f / ((float) kernelSize * kernelSize);

        var hPass = new float[height][width];
        for (int y = 0; y < height; y++) {
            var srcRow = source[y];
            var dstRow = hPass[y];
            float sum = 0;
            for (int kx = -halfK; kx <= halfK; kx++) {
                sum += srcRow[Math.clamp(kx, 0, width - 1)];
            }
            dstRow[0] = sum;
            for (int x = 1; x < width; x++) {
                sum -= srcRow[Math.clamp(x - halfK - 1, 0, width - 1)];
                sum += srcRow[Math.clamp(x + halfK, 0, width - 1)];
                dstRow[x] = sum;
            }
        }

        var result = new float[height][width];
        var colSums = new float[width];
        for (int ky = -halfK; ky <= halfK; ky++) {
            var row = hPass[Math.clamp(ky, 0, height - 1)];
            for (int x = 0; x < width; x++) {
                colSums[x] += row[x];
            }
        }
        for (int x = 0; x < width; x++) {
            result[0][x] = colSums[x] * invN2;
        }
        for (int y = 1; y < height; y++) {
            var removeRow = hPass[Math.clamp(y - halfK - 1, 0, height - 1)];
            var addRow = hPass[Math.clamp(y + halfK, 0, height - 1)];
            var resultRow = result[y];
            for (int x = 0; x < width; x++) {
                colSums[x] += addRow[x] - removeRow[x];
                resultRow[x] = colSums[x] * invN2;
            }
        }

        return image.withData(result);
    }

    /**
     * Applies a convolution kernel to an image.
     *
     * @param image  the source image
     * @param kernel the convolution kernel
     * @return the convolved image
     */
    default Image convolve(Image image, Kernel kernel) {
        var output = new float[image.height()][image.width()];
        convolve(image, kernel, output);
        return image.withData(output);
    }

    /**
     * Applies a convolution kernel to an image, writing the result to a
     * caller-supplied output buffer. The buffer must have the same dimensions
     * as the image. Use this overload when reusing a pooled buffer to avoid
     * per-call allocation.
     *
     * @param image  the source image
     * @param kernel the convolution kernel
     * @param output the output buffer ({@code float[height][width]})
     * @return the {@code output} buffer, populated with the convolved data
     */
    default float[][] convolve(Image image, Kernel kernel, float[][] output) {
        var source = image.data();
        var height = image.height();
        var width = image.width();

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
                output[y][x] = val;
            }
        }

        return output;
    }

    /**
     * Applies a separable 3x3 Gaussian blur, equivalent to convolving with
     * {@link Kernel33#GAUSSIAN_BLUR}. Border pixels replicate the edge.
     *
     * @param image  the source image
     * @param output the output buffer ({@code float[height][width]})
     * @return the {@code output} buffer, populated with the blurred data
     */
    default float[][] gaussianBlur(Image image, float[][] output) {
        return gaussianBlur(image, output, 0, image.width());
    }

    /**
     * Variant of {@link #gaussianBlur(Image, float[][])} that blurs only the columns in
     * {@code [fromColumn, toColumn)}, leaving the remaining columns of {@code output} untouched.
     *
     * @param image      the source image
     * @param output     the output buffer ({@code float[height][width]})
     * @param fromColumn the first column to blur (inclusive)
     * @param toColumn   the column past the last to blur (exclusive)
     * @return the {@code output} buffer, with columns {@code [fromColumn, toColumn)} blurred
     */
    default float[][] gaussianBlur(Image image, float[][] output, int fromColumn, int toColumn) {
        return gaussianBlur(image, output, new float[image.height()][image.width()], fromColumn, toColumn);
    }

    /**
     * Variant of {@link #gaussianBlur(Image, float[][], int, int)} that reuses a
     * caller-supplied scratch buffer instead of allocating one per call.
     *
     * @param image      the source image
     * @param output     the output buffer ({@code float[height][width]})
     * @param tmp        a reusable scratch buffer ({@code float[height][width]})
     * @param fromColumn the first column to blur (inclusive)
     * @param toColumn   the column past the last to blur (exclusive)
     * @return the {@code output} buffer, with columns {@code [fromColumn, toColumn)} blurred
     */
    default float[][] gaussianBlur(Image image, float[][] output, float[][] tmp, int fromColumn, int toColumn) {
        var source = image.data();
        var height = image.height();
        var width = image.width();
        int interiorStart = Math.max(fromColumn, 1);
        int interiorEnd = Math.min(toColumn, width - 1);
        for (int y = 0; y < height; y++) {
            var srcRow = source[y];
            var tmpRow = tmp[y];
            if (width == 1) {
                if (fromColumn == 0) {
                    tmpRow[0] = 4 * srcRow[0];
                }
                continue;
            }
            if (fromColumn == 0) {
                tmpRow[0] = srcRow[0] + 2 * srcRow[0] + srcRow[1];
            }
            for (int x = interiorStart; x < interiorEnd; x++) {
                tmpRow[x] = srcRow[x - 1] + 2 * srcRow[x] + srcRow[x + 1];
            }
            if (toColumn == width) {
                tmpRow[width - 1] = srcRow[width - 2] + 2 * srcRow[width - 1] + srcRow[width - 1];
            }
        }
        for (int y = 0; y < height; y++) {
            var up = tmp[y > 0 ? y - 1 : 0];
            var mid = tmp[y];
            var down = tmp[y < height - 1 ? y + 1 : height - 1];
            var outRow = output[y];
            for (int x = fromColumn; x < toColumn; x++) {
                var val = (up[x] + 2 * mid[x] + down[x]) * (1f / 16f);
                outRow[x] = val;
            }
        }
        return output;
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
     * @param f      the multiplication factor
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
     * @param f      the value to add
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
     * @param first  the numerator image
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
     * @param first  the first image
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
     * @param source     the source image to warp
     * @param gridDx     the x-displacement grid (gridHeight x gridWidth)
     * @param gridDy     the y-displacement grid (gridHeight x gridWidth)
     * @param gridStep   the step size between grid samples in pixels
     * @param useLanczos if true, use Lanczos-3 interpolation; otherwise use bilinear
     * @return the warped image
     */
    default Image dedistort(Image source, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos) {
        return dedistort(source, gridDx, gridDy, gridStep, useLanczos, 1.0);
    }

    /**
     * Applies a distortion map to warp an image, optionally producing a super-resolved
     * output. When {@code scale} is greater than 1, the output image is allocated at
     * {@code round(scale * width)} by {@code round(scale * height)} and each output pixel
     * is sampled from the source at sub-pixel coordinates via the same Lanczos / bilinear
     * kernel. This is the inverse-warp form of drizzle: combined with stack_dedis
     * averaging, frames with sub-pixel diversity contribute high-frequency content
     * recoverable on the finer output grid.
     *
     * @param source     the source image to warp
     * @param gridDx     the x-displacement grid (gridHeight x gridWidth)
     * @param gridDy     the y-displacement grid (gridHeight x gridWidth)
     * @param gridStep   the step size between grid samples in pixels (in source units)
     * @param useLanczos if true, use Lanczos-3 interpolation; otherwise use bilinear
     * @param scale      output super-resolution factor (1.0 = native, e.g. 1.5, 2.0, 3.0)
     * @return the warped image, sized {@code round(scale*width)} x {@code round(scale*height)}
     */
    default Image dedistort(Image source, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos, double scale) {
        var warp = new PlaneOperations.Warp(source.data(), source.width(), source.height(), gridDx, gridDy, gridStep, useLanczos, scale);
        var outWidth = warp.outWidth();
        var outHeight = warp.outHeight();
        var result = new float[outHeight][outWidth];
        RowStrips.forEach(outHeight, (yStart, yEnd) -> {
            var lanczos = new Lanczos();
            for (int y = yStart; y < yEnd; y++) {
                warp.warpRow(lanczos, y, result[y]);
            }
        });
        return new Image(outWidth, outHeight, result);
    }

    /**
     * Applies a median filter to an image.
     *
     * @param image  the source image
     * @param radius the filter radius (kernel size = 2*radius+1)
     * @return the filtered image
     */
    default Image medianFilter(Image image, int radius) {
        var source = image.data();
        var height = image.height();
        var width = image.width();
        var result = new float[height][width];
        int kSize = 2 * radius + 1;
        var buffer = new float[kSize * kSize];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int count = 0;
                for (int ky = -radius; ky <= radius; ky++) {
                    int sy = Math.clamp(y + ky, 0, height - 1);
                    for (int kx = -radius; kx <= radius; kx++) {
                        int sx = Math.clamp(x + kx, 0, width - 1);
                        buffer[count++] = source[sy][sx];
                    }
                }
                Arrays.sort(buffer, 0, count);
                result[y][x] = buffer[count / 2];
            }
        }
        return image.withData(result);
    }

    /**
     * Rotates an image by 90 degrees counter-clockwise.
     *
     * @param plane the source pixels
     * @return a new plane
     */
    default ImagePlane rotateLeft(ImagePlane plane) {
        return PlaneOperations.rotateQuarter(plane, false);
    }

    /**
     * Rotates an image by 90 degrees clockwise.
     *
     * @param plane the source pixels
     * @return a new plane
     */
    default ImagePlane rotateRight(ImagePlane plane) {
        return PlaneOperations.rotateQuarter(plane, true);
    }

    /**
     * Same as {@link #rotateAndScale(Image, double, float, double, double)}, on a plane.
     *
     * @param plane the source pixels
     * @param angle the rotation angle, in radians
     * @param blackpoint the value of pixels outside of the source
     * @param scaleX the horizontal scale
     * @param scaleY the vertical scale
     * @return a new plane
     */
    default ImagePlane rotateAndScale(ImagePlane plane, double angle, float blackpoint, double scaleX, double scaleY) {
        var sampling = PlaneOperations.rotationAndScale(plane.toArray(), plane.width(), plane.height(), angle, scaleX, scaleY);
        return PlaneOperations.render(sampling.newWidth(), sampling.newHeight(), () -> (y, row) -> {
            Arrays.fill(row, blackpoint);
            sampling.rotateAndScaleRow(scaleX, scaleY, y, row);
        });
    }

    /**
     * Same as {@link #rotate(Image, double, float, boolean)}, on a plane.
     *
     * @param plane the source pixels
     * @param angle the rotation angle, in radians
     * @param blackpoint the value of pixels outside of the source
     * @param resize whether the output is resized to contain the whole rotated image
     * @return a new plane
     */
    default ImagePlane rotate(ImagePlane plane, double angle, float blackpoint, boolean resize) {
        var sampling = PlaneOperations.rotation(plane.toArray(), plane.width(), plane.height(), angle, resize);
        var newWidth = sampling.newWidth();
        var newHeight = sampling.newHeight();
        if (blackpoint < 0) {
            var output = ImagePlane.allocate(newWidth, newHeight);
            var row = new float[newWidth];
            var fill = new double[2];
            for (int y = 0; y < newHeight; y++) {
                Arrays.fill(row, blackpoint);
                sampling.rotateRow(y, row, fill);
                output.setRow(y, row);
            }
            var meanFill = (float) fill[0];
            output.updateRows((y, line) -> sampling.fillMissingRow(y, line, meanFill));
            return output;
        }
        return PlaneOperations.render(newWidth, newHeight, () -> (y, row) -> {
            Arrays.fill(row, blackpoint);
            sampling.rotateRow(y, row, null);
        });
    }

    /**
     * Same as {@link #rescale(Image, int, int)}, on a plane.
     *
     * @param plane the source pixels
     * @param newWidth the new width
     * @param newHeight the new height
     * @return a new plane
     */
    default ImagePlane rescale(ImagePlane plane, int newWidth, int newHeight) {
        var sampling = PlaneOperations.rescaling(plane.toArray(), plane.width(), plane.height(), newWidth, newHeight);
        return PlaneOperations.render(newWidth, newHeight, () -> sampling::rescaleRow);
    }

    /**
     * Same as {@link #mirror(Image, boolean, boolean)}, on a plane.
     *
     * @param plane the source pixels
     * @param horizontalMirror whether to flip horizontally
     * @param verticalMirror whether to flip vertically
     * @return a new plane
     */
    default ImagePlane mirror(ImagePlane plane, boolean horizontalMirror, boolean verticalMirror) {
        return PlaneOperations.mirror(plane, horizontalMirror, verticalMirror);
    }

    /**
     * Same as {@link #convolve(Image, Kernel)}, on a plane.
     *
     * @param plane the source pixels
     * @param kernel the kernel
     * @return a new plane
     */
    default ImagePlane convolve(ImagePlane plane, Kernel kernel) {
        return PlaneOperations.convolve(plane, kernel);
    }

    /**
     * Same as {@link #estimateSharpness(Image)}, on a plane.
     *
     * @param plane the source pixels
     * @return the sharpness estimate
     */
    default double estimateSharpness(ImagePlane plane) {
        return PlaneOperations.estimateSharpness(plane);
    }

    /**
     * Same as {@link #integralImage(Image)}, on a plane.
     *
     * @param plane the source pixels
     * @return the integral image
     */
    default Image integralImage(ImagePlane plane) {
        return PlaneOperations.integralImage(plane);
    }

    /**
     * Same as {@link #dedistort(Image, float[][], float[][], int, boolean, double)}, on a plane.
     *
     * @param plane the source pixels
     * @param gridDx the horizontal displacements
     * @param gridDy the vertical displacements
     * @param gridStep the step of the displacement grid
     * @param useLanczos whether to use Lanczos interpolation
     * @param scale the scale of the output
     * @return a new plane
     */
    default ImagePlane dedistort(ImagePlane plane, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos, double scale) {
        var warp = new PlaneOperations.Warp(plane.toArray(), plane.width(), plane.height(), gridDx, gridDy, gridStep, useLanczos, scale);
        return PlaneOperations.render(warp.outWidth(), warp.outHeight(), () -> {
            var lanczos = new Lanczos();
            return (y, row) -> warp.warpRow(lanczos, y, row);
        });
    }

    static float bilinear2D(float[][] image, double xx, double yy, int width, int height) {
        int x0 = Math.max(0, Math.min((int) Math.floor(xx), width - 1));
        int y0 = Math.max(0, Math.min((int) Math.floor(yy), height - 1));
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);

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
