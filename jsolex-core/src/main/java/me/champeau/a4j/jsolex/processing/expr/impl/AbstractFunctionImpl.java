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

import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class AbstractFunctionImpl {
    protected final ForkJoinContext forkJoinContext;
    protected final Map<Class<?>, Object> context;

    protected AbstractFunctionImpl(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        this.forkJoinContext = forkJoinContext;
        this.context = context;
    }

    protected <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    protected void assertExpectedArgCount(List<Object> arguments, String help, int min, int max) {
        var size = arguments.size();
        if (size < min || size > max) {
            throw new IllegalArgumentException(help);
        }
    }

    protected <T> Optional<T> getArgument(Class<T> clazz, List<Object> args, int position) {
        if (position < args.size()) {
            return Optional.of((T) args.get(position));
        }
        return Optional.empty();
    }

    protected double doubleArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).doubleValue();
    }

    protected float floatArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).floatValue();
    }

    protected int intArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).intValue();
    }

    protected Number getAsNumber(List<Object> arguments, int position) {
        if (position < arguments.size()) {
            var obj = arguments.get(position);
            if (obj instanceof Number num) {
                return num;
            }
            throw new IllegalStateException("Expected to find a number argument at position " + position + " but it as a " + obj.getClass());
        }
        throw new IllegalStateException("Not enough arguments to select a number at position " + position);
    }
}
