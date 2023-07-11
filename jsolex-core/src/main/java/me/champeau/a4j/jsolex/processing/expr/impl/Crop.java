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

import me.champeau.a4j.jsolex.processing.sun.crop.Cropper;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class Crop extends AbstractFunctionImpl {
    public Crop(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object autocrop(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("autocrop takes 1 arguments (image(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::autocrop);
        }
        var ellipse = getFromContext(Ellipse.class);
        var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
        if (ellipse.isPresent()) {
            var circle = ellipse.get();
            if (arg instanceof ImageWrapper32 mono) {
                var image = mono.asImage();
                var cropped = Cropper.cropToSquare(image, circle, blackpoint);
                return ImageWrapper32.fromImage(cropped);
            } else if (arg instanceof ColorizedImageWrapper wrapper) {
                var mono = wrapper.mono();
                var cropped = Cropper.cropToSquare(mono.asImage(), circle, blackpoint);
                return new ColorizedImageWrapper(ImageWrapper32.fromImage(cropped), wrapper.converter());
            }
            throw new IllegalStateException("Unsupported image type");
        } else {
            return arg;
        }
    }
}
