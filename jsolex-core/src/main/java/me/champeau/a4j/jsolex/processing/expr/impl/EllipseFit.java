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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;

public class EllipseFit extends AbstractFunctionImpl {
    public EllipseFit(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object fit(Map<String ,Object> arguments) {
        BuiltinFunction.ELLIPSE_FIT.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("ellipse_fit", "img", arguments, this::fit);
        }
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 image) {
            return performEllipseFitting(image);
        }
        var rgb = (RGBImage) arg;
        var ellipseFitting = performEllipseFitting(rgb.toMono());
        var rgbCopy = rgb.copy();
        rgbCopy.metadata().put(Ellipse.class, ellipseFitting.findMetadata(Ellipse.class).orElse(null));
        return rgbCopy;
    }

    public ImageWrapper32 performEllipseFitting(ImageWrapper32 image) {
        var task = new EllipseFittingTask(
                getFromContext(Broadcaster.class).orElse(Broadcaster.NO_OP),
                newOperation(),
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
