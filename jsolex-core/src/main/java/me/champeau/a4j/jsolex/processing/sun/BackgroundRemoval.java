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

import me.champeau.a4j.math.regression.Ellipse;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;

public class BackgroundRemoval {
    private BackgroundRemoval() {

    }

    public static void removeBackground(int width,
                                        int height,
                                        float[] data,
                                        double tolerance,
                                        double background,
                                        Ellipse ellipse) {
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offset = y * width + x;
                if (!ellipse.isWithin(x, y)) {
                    var v = data[offset];
                    var offcenter = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius;
                    var correction = tolerance * (offcenter * offcenter) * background;
                    var corrected = Math.max(0, v - correction);
                    data[offset] = (float) corrected;
                }
            }
        }
        // perform bilinear interpolation at edges for smoothing
        bilinearSmoothing(ellipse, width, height, data);
    }
}
