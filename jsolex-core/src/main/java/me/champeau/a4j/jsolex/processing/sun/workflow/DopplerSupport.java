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

import me.champeau.a4j.jsolex.processing.expr.impl.DiskFill;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Arrays;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class DopplerSupport {
    private final ProcessParams processParams;
    private final List<WorkflowState> states;
    private final ImageEmitter processedImagesEmitter;

    public DopplerSupport(ProcessParams processParams, List<WorkflowState> states, ImageEmitter processedImagesEmitter) {
        this.processParams = processParams;
        this.states = states;
        this.processedImagesEmitter = processedImagesEmitter;
    }

    public void produceDopplerImage() {
        if (!SpectralRay.H_ALPHA.equals(processParams.spectrumParams().ray())) {
            return;
        }
        var dopplerShift = processParams.spectrumParams().dopplerShift();
        double lookupShift = processParams.spectrumParams().switchRedBlueChannels() ? -dopplerShift : dopplerShift;
        var first = states.stream().filter(s -> s.pixelShift() == lookupShift).findFirst();
        var second = states.stream().filter(s -> s.pixelShift() == -lookupShift).findFirst();
        first.ifPresent(s1 -> second.ifPresent(s2 -> {
            s1.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(i1 -> s2.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(i2 -> {
                var grey1 = (ImageWrapper32) ((GeometryCorrector.Result) i1).corrected().unwrapToMemory();
                var grey2 = (ImageWrapper32) ((GeometryCorrector.Result) i2).corrected().unwrapToMemory();
                var width = grey1.width();
                var height = grey1.height();
                var metadata = MetadataMerger.merge(List.of(grey1, grey2));
                processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER,
                    null, "Doppler",
                    "doppler",
                    message("doppler.description"),
                    width,
                    height,
                    metadata, () -> DopplerSupport.toDopplerImage(width, height, grey1, grey2));
                if (processParams.requestedImages().isEnabled(GeneratedImageKind.DOPPLER_ECLIPSE)) {
                    produceDopplerEclipseImage(grey1, grey2, width, height);
                }
            }));
        }));
    }

    private void produceDopplerEclipseImage(ImageWrapper32 grey1, ImageWrapper32 grey2, int width, int height) {
        var grey1Eclipse = grey1.copy();
        var grey2Eclipse = grey2.copy();
        grey1Eclipse.findMetadata(Ellipse.class).ifPresent(eclipse1 -> {
            var g1 = BackgroundRemoval.neutralizeBackground(grey1Eclipse);
            grey2Eclipse.findMetadata(Ellipse.class).ifPresent(eclipse2 -> {
                var g2 = BackgroundRemoval.neutralizeBackground(grey2Eclipse);
                DiskFill.doFill(eclipse1, g1.data(), 0, null);
                DiskFill.doFill(eclipse2, g2.data(), 0, null);
                var stretch = new ArcsinhStretchingStrategy(0, 50, 50);
                stretch.stretch(g1);
                stretch.stretch(g2);
                var metadata = MetadataMerger.merge(List.of(grey1, grey2));
                processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER_ECLIPSE,
                    null, message("doppler.eclipse"),
                    "doppler-eclipse",
                    message("doppler.eclipse.description"),
                    width,
                    height,
                    metadata, () -> {
                        var dopplerImage = DopplerSupport.toDopplerImage(width, height, g1, g2);
                        RangeExpansionStrategy.DEFAULT.stretch(new RGBImage(width, height, dopplerImage[0], dopplerImage[1], dopplerImage[2], metadata));
                        return dopplerImage;
                    });
            });
        });
    }

    static float[][][] toDopplerImage(int width, int height, ImageWrapper32 grey1, ImageWrapper32 grey2) {
        float[][] r = new float[height][width];
        float[][] g = new float[height][width];
        float[][] b = new float[height][width];
        var d1 = grey1.data();
        var d2 = grey2.data();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                r[y][x] = d1[y][x];
                g[y][x] = Math.min(d1[y][x], d2[y][x]);
                b[y][x] = d2[y][x];
            }
        }
        var rgb = new float[][][]{r, g, b};
        var hsl = ImageUtils.fromRGBtoHSL(rgb);
        var saturation = hsl[1];
        Arrays.stream(saturation).forEach(line -> {
            for (int j = 0; j < line.length; j++) {
                float v = line[j];
                var sat = Math.sqrt(v);
                line[j] = (float) sat;
            }
        });
        ImageUtils.fromHSLtoRGB(hsl, rgb);
        var metadata = MetadataMerger.merge(List.of(grey1, grey2));
        RangeExpansionStrategy.DEFAULT.stretch(new RGBImage(width, height, r, g, b, metadata));
        return rgb;
    }
}
