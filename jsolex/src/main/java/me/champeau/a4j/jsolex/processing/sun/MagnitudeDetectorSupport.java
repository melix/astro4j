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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.math.tuples.IntPair;
import me.champeau.a4j.math.fft.FastFourierTransform;

import java.util.Arrays;

import static me.champeau.a4j.math.fft.FFTSupport.nextPowerOf2;

public class MagnitudeDetectorSupport {
    private static final IntPair NOT_FOUND = new IntPair(-1, -1);

    private MagnitudeDetectorSupport() {

    }

    public static double[] computeMagnitudes(int width, float[] buffer) {
        float[] line = new float[nextPowerOf2(width)];
        int padding = (line.length - width) / 2;
        System.arraycopy(buffer, 0, line, padding, width);
        var fft = FastFourierTransform.ofReal(line);
        var im = fft.imaginary();
        var magnitudes = new double[line.length];
        for (int k = 0; k < line.length; k++) {
            double magnitude = Math.sqrt(line[k] * line[k] + im[k] * im[k]);
            magnitudes[k] = magnitude;
        }
        return magnitudes;
    }

    public static double maxMagnitude(int width, float[] buffer) {
        var magnitudes = computeMagnitudes(width, buffer);
        return Arrays.stream(magnitudes).max().orElse(0d);
    }

    public static IntPair findEdges(double[] magnitudes, int width, double sensitivity) {
        double min = Arrays.stream(magnitudes).min().orElse(0d);
        double max = Arrays.stream(magnitudes).max().orElse(0d);
        double amplitude = max - min;
        if (amplitude == 0) {
            return NOT_FOUND;
        }
        double threshold = amplitude / sensitivity;
        int start = -1;
        int end = -1;
        for (int i = 0; i < width; i++) {
            double magnitude = magnitudes[i];
            if (magnitude >= threshold) {
                start = i;
                break;
            }
        }
        for (int i = width - 1; i >= 0; i--) {
            double magnitude = magnitudes[i];
            if (magnitude >= threshold) {
                end = i;
                break;
            }
        }
        return new IntPair(start, end);
    }
}
