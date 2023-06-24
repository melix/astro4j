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

import me.champeau.a4j.jsolex.expr.ExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.stretching.NegativeImageStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public abstract class AbstractImageExpressionEvaluator extends ExpressionEvaluator {

    @Override
    protected Object plus(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            return Stream.concat(leftList.stream(), rightList.stream()).toList();
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, Double::sum);
    }

    @Override
    protected Object minus(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            var copy = new ArrayList<>(leftList);
            copy.removeAll(rightList);
            return Collections.unmodifiableList(copy);
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a - b);
    }

    @Override
    protected Object mul(Object left, Object right) {
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a * b);
    }

    @Override
    protected Object div(Object left, Object right) {
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a / b);
    }

    @Override
    protected Object functionCall(String name, List<Object> arguments) {
        return switch (name) {
            case "img" -> image(arguments);
            case "avg" -> applyFunction(arguments, DoubleStream::average);
            case "min" -> applyFunction(arguments, DoubleStream::min);
            case "max" -> applyFunction(arguments, DoubleStream::max);
            case "invert" -> inverse(arguments);
            case "range" -> createRange(arguments);
            default -> throw new IllegalArgumentException("Unknown function call '" + name + "'");
        };
    }

    private Object inverse(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("invert() call must have a single argument (image)");
        }
        var arg = arguments.get(0);
        if (arg instanceof ImageWrapper32 image) {
            var source = image.data();
            var inverted = new float[source.length];
            System.arraycopy(source, 0, inverted, 0, source.length);
            NegativeImageStrategy.DEFAULT.stretch(inverted);
            return new ImageWrapper32(image.width(), image.height(), inverted);
        } else if (arg instanceof List<?> list) {
            return list.stream().map(e -> inverse(List.of(e))).toList();
        }
        throw new IllegalArgumentException("invert() call must have a single argument (image)");
    }

    protected abstract ImageWrapper32 findImage(int shift);

    private ImageWrapper32 image(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("img() call must have a single argument (image shift)");
        }
        var arg = arguments.get(0);
        if (arg instanceof Number shift) {
            return findImage(shift.intValue());
        }
        throw new IllegalArgumentException("img() argument must be a number representing an image shift");

    }

    private Object applyFunction(List<Object> arguments, Function<DoubleStream, OptionalDouble> operator) {
        if (arguments.size() == 1) {
            if (arguments.get(0) instanceof List<?> list) {
                // unwrap
                //noinspection unchecked
                return applyFunction((List<Object>) list, operator);
            }
        }
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("avg() must have at least one argument");
        }
        var types = arguments.stream().map(Object::getClass).distinct().toList();
        if (types.size() > 1) {
            throw new IllegalArgumentException("avg() only works on arguments of the same type");
        }
        var type = types.get(0);
        if (Number.class.isAssignableFrom(type)) {
            return operator.apply(arguments.stream()
                            .map(Number.class::cast)
                            .mapToDouble(Number::doubleValue))
                    .orElse(0);
        }
        if (ImageWrapper32.class.isAssignableFrom(type)) {
            var images = arguments
                    .stream()
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

    private Object apply(ImageWrapper32 leftImage,
                         ImageWrapper32 rightImage,
                         Number leftScalar,
                         Number rightScalar,
                         DoubleBinaryOperator operator) {
        if (leftImage != null && rightImage != null) {
            var leftData = leftImage.data();
            var rightData = rightImage.data();
            var length = leftData.length;
            var width = leftImage.width();
            var height = leftImage.height();
            if (width != rightImage.width() || height != rightImage.height()) {
                throw new IllegalArgumentException("Both images must have the same dimensions");
            }
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = (float) operator.applyAsDouble(leftData[i], rightData[i]);
            }
            return new ImageWrapper32(width, height, result);
        }
        if (leftImage != null && rightScalar != null) {
            var scalar = rightScalar.doubleValue();
            var leftData = leftImage.data();
            var length = leftData.length;
            var width = leftImage.width();
            var height = leftImage.height();
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = (float) operator.applyAsDouble(leftData[i], scalar);
            }
            return new ImageWrapper32(width, height, result);
        }
        if (rightImage != null && leftScalar != null) {
            var scalar = leftScalar.doubleValue();
            var rightData = rightImage.data();
            var length = rightData.length;
            var width = rightImage.width();
            var height = rightImage.height();
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                result[i] = (float) operator.applyAsDouble(rightData[i], scalar);
            }
            return new ImageWrapper32(width, height, result);
        }
        if (leftScalar != null && rightScalar != null) {
            return operator.applyAsDouble(leftScalar.doubleValue(), rightScalar.doubleValue());
        }
        throw new IllegalArgumentException("Unexpected operand types");
    }

    private ImageWrapper32 asImage(Object source) {
        if (source instanceof ImageWrapper32 image) {
            return image;
        }
        return null;
    }

    private Number asScalar(Object source) {
        if (source instanceof Number number) {
            return number;
        }
        return null;
    }

    private List<ImageWrapper32> createRange(List<Object> arguments) {
        if (arguments.size() < 2) {
            return List.of();
        }
        Number from = asScalar(arguments.get(0));
        Number to = asScalar(arguments.get(1));
        Number step = 1;
        if (arguments.size() == 3) {
            step = asScalar(arguments.get(2));
            if (step == null) {
                step = 1;
            }
        }
        List<ImageWrapper32> images = new ArrayList<>();
        if (from != null && to != null) {
            int fromInt = from.intValue();
            int toInt = to.intValue();
            int stepInt = step.intValue();
            if (fromInt > toInt) {
                int tmp = fromInt;
                fromInt = toInt;
                toInt = tmp;
            }
            for (int i = fromInt; i <= toInt; i += stepInt) {
                images.add(findImage(i));
            }
        }
        return Collections.unmodifiableList(images);
    }
}
