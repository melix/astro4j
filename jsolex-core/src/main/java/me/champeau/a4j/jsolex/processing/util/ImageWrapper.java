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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public sealed interface ImageWrapper permits ImageWrapper32, RGBImage, FileBackedImage {
    int width();

    int height();

    Map<Class<?>, Object> metadata();

    default <T> Optional<T> findMetadata(Class<T> clazz) {
        var metadata = metadata();
        if (metadata.containsKey(clazz)) {
            //noinspection unchecked
            return (Optional<T>) Optional.of(metadata.get(clazz));
        }
        return Optional.empty();
    }

    default <T> void transformMetadata(Class<T> clazz, Function<T, T> transformer) {
        findMetadata(clazz).ifPresent(value -> metadata().put(clazz, transformer.apply(value)));
    }

    ImageWrapper copy();

    default ImageWrapper unwrapToMemory() {
        if (this instanceof FileBackedImage fbi) {
            return fbi.unwrapToMemory();
        }
        return this;
    }

    default ImageWrapper wrap() {
        return FileBackedImage.wrap(this);
    }

    static float[][] copyData(float[][] source) {
        if (source.length == 0) {
            return new float[0][];
        }
        int height = source.length;
        int width = source[0].length;
        float[][] result = new float[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(source[y], 0, result[y], 0, width);
        }
        return result;
    }
}
