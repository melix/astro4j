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

    public Object removeBackground(Map<String ,Object> arguments) {
        BuiltinFunction.REMOVE_BG.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("remove_bg", "img", arguments, this::removeBackground);
        }
        Optional<Ellipse> ellipse = getEllipse(arguments, "ellipse");
        if (ellipse.isEmpty()) {
            throw new IllegalArgumentException("Cannot perform background removal because ellipse isn't found");
        }
        double tolerance = doubleArg(arguments, "tolerance", .9);
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 ref) {
            return monoToMonoImageTransformer("remove_bg", "img", arguments, src -> {
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

    public Object neutralizeBackground(Map<String ,Object> arguments) {
        BuiltinFunction.NEUTRALIZE_BG.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("neutralize_bg", "img", arguments, this::neutralizeBackground);
        }
        var iterations = intArg(arguments, "iterations", 1);
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty()) {
                throw new IllegalArgumentException("Cannot perform background neutralization because ellipse isn't found");
            }
            return monoToMonoImageTransformer("neutralize_bg", "img", arguments, src -> {
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

    public Object backgroundModel(Map<String ,Object> arguments) {
        BuiltinFunction.BG_MODEL.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("backgroundModel", "img", arguments, this::backgroundModel);
        }
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty()) {
                throw new IllegalArgumentException("Cannot perform background neutralization because ellipse isn't found");
            }
            int order = intArg(arguments, "order", 2);
            double sigma = doubleArg(arguments, "sigma", 2.5);
            return monoToMonoImageTransformer("backgroundModel", "img", arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    Optional<ImageWrapper32> optionalModel = me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.backgroundModel(image, order, sigma);
                    optionalModel.ifPresent(model -> {
                        var data = model.data();
                        for (int y = 0; y < image.height(); y++) {
                            System.arraycopy(data[y], 0, image.data()[y], 0, image.width());
                        }
                    });
                } else {
                    throw new IllegalArgumentException("backgroundModel only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("backgroundModel only supports mono images");
    }
}
