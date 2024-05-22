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
import java.util.function.Function;

/**
 * A wrapper for images which are using 32-bit floats.
 * @param mono the monochrome image
 * @param converter the converter from mono to RGB
 */
public record ColorizedImageWrapper(
        ImageWrapper32 mono,
        Function<ImageWrapper32, float[][]> converter,
        Map<Class<?>, Object> metadata
) implements ImageWrapper {
    @Override
    public int width() {
        return mono().width();
    }

    @Override
    public int height() {
        return mono.height();
    }

    public ColorizedImageWrapper copy() {
        return new ColorizedImageWrapper(mono.copy(), converter, new LinkedHashMap<>(metadata));
    }
}
