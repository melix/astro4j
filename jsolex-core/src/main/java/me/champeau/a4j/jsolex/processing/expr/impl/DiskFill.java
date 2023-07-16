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

import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class DiskFill extends AbstractFunctionImpl {
    public DiskFill(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object fill(List<Object> arguments) {
        assertExpectedArgCount(arguments, "disk_fill takes 1, 2 or 3 arguments (image(s), [fillColor], [ellipse])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::fill);
        }
        var img = arguments.get(0);
        var ellipse = getArgument(Ellipse.class, arguments, 2).or(() -> getFromContext(Ellipse.class));
        if (ellipse.isPresent()) {
            var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
            var fill = arguments.size() == 2 ? ((Number) arguments.get(1)).floatValue() : blackpoint;
            if (img instanceof ImageWrapper32 mono) {
                var copy = mono.copy();
                doFill(ellipse.get(), copy.data(), copy.width(), fill);
                return copy;
            } else if (img instanceof ColorizedImageWrapper colorized) {
                var copy = colorized.mono().copy();
                doFill(ellipse.get(), copy.data(), copy.width(), fill);
                return new ColorizedImageWrapper(copy, colorized.converter());
            } else if (img instanceof RGBImage rgb) {
                var r = new float[rgb.r().length];
                System.arraycopy(rgb.r(), 0, r, 0, r.length);
                doFill(ellipse.get(), r, rgb.width(), fill);
                var g = new float[rgb.g().length];
                System.arraycopy(rgb.g(), 0, g, 0, g.length);
                doFill(ellipse.get(), r, rgb.width(), fill);
                var b = new float[rgb.b().length];
                System.arraycopy(rgb.b(), 0, b, 0, b.length);
                doFill(ellipse.get(), b, rgb.width(), fill);
                return new RGBImage(rgb.width(), rgb.height(), r, g, b);
            }
        }
        throw new IllegalArgumentException("Ellipse fitting not found, cannot perform fill");
    }

    private static void doFill(Ellipse ellipse, float[] image, int width, float color) {
        int height = image.length / width;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (ellipse.isWithin(x, y)) {
                    image[x + y * width] = color;
                }
            }
        }
    }
}
