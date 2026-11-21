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

import java.util.Arrays;

import static java.lang.Math.round;

final class LegacyImageMath {
    private LegacyImageMath() {
    }

    static Image rotateAndScale(Image image, double angle, float blackpoint, double scaleX, double scaleY) {
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

    static Image rotate(Image image, double angle, float blackpoint, boolean resize) {
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

    static Image rescale(Image image, int newWidth, int newHeight) {
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
}
