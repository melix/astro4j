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

import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class Rotate extends AbstractFunctionImpl {
    private final ImageMath imageMath = ImageMath.newInstance();

    public Rotate(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    public Object rotateLeft(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_left takes 1 arguments (image(s))", 1, 1);
        return doRotate(arguments, image -> arbitraryRotation(arguments, image, -Math.PI / 2), -Math.PI / 2);
    }

    public Object rotateRight(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_right takes 1 arguments (image(s))", 1, 1);
        return doRotate(arguments, image -> arbitraryRotation(arguments, image, Math.PI / 2), Math.PI / 2);
    }

    public Object rotateDegrees(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_deg takes 2 to 4 arguments (image(s), angle, [blackPoint], [resize])", 1, 4);
        var angle = Math.toRadians(doubleArg(arguments, 1));
        return doRotate(arguments, image -> arbitraryRotation(arguments, image, angle), angle);
    }

    public Object rotateRadians(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_rad takes 2 to 4 arguments (image(s), angle, [blackPoint], [resize])", 1, 4);
        var angle = doubleArg(arguments, 1);
        return doRotate(arguments, image -> arbitraryRotation(arguments, image, angle), angle);
    }

    private Image arbitraryRotation(List<Object> arguments, Image image, double angle) {
        var blackpoint = getArgument(Number.class, arguments, 2)
                .map(Number::floatValue)
                .or(() -> getFromContext(ImageStats.class).map(ImageStats::blackpoint))
                .orElse(0f);
        var resize = getArgument(Number.class, arguments, 3)
                .map(Number::intValue)
                .map(i -> i == 1)
                .orElse(false);
        return imageMath.rotate(image, angle, blackpoint, resize);
    }

    private Object doRotate(List<Object> arguments, UnaryOperator<Image> rotateFunction, double angle) {
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::rotateLeft);
        }
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 mono) {
            var trn = rotateFunction.apply(mono.asImage());
            return ImageWrapper32.fromImage(trn, fixMetadata(mono, angle));
        } else if (arg instanceof ColorizedImageWrapper colorized) {
            var mono = colorized.mono();
            var trn = rotateFunction.apply(mono.asImage());
            var md = fixMetadata(mono, angle);
            return new ColorizedImageWrapper(ImageWrapper32.fromImage(trn, md), colorized.converter(), md);
        } else if (arg instanceof RGBImage rgb) {
            var height = rgb.height();
            var width = rgb.width();
            var md = fixMetadata(rgb, angle);
            var r = rotateFunction.apply(new Image(width, height, rgb.r()));
            var g = rotateFunction.apply(new Image(width, height, rgb.g()));
            var b = rotateFunction.apply(new Image(width, height, rgb.b()));
            return new RGBImage(r.width(), r.height(), r.data(), g.data(), b.data(), md);
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    private static Map<Class<?>, Object> fixMetadata(ImageWrapper wrapper, double angle) {
        var metadata = new LinkedHashMap<>(wrapper.metadata());
        wrapper.findMetadata(Ellipse.class).ifPresent(ellipse -> {
            var sx = wrapper.width() / 2;
            var sy = wrapper.height() / 2;
            metadata.put(Ellipse.class, ellipse.rotate(-angle, new Point2D(sx, sy)));
        });
        return metadata;
    }
}
