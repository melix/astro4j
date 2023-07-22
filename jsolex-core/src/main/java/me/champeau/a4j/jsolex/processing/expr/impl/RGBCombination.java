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

import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.List;

public class RGBCombination {
    private RGBCombination() {

    }

    public static Object combine(List<Object> arguments) {
        if (arguments.size() != 3) {
            throw new IllegalArgumentException("rgb takes 3 arguments (red image, green image, blue image)");
        }
        var ra = arguments.get(0);
        var ga = arguments.get(1);
        var ba = arguments.get(2);
        if (ra instanceof FileBackedImage fileBackedImage) {
            ra = fileBackedImage.unwrapToMemory();
        }
        if (ga instanceof FileBackedImage fileBackedImage) {
            ga = fileBackedImage.unwrapToMemory();
        }
        if (ba instanceof FileBackedImage fileBackedImage) {
            ba = fileBackedImage.unwrapToMemory();
        }
        if (ra instanceof ImageWrapper32 r && ga instanceof ImageWrapper32 g && ba instanceof ImageWrapper32 b) {
            if ((r.width() == g.width()) && (r.width() == b.width())
                && (r.height() == g.height()) && (r.height() == b.height())) {
                return new RGBImage(r.width(), r.height(), r.data(), g.data(), b.data());
            } else {
                throw new IllegalArgumentException("Images must have the same dimensions");
            }
        }
        throw new IllegalArgumentException("rgb only supports mono images as arguments");
    }
}
