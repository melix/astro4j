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

/**
 * An utility responsible for saving images after
 * applying a stretching strategy.
 */
public class ImageSaver {
    private final StretchingStrategy stretchingStrategy;
    private final ProcessParams processParams;

    public ImageSaver(StretchingStrategy stretchingStrategy, ProcessParams processParams) {
        this.stretchingStrategy = stretchingStrategy;
        this.processParams = processParams;
    }


    public void save(ImageWrapper image, File target) {
        if (image instanceof ImageWrapper32 mono) {
            float[] stretched = stretch(mono.data());
            ImageUtils.writeMonoImage(image.width(), image.height(), stretched, target);
            if (processParams.debugParams().generateFits()) {
                FitsUtils.writeFitsFile(new ImageWrapper32(image.width(), image.height(), stretched), toFits(target), processParams);
            }
        } else if (image instanceof ColorizedImageWrapper colorImage) {
            float[] stretched = stretch(colorImage.mono().data());
            var colorized = colorImage.converter().apply(stretched);
            var r = colorized[0];
            var g = colorized[1];
            var b = colorized[2];
            ImageUtils.writeRgbImage(colorImage.width(), colorImage.height(), r, g, b, target);
            if (processParams.debugParams().generateFits()) {
                FitsUtils.writeFitsFile(new RGBImage(image.width(), image.height(), r, g, b), toFits(target), processParams);
            }
        } else if (image instanceof RGBImage rgb) {
            var stretched = stretch(rgb.r(), rgb.g(), rgb.g());
            var r = stretched[0];
            var g = stretched[1];
            var b = stretched[2];
            ImageUtils.writeRgbImage(rgb.width(), rgb.height(), r, g, b, target);
            if (processParams.debugParams().generateFits()) {
                FitsUtils.writeFitsFile(new RGBImage(image.width(), image.height(), r, g, b), toFits(target), processParams);
            }
        }
    }

    private float[] stretch(float[] data) {
        float[] streched = new float[data.length];
        System.arraycopy(data, 0, streched, 0, data.length);
        stretchingStrategy.stretch(streched);
        return streched;
    }

    private float[][] stretch(float[] r, float[] g, float[] b) {
        float[] rr = new float[r.length];
        float[] gg = new float[g.length];
        float[] bb = new float[b.length];
        System.arraycopy(r, 0, rr, 0, r.length);
        System.arraycopy(g, 0, gg, 0, g.length);
        System.arraycopy(b, 0, bb, 0, b.length);
        var rgb = new float[][]{rr, gg, bb};
        stretchingStrategy.stretch(rgb);
        return rgb;
    }

    private File toFits(File target) {
        String fileNameWithoutExtension = target.getName().substring(0, target.getName().lastIndexOf("."));
        return new File(target.getParentFile(), fileNameWithoutExtension + ".fits");
    }
}
