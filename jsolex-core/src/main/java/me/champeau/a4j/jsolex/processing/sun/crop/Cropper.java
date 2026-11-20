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

import me.champeau.a4j.math.image.ImagePlane;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * An utility class which performs cropping of the sun disk to a square.
 */
public class Cropper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Cropper.class);

    private Cropper() {

    }

    public static CropResult cropToSquare(ImagePlane image, Ellipse sunDisk, float blackPoint, Double diameterFactor, Integer rounding) {
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
        LOGGER.info(message("diameter"), String.format("%.2f", diameter));
        var half = square / 2;
        var originX = cropOrigin(cx, half);
        var originY = cropOrigin(cy, half);
        return new CropResult(
                crop(image, originX, originY, square, square, blackPoint),
                new DoublePair(originX, originY)
        );
    }

    private static int cropOrigin(double center, int halfSize) {
        return (int) Math.round(center) - halfSize;
    }

    public static CropResult cropToRectangle(ImagePlane image, Ellipse sunDisk, float blackPoint, int width, int height) {
        var center = sunDisk.center();
        var cx = center.a();
        var cy = center.b();
        var originX = cropOrigin(cx, width / 2);
        var originY = cropOrigin(cy, height / 2);
        return new CropResult(
                crop(image, originX, originY, width, height, blackPoint),
                new DoublePair(originX, originY)
        );
    }

    private static ImagePlane crop(ImagePlane source, int originX, int originY, int width, int height, float blackPoint) {
        var cropped = ImagePlane.allocate(width, height);
        var row = new float[width];
        var fromX = Math.max(0, originX);
        var toX = Math.min(source.width(), originX + width);
        for (int yy = 0; yy < height; yy++) {
            Arrays.fill(row, blackPoint);
            var sourceY = originY + yy;
            if (sourceY >= 0 && sourceY < source.height() && fromX < toX) {
                source.getRow(sourceY, fromX, row, fromX - originX, toX - fromX);
            }
            cropped.setRow(yy, row);
        }
        return cropped;
    }

    public record CropResult(
            ImagePlane cropped,
            DoublePair centerShift
    ) {
    }
}
