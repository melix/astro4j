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
import me.champeau.a4j.jsolex.processing.sun.SpectralLineFit;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LineFit extends AbstractFunctionImpl {

    public LineFit(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object lineFit(Map<String, Object> arguments) {
        BuiltinFunction.LINE_FIT.validateArgs(arguments);
        var images = spectrumImages(arguments.get("img"));
        if (images.size() < 2) {
            throw new IllegalArgumentException("line_fit requires a list of images forming a spectrum");
        }
        var first = images.getFirst();
        var width = first.width();
        var height = first.height();
        var cube = new float[images.size()][][];
        var positions = new double[images.size()];
        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            if (image.width() != width || image.height() != height) {
                throw new IllegalArgumentException("All images must have the same dimensions");
            }
            var pixelShift = image.findMetadata(PixelShift.class)
                    .orElseThrow(() -> new IllegalArgumentException("line_fit requires images with a pixel shift, for example obtained with range"));
            cube[i] = image.data();
            positions[i] = pixelShift.pixelShift();
        }
        var shift = getAsNumber(arguments, "shift").doubleValue();
        var fwhm = getAsNumber(arguments, "fwhm").doubleValue();
        var template = maskArgument(arguments, "template", first);
        if (template == null) {
            throw new IllegalArgumentException("line_fit requires a template mask");
        }
        var region = maskArgument(arguments, "mask", first);
        var doppler = intArg(arguments, "doppler", 0);
        var faintest = doubleArg(arguments, "faintest", 1);
        var amplitude = SpectralLineFit.fit(width, height, cube, positions, shift, fwhm, template, region, doppler, faintest);
        var metadata = new HashMap<>(closestTo(images, positions, shift).metadata());
        metadata.put(PixelShift.class, new PixelShift(shift));
        return new ImageWrapper32(width, height, amplitude, metadata);
    }

    private static ImageWrapper32 closestTo(List<ImageWrapper32> images, double[] positions, double shift) {
        var best = 0;
        for (int i = 1; i < positions.length; i++) {
            if (Math.abs(positions[i] - shift) < Math.abs(positions[best] - shift)) {
                best = i;
            }
        }
        return images.get(best);
    }

    private static List<ImageWrapper32> spectrumImages(Object input) {
        var result = new ArrayList<ImageWrapper32>();
        if (input instanceof List<?> list) {
            for (var item : list) {
                result.add(asMonoImage(item));
            }
        } else {
            result.add(asMonoImage(input));
        }
        return result;
    }

    private static ImageWrapper32 asMonoImage(Object item) {
        if (item instanceof ImageWrapper wrapper && wrapper.unwrapToMemory() instanceof ImageWrapper32 mono) {
            return mono;
        }
        throw new IllegalArgumentException("line_fit only supports mono images");
    }
}
