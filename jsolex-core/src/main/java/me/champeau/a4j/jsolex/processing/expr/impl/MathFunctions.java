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
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

public class MathFunctions extends AbstractFunctionImpl {
    public MathFunctions(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }
    
    public Object pow(List<Object> arguments) {
        return applyBinary(arguments, "pow", "exponent", Math::pow);
    }

    public Object log(List<Object> arguments) {
        return applyBinary(arguments, "log", "base", (a, b) -> Math.log(a) / Math.log(b));
    }

    public Object exp(List<Object> arguments) {
        return applyUnary(arguments, "exp", Math::exp);
    }

    private Object applyUnary(List<Object> arguments, String name, DoubleUnaryOperator function) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException(name + " takes 1 argument (image(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, list -> applyUnary(list, name, function));
        }
        var img = arguments.get(0);
        return applyUnary(img, function);
    }

    private Object applyBinary(List<Object> arguments, String name, String argName, DoubleBinaryOperator function) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException(name + " takes 2 arguments (image(s), " + argName + ")");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, list -> applyBinary(list, name, argName, function));
        }
        var img = arguments.get(0);
        var argument = doubleArg(arguments, 1);
        var unary = (DoubleUnaryOperator) v -> function.applyAsDouble(v, argument);
        return applyUnary(img, unary);
    }

    private Object applyUnary(Object img, DoubleUnaryOperator unary) {
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            var copy = mono.copy();
            applyFunction(copy.data(), unary);
            return copy;
        } else if (img instanceof ColorizedImageWrapper colorized) {
            var copy = colorized.copy();
            applyFunction(copy.mono().data(), unary);
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var copy = rgb.copy();
            applyFunction(copy.r(), unary);
            applyFunction(copy.g(), unary);
            applyFunction(copy.b(), unary);
            return copy;
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    private static void applyFunction(float[] data, DoubleUnaryOperator function) {
        for (var i = 0; i < data.length; i++) {
            data[i] = (float) function.applyAsDouble(data[i]);
        }
    }
}
