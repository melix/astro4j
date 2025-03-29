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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.expr.impl.Utilities;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutohistogramStrategy.class);

    private static final float BLEND_START = 1.0f;
    private static final float BLEND_END = 1.025f;
    private static final int HISTOGRAM_BINS = 256;
    private static final double TARGET_PEAK = 0.4;

    public static final double DEFAULT_GAMMA = 1.5;

    private final double gamma;

    public AutohistogramStrategy(double gamma) {
        if (gamma < 1) {
            throw new IllegalArgumentException("Gamma must be greater than 1");
        }
        this.gamma = gamma;
    }

    /**
     * Stretches an image using automatic parameters.
     *
     * @param image grayscale image, where each pixel must be in the 0-65535 range.
     */
    @Override
    public void stretch(ImageWrapper32 image) {
        var disk = image.copy();
        var height = image.height();
        var width = image.width();
        var diskData = disk.data();
        var avg = neutralizeBg(disk, 3, 2.5, 0.9f);
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            var cx = e.center().a();
            var cy = e.center().b();
            var semiAxis = e.semiAxis();
            var radius = (semiAxis.a() + semiAxis.b()) / 2;
            float max = 1e-7f;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    max = Math.max(diskData[y][x], max);
                }
            }
            var asinh = new ArcsinhStretchingStrategy(0, 1.5f, 1.5f);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = diskData[y][x];
                    float normalized = v / max;
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    var gammaCorrected = (float) pow(normalized, gamma) * MAX_PIXEL_VALUE;
                    var v1 = clamp(gammaCorrected, 0, MAX_PIXEL_VALUE);

                    if (dist <= BLEND_START) {
                        diskData[y][x] = v1;
                    } else if (dist <= BLEND_END) {
                        // Smooth transition using a cosine blend
                        float alpha = (float) (0.5 * (1 + Math.cos(Math.PI * (dist - BLEND_START) / (BLEND_END - BLEND_START))));
                        float prominenceValue = diskData[y][x] + asinh.stretchPixel(v);
                        diskData[y][x] = alpha * v1 + (1 - alpha) * prominenceValue;
                    } else {
                        diskData[y][x] += asinh.stretchPixel(v);
                    }

                }
            }
            for (int y = 0; y < height; y++) {
                System.arraycopy(diskData[y], 0, image.data()[y], 0, width);
            }
        } else {
            new GammaStrategy(gamma).stretch(disk);
            for (int y = 0; y < height; y++) {
                System.arraycopy(diskData[y], 0, image.data()[y], 0, width);
            }
        }
        diskData = image.data();
        // for histogram transform, we will only consider pixels within 1.2 * radius of the center of the disk
        LoHi lohi;
        if (ellipse.isPresent()) {
            var histo = maskedHistogram(diskData, width, height, ellipse.get());
            lohi = findLoHi(histo);
        } else {
            lohi = findLoHi(diskData);
        }
        new DynamicStretchStrategy(lohi.hi(), TARGET_PEAK).stretch(image);
        mixInClahe(image, diskData);
        alignLeftHistogram(diskData);
        clamping(diskData, 0, .9998).stretch(image);
        RangeExpansionStrategy.DEFAULT.stretch(image);
    }

    private static double neutralizeBg(ImageWrapper32 disk, int degree, double sigma, float smoothing) {
        var diskData = disk.data();
        var model = BackgroundRemoval.backgroundModel(disk, degree, sigma).data();
        double avg = 0;
        for (int y = 0; y < disk.height(); y++) {
            for (int x = 0; x < disk.width(); x++) {
                float v = model[y][x];
                avg += v;
                diskData[y][x] = Math.clamp(diskData[y][x] - smoothing * v, 0, MAX_PIXEL_VALUE);
            }
        }
        avg = avg / (disk.width() * disk.height());
        return avg;
    }

    private static void mixInClahe(ImageWrapper32 image, float[][] diskData) {
        var clahe = image.copy();
        new ClaheStrategy(8, 64, 1.0).stretch(clahe);
        // combine CLAHE with image
        var claheData = clahe.data();
        var width = image.width();
        var height = image.height();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                diskData[y][x] = (float) (0.9 * diskData[y][x] + 0.1 * claheData[y][x]);
            }
        }
    }

    private static void alignLeftHistogram(float[][] diskData) {
        var histo = Histogram.of(diskData, 256);
        var avg = 0;
        for (int i = 0; i < 256; i++) {
            avg += histo.get(i);
        }
        avg = avg / 256;
        float stddev = 0;
        for (int i = 0; i < 256; i++) {
            var v = histo.get(i) - avg;
            stddev += v * v;
        }
        stddev = (float) sqrt(stddev / 255);
        for (int i = 0; i < 256; i++) {
            if (histo.get(i) > 2 * stddev) {
                var limit = 256 * i;
                for (var line : diskData) {
                    for (int k = 0; k < line.length; k++) {
                        float v = line[k];
                        line[k] = Math.max(0, v - limit);
                    }
                }
                break;
            }
        }
    }

    private static ContrastAdjustmentStrategy clamping(float[][] diskData, double loPercentile, double hiPercentile) {
        var cumulative = Histogram.of(diskData, 65536).cumulative();
        var lo = cumulative.percentile(loPercentile);
        var hi = cumulative.percentile(hiPercentile);
        return new ContrastAdjustmentStrategy(lo, hi);
    }

    private static Histogram maskedHistogram(float[][] image, int width, int height, Ellipse ellipse) {
        var builder = Histogram.builder(HISTOGRAM_BINS);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    builder.record(image[y][x]);
                }
            }
        }
        return builder.build();
    }

    private static LoHi findLoHi(float[][] image) {
        var histo = Histogram.of(image, HISTOGRAM_BINS);
        return findLoHi(histo);
    }

    private static LoHi findLoHi(Histogram histo) {
        var values = StretchingUtils.performSmoothing(histo.values());
        int peakIndex = findRightmostPeak(values);
        float lo = 0;
        float hi = peakIndex * HISTOGRAM_BINS;
        double val = values[peakIndex];
        double cutoff = val / 2;
        for (int i = peakIndex + 1; i < HISTOGRAM_BINS; i++) {
            if (values[i] <= cutoff) {
                hi = i * HISTOGRAM_BINS;
                break;
            }
        }

        for (int i = peakIndex - 1; i >= 0; i--) {
            if (values[i] <= cutoff) {
                lo = i * HISTOGRAM_BINS;
                break;
            }
        }
        return new LoHi(lo, hi);
    }

    public static int findRightmostPeak(double[] values) {
        List<Peak> peaks = new ArrayList<>();
        // we intentionally ignore the 4 first bins, assuming they are not relevant
        for (int i = 8; i < values.length - 1; i++) {
            double previous = values[i - 1];
            double value = values[i];
            double next = values[i + 1];
            if (value > previous && value > next) {
                peaks.add(new Peak(i, value));
            }
        }
        // remove peaks which are too low
        var avgPeakValue = peaks.stream().mapToDouble(Peak::value).average().orElse(0);
        peaks.removeIf(p -> p.value < 0.25 * avgPeakValue);
        if (peaks.isEmpty()) {
            return 0;
        }
        return peaks.getLast().index();
    }

    record Peak(int index, double value) {
    }

    private record LoHi(float lo, float hi) {
    }

}
