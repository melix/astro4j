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
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.BlurKernel;
import me.champeau.a4j.math.image.Deconvolution;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel;
import me.champeau.a4j.math.image.SharpenKernel;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.applyOperator;

public class Convolution extends AbstractFunctionImpl {
    private final ImageMath imageMath = ImageMath.newInstance();
    private final Deconvolution deconvolution = new Deconvolution(imageMath);

    public Convolution(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object sharpen(Map<String ,Object> arguments) {
        return applyConvolution(arguments, SharpenKernel::of, "sharpen");
    }

    public Object blur(Map<String ,Object> arguments) {
        return applyConvolution(arguments, BlurKernel::of, "blur");
    }

    private Object applyConvolution(Map<String ,Object> arguments, KernelFactory kernelFactory, String functionName) {
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList(functionName, "img", arguments, a -> applyConvolution(a, kernelFactory, functionName));
        }
        if (arg instanceof ImageWrapper image) {
            var kernelSize = intArg(arguments, "kernel", 3);
            return convolve(image, kernelFactory.create(kernelSize));
        }
        throw new IllegalArgumentException(functionName + " doesn't support argument " + arg);
    }

    private Object convolve(ImageWrapper image, Kernel kernel) {
        if (image instanceof FileBackedImage fileBackedImage) {
            image = fileBackedImage.unwrapToMemory();
        }
        if (image instanceof ImageWrapper32 mono) {
            return new ImageWrapper32(mono.width(), mono.height(), imageMath.convolve(mono.asImage(), kernel).data(), mono.metadata());
        } else if (image instanceof RGBImage rgb) {
            var hsl = ImageUtils.fromRGBtoHSL(new float[][][]{rgb.r(), rgb.g(), rgb.b()});
            hsl[2] = imageMath.convolve(new Image(rgb.width(), rgb.height(), hsl[2]), kernel).data();
            var transformed = ImageUtils.fromHSLtoRGB(hsl);
            return new RGBImage(rgb.width(), rgb.height(), transformed[0], transformed[1], transformed[2], rgb.metadata());
        }
        throw new IllegalArgumentException("Unsupported image type " + image);
    }

    public Object richardsonLucy(Map<String ,Object> arguments) {
        BuiltinFunction.RL_DECON.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("rl_decon", "img", arguments, this::richardsonLucy);
        }
        if (arg instanceof ImageWrapper image) {
            if (image instanceof FileBackedImage fileBackedImage) {
                image = fileBackedImage.unwrapToMemory();
            }
            var radius = floatArg(arguments, "radius", (float) Deconvolution.DEFAULT_RADIUS);
            var sigma = floatArg(arguments, "sigma", (float) Deconvolution.DEFAULT_SIGMA);
            var iterations = intArg(arguments, "iterations", Deconvolution.DEFAULT_ITERATIONS);
            if (image instanceof ImageWrapper32 mono) {
                var psf = Deconvolution.generateGaussianPSF(radius, sigma);
                var decon = deconvolution.richardsonLucy(mono.asImage(), psf, iterations);
                return new ImageWrapper32(mono.width(), mono.height(), decon.data(), mono.metadata());
            }
            throw new IllegalArgumentException("rl_decon only supports mono images");
        }
        throw new IllegalArgumentException("rl_decon doesn't support argument " + arg);
    }

    public Object unsharpMask(Map<String, Object> arguments) {
        BuiltinFunction.UNSHARP_MASK.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("unsharp_mask", "img", arguments, this::unsharpMask);
        }
        if (arg instanceof ImageWrapper image) {
            var strength = doubleArg(arguments, "strength", 1.0d);
            var kernelSize = intArg(arguments, "kernel", 3);
            var unwrapped = image.unwrapToMemory();
            if (unwrapped instanceof ImageWrapper32 mono) {
                var blurred = imageMath.convolve(mono.asImage(), BlurKernel.of(kernelSize));
                var diff = (ImageWrapper32) applyOperator(mono, ImageWrapper32.fromImage(blurred), null, null, (a, b) -> a - b);
                var strengthened = (ImageWrapper32) applyOperator(diff, null, null, strength, (a, b) -> a * b);
                return applyOperator(mono, strengthened, null, null, Double::sum);
            }
            throw new IllegalArgumentException("unsharp_mask only supports mono images");
        }
        throw new IllegalArgumentException("Unsupported image type " + arg);
    }

    private interface KernelFactory {
        Kernel create(int size);
    }
}
