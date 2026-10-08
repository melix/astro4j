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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Base class of the functions which measure the solar limb to correct the geometry of
 * the disk. It finds the limb along radial lines, fits a circle to it, and resamples
 * images with a displacement grid.
 */
public abstract class AbstractLimbFunction extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLimbFunction.class);
    protected static final int ANGLES = 720;
    protected static final int FIT_PASSES = 3;
    protected static final double OUTLIER_SIGMA = 3;
    protected static final int GRID_STEP = 8;
    private static final double INNER = 0.90;
    private static final double OUTER = 1.12;
    private static final double STEP = 0.5;

    private final EllipseFit ellipseFit;
    private final ImageMath imageMath = ImageMath.newInstance();

    protected AbstractLimbFunction(Map<Class<?>, Object> context, Broadcaster broadcaster, EllipseFit ellipseFit) {
        super(context, broadcaster);
        this.ellipseFit = ellipseFit;
    }

    /**
     * Resamples an image with a Lanczos kernel. The grids give, every {@link #GRID_STEP}
     * pixels, the offset from each output pixel to the point of the source image it takes
     * its value from.
     */
    protected float[][] resample(float[][] data, int width, int height, float[][] gridDx, float[][] gridDy) {
        return imageMath.dedistort(new Image(width, height, data), gridDx, gridDy, GRID_STEP, true).data();
    }

    protected static ImageWrapper32 toMono(ImageWrapper image) {
        if (image instanceof ImageWrapper32 mono) {
            return mono;
        }
        if (image instanceof RGBImage rgb) {
            return rgb.toMono();
        }
        throw new IllegalArgumentException("only mono and RGB images are supported");
    }

    protected record Circle(double cx, double cy, double radius) {
        Ellipse toEllipse() {
            return Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -2 * cx, -2 * cy, cx * cx + cy * cy - radius * radius));
        }
    }

    /**
     * The limb measured on {@link #ANGLES} regularly spaced angles around a fitted circle.
     *
     * @param circle the circle fitted to the limb
     * @param radii the distance of the limb to the center of the circle for each angle, NaN when not measured
     */
    protected record LimbPoints(Circle circle, double[] radii) {
    }

    /**
     * Finds the disk, fits a circle to its limb and measures the limb around that circle.
     */
    protected Optional<LimbPoints> measureLimbPoints(ImageWrapper32 image) {
        var ellipse = findDisk(image);
        if (ellipse.isEmpty()) {
            return Optional.empty();
        }
        var guess = new Circle(ellipse.get().center().a(), ellipse.get().center().b(), (ellipse.get().semiAxis().a() + ellipse.get().semiAxis().b()) / 2);
        var circle = fitCircle(limbPoints(image, guess), guess);
        return Optional.of(new LimbPoints(circle, limbPoints(image, circle)));
    }

    private Optional<Ellipse> findDisk(ImageWrapper32 image) {
        var known = image.findMetadata(Ellipse.class);
        if (known.isPresent()) {
            return known;
        }
        try {
            return ellipseFit.performEllipseFitting(image).findMetadata(Ellipse.class);
        } catch (RuntimeException e) {
            LOGGER.debug("Disk detection failed", e);
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

    protected static double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        var sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    protected static double[] solve(double[][] matrix, double[] rhs) {
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
    }}
