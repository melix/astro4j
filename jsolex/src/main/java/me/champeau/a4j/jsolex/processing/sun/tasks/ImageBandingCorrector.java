/*
 * Copyright 2003-2021 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.image.ImageMath;

public class ImageBandingCorrector extends AbstractTask<float[]> {

    public ImageBandingCorrector(Broadcaster broadcaster, ImageWrapper32 image) {
        super(broadcaster, image);
    }

    @Override
    public float[] call() throws Exception {
        var imageMath = ImageMath.newInstance();
        // Perform one iteration vertically, were there are not so many lines
        BandingReduction.reduceBanding(width, height, buffer, 1, 16);
        // Then perform multiple iterations vertically, were there are many line artifacts
        // we need to transpose the image to compute the average value of each line
        float[] transposed = imageMath.rotateLeft(buffer, width, height);
        BandingReduction.reduceBanding(height, width, transposed, 4, 32);
        transposed = imageMath.rotateRight(transposed, height, width);
        System.arraycopy(transposed, 0, buffer, 0, transposed.length);
        return buffer;
    }
}
