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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BackgroundRemoval extends AbstractFunctionImpl {
    public BackgroundRemoval(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object removeBackground(List<Object> arguments) {
        assertExpectedArgCount(arguments, "remove_bg takes 1, 2 or 3 arguments (image(s), [tolerance], [fitting])", 1, 2);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("remove_bg", arguments, this::removeBackground);
        }
        Optional<Ellipse> ellipse = getEllipse(arguments, 2);
        if (ellipse.isEmpty()) {
            throw new IllegalArgumentException("Cannot perform background removal because ellipse isn't found");
        }
        double tolerance;
        if (arguments.size() == 2) {
            tolerance = doubleArg(arguments, 1);
            if (tolerance < 0) {
                throw new IllegalArgumentException("Tolerance should be greater than 0");
            }
        } else {
            tolerance = .9;
        }
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 ref) {
            return monoToMonoImageTransformer("remove_bg", 2, arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var e = ellipse.get();
                    var background = AnalysisUtils.estimateBackground(ref, e);
                    var width = image.width();
                    var height = image.height();
                    var data = image.data();
                    me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.removeBackground(width, height, data, tolerance, background, e);
                } else {
                    throw new IllegalArgumentException("remove_bg only supports mono images");
                }
            });
        }
        throw new IllegalArgumentException("remove_bg only supports mono images");
    }

    public Object neutralizeBackground(List<Object> arguments) {
        assertExpectedArgCount(arguments, "neutralize_bg takes 1 or 2 arguments (image(s), iterations)", 1, 2);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("neutralize_bg", arguments, this::neutralizeBackground);
        }
        var iterations = arguments.size() == 2 ? intArg(arguments, 1) : 1;
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty()) {
                throw new IllegalArgumentException("Cannot perform background neutralization because ellipse isn't found");
            }
            return monoToMonoImageTransformer("neutralize_bg", 2, arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var model = me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.neutralizeBackground(image, iterations).data();
                    for (int y = 0; y < image.height(); y++) {
                        System.arraycopy(model[y], 0, image.data()[y], 0, image.width());
                    }
                } else {
                    throw new IllegalArgumentException("neutralize_bg only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("neutralize_bg only supports mono images");
    }

    public Object backgroundModel(List<Object> arguments) {
        assertExpectedArgCount(arguments, "backgroundModel takes 1 or 2 arguments (image(s), order)", 1, 2);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("backgroundModel", arguments, this::backgroundModel);
        }
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty()) {
                throw new IllegalArgumentException("Cannot perform background neutralization because ellipse isn't found");
            }
            int order = arguments.size() == 2 ? intArg(arguments, 1) : 2;
            return monoToMonoImageTransformer("backgroundModel", 2, arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var model = me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.backgroundModel(image, order).data();
                    for (int y = 0; y < image.height(); y++) {
                        System.arraycopy(model[y], 0, image.data()[y], 0, image.width());
                    }
                } else {
                    throw new IllegalArgumentException("backgroundModel only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("backgroundModel only supports mono images");
    }
}
