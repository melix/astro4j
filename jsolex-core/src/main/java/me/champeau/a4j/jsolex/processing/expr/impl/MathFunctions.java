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
import me.champeau.a4j.jsolex.processing.util.Constants;

import java.util.Map;
import java.util.function.DoubleUnaryOperator;

public class MathFunctions extends AbstractFunctionImpl {
    public MathFunctions(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }
    
    public Object pow(Map<String ,Object> arguments) {
        BuiltinFunction.POW.validateArgs(arguments);
        return applyBinary(arguments, "v", "exp", "pow", Math::pow);
    }

    public Object log(Map<String ,Object> arguments) {
        BuiltinFunction.LOG.validateArgs(arguments);
        return applyBinary(arguments, "v", "exp", "log", (a, b) -> Math.log(a) / Math.log(b));
    }

    public Object exp(Map<String ,Object> arguments) {
        BuiltinFunction.EXP.validateArgs(arguments);
        return applyUnary(arguments, "exp", "v", Math::exp);
    }

    public Object scaleToUnit(Map<String, Object> arguments) {
        BuiltinFunction.SCALE_TO_UNIT.validateArgs(arguments);
        var clamp = intArg(arguments, "clamp", 1) != 0;
        DoubleUnaryOperator op = clamp
                ? v -> Math.max(0d, Math.min(1d, v / Constants.MAX_PIXEL_VALUE))
                : v -> v / Constants.MAX_PIXEL_VALUE;
        return applyUnary(Map.of("img", arguments.get("img")), "scale_to_unit", "img", op);
    }

    public Object scaleFromUnit(Map<String, Object> arguments) {
        BuiltinFunction.SCALE_FROM_UNIT.validateArgs(arguments);
        var clamp = intArg(arguments, "clamp", 1) != 0;
        DoubleUnaryOperator op = clamp
                ? v -> Math.max(0d, Math.min(Constants.MAX_PIXEL_VALUE, v * Constants.MAX_PIXEL_VALUE))
                : v -> v * Constants.MAX_PIXEL_VALUE;
        return applyUnary(Map.of("img", arguments.get("img")), "scale_from_unit", "img", op);
    }

}
