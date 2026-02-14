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

import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor.ParameterExtractionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extracts parameters and metadata from Python scripts using comment-based conventions.
 *
 * <p>Python scripts can declare metadata and parameters using specially formatted comments:
 * <pre>
 * # meta:title = "My Script"
 * # meta:title:fr = "Mon Script"
 * # meta:author = "Author Name"
 * # meta:version = "1.0"
 * # meta:requires = "4.6.0"
 * # meta:description = "Script description"
 *
 * # param:gamma:type = number
 * # param:gamma:default = 1.5
 * # param:gamma:min = 0.1
 * # param:gamma:max = 3.0
 * # param:gamma:name = "Gamma"
 * # param:gamma:description = "Contrast adjustment"
 * </pre>
 */
public class PythonParameterExtractor {

    private static final Pattern META_PATTERN = Pattern.compile(
        "^\\s*#\\s*meta:([a-zA-Z_][a-zA-Z0-9_]*)(?::([a-z]{2}))?\\s*=\\s*(.+)$"
    );

    private static final Pattern PARAM_PATTERN = Pattern.compile(
        "^\\s*#\\s*param:([a-zA-Z_][a-zA-Z0-9_]*):([a-zA-Z_][a-zA-Z0-9_]*)(?::([a-z]{2}))?\\s*=\\s*(.+)$"
    );

    public ParameterExtractionResult extractParameters(Path scriptFile) throws IOException {
        var content = Files.readString(scriptFile);
        var fileName = scriptFile.getFileName().toString();
        return extractParameters(content, fileName);
    }

    public ParameterExtractionResult extractParameters(String scriptContent, String fileName) {
        Map<String, String> title = new HashMap<>();
        Map<String, String> description = new HashMap<>();
        String author = null;
        String version = null;
        String requiredVersion = null;

        // Collect parameter properties by parameter name
        Map<String, Map<String, Object>> paramProperties = new HashMap<>();

        for (var line : scriptContent.lines().toList()) {
            // Try meta pattern
            var metaMatcher = META_PATTERN.matcher(line);
            if (metaMatcher.matches()) {
                var propertyName = metaMatcher.group(1);
                var language = metaMatcher.group(2); // may be null
                var value = unquote(metaMatcher.group(3).trim());

                switch (propertyName) {
                    case "title", "name" -> {
                        title.put(language != null ? language : "default", value);
                    }
                    case "description" -> {
                        description.put(language != null ? language : "default", value);
                    }
                    case "author" -> author = value;
                    case "version" -> version = value;
                    case "requires" -> requiredVersion = value;
                }
                continue;
            }

            // Try param pattern
            var paramMatcher = PARAM_PATTERN.matcher(line);
            if (paramMatcher.matches()) {
                var paramName = paramMatcher.group(1);
                var propertyName = paramMatcher.group(2);
                var language = paramMatcher.group(3); // may be null
                var value = unquote(paramMatcher.group(4).trim());

                var props = paramProperties.computeIfAbsent(paramName, k -> new HashMap<>());

                if ("name".equals(propertyName) || "description".equals(propertyName)) {
                    // Localized properties
                    @SuppressWarnings("unchecked")
                    var localizedMap = (Map<String, String>) props.computeIfAbsent(
                        propertyName, k -> new HashMap<String, String>()
                    );
                    localizedMap.put(language != null ? language : "default", value);
                } else {
                    props.put(propertyName, value);
                }
            }
        }

        // Convert parameter properties to ScriptParameter objects
        List<ScriptParameter> parameters = new ArrayList<>();
        for (var entry : paramProperties.entrySet()) {
            var paramName = entry.getKey();
            var props = entry.getValue();
            var param = createParameter(paramName, props);
            if (param != null) {
                parameters.add(param);
            }
        }

        return new ParameterExtractionResult(
            parameters,
            !parameters.isEmpty(),
            title,
            description,
            fileName,
            requiredVersion,
            author,
            version,
            Map.of() // No outputs metadata for Python scripts
        );
    }

    @SuppressWarnings("unchecked")
    private ScriptParameter createParameter(String name, Map<String, Object> props) {
        var typeStr = (String) props.get("type");
        if (typeStr == null) {
            return null;
        }

        ParameterType type;
        try {
            type = ParameterType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        var displayNames = props.containsKey("name")
            ? (Map<String, String>) props.get("name")
            : Map.<String, String>of();

        var descriptions = props.containsKey("description")
            ? (Map<String, String>) props.get("description")
            : Map.<String, String>of();

        return switch (type) {
            case NUMBER -> {
                var defaultValue = parseNumber(props.get("default"));
                var min = parseDouble(props.get("min"));
                var max = parseDouble(props.get("max"));
                yield new NumberParameter(name, defaultValue != null ? defaultValue : 0.0, displayNames, descriptions, min, max);
            }
            case STRING -> {
                var defaultValue = props.get("default");
                yield new StringParameter(name, defaultValue != null ? defaultValue.toString() : "", displayNames, descriptions, null, null);
            }
            case CHOICE -> {
                var choicesStr = (String) props.get("choices");
                List<String> choices = choicesStr != null && !choicesStr.isEmpty()
                    ? List.of(choicesStr.split(","))
                    : List.of();
                var defaultValue = props.get("default");
                var defaultStr = defaultValue != null ? defaultValue.toString() : (choices.isEmpty() ? "" : choices.getFirst());
                yield new ChoiceParameter(name, defaultStr, displayNames, descriptions, choices);
            }
        };
    }

    private Number parseNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n;
        }
        try {
            var str = value.toString().trim();
            if (str.contains(".")) {
                return Double.parseDouble(str);
            }
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String unquote(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // Remove surrounding quotes if present
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
