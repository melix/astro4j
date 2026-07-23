/*
 * Copyright 2026 the original author or authors.
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

import java.util.function.BiPredicate;

/**
 * A mask selecting the pixels whose value lies in the given range (bounds included).
 *
 * @param lo the lowest selected pixel value
 * @param hi the highest selected pixel value
 */
public record RangeMask(double lo, double hi) implements ImageMask {
    public RangeMask {
        if (hi < lo) {
            throw new IllegalArgumentException("A range mask requires lo <= hi, got lo=" + lo + ", hi=" + hi);
        }
    }

    @Override
    public BiPredicate<Integer, Integer> resolve(ImageWrapper image) {
        if (image.unwrapToMemory() instanceof ImageWrapper32 mono) {
            var data = mono.data();
            return (x, y) -> {
                var v = data[y][x];
                return v >= lo && v <= hi;
            };
        }
        throw new IllegalArgumentException("A range mask can only be resolved against a mono image");
    }
}
