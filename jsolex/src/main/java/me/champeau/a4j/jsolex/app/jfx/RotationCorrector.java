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

import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataSupport;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class RotationCorrector {
    private RotationCorrector() {

    }

    public static ImageWrapper rotate(ImageWrapper image, double angle) {
        return (ImageWrapper) MetadataSupport.applyMetadata(String.format(message("rotate.radians.format"), angle), () -> {
            var img = image;
            var imageMath = ImageMath.newInstance();
            if (img instanceof FileBackedImage fileBackedImage) {
                img = fileBackedImage.unwrapToMemory();
            }
            if (img instanceof ImageWrapper32 mono) {
                var trn = imageMath.rotate(mono.asImage(), angle, -1, true);
                return ImageWrapper32.fromImage(trn, mono.metadata());
            } else if (img instanceof ColorizedImageWrapper colorized) {
                var mono = colorized.mono();
                var trn = imageMath.rotate(mono.asImage(), angle, -1, true);
                return new ColorizedImageWrapper(ImageWrapper32.fromImage(trn), colorized.converter(), colorized.metadata());
            } else if (img instanceof RGBImage rgb) {
                var height = rgb.height();
                var width = rgb.width();
                var r = imageMath.rotate(new Image(width, height, rgb.r()), angle, -1, true);
                var g = imageMath.rotate(new Image(width, height, rgb.g()), angle, -1, true);
                var b = imageMath.rotate(new Image(width, height, rgb.b()), angle, -1, true);
                return new RGBImage(r.width(), r.height(), r.data(), g.data(), b.data(), rgb.metadata());
            }
            throw new IllegalArgumentException("Unsupported image type");
        });
    }
}
