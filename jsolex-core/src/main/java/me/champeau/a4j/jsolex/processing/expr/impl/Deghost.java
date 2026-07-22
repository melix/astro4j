/*
 * Copyright 2023-2026 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.expr.impl.deghost.GhostDetector;
import me.champeau.a4j.jsolex.processing.expr.impl.deghost.GhostShift;
import me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid;
import me.champeau.a4j.jsolex.processing.expr.impl.deghost.RimProfile;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid.AZIMUTH_SAMPLES;
import static me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid.RADIAL_SAMPLES;

/**
 * Attenuates reflections (ghosts) of the solar disk, possibly several at once and on any side. Each reflection is a
 * same-diameter copy of the disk shifted by some offset, which shows up as a crescent between the limb and the rim of
 * the shifted copy. The rim is located by {@link GhostDetector}, the reflection is measured at its rim by
 * {@link RimProfile}, and what is subtracted is the reflection's radial brightness profile, measured as a robust
 * median of the excess over the baseline beyond the rim, so the removal is smooth and the texture of the background
 * is preserved; candidates whose crescent does not consistently carry that profile are discarded, which protects
 * glow and gradients that merely fade with distance. Everything is derived from the image relative to the disk
 * radius, so nothing is fitted to a particular frame.
 */
public class Deghost extends AbstractFunctionImpl {

    private static final double SAMPLE_RADIUS = 1.6;       // sample the annulus out to this (× R)
    private static final double DETECT_AZIMUTH_SIGMA_BINS = 10; // ≈ 5° smoothing of the excess
    private static final double BACKGROUND_PERCENTILE = 0.10;
    private static final double SIGNIFICANCE = 0.01;       // rim step below this fraction of the background means no ghost
    private static final double CONSISTENCY = 0.7;         // fraction of the crescent that must carry the pedestal
    private static final double SMOOTH_SIGMA_BINS = 6;     // smoothing of the brightness field and of the removal map
    private static final double PROFILE_SIGMA_BINS = 4;    // smoothing of the empirical ghost radial profile
    private static final int MAX_GHOSTS = 6;

    private record Disk(double cx, double cy, double radius) {
    }

    private record Reflection(float[][] map, double outerRadius) {
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
        var estimate = estimateReflection(data, disk);
        if (estimate == null) {
            return;
        }
        var reflection = estimate.map();

        var height = data.length;
        var width = data[0].length;
        var cx = disk.cx();
        var cy = disk.cy();
        var radius = disk.radius();
        var outerR = estimate.outerRadius();
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
                var removed = strength * reflection[y][x];
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
     * Detects up to {@link #MAX_GHOSTS} shifted-disk reflections and accumulates their removal amounts on a polar
     * grid around the disk, then maps the result back to image space. Returns {@code null} when no significant
     * reflection is present.
     */
    private Reflection estimateReflection(float[][] data, Disk disk) {
        var height = data.length;
        var width = data[0].length;
        var cx = disk.cx();
        var cy = disk.cy();
        var radius = disk.radius();
        var outerR = SAMPLE_RADIUS * radius;

        var polar = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        var valid = new boolean[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var r = PolarGrid.radiusAt(ir, radius, outerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                var th = PolarGrid.azimuthAt(it);
                var px = cx + r * Math.cos(th);
                var py = cy + r * Math.sin(th);
                if (px >= 0 && px <= width - 1 && py >= 0 && py <= height - 1) {
                    polar[ir][it] = ImageMath.bilinear2D(data, px, py, width, height);
                    valid[ir][it] = true;
                }
            }
        }

        var removal = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        var maxRim = 0.0;
        var found = false;
        var exclusions = new ArrayList<GhostShift>();
        for (int ghost = 0; ghost < MAX_GHOSTS; ghost++) {
            var meanBackground = 0.0;
            var backgroundCount = 0;
            var excess = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
            var sorted = new double[AZIMUTH_SAMPLES];
            for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
                var validCount = 0;
                for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                    if (valid[ir][it]) {
                        sorted[validCount++] = polar[ir][it];
                    }
                }
                if (validCount == 0) {
                    continue;
                }
                Arrays.sort(sorted, 0, validCount);
                var background = sorted[(int) (BACKGROUND_PERCENTILE * (validCount - 1))];
                meanBackground += background;
                backgroundCount++;
                for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                    excess[ir][it] = valid[ir][it] ? Math.max(0, polar[ir][it] - background) : 0;
                }
            }
            if (backgroundCount == 0) {
                break;
            }
            meanBackground /= backgroundCount;
            PolarGrid.smoothAzimuthal(excess, DETECT_AZIMUTH_SIGMA_BINS);

            var smooth = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
            for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
                System.arraycopy(polar[ir], 0, smooth[ir], 0, AZIMUTH_SAMPLES);
            }
            PolarGrid.smoothAzimuthal(smooth, SMOOTH_SIGMA_BINS);
            PolarGrid.smoothRadial(smooth, SMOOTH_SIGMA_BINS);
            var shift = GhostDetector.detect(excess, valid, smooth, exclusions, SIGNIFICANCE * meanBackground, radius, outerR, radius);
            if (shift == null) {
                break;
            }
            exclusions.add(shift);
            if (shift.amount() > 0.95 * GhostDetector.MAX_SHIFT * radius) {
                continue;
            }
            var profile = RimProfile.measure(excess, shift, radius, outerR, radius);
            var alpha = profile.alpha();
            var alphaMax = 0.0;
            for (var a : alpha) {
                alphaMax = Math.max(alphaMax, a);
            }
            if (alphaMax < GhostDetector.EDGE_PEAK_FACTOR * SIGNIFICANCE * meanBackground) {
                continue;
            }
            if (!isConsistentCrescent(excess, shift, profile, alpha, radius, outerR)) {
                continue;
            }
            found = true;
            accumulateRemoval(excess, valid, shift, profile, removal, polar, radius, outerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                maxRim = Math.max(maxRim, shift.rimAt(it, radius));
            }
        }
        if (!found) {
            return null;
        }
        PolarGrid.smoothAzimuthal(removal, SMOOTH_SIGMA_BINS);
        PolarGrid.smoothRadial(removal, SMOOTH_SIGMA_BINS);

        var removalR = Math.min(outerR, maxRim + RimProfile.RIM_FEATHER * radius);
        var reflection = new float[height][width];
        var yFrom = Math.max(0, (int) Math.floor(cy - removalR));
        var yTo = Math.min(height - 1, (int) Math.ceil(cy + removalR));
        var xFrom = Math.max(0, (int) Math.floor(cx - removalR));
        var xTo = Math.min(width - 1, (int) Math.ceil(cx + removalR));
        for (int y = yFrom; y <= yTo; y++) {
            for (int x = xFrom; x <= xTo; x++) {
                var dx = x - cx;
                var dy = y - cy;
                var r = Math.sqrt(dx * dx + dy * dy);
                if (r < radius || r > removalR) {
                    continue;
                }
                var th = Math.atan2(dy, dx);
                var fr = PolarGrid.indexAt(r, radius, outerR);
                var ft = (th + Math.PI) / (2 * Math.PI) * AZIMUTH_SAMPLES;
                reflection[y][x] = (float) PolarGrid.samplePolar(removal, fr, ft);
            }
        }
        return new Reflection(reflection, removalR);
    }

    /**
     * Verifies that most of the crescent actually carries the measured reflection level, which discards candidates
     * whose rim was fitted to unrelated structure.
     */
    private static boolean isConsistentCrescent(double[][] excess, GhostShift shift, RimProfile profile, double[] alpha, double radius, double outerR) {
        var pedestal = new double[1][AZIMUTH_SAMPLES];
        var shelves = new double[RADIAL_SAMPLES];
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            var rim = shift.rimAt(it, radius);
            if (rim <= radius || alpha[it] <= 0) {
                continue;
            }
            var from = (radius + rim) / 2;
            var to = rim - RimProfile.INNER_FEATHER * radius;
            var shelfCount = 0;
            for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
                var r = PolarGrid.radiusAt(ir, radius, outerR);
                if (r > from && r < to) {
                    shelves[shelfCount++] = Math.max(0, excess[ir][it] - profile.baselineAt(it, r));
                }
            }
            if (shelfCount <= 4) {
                continue;
            }
            Arrays.sort(shelves, 0, shelfCount);
            pedestal[0][it] = Math.min(alpha[it], shelves[shelfCount / 2]);
        }
        PolarGrid.smoothAzimuthal(pedestal, RimProfile.ALPHA_SIGMA_BINS);
        var covered = 0L;
        var total = 0L;
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            var rim = shift.rimAt(it, radius);
            if (rim <= radius || pedestal[0][it] <= 0) {
                continue;
            }
            var from = (radius + rim) / 2;
            var to = rim - RimProfile.INNER_FEATHER * radius;
            for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
                var r = PolarGrid.radiusAt(ir, radius, outerR);
                if (r > from && r < to) {
                    total++;
                    if (excess[ir][it] - profile.baselineAt(it, r) >= 0.5 * pedestal[0][it]) {
                        covered++;
                    }
                }
            }
        }
        return total > 0 && covered >= CONSISTENCY * total;
    }

    /**
     * Measures the reflection's radial brightness profile (as a function of the distance to the ghost centre) and
     * subtracts it from the working grid, accumulating the removal map.
     */
    private static void accumulateRemoval(double[][] excess, boolean[][] valid, GhostShift shift, RimProfile profile, double[][] removal, double[][] polar, double radius, double outerR) {
        var gcx = shift.amount() * Math.cos(shift.directionRad());
        var gcy = shift.amount() * Math.sin(shift.directionRad());
        var step = PolarGrid.radialStep(radius, outerR);
        var blur = RimProfile.RIM_FEATHER * radius;
        var dMin = Math.max(step, radius - shift.amount() - blur);
        var dMax = radius + 3 * blur;
        var binCount = Math.max(1, (int) Math.ceil((dMax - dMin) / step) + 1);
        var onSide = new boolean[AZIMUTH_SAMPLES];
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            onSide[it] = shift.rimAt(it, radius) > radius;
        }
        var counts = new int[binCount];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var r = PolarGrid.radiusAt(ir, radius, outerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                if (!valid[ir][it] || !onSide[it]) {
                    continue;
                }
                var bin = ghostBin(r, it, gcx, gcy, dMin, dMax, step);
                if (bin >= 0) {
                    counts[bin]++;
                }
            }
        }
        var bins = new double[binCount][];
        for (int b = 0; b < binCount; b++) {
            bins[b] = new double[counts[b]];
        }
        var fill = new int[binCount];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var r = PolarGrid.radiusAt(ir, radius, outerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                if (!valid[ir][it] || !onSide[it]) {
                    continue;
                }
                var bin = ghostBin(r, it, gcx, gcy, dMin, dMax, step);
                if (bin >= 0) {
                    bins[bin][fill[bin]++] = excess[ir][it] - profile.baselineAt(it, r);
                }
            }
        }
        var ghostProfile = new double[binCount];
        for (int b = 0; b < binCount; b++) {
            if (bins[b].length > 0) {
                Arrays.sort(bins[b]);
                ghostProfile[b] = bins[b][bins[b].length / 2];
            }
        }
        PolarGrid.smoothProfile(ghostProfile, PROFILE_SIGMA_BINS);
        for (int b = 0; b < binCount; b++) {
            ghostProfile[b] = Math.max(0, ghostProfile[b]);
        }
        var innerWidth = RimProfile.INNER_FEATHER * radius;
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var r = PolarGrid.radiusAt(ir, radius, outerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                if (!valid[ir][it] || !onSide[it]) {
                    continue;
                }
                var bin = ghostBin(r, it, gcx, gcy, dMin, dMax, step);
                if (bin < 0 || ghostProfile[bin] <= 0) {
                    continue;
                }
                var fin = PolarGrid.smoothstep((r - radius) / innerWidth);
                var amount = Math.min(ghostProfile[bin] * fin, Math.max(0, excess[ir][it] - profile.baselineAt(it, r)));
                if (amount > 0) {
                    removal[ir][it] += amount;
                    polar[ir][it] = Math.max(0, polar[ir][it] - amount);
                }
            }
        }
    }

    /**
     * Bin index of the distance between the given polar cell and the ghost centre, or -1 when out of range.
     */
    private static int ghostBin(double r, int azimuthBin, double gcx, double gcy, double dMin, double dMax, double step) {
        var th = PolarGrid.azimuthAt(azimuthBin);
        var d = Math.hypot(r * Math.cos(th) - gcx, r * Math.sin(th) - gcy);
        if (d < dMin || d >= dMax) {
            return -1;
        }
        return (int) ((d - dMin) / step);
    }
}
