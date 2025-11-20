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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum BuiltinFunction {

%FUNCTIONS%;

    private final boolean hasSideEffect;
    private final boolean concurrent;
    private final List<Parameter> parameters;


    private static Parameter req(String name) {
        return new Parameter(name, true);
    }

    private static Parameter opt(String name) {
        return new Parameter(name, false);
    }

    BuiltinFunction() {
        this(false, true, List.of());
    }

    BuiltinFunction(boolean hasSideEffect, boolean concurrent, List<Parameter> parameters) {
        this.hasSideEffect = hasSideEffect;
        this.concurrent = concurrent;
        this.parameters = parameters;
    }

    BuiltinFunction(Parameter... parameters) {
        this(false, true, Arrays.stream(parameters).toList());
    }

    BuiltinFunction(String... parameters) {
        this(false, true, Arrays.stream(parameters).map(s -> new Parameter(s, true)).toList());
    }

    public String lowerCaseName() {
        return name().toLowerCase(Locale.US);
    }

    static BuiltinFunction of(String value) {
        return valueOf(value.toUpperCase(Locale.US));
    }

    public boolean hasSideEffect() {
        return hasSideEffect;
    }

    public boolean isConcurrent() {
        return concurrent;
    }

    public void validateArgs(Map<String, Object> args) {
        if (this.parameters.size() == 1 && Parameter.SPREAD_LIST.equals(this.parameters.getFirst())) {
            return;
        }
        var keys = args.keySet();
        var required = parameters.stream().filter(Parameter::required).map(Parameter::name).collect(Collectors.toSet());
        var missing = required.stream().filter(k -> !keys.contains(k)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            var sb = new StringBuilder();
            sb.append("Function '")
                    .append(name())
                    .append("' is missing required arguments: ");
            for (String key : missing) {
                sb.append(key).append(", ");
            }
            sb.setLength(sb.length() - 2); // remove last comma
            throw new IllegalArgumentException(sb.toString());
        }
        var unknown = keys.stream().filter(k -> parameters.stream().noneMatch(p -> p.name.equals(k))).collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            var sb = new StringBuilder();
            sb.append("Function '")
                    .append(name())
                    .append("' has unknown arguments: ");
            for (String key : unknown) {
                sb.append(key).append(", ");
            }
            sb.setLength(sb.length() - 2); // remove last comma
            throw new IllegalArgumentException(sb.toString());
        }
    }

    /**
     * Maps the positional arguments to a map of named arguments.
     * @param args the positional arguments
     * @return a map of named arguments
     */
    public Map<String, Object> mapPositionalArguments(List<Object> args) {
        if (this.parameters.size() == 1 && Parameter.SPREAD_LIST.equals(this.parameters.getFirst())) {
            return Map.of("list", args);
        }
        var minArgs = parameters.stream().filter(p -> p.required).count();
        var totalArgs = parameters.size();
        if (args.size() < minArgs || args.size() > totalArgs) {
            var sb = new StringBuilder();
            sb.append("Function '")
                    .append(name());
            if (minArgs == totalArgs) {
                sb.append("' expects ").append(totalArgs).append(" argument")
                        .append(totalArgs > 1 ? "s" : "").append(": ");
            } else {
                sb.append("' expects between ")
                        .append(minArgs)
                        .append(" and ")
                        .append(totalArgs)
                        .append(" arguments: ");
            }
            for (int i = 0; i < parameters.size(); i++) {
                var parameter = parameters.get(i);
                if (!parameter.required()) {
                    sb.append("[");
                }
                sb.append(parameter.name());
                if (!parameter.required()) {
                    sb.append("]");
                }
                if (i < parameters.size() - 1) {
                    sb.append(", ");
                }
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return IntStream.range(0, args.size())
                .mapToObj(i -> new Object() {
                    final String name = parameters.get(i).name();
                    final Object value = args.get(i);
                })
                .collect(Collectors.toMap(i -> i.name, i -> i.value, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Set<String> getAllParameterNames() {
        return parameters.stream().map(Parameter::name).collect(Collectors.toSet());
    }
%DOCUMENTATION_METHODS%
    public record FunctionParameter(
            String name,
            boolean optional,
            Map<String, String> descriptions
    ) {
        public String getDescription(String locale) {
            return descriptions.getOrDefault(locale, descriptions.getOrDefault("en", ""));
        }
    }

    private record Parameter(
            String name,
            boolean required
    ) {
        private static final Parameter SPREAD_LIST = new Parameter("list",  true);
    }
}
