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
import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.stretching.GammaStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Colorize extends AbstractFunctionImpl {

    public Colorize(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object colorize(Map<String ,Object> arguments) {
        BuiltinFunction.COLORIZE.validateArgs(arguments);
        return doColorize(arguments, this::colorize);
    }
    public Object colorize2(Map<String ,Object> arguments) {
        BuiltinFunction.COLORIZE2.validateArgs(arguments);
        return doColorize(arguments, this::colorize2);
    }

    private Object doColorize(Map<String, Object> arguments, Function<Map<String, Object>, Object> function) {
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("colorize", "img", arguments, function);
        }
        var profile = stringArg(arguments, "profile", null);
        if (profile == null) {
            int rIn = intArg(arguments, "rIn", 0);
            int rOut = intArg(arguments, "rOut", 255);
            int gIn = intArg(arguments, "gIn", 0);
            int gOut = intArg(arguments, "gOut", 255);
            int bIn = intArg(arguments, "bIn", 0);
            int bOut = intArg(arguments, "bOut", 255);
            if (arg instanceof FileBackedImage fileBackedImage) {
                arg = fileBackedImage.unwrapToMemory();
            }
            if (arg instanceof ImageWrapper32 mono) {
                return RGBImage.fromMono(mono, data -> {
                    var curve = new ColorCurve("adhoc", rIn, rOut, gIn, gOut, bIn, bOut);
                    return doColorize(mono.width(), mono.height(), data.data(), curve);
                });
            }
        } else {
            if (arg instanceof FileBackedImage fileBackedImage) {
                arg = fileBackedImage.unwrapToMemory();
            }
            var rays = SpectralRayIO.loadDefaults();
            for (SpectralRay ray : rays) {
                if (ray.label().equalsIgnoreCase(profile) && (arg instanceof ImageWrapper32 mono)) {
                    var curve = ray.colorCurve();
                    if (curve != null) {
                        return RGBImage.fromMono(mono, data -> doColorize(mono.width(), mono.height(), data.data(), curve));
                    } else if (ray.wavelength().nanos() != 0) {
                        var rgb = ray.toRGB();
                        return RGBImage.fromMono(mono, data -> doColorize(mono.width(), mono.height(), data.data(), rgb));
                    }
                }
            }
            throw new IllegalArgumentException("Cannot find color profile '" + profile + "'");
        }
        throw new IllegalArgumentException("colorize first argument must be an image or a list of images");
    }

    private static float[][][] doColorize(int width, int height, float[][] data, ColorCurve curve) {
        var copy = new float[height][width];
        for (int y = 0; y < height; y++) {
            float[] line = data[y];
            System.arraycopy(line, 0, copy[y], 0, line.length);
        }
        return ImageUtils.convertToRGB(curve, copy);
    }

    public static float[][][] doColorize(int width, int height, float[][] mono, int[] rgb) {
        var copy = new float[height][width];
        for (int y = 0; y < mono.length; y++) {
            float[] line = mono[y];
            System.arraycopy(line, 0, copy[y], 0, line.length);
        }
        new GammaStrategy(1.2).stretch(new ImageWrapper32(width, height, copy, Map.of()));
        var r = new float[height][width];
        var g = new float[height][width];
        var b = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var gray = copy[y][x];
                r[y][x] = gray * rgb[0] / 255f;
                g[y][x] = gray * rgb[1] / 255f;
                b[y][x] = gray * rgb[2] / 255f;
            }
        }
        var rgbImage = new RGBImage(
            width, height,
            r, g, b,
            Map.of()
        );
        new LinearStrechingStrategy(0, 0.9f * Constants.MAX_PIXEL_VALUE).stretch(
            rgbImage
        );
        return new float[][][]{r, g, b};
    }
}
