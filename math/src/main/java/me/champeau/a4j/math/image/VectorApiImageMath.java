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

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

class VectorApiImageMath implements ImageMath {
    private final VectorSpecies<Float> floatSpecies;
    private final VectorSpecies<Double> doubleSpecies;

    public VectorApiImageMath() {
        this.floatSpecies = FloatVector.SPECIES_PREFERRED;
        this.doubleSpecies = DoubleVector.SPECIES_PREFERRED;
    }

    @Override
    public double[] lineAverages(float[] data, int width, int height) {
        double[] result = new double[height];
        for (int y = 0; y < height; y++) {
            result[y] = averageOf(data, width, y);
        }
        return result;
    }

    @Override
    public double averageOf(float[] data, int width, int lineNb) {
        double sum = 0;
        int offset = lineNb * width;
        int max = data.length;
        int x = 0;
        for (; x < floatSpecies.loopBound(width); x += floatSpecies.length()) {
            var mask = floatSpecies.indexInRange(offset + x, max);
            var v = FloatVector.fromArray(floatSpecies, data, offset + x, mask);
            sum += v.reduceLanes(VectorOperators.ADD, mask);
        }
        for (; x < width; x++) {
            sum += data[offset + x];
        }
        return sum / width;
    }

    @Override
    public double averageOf(double[] data) {
        double sum = 0;
        int max = data.length;
        int x = 0;
        for (; x < doubleSpecies.loopBound(max); x += doubleSpecies.length()) {
            var mask = doubleSpecies.indexInRange(x, max);
            var v = DoubleVector.fromArray(doubleSpecies, data, x, mask);
            sum += v.reduceLanes(VectorOperators.ADD, mask);
        }
        for (; x < max; x++) {
            sum += data[x];
        }
        return sum / max;
    }

}
