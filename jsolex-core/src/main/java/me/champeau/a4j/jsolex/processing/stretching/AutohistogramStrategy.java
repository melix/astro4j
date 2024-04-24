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
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.image.BlurKernel;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.List;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final int HISTOGRAM_BINS = 256;
    private static final GammaStrategy PROTUS_STRATEGY = new GammaStrategy(.7);

    public static final double DEFAULT_GAMMA = 1.5;

    private final double gamma;
    private final ImageMath imageMath = ImageMath.newInstance();

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
                    var weight = Math.pow(mask[idx], gamma);
                    diskData[idx] = (float) ((1 - weight) * disk.data()[idx] + weight * protusData[idx]);
                }
            }
            System.arraycopy(disk.data(), 0, image.data(), 0, diskData.length);
        } else {
            System.arraycopy(disk.data(), 0, image.data(), 0, diskData.length);
        }
        diskData = image.data();
        var lohi = findLoHi(diskData);
        new DynamicStretchStrategy(lohi.hi(), .6).stretch(image);
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

    private float[] createMask(int height, int width, Ellipse e) {
        float[] mask = new float[width * height];
        var cx = e.center().a();
        var cy = e.center().b();
        double min = Double.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var idx = y * width + x;
                if (!e.isWithin(x, y)) {
                    var distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                    mask[idx] = (float) distance;
                    min = Math.min(min, distance);
                }
            }
        }
        for (int i = 0; i < mask.length; i++) {
            var v = mask[i];
            if (v > 0) {
                double d = (v - min) / min;
                if (d < 0.2) {
                    mask[i] = 1;
                } else {
                    mask[i] = 0;
                }
            }
        }
        var tmp = new Image(width, height, mask);
        tmp = imageMath.rescale(tmp, width / 16, height / 16);
        tmp = imageMath.convolve(tmp, BlurKernel.of(8));
        mask = imageMath.rescale(tmp, width, height).data();
        for (int i = 0; i < mask.length; i++) {
            var v = mask[i];
            mask[i] = (float) Math.pow(v, 2);
        }
        return mask;
    }

    private static LoHi findLoHi(float[] image) {
        var histo = Histogram.of(image, HISTOGRAM_BINS);
        var values = performSmoothing(histo.values());
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

    private static double[] performSmoothing(int[] values) {
        double[] result = new double[values.length];
        double max = 0;
        result[0] = (2 * values[0] + values[1]) / 3d;
        for (int i = 1; i < values.length - 1; i++) {
            result[i] = (values[i - 1] + 2 * values[i] + values[i + 1]) / 4d;
            max = Math.max(max, result[i]);
        }
        result[values.length - 1] = (2 * values[values.length - 1] + values[values.length - 2]) / 3d;
        return result;
    }

    public static int findRightmostPeak(double[] values) {
        List<Peak> peaks = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < values.length - 1; i++) {
            double previous = i == 0 ? 0 : values[i - 1];
            double value = values[i];
            double next = values[i + 1];
            if (value > previous && value > next) {
                if (first) {
                    // Skip the first peak, which is usually the background
                    first = false;
                    //continue;
                }
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
