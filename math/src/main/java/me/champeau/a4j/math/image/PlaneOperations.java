/*
 * Copyright 2026 the original author or authors.
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

import java.util.Arrays;
import java.util.function.Supplier;

import static java.lang.Math.round;

/**
 * Implementations of the {@link ImageMath} operations on planes, which read
 * and write the pixels row by row instead of copying whole images.
 */
final class PlaneOperations {
    private static final int TILE_SIZE = 64;

    private PlaneOperations() {
    }

    static ImagePlane rotateQuarter(ImagePlane plane, boolean clockwise) {
        var width = plane.width();
        var height = plane.height();
        var output = ImagePlane.allocate(height, width);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var tile = new float[TILE_SIZE * TILE_SIZE];
            var column = new float[TILE_SIZE];
            for (int y0 = yStart; y0 < yEnd; y0 += TILE_SIZE) {
                var tileHeight = Math.min(TILE_SIZE, yEnd - y0);
                for (int x0 = 0; x0 < width; x0 += TILE_SIZE) {
                    var tileWidth = Math.min(TILE_SIZE, width - x0);
                    for (int dy = 0; dy < tileHeight; dy++) {
                        plane.getRow(y0 + dy, x0, tile, dy * tileWidth, tileWidth);
                    }
                    for (int dx = 0; dx < tileWidth; dx++) {
                        if (clockwise) {
                            for (int dy = 0; dy < tileHeight; dy++) {
                                column[tileHeight - dy - 1] = tile[dy * tileWidth + dx];
                            }
                            output.setRow(x0 + dx, height - y0 - tileHeight, column, 0, tileHeight);
                        } else {
                            for (int dy = 0; dy < tileHeight; dy++) {
                                column[dy] = tile[dy * tileWidth + dx];
                            }
                            output.setRow(width - x0 - dx - 1, y0, column, 0, tileHeight);
                        }
                    }
                }
            }
        });
        return output;
    }

    static ImagePlane mirror(ImagePlane plane, boolean horizontalMirror, boolean verticalMirror) {
        if (!horizontalMirror && !verticalMirror) {
            return plane.copy();
        }
        var width = plane.width();
        var height = plane.height();
        var output = ImagePlane.allocate(width, height);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var row = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                plane.getRow(verticalMirror ? height - y - 1 : y, row);
                if (horizontalMirror) {
                    for (int i = 0, j = width - 1; i < j; i++, j--) {
                        var tmp = row[i];
                        row[i] = row[j];
                        row[j] = tmp;
                    }
                }
                output.setRow(y, row);
            }
        });
        return output;
    }

    static ImagePlane convolve(ImagePlane plane, Kernel kernel) {
        var width = plane.width();
        var height = plane.height();
        var coefficients = kernel.kernel();
        var factor = kernel.factor();
        var output = ImagePlane.allocate(width, height);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var convolver = new RowConvolver(plane, coefficients, factor);
            var row = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                convolver.convolve(y, row);
                output.setRow(y, row);
            }
        });
        return output;
    }

    static double estimateSharpness(ImagePlane plane) {
        var laplacian = new RowConvolver(plane, Kernel33.LAPLACIAN.kernel(), Kernel33.LAPLACIAN.factor());
        var laplacianB = new RowConvolver(plane, Kernel33.LAPLACIAN_B.kernel(), Kernel33.LAPLACIAN_B.factor());
        return estimateSharpness(plane.width(), plane.height(), laplacian::convolve, laplacianB::convolve);
    }

    static double estimateSharpness(ImagePlane laplacian, ImagePlane laplacianB) {
        return estimateSharpness(laplacian.width(), laplacian.height(), laplacian::getRow, laplacianB::getRow);
    }

    private static double estimateSharpness(int width, int height, RowSource laplacian, RowSource laplacianB) {
        var a = new float[width];
        var b = new float[width];
        double sumOfSquares = 0.0;
        double count = 0;
        for (int y = 0; y < height; y++) {
            laplacian.read(y, a);
            laplacianB.read(y, b);
            for (int x = 0; x < width; x++) {
                var magnitude = a[x] * (2 / 3f) + b[x] * (1 / 3f);
                count++;
                sumOfSquares += magnitude;
            }
        }
        if (count == 0) {
            return 0;
        }
        return Math.sqrt(sumOfSquares) / count;
    }

    static Image integralImage(ImagePlane plane) {
        var width = plane.width();
        var height = plane.height();
        var integral = new float[height][width];
        var row = new float[width];
        plane.getRow(0, row);
        var first = integral[0];
        first[0] = row[0];
        for (int x = 1; x < width; x++) {
            first[x] = first[x - 1] + row[x];
        }
        for (int y = 1; y < height; y++) {
            plane.getRow(y, row);
            var previous = integral[y - 1];
            var current = integral[y];
            current[0] = previous[0] + row[0];
            for (int x = 1; x < width; x++) {
                current[x] = row[x]
                        + previous[x]
                        + current[x - 1]
                        - previous[x - 1];
            }
        }
        return new Image(width, height, integral);
    }

    /**
     * Creates a plane whose rows are computed in parallel by an operator
     * which receives a zero-filled row. The supplier is called once per strip
     * of rows, so that operators can hold state which is not thread safe.
     *
     * @param width the width of the plane
     * @param height the height of the plane
     * @param operators supplies the operator which fills each row
     * @return a new plane
     */
    static ImagePlane render(int width, int height, Supplier<ImagePlane.RowOperator> operators) {
        var output = ImagePlane.allocate(width, height);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var operator = operators.get();
            var row = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                Arrays.fill(row, 0f);
                operator.apply(y, row);
                output.setRow(y, row);
            }
        });
        return output;
    }

    static Sampling rotationAndScale(float[][] data, int width, int height, double angle, double scaleX, double scaleY) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int newWidth = (int) ((round(Math.abs(width * cos) + Math.abs(height * sin))) * scaleX);
        int newHeight = (int) ((round(Math.abs(height * cos) + Math.abs(width * sin))) * scaleY);
        return new Sampling(data, width, height, newWidth, newHeight, cos, sin);
    }

    static Sampling rotation(float[][] data, int width, int height, double angle, boolean resize) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int newWidth = resize ? (int) (round(Math.abs(width * cos) + Math.abs(height * sin))) : width;
        int newHeight = resize ? (int) (round(Math.abs(height * cos) + Math.abs(width * sin))) : height;
        return new Sampling(data, width, height, newWidth, newHeight, cos, sin);
    }

    static Sampling rescaling(float[][] data, int width, int height, int newWidth, int newHeight) {
        return new Sampling(data, width, height, newWidth, newHeight, 1, 0);
    }

    /**
     * The geometry of a resampling of an image into an image of different dimensions.
     *
     * @param data the source pixels
     * @param width the width of the source
     * @param height the height of the source
     * @param newWidth the width of the output
     * @param newHeight the height of the output
     * @param cos the cosine of the rotation angle
     * @param sin the sine of the rotation angle
     */
    record Sampling(float[][] data, int width, int height, int newWidth, int newHeight, double cos, double sin) {

        void rotateAndScaleRow(double scaleX, double scaleY, int y, float[] row) {
            int centerX = width / 2;
            int centerY = height / 2;
            int newCenterX = newWidth / 2;
            int newCenterY = newHeight / 2;
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
                    row[x] = interpVal;
                }
            }
        }

        /**
         * Computes a row of a rotation. Pixels which fall outside of the source
         * are left untouched, and when {@code fill} is not null, the source pixel
         * closest to each of them is added to the running mean held by
         * {@code fill}, as {@code [mean, count]}.
         *
         * @param y the row
         * @param row the row of the output
         * @param fill the running mean of the pixels used to fill the missing ones, or null
         */
        void rotateRow(int y, float[] row, double[] fill) {
            int centerX = width / 2;
            int centerY = height / 2;
            int newCenterX = newWidth / 2;
            int newCenterY = newHeight / 2;
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
                    row[x] = interpVal;
                } else if (fill != null) {
                    int prevSX = Math.min(Math.max(0, sourceX1), width - 1);
                    int prevSY = Math.min(Math.max(0, sourceY1), height - 1);
                    var cur = data[prevSY][prevSX];
                    fill[0] = fill[0] + ((cur - fill[0]) / (++fill[1]));
                }
            }
        }

        void fillMissingRow(int y, float[] row, float value) {
            int centerX = width / 2;
            int centerY = height / 2;
            int newCenterX = newWidth / 2;
            int newCenterY = newHeight / 2;
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
                    row[x] = value;
                }
            }
        }

        void rescaleRow(int y, float[] row) {
            int centerX = width / 2;
            int centerY = height / 2;
            int newCenterX = newWidth / 2;
            int newCenterY = newHeight / 2;
            double scaleX = (double) newWidth / width;
            double scaleY = (double) newHeight / height;
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
                    row[x] = interpVal;
                }
            }
        }
    }

    /**
     * A warp of an image by a distortion grid.
     *
     * @param data the source pixels
     * @param width the width of the source
     * @param height the height of the source
     * @param gridDx the horizontal displacements
     * @param gridDy the vertical displacements
     * @param gridStep the step of the displacement grid
     * @param useLanczos whether to use Lanczos interpolation instead of bilinear
     * @param scale the scale of the output
     */
    record Warp(float[][] data, int width, int height, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos, double scale) {

        int outWidth() {
            return (int) Math.round(width * scale);
        }

        int outHeight() {
            return (int) Math.round(height * scale);
        }

        void warpRow(Lanczos lanczos, int y, float[] row) {
            var gridHeight = gridDx.length;
            var gridWidth = gridDx[0].length;
            var outWidth = outWidth();
            for (int x = 0; x < outWidth; x++) {
                double sx = x / scale;
                double sy = y / scale;
                double ax = sx / gridStep;
                double ay = sy / gridStep;

                double dx = 0.0;
                double dy = 0.0;

                if (ax >= 0 && ax < gridWidth - 1 && ay >= 0 && ay < gridHeight - 1) {
                    dx = bicubicGridSample(gridDx, ax, ay, gridWidth, gridHeight);
                    dy = bicubicGridSample(gridDy, ax, ay, gridWidth, gridHeight);
                }

                double srcX = sx + dx;
                double srcY = sy + dy;

                if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
                    if (useLanczos) {
                        row[x] = lanczos.interpolate(data, srcX, srcY, width, height);
                    } else {
                        row[x] = ImageMath.bilinear2D(data, srcX, srcY, width, height);
                    }
                } else {
                    row[x] = 0.0f;
                }
            }
        }
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

    private interface RowSource {
        void read(int y, float[] row);
    }

    /**
     * Convolves rows of a plane with a kernel, keeping the source rows it needs
     * in a sliding window. Border pixels replicate the edge. An instance must
     * not be shared between threads.
     */
    private static final class RowConvolver {
        private final ImagePlane plane;
        private final float[][] coefficients;
        private final float factor;
        private final int kernelWidth;
        private final int centerX;
        private final int centerY;
        private final float[][] window;
        private final int[] windowRows;
        private final float[][] rows;

        private RowConvolver(ImagePlane plane, float[][] coefficients, float factor) {
            this.plane = plane;
            this.coefficients = coefficients;
            this.factor = factor;
            this.kernelWidth = coefficients[0].length;
            this.centerX = kernelWidth / 2;
            this.centerY = coefficients.length / 2;
            this.window = new float[coefficients.length][plane.width() + kernelWidth - 1];
            this.windowRows = new int[coefficients.length];
            this.rows = new float[coefficients.length][];
            Arrays.fill(windowRows, -1);
        }

        private float[] paddedRow(int y) {
            var sy = Math.clamp(y, 0, plane.height() - 1);
            var slot = sy % window.length;
            var row = window[slot];
            if (windowRows[slot] != sy) {
                var width = plane.width();
                plane.getRow(sy, 0, row, centerX, width);
                if (width > 0) {
                    Arrays.fill(row, 0, centerX, row[centerX]);
                    Arrays.fill(row, centerX + width, row.length, row[centerX + width - 1]);
                }
                windowRows[slot] = sy;
            }
            return row;
        }

        void convolve(int y, float[] output) {
            var kernelHeight = coefficients.length;
            for (int ky = 0; ky < kernelHeight; ky++) {
                rows[ky] = paddedRow(y + ky - centerY);
            }
            var width = output.length;
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int ky = 0; ky < kernelHeight; ky++) {
                    var kernelRow = coefficients[ky];
                    var sourceRow = rows[ky];
                    for (int kx = 0; kx < kernelWidth; kx++) {
                        sum += kernelRow[kx] * sourceRow[x + kx];
                    }
                }
                output[x] = sum * factor;
            }
        }
    }
}
