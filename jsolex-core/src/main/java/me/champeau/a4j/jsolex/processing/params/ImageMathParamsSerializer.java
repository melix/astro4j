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

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ImageMathParamsSerializer implements JsonSerializer<ImageMathParams>, JsonDeserializer<ImageMathParams> {

    @Override
    public ImageMathParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonObject obj) {
            var scriptsArray = obj.getAsJsonArray("scripts");
            var scripts = scriptsArray != null ?
                scriptsArray.asList().stream().map(e -> new File(e.getAsString())).toList() :
                List.<File>of();

            var parametersObj = obj.getAsJsonObject("parameters");
            var parameters = new HashMap<File, Map<String, Object>>();
            if (parametersObj != null) {
                for (var entry : parametersObj.entrySet()) {
                    var file = new File(entry.getKey());
                    var fileParams = new HashMap<String, Object>();
                    var paramsObj = entry.getValue().getAsJsonObject();
                    for (var paramEntry : paramsObj.entrySet()) {
                        var value = paramEntry.getValue();
                        if (value.isJsonPrimitive()) {
                            var primitive = value.getAsJsonPrimitive();
                            if (primitive.isString()) {
                                fileParams.put(paramEntry.getKey(), primitive.getAsString());
                            } else if (primitive.isNumber()) {
                                fileParams.put(paramEntry.getKey(), primitive.getAsDouble());
                            } else if (primitive.isBoolean()) {
                                fileParams.put(paramEntry.getKey(), primitive.getAsBoolean());
                            }
                        }
                    }
                    parameters.put(file, fileParams);
                }
            }
            return new ImageMathParams(scripts, parameters);
        } else if (json instanceof JsonArray array) {
            return new ImageMathParams(
                    array.asList().stream()
                    .map(e -> new File(e.getAsString()))
                    .toList(),
                    Map.of()
            );
        }
        return new ImageMathParams(List.of(), Map.of());
    }

    @Override
    public JsonElement serialize(ImageMathParams src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();

        var scriptsArray = new JsonArray();
        for (var file : src.scriptFiles()) {
            scriptsArray.add(file.getAbsolutePath());
        }
        obj.add("scripts", scriptsArray);

        var parametersObj = new JsonObject();
        for (var entry : src.parameterValues().entrySet()) {
            var fileParams = new JsonObject();
            for (var paramEntry : entry.getValue().entrySet()) {
                var value = paramEntry.getValue();
                if (value instanceof String s) {
                    fileParams.addProperty(paramEntry.getKey(), s);
                } else if (value instanceof Number n) {
                    fileParams.addProperty(paramEntry.getKey(), n);
                } else if (value instanceof Boolean b) {
                    fileParams.addProperty(paramEntry.getKey(), b);
                }
            }
            parametersObj.add(entry.getKey().getAbsolutePath(), fileParams);
        }
        obj.add("parameters", parametersObj);

        return obj;
    }
}
