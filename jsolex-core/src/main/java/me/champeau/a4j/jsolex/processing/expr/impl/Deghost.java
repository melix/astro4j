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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;

/**
 * Attenuates a one-sided reflection (ghost) of the solar disk. The reflection is the excess of each pixel over its
 * point reflection through the disk centre (which cancels everything symmetric); smoothing that excess heavily along
 * the limb keeps the broad reflection band while dropping the fine radial detail, so subtracting it removes the
 * reflection and leaves the underlying detail untouched. Everything is derived from the image relative to the disk
 * radius, so nothing is fitted to a particular frame.
 */
public class Deghost extends AbstractFunctionImpl {

    private static final int AZIMUTH_SAMPLES = 720;
    private static final int RADIAL_SAMPLES = 240;
    private static final double SAMPLE_RADIUS = 1.35;      // sample the excess out to this (× R)
    private static final double OUTER_RADIUS = 1.30;       // subtraction taper ends here (× R)
    private static final double OUTER_FEATHER = 0.15;      // taper width (× R)
    private static final double INNER_FEATHER = 0.015;     // limb feather (× R)
    private static final double AZIMUTH_SIGMA_BINS = 30;   // ≈ 15° smoothing along the limb
    private static final double RADIAL_SIGMA_BINS = 3;

    private record Disk(double cx, double cy, double radius) {
    }

    public Deghost(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object deghost(Map<String, Object> arguments) {
        BuiltinFunction.DEGHOST.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("deghost", "img", arguments, this::deghost);
        }
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (!(arg instanceof ImageWrapper32)) {
            throw new IllegalArgumentException("deghost only supports mono images");
        }
        var ellipse = getEllipse(arguments, "ellipse");
        if (ellipse.isEmpty()) {
            throw new IllegalArgumentException("Cannot remove the reflection because the solar disk wasn't found");
        }
        var e = ellipse.get();
        var strength = doubleArg(arguments, "strength", 1.0);
        var iterations = Math.max(1, intArg(arguments, "iterations", 1));
        var debug = booleanArg(arguments, "debug", false);
        return monoToMonoImageTransformer("deghost", "img", arguments, src -> {
            if (src instanceof ImageWrapper32 image) {
                var passes = debug ? 1 : iterations;
                for (int i = 0; i < passes; i++) {
                    apply(image, e, strength, debug);
                }
            } else {
                throw new IllegalArgumentException("deghost only supports mono images");
            }
        });
    }

    private void apply(ImageWrapper32 image, Ellipse ellipse, double strength, boolean debug) {
        if (strength <= 0 && !debug) {
            return;
        }
        var data = image.data();
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        var disk = new Disk(center.a(), center.b(), (semiAxis.a() + semiAxis.b()) / 2.0);
        if (disk.radius() <= 0) {
            return;
        }
        var reflection = estimateReflection(data, disk);

        var height = data.length;
        var width = data[0].length;
        var cx = disk.cx();
        var cy = disk.cy();
        var radius = disk.radius();
        var innerWidth = Math.max(1.0, INNER_FEATHER * radius);
        var outerFeather = Math.max(1.0, OUTER_FEATHER * radius);
        var outerR = OUTER_RADIUS * radius;
        var yFrom = Math.max(0, (int) Math.floor(cy - outerR));
        var yTo = Math.min(height - 1, (int) Math.ceil(cy + outerR));
        var xFrom = Math.max(0, (int) Math.floor(cx - outerR));
        var xTo = Math.min(width - 1, (int) Math.ceil(cx + outerR));
        for (int y = yFrom; y <= yTo; y++) {
            for (int x = xFrom; x <= xTo; x++) {
                var dx = x - cx;
                var dy = y - cy;
                var r = Math.sqrt(dx * dx + dy * dy);
                if (r <= radius || r > outerR) {
                    continue;
                }
                var mask = smoothstep((r - radius) / innerWidth) * smoothstep((outerR - r) / outerFeather);
                if (mask <= 0) {
                    continue;
                }
                var removed = strength * reflection[y][x] * mask;
                if (debug) {
                    data[y][x] = (float) removed;
                } else if (removed > 0) {
                    var value = data[y][x] - removed;
                    data[y][x] = value > 0 ? (float) value : 0f;
                }
            }
        }
    }

    /**
     * Builds the one-sided reflection band: the per-pixel excess over its point reflection through the disk centre,
     * sampled on a polar grid around the disk, smoothed heavily along the limb (which keeps the broad reflection and
     * drops the fine radial structure), then mapped back to image space.
     */
    private float[][] estimateReflection(float[][] data, Disk disk) {
        var height = data.length;
        var width = data[0].length;
        var cx = disk.cx();
        var cy = disk.cy();
        var radius = disk.radius();
        var innerR = radius;
        var outerR = SAMPLE_RADIUS * radius;

        var polar = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var r = innerR + (ir / (double) (RADIAL_SAMPLES - 1)) * (outerR - innerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                var th = -Math.PI + it * (2 * Math.PI / AZIMUTH_SAMPLES);
                var px = cx + r * Math.cos(th);
                var py = cy + r * Math.sin(th);
                var value = ImageMath.bilinear2D(data, px, py, width, height);
                var mirror = ImageMath.bilinear2D(data, 2 * cx - px, 2 * cy - py, width, height);
                polar[ir][it] = Math.max(0, value - mirror);
            }
        }
        smoothAzimuthal(polar, AZIMUTH_SIGMA_BINS);
        smoothRadial(polar, RADIAL_SIGMA_BINS);

        var reflection = new float[height][width];
        var yFrom = Math.max(0, (int) Math.floor(cy - outerR));
        var yTo = Math.min(height - 1, (int) Math.ceil(cy + outerR));
        var xFrom = Math.max(0, (int) Math.floor(cx - outerR));
        var xTo = Math.min(width - 1, (int) Math.ceil(cx + outerR));
        for (int y = yFrom; y <= yTo; y++) {
            for (int x = xFrom; x <= xTo; x++) {
                var dx = x - cx;
                var dy = y - cy;
                var r = Math.sqrt(dx * dx + dy * dy);
                if (r < innerR || r > outerR) {
                    continue;
                }
                var th = Math.atan2(dy, dx);
                var fr = (r - innerR) / (outerR - innerR) * (RADIAL_SAMPLES - 1);
                var ft = (th + Math.PI) / (2 * Math.PI) * AZIMUTH_SAMPLES;
                reflection[y][x] = (float) samplePolar(polar, fr, ft);
            }
        }
        return reflection;
    }

    private static void smoothAzimuthal(double[][] polar, double sigma) {
        var kernel = gaussianKernel(sigma);
        var half = kernel.length / 2;
        var nt = polar[0].length;
        var row = new double[nt];
        for (var radial : polar) {
            System.arraycopy(radial, 0, row, 0, nt);
            for (int it = 0; it < nt; it++) {
                var sum = 0.0;
                for (int k = -half; k <= half; k++) {
                    var idx = ((it + k) % nt + nt) % nt;
                    sum += row[idx] * kernel[k + half];
                }
                radial[it] = sum;
            }
        }
    }

    private static void smoothRadial(double[][] polar, double sigma) {
        var kernel = gaussianKernel(sigma);
        var half = kernel.length / 2;
        var nr = polar.length;
        var nt = polar[0].length;
        var col = new double[nr];
        for (int it = 0; it < nt; it++) {
            for (int ir = 0; ir < nr; ir++) {
                col[ir] = polar[ir][it];
            }
            for (int ir = 0; ir < nr; ir++) {
                var sum = 0.0;
                var weight = 0.0;
                for (int k = -half; k <= half; k++) {
                    var idx = ir + k;
                    if (idx < 0 || idx >= nr) {
                        continue;
                    }
                    sum += col[idx] * kernel[k + half];
                    weight += kernel[k + half];
                }
                polar[ir][it] = sum / weight;
            }
        }
    }

    private static double[] gaussianKernel(double sigma) {
        var radius = Math.max(1, (int) Math.ceil(3 * sigma));
        var kernel = new double[2 * radius + 1];
        var sum = 0.0;
        for (int i = -radius; i <= radius; i++) {
            var v = Math.exp(-(i * i) / (2 * sigma * sigma));
            kernel[i + radius] = v;
            sum += v;
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    private static double samplePolar(double[][] polar, double fr, double ft) {
        var nr = polar.length;
        var nt = polar[0].length;
        fr = Math.max(0, Math.min(nr - 1.0001, fr));
        var r0 = (int) Math.floor(fr);
        var r1 = Math.min(r0 + 1, nr - 1);
        var wr = fr - r0;
        var t0 = ((int) Math.floor(ft) % nt + nt) % nt;
        var t1 = (t0 + 1) % nt;
        var wt = ft - Math.floor(ft);
        var top = polar[r0][t0] * (1 - wt) + polar[r0][t1] * wt;
        var bottom = polar[r1][t0] * (1 - wt) + polar[r1][t1] * wt;
        return top * (1 - wr) + bottom * wr;
    }

    private static double smoothstep(double x) {
        var t = x < 0 ? 0 : (x > 1 ? 1 : x);
        return t * t * (3 - 2 * t);
    }
}
