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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.math.image.ImageMath;

public class BandingReduction {
    private BandingReduction() {
    }

    public static void reduceBanding(int width, int height, float[] data, int bandSize) {
        var imageMath = ImageMath.newInstance();
        // compute average value of each line
        double[] lineAverages = imageMath.lineAverages(new ImageMath.Image(width, height, data));
        for (int y = 0; y < height; y++) {
            double sum = 0;
            int count = 0;
            for (int k = Math.max(0, y - bandSize); k < Math.min(y + bandSize, height); k++) {
                if (k != y) {
                    sum += lineAverages[k];
                    count++;
                }
            }
            double mean = sum / count;
            double current = lineAverages[y];
            Double correction = Double.valueOf(mean / current);
            if (correction > 1 && !correction.isInfinite() && !correction.isNaN()) {
                for (int x = 0; x < width; x++) {
                    data[y * width + x] *= correction;
                }
            }
        }
    }
}
