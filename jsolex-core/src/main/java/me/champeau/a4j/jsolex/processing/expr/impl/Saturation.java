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
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Saturation extends AbstractFunctionImpl {
    public Saturation(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object saturate(List<Object> arguments) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("saturate takes 2 argument (image(s), saturation)");
        }
        var arg = arguments.get(0);
        if (arg instanceof List) {
            return expandToImageList("saturate", arguments, this::saturate);
        }
        var saturation = doubleArg(arguments, 1);
        var exponent = Math.pow(2, -saturation);
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof RGBImage rgb) {
            var hsl = ImageUtils.fromRGBtoHSL(new float[][][]{rgb.r(), rgb.g(), rgb.b()});
            var s = hsl[1];
            for (float[] line : s) {
                for (int i = 0; i < s.length; i++) {
                    var sat = Math.pow(line[i], exponent);
                    line[i] = (float) sat;
                }
            }
            var output = new float[3][rgb.height()][rgb.width()];
            ImageUtils.fromHSLtoRGB(hsl, output);
            return new RGBImage(rgb.width(), rgb.height(), output[0], output[1], output[2], new LinkedHashMap<>(rgb.metadata()));
        }
        return arg;
    }
}
