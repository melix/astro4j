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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

public class AnalysisUtils {
    public static double estimateBlackPoint(ImageWrapper32 image, Ellipse ellipse) {
        var width = image.width();
        var height = image.height();
        var buffer = image.data();
        double blackEstimate = Double.MAX_VALUE;
        int cpt = 0;
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!ellipse.isWithin(x, y)) {
                    var v = buffer[x + y * width];
                    if (v > 0) {
                        var offcenter = 2 * Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (width + height);
                        blackEstimate = blackEstimate + (offcenter * v - blackEstimate) / (++cpt);
                    }
                }
            }
        }
        return blackEstimate;
    }

    public static double estimateBackground(ImageWrapper32 image, Ellipse ellipse) {
        var width = image.width();
        var height = image.height();
        var buffer = image.data();
        double avg = Double.MAX_VALUE;
        int cpt = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!ellipse.isWithin(x, y)) {
                    var v = buffer[x + y * width];
                    if (v > 0) {
                        avg = avg + (v - avg) / (++cpt);
                    }
                }
            }
        }
        return avg;
    }
}
