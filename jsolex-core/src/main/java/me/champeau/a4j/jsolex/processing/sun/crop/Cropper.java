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
package me.champeau.a4j.jsolex.processing.sun.crop;

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * An utility class which performs cropping of the sun disk to a square.
 */
public class Cropper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Cropper.class);

    private Cropper() {

    }

    public static Image cropToSquare(Image image, Ellipse sunDisk, float blackPoint, Double diameterFactor, Integer rounding) {
        var source = image.data();
        // at this stage, the new fitting should give us a good estimate of the center and radius
        // because if geometry correction worked, the disk should be circle, so we can crop to a square
        var center = sunDisk.center();
        var cx = center.a();
        var cy = center.b();
        var semiAxis = sunDisk.semiAxis();
        var diameter = (semiAxis.a() + semiAxis.b());
        var width = image.width();
        var height = image.height();
        var square = diameter > width || diameter > height ? Math.max(width, height) : Math.min(width, height);
        if (diameterFactor != null) {
            square = (int) (diameter * diameterFactor);
            int remainder = square % rounding;
            if (remainder != 0) {
                int closestMultiple = (remainder <= rounding / 2) ? -remainder : (rounding - remainder);
                square += closestMultiple;
            }
        }
        LOGGER.info("Diameter {}", diameter);
        var half = square / 2;
        var cropped = new float[square * square];
        Arrays.fill(cropped, blackPoint);
        for (int yy = 0; yy < square; yy++) {
            for (int xx = 0; xx < square; xx++) {
                int sourceX = (int) cx - half + xx;
                int sourceY = (int) cy - half + yy;
                if (sourceX >= 0 && sourceY >= 0 && sourceX < width && sourceY < height) {
                    cropped[xx + yy * square] = source[sourceX + sourceY * width];
                }
            }
        }
        return new Image(square, square, cropped);
    }
}
