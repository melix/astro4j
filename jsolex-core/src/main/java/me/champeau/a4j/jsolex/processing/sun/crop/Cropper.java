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

/**
 * An utility class which performs cropping of the sun disk to a square.
 */
public class Cropper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Cropper.class);

    private Cropper() {

    }

    public static Image cropToSquare(Image image, Ellipse sunDisk) {
        var source = image.data();
        // at this stage, the new fitting should give us a good estimate of the center and radius
        // because if geometry correction worked, the disk should be circle, so we can crop to a square
        var center = sunDisk.center();
        var cx = center.a();
        var cy = center.b();
        var semiAxis = sunDisk.semiAxis();
        var diameter = (semiAxis.a() + semiAxis.b());
        var croppedSize = 1.2d * diameter;
        LOGGER.info("Diameter {}", diameter);
        var croppedWidth = (int) Math.round(Math.min(image.width(), croppedSize));
        var croppedHeight = (int) Math.round(Math.min(image.height(), croppedSize));
        var square = Math.max(croppedWidth, croppedHeight);
        var xOffset = croppedWidth < square ? (square - croppedWidth) / 2 : 0;
        var yOffset = croppedHeight < square ? (square - croppedHeight) / 2 : 0;
        var cropped = new float[square * square];
        var dx = cx - croppedWidth / 2d;
        var dy = cy - croppedHeight / 2d;
        for (int y = 0; y < croppedHeight; y++) {
            for (int x = 0; x < croppedWidth; x++) {
                int idx = (x + xOffset) + (y + yOffset) * square;
                int sourceX = (int) Math.round(dx + x);
                int sourceY = (int) Math.round(dy + y);
                if (sourceX >= 0 && sourceY >= 0 && sourceX < image.width() && sourceY < image.height()) {
                    cropped[idx] = source[sourceX + sourceY * image.width()];
                }
            }
        }
        return new Image(square, square, cropped);
    }
}
