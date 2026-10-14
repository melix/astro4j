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
 * radius, so nothing is fitted to a particular frame. When a reference image is given, the reflections are detected on
 * it instead, and each image only measures their profile, so that a series of images removes the same reflections.
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

    private record Polar(double[][] values, boolean[][] valid) {
    }

    private record Excess(double[][] values, double meanBackground) {
    }

    private record Detection(List<GhostShift> reflections, double[][] removal) {
    }

    private record Reference(List<GhostShift> reflections, double radius) {
    }

    public Deghost(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object deghost(Map<String, Object> arguments) {
        BuiltinFunction.DEGHOST.validateArgs(arguments);
        var active = doubleArg(arguments, "strength", 1.0) > 0 || booleanArg(arguments, "debug", false);
        return deghost(arguments, active ? detectReference(arguments.get("ref")) : null);
    }

    private Object deghost(Map<String, Object> arguments, Reference reference) {
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("deghost", "img", arguments, args -> deghost(args, reference));
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
        var disk = diskOf(ellipse.get());
        var strength = doubleArg(arguments, "strength", 1.0);
        var iterations = Math.max(1, intArg(arguments, "iterations", 1));
        var debug = booleanArg(arguments, "debug", false);
        return monoToMonoImageTransformer("deghost", "img", arguments, src -> {
            if (src instanceof ImageWrapper32 image) {
                var passes = debug ? 1 : iterations;
                for (int i = 0; i < passes; i++) {
                    apply(image, disk, reference, strength, debug);
                }
            } else {
                throw new IllegalArgumentException("deghost only supports mono images");
            }
        });
    }

    private static Disk diskOf(Ellipse ellipse) {
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        return new Disk(center.a(), center.b(), (semiAxis.a() + semiAxis.b()) / 2.0);
    }

    /**
     * Detects the reflections once on the reference image, so that every image processed with it removes the same
     * reflections.
     */
    private static Reference detectReference(Object ref) {
        if (ref == null) {
            return null;
        }
        if (ref instanceof FileBackedImage fileBackedImage) {
            ref = fileBackedImage.unwrapToMemory();
        }
        if (!(ref instanceof ImageWrapper32 image)) {
            throw new IllegalArgumentException("deghost only supports a mono reference image");
        }
        var ellipse = image.findMetadata(Ellipse.class);
        if (ellipse.isEmpty()) {
            throw new IllegalArgumentException("Cannot use the reference image because its solar disk wasn't found");
        }
        var disk = diskOf(ellipse.get());
        if (disk.radius() <= 0) {
            return new Reference(List.of(), 1);
        }
        var outerR = SAMPLE_RADIUS * disk.radius();
        var detection = detect(samplePolar(image.data(), disk, outerR), disk.radius(), outerR);
        return new Reference(detection.reflections(), disk.radius());
    }

    private void apply(ImageWrapper32 image, Disk disk, Reference reference, double strength, boolean debug) {
        if (strength <= 0 && !debug) {
            return;
        }
        if (disk.radius() <= 0) {
            return;
        }
        var data = image.data();
        var reflection = estimateReflection(data, disk, reference);
        if (reflection == null) {
            return;
        }

        var height = data.length;
        var width = data[0].length;
        var cx = disk.cx();
        var cy = disk.cy();
        var radius = disk.radius();
        var outerR = SAMPLE_RADIUS * radius;
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
     * Accumulates the removal amounts of the reflections on a polar grid around the disk, then maps the result back
     * to image space. The reflections are detected on the image itself, or taken from the reference and scaled to the
     * radius of the disk. Returns {@code null} when no significant reflection is present.
     */
    private static float[][] estimateReflection(float[][] data, Disk disk, Reference reference) {
        var height = data.length;
        var width = data[0].length;
        var cx = disk.cx();
        var cy = disk.cy();
        var radius = disk.radius();
        var outerR = SAMPLE_RADIUS * radius;

        var polar = samplePolar(data, disk, outerR);
        double[][] removal;
        if (reference == null) {
            removal = detect(polar, radius, outerR).removal();
        } else {
            var scale = radius / reference.radius();
            var reflections = reference.reflections().stream()
                    .map(shift -> new GhostShift(scale * shift.amount(), shift.directionRad()))
                    .toList();
            removal = remove(polar, reflections, radius, outerR);
        }
        if (removal == null) {
            return null;
        }
        PolarGrid.smoothAzimuthal(removal, SMOOTH_SIGMA_BINS);
        PolarGrid.smoothRadial(removal, SMOOTH_SIGMA_BINS);

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
                if (r < radius || r > outerR) {
                    continue;
                }
                var th = Math.atan2(dy, dx);
                var fr = PolarGrid.indexAt(r, radius, outerR);
                var ft = (th + Math.PI) / (2 * Math.PI) * AZIMUTH_SAMPLES;
                reflection[y][x] = (float) PolarGrid.samplePolar(removal, fr, ft);
            }
        }
        return reflection;
    }

    private static Polar samplePolar(float[][] data, Disk disk, double outerR) {
        var height = data.length;
        var width = data[0].length;
        var values = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        var valid = new boolean[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var r = PolarGrid.radiusAt(ir, disk.radius(), outerR);
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                var th = PolarGrid.azimuthAt(it);
                var px = disk.cx() + r * Math.cos(th);
                var py = disk.cy() + r * Math.sin(th);
                if (px >= 0 && px <= width - 1 && py >= 0 && py <= height - 1) {
                    values[ir][it] = ImageMath.bilinear2D(data, px, py, width, height);
                    valid[ir][it] = true;
                }
            }
        }
        return new Polar(values, valid);
    }

    /**
     * Detects up to {@link #MAX_GHOSTS} shifted-disk reflections, keeping those whose crescent carries a consistent
     * profile, and removes each of them from the polar grid before looking for the next one.
     */
    private static Detection detect(Polar polar, double radius, double outerR) {
        var removal = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        var reflections = new ArrayList<GhostShift>();
        var exclusions = new ArrayList<GhostShift>();
        for (int ghost = 0; ghost < MAX_GHOSTS; ghost++) {
            var excess = computeExcess(polar);
            if (excess == null) {
                break;
            }
            var meanBackground = excess.meanBackground();
            var smooth = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
            for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
                System.arraycopy(polar.values()[ir], 0, smooth[ir], 0, AZIMUTH_SAMPLES);
            }
            PolarGrid.smoothAzimuthal(smooth, SMOOTH_SIGMA_BINS);
            PolarGrid.smoothRadial(smooth, SMOOTH_SIGMA_BINS);
            var shift = GhostDetector.detect(excess.values(), polar.valid(), smooth, exclusions, SIGNIFICANCE * meanBackground, radius, outerR, radius);
            if (shift == null) {
                break;
            }
            exclusions.add(shift);
            if (shift.amount() > 0.95 * GhostDetector.MAX_SHIFT * radius) {
                continue;
            }
            var profile = RimProfile.measure(excess.values(), shift, radius, outerR, radius);
            var alpha = profile.alpha();
            var alphaMax = 0.0;
            for (var a : alpha) {
                alphaMax = Math.max(alphaMax, a);
            }
            if (alphaMax < GhostDetector.EDGE_PEAK_FACTOR * SIGNIFICANCE * meanBackground) {
                continue;
            }
            if (!isConsistentCrescent(excess.values(), shift, profile, alpha, radius, outerR)) {
                continue;
            }
            reflections.add(shift);
            accumulateRemoval(excess.values(), polar.valid(), shift, profile, removal, polar.values(), radius, outerR);
        }
        return new Detection(reflections, reflections.isEmpty() ? null : removal);
    }

    /**
     * Removes reflections which are already known, measuring their profile on this grid.
     */
    private static double[][] remove(Polar polar, List<GhostShift> reflections, double radius, double outerR) {
        if (reflections.isEmpty()) {
            return null;
        }
        var removal = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        for (var shift : reflections) {
            var excess = computeExcess(polar);
            if (excess == null) {
                return null;
            }
            var profile = RimProfile.measure(excess.values(), shift, radius, outerR, radius);
            accumulateRemoval(excess.values(), polar.valid(), shift, profile, removal, polar.values(), radius, outerR);
        }
        return removal;
    }

    /**
     * Excess of each sample over the background of its ring, smoothed along the azimuth. Returns {@code null} when no
     * sample is valid.
     */
    private static Excess computeExcess(Polar polar) {
        var values = polar.values();
        var valid = polar.valid();
        var meanBackground = 0.0;
        var backgroundCount = 0;
        var excess = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        var sorted = new double[AZIMUTH_SAMPLES];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            var validCount = 0;
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                if (valid[ir][it]) {
                    sorted[validCount++] = values[ir][it];
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
                excess[ir][it] = valid[ir][it] ? Math.max(0, values[ir][it] - background) : 0;
            }
        }
        if (backgroundCount == 0) {
            return null;
        }
        PolarGrid.smoothAzimuthal(excess, DETECT_AZIMUTH_SIGMA_BINS);
        return new Excess(excess, meanBackground / backgroundCount);
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
