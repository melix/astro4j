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

    public Object fill(Map<String ,Object> arguments) {
        BuiltinFunction.DISK_FILL.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("disk_fill", "img", arguments, this::fill);
        }
        var img = arguments.get("img");
        var ellipse = getEllipse(arguments, "ellipse");
        if (ellipse.isPresent()) {
            var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
            var fill = floatArg(arguments, "color", blackpoint);
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
            doFill(ellipse, copy.data(), fill, outsideFill);
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var r = ImageWrapper.copyData(rgb.r());
            doFill(ellipse, r, fill, outsideFill);
            var g = ImageWrapper.copyData(rgb.g());
            doFill(ellipse, g, fill, outsideFill);
            var b = ImageWrapper.copyData(rgb.b());
            doFill(ellipse, b, fill, outsideFill);
            return new RGBImage(rgb.width(), rgb.height(), r, g, b, new LinkedHashMap<>(rgb.metadata()));
        }
        return null;
    }

    public Object mask(Map<String ,Object> arguments) {
        BuiltinFunction.DISK_MASK.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("disk_mask", "img", arguments, this::mask);
        }
        var img = arguments.get("img");
        if (img instanceof ImageWrapper wrapper) {
            var ellipse = wrapper.findMetadata(Ellipse.class);
            if (ellipse.isPresent()) {
                float insideFill = 1;
                float outsideFill = 0;
                if (arguments.size() == 2) {
                    int mode = intArg(arguments, "invert", 0);
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

    public static void doFill(Ellipse ellipse, float[][] image, float color, Float outsideColor) {
        for (int y = 0; y < image.length; y++) {
            var line = image[y];
            for (int x = 0; x < line.length; x++) {
                if (ellipse.isWithin(x, y)) {
                    image[y][x] = color;
                } else if (outsideColor != null) {
                    image[y][x] = outsideColor;
                }
            }
        }
    }
}
