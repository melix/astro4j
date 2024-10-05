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
import me.champeau.a4j.jsolex.processing.sun.FlatCorrection;

import java.lang.reflect.Type;

class EnhancementParamsSerializer implements JsonSerializer<EnhancementParams>, JsonDeserializer<EnhancementParams> {

    private static boolean getNullableBoolean(JsonObject obj, String key) {
        var element = obj.get(key);
        return element != null && element.getAsBoolean();
    }

    private static Integer getNullableInt(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsInt();
    }

    private static Double getNullableDouble(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsDouble();
    }

    @Override
    public EnhancementParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonObject obj) {
            var artificialFlatCorrection = getNullableBoolean(obj, "artificialFlatCorrection");
            var loPercentile = getNullableDouble(obj, "artificialFlatCorrectionLoPercentile");
            if (loPercentile == null) {
                loPercentile = FlatCorrection.DEFAULT_LO_PERCENTILE;
            }
            var hiPercentile = getNullableDouble(obj, "artificialFlatCorrectionHiPercentile");
            if (hiPercentile == null) {
                hiPercentile = FlatCorrection.DEFAULT_HI_PERCENTILE;
            }
            var order = getNullableInt(obj, "artificialFlatCorrectionOrder");
            if (order == null) {
                order = FlatCorrection.DEFAULT_ORDER;
            }
            return new EnhancementParams(artificialFlatCorrection, loPercentile, hiPercentile, order);
        }
        throw new IllegalAccessError("Unexpected JSON type " + json.getClass());
    }

    @Override
    public JsonElement serialize(EnhancementParams src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty("artificialFlatCorrection", src.artificialFlatCorrection());
        obj.addProperty("artificialFlatCorrectionLoPercentile", src.artificialFlatCorrectionLoPercentile());
        obj.addProperty("artificialFlatCorrectionHiPercentile", src.artificialFlatCorrectionHiPercentile());
        obj.addProperty("artificialFlatCorrectionOrder", src.artificialFlatCorrectionOrder());
        return obj;
    }
}
