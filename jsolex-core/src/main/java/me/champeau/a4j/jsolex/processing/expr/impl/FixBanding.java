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

import java.util.Map;

public class FixBanding extends AbstractFunctionImpl {
    public FixBanding(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object fixBanding(Map<String ,Object> arguments) {
        BuiltinFunction.FIX_BANDING.validateArgs(arguments);
        var ellipse = getEllipse(arguments, "ellipse");
        int bandSize = intArg(arguments, "bs", 32);
        int passes = intArg(arguments, "passes", 1);
        return monoToMonoImageTransformer( "fix_banding", "img", arguments, src -> {
            if (src instanceof ImageWrapper32 image) {
                var width = image.width();
                var height = image.height();
                var data = image.data();
                for (int i = 0; i < passes; i++) {
                    BandingReduction.reduceBanding(width, height, data, bandSize, ellipse.orElse(null));
                }
            } else {
                throw new ProcessingException("fix_banding can only be applied to mono images");
            }
        });
    }

}
