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
import me.champeau.a4j.math.tuples.DoublePair;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;

class ObservationDetailsSerializer implements JsonSerializer<ObservationDetails>, JsonDeserializer<ObservationDetails> {

    private static String getNullableString(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsString();
    }

    private static Integer getNullableInt(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsInt();
    }

    private static boolean getNullableBoolean(JsonObject obj, String key) {
        var element = obj.get(key);
        return element != null && element.getAsBoolean();
    }

    private static Double getNullableDouble(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsDouble();
    }

    @Override
    public ObservationDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonObject obj) {
            var observer = getNullableString(obj, "observer");
            var aperture = getNullableInt(obj, "aperture");
            var binning = getNullableInt(obj,"binning");
            var camera = getNullableString(obj, "camera");
            var email = getNullableString(obj, "email");
            var telescope = getNullableString(obj, "telescope");
            var pixelSize = getNullableDouble(obj, "pixelSize");
            var date = (ZonedDateTime) context.deserialize(obj.get("date"), ZonedDateTime.class);
            var focalLength = getNullableInt(obj, "focalLength");
            var forceCamera = getNullableBoolean(obj, "forceCamera");
            var showCoordinatesInDetails = getNullableBoolean(obj, "showCoordinatesInDetails");
            var altAzMode = getNullableBoolean(obj, "altAzMode");
            SpectroHeliograph instrument;
            if (obj.get("instrument") instanceof JsonObject) {
                instrument = context.deserialize(obj.get("instrument"), SpectroHeliograph.class);
            } else {
                var key = obj.get("instrument").getAsString();
                instrument = SpectroHeliographsIO.loadDefaults()
                    .stream()
                    .filter(it -> it.label().equals(key))
                    .findFirst()
                    .orElse(SpectroHeliograph.SOLEX);
            }
            var coordinates = (DoublePair) context.deserialize(obj.get("coordinates"), DoublePair.class);
            return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera, binning, pixelSize, forceCamera, showCoordinatesInDetails, altAzMode);
        }
        throw new IllegalAccessError("Unexpected JSON type " + json.getClass());
    }

    @Override
    public JsonElement serialize(ObservationDetails src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty("observer", src.observer());
        obj.addProperty("aperture", src.aperture());
        obj.addProperty("binning", src.binning());
        obj.addProperty("camera", src.camera());
        obj.addProperty("email", src.email());
        obj.addProperty("telescope", src.telescope());
        obj.addProperty("pixelSize", src.pixelSize());
        obj.addProperty("focalLength", src.focalLength());
        obj.add("date", context.serialize(src.date()));
        obj.add("instrument", context.serialize(src.instrument()));
        obj.add("coordinates", context.serialize(src.coordinates()));
        obj.add("forceCamera", context.serialize(src.forceCamera()));
        obj.add("showCoordinatesInDetails", context.serialize(src.showCoordinatesInDetails()));
        obj.add("altAzMode", context.serialize(src.altAzMode()));
        return obj;
    }
}
