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
 * The inverse of another mask: pixels excluded by the delegate are part of this mask and
 * conversely. Use {@link ImageMask#inverted(ImageMask)} to create one.
 *
 * @param mask the mask to invert
 */
public record InvertedMask(ImageMask mask) implements ImageMask {
    @Override
    public BiPredicate<Integer, Integer> resolve(ImageWrapper image) {
        var delegate = mask.resolve(image);
        return (x, y) -> !delegate.test(x, y);
    }
}
