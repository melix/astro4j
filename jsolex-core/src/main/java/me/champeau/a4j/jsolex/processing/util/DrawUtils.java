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
package me.champeau.a4j.jsolex.processing.util;

/**
 * Utility class for drawing on images, for debugging purposes.
 */
public class DrawUtils {
    private DrawUtils() {
    }

    public static void drawRectangle(float[] image, int width, int height, int x, int y, int w, int h, float value) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                var idx = (y + dy) * width + (x + dx);
                if (idx >= 0 && idx < image.length) {
                    image[idx] = value;
                }
            }
        }
    }


    public static void drawLine(float[] image, int width, int height, int x1, int y1, int x2, int y2, int thickness) {
        var dx = x2 - x1;
        var dy = y2 - y1;
        var length = Math.max(Math.abs(dx), Math.abs(dy));
        var xinc = dx / (double) length;
        var yinc = dy / (double) length;

        for (int i = 0; i < length; i++) {
            for (int j = -thickness / 2; j <= thickness / 2; j++) {
                int x = (int) Math.round(x1 + i * xinc);
                int y = (int) Math.round(y1 + i * yinc) + j;
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    int idx = y * width + x;
                    if (idx >= 0 && idx < image.length) {
                        image[idx] = 65535;
                    }
                }
            }
        }
    }

    public static float[][] asTile(float[] data, int x, int y, int width, int tileSize) {
        float[][] result = new float[tileSize][tileSize];
        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                if (y * width + x < data.length) {
                    result[dy][dx] = data[y * width + x];
                }
            }
        }
        return result;
    }
}
