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
package me.champeau.a4j.jsolex.processing.event;

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

public final class ProcessingDoneEvent extends ProcessingEvent<ProcessingDoneEvent.Outcome> {
    public ProcessingDoneEvent(ProcessingDoneEvent.Outcome payload) {
        super(payload);
    }

    public record Outcome(
        long timestamp,
        Map<Double, ImageWrapper> shiftImages,
        ImageEmitter customImageEmitter,
        Ellipse mainEllipse,
        Ellipse ellipse,
        ImageStats imageStats,
        List<RedshiftArea> redshifts,
        DoubleUnaryOperator polynomial,
        float[][] averageImage,
        ProcessParams processParams,
        PixelShiftRange pixelShiftRange,
        int detectedActiveRegions) {

    }

    public static ProcessingDoneEvent of(long timestamp,
                                         Map<Double, ImageWrapper> images,
                                         ImageEmitter customImageEmitter,
                                         Ellipse mainEllipse,
                                         Ellipse ellipse,
                                         ImageStats imageStats,
                                         List<RedshiftArea> redshifts,
                                         DoubleUnaryOperator polynomial,
                                         float[][] averageImage,
                                         ProcessParams processParams,
                                         PixelShiftRange pixelShiftRange,
                                         int detectedActiveRegions) {
        return new ProcessingDoneEvent(new Outcome(timestamp, Collections.unmodifiableMap(images), customImageEmitter, mainEllipse, ellipse, imageStats, redshifts, polynomial, averageImage, processParams, pixelShiftRange, detectedActiveRegions));
    }
}
