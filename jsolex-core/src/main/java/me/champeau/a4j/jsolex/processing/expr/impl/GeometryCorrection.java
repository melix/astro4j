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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowResults;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class GeometryCorrection extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryCorrection.class);
    private final EllipseFit ellipseFit;

    public GeometryCorrection(Map<Class<?>, Object> context, Broadcaster broadcaster, EllipseFit ellipseFit) {
        super(context, broadcaster);
        this.ellipseFit = ellipseFit;
    }

    public Object fixGeometry(List<Object> arguments) {
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("fix_geometry", arguments, this::fixGeometry);
        }
        if (arg instanceof FileBackedImage fbi) {
            arg = fbi.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 tmp) {
            return fixGeometry(tmp);
        }
        throw new IllegalArgumentException("fix_geometry only supports mono images");
    }

    public ImageWrapper32 fixGeometry(ImageWrapper32 tmp) {
        var ellipse = tmp.findMetadata(Ellipse.class);
        if (ellipse.isEmpty()) {
            LOGGER.info(message("miss.ellipse.fit"));
            tmp = ellipseFit.performEllipseFitting(tmp);
        }
        var image = tmp;
        var state = new WorkflowState(image.width(), image.height(), 0);
        state.recordResult(WorkflowResults.RECONSTRUCTED, image);
        var task = new GeometryCorrector(
            getFromContext(Broadcaster.class).orElse(Broadcaster.NO_OP),
            newOperation(),
            () -> image,
            image.findMetadata(Ellipse.class).orElse(null),
            null,
            null,
            null,
            0,
            getFromContext(ProcessParams.class).orElse(ProcessParams.loadDefaults()),
            getFromContext(ImageEmitter.class).orElse(null),
            state,
            null
        );
        try {
            return (ImageWrapper32) task.get().corrected().unwrapToMemory();
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
    }
}
