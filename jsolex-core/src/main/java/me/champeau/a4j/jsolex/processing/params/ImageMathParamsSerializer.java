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
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.File;
import java.lang.reflect.Type;
import java.util.List;

class ImageMathParamsSerializer implements JsonSerializer<ImageMathParams>, JsonDeserializer<ImageMathParams> {

    @Override
    public ImageMathParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonArray array) {
            return new ImageMathParams(
                    array.asList().stream()
                    .map(e -> new File(e.getAsString()))
                    .toList()
            );
        }
        return new ImageMathParams(List.of());
    }

    @Override
    public JsonElement serialize(ImageMathParams src, Type typeOfSrc, JsonSerializationContext context) {
        var array = new JsonArray();
        for (File file : src.scriptFiles()) {
            array.add(file.getAbsolutePath());
        }
        return array;
    }
}
