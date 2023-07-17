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

import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class BackgroundRemoval extends AbstractFunctionImpl {
    public BackgroundRemoval(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object removeBackground(List<Object> arguments) {
        assertExpectedArgCount(arguments, "remove_bg takes 1, 2 or 3 arguments (image(s), [tolerance], [fitting])", 1, 2);
        Optional<Ellipse> ellipse = getArgument(Ellipse.class, arguments, 2).or(() -> getFromContext(Ellipse.class));
        if (ellipse.isEmpty()) {
            throw new IllegalArgumentException("Cannot perform background removal because ellipse isn't found");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::removeBackground);
        }
        double tolerance;
        if (arguments.size() == 2) {
            tolerance = ((Number) arguments.get(1)).doubleValue();
            if (tolerance < 0) {
                throw new IllegalArgumentException("Tolerance should be greater than 0");
            }
        } else {
            tolerance = .9;
        }
        if (arg instanceof ImageWrapper32 ref) {
            return ScriptSupport.monoToMonoImageTransformer("remove_bg", 2, arguments, (width, height, data) -> {
                var e = ellipse.get();
                var background = AnalysisUtils.estimateBackground(ref, e);
                me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.removeBackground(width, height, data, tolerance, background, e);
            });
        }
        throw new IllegalArgumentException("remove_bg only supports mono images");
    }
}
