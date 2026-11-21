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
package me.champeau.a4j.jsolex.app.jfx;

import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.math.image.ImagePlane;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataSupport;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

import java.util.LinkedHashMap;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Utility class for applying geometric corrections to images.
 */
public class Corrector {
    private Corrector() {

    }

    /**
     * Rotates an image by the specified angle.
     * @param image the image to rotate
     * @param angle the rotation angle in radians
     * @param resize whether to resize the output to fit the rotated image
     * @return the rotated image
     */
    public static ImageWrapper rotate(ImageWrapper image, double angle, boolean resize) {
        return (ImageWrapper) MetadataSupport.applyMetadata(String.format(message("rotate.radians.format"), angle), () -> {
            var imageMath = ImageMath.newInstance();
            if (image instanceof ImageWrapper32 mono) {
                var trn = imageMath.rotate(mono.asImage(), angle, -1, resize);
                return ImageWrapper32.fromImage(trn, Rotate.fixMetadata(mono, angle, trn.width(), trn.height()));
            } else if (image instanceof RGBImage rgb) {
                var height = rgb.height();
                var width = rgb.width();
                var r = imageMath.rotate(new Image(width, height, rgb.red().toArray()), angle, -1, resize);
                var g = imageMath.rotate(new Image(width, height, rgb.green().toArray()), angle, -1, resize);
                var b = imageMath.rotate(new Image(width, height, rgb.blue().toArray()), angle, -1, resize);
                return new RGBImage(r.width(), r.height(), r.data(), g.data(), b.data(), Rotate.fixMetadata(rgb, angle, r.width(), r.height()));
            }
            throw new IllegalArgumentException("Unsupported image type");
        });
    }

    /**
     * Flips an image vertically.
     * @param image the image to flip
     * @return the vertically flipped image
     */
    public static ImageWrapper verticalFlip(ImageWrapper image) {
        var result = (ImageWrapper) MetadataSupport.applyMetadata(message("flip.vertical"), () -> {
            if (image instanceof ImageWrapper32 mono) {
                return new ImageWrapper32(verticalFlip(mono.pixels()), new LinkedHashMap<>(mono.metadata()));
            } else if (image instanceof RGBImage rgb) {
                return new RGBImage(verticalFlip(rgb.red()), verticalFlip(rgb.green()), verticalFlip(rgb.blue()), rgb.metadata());
            }
            throw new IllegalArgumentException("Unsupported image type");
        });
        result.transformMetadata(ReferenceCoords.class, coords -> coords.addVFlip(result.height()));
        return result;
    }

    private static ImagePlane verticalFlip(ImagePlane source) {
        int height = source.height();
        var flipped = ImagePlane.allocate(source.width(), height);
        var row = new float[source.width()];
        for (int y = 0; y < height; y++) {
            source.getRow(height - y - 1, row);
            flipped.setRow(y, row);
        }
        return flipped;
    }
}
