/*
 * Copyright 2026 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Brings the limb of the solar disk back onto a circle. The limb radius is measured on
 * many angles, a circle is fitted to the measurements, the remaining deviation is
 * modelled by a short Fourier series, and the image is resampled along radial lines so
 * that the deviation vanishes at the limb while fading towards the center of the disk.
 * When a list of images is given, they are all brought onto the same circle, so that
 * the disk does not move from one image to the next. The images are processed one at
 * a time, so that a long series does not have to fit in memory.
 */
public class LimbCorrection extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(LimbCorrection.class);
    private static final int ANGLES = 720;
    private static final double INNER = 0.90;
    private static final double OUTER = 1.12;
    private static final double STEP = 0.5;
    private static final int FIT_PASSES = 3;
    private static final double OUTLIER_SIGMA = 3;
    private static final double MIN_COVERAGE = 0.25;
    private static final double MAX_SCATTER = 0.01;
    private static final int GRID_STEP = 8;

    private final EllipseFit ellipseFit;
    private final ImageMath imageMath = ImageMath.newInstance();

    public LimbCorrection(Map<Class<?>, Object> context, Broadcaster broadcaster, EllipseFit ellipseFit) {
        super(context, broadcaster);
        this.ellipseFit = ellipseFit;
    }

    public Object correctLimb(Map<String, Object> arguments) {
        BuiltinFunction.CORRECT_LIMB.validateArgs(arguments);
        var order = Math.max(0, intArg(arguments, "order", 6));
        var falloff = Math.max(0, doubleArg(arguments, "falloff", 2));
        var arg = arguments.get("img");
        var ref = arguments.get("ref");
        if (arg instanceof List<?> list) {
            var references = ref instanceof List<?> refList ? refList : list;
            if (references.size() != list.size()) {
                throw new IllegalArgumentException("correct_limb: the reference list must have the same size as the image list");
            }
            var limbs = new ArrayList<Limb>(list.size());
            var operation = newOperation("correct_limb");
            for (int i = 0; i < references.size(); i++) {
                limbs.add(measure(toMono(((ImageWrapper) references.get(i)).unwrapToMemory()), order));
                broadcaster.broadcast(operation.update((i + 1) / (2.0 * list.size())));
            }
            var valid = limbs.stream().filter(Limb::valid).toList();
            if (valid.isEmpty()) {
                LOGGER.warn("correct_limb: the limb could not be measured reliably on any image, images left unchanged");
                broadcaster.broadcast(operation.complete());
                return list;
            }
            var reference = new Circle(
                    median(valid.stream().mapToDouble(l -> l.circle().cx()).toArray()),
                    median(valid.stream().mapToDouble(l -> l.circle().cy()).toArray()),
                    median(valid.stream().mapToDouble(l -> l.circle().radius()).toArray())
            );
            var result = new ArrayList<Object>(list.size());
            for (int i = 0; i < list.size(); i++) {
                var image = (ImageWrapper) list.get(i);
                result.add(FileBackedImage.wrap(correct(image.unwrapToMemory(), limbs.get(i), reference, falloff)));
                broadcaster.broadcast(operation.update((list.size() + i + 1) / (2.0 * list.size())));
            }
            broadcaster.broadcast(operation.complete());
            return result;
        }
        if (arg instanceof ImageWrapper wrapper) {
            var image = wrapper.unwrapToMemory();
            var source = ref instanceof ImageWrapper refWrapper ? refWrapper.unwrapToMemory() : image;
            var limb = measure(toMono(source), order);
            return correct(image, limb, limb.circle(), falloff);
        }
        throw new IllegalArgumentException("correct_limb only supports mono and RGB images");
    }

    private static ImageWrapper32 toMono(ImageWrapper image) {
        if (image instanceof ImageWrapper32 mono) {
            return mono;
        }
        if (image instanceof RGBImage rgb) {
            return rgb.toMono();
        }
        throw new IllegalArgumentException("correct_limb only supports mono and RGB images");
    }

    private ImageWrapper correct(ImageWrapper image, Limb limb, Circle reference, double falloff) {
        if (!limb.valid()) {
            LOGGER.warn("correct_limb: the limb could not be measured reliably (coverage {}%, scatter {} px), image left unchanged", Math.round(100 * limb.coverage()), fmt(limb.scatter()));
            return image;
        }
        var width = image.width();
        var height = image.height();
        var metadata = new LinkedHashMap<>(image.metadata());
        metadata.put(Ellipse.class, reference.toEllipse());
        if (image instanceof ImageWrapper32 mono) {
            var corrected = new ImageWrapper32(width, height, warp(mono.data(), width, height, limb, reference, falloff), metadata);
            if (LOGGER.isDebugEnabled()) {
                var check = measure(corrected, limb.order());
                LOGGER.debug("correct_limb: coverage {}%, order {}, limb deviation rms {} px, modelled {} px, residual {} px, after correction {} px, center shift {} px",
                        Math.round(100 * limb.coverage()), limb.order(), fmt(limb.rawRms()), fmt(limb.modelRms()), fmt(limb.residualRms()), fmt(check.rawRms()),
                        fmt(Math.hypot(limb.circle().cx() - reference.cx(), limb.circle().cy() - reference.cy())));
            }
            return corrected;
        }
        var rgb = (RGBImage) image;
        return new RGBImage(width, height,
                warp(rgb.r(), width, height, limb, reference, falloff),
                warp(rgb.g(), width, height, limb, reference, falloff),
                warp(rgb.b(), width, height, limb, reference, falloff),
                metadata);
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    private record Circle(double cx, double cy, double radius) {
        Ellipse toEllipse() {
            return Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -2 * cx, -2 * cy, cx * cx + cy * cy - radius * radius));
        }
    }

    private record Limb(Circle circle, double[] coefficients, int order, double rawRms, double modelRms, double residualRms, double coverage, double scatter, boolean valid) {
        static Limb invalid(Circle circle) {
            return new Limb(circle, new double[1], 0, 0, 0, 0, 0, 0, false);
        }

        double deviation(double angle) {
            var value = coefficients[0];
            for (int m = 1; m <= order; m++) {
                value += coefficients[2 * m - 1] * Math.cos(m * angle) + coefficients[2 * m] * Math.sin(m * angle);
            }
            return value;
        }
    }

    /**
     * Measures the limb around the disk. The ellipse found by the regular disk detection
     * is only a starting point: a circle is fitted to the measured limb points, the limb
     * is measured again around that circle, and the deviations from it are modelled.
     * A partial disk only constrains the shape on the visible arc, so the model is
     * reduced accordingly, and a limb which is not found or too scattered is marked
     * invalid so that the image is left alone.
     */
    private Limb measure(ImageWrapper32 image, int order) {
        var ellipse = findDisk(image);
        if (ellipse.isEmpty()) {
            return Limb.invalid(new Circle(image.width() / 2.0, image.height() / 2.0, 0));
        }
        var guess = new Circle(ellipse.get().center().a(), ellipse.get().center().b(), (ellipse.get().semiAxis().a() + ellipse.get().semiAxis().b()) / 2);
        var circle = fitCircle(limbPoints(image, guess), guess);
        var deviations = limbPoints(image, circle);
        var measured = 0;
        for (int k = 0; k < ANGLES; k++) {
            deviations[k] -= circle.radius();
            if (!Double.isNaN(deviations[k])) {
                measured++;
            }
        }
        if (measured == 0) {
            return Limb.invalid(circle);
        }
        var coverage = (double) measured / ANGLES;
        var effectiveOrder = (int) Math.floor(order * coverage);
        var coefficients = fit(deviations, effectiveOrder);
        var limb = new Limb(circle, coefficients, effectiveOrder, 0, 0, 0, coverage, 0, true);
        double raw = 0, modelled = 0, residual = 0;
        var residuals = new ArrayList<Double>();
        for (int k = 0; k < ANGLES; k++) {
            if (Double.isNaN(deviations[k])) {
                continue;
            }
            var m = limb.deviation(2 * Math.PI * k / ANGLES);
            raw += deviations[k] * deviations[k];
            modelled += m * m;
            residual += (deviations[k] - m) * (deviations[k] - m);
            residuals.add(Math.abs(deviations[k] - m));
        }
        // The scatter of the measurements around the model tells whether the limb was
        // really found: a limb latched onto texture or image borders is pure noise
        var scatter = 1.4826 * median(residuals.stream().mapToDouble(d -> d).toArray());
        var valid = coverage >= MIN_COVERAGE && scatter <= MAX_SCATTER * circle.radius();
        return new Limb(circle, coefficients, effectiveOrder, Math.sqrt(raw / measured), Math.sqrt(modelled / measured), Math.sqrt(residual / measured), coverage, scatter, valid);
    }

    private Optional<Ellipse> findDisk(ImageWrapper32 image) {
        var known = image.findMetadata(Ellipse.class);
        if (known.isPresent()) {
            return known;
        }
        try {
            return ellipseFit.performEllipseFitting(image).findMetadata(Ellipse.class);
        } catch (RuntimeException e) {
            LOGGER.debug("correct_limb: disk detection failed", e);
            return Optional.empty();
        }
    }

    private static double[] limbPoints(ImageWrapper32 image, Circle circle) {
        var radii = new double[ANGLES];
        for (int k = 0; k < ANGLES; k++) {
            radii[k] = limbRadius(image.data(), image.width(), image.height(), circle, 2 * Math.PI * k / ANGLES);
        }
        return radii;
    }

    /**
     * Algebraic least-squares circle fit of the limb points measured around a guess
     * circle, with iterative outlier rejection. The result is in image coordinates.
     */
    private static Circle fitCircle(double[] radii, Circle guess) {
        var valid = new boolean[ANGLES];
        for (int k = 0; k < ANGLES; k++) {
            valid[k] = !Double.isNaN(radii[k]);
        }
        var circle = guess;
        for (int pass = 0; pass < FIT_PASSES; pass++) {
            var normal = new double[3][3];
            var rhs = new double[3];
            var samples = 0;
            for (int k = 0; k < ANGLES; k++) {
                if (!valid[k]) {
                    continue;
                }
                samples++;
                var angle = 2 * Math.PI * k / ANGLES;
                var x = guess.cx() + radii[k] * Math.cos(angle);
                var y = guess.cy() + radii[k] * Math.sin(angle);
                var basis = new double[]{x, y, 1};
                var target = -(x * x + y * y);
                for (int i = 0; i < 3; i++) {
                    rhs[i] += basis[i] * target;
                    for (int j = 0; j < 3; j++) {
                        normal[i][j] += basis[i] * basis[j];
                    }
                }
            }
            if (samples < 12) {
                return guess;
            }
            var solution = solve(normal, rhs);
            var cx = -solution[0] / 2;
            var cy = -solution[1] / 2;
            var square = cx * cx + cy * cy - solution[2];
            if (square <= 0) {
                return guess;
            }
            var radius = Math.sqrt(square);
            circle = new Circle(cx, cy, radius);
            var residuals = new double[ANGLES];
            var absResiduals = new ArrayList<Double>();
            for (int k = 0; k < ANGLES; k++) {
                if (valid[k]) {
                    var angle = 2 * Math.PI * k / ANGLES;
                    var x = guess.cx() + radii[k] * Math.cos(angle);
                    var y = guess.cy() + radii[k] * Math.sin(angle);
                    residuals[k] = Math.hypot(x - cx, y - cy) - radius;
                    absResiduals.add(Math.abs(residuals[k]));
                }
            }
            var sigma = 1.4826 * median(absResiduals.stream().mapToDouble(d -> d).toArray());
            if (sigma <= 0) {
                break;
            }
            for (int k = 0; k < ANGLES; k++) {
                if (valid[k] && Math.abs(residuals[k]) > OUTLIER_SIGMA * sigma) {
                    valid[k] = false;
                }
            }
        }
        return circle;
    }

    /**
     * Finds the limb along a radial line, as the half-level crossing between the disk
     * level just inside the limb and the sky level just outside. Prominences lie beyond
     * the first crossing, so scanning outwards from inside the disk ignores them. Rays
     * which leave the image are not measured. The returned radius is relative to the
     * center of the circle.
     */
    private static double limbRadius(float[][] data, int width, int height, Circle circle, double angle) {
        var cos = Math.cos(angle);
        var sin = Math.sin(angle);
        var radius = circle.radius();
        if (!inside(circle, INNER * radius, cos, sin, width, height) || !inside(circle, OUTER * radius, cos, sin, width, height)) {
            return Double.NaN;
        }
        var count = (int) ((OUTER - INNER) * radius / STEP);
        var profile = new double[count];
        var radii = new double[count];
        for (int i = 0; i < count; i++) {
            radii[i] = INNER * radius + i * STEP;
            profile[i] = sample(data, width, height, circle.cx() + radii[i] * cos, circle.cy() + radii[i] * sin);
        }
        var disk = median(profile, radii, 0.92 * radius, 0.96 * radius);
        var sky = median(profile, radii, 1.06 * radius, 1.10 * radius);
        if (Double.isNaN(disk) || Double.isNaN(sky) || disk <= sky) {
            return Double.NaN;
        }
        var level = (disk + sky) / 2;
        for (int i = 1; i < count; i++) {
            if (radii[i] < 0.95 * radius) {
                continue;
            }
            if (profile[i] < level && profile[i - 1] >= level) {
                var t = (profile[i - 1] - level) / (profile[i - 1] - profile[i]);
                return radii[i - 1] + t * STEP;
            }
        }
        return Double.NaN;
    }

    private static boolean inside(Circle circle, double r, double cos, double sin, int width, int height) {
        var x = circle.cx() + r * cos;
        var y = circle.cy() + r * sin;
        return x >= 0 && y >= 0 && x <= width - 1 && y <= height - 1;
    }

    private static double median(double[] profile, double[] radii, double from, double to) {
        return median(IntStream.range(0, profile.length)
                .filter(i -> radii[i] >= from && radii[i] <= to)
                .mapToDouble(i -> profile[i])
                .filter(v -> !Double.isNaN(v))
                .toArray());
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /**
     * Least-squares Fourier fit of the limb deviations with iterative outlier rejection.
     */
    private static double[] fit(double[] deviations, int order) {
        var terms = 2 * order + 1;
        var valid = new boolean[ANGLES];
        for (int k = 0; k < ANGLES; k++) {
            valid[k] = !Double.isNaN(deviations[k]);
        }
        var coefficients = new double[terms];
        for (int pass = 0; pass < FIT_PASSES; pass++) {
            var normal = new double[terms][terms];
            var rhs = new double[terms];
            var basis = new double[terms];
            var samples = 0;
            for (int k = 0; k < ANGLES; k++) {
                if (!valid[k]) {
                    continue;
                }
                samples++;
                var angle = 2 * Math.PI * k / ANGLES;
                basis[0] = 1;
                for (int m = 1; m <= order; m++) {
                    basis[2 * m - 1] = Math.cos(m * angle);
                    basis[2 * m] = Math.sin(m * angle);
                }
                for (int i = 0; i < terms; i++) {
                    rhs[i] += basis[i] * deviations[k];
                    for (int j = 0; j < terms; j++) {
                        normal[i][j] += basis[i] * basis[j];
                    }
                }
            }
            if (samples < terms * 4) {
                return new double[terms];
            }
            coefficients = solve(normal, rhs);
            var limb = new Limb(null, coefficients, order, 0, 0, 0, 1, 0, true);
            var residuals = new double[ANGLES];
            var absResiduals = new ArrayList<Double>();
            for (int k = 0; k < ANGLES; k++) {
                if (valid[k]) {
                    residuals[k] = deviations[k] - limb.deviation(2 * Math.PI * k / ANGLES);
                    absResiduals.add(Math.abs(residuals[k]));
                }
            }
            var sigma = 1.4826 * median(absResiduals.stream().mapToDouble(d -> d).toArray());
            if (sigma <= 0) {
                break;
            }
            for (int k = 0; k < ANGLES; k++) {
                if (valid[k] && Math.abs(residuals[k]) > OUTLIER_SIGMA * sigma) {
                    valid[k] = false;
                }
            }
        }
        return coefficients;
    }

    private static double[] solve(double[][] matrix, double[] rhs) {
        var n = rhs.length;
        var a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
            a[i][n] = rhs[i];
        }
        for (int col = 0; col < n; col++) {
            var pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            var tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            if (Math.abs(a[col][col]) < 1e-12) {
                return new double[n];
            }
            for (int row = 0; row < n; row++) {
                if (row != col) {
                    var factor = a[row][col] / a[col][col];
                    for (int k = col; k <= n; k++) {
                        a[row][k] -= factor * a[col][k];
                    }
                }
            }
        }
        var solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = a[i][n] / a[i][i];
        }
        return solution;
    }

    /**
     * Resamples the image so that its disk lands on the reference circle: a pixel at
     * radius r from the reference center takes its value, in the source image, at the
     * same angle from the measured center and at radius r + deviation * weight, where
     * the weight is 1 beyond the limb and fades as (r / R)^falloff inside the disk.
     * The displacement is evaluated on a coarse grid and the image is resampled with a
     * Lanczos kernel, which preserves the fine details much better than a bilinear one.
     */
    private float[][] warp(float[][] data, int width, int height, Limb limb, Circle reference, double falloff) {
        var measured = limb.circle();
        var gridWidth = width / GRID_STEP + 2;
        var gridHeight = height / GRID_STEP + 2;
        var gridDx = new float[gridHeight][gridWidth];
        var gridDy = new float[gridHeight][gridWidth];
        IntStream.range(0, gridHeight).parallel().forEach(j -> {
            var y = j * GRID_STEP;
            var dy = y - reference.cy();
            for (int i = 0; i < gridWidth; i++) {
                var x = i * GRID_STEP;
                var dx = x - reference.cx();
                var r = Math.sqrt(dx * dx + dy * dy);
                var angle = Math.atan2(dy, dx);
                var weight = r >= reference.radius() ? 1 : Math.pow(r / reference.radius(), falloff);
                var source = r + (measured.radius() - reference.radius() + limb.deviation(angle)) * weight;
                gridDx[j][i] = (float) (measured.cx() + source * Math.cos(angle) - x);
                gridDy[j][i] = (float) (measured.cy() + source * Math.sin(angle) - y);
            }
        });
        return imageMath.dedistort(new Image(width, height, data), gridDx, gridDy, GRID_STEP, true).data();
    }

    private static double sample(float[][] data, int width, int height, double x, double y) {
        var x0 = (int) Math.floor(x);
        var y0 = (int) Math.floor(y);
        var fx = x - x0;
        var fy = y - y0;
        var x1 = Math.min(width - 1, Math.max(0, x0 + 1));
        var y1 = Math.min(height - 1, Math.max(0, y0 + 1));
        x0 = Math.min(width - 1, Math.max(0, x0));
        y0 = Math.min(height - 1, Math.max(0, y0));
        var top = data[y0][x0] * (1 - fx) + data[y0][x1] * fx;
        var bottom = data[y1][x0] * (1 - fx) + data[y1][x1] * fx;
        return top * (1 - fy) + bottom * fy;
    }
}
