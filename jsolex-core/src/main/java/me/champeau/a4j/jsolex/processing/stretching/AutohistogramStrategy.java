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

import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final int HISTOGRAM_BINS = 256;
    private static final GammaStrategy PROTUS_STRATEGY = new GammaStrategy(.5);

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
        new GammaStrategy(gamma).stretch(disk);
        var diskData = disk.data();
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            // we have an ellipse, so we'll perform another stretch, then combine pixels from both images
            var height = image.height();
            var width = image.width();
            var mask = createMask(height, width, e);
            var protus = prepareProtusImage(image);
            var protusData = protus.data();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var idx = y * width + x;
                    var weight = mask[idx];
                    diskData[idx] = ((1 - weight) * disk.data()[idx] + weight * protusData[idx]);
                }
            }
            System.arraycopy(disk.data(), 0, image.data(), 0, diskData.length);
        } else {
            System.arraycopy(disk.data(), 0, image.data(), 0, diskData.length);
        }
        diskData = image.data();
        var lohi = findLoHi(diskData);
        new DynamicStretchStrategy(lohi.hi(), .65).stretch(image);
        var clahe = image.copy();
        new ClaheStrategy(8, 64, 1.0).stretch(clahe);
        // combine CLAHE with image
        var claheData = clahe.data();
        for (int i = 0; i < diskData.length; i++) {
            diskData[i] = (float) (0.75 * diskData[i] + 0.25 * claheData[i]);
        }
    }

    private static ImageWrapper32 prepareProtusImage(ImageWrapper32 image) {
        var protus = image.copy();
        var data = protus.data();
        trimLeft(data);
        protus = BackgroundRemoval.neutralizeBackground(protus);
        PROTUS_STRATEGY.stretch(protus);
        // Truncate the histogram on the left to protect shadows
        var histo = Histogram.of(data, 256);
        for (int i = 0; i < 32; i++) {
            if (histo.get(i) == 0) {
                for (int j = i + 1; j < 32; j++) {
                    if (histo.get(j) == 0) {
                        i++;
                    }
                }
                truncateLeft(data, 256f * i);
                break;
            }
        }
        return protus;
    }

    private static void trimLeft(float[] data) {
        float minNonZero = Float.MAX_VALUE;
        for (float v : data) {
            if (v > 0) {
                minNonZero = Math.min(v, minNonZero);
            }
        }
        // normalize so that the minimum is non-zero (which can happen because of artifacts or resizing, ...)
        truncateLeft(data, minNonZero);
    }

    private static void truncateLeft(float[] data, float minNonZero) {
        for (int i = 0; i < data.length; i++) {
            data[i] = Math.max(0, data[i] - minNonZero);
        }
    }

    // computes the distance to the circle center, relative to the radius. A negative value
    // means that the point is inside the circle, a positive value means that the point is outside
    // the circle. The distance is normalized so that it is 0 at the circle border, and 1 at the
    // circle center.
    private static double normalizedDistanceToCenter(double x, double y, double cx, double cy, double radius) {
        double distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
        return (distance - radius) / radius;
    }

    private float[] createMask(int height, int width, Ellipse e) {
        float[] mask = new float[width * height];
        var cx = e.center().a();
        var cy = e.center().b();
        var semiAxis = e.semiAxis();
        var radius = (semiAxis.a() + semiAxis.b()) / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var idx = y * width + x;
                var distance = normalizedDistanceToCenter(x, y, cx, cy, radius);
                var v = 0.5 * (distance < 0.005 ? Math.pow(Math.exp(distance - 0.01), 24) : 1 - distance);
                mask[idx] = (float) (v * v);
            }
        }

        if (broadcaster != null) {
            var t = new float[width * height];
            System.arraycopy(mask, 0, t, 0, mask.length);
            for (int i = 0; i < t.length; i++) {
                t[i] = t[i] * MAX_PIXEL_VALUE;
            }
            broadcaster.broadcast(new ImageGeneratedEvent(new GeneratedImage(GeneratedImageKind.IMAGE_MATH, "Mask", Path.of("/tmp/mask.png"), new ImageWrapper32(width, height, t, Map.of()))));
        }
        return mask;
    }

    private static LoHi findLoHi(float[] image) {
        var histo = Histogram.of(image, HISTOGRAM_BINS);
        var values = StretchingUtils.performSmoothing(histo.values());
        int peakIndex = findRightmostPeak(values);
        float lo = 0;
        float hi = 0;
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
        // we intentionally ignore the 8 first bins, assuming they are not relevant
        for (int i = 8; i < values.length - 1; i++) {
            double previous = values[i - 1];
            double value = values[i];
            double next = values[i + 1];
            if (value > previous && value > next) {
                peaks.add(new Peak(i, value));
            }
        }
        double peakValue = 0;
        int idx = -1;
        for (int i = peaks.size() - 1; i >= 0; i--) {
            var p = peaks.get(i);
            var v = p.value();
            if (v > peakValue) {
                idx = p.index();
                if (peakValue != 0 && v > 100 * peakValue) {
                    break;
                }
            } else if (idx >= 0 && v < 0.25 * peakValue) {
                break;
            }
            peakValue = Math.max(peakValue, v);
        }
        return idx == -1 ? peaks.getFirst().index() : idx;
    }

    record Peak(int index, double value) {
    }

    private record LoHi(float lo, float hi) {
    }

}
