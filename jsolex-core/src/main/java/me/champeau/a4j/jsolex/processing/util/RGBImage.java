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
package me.champeau.a4j.jsolex.processing.util;

import java.util.LinkedHashMap;
import java.util.Map;

public record RGBImage(
        int width,
        int height,
        float[] r,
        float[] g,
        float[] b,
        Map<Class<?>, Object> metadata
) implements ImageWrapper {
    @Override
    public RGBImage copy() {
        return new RGBImage(width, height, copyOf(r), copyOf(g), copyOf(b), new LinkedHashMap<>(metadata));
    }

    private static float[] copyOf(float[] array) {
        float[] rcopy = new float[array.length];
        System.arraycopy(array, 0, rcopy, 0, array.length);
        return rcopy;
    }
}
