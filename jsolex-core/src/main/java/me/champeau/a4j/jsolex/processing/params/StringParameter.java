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
package me.champeau.a4j.jsolex.processing.params;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class StringParameter extends ScriptParameter {
    private final String pattern;
    private final List<String> choices;
    private final Pattern compiledPattern;

    public StringParameter(String name, String defaultValue, Map<String, String> displayName, Map<String, String> description,
                          String pattern, List<String> choices) {
        super(name, ParameterType.STRING, defaultValue, displayName, description);
        this.pattern = pattern;
        this.choices = choices != null ? List.copyOf(choices) : null;
        this.compiledPattern = pattern != null ? Pattern.compile(pattern) : null;
    }

    public String getPattern() {
        return pattern;
    }

    public List<String> getChoices() {
        return choices;
    }

    @Override
    public ValidationResult validate(Object value) {
        if (value == null) {
            return ValidationResult.invalid("Value cannot be null");
        }

        String strValue = value.toString();

        if (choices != null && !choices.contains(strValue)) {
            return ValidationResult.invalid("Value must be one of: " + choices);
        }

        if (compiledPattern != null && !compiledPattern.matcher(strValue).matches()) {
            return ValidationResult.invalid("Value must match pattern: " + pattern);
        }

        return ValidationResult.valid();
    }

    @Override
    public String formatValue(Object value) {
        return value.toString();
    }
}