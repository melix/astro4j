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

class GeometryParamsSerializer implements JsonSerializer<GeometryParams>, JsonDeserializer<GeometryParams> {

    @Override
    public GeometryParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var o = json.getAsJsonObject();
        var tilt = o.get("tilt");
        var ratio = o.get("xyRatio");
        return new GeometryParams(
                tilt == null ? null : tilt.getAsDouble(),
                ratio == null ? null : ratio.getAsDouble()
        );
    }

    @Override
    public JsonElement serialize(GeometryParams src, Type typeOfSrc, JsonSerializationContext context) {
        var jsonObject = new JsonObject();
        src.tilt().ifPresent(tilt -> jsonObject.addProperty("tilt", tilt));
        src.xyRatio().ifPresent(ratio -> jsonObject.addProperty("xyRatio", ratio));
        return jsonObject;
    }
}
