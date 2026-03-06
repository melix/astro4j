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
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.matrix.DoubleMatrix;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PolyFit2D extends AbstractFunctionImpl {
    public PolyFit2D(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object polyFit2D(Map<String, Object> arguments) {
        BuiltinFunction.POLY_FIT_2D.validateArgs(arguments);
        var arg = arguments.get("image");
        if (arg instanceof List) {
            return expandToImageList("poly_fit_2d", "image", arguments, this::polyFit2D);
        }
        if (arg instanceof FileBackedImage fbi) {
            arg = fbi.unwrapToMemory();
        }
        if (arg instanceof RGBImage) {
            throw new IllegalArgumentException("poly_fit_2d does not support RGB images");
        }
        if (!(arg instanceof ImageWrapper32 img)) {
            throw new IllegalArgumentException("poly_fit_2d requires a mono image");
        }
        var degree = intArg(arguments, "degree", 3);
        return fitSurface(img, degree);
    }

    private ImageWrapper32 fitSurface(ImageWrapper32 img, int degree) {
        var ellipseOpt = img.findMetadata(Ellipse.class);
        if (ellipseOpt.isEmpty()) {
            ellipseOpt = getFromContext(Ellipse.class);
        }
        if (ellipseOpt.isEmpty()) {
            throw new IllegalArgumentException("poly_fit_2d requires an ellipse in image metadata or context");
        }
        var ellipse = ellipseOpt.get();
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var semiAxis = ellipse.semiAxis();
        var radius = (semiAxis.a() + semiAxis.b()) / 2.0;
        if (radius <= 0) {
            throw new IllegalArgumentException("poly_fit_2d: invalid ellipse radius");
        }

        var width = img.width();
        var height = img.height();
        var data = img.data();
        var polyTerms = (degree + 1) * (degree + 2) / 2;

        var xtx = new double[polyTerms][polyTerms];
        var xty = new double[polyTerms];
        var fitRadius = 0.97 * radius;
        var fitRadius2 = fitRadius * fitRadius;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var px = x - cx;
                var py = y - cy;
                if (px * px + py * py >= fitRadius2) {
                    continue;
                }
                var dx = px / radius;
                var dy = py / radius;
                var value = data[y][x];
                var terms = polyTerms2D(dx, dy, degree, polyTerms);
                for (int i = 0; i < polyTerms; i++) {
                    xty[i] += terms[i] * value;
                    for (int j = i; j < polyTerms; j++) {
                        xtx[i][j] += terms[i] * terms[j];
                    }
                }
            }
        }
        for (int i = 1; i < polyTerms; i++) {
            for (int j = 0; j < i; j++) {
                xtx[i][j] = xtx[j][i];
            }
        }

        double[] coeffs;
        try {
            var xtyMatrix = new double[polyTerms][1];
            for (int i = 0; i < polyTerms; i++) {
                xtyMatrix[i][0] = xty[i];
            }
            var result = DoubleMatrix.of(xtx).inverse().mul(DoubleMatrix.of(xtyMatrix)).asArray();
            coeffs = new double[polyTerms];
            for (int i = 0; i < polyTerms; i++) {
                coeffs[i] = result[i][0];
            }
        } catch (Exception e) {
            throw new IllegalStateException("poly_fit_2d: failed to solve normal equations", e);
        }

        var radius2 = radius * radius;
        var surface = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var px = x - cx;
                var py = y - cy;
                if (px * px + py * py >= radius2) {
                    continue;
                }
                var dx = px / radius;
                var dy = py / radius;
                var terms = polyTerms2D(dx, dy, degree, polyTerms);
                double val = 0;
                for (int i = 0; i < polyTerms; i++) {
                    val += coeffs[i] * terms[i];
                }
                surface[y][x] = (float) val;
            }
        }
        return new ImageWrapper32(width, height, surface, new LinkedHashMap<>(img.metadata()));
    }

    private static double[] polyTerms2D(double dx, double dy, int degree, int numTerms) {
        var terms = new double[numTerms];
        int idx = 0;
        for (int d = 0; d <= degree; d++) {
            for (int px = d; px >= 0; px--) {
                int py = d - px;
                terms[idx++] = Math.pow(dx, px) * Math.pow(dy, py);
            }
        }
        return terms;
    }
}
