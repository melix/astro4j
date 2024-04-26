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
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;

public class EllipseFit extends AbstractFunctionImpl {
    public EllipseFit(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object fit(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("ellipse_fit takes 1 arguments (image(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("ellipse_fit", arguments, this::fit);
        }
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 image) {
            return performEllipseFitting(image);
        }
        throw new IllegalStateException("Ellipse fitting only works on mono images");
    }

    public ImageWrapper32 performEllipseFitting(ImageWrapper32 image) {
        var task = new EllipseFittingTask(
                getFromContext(Broadcaster.class).orElse(Broadcaster.NO_OP),
                () -> image,
                getFromContext(ProcessParams.class).orElse(null),
                getFromContext(ImageEmitter.class).orElse(null)
        );
        EllipseFittingTask.Result result;
        try {
            result = task.call();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        var copy = image.copy();
        copy.metadata().put(Ellipse.class, result.ellipse());
        return copy;
    }
}
