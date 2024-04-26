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

import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ContrastAdjustmentStrategy;
import me.champeau.a4j.jsolex.processing.stretching.GammaStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;

import java.util.List;
import java.util.Map;

public class AdjustContrast extends AbstractFunctionImpl {
    public AdjustContrast(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object adjustContrast(List<Object> arguments) {
        if (arguments.size() != 3) {
            throw new IllegalArgumentException("adjust_contrast takes 3 arguments (image(s), min, max)");
        }
        int min = intArg(arguments, 1);
        int max = intArg(arguments, 2);
        if (min < 0 || min > 255) {
            throw new IllegalArgumentException("adjust_contrast min must be between 0 and 255");
        }
        if (max < 0 || max > 255) {
            throw new IllegalArgumentException("adjust_contrast max must be between 0 and 255");
        }
        return monoToMonoImageTransformer( "adjust_contrast", 3, arguments, image -> new ContrastAdjustmentStrategy(min << 8, max << 8).stretch(image));
    }

    public Object adjustGamma(List<Object> arguments) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("adjust_gamma takes 2 arguments (image(s), gamma)");
        }
        double gamma = doubleArg(arguments, 1);
        if (gamma <= 0) {
            throw new IllegalArgumentException("gamma must be positive");
        }
        return monoToMonoImageTransformer( "adjust_gamma", 3, arguments, image -> new GammaStrategy(gamma).stretch(image));
    }

    public Object autoContrast(List<Object> arguments) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("auto_contrast takes 2 arguments (image(s), gamma)");
        }
        double gamma = doubleArg(arguments, 1);
        if (gamma <= 1) {
            throw new IllegalArgumentException("gamma must be greater than 1");
        }
        return monoToMonoImageTransformer( "auto_contrast", 3, arguments, image -> new AutohistogramStrategy(gamma).stretch(image));
    }
}
