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
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class UserFunction {
    private final String name;
    private final int arity;
    private final List<String> arguments;
    private final List<Expression> body;
    private final Function<PixelShift, ImageWrapper> imageSupplier;
    private final Map<Class, Object> context;
    private final Consumer<? super Double> shiftCollector;
    private final Broadcaster broadcaster;
    private final List<UserFunction> userFunctions;

    public UserFunction(String name,
                        List<String> arguments,
                        List<Expression> body,
                        Function<PixelShift, ImageWrapper> imageSupplier,
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

    public List<String> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    public UserFunction prepare(
        Function<PixelShift, ImageWrapper> imageSupplier,
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

    public Object invoke(Map<String, Object> args) {
        validateFunctionArguments(args);
        if (!args.isEmpty()) {
            var first = args.entrySet().iterator().next();
            var firstArg = first.getValue();

            if (firstArg instanceof List<?> list) {
                return list.stream()
                        .parallel()
                        .map(f -> {
                            var newArgs = new LinkedHashMap<>(args);
                            newArgs.put(first.getKey(), f);
                            return invoke(newArgs);
                        })
                        .toList();
            } else if (firstArg instanceof Map<?, ?> map) {
                if (map.size() == 1 && map.containsKey("list")) {
                    var list = (List<?>) map.get("list");
                    return list.stream()
                            .parallel()
                            .map(f -> {
                                var newArgs = new LinkedHashMap<>(args);
                                newArgs.put(first.getKey(), f);
                                return invoke(newArgs);
                            })
                            .toList();
                }
            }
        }
        var evaluator = new DefaultImageScriptExecutor(
            imageSupplier,
            context,
            broadcaster
        );
        evaluator.disableOutputLogging();
        for (var entry : args.entrySet()) {
            evaluator.putVariable(entry.getKey(), entry.getValue());

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

    private void validateFunctionArguments(Map<String, Object> args) {
        var argNames = args.keySet();
        if (argNames.size() != arity) {
            throw new IllegalArgumentException("Function " + name + " expects " + arity + " arguments, but got " + argNames.size());
        }
    }
}
