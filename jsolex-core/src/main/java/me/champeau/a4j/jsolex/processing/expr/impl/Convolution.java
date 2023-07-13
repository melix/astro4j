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

import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel;
import me.champeau.a4j.math.image.Kernel33;

import java.util.List;
import java.util.Map;

public class Convolution extends AbstractFunctionImpl {
    private final ImageMath imageMath = ImageMath.newInstance();

    public Convolution(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object sharpen(List<Object> arguments) {
        return applyConvolution(arguments, Kernel33.SHARPEN, "sharpen");
    }

    public Object blur(List<Object> arguments) {
        return applyConvolution(arguments, Kernel33.GAUSSIAN_BLUR, "blur");
    }

    private Object applyConvolution(List<Object> arguments, Kernel kernel, String functionName) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException(functionName + " takes 1 arguments (image(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return ScriptSupport.expandToImageList(forkJoinContext, arguments, this::sharpen);
        }
        if (arg instanceof ImageWrapper image) {
            return convolve(image, kernel);
        }
        throw new IllegalArgumentException(functionName  + " doesn't support argument " + arg);
    }

    private Object convolve(ImageWrapper image, Kernel kernel) {
        if (image instanceof ImageWrapper32 mono) {
            return new ImageWrapper32(mono.width(), mono.height(), imageMath.convolve(mono.asImage(), kernel).data());
        } else if (image instanceof ColorizedImageWrapper colorized) {
            return new ColorizedImageWrapper((ImageWrapper32) convolve(colorized.mono(), kernel), colorized.converter());
        } else if (image instanceof RGBImage rgb) {
            var hsl = ImageUtils.fromRGBtoHSL(new float[][] { rgb.r(), rgb.g(), rgb.b() });
            hsl[2] = imageMath.convolve(new Image(rgb.width(), rgb.height(), hsl[2]), kernel).data();
            var transformed = ImageUtils.fromHSLtoRGB(hsl);
            return new RGBImage(rgb.width(), rgb.height(), transformed[0], transformed[1], transformed[2]);
        }
        throw new IllegalArgumentException("Unsupported image type " + image);
    }
}
