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
 * A mask restricting the pixels an operation takes into account, passed explicitly to the
 * functions which support one. The first use case is restricting the sampling of
 * statistics-based operations (histograms, percentiles, auto-stretch parameters), in which
 * case the transformation is still applied to the entire image.
 */
public sealed interface ImageMask permits AnnulusMask, RangeMask, InvertedMask {
    /**
     * Resolves this mask against the given image into a pixel predicate.
     *
     * @param image the image the mask is evaluated against
     * @return a predicate telling, for (x, y) coordinates, whether the pixel is part of the mask
     */
    BiPredicate<Integer, Integer> resolve(ImageWrapper image);

    /**
     * Returns the inverse of the given mask; inverting an inverted mask returns the original one.
     *
     * @param mask the mask to invert
     * @return the inverted mask
     */
    static ImageMask inverted(ImageMask mask) {
        if (mask instanceof InvertedMask inverted) {
            return inverted.mask();
        }
        return new InvertedMask(mask);
    }
}
