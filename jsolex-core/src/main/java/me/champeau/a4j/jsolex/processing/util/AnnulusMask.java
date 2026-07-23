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

import me.champeau.a4j.math.regression.Ellipse;

import java.util.function.BiPredicate;

/**
 * A mask defined as an annulus around the detected solar disk, with radii expressed in solar
 * radii. A {@code rmin} of 0 turns it into a disk mask, an infinite {@code rmax} keeps
 * everything beyond {@code rmin}. Being relative to the disk, it remains valid through
 * geometric transformations which maintain the ellipse metadata (crop, rescale, ...).
 *
 * @param rmin the inner radius, in solar radii
 * @param rmax the outer radius, in solar radii
 */
public record AnnulusMask(double rmin, double rmax) implements ImageMask {
    public AnnulusMask {
        if (rmin < 0 || rmax <= rmin) {
            throw new IllegalArgumentException("An annulus mask requires 0 <= rmin < rmax, got rmin=" + rmin + ", rmax=" + rmax);
        }
    }

    @Override
    public BiPredicate<Integer, Integer> resolve(ImageWrapper image) {
        var ellipse = image.findMetadata(Ellipse.class)
                .orElseThrow(() -> new IllegalArgumentException("Cannot resolve the image mask because the image has no detected solar disk"));
        return resolve(ellipse);
    }

    public BiPredicate<Integer, Integer> resolve(Ellipse ellipse) {
        var inner = rmin == 0 ? null : ellipse.rescale(rmin, rmin);
        var outer = Double.isInfinite(rmax) ? null : ellipse.rescale(rmax, rmax);
        return (x, y) -> (inner == null || !inner.isWithin(x, y)) && (outer == null || outer.isWithin(x, y));
    }
}
