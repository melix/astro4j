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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.util.Histogram;

import java.util.HashSet;

/**
 * Represents the statistical analysis of an image.
 *
 * @param avg the average value
 * @param stddev the standard deviation
 * @param min the minimum value
 * @param max the maximum value
 * @param distinctValues the number of distinct values
 */
public record ImageAnalysis(float avg, float stddev, float min, float max, int distinctValues, Histogram histogram) {

    public static ImageAnalysis of(float[] array) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0.0f;
        var builder = Histogram.builder(65536);
        var distinctValues = new HashSet<Float>();

        int n = array.length;
        for (float v : array) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            distinctValues.add(v);
            builder.record(v);
        }
        float average = sum / n;
        float stddev = 0;
        for (float v : array) {
            stddev += (v - average) * (v - average);
        }
        stddev = (float) Math.sqrt(stddev / (n - 1));
        return new ImageAnalysis(average, stddev, min, max, distinctValues.size(), builder.build());
    }
}
