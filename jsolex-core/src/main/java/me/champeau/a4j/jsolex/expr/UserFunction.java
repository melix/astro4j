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
package me.champeau.a4j.jsolex.expr;

import me.champeau.a4j.jsolex.expr.ast.Expression;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class UserFunction {
    private final String name;
    private final int arity;
    private final List<String> arguments;
    private final List<Expression> body;
    private final Function<Double, ImageWrapper> imageSupplier;
    private final Map<Class, Object> context;
    private final Consumer<? super Double> shiftCollector;
    private final Broadcaster broadcaster;
    private final List<UserFunction> userFunctions;

    public UserFunction(String name,
                        List<String> arguments,
                        List<Expression> body,
                        Function<Double, ImageWrapper> imageSupplier,
                        Map<Class, Object> context,
                        Consumer<? super Double> shiftCollector,
                        Broadcaster broadcaster,
                        List<UserFunction> userFunctions
    ) {
        this.name = name;
        this.arity = arguments.size();
        this.arguments = arguments;
        this.body = body;
        this.imageSupplier = imageSupplier;
        this.context = context;
        this.shiftCollector = shiftCollector;
        this.broadcaster = broadcaster;
        this.userFunctions = userFunctions;
    }

    public String name() {
        return name;
    }

    public UserFunction prepare(
        Function<Double, ImageWrapper> imageSupplier,
        Map<Class, Object> context,
        Consumer<? super Double> shiftCollector,
        Broadcaster broadcaster
    ) {
        return new UserFunction(
            name,
            arguments,
            body,
            imageSupplier,
            context,
            shiftCollector,
            broadcaster,
            userFunctions
        );
    }

    public Object invoke(Object... args) {
        if (args.length != arity) {
            throw new IllegalArgumentException("Invalid number of arguments for function " + name + ": expected " + arity + ", got " + args.length);
        }
        if (args.length>0 && args[0] instanceof List<?> list) {
            var newArgs = new Object[args.length];
            return list.stream()
                    .parallel()
                    .map(f -> {
                        newArgs[0] = f;
                        System.arraycopy(args, 1, newArgs, 1, args.length - 1);
                        return invoke(newArgs);
                    })
                    .toList();
        }
        var evaluator = new DefaultImageScriptExecutor(
            imageSupplier,
            context,
            broadcaster
        );
        evaluator.disableOutputLogging();
        for (int i = 0; i < args.length; i++) {
            evaluator.putVariable(arguments.get(i), args[i]);
        }
        var scriptResult = evaluator.execute(body, userFunctions);
        if (scriptResult.invalidExpressions().isEmpty()) {
            var result = evaluator.getVariable("result");
            if (result.isPresent()) {
                scriptResult.internalShifts().forEach(shiftCollector);
                scriptResult.outputShifts().forEach(shiftCollector);
                return result.get();
            }
            throw new IllegalStateException("No result variable found in function " + name);
        }
        throw new IllegalStateException("Invalid expressions in function " + name + ": " + scriptResult.invalidExpressions());
    }
}
