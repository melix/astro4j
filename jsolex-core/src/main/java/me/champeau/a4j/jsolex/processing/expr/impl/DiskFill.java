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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiskFill extends AbstractFunctionImpl {
    public DiskFill(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object fill(List<Object> arguments) {
        assertExpectedArgCount(arguments, "disk_fill takes 1, 2 or 3 arguments (image(s), [fillColor], [ellipse])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("disk_fill", arguments, this::fill);
        }
        var img = arguments.get(0);
        var ellipse = getEllipse(arguments, 2);
        if (ellipse.isPresent()) {
            var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
            var fill = arguments.size() == 2 ? floatArg(arguments, 1) : blackpoint;
            return doFillAnyImageKind(img, ellipse.get(), fill, null);
        }
        throw new IllegalArgumentException("Ellipse fitting not found, cannot perform fill");
    }

    private static Object doFillAnyImageKind(Object img, Ellipse ellipse, float fill, Float outsideFill) {
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            var copy = mono.copy();
            doFill(ellipse, copy.data(), copy.width(), fill, outsideFill);
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var r = new float[rgb.r().length];
            System.arraycopy(rgb.r(), 0, r, 0, r.length);
            doFill(ellipse, r, rgb.width(), fill, outsideFill);
            var g = new float[rgb.g().length];
            System.arraycopy(rgb.g(), 0, g, 0, g.length);
            doFill(ellipse, g, rgb.width(), fill, outsideFill);
            var b = new float[rgb.b().length];
            System.arraycopy(rgb.b(), 0, b, 0, b.length);
            doFill(ellipse, b, rgb.width(), fill, outsideFill);
            return new RGBImage(rgb.width(), rgb.height(), r, g, b, new LinkedHashMap<>(rgb.metadata()));
        }
        return null;
    }

    public Object mask(List<Object> arguments) {
        assertExpectedArgCount(arguments, "disk_mask takes 1 or 2 arguments (image(s), [invert])", 1, 2);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("disk_mask", arguments, this::mask);
        }
        var img = arguments.getFirst();
        if (img instanceof ImageWrapper wrapper) {
            var ellipse = wrapper.findMetadata(Ellipse.class);
            if (ellipse.isPresent()) {
                float insideFill = 1;
                float outsideFill = 0;
                if (arguments.size() == 2) {
                    int mode = intArg(arguments, 1);
                    if (mode == 1) {
                        insideFill = 0;
                        outsideFill = 1;
                    }
                }
                return doFillAnyImageKind(img, ellipse.get(), insideFill, outsideFill);
            } else {
                throw new IllegalStateException("Ellipse not found");
            }
        } else {
            throw new IllegalStateException("Unsupported image kind: " + img.getClass().getSimpleName());
        }
    }

    public static void doFill(Ellipse ellipse, float[] image, int width, float color, Float outsideColor) {
        int height = image.length / width;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (ellipse.isWithin(x, y)) {
                    image[x + y * width] = color;
                } else if (outsideColor != null) {
                    image[x + y * width] = outsideColor;
                }
            }
        }
    }
}
