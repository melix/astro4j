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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.image.ImagePlane;

import java.util.LinkedHashMap;
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
        var profileAsNumber = getArgument(Object.class, arguments, "profile");
        if (profileAsNumber.orElse(null) instanceof Number referenceWl) {
            if (arg instanceof ImageWrapper32 mono) {
                var ray = new SpectralRay("tmp", null, Wavelen.ofAngstroms(referenceWl.doubleValue()), true, List.of());
                return doColorize(mono, ray.toRGB());
            }
        }
        var profile = stringArg(arguments, "profile", null);
        if (profile == null) {
            int rIn = intArg(arguments, "rIn", 0);
            int rOut = intArg(arguments, "rOut", 255);
            int gIn = intArg(arguments, "gIn", 0);
            int gOut = intArg(arguments, "gOut", 255);
            int bIn = intArg(arguments, "bIn", 0);
            int bOut = intArg(arguments, "bOut", 255);
            if (arg instanceof ImageWrapper32 mono) {
                var curve = new ColorCurve("adhoc", rIn, rOut, gIn, gOut, bIn, bOut);
                return toRGBImage(mono, ImageUtils.convertToRGB(curve, mono.pixels()));
            }
        } else {
            var rays = SpectralRayIO.loadDefaults();
            for (SpectralRay ray : rays) {
                if (ray.label().equalsIgnoreCase(profile) && (arg instanceof ImageWrapper32 mono)) {
                    var curve = ray.colorCurve();
                    if (curve != null) {
                        return toRGBImage(mono, ImageUtils.convertToRGB(curve, mono.pixels()));
                    } else if (ray.wavelength().nanos() != 0) {
                        var rgb = ray.toRGB();
                        return doColorize(mono, rgb);
                    }
                }
            }
            throw new IllegalArgumentException("Cannot find color profile '" + profile + "'");
        }
        throw new IllegalArgumentException("colorize first argument must be an image or a list of images");
    }

    public static float[][][] doColorize(int width, int height, float[][] mono, int[] rgb) {
        var planes = colorizeInPlace(ImagePlane.of(width, height, mono), rgb);
        return new float[][][]{planes[0].toArray(), planes[1].toArray(), planes[2].toArray()};
    }

    /**
     * Colorizes a monochrome image with the given color.
     *
     * @param mono the monochrome image
     * @param rgb the red, green and blue components of the color, between 0 and 255
     * @return the colorized image
     */
    public static RGBImage doColorize(ImageWrapper32 mono, int[] rgb) {
        return toRGBImage(mono, colorizeInPlace(mono.pixels().copy(), rgb));
    }

    private static ImagePlane[] colorizeInPlace(ImagePlane gray, int[] rgb) {
        new GammaStrategy(1.2).stretch(new ImageWrapper32(gray, Map.of()));
        var r = scale(gray, rgb[0]);
        var g = scale(gray, rgb[1]);
        var b = scale(gray, rgb[2]);
        new LinearStrechingStrategy(0, 0.9f * Constants.MAX_PIXEL_VALUE).stretch(
            new RGBImage(r, g, b, Map.of())
        );
        return new ImagePlane[]{r, g, b};
    }

    private static ImagePlane scale(ImagePlane gray, int component) {
        return gray.mapRows((y, row) -> {
            for (int x = 0; x < row.length; x++) {
                row[x] = row[x] * component / 255f;
            }
        });
    }

    private static RGBImage toRGBImage(ImageWrapper32 mono, ImagePlane[] rgb) {
        return new RGBImage(rgb[0], rgb[1], rgb[2], new LinkedHashMap<>(mono.metadata()));
    }
}
