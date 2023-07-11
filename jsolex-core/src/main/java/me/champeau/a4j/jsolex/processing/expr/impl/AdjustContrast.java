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

import me.champeau.a4j.jsolex.processing.stretching.ConstrastAdjustmentStrategy;

import java.util.List;

public class AdjustContrast {
    private AdjustContrast() {

    }

    public static Object adjustContrast(List<Object> arguments) {
        if (arguments.size() != 3) {
            throw new IllegalArgumentException("adjust_contrast takes 3 arguments (image(s), min, max)");
        }
        int min = ((Number) arguments.get(1)).intValue();
        int max = ((Number) arguments.get(2)).intValue();
        if (min < 0 || min > 255) {
            throw new IllegalArgumentException("adjust_contrast min must be between 0 and 255");
        }
        if (max < 0 || max > 255) {
            throw new IllegalArgumentException("adjust_contrast max must be between 0 and 255");
        }
        return ScriptSupport.monoToMonoImageTransformer("adjust_contrast", 3, arguments, (width, height, data) -> new ConstrastAdjustmentStrategy(min << 8, max << 8).stretch(width, height, data));
    }
}
