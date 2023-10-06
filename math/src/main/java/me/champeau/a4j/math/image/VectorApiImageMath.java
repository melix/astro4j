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
    private static final VectorSpecies<Float> FLOAT_SPECIES = FloatVector.SPECIES_PREFERRED;
    public static final int FLOAT_LEN = FLOAT_SPECIES.length();
    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_PREFERRED;

    @Override
    public double[] lineAverages(Image image) {
        var height = image.height();
        double[] result = new double[height];
        for (int y = 0; y < height; y++) {
            result[y] = averageOf(image, y);
        }
        return result;
    }

    @Override
    public double averageOf(Image image, int lineNb) {
        var data = image.data();
        var width = image.width();
        int offset = lineNb * width;
        int x = 0;
        double sum = 0;
        // pre-loop in order to align to float species length
        while ((offset + x) % FLOAT_LEN != 0 && x < width) {
            sum += data[offset + x];
            x++;
        }
        // vectorized loop
        var sumVector = FloatVector.zero(FLOAT_SPECIES);
        var max = FLOAT_SPECIES.loopBound(width - x);
        for (; x < max; x += FLOAT_LEN) {
            var v = FloatVector.fromArray(FLOAT_SPECIES, data, offset + x);
            sumVector = sumVector.add(v);
        }
        sum += sumVector.reduceLanes(VectorOperators.ADD);
        // post-loop for the remainder
        for (; x < width; x++) {
            sum += data[offset + x];
        }
        return sum / width;
    }

    @Override
    public double averageOf(double[] data) {
        int max = data.length;
        int x = 0;
        var sumVector = DoubleVector.zero(DOUBLE_SPECIES);
        for (; x < DOUBLE_SPECIES.loopBound(max); x += DOUBLE_SPECIES.length()) {
            var v = DoubleVector.fromArray(DOUBLE_SPECIES, data, x);
            sumVector = sumVector.add(v);
        }
        double sum = sumVector.reduceLanes(VectorOperators.ADD);
        for (; x < max; x++) {
            sum += data[x];
        }
        return sum / max;
    }

    /**
     * Performs incremental computation of an average at step n.
     * It uses the incremental average formula avg(n) = avg(n-1) + (cur(n)-avg(n-1)/n)
     */
    @Override
    public void incrementalAverage(float[] current, float[] average, int n) {
        var len = current.length;
        int offset = 0;
        for (; offset < FLOAT_SPECIES.loopBound(len); offset += FLOAT_LEN) {
            var avg = FloatVector.fromArray(FLOAT_SPECIES, average, offset);
            var cur = FloatVector.fromArray(FLOAT_SPECIES, current, offset);
            cur.sub(avg).div(n).add(avg).intoArray(average, offset);
        }
        for (int j = offset; j < len; j++) {
            average[j] = average[j] + (current[j] - average[j]) / n;
        }
    }
}
