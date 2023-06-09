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

import me.champeau.a4j.jsolex.processing.sun.crop.Cropper;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class Crop extends AbstractFunctionImpl {
    public Crop(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object crop(List<Object> arguments) {
        if (arguments.size() != 5) {
            throw new IllegalArgumentException("crop takes 5 arguments (image(s), left, top, width, height)");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::crop);
        }
        var img = arguments.get(0);
        var left = ((Number) arguments.get(1)).intValue();
        var top = ((Number) arguments.get(2)).intValue();
        var width = ((Number) arguments.get(3)).intValue();
        var height = ((Number) arguments.get(4)).intValue();
        if (left < 0 || top < 0) {
            throw new IllegalArgumentException("top and left values must be >=0");
        }
        if (img instanceof ImageWrapper32 mono) {
            return cropMonoImage(left, top, width, height, mono);
        } else if (img instanceof ColorizedImageWrapper colorized) {
            var mono = colorized.mono();
            return new ColorizedImageWrapper(cropMonoImage(left, top, width, height, mono), colorized.converter());
        } else if (img instanceof RGBImage rgb) {
            return new RGBImage(width, height,
                    cropMonoImage(left, top, width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.r())).data(),
                    cropMonoImage(left, top, width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.g())).data(),
                    cropMonoImage(left, top, width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.b())).data()
            );
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    private static ImageWrapper32 cropMonoImage(int left, int top, int width, int height, ImageWrapper32 mono) {
        if (left + width > mono.width()) {
            throw new IllegalArgumentException("Crop width too large");
        }
        if (top + height > mono.height()) {
            throw new IllegalArgumentException("Crop height too large");
        }
        float[] cropped = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(mono.data(), left + (y + top) * mono.width(), cropped, y * width, width);
        }
        return new ImageWrapper32(width, height, cropped);
    }

    public Object autocrop(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("autocrop takes 1 arguments (image(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::autocrop);
        }
        return doAutocrop(arg, null, null);
    }

    public Object autocrop2(List<Object> arguments) {
        if (arguments.size() != 2 && arguments.size() != 3) {
            throw new IllegalArgumentException("autocrop2 takes 2 or 3 arguments (image(s), factor, rounding)");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::autocrop2);
        }
        var factor = ((Number) arguments.get(1)).doubleValue();
        int rounding = 16;
        if (arguments.size() == 3) {
            rounding = ((Number) arguments.get(2)).intValue();
            if (rounding % 2 == 1) {
                throw new IllegalArgumentException("Rounding must be a factor of 2");
            }
        }
        return doAutocrop(arg, factor, rounding);
    }

    private Object doAutocrop(Object arg, Double diameterFactor, Integer rounding) {
        var ellipse = getFromContext(Ellipse.class);
        var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
        if (ellipse.isPresent()) {
            var circle = ellipse.get();
            if (arg instanceof ImageWrapper32 mono) {
                var image = mono.asImage();
                var cropped = Cropper.cropToSquare(image, circle, blackpoint, diameterFactor, rounding);
                return ImageWrapper32.fromImage(cropped);
            } else if (arg instanceof ColorizedImageWrapper wrapper) {
                var mono = wrapper.mono();
                var cropped = Cropper.cropToSquare(mono.asImage(), circle, blackpoint, diameterFactor, rounding);
                return new ColorizedImageWrapper(ImageWrapper32.fromImage(cropped), wrapper.converter());
            }
            throw new IllegalStateException("Unsupported image type");
        } else {
            return arg;
        }
    }
}
