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

import java.util.Map;

public class NumberParameter extends ScriptParameter {
    private final Double min;
    private final Double max;

    public NumberParameter(String name,
                           Number defaultValue,
                           Map<String, String> displayName,
                           Map<String, String> description,
                           Double min,
                           Double max) {
        super(name, ParameterType.NUMBER, defaultValue, displayName, description);
        this.min = min;
        this.max = max;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    @Override
    public ValidationResult validate(Object value) {
        if (value == null) {
            return ValidationResult.invalid("Value cannot be null");
        }

        double numValue;
        try {
            if (value instanceof Number) {
                numValue = ((Number) value).doubleValue();
            } else {
                numValue = Double.parseDouble(value.toString());
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("Value must be a number");
        }

        if (min != null && numValue < min) {
            return ValidationResult.invalid("Value must be >= " + min);
        }

        if (max != null && numValue > max) {
            return ValidationResult.invalid("Value must be <= " + max);
        }

        return ValidationResult.valid();
    }

    @Override
    public String formatValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        return String.valueOf(value);
    }
}