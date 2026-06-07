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
        var data = image.data()[lineNb];
        var width = image.width();
        int x = 0;
        double sum = 0;
        // Vectorized loop
        var sumVector = FloatVector.zero(FLOAT_SPECIES);
        var max = FLOAT_SPECIES.loopBound(width);
        for (; x < max; x += FLOAT_LEN) {
            var v = FloatVector.fromArray(FLOAT_SPECIES, data, x);
            sumVector = sumVector.add(v);
        }
        sum += sumVector.reduceLanes(VectorOperators.ADD);
        // post-loop for the remainder
        for (; x < width; x++) {
            sum += data[x];
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

    @Override
    public float averageOf(float[][] data) {
        double totalSum = 0;
        double count = 0;
        for (float[] line : data) {
            int max = line.length;
            int x = 0;
            var sumVector = FloatVector.zero(FLOAT_SPECIES);
            for (; x < FLOAT_SPECIES.loopBound(max); x += FLOAT_SPECIES.length()) {
                var v = FloatVector.fromArray(FLOAT_SPECIES, line, x);
                sumVector = sumVector.add(v);
            }
            float sum = sumVector.reduceLanes(VectorOperators.ADD);
            for (; x < max; x++) {
                sum += line[x];
            }
            totalSum += sum;
            count += max;
        }
        return (float) (totalSum / count);
    }

    @Override
    public Image multiply(Image source, float f) {
        var height = source.height();
        var width = source.width();
        var output = new float[height][width];
        for (int y = 0; y < height; y++) {
            var data = source.data()[y];
            int max = data.length;
            int i = 0;
            var result = new float[max];
            for (; i < FLOAT_SPECIES.loopBound(max); i += FLOAT_SPECIES.length()) {
                var mulVector = FloatVector.broadcast(FLOAT_SPECIES, f);
                var v = FloatVector.fromArray(FLOAT_SPECIES, data, i);
                mulVector = mulVector.mul(v);
                mulVector.intoArray(result, i);
            }
            for (; i < max; i++) {
                result[i] = data[i] * f;
            }
            output[y] = result;
        }
        return new Image(width, height, output);
    }

    @Override
    public Image add(Image source, float f) {
        var height = source.height();
        var width = source.width();
        var output = new float[height][width];
        for (int y = 0; y < height; y++) {
            var data = source.data()[y];
            int max = data.length;
            int i = 0;
            var result = new float[max];
            for (; i < FLOAT_SPECIES.loopBound(max); i += FLOAT_SPECIES.length()) {
                var sumVector = FloatVector.broadcast(FLOAT_SPECIES, f);
                var v = FloatVector.fromArray(FLOAT_SPECIES, data, i);
                sumVector = sumVector.add(v);
                sumVector.intoArray(result, i);
            }
            for (; i < max; i++) {
                result[i] = data[i] + f;
            }
            output[y] = result;
        }
        return new Image(source.width(), source.height(), output);
    }

    @Override
    public Image divide(Image first, Image second) {
        if (first.height() != second.height() || first.width() != second.width()) {
            throw new IllegalArgumentException("Images must have the same dimensions");
        }
        var height = first.height();
        var width = second.width();
        var output = new float[height][width];
        for (int y = 0; y < height; y++) {
            var one = first.data()[y];
            var two = second.data()[y];
            int max = one.length;
            int i = 0;
            var result = new float[max];
            for (; i < FLOAT_SPECIES.loopBound(max); i += FLOAT_SPECIES.length()) {
                var v1 = FloatVector.fromArray(FLOAT_SPECIES, one, i);
                var v2 = FloatVector.fromArray(FLOAT_SPECIES, two, i);
                v1.div(v2).intoArray(result, i);
            }
            for (; i < max; i++) {
                result[i] = one[i] / two[i];
            }
            output[y] = result;
        }
        return new Image(first.width(), second.height(), output);
    }

    @Override
    public Image multiply(Image first, Image second) {
        var height = first.height();
        var width = second.width();
        var output = new float[height][width];
        for (int y = 0; y < height; y++) {
            var one = first.data()[y];
            var two = second.data()[y];
            int max = one.length;
            int i = 0;
            var result = new float[max];
            for (; i < FLOAT_SPECIES.loopBound(max); i += FLOAT_SPECIES.length()) {
                var v1 = FloatVector.fromArray(FLOAT_SPECIES, one, i);
                var v2 = FloatVector.fromArray(FLOAT_SPECIES, two, i);
                v1.mul(v2).intoArray(result, i);
            }
            for (; i < max; i++) {
                result[i] = one[i] * two[i];
            }
            output[y] = result;
        }
        return new Image(first.width(), second.height(), output);
    }

    @Override
    public Image boxBlur(Image image, int kernelSize) {
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
            int x = 0;
            for (; x < FLOAT_SPECIES.loopBound(width); x += FLOAT_LEN) {
                var cs = FloatVector.fromArray(FLOAT_SPECIES, colSums, x);
                var rv = FloatVector.fromArray(FLOAT_SPECIES, row, x);
                cs.add(rv).intoArray(colSums, x);
            }
            for (; x < width; x++) {
                colSums[x] += row[x];
            }
        }
        var invN2Vec = FloatVector.broadcast(FLOAT_SPECIES, invN2);
        var zeroVec = FloatVector.zero(FLOAT_SPECIES);
        var maxVec = FloatVector.broadcast(FLOAT_SPECIES, MAX_VALUE);
        {
            int x = 0;
            for (; x < FLOAT_SPECIES.loopBound(width); x += FLOAT_LEN) {
                var cs = FloatVector.fromArray(FLOAT_SPECIES, colSums, x);
                cs.mul(invN2Vec).max(zeroVec).min(maxVec).intoArray(result[0], x);
            }
            for (; x < width; x++) {
                result[0][x] = Math.clamp(colSums[x] * invN2, 0, MAX_VALUE);
            }
        }
        for (int y = 1; y < height; y++) {
            var removeRow = hPass[Math.clamp(y - halfK - 1, 0, height - 1)];
            var addRow = hPass[Math.clamp(y + halfK, 0, height - 1)];
            var resultRow = result[y];
            int x = 0;
            for (; x < FLOAT_SPECIES.loopBound(width); x += FLOAT_LEN) {
                var cs = FloatVector.fromArray(FLOAT_SPECIES, colSums, x);
                var add = FloatVector.fromArray(FLOAT_SPECIES, addRow, x);
                var rem = FloatVector.fromArray(FLOAT_SPECIES, removeRow, x);
                cs = cs.add(add).sub(rem);
                cs.intoArray(colSums, x);
                cs.mul(invN2Vec).max(zeroVec).min(maxVec).intoArray(resultRow, x);
            }
            for (; x < width; x++) {
                colSums[x] += addRow[x] - removeRow[x];
                resultRow[x] = Math.clamp(colSums[x] * invN2, 0, MAX_VALUE);
            }
        }

        return image.withData(result);
    }

    @Override
    public float[][] gaussianBlur(Image image, float[][] output, float[][] tmp, int fromColumn, int toColumn) {
        var source = image.data();
        var height = image.height();
        var width = image.width();
        int interiorStart = Math.max(fromColumn, 1);
        int interiorEnd = Math.min(toColumn, width - 1);
        var two = FloatVector.broadcast(FLOAT_SPECIES, 2f);
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
            int x = interiorStart;
            for (; x + FLOAT_LEN <= interiorEnd; x += FLOAT_LEN) {
                var left = FloatVector.fromArray(FLOAT_SPECIES, srcRow, x - 1);
                var center = FloatVector.fromArray(FLOAT_SPECIES, srcRow, x);
                var right = FloatVector.fromArray(FLOAT_SPECIES, srcRow, x + 1);
                left.add(center.mul(two)).add(right).intoArray(tmpRow, x);
            }
            for (; x < interiorEnd; x++) {
                tmpRow[x] = srcRow[x - 1] + 2 * srcRow[x] + srcRow[x + 1];
            }
            if (toColumn == width) {
                tmpRow[width - 1] = srcRow[width - 2] + 2 * srcRow[width - 1] + srcRow[width - 1];
            }
        }
        var norm = FloatVector.broadcast(FLOAT_SPECIES, 1f / 16f);
        var zeroVec = FloatVector.zero(FLOAT_SPECIES);
        var maxVec = FloatVector.broadcast(FLOAT_SPECIES, MAX_VALUE);
        for (int y = 0; y < height; y++) {
            var up = tmp[y > 0 ? y - 1 : 0];
            var mid = tmp[y];
            var down = tmp[y < height - 1 ? y + 1 : height - 1];
            var outRow = output[y];
            int x = fromColumn;
            for (; x + FLOAT_LEN <= toColumn; x += FLOAT_LEN) {
                var u = FloatVector.fromArray(FLOAT_SPECIES, up, x);
                var m = FloatVector.fromArray(FLOAT_SPECIES, mid, x);
                var d = FloatVector.fromArray(FLOAT_SPECIES, down, x);
                u.add(m.mul(two)).add(d).mul(norm).max(zeroVec).min(maxVec).intoArray(outRow, x);
            }
            for (; x < toColumn; x++) {
                var val = (up[x] + 2 * mid[x] + down[x]) * (1f / 16f);
                outRow[x] = Math.clamp(val, 0, MAX_VALUE);
            }
        }
        return output;
    }

    @Override
    public void incrementalAverage(float[][] current, float[][] average, int n) {
        var invN = FloatVector.broadcast(FLOAT_SPECIES, 1.0f / n);
        for (int j = 0; j < current.length; j++) {
            var averageLine = average[j];
            var currentLine = current[j];
            int len = currentLine.length;
            int i = 0;
            for (; i < FLOAT_SPECIES.loopBound(len); i += FLOAT_LEN) {
                var avg = FloatVector.fromArray(FLOAT_SPECIES, averageLine, i);
                var cur = FloatVector.fromArray(FLOAT_SPECIES, currentLine, i);
                // avg + (cur - avg) / n
                avg.add(cur.sub(avg).mul(invN)).intoArray(averageLine, i);
            }
            for (; i < len; i++) {
                averageLine[i] = averageLine[i] + (currentLine[i] - averageLine[i]) / n;
            }
        }
    }
}
