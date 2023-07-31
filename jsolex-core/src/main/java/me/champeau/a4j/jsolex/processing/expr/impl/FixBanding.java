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

import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;

import java.util.List;
import java.util.Map;

public class FixBanding extends AbstractFunctionImpl {
    public FixBanding(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object fixBanding(List<Object> arguments) {
        assertExpectedArgCount(arguments, "fix_banding takes 3 or 4 arguments (image, band size, passes, [ellipse])", 3, 4);
        var ellipse = getEllipse(arguments, 3);
        int bandSize = intArg(arguments, 1);
        int passes = intArg(arguments, 2);
        return ScriptSupport.monoToMonoImageTransformer(forkJoinContext, "fix_banding", 3, arguments, (width, height, data) -> {
            for (int i = 0; i < passes; i++) {
                BandingReduction.reduceBanding(width, height, data, bandSize, ellipse.orElse(null));
            }
        });
    }

}
