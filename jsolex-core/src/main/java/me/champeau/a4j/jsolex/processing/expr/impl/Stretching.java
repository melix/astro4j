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

import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.Constants;

import java.util.List;

public class Stretching {
    private Stretching() {

    }

    public static Object asinhStretch(List<Object> arguments) {
        if (arguments.size() < 3) {
            throw new IllegalArgumentException("asinh_stretch takes 3 arguments (image(s), blackpoint, stretch)");
        }
        float blackpoint = ((Number) arguments.get(1)).floatValue();
        float stretch = ((Number) arguments.get(2)).floatValue();
        return ScriptSupport.monoToMonoImageTransformer("asinh_stretch", 3, arguments, (width, height, data) -> new ArcsinhStretchingStrategy(blackpoint, stretch, stretch).stretch(width, height, data));
    }

    public static Object linearStretch(List<Object> arguments) {
        if (arguments.size() != 1 && arguments.size() != 3) {
            throw new IllegalArgumentException("linear_stretch takes 3 arguments (image(s), lo, hi)");
        }
        float lo;
        float hi;
        if (arguments.size() == 3) {
            lo = Math.min(Constants.MAX_PIXEL_VALUE, Math.max(0, ((Number) arguments.get(1)).floatValue()));
            hi = Math.min(Constants.MAX_PIXEL_VALUE, Math.max(0, ((Number) arguments.get(2)).floatValue()));
        } else {
            hi = Constants.MAX_PIXEL_VALUE;
            lo = 0;
        }
        return ScriptSupport.monoToMonoImageTransformer("linear_stretch", 3, arguments, (width, height, data) -> new LinearStrechingStrategy(lo, hi).stretch(width, height, data));
    }
}
