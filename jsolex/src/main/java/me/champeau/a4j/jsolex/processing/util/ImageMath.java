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

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class ImageMath {
    private static final VectorSpecies<Float> FLOAT = FloatVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Double> DOUBLE = DoubleVector.SPECIES_PREFERRED;

    private ImageMath() {

    }

    public static double[] lineAverages(float[] data, int width, int height) {
        double[] result = new double[height];
        for (int y = 0; y < height; y++) {
            result[y] = averageOf(data, width, y);
        }
        return result;
    }

    public static double averageOf(float[] data, int width, int lineNb) {
        double sum = 0;
        int offset = lineNb * width;
        int max = data.length;
        int x = 0;
        for (; x < FLOAT.loopBound(width); x += FLOAT.length()) {
            var mask = FLOAT.indexInRange(offset + x, max);
            var v = FloatVector.fromArray(FLOAT, data, offset + x, mask);
            sum += v.reduceLanes(VectorOperators.ADD, mask);
        }
        for (; x < width; x++) {
            sum += data[offset + x];
        }
        return sum / width;
    }

    public static double averageOf(double[] data) {
        double sum = 0;
        int max = data.length;
        int x = 0;
        for (; x < DOUBLE.loopBound(max); x += FLOAT.length()) {
            var mask = DOUBLE.indexInRange(x, max);
            var v = DoubleVector.fromArray(DOUBLE, data, x, mask);
            sum += v.reduceLanes(VectorOperators.ADD, mask);
        }
        for (; x < max; x++) {
            sum += data[x];
        }
        return sum / max;
    }

    public static float[] rotateLeft(float[] data, int width, int height) {
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[(width - x - 1) * height + y] = data[y * width + x];
            }
        }
        return output;
    }

    public static float[] rotateRight(float[] data, int width, int height) {
        float[] output = new float[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                output[x * height + (height - y - 1)] = data[y * width + x];
            }
        }
        return output;
    }
}
