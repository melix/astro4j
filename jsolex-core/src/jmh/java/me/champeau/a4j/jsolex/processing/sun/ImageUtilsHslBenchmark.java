/*
 * Copyright 2026 the original author or authors.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ImageUtilsHslBenchmark {

    @Param({"2048"})
    private int size;

    private float[][][] rgb;
    private float[][][] hsl;
    private float[][][] output;

    @Setup(Level.Trial)
    public void setup() {
        var random = new Random(42);
        rgb = new float[3][size][size];
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    rgb[c][y][x] = random.nextFloat() * 65535f;
                }
            }
        }
        output = new float[3][size][size];
        hsl = ImageUtils.fromRGBtoHSL(rgb);
    }

    @Benchmark
    public float[][][] rgbToHsl() {
        return ImageUtils.fromRGBtoHSL(rgb, output);
    }

    @Benchmark
    public float[][][] hslToRgb() {
        return ImageUtils.fromHSLtoRGB(hsl, output);
    }

    @Benchmark
    public float[][][] rgbToHslSerial() {
        return serialRgbToHsl(rgb, output);
    }

    @Benchmark
    public float[][][] hslToRgbSerial() {
        return serialHslToRgb(hsl, output);
    }

    private static float[][][] serialRgbToHsl(float[][][] rgb, float[][][] output) {
        float[][] rChannel = rgb[0];
        float[][] gChannel = rgb[1];
        float[][] bChannel = rgb[2];
        int height = rChannel.length;
        int width = rChannel[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = rChannel[y][x] / 65535f;
                float g = gChannel[y][x] / 65535f;
                float b = bChannel[y][x] / 65535f;
                float max = Math.max(r, Math.max(g, b));
                float min = Math.min(r, Math.min(g, b));
                float delta = max - min;
                float hue = 0.0f;
                if (delta == 0) {
                    hue = 0.0f;
                } else if (max == r) {
                    hue = ((g - b) / delta) % 6;
                } else if (max == g) {
                    hue = (b - r) / delta + 2;
                } else if (max == b) {
                    hue = (r - g) / delta + 4;
                }
                hue *= 60.0f;
                if (hue < 0) {
                    hue += 360.0f;
                }
                float lightness = (max + min) / 2;
                float saturation;
                if (delta == 0) {
                    saturation = 0;
                } else {
                    saturation = delta / (1 - Math.abs(2 * lightness - 1));
                }
                if (lightness <= 0.0001f) {
                    saturation = 0;
                }
                output[0][y][x] = Math.max(0, Math.min(360, hue));
                output[1][y][x] = Math.max(0, Math.min(saturation, 1.0f));
                output[2][y][x] = Math.max(0, Math.min(lightness, 1.0f));
            }
        }
        return output;
    }

    private static float[][][] serialHslToRgb(float[][][] hsl, float[][][] output) {
        float[][] hChannel = hsl[0];
        float[][] sChannel = hsl[1];
        float[][] lChannel = hsl[2];
        int height = hChannel.length;
        int width = hChannel[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float h = hChannel[y][x];
                float s = sChannel[y][x];
                float l = lChannel[y][x];
                float chroma = (1 - Math.abs(2 * l - 1)) * s;
                float hueSegment = h / 60.0f;
                float k = chroma * (1 - Math.abs((hueSegment % 2) - 1));
                float m = l - chroma / 2;
                float r, g, b;
                if (hueSegment < 1) {
                    r = chroma;
                    g = k;
                    b = 0;
                } else if (hueSegment < 2) {
                    r = k;
                    g = chroma;
                    b = 0;
                } else if (hueSegment < 3) {
                    r = 0;
                    g = chroma;
                    b = k;
                } else if (hueSegment < 4) {
                    r = 0;
                    g = k;
                    b = chroma;
                } else if (hueSegment < 5) {
                    r = k;
                    g = 0;
                    b = chroma;
                } else {
                    r = chroma;
                    g = 0;
                    b = k;
                }
                output[0][y][x] = (r + m) * 65535f;
                output[1][y][x] = (g + m) * 65535f;
                output[2][y][x] = (b + m) * 65535f;
            }
        }
        return output;
    }
}
