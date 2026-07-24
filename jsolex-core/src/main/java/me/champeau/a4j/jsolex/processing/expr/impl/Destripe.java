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
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Map;
import java.util.Optional;

public class Destripe extends AbstractFunctionImpl {
    public Destripe(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object destripe(Map<String, Object> arguments) {
        BuiltinFunction.DESTRIPE.validateArgs(arguments);
        var mode = switch (intArg(arguments, "ellipseMode", 1)) {
            case 0 -> BandingReduction.Mode.WHOLE_LINE;
            case 2 -> BandingReduction.Mode.OUTSIDE_DISK;
            default -> BandingReduction.Mode.INSIDE_DISK;
        };
        var ellipse = mode == BandingReduction.Mode.WHOLE_LINE ? Optional.<Ellipse>empty() : getEllipse(arguments, "ellipse");
        int bandSize = intArg(arguments, "bs", 48);
        int passes = intArg(arguments, "passes", 2);
        int strips = intArg(arguments, "strips", 0);
        return monoToMonoImageTransformer("destripe", "img", arguments, src -> {
            if (src instanceof ImageWrapper32 image) {
                var width = image.width();
                var height = image.height();
                var data = image.data();
                for (int i = 0; i < passes; i++) {
                    BandingReduction.removeStripes(width, height, data, bandSize, ellipse.orElse(null), mode, strips);
                }
            } else {
                throw new ProcessingException("destripe can only be applied to mono images");
            }
        });
    }
}
