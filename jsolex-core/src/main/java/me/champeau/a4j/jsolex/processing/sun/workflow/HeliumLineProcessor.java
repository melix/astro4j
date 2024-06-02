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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.expr.ImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * A workflow dedicated to generating an helium image.
 */
public class HeliumLineProcessor {

    private final ProcessParams processParams;
    private final PixelShiftRange pixelShiftRange;
    private final Map<Double, WorkflowState> imageByPixelShift;
    private final double heliumLineShift;
    private final ImageEmitter imageEmitter;
    private final Broadcaster broadcaster;

    public HeliumLineProcessor(ProcessParams processParams,
                               PixelShiftRange pixelShiftRange,
                               List<WorkflowState> imageList,
                               double heliumLineShift,
                               ImageEmitter imageEmitter,
                               Broadcaster broadcaster) {
        this.processParams = processParams;
        this.pixelShiftRange = pixelShiftRange;
        this.imageByPixelShift = imageList.stream().collect(Collectors.toMap(WorkflowState::pixelShift, s -> s));
        this.heliumLineShift = heliumLineShift;
        this.imageEmitter = imageEmitter;
        this.broadcaster = broadcaster;
    }

    public void process() {
        var source = findEnhancedImage(heliumLineShift);
        if (source == null) {
            return;
        }
        var evaluator = new ImageExpressionEvaluator(broadcaster, this::findEnhancedImage);
        evaluator.putInContext(PixelShiftRange.class, pixelShiftRange);
        var continuum = evaluator.createContinuumImage();
        var raw = evaluator.minus(source, continuum);
        if (raw instanceof ImageWrapper32 image) {
            var ellipse = image.findMetadata(Ellipse.class).orElse(null);
            for (int i = 0; i < 64; i++) {
                BandingReduction.reduceBanding(image.width(), image.height(), image.data(), 8, ellipse);
            }
            new AutohistogramStrategy(processParams.autoStretchParams().gamma()).stretch(image);
            imageEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, "Helium D3", "helium", image);
            var profile = SpectralRayIO.loadDefaults()
                .stream()
                .filter(r -> SpectralRay.HELIUM_D3.label().equals(r.label()))
                .findFirst()
                .orElse(SpectralRay.HELIUM_D3)
                .label();
            if (evaluator.functionCall(BuiltinFunction.COLORIZE, List.of(image, profile)) instanceof ColorizedImageWrapper colorized) {
                imageEmitter.newColorImage(GeneratedImageKind.COLORIZED, MessageFormat.format(message("colorized"), profile), "helium", image.width(), image.height(), new HashMap<>(image.metadata()), () -> colorized.converter().apply(image));
            }
        }
    }

    private ImageWrapper findEnhancedImage(double shift) {
        var workflowState = imageByPixelShift.get(shift);
        if (workflowState == null) {
            return null;
        }
        var result = workflowState.<GeometryCorrector.Result>findResult(WorkflowResults.GEOMETRY_CORRECTION);
        return result.map(GeometryCorrector.Result::enhanced).orElse(null);
    }
}
