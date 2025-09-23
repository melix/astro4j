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

public abstract class ScriptParameter {
    private final String name;
    private final ParameterType type;
    private final Object defaultValue;
    private final Map<String, String> displayName;
    private final Map<String, String> description;

    protected ScriptParameter(String name,
                              ParameterType type,
                              Object defaultValue,
                              Map<String, String> displayName,
                              Map<String, String> description) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.displayName = displayName != null ? Map.copyOf(displayName) : Map.of();
        this.description = description != null ? Map.copyOf(description) : Map.of();
    }

    public String getName() {
        return name;
    }

    public ParameterType getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Map<String, String> getDisplayName() {
        return displayName;
    }

    public String getDisplayName(String language) {
        String name = displayName.get(language);
        if (name != null) {
            return name;
        }

        // If shorthand notation was used, it's stored as "default" - use it
        name = displayName.get("default");
        if (name != null) {
            return name;
        }

        // If no shorthand, use English as default
        name = displayName.get("en");
        if (name != null) {
            return name;
        }

        // If no English, use first available translation
        if (!displayName.isEmpty()) {
            return displayName.values().iterator().next();
        }

        // Last resort: use parameter variable name
        return this.name;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public String getDescription(String language) {
        String desc = description.get(language);
        if (desc != null) {
            return desc;
        }

        // If shorthand notation was used, it's stored as "default" - use it
        desc = description.get("default");
        if (desc != null) {
            return desc;
        }

        // If no shorthand, use English as default
        desc = description.get("en");
        if (desc != null) {
            return desc;
        }

        // If no English, use first available translation
        if (!description.isEmpty()) {
            return description.values().iterator().next();
        }

        // No description available
        return null;
    }

    public abstract ValidationResult validate(Object value);

    public abstract String formatValue(Object value);

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}