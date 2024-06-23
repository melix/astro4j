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

import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final int HISTOGRAM_BINS = 256;
    private static final double TARGET_PEAK = 0.65;
    private static final double LIMB_MARGIN = 1.1;
    private static final double REDUCED_AMPLIFICATION_LIMIT = 1.3;

    public static final double DEFAULT_GAMMA = 1.5;

    private final double gamma;

    private Broadcaster broadcaster;

    public AutohistogramStrategy(double gamma) {
        if (gamma < 1) {
            throw new IllegalArgumentException("Gamma must be greater than 1");
        }
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
            disk = BackgroundRemoval.neutralizeBackground(disk, 4);
            diskData = disk.data();
            // the initial strech is to adjust the global brightness by estimating the brightness of
            // the disk itself
            stats = ImageAnalysis.masked(diskData, width, height, e);
            var cx = e.center().a();
            var cy = e.center().b();
            var semiAxis = e.semiAxis();
            var radius = (semiAxis.a() + semiAxis.b()) / 2;
            var amplificationThreshold = 0.5 * AnalysisUtils.estimateBlackPoint(disk, e);
            if (stats.avg() > stats.max() / 8) {
                float max = 1e-7f;
                for (float v : diskData) {
                    max = Math.max(v, max);
                }
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
                            float corrected = (float) Math.pow((gammaCorrected - amplificationThreshold) / max, Math.max(.5, gamma * (.2 * Math.pow(1 / dist, .5))));
                            float newValue = (corrected * Constants.MAX_PIXEL_VALUE);
                            if (Float.isNaN(newValue)) {
                                newValue = gammaCorrected;
                            }
                            var diff = newValue - gammaCorrected;
                            float limitFactor = 1f;
                            if (dist < LIMB_MARGIN) {
                                var k = (LIMB_MARGIN - dist) / (LIMB_MARGIN - 1);
                                limitFactor = (float) (1f - k);
                            }
                            if (dist > REDUCED_AMPLIFICATION_LIMIT) {
                                limitFactor /= (float) (4 * (1 + (dist - REDUCED_AMPLIFICATION_LIMIT)));
                            }
                            newValue = gammaCorrected + diff * limitFactor;
                            diskData[idx] = newValue;
                        }
                    }
                }
            } else {
                disk = image.copy();
                diskData = disk.data();
                for (int i = 0; i < diskData.length; i++) {
                    float v = diskData[i];
                    diskData[i] = (float) Math.pow(v, 0.8);
                }
                LinearStrechingStrategy.DEFAULT.stretch(disk);
                LOGGER.warn(message("skip.gamma.stretch"));
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
        var clahe = image.copy();
        new ClaheStrategy(8, 64, 1.0).stretch(clahe);
        // combine CLAHE with image
        var claheData = clahe.data();
        for (int i = 0; i < diskData.length; i++) {
            diskData[i] = (float) (0.75 * diskData[i] + 0.25 * claheData[i]);
        }
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
