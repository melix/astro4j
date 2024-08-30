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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.tanh;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final int HISTOGRAM_BINS = 256;
    private static final double TARGET_PEAK = 0.4;
    private static final double LIMB_MARGIN = 1.1;
    private static final double REDUCED_AMPLIFICATION_LIMIT = 1.3;
    private static final float ASINH_FACTOR = 2.5f;

    public static final double DEFAULT_AMPLIFICATION_THRESHOLD = 0.97;
    public static final double DEFAULT_GAMMA = 1.5;


    private final double gamma;
    private final double amplificationThreshold;

    private Broadcaster broadcaster;

    public AutohistogramStrategy(double gamma) {
        this(gamma, DEFAULT_AMPLIFICATION_THRESHOLD);
    }

    public AutohistogramStrategy(double gamma, double amplificationThreshold) {
        if (gamma < 1) {
            throw new IllegalArgumentException("Gamma must be greater than 1");
        }
        if (amplificationThreshold < 0 || amplificationThreshold > 1) {
            throw new IllegalArgumentException("Amplification threshold must be between 0 and 1");
        }
        this.amplificationThreshold = amplificationThreshold;
        this.gamma = gamma;
    }

    public void setBroadcaster(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * Stretches an image using automatic parameters.
     *
     * @param image grayscale image, where each pixel must be in the 0-65535 range.
     */
    @Override
    public void stretch(ImageWrapper32 image) {
        var disk = image.copy();
        var diskData = disk.data();
        // Neutralize offset
        var stats = ImageAnalysis.of(diskData);
        var min = stats.min();
        for (int j = 0; j < diskData.length; j++) {
            diskData[j] -= min;
        }
        var ellipse = image.findMetadata(Ellipse.class);
        var height = image.height();
        var width = image.width();
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            diskData = disk.data();
            var cx = e.center().a();
            var cy = e.center().b();
            var semiAxis = e.semiAxis();
            var radius = (semiAxis.a() + semiAxis.b()) / 2;
            var amplificationThreshold = findAmplificationThreshold(diskData, width, height, e);
            float max = 1e-7f;
            for (float v : diskData) {
                max = Math.max(v, max);
            }
            var asinhs = new ArcsinhStretchingStrategy(0, ASINH_FACTOR, ASINH_FACTOR);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var idx = y * width + x;
                    var v = diskData[idx];
                    float normalized = v / max;
                    var dist = normalizedDistanceToCenter(x, y, cx, cy, radius);
                    var gammaCorrected = (float) Math.pow(normalized, gamma) * Constants.MAX_PIXEL_VALUE;
                    if (dist <= 1 || v < amplificationThreshold) {
                        diskData[idx] = gammaCorrected;
                    } else {
                        var f = 8 * Math.pow(gamma, 2) * tanh(dist - 1);
                        var newValue = (float) (gammaCorrected + f * asinhs.stretchPixel(gammaCorrected));
                        diskData[idx] = Math.clamp(newValue, 0, Constants.MAX_PIXEL_VALUE);
                    }
                }
            }
            System.arraycopy(diskData, 0, image.data(), 0, diskData.length);
        } else {
            new GammaStrategy(gamma).stretch(disk);
            System.arraycopy(diskData, 0, image.data(), 0, diskData.length);
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
        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

    private static void mixInClahe(ImageWrapper32 image, float[] diskData) {
        var clahe = image.copy();
        new ClaheStrategy(8, 64, 1.0).stretch(clahe);
        // combine CLAHE with image
        var claheData = clahe.data();
        for (int i = 0; i < diskData.length; i++) {
            diskData[i] = (float) (0.75 * diskData[i] + 0.25 * claheData[i]);
        }
    }

    private static void alignLeftHistogram(float[] diskData) {
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
        stddev = (float) Math.sqrt(stddev / 255);
        for (int i = 0; i < 256; i++) {
            if (histo.get(i) > 2 * stddev) {
                var limit = 256 * i;
                for (int j = 0; j < diskData.length; j++) {
                    diskData[j] = Math.max(0, diskData[j] - limit);
                }
                break;
            }
        }
    }

    private static ContrastAdjustmentStrategy clamping(float[] diskData, double loPercentile, double hiPercentile) {
        var cumulative = Histogram.of(diskData, 65536).cumulative();
        var lo = cumulative.percentile(loPercentile);
        var hi = cumulative.percentile(hiPercentile);
        return new ContrastAdjustmentStrategy(lo, hi);
    }

    private double findAmplificationThreshold(float[] diskData, int width, int height, Ellipse e) {
        var builder = Histogram.builder(65536);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!e.isWithin(x, y)) {
                    builder.record(diskData[y * width + x]);
                }
            }
        }
        var h = builder.build().cumulative();
        return h.percentile(amplificationThreshold);
    }

    // computes the distance to the circle center, relative to the radius. A negative value
    // means that the point is inside the circle, a positive value means that the point is outside
    // the circle. The distance is normalized so that it is 0 at the circle border, and 1 at the
    // circle center.
    private static double normalizedDistanceToCenter(double x, double y, double cx, double cy, double radius) {
        var dx = x - cx;
        var dy = y - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance / radius;
    }

    private static Histogram maskedHistogram(float[] image, int width, int height, Ellipse ellipse) {
        var builder = Histogram.builder(HISTOGRAM_BINS);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    builder.record(image[y * width + x]);
                }
            }
        }
        return builder.build();
    }

    private static LoHi findLoHi(float[] image) {
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
