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
import me.champeau.a4j.jsolex.processing.expr.impl.DiskFill;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.function.Supplier;

public class CoronagraphTask extends AbstractTask<ImageWrapper32> {
    private final Ellipse fitting;
    private final float blackPoint;

    public CoronagraphTask(Broadcaster broadcaster,
                           ProgressOperation operation,
                           Supplier<ImageWrapper32> image,
                           Ellipse fitting,
                           float blackPoint) {
        super(broadcaster, operation, image);
        this.fitting = fitting;
        this.blackPoint = blackPoint;
    }

    @Override
    protected ImageWrapper32 doCall() throws Exception {
        // The disk-fill, background-removal and stretching steps below mutate
        // their target buffer in place. The base class no longer provides a
        // defensive copy, so we make our own owned copy here.
        var work = workImage.copy();
        DiskFill.doFillWithGradient(fitting, work.data(), 0);
        for (int i = 0; i < 2; i++) {
            work = BackgroundRemoval.neutralizeBackground(work);
        }
        var buffer = work.data();
        new ArcsinhStretchingStrategy(0, 50, 50).stretch(work);
        return new ImageWrapper32(width, height, buffer, work.metadata());
    }

}
