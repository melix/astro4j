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
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

public final class DynamicCutoffStrategy implements StretchingStrategy {
    private final double cutoff;
    private final int start;

    public DynamicCutoffStrategy(double cutoff, float start) {
        this.start = Math.min((int) start >> 8, 255);
        if (cutoff < 0 || cutoff > 1) {
            throw new IllegalArgumentException("Cutoff must be between 0 and 1");
        }
        this.cutoff = cutoff;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var histogram = Histogram.of(image.data(), 256);
        var initialValue = histogram.get(start);
        int idx = start;
        double max = initialValue;
        for (int i = idx + 1; i < 255; i++) {
            var v = histogram.get(i);
            if (v >= initialValue) {
                idx = i;
                max = Math.max(max, v);
            }
        }
        double factor = cutoff / (idx/255d);
        var data = image.data();
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) Math.min(Constants.MAX_PIXEL_VALUE, data[i] * factor);
        }

    }

}
