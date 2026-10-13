/*
 * Copyright 2026 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.util.AtrousWavelet;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

public class Wavelets extends AbstractFunctionImpl {

    public Wavelets(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object denoise(Map<String, Object> arguments) {
        BuiltinFunction.DENOISE.validateArgs(arguments);
        var grain = doubleArg(arguments, "grain", 0.25);
        if (grain < 0 || grain > 1) {
            throw new IllegalArgumentException("grain must be between 0 and 1");
        }
        return monoToMonoImageTransformer("denoise", "img", arguments, image -> {
            var mask = maskArgument(arguments, "mask", image);
            if (image instanceof ImageWrapper32 mono) {
                denoise(mono.width(), mono.height(), mono.data(), mask, grain);
            } else if (image instanceof RGBImage rgb) {
                denoise(rgb.width(), rgb.height(), rgb.r(), mask, grain);
                denoise(rgb.width(), rgb.height(), rgb.g(), mask, grain);
                denoise(rgb.width(), rgb.height(), rgb.b(), mask, grain);
            }
        });
    }

    public Object aTrous(Map<String, Object> arguments) {
        BuiltinFunction.A_TROUS.validateArgs(arguments);
        if (!(arguments.get("img") instanceof ImageWrapper wrapper) || !(wrapper.unwrapToMemory() instanceof ImageWrapper32 image)) {
            throw new IllegalArgumentException("a_trous only supports a single mono image");
        }
        var levels = intArg(arguments, "levels", AtrousWavelet.defaultLevels(image.width(), image.height()));
        if (levels < 1) {
            throw new IllegalArgumentException("levels must be at least 1");
        }
        var mask = maskArgument(arguments, "mask", image);
        return AtrousWavelet.decompose(image.width(), image.height(), image.data(), mask, levels)
                .stream()
                .map(scale -> new ImageWrapper32(image.width(), image.height(), scale, new HashMap<>(image.metadata())))
                .toList();
    }

    private static void denoise(int width, int height, float[][] data, BiPredicate<Integer, Integer> mask, double grain) {
        var denoised = AtrousWavelet.denoise(width, height, data, mask, grain);
        for (int y = 0; y < height; y++) {
            System.arraycopy(denoised[y], 0, data[y], 0, width);
        }
    }
}
