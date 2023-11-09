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

public class ImageKernel implements Kernel {
    private final int width;
    private final int height;
    private final float[][] kernel;
    private final float factor;

    public static ImageKernel of(Image image) {
        var height = image.height();
        var width = image.width();
        var data = image.data();
        var kernel = new float[height][width];
        float sum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var value = data[y * width + x] / 65535f;
                sum += value;
                kernel[y][x] = value;
            }
        }
        return new ImageKernel(width, height, kernel, 1f/sum);
    }

    private ImageKernel(int width, int height, float[][] kernel, float factor) {
        this.width = width;
        this.height = height;
        this.kernel = kernel;
        this.factor = factor;
    }

    @Override
    public int rows() {
        return height;
    }

    @Override
    public int cols() {
        return width;
    }

    @Override
    public float[][] kernel() {
        return kernel;
    }

    @Override
    public float factor() {
        return factor;
    }
}
