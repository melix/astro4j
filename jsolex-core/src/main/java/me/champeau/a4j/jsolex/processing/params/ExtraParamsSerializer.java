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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

class ExtraParamsSerializer implements JsonSerializer<ExtraParams>, JsonDeserializer<ExtraParams> {

    @Override
    public ExtraParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonObject obj) {
            var generateDebugImages = obj.has("generateDebugImages") && obj.get("generateDebugImages").getAsBoolean();
            var autosave = obj.has("autosave") && obj.get("autosave").getAsBoolean();
            var fileNamePattern = obj.has("fileNamePattern") ? obj.get("fileNamePattern").getAsString() : null;
            var datetimeFormat = obj.has("datetimeFormat") ? obj.get("datetimeFormat").getAsString() : null;
            var dateFormat = obj.has("dateFormat") ? obj.get("dateFormat").getAsString() : null;
            var reviewImagesAfterBatch = obj.has("reviewImagesAfterBatch") && obj.get("reviewImagesAfterBatch").getAsBoolean();
            GlobeStyle globeStyle = obj.has("globeStyle") ? context.deserialize(obj.get("globeStyle"), GlobeStyle.class) : null;
            return new ExtraParams(
                    generateDebugImages,
                    autosave,
                    fileNamePattern,
                    datetimeFormat,
                    dateFormat,
                    reviewImagesAfterBatch,
                    globeStyle
            );
        }
        throw new JsonParseException("Unexpected JSON type for ExtraParams: " + json.getClass());
    }

    @Override
    public JsonElement serialize(ExtraParams src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty("generateDebugImages", src.generateDebugImages());
        obj.addProperty("autosave", src.autosave());
        if (src.fileNamePattern() != null) {
            obj.addProperty("fileNamePattern", src.fileNamePattern());
        }
        if (src.datetimeFormat() != null) {
            obj.addProperty("datetimeFormat", src.datetimeFormat());
        }
        if (src.dateFormat() != null) {
            obj.addProperty("dateFormat", src.dateFormat());
        }
        obj.addProperty("reviewImagesAfterBatch", src.reviewImagesAfterBatch());
        if (src.globeStyle() != null) {
            obj.add("globeStyle", context.serialize(src.globeStyle()));
        }
        return obj;
    }
}
