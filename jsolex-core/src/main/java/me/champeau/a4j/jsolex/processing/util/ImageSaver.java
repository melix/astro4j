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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An utility responsible for saving images after
 * applying a stretching strategy.
 */
public class ImageSaver {
    private final StretchingStrategy stretchingStrategy;
    private final ProcessParams processParams;
    private final Set<ImageFormat> imageFormats;

    public ImageSaver(StretchingStrategy stretchingStrategy, ProcessParams processParams, Set<ImageFormat> imageFormats) {
        this.stretchingStrategy = stretchingStrategy;
        this.processParams = processParams;
        this.imageFormats = imageFormats;
    }


    public List<File> save(ImageWrapper image, File target) {
        var files = List.<File>of();
        if (image instanceof FileBackedImage fbi) {
            image = fbi.unwrapToMemory();
        }
        if (image instanceof ImageWrapper32 mono) {
            var stretched = stretch(mono);
            files = ImageUtils.writeMonoImage(image.width(), image.height(), stretched.data(), target, imageFormats);
            if (imageFormats.contains(ImageFormat.FITS)) {
                var fits = toFits(target);
                files = Stream.concat(files.stream(), Stream.of(fits)).toList();
                FitsUtils.writeFitsFile(stretched, fits, processParams);
            }
        } else if (image instanceof RGBImage rgb) {
            var stretched = stretch(rgb);
            var r = stretched.r();
            var g = stretched.g();
            var b = stretched.b();
            files = ImageUtils.writeRgbImage(rgb.width(), rgb.height(), r, g, b, target, imageFormats);
            if (imageFormats.contains(ImageFormat.FITS)) {
                var fits = toFits(target);
                files = Stream.concat(files.stream(), Stream.of(fits)).toList();
                FitsUtils.writeFitsFile(new RGBImage(image.width(), image.height(), r, g, b, rgb.metadata()), fits, processParams);
            }
        }
        return files;
    }

    private ImageWrapper32 stretch(ImageWrapper32 image) {
        var copy = image.copy();
        stretchingStrategy.stretch(copy);
        return copy;
    }

    private RGBImage stretch(RGBImage image) {
        var copy = image.copy();
        stretchingStrategy.stretch(copy);
        return copy;
    }

    private File toFits(File target) {
        var endIndex = target.getName().lastIndexOf(".");
        String fileNameWithoutExtension = endIndex >= 0 ? target.getName().substring(0, endIndex) : target.getName();
        return new File(target.getParentFile(), fileNameWithoutExtension + ".fits");
    }
}
