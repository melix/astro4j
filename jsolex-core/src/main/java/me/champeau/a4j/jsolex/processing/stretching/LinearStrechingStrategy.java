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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

// Not worth running on GPU: memory transfer overhead dominates the simple linear scaling
public final class LinearStrechingStrategy implements StretchingStrategy {
    public static final LinearStrechingStrategy DEFAULT = new LinearStrechingStrategy(0, Constants.MAX_PIXEL_VALUE);

    private final float lo;
    private final float hi;

    public LinearStrechingStrategy(float lo, float hi) {
        this.lo = lo;
        this.hi = hi;
    }

    public float getLo() {
        return lo;
    }

    public float getHi() {
        return hi;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var pixels = image.pixels();
        if (image.height() == 0) {
            return;
        }
        double min = pixels.min();
        double max = pixels.max();
        double range = max - min;
        if (range == 0) {
            return;
        }
        pixels.updateRows((y, row) -> {
            for (int x = 0; x < row.length; x++) {
                row[x] = (float) (hi / range * (row[x] - min));
            }
        });
    }
}
