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

import me.champeau.a4j.jsolex.expr.ImageMathParser;
import me.champeau.a4j.jsolex.expr.ast.Identifier;
import me.champeau.a4j.jsolex.expr.ast.ImageMathScript;
import me.champeau.a4j.jsolex.expr.ast.MetaBlock;
import me.champeau.a4j.jsolex.expr.ast.MetaProperty;
import me.champeau.a4j.jsolex.expr.ast.ParameterDef;
import me.champeau.a4j.jsolex.expr.ast.ParameterObject;
import me.champeau.a4j.jsolex.expr.ast.ParameterProperty;
import me.champeau.a4j.jsolex.expr.ast.StringLiteral;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ImageMathParameterExtractor {

    public static class ParameterExtractionResult {
        private final List<ScriptParameter> parameters;
        private final boolean hasParametersSection;
        private final Map<String, String> title;
        private final Map<String, String> description;
        private final String scriptFileName;
        private final String requiredVersion;
        private final String author;
        private final String version;

        public ParameterExtractionResult(List<ScriptParameter> parameters,
                                         boolean hasParametersSection,
                                         Map<String, String> title,
                                         Map<String, String> description,
                                         String scriptFileName,
                                         String requiredVersion,
                                         String author,
                                         String version) {
            this.parameters = List.copyOf(parameters);
            this.hasParametersSection = hasParametersSection;
            this.title = Map.copyOf(title != null ? title : Map.of());
            this.description = Map.copyOf(description != null ? description : Map.of());
            this.scriptFileName = scriptFileName;
            this.requiredVersion = requiredVersion;
            this.author = author;
            this.version = version;
        }

        public List<ScriptParameter> getParameters() {
            return parameters;
        }

        public boolean hasParametersSection() {
            return hasParametersSection;
        }

        public Map<String, String> getTitle() {
            return title;
        }

        public Map<String, String> getDescription() {
            return description;
        }

        public String getScriptFileName() {
            return scriptFileName;
        }

        public String getRequiredVersion() {
            return requiredVersion;
        }

        public String getAuthor() {
            return author;
        }

        public String getVersion() {
            return version;
        }

        public boolean isVersionSupported() {
            return VersionUtil.isVersionSupported(requiredVersion);
        }

        public String getDisplayTitle(String language) {
            if (title.isEmpty()) {
                return scriptFileName;
            }

            String titleText = title.get(language);
            if (titleText != null) {
                return titleText;
            }

            titleText = title.get("default");
            if (titleText != null) {
                return titleText;
            }

            titleText = title.get("en");
            if (titleText != null) {
                return titleText;
            }

            if (!title.isEmpty()) {
                return title.values().iterator().next();
            }

            return scriptFileName;
        }

        public String getDisplayDescription(String language) {
            if (description.isEmpty()) {
                return null;
            }

            String descText = description.get(language);
            if (descText != null) {
                return descText;
            }

            descText = description.get("default");
            if (descText != null) {
                return descText;
            }

            descText = description.get("en");
            if (descText != null) {
                return descText;
            }

            if (!description.isEmpty()) {
                return description.values().iterator().next();
            }

            return null;
        }
    }

    public ParameterExtractionResult extractParameters(Path scriptFile) throws Exception {
        String content = Files.readString(scriptFile);
        String fileName = scriptFile.getFileName().toString();
        return extractParameters(content, fileName);
    }

    public ParameterExtractionResult extractParameters(String scriptContent) throws Exception {
        return extractParameters(scriptContent, "unknown");
    }

    public ParameterExtractionResult extractParameters(String scriptContent, String fileName) throws Exception {
        try {
            var parser = new ImageMathParser(scriptContent);
            ImageMathScript script = parser.parseAndInlineIncludes();

            return extractParametersFromAST(script, fileName);
        } catch (Exception e) {
            throw new Exception("Failed to parse script for parameter extraction", e);
        }
    }

    public ParameterExtractionResult extractParametersFromAST(ImageMathScript script, String fileName) {
        List<ScriptParameter> parameters = new ArrayList<>();
        boolean hasParametersSection = false;
        Map<String, String> title = new HashMap<>();
        Map<String, String> description = new HashMap<>();
        String requiredVersion = null;
        String author = null;
        String version = null;

        var metaBlocks = script.childrenOfType(MetaBlock.class);
        for (var metaBlock : metaBlocks) {
            var metaContent = metaBlock.getContent();
            var metaProperties = metaContent.getMetaProperties();

            for (var property : metaProperties) {
                var identifier = property.firstChildOfType(Identifier.class);
                if (identifier != null) {
                    var name = identifier.toString();
                    if ("title".equals(name) || "name".equals(name)) {
                        title.putAll(extractLocalizedValuesFromProperty(property));
                    } else if ("description".equals(name)) {
                        description.putAll(extractLocalizedValuesFromProperty(property));
                    } else if ("requires".equals(name)) {
                        var stringLiteral = property.firstChildOfType(StringLiteral.class);
                        if (stringLiteral != null) {
                            requiredVersion = stringLiteral.toString();
                        }
                    } else if ("author".equals(name)) {
                        var stringLiteral = property.firstChildOfType(StringLiteral.class);
                        if (stringLiteral != null) {
                            author = stringLiteral.toString();
                        }
                    } else if ("version".equals(name)) {
                        var stringLiteral = property.firstChildOfType(StringLiteral.class);
                        if (stringLiteral != null) {
                            version = stringLiteral.toString();
                        }
                    }
                }
            }
        }

        List<ParameterDef> topLevelParams = script.getTopLevelParameterDefs();
        if (!topLevelParams.isEmpty()) {
            hasParametersSection = true;
            for (ParameterDef paramDef : topLevelParams) {
                ScriptParameter parameter = extractParameterFromDef(paramDef);
                if (parameter != null) {
                    parameters.add(parameter);
                }
            }
        }

        return new ParameterExtractionResult(parameters, hasParametersSection, title, description, fileName, requiredVersion, author, version);
    }


    private ScriptParameter extractParameterFromDef(ParameterDef paramDef) {
        String parameterName = paramDef.getName();
        ParameterObject paramObj = paramDef.getObjectValue();

        if (paramObj == null) {
            return null;
        }

        Optional<String> typeOpt = paramObj.getProperty("type");
        if (typeOpt.isEmpty()) {
            return null;
        }

        ParameterType parameterType;
        try {
            parameterType = ParameterType.valueOf(typeOpt.get().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }

        Object defaultValue = null;
        var defaultNumberOpt = paramObj.getNumberProperty("default");
        if (defaultNumberOpt.isPresent()) {
            defaultValue = defaultNumberOpt.get();
        } else {
            var defaultStringOpt = paramObj.getProperty("default");
            if (defaultStringOpt.isPresent()) {
                defaultValue = defaultStringOpt.get();
            }
        }

        Map<String, String> displayNames = extractLocalizedValues(paramObj, "name");
        Map<String, String> descriptions = extractLocalizedValues(paramObj, "description");

        return switch (parameterType) {
            case NUMBER -> {
                Double min = paramObj.getNumberProperty("min").map(Number::doubleValue).orElse(null);
                Double max = paramObj.getNumberProperty("max").map(Number::doubleValue).orElse(null);
                Number numDefault = defaultValue instanceof Number ? (Number) defaultValue : 0.0;
                yield new NumberParameter(parameterName, numDefault, displayNames, descriptions, min, max);
            }
            case STRING -> {
                String strDefault = defaultValue != null ? defaultValue.toString() : "";
                yield new StringParameter(parameterName, strDefault, displayNames, descriptions, null, null);
            }
            case CHOICE -> {
                String choices = paramObj.getProperty("choices").orElse("");
                List<String> choiceList = choices.isEmpty() ? List.of() : List.of(choices.split(","));
                String strDefault = defaultValue != null ? defaultValue.toString() : (choiceList.isEmpty() ? "" : choiceList.get(0));
                yield new ChoiceParameter(parameterName, strDefault, displayNames, descriptions, choiceList);
            }
        };
    }

    private Map<String, String> extractLocalizedValues(ParameterObject paramObj, String baseName) {
        Map<String, String> result = new HashMap<>();

        for (ParameterProperty prop : paramObj.getProperties()) {
            String propName = prop.getName();
            if (propName.equals(baseName)) {
                var nestedObj = prop.getValue().firstChildOfType(ParameterObject.class);
                if (nestedObj != null) {
                    for (ParameterProperty langProp : nestedObj.getProperties()) {
                        String language = langProp.getName();
                        Optional<String> value = langProp.getStringValue();
                        if (value.isPresent()) {
                            result.put(language, value.get());
                        }
                    }
                } else {
                    Optional<String> directValue = prop.getStringValue();
                    if (directValue.isPresent()) {
                        result.put("default", directValue.get());
                    }
                }
            }
        }

        return result;
    }

    private Map<String, String> extractLocalizedValuesFromProperty(MetaProperty property) {
        Map<String, String> result = new HashMap<>();

        var nestedObj = property.firstChildOfType(ParameterObject.class);
        if (nestedObj != null) {
            for (ParameterProperty langProp : nestedObj.getProperties()) {
                String language = langProp.getName();
                Optional<String> value = langProp.getStringValue();
                if (value.isPresent()) {
                    result.put(language, value.get());
                }
            }
        } else {
            var stringLiteral = property.firstChildOfType(StringLiteral.class);
            if (stringLiteral != null) {
                result.put("default", stringLiteral.toString());
            }
        }

        return result;
    }

}