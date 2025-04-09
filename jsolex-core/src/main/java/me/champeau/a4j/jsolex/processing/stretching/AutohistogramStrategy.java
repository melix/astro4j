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
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static java.lang.Math.clamp;
import static java.lang.Math.pow;
import static me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast.equalize;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public final class AutohistogramStrategy implements StretchingStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutohistogramStrategy.class);

    private static final float BLEND_START = 1.0f;
    private static final float BLEND_END = 1.05f;

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
        var origAnalysis = ImageAnalysis.of(image, true);
        var disk = image.copy();
        var height = image.height();
        var width = image.width();
        var diskData = disk.data();
        var avg = neutralizeBg(disk, 3, 2.5, 0.9f);
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isPresent()) {
            var e = ellipse.get();
            var blackPoint = (float) AnalysisUtils.estimateBlackPoint(disk, e) * 1.2f;
            var backgroundMask = new boolean[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    backgroundMask[y][x] = diskData[y][x] < blackPoint;
                }
            }
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
            var protus = new float[height][width];
            var asinh = new ArcsinhStretchingStrategy(0, 1.2f, 1.5f);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = diskData[y][x];
                    float normalized = v / max;
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    var gammaCorrected = (float) pow(normalized, gamma) * MAX_PIXEL_VALUE;
                    var v1 = clamp(gammaCorrected, 0, MAX_PIXEL_VALUE);

                    if (dist <= BLEND_START) {
                        diskData[y][x] = v1;
                    } else {
                        protus[y][x] = asinh.stretchPixel(v);
                    }
                }
            }

            var analysis = ImageAnalysis.of(disk, true);
            equalize(disk, analysis, 10000, 8000);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    diskData[y][x] = backgroundMask[y][x] ? blackPoint : diskData[y][x];
                }
            }
            var protusImage = new ImageWrapper32(width, height, protus, Map.of());
            var stats = ImageAnalysis.of(protusImage, false);
            while (stats.avg() / stats.stddev()>0.12) {
                if (neutralizeBg(protusImage, 2, 2.5, 1f) == 0) {
                    break;
                }
                stats = ImageAnalysis.of(protusImage, false);
            }
            clamping(protus, 0, .9998).stretch(protusImage);
            LinearStrechingStrategy.DEFAULT.stretch(protusImage);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var dist = Utilities.normalizedDistanceToCenter(x, y, cx, cy, radius);
                    if (dist >= BLEND_START && dist <= BLEND_END) {
                        // Smooth transition using a cosine blend
                        var alpha = (0.5 * (1 + Math.cos(Math.PI * (dist - BLEND_START) / (BLEND_END - BLEND_START))));
                        var prominenceValue = 0.8f * protus[y][x];
                        diskData[y][x] = (float) (alpha * diskData[y][x] + (1 - alpha) * prominenceValue);
                    } else if (dist > BLEND_END) {
                        diskData[y][x] = 0.8f * protus[y][x];
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
        mixInClahe(image, diskData);
        clamping(diskData, 0, .9998).stretch(image);
        RangeExpansionStrategy.DEFAULT.stretch(image);
    }

    public static double neutralizeBg(ImageWrapper32 disk, int degree, double sigma, float smoothing) {
        var diskData = disk.data();
        var optionalModel = BackgroundRemoval.backgroundModel(disk, degree, sigma);
        if (optionalModel.isPresent()) {
            var data = optionalModel.get().data();
            double avg = 0;
            for (int y = 0; y < disk.height(); y++) {
                for (int x = 0; x < disk.width(); x++) {
                    float v = data[y][x];
                    avg += v;
                    diskData[y][x] = Math.clamp(diskData[y][x] - smoothing * v, 0, MAX_PIXEL_VALUE);
                }
            }
            avg = avg / (disk.width() * disk.height());
            return avg;
        }
        return 0;
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

    private static ContrastAdjustmentStrategy clamping(float[][] diskData, double loPercentile, double hiPercentile) {
        var cumulative = Histogram.of(diskData, 65536).cumulative();
        var lo = cumulative.percentile(loPercentile);
        var hi = cumulative.percentile(hiPercentile);
        return new ContrastAdjustmentStrategy(lo, hi);
    }

}
