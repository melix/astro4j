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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Map;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class ImageBandingCorrector extends AbstractTask<ImageWrapper32> {

    private final Ellipse ellipse;
    private final BandingCorrectionParams params;
    private final RotationKind rotation;

    public ImageBandingCorrector(Broadcaster broadcaster,
                                 ProgressOperation operation,
                                 Supplier<ImageWrapper32> image,
                                 Ellipse ellipse,
                                 BandingCorrectionParams params,
                                 RotationKind rotation) {
        super(broadcaster, operation, image);
        this.ellipse = ellipse;
        this.params = params;
        this.rotation = rotation;
    }

    @Override
    protected ImageWrapper32 doCall() throws Exception {
        broadcaster.broadcast(operation.update(0, message("banding.correction")));
        var rotator = new Rotate(Map.of(), Broadcaster.NO_OP);
        var passes = params.passes();
        var bandSize = params.width();
        var width = this.width;
        var height = this.height;
        float[][] buffer = getBuffer();
        var ellipse = this.ellipse;
        if (rotation != RotationKind.NONE) {
            var copy = workImage.copy();
            copy.metadata().put(Ellipse.class, ellipse);
            var rotated = (ImageWrapper32) switch (rotation) {
                case LEFT -> rotator.rotateRight(Map.of("img", copy));
                case RIGHT -> rotator.rotateLeft(Map.of("img", copy));
                default -> workImage;
            };
            width = rotated.width();
            height = rotated.height();
            buffer = rotated.data();
            ellipse = rotated.findMetadata(Ellipse.class).orElse(null);
        }
        for (int i = 0; i < passes; i++) {
            BandingReduction.reduceBanding(width, height, buffer, bandSize, ellipse);
            broadcaster.broadcast(operation.update((i + 1d / passes)));
        }
        broadcaster.broadcast(operation.complete());
        TransformationHistory.recordTransform(workImage, "Banding reduction (band size: " + bandSize + " passes: " + passes + ")");

        // rotate back to original orientation
        if (rotation != RotationKind.NONE) {
            var rotated = (ImageWrapper32) switch (rotation) {
                case LEFT ->
                        rotator.rotateLeft(Map.of("img", new ImageWrapper32(width, height, buffer, MutableMap.of())));
                case RIGHT ->
                        rotator.rotateRight(Map.of("img", new ImageWrapper32(width, height, buffer, MutableMap.of())));
                default -> workImage;
            };
            for (int y = 0; y < this.height; y++) {
                System.arraycopy(rotated.data()[y], 0, getBuffer()[y], 0, this.width);
            }
        }

        return workImage;
    }
}
