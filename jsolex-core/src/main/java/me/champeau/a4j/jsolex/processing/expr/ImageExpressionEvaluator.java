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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.function.Function;

public class ImageExpressionEvaluator extends AbstractImageExpressionEvaluator {
    private final Function<Double, ImageWrapper> images;

    public ImageExpressionEvaluator(ForkJoinContext forkJoinContext, Function<Double, ImageWrapper> images) {
        super(forkJoinContext);
        this.images = images;
    }

    protected ImageWrapper findImage(double shift) {
        var image = images.apply(shift);
        if (image == null) {
            throw new IllegalArgumentException("Image for shift '" + shift + "' is missing");
        }
        return image;
    }
}
