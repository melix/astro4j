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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.List;

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
                var grey1 = ((GeometryCorrector.Result) i1).corrected();
                var grey2 = ((GeometryCorrector.Result) i2).corrected();
                var width = grey1.width();
                var height = grey1.height();
                processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER,
                    "Doppler",
                    "doppler",
                    width,
                    height,
                    () -> DopplerSupport.toDopplerImage(width, height, grey1, grey2));
            }));
        }));
    }

    static float[][] toDopplerImage(int width, int height, ImageWrapper32 grey1, ImageWrapper32 grey2) {
        float[] r = new float[width * height];
        float[] g = new float[width * height];
        float[] b = new float[width * height];
        var d1 = grey1.data();
        var d2 = grey2.data();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var idx = x + y * width;
                r[idx] = d1[idx];
                g[idx] = (d1[idx] + d2[idx]) / 2;
                b[idx] = d2[idx];

            }
        }
        var rgb = new float[][]{r, g, b};
        var hsl = ImageUtils.fromRGBtoHSL(rgb);
        var saturation = hsl[1];
        for (int i = 0; i < saturation.length; i++) {
            var sat = Math.sqrt(saturation[i]);
            saturation[i] = (float) sat;
        }
        ImageUtils.fromHSLtoRGB(hsl, rgb);
        var metadata = MutableMap.<Class<?>, Object>of();
        metadata.putAll(grey1.metadata());
        metadata.putAll(grey2.metadata());
        RangeExpansionStrategy.DEFAULT.stretch(new RGBImage(width, height, r, g, b, metadata));
        return rgb;
    }
}
