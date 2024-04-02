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

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.List;
import java.util.Map;

public class Colorize extends AbstractFunctionImpl {

    public Colorize(Map<Class<?>, Object> context) {
        super(context);
    }

    public Object colorize(List<Object> arguments) {
        if (arguments.size() != 7 && arguments.size() != 2) {
            throw new IllegalArgumentException("colorize takes 3 arguments (image, rIn, rOut, gIn, gOut, bIn, bOut) or 2 arguments (image, profile name)");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return ScriptSupport.expandToImageList(arguments, this::colorize);
        }
        if (arguments.size() == 7) {
            int rIn = intArg(arguments, 1);
            int rOut = intArg(arguments, 2);
            int gIn = intArg(arguments, 3);
            int gOut = intArg(arguments, 4);
            int bIn = intArg(arguments, 5);
            int bOut = intArg(arguments, 6);
            if (arg instanceof FileBackedImage fileBackedImage) {
                arg = fileBackedImage.unwrapToMemory();
            }
            if (arg instanceof ImageWrapper32 mono) {
                return new ColorizedImageWrapper(mono, data -> {
                    var curve = new ColorCurve("adhoc", rIn, rOut, gIn, gOut, bIn, bOut);
                    return doColorize(data, curve);
                }, mono.metadata());
            }
        } else {
            if (arg instanceof FileBackedImage fileBackedImage) {
                arg = fileBackedImage.unwrapToMemory();
            }
            String profile = arguments.get(1).toString();
            var rays = SpectralRayIO.loadDefaults();
            for (SpectralRay ray : rays) {
                if (ray.label().equalsIgnoreCase(profile) && (arg instanceof ImageWrapper32 mono)) {
                    var curve = ray.colorCurve();
                    if (curve != null) {
                        return new ColorizedImageWrapper(mono, data -> doColorize(data, curve), mono.metadata());
                    }
                }
            }
            throw new IllegalArgumentException("Cannot find color profile '" + profile + "'");
        }
        throw new IllegalArgumentException("colorize first argument must be an image or a list of images");
    }

    private static float[][] doColorize(float[] data, ColorCurve curve) {
        float[] copy = new float[data.length];
        System.arraycopy(data, 0, copy, 0, copy.length);
        return ImageUtils.convertToRGB(curve, copy);
    }
}
