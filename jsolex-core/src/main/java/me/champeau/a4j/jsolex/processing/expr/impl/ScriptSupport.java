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

import me.champeau.a4j.jsolex.processing.stretching.NegativeImageStrategy;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.DoubleStream;

public class ScriptSupport {
    private ScriptSupport() {

    }

    @SuppressWarnings("unchecked")
    public static List<Object> expandToImageList(ForkJoinContext forkJoinContext, List<Object> arguments, Function<List<Object>, Object> function) {
        var listOfImages = (List) arguments.get(0);
        var params = arguments.subList(1, arguments.size());
        var collected = Collections.synchronizedMap(new TreeMap<>());
        forkJoinContext.blocking(ctx -> {
            int i = 0;
            for (Object image : listOfImages) {
                var idx = i++;
                ctx.async(() -> {
                    var allArgs = new ArrayList<>();
                    allArgs.add(image);
                    allArgs.addAll(params);
                    var result = function.apply(allArgs);
                    collected.put(idx, result);
                });
            }
        });
        // iterate on keys to preserve order
        return collected.keySet().stream().map(collected::get).toList();
    }

    public static Object monoToMonoImageTransformer(ForkJoinContext forkJoinContext, String name, int maxArgCount, List<Object> arguments, ImageConsumer consumer) {
        if (arguments.size() > maxArgCount) {
            throw new IllegalArgumentException("Invalid number of arguments on '" + name + "' call");
        }
        var arg = arguments.get(0);
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 image) {
            var copy = image.copy();
            consumer.accept(copy.width(), copy.height(), copy.data());
            return copy;
        } else if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, e -> monoToMonoImageTransformer(forkJoinContext, name, maxArgCount, e, consumer));
        }
        throw new IllegalArgumentException(name + "first argument must be a mono image or a list of images");
    }

    public static Object inverse(ForkJoinContext forkJoinContext, List<Object> arguments) {
        return monoToMonoImageTransformer(forkJoinContext, "invert", 1, arguments, NegativeImageStrategy.DEFAULT::stretch);
    }

    public static Object applyFunction(String name, List<Object> arguments, Function<DoubleStream, OptionalDouble> operator) {
        if (arguments.size() == 1) {
            if (arguments.get(0) instanceof List<?> list) {
                // unwrap
                //noinspection unchecked
                return applyFunction(name, (List<Object>) list, operator);
            }
        }
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' must have at least one argument");
        }
        var types = arguments.stream().map(Object::getClass).distinct().toList();
        if (types.size() > 1) {
            throw new IllegalArgumentException("'" + name + "' only works on arguments of the same type");
        }
        var type = types.get(0);
        if (Number.class.isAssignableFrom(type)) {
            return operator.apply(arguments.stream()
                            .map(Number.class::cast)
                            .mapToDouble(Number::doubleValue))
                    .orElse(0);
        }
        if (ImageWrapper32.class.isAssignableFrom(type) || FileBackedImage.class.isAssignableFrom(type)) {
            var images = arguments
                    .stream()
                    .map(i -> i instanceof FileBackedImage fbi ? fbi.unwrapToMemory() : i)
                    .map(ImageWrapper32.class::cast)
                    .toList();
            var first = images.get(0);
            var width = first.width();
            var height = first.height();
            var length = first.data().length;
            if (images.stream().anyMatch(i -> i.data().length != length || i.width() != width || i.height() != height)) {
                throw new IllegalArgumentException("All images must have the same dimensions");
            }
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                var idx = i;
                result[i] = (float) operator.apply(images.stream().mapToDouble(img -> img.data()[idx])).orElse(0);
            }
            return new ImageWrapper32(width, height, result);
        }
        throw new IllegalArgumentException("Unexpected argument type '" + type + "'");
    }

    @FunctionalInterface
    public interface ImageConsumer {
        void accept(int width, int height, float[] data);
    }
}
