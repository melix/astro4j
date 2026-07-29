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
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.stream.DoubleStream;

public class SimpleFunctionCall extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleFunctionCall.class);

    public SimpleFunctionCall(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object applyFunction(String name, Map<String ,Object> arguments, Function<DoubleStream, OptionalDouble> operator) {
        return applyFunction(name, arguments, operator, null);
    }

    /**
     * Applies a reduction to a list of numbers or images. When a streaming reduction is
     * provided, images are unwrapped one at a time instead of being all loaded in memory,
     * which makes it possible to combine large sets of images on machines with little memory.
     *
     * @param reduction the incremental equivalent of {@code operator}, or null if the
     * operator cannot be expressed as a fold (for example a median)
     */
    public Object applyFunction(String name, Map<String ,Object> arguments, Function<DoubleStream, OptionalDouble> operator, StreamingReduction reduction) {
        var list = (List<?>) arguments.get("list");
        if (list.stream().allMatch(i -> i instanceof List<?>)) {
            // either the list size is 1 or all lists have the same size
            if (list.size() == 1) {
                list = (List<?>) list.getFirst();
            } else {
                // make sure that all lists have the same size
                if (list.stream().map(List.class::cast).map(List::size).distinct().count() == 1) {
                    List<Object> result = new ArrayList<>();
                    var size = ((List<?>) list.getFirst()).size();
                    for (int i = 0; i < size; i++) {
                        List<Object> args = new ArrayList<>();
                        for (Object argument : list) {
                            args.add(((List<?>) argument).get(i));
                        }
                        result.add(applyFunction(name, Map.of("list", args), operator, reduction));
                    }
                    return result;
                }
            }
        }
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' must have at least one argument");
        }
        var types = list.stream().map(Object::getClass).distinct().toList();
        if (types.size() > 1) {
            throw new IllegalArgumentException("'" + name + "' only works on arguments of the same type");
        }
        var type = types.getFirst();
        if (Number.class.isAssignableFrom(type)) {
            return operator.apply(list.stream()
                    .map(Number.class::cast)
                    .mapToDouble(Number::doubleValue))
                .orElse(0);
        }
        if (ImageWrapper32.class.isAssignableFrom(type) || FileBackedImage.class.isAssignableFrom(type)) {
            var wrappers = list.stream().map(ImageWrapper.class::cast).toList();
            var first = wrappers.getFirst();
            var width = first.width();
            var height = first.height();
            if (wrappers.stream().anyMatch(i -> i.width() != width || i.height() != height)) {
                throw new IllegalArgumentException(name + " function call failed: all images must have the same dimensions");
            }
            var metadata = MetadataMerger.merge(wrappers, MetadataMerger.averagingMergers());
            if (reduction != null) {
                return new ImageWrapper32(width, height, reduceStreaming(name, wrappers, width, height, reduction), metadata);
            }
            var images = wrappers.stream()
                .map(i -> i instanceof FileBackedImage fbi ? fbi.unwrapToMemory() : i)
                .map(ImageWrapper32.class::cast)
                .toList();
            var length = images.getFirst().data().length;
            if (images.stream().anyMatch(i -> i.data().length != length)) {
                throw new IllegalArgumentException(name + " function call failed: all images must have the same dimensions");
            }
            float[][] result = new float[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int finalY = y;
                    int finalX = x;
                    result[y][x] = (float) operator.apply(images.stream().mapToDouble(img -> img.data()[finalY][finalX])).orElse(0);
                }
            }
            return new ImageWrapper32(width, height, result, metadata);
        }
        throw new IllegalArgumentException("Unexpected argument type '" + type + "'");
    }

    private static float[][] reduceStreaming(String name, List<ImageWrapper> wrappers, int width, int height, StreamingReduction reduction) {
        var accumulator = new double[height][width];
        for (var row : accumulator) {
            Arrays.fill(row, reduction.identity());
        }
        for (var wrapper : wrappers) {
            var unwrapped = wrapper instanceof FileBackedImage fbi ? fbi.unwrapToMemory() : wrapper;
            var data = ((ImageWrapper32) unwrapped).data();
            if (data.length != height) {
                throw new IllegalArgumentException(name + " function call failed: all images must have the same dimensions");
            }
            for (int y = 0; y < height; y++) {
                var accumulatorRow = accumulator[y];
                var dataRow = data[y];
                for (int x = 0; x < width; x++) {
                    accumulatorRow[x] = reduction.accumulator().applyAsDouble(accumulatorRow[x], dataRow[x]);
                }
            }
        }
        var count = wrappers.size();
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = (float) reduction.finisher().finish(accumulator[y][x], count);
            }
        }
        return result;
    }

    /**
     * The incremental form of a reduction over a list of images, so that images can be
     * combined one at a time instead of being all held in memory.
     *
     * @param identity    the value a pixel accumulator starts from
     * @param accumulator combines the running value of a pixel with its next sample
     * @param finisher    turns the accumulated value into the final one, given the image count
     */
    public record StreamingReduction(double identity, DoubleBinaryOperator accumulator, Finisher finisher) {
        public static final StreamingReduction AVERAGE = new StreamingReduction(0, Double::sum, (accumulated, count) -> count == 0 ? 0 : accumulated / count);
        public static final StreamingReduction MAX = new StreamingReduction(Double.NEGATIVE_INFINITY, Math::max, (accumulated, count) -> count == 0 ? 0 : accumulated);
        public static final StreamingReduction MIN = new StreamingReduction(Double.POSITIVE_INFINITY, Math::min, (accumulated, count) -> count == 0 ? 0 : accumulated);

        @FunctionalInterface
        public interface Finisher {
            double finish(double accumulated, int count);
        }
    }
}
