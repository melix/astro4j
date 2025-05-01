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
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;

import java.lang.reflect.Type;

class AutoStretchParamsSerializer implements JsonSerializer<AutoStretchParams>, JsonDeserializer<AutoStretchParams> {

    private static Double getNullableDouble(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsDouble();
    }

    @Override
    public AutoStretchParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonObject obj) {
            var gamma = getNullableDouble(obj, "gamma");
            if (gamma == null) {
                gamma = AutohistogramStrategy.DEFAULT_GAMMA;
            }
            var bgThreshold = getNullableDouble(obj, "bgThreshold");
            if (bgThreshold == null) {
                bgThreshold = AutohistogramStrategy.DEFAULT_BACKGROUND_THRESHOLD;
            }
            var protusStretch = getNullableDouble(obj, "protusStretch");
            if (protusStretch == null) {
                protusStretch = AutohistogramStrategy.DEFAULT_PROM_STRETCH;
            }
            return new AutoStretchParams(gamma, bgThreshold, protusStretch);
        }
        throw new IllegalAccessError("Unexpected JSON type " + json.getClass());
    }


    @Override
    public JsonElement serialize(AutoStretchParams src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty("gamma", src.gamma());
        obj.addProperty("bgThreshold", src.bgThreshold());
        obj.addProperty("protusStretch", src.protusStretch());
        return obj;
    }
}
