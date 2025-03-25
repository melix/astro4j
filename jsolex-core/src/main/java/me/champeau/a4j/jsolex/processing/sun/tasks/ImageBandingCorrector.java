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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class ImageBandingCorrector extends AbstractTask<ImageWrapper32> {

    private final Ellipse ellipse;
    private final BandingCorrectionParams params;

    public ImageBandingCorrector(Broadcaster broadcaster,
                                 ProgressOperation operation,
                                 Supplier<ImageWrapper32> image,
                                 Ellipse ellipse,
                                 BandingCorrectionParams params) {
        super(broadcaster, operation, image);
        this.ellipse = ellipse;
        this.params = params;
    }

    @Override
    protected ImageWrapper32 doCall() throws Exception {
        broadcaster.broadcast(operation.update(0, message("banding.correction")));
        var passes = params.passes();
        var bandSize = params.width();
        for (int i = 0; i < passes; i++) {
            BandingReduction.reduceBanding(width, height, getBuffer(), bandSize, ellipse);
            broadcaster.broadcast(operation.update((i + 1d / passes)));
        }
        broadcaster.broadcast(operation.complete());
        TransformationHistory.recordTransform(workImage, "Banding reduction (band size: " + bandSize + " passes: " + passes + ")");
        return workImage;
    }
}
