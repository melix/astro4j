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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.function.Function;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class ImageExpressionEvaluator extends AbstractImageExpressionEvaluator {
    protected final Function<PixelShift, ImageWrapper> images;

    public ImageExpressionEvaluator(Broadcaster broadcaster, Function<PixelShift, ImageWrapper> images) {
        super(broadcaster);
        this.images = images;
    }

    public ImageWrapper findImage(PixelShift shift) {
        var image = images.apply(shift);
        if (image == null) {
            throw new IllegalArgumentException(String.format(message("missing.shift"), shift.pixelShift()));
        }
        return image;
    }
}
