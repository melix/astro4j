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

class FallbackImageMath implements ImageMath {
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
        for (int x = 0; x < width; x++) {
            sum += data[offset + x];
        }
        return sum / width;
    }

    @Override
    public double averageOf(double[] data) {
        double sum = 0;
        int max = data.length;
        for (double datum : data) {
            sum += datum;
        }
        return sum / max;
    }

    @Override
    public void incrementalAverage(float[] current, float[] average, int n) {
        for (int j = 0; j < current.length; j++) {
            average[j] = average[j] + (current[j] - average[j]) / n;
        }
    }
}
