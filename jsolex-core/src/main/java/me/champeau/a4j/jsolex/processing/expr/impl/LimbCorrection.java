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
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class LimbCorrection extends AbstractLimbFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(LimbCorrection.class);
    private static final double MIN_COVERAGE = 0.25;
    private static final double MAX_SCATTER = 0.01;

    public LimbCorrection(Map<Class<?>, Object> context, Broadcaster broadcaster, EllipseFit ellipseFit) {
        super(context, broadcaster, ellipseFit);
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
        var points = measureLimbPoints(image);
        if (points.isEmpty()) {
            return Limb.invalid(new Circle(image.width() / 2.0, image.height() / 2.0, 0));
        }
        var circle = points.get().circle();
        var deviations = points.get().radii();
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
        return resample(data, width, height, gridDx, gridDy);
    }


}
