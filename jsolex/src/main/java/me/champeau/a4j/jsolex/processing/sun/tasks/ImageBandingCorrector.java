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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

public class ImageBandingCorrector extends AbstractTask<ImageWrapper32> {

    private final Ellipse ellipse;
    private final BandingCorrectionParams params;

    public ImageBandingCorrector(Broadcaster broadcaster, ImageWrapper32 image, Ellipse ellipse, BandingCorrectionParams params) {
        super(broadcaster, image);
        this.ellipse = ellipse;
        this.params = params;
    }

    @Override
    public ImageWrapper32 call() throws Exception {
        float[] result = new float[buffer.length];
        System.arraycopy(buffer, 0, result, 0, buffer.length);
        BandingReduction.reduceBanding(width, height, result, params.passes(), params.width());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!ellipse.isWithin(x,y)) {
                    result[index] = buffer[index];
                }
            }
        }
        return new ImageWrapper32(width, height, result);
    }
}
