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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.applySigmaClippedAverage;
import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.applySigmaClippedMedian;
import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.median;

public class ImageStatistics extends AbstractFunctionImpl {

    public ImageStatistics(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object imgAvg(Map<String, Object> arguments) {
        BuiltinFunction.IMG_AVG.validateArgs(arguments);
        return applyImageStatistic("IMG_AVG", arguments, DoubleStream::average);
    }

    public Object imgAvg2(Map<String, Object> arguments) {
        BuiltinFunction.IMG_AVG2.validateArgs(arguments);
        var sigma = ((Number) arguments.get("sigma")).doubleValue();
        return applyImageStatistic("IMG_AVG2", arguments, stream -> applySigmaClippedAverage(stream, sigma));
    }

    public Object imgMedian(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MEDIAN.validateArgs(arguments);
        return applyImageStatistic("IMG_MEDIAN", arguments, stream -> median(stream));
    }

    public Object imgMedian2(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MEDIAN2.validateArgs(arguments);
        var sigma = ((Number) arguments.get("sigma")).doubleValue();
        return applyImageStatistic("IMG_MEDIAN2", arguments, stream -> applySigmaClippedMedian(stream, sigma));
    }

    public Object imgMin(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MIN.validateArgs(arguments);
        return applyImageStatistic("IMG_MIN", arguments, DoubleStream::min);
    }

    public Object imgMax(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MAX.validateArgs(arguments);
        return applyImageStatistic("IMG_MAX", arguments, DoubleStream::max);
    }

    private Object applyImageStatistic(String name, Map<String, Object> arguments,
                                       Function<DoubleStream, OptionalDouble> operator) {
        var list = flattenToImageList(arguments.get("list"));
        if (list.isEmpty()) {
            throw new IllegalArgumentException(name + " requires at least one image argument");
        }
        if (list.size() == 1) {
            return computeImageStatistic(list.getFirst(), operator);
        }
        return list.stream()
                .map(img -> computeImageStatistic(img, operator))
                .toList();
    }

    private List<ImageWrapper32> flattenToImageList(Object input) {
        var result = new ArrayList<ImageWrapper32>();
        flattenToImageListRecursive(input, result);
        return result;
    }

    private void flattenToImageListRecursive(Object input, List<ImageWrapper32> result) {
        if (input instanceof List<?> list) {
            for (var item : list) {
                flattenToImageListRecursive(item, result);
            }
        } else if (input instanceof ImageWrapper32 img) {
            result.add(img);
        } else if (input instanceof ImageWrapper wrapper) {
            var unwrapped = wrapper.unwrapToMemory();
            if (unwrapped instanceof ImageWrapper32 img) {
                result.add(img);
            }
        }
    }

    private double computeImageStatistic(ImageWrapper32 image,
                                         Function<DoubleStream, OptionalDouble> operator) {
        var data = image.data();
        var height = data.length;
        var width = height > 0 ? data[0].length : 0;
        var stream = IntStream.range(0, height)
                .boxed()
                .flatMapToDouble(y -> IntStream.range(0, width)
                        .mapToDouble(x -> data[y][x]));
        return operator.apply(stream).orElse(0);
    }
}
