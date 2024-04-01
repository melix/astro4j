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
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class Saturation extends AbstractFunctionImpl {
    public Saturation(Map<Class<?>, Object> context) {
        super(context);
    }

    public Object saturate(List<Object> arguments) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("saturate takes 2 argument (image(s), saturation)");
        }
        var arg = arguments.get(0);
        if (arg instanceof List) {
            return expandToImageList(arguments, this::saturate);
        }
        var saturation = doubleArg(arguments, 1);
        var exponent = Math.pow(2, -saturation);
        if (arg instanceof ColorizedImageWrapper colorized) {
            return new ColorizedImageWrapper(colorized.mono(), mono -> {
                var rgb = colorized.converter().apply(mono);
                var hsl = ImageUtils.fromRGBtoHSL(rgb);
                var s = hsl[1];
                for (int i = 0; i < s.length; i++) {
                    var sat = Math.pow(s[i], exponent);
                    s[i] = (float) sat;
                }
                ImageUtils.fromHSLtoRGB(hsl, rgb);
                return rgb;
            }, colorized.metadata());
        } else if (arg instanceof RGBImage rgb) {
            var hsl = ImageUtils.fromRGBtoHSL(new float[][]{rgb.r(), rgb.g(), rgb.b()});
            var s = hsl[1];
            for (int i = 0; i < s.length; i++) {
                var sat = Math.pow(s[i], exponent);
                s[i] = (float) sat;
            }
            var output = new float[3][rgb.r().length];
            ImageUtils.fromHSLtoRGB(hsl, output);
            return new RGBImage(rgb.width(), rgb.height(), output[0], output[1], output[2], rgb.metadata());
        }
        return arg;
    }
}
