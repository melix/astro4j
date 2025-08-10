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
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.lang.reflect.Type;

class SpectralRaySerializer implements JsonSerializer<SpectralRay>, JsonDeserializer<SpectralRay> {

    @Override
    public SpectralRay deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonPrimitive primitive) {
            // old serialization format
            String ray = primitive.getAsString();
            return switch (ray) {
                case "H_ALPHA" -> SpectralRay.H_ALPHA;
                case "H_BETA" -> SpectralRay.H_BETA;
                case "CALCIUM_K" -> SpectralRay.CALCIUM_K;
                case "CALCIUM_H" -> SpectralRay.CALCIUM_H;
                case "SODIUM_D1" -> SpectralRay.SODIUM_D1;
                case "SODIUM_D2" -> SpectralRay.SODIUM_D2;
                default -> SpectralRay.OTHER;
            };
        } else if (json instanceof JsonObject obj) {
            ColorCurve curve = readColorCurve(obj);
            var emission = obj.has("emission") && obj.get("emission").getAsBoolean();
            return new SpectralRay(
                    obj.get("label").getAsString(),
                    curve,
                    Wavelen.ofNanos(obj.get("wavelength").getAsDouble()),
                    emission);
        }
        throw new IllegalAccessError("Unexpected JSON type " + json.getClass());
    }

    private static ColorCurve readColorCurve(JsonObject object) {
        JsonElement e = object.get("colorCurve");
        if (e instanceof JsonObject colorCurve) {
            return new ColorCurve(
                    colorCurve.get("ray").getAsString(),
                    colorCurve.get("rIn").getAsInt(),
                    colorCurve.get("rOut").getAsInt(),
                    colorCurve.get("gIn").getAsInt(),
                    colorCurve.get("gOut").getAsInt(),
                    colorCurve.get("bIn").getAsInt(),
                    colorCurve.get("bOut").getAsInt()
            );
        }
        return null;
    }

    private static JsonObject writeColorCurve(ColorCurve curve) {
        if (curve == null) {
            return null;
        }
        var e = new JsonObject();
        e.addProperty("ray", curve.ray());
        e.addProperty("rIn", curve.rIn());
        e.addProperty("rOut", curve.rOut());
        e.addProperty("gIn", curve.gIn());
        e.addProperty("gOut", curve.gOut());
        e.addProperty("bIn", curve.bIn());
        e.addProperty("bOut", curve.bOut());
        return e;
    }

    @Override
    public JsonElement serialize(SpectralRay src, Type typeOfSrc, JsonSerializationContext context) {
        var obj = new JsonObject();
        obj.addProperty("label", src.label());
        obj.addProperty("wavelength", src.wavelength().nanos());
        obj.addProperty("emission", src.emission());
        var curve = writeColorCurve(src.colorCurve());
        if (curve != null) {
            obj.add("colorCurve", curve);
        }
        return obj;
    }
}
