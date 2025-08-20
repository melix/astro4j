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

import java.util.Map;

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

}
