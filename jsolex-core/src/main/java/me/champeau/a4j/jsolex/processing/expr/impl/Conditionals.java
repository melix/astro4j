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

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Conditionals extends AbstractFunctionImpl {

    @FunctionalInterface
    public interface DoubleBinaryPredicate {
        boolean test(double a, double b);
    }

    public Conditionals(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object ifeq(Map<String, Object> arguments) {
        BuiltinFunction.IFEQ.validateArgs(arguments);
        return conditionalSelect("ifeq", arguments, (a, b) -> a == b, Objects::equals);
    }

    public Object ifneq(Map<String, Object> arguments) {
        BuiltinFunction.IFNEQ.validateArgs(arguments);
        return conditionalSelect("ifneq", arguments, (a, b) -> a != b, (a, b) -> !Objects.equals(a, b));
    }

    public Object ifgt(Map<String, Object> arguments) {
        BuiltinFunction.IFGT.validateArgs(arguments);
        return conditionalSelect("ifgt", arguments, (a, b) -> a > b, null);
    }

    public Object ifgte(Map<String, Object> arguments) {
        BuiltinFunction.IFGTE.validateArgs(arguments);
        return conditionalSelect("ifgte", arguments, (a, b) -> a >= b, null);
    }

    public Object iflt(Map<String, Object> arguments) {
        BuiltinFunction.IFLT.validateArgs(arguments);
        return conditionalSelect("iflt", arguments, (a, b) -> a < b, null);
    }

    public Object iflte(Map<String, Object> arguments) {
        BuiltinFunction.IFLTE.validateArgs(arguments);
        return conditionalSelect("iflte", arguments, (a, b) -> a <= b, null);
    }

    @FunctionalInterface
    private interface ObjectBinaryPredicate {
        boolean test(Object a, Object b);
    }

    private Object conditionalSelect(String name,
                                     Map<String, Object> arguments,
                                     DoubleBinaryPredicate numericPredicate,
                                     ObjectBinaryPredicate objectPredicate) {
        var subject = arguments.get("subject");
        var value = arguments.get("value");
        var thenValue = arguments.get("then");
        var elseValue = arguments.get("else");

        if (subject instanceof List<?>) {
            return expandToImageList(name, "subject", arguments,
                    args -> conditionalSelect(name, args, numericPredicate, objectPredicate));
        }

        boolean conditionMet = evaluateCondition(name, subject, value, numericPredicate, objectPredicate);
        return conditionMet ? thenValue : elseValue;
    }

    private boolean evaluateCondition(String name,
                                      Object subject,
                                      Object value,
                                      DoubleBinaryPredicate numericPredicate,
                                      ObjectBinaryPredicate objectPredicate) {
        var subjectImage = unwrapToImage(subject);
        var valueImage = unwrapToImage(value);
        var subjectScalar = asScalar(subject);
        var valueScalar = asScalar(value);

        // Image comparison: ALL pixels must satisfy the condition
        if (subjectImage != null && valueImage != null) {
            return allPixelsSatisfy(subjectImage, valueImage, numericPredicate);
        }
        if (subjectImage != null && valueScalar != null) {
            return allPixelsSatisfy(subjectImage, valueScalar, numericPredicate);
        }
        if (subjectScalar != null && valueImage != null) {
            return allPixelsSatisfy(subjectScalar, valueImage, numericPredicate);
        }

        // Scalar comparison
        if (subjectScalar != null && valueScalar != null) {
            return numericPredicate.test(subjectScalar, valueScalar);
        }

        // Object comparison (for IFEQ/IFNEQ with strings, etc.)
        if (objectPredicate != null) {
            return objectPredicate.test(subject, value);
        }

        throw new IllegalArgumentException(name + " requires numeric or image arguments for comparison operators");
    }

    private static boolean allPixelsSatisfy(ImageWrapper32 subjectImage, ImageWrapper32 valueImage, DoubleBinaryPredicate predicate) {
        int width = subjectImage.width();
        int height = subjectImage.height();
        if (width != valueImage.width() || height != valueImage.height()) {
            throw new IllegalArgumentException("Images must have the same dimensions");
        }
        var subjectData = subjectImage.data();
        var valueData = valueImage.data();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!predicate.test(subjectData[y][x], valueData[y][x])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean allPixelsSatisfy(ImageWrapper32 image, double scalar, DoubleBinaryPredicate predicate) {
        var data = image.data();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (!predicate.test(data[y][x], scalar)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean allPixelsSatisfy(double scalar, ImageWrapper32 image, DoubleBinaryPredicate predicate) {
        var data = image.data();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (!predicate.test(scalar, data[y][x])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ImageWrapper32 unwrapToImage(Object obj) {
        if (obj instanceof ImageWrapper wrapper) {
            var unwrapped = wrapper.unwrapToMemory();
            if (unwrapped instanceof ImageWrapper32 img) {
                return img;
            }
        }
        return null;
    }

    private static Double asScalar(Object obj) {
        if (obj instanceof Number num) {
            return num.doubleValue();
        }
        return null;
    }
}
