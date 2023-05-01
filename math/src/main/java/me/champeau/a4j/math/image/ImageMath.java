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
import me.champeau.a4j.math.matrix.DoubleMatrix;

import java.util.Arrays;

import static java.lang.Math.round;

public interface ImageMath {

    static ImageMath newInstance() {
        if (VectorApiSupport.isPresent()) {
            return new VectorApiImageMath();
        }
        return new FallbackImageMath();
    }

    default float[] rotateLeft(float[] data, int width, int height) {
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[(width - x - 1) * height + y] = data[y * width + x];
            }
        }
        return output;
    }

    default float[] rotateRight(float[] data, int width, int height) {
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[x * height + (height - y - 1)] = data[y * width + x];
            }
        }
        return output;
    }

    double[] lineAverages(float[] data, int width, int height);

    double averageOf(float[] data, int width, int lineNb);

    double averageOf(double[] data);

    private static DoubleMatrix rotationMatrix(double angle) {
        double[][] matrix = new double[2][2];
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        matrix[0][0] = cos;
        matrix[0][1] = sin;
        matrix[1][0] = -sin;
        matrix[1][1] = cos;
        return DoubleMatrix.of(matrix);
    }

    default Image rotateAndScale(float[] data, int width, int height, double angle, float blackpoint, double scaleX, double scaleY) {
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
                var sy = - rx * sin + ry * cos + centerY;
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

    record Image(int width, int height, float[] data) {

    }
}
