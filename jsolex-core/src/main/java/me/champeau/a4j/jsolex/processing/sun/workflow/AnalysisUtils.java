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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

/**
 * Utility class for image analysis operations.
 */
public class AnalysisUtils {
    private static final int STRIP_PIXELS = 1 << 18;

    private AnalysisUtils() {
    }

    /**
     * Estimates the black point value from pixels outside the solar disk.
     *
     * @param image the image to analyze
     * @param ellipse the solar disk ellipse
     * @return the estimated black point
     */
    public static double estimateBlackPoint(ImageWrapper32 image, Ellipse ellipse) {
        var width = image.width();
        var height = image.height();
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var mean = new RunningMean();
        forEachBackgroundPixel(image.pixels()::getRow, width, height, ellipse, (x, y, v) -> {
            var offcenter = 2 * Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (width + height);
            mean.add(offcenter * v);
        });
        return mean.result();
    }

    /**
     * Estimates the background level from pixels outside the solar disk.
     *
     * @param image the image to analyze
     * @param ellipse the solar disk ellipse
     * @return the estimated background level
     */
    public static double estimateBackground(ImageWrapper32 image, Ellipse ellipse) {
        return estimateBackground(image.pixels()::getRow, image.width(), image.height(), ellipse);
    }

    /**
     * Estimates the background level of pixels outside the solar disk.
     *
     * @param buffer  the pixels, indexed by row then column
     * @param ellipse the solar disk ellipse
     * @return the estimated background level
     */
    public static double estimateBackground(float[][] buffer, Ellipse ellipse) {
        var height = buffer.length;
        var width = height == 0 ? 0 : buffer[0].length;
        return estimateBackground((y, x, target, offset, length) -> System.arraycopy(buffer[y], x, target, offset, length), width, height, ellipse);
    }

    private static double estimateBackground(RowReader rows, int width, int height, Ellipse ellipse) {
        var mean = new RunningMean();
        forEachBackgroundPixel(rows, width, height, ellipse, (x, y, v) -> mean.add(v));
        return mean.result();
    }

    private static void forEachBackgroundPixel(RowReader rows, int width, int height, Ellipse ellipse, PixelConsumer consumer) {
        if (width == 0 || height == 0) {
            return;
        }
        var stripWidth = Math.clamp(STRIP_PIXELS / height, 1, width);
        var strip = new float[stripWidth * height];
        for (int x0 = 0; x0 < width; x0 += stripWidth) {
            var w = Math.min(stripWidth, width - x0);
            for (int y = 0; y < height; y++) {
                rows.read(y, x0, strip, y * w, w);
            }
            for (int dx = 0; dx < w; dx++) {
                var x = x0 + dx;
                for (int y = 0; y < height; y++) {
                    if (!ellipse.isWithin(x, y)) {
                        var v = strip[y * w + dx];
                        if (v > 0 && Float.isFinite(v)) {
                            consumer.accept(x, y, v);
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface RowReader {
        void read(int y, int x, float[] target, int offset, int length);
    }

    @FunctionalInterface
    private interface PixelConsumer {
        void accept(int x, int y, float v);
    }

    private static final class RunningMean {
        private double value = Double.MAX_VALUE;
        private int count;

        void add(double v) {
            value = value + (v - value) / (++count);
        }

        double result() {
            return count == 0 ? 0 : value;
        }
    }

    /**
     * Estimates the signal level using histogram analysis with default bins.
     *
     * @param data the image data
     * @return the estimated signal level
     */
    public static float estimateSignalLevel(float[][] data) {
        return estimateSignalLevel(data, 32);
    }

    /**
     * Estimates the background level using histogram analysis with default bins.
     *
     * @param data the image data
     * @return the estimated background level
     */
    public static float estimateBackgroundLevel(float[][] data) {
        return estimateBackgroundLevel(data, 64);
    }

    /**
     * Estimates the background level using histogram analysis.
     *
     * @param data the image data
     * @param bins the number of histogram bins
     * @return the estimated background level
     */
    public static float estimateBackgroundLevel(float[][] data, int bins) {
        var h = Histogram.of(data, bins);
        var values = h.values();
        float cur = values[0];
        int idx = 0;
        for (int i = 1; i < values.length; i++) {
            idx = i + 1;
            float previous = cur;
            cur = values[i];
            if (cur < 0.5 * previous) {
                break;
            }
        }
        while (idx + 1 < values.length && values[idx + 1] <= cur) {
            idx++;
            cur = values[idx];
        }
        return (65535f * idx) / bins;
    }

    /**
     * Estimates the signal level using histogram analysis.
     *
     * @param data the image data
     * @param bins the number of histogram bins
     * @return the estimated signal level
     */
    public static float estimateSignalLevel(float[][] data, int bins) {
        var h = Histogram.of(data, bins);
        var values = h.values();
        float cur = values[0];
        // First, identify the background level
        int idx = 0;
        for (int i = 1; i < values.length; i++) {
            idx++;
            float previous = cur;
            cur = values[i];
            if (cur < 0.5 * previous) {
                break;
            }
        }
        // Then find the actual signal
        for (int i = idx; i < values.length; i++) {
            idx++;
            float previous = cur;
            cur = values[i];
            if (cur > 1.1 * previous) {
                break;
            }
        }
        return 0.8f * (65536f * idx / h.levelsCount());
    }
}
