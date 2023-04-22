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
}
