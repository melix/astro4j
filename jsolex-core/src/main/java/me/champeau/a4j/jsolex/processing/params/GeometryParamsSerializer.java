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
import java.util.Locale;

class GeometryParamsSerializer implements JsonSerializer<GeometryParams>, JsonDeserializer<GeometryParams> {

    @Override
    public GeometryParams deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var o = json.getAsJsonObject();
        var tilt = o.get("tilt");
        var ratio = o.get("xyRatio");
        var horizontalMirror = o.get("horizontalMirror") != null ? o.get("horizontalMirror").getAsBoolean() : false;
        var verticalMirror = o.get("verticalMirror") != null ? o.get("verticalMirror").getAsBoolean() : false;
        var allowDownsampling = o.get("allowDownsampling") != null ? o.get("allowDownsampling").getAsBoolean() : false;
        var autocorrectAngleP = o.get("autocorrectAngleP") != null ? o.get("autocorrectAngleP").getAsBoolean() : true;
        var rotation = o.get("rotation");
        var scanDirection = rotation != null ? RotationKind.valueOf(rotation.getAsString().toUpperCase(Locale.US)) : RotationKind.NONE;
        var autocropMode = o.get("autocropMode") != null ? AutocropMode.valueOf(o.get("autocropMode").getAsString()) : AutocropMode.OFF;
        var deconvolutionMode = o.get("deconvolutionMode") != null ? DeconvolutionMode.valueOf(o.get("deconvolutionMode").getAsString()) : DeconvolutionMode.NONE;
        var richardsonLucyDeconvolutionParams = readRichardsonLucyDeconvolutionParams(o.get("richardsonLucyDeconvolutionParams"));
        var forcePolynomial = o.get("forcePolynomial") != null ? o.get("forcePolynomial").getAsBoolean() : false;
        var forcedPolynomial = o.get("forcedPolynomial") != null ? o.get("forcedPolynomial").getAsString() : null;
        var spectrumVFlip = o.get("spectrumVFlip") != null ? o.get("spectrumVFlip").getAsBoolean() : false;
        var ellipseFittingMode = o.get("ellipseFittingMode") != null ? EllipseFittingMode.valueOf(o.get("ellipseFittingMode").getAsString()) : EllipseFittingMode.AUTOMATIC;
        var fixedWidth = o.get("fixedWidth") != null ? o.get("fixedWidth").getAsInt() : null;
        return new GeometryParams(
            tilt == null ? null : tilt.getAsDouble(),
            ratio == null ? null : ratio.getAsDouble(),
            horizontalMirror,
            verticalMirror,
            allowDownsampling,
            autocorrectAngleP,
            scanDirection,
            autocropMode,
            fixedWidth,
            deconvolutionMode,
            richardsonLucyDeconvolutionParams,
            forcePolynomial,
            forcedPolynomial,
            spectrumVFlip,
            ellipseFittingMode);
    }

    private RichardsonLucyDeconvolutionParams readRichardsonLucyDeconvolutionParams(JsonElement params) {
        if (params instanceof JsonObject obj) {
            var radius = obj.get("radius").getAsDouble();
            var sigma = obj.get("sigma").getAsDouble();
            var iterations = obj.get("iterations").getAsInt();
            return new RichardsonLucyDeconvolutionParams(radius, sigma, iterations);
        }
        return null;
    }

    @Override
    public JsonElement serialize(GeometryParams src, Type typeOfSrc, JsonSerializationContext context) {
        var jsonObject = new JsonObject();
        src.tilt().ifPresent(tilt -> jsonObject.addProperty("tilt", tilt));
        src.xyRatio().ifPresent(ratio -> jsonObject.addProperty("xyRatio", ratio));
        jsonObject.addProperty("horizontalMirror", src.isHorizontalMirror());
        jsonObject.addProperty("verticalMirror", src.isVerticalMirror());
        jsonObject.addProperty("autocorrectAngleP", String.valueOf(src.isAutocorrectAngleP()));
        jsonObject.addProperty("rotation", src.rotation().toString());
        jsonObject.addProperty("autocropMode", src.autocropMode().toString());
        src.fixedWidth().ifPresent(fw -> jsonObject.addProperty("fixedWidth", fw));
        jsonObject.addProperty("deconvolutionMode", src.deconvolutionMode().toString());
        src.richardsonLucyDeconvolutionParams().ifPresent(rl -> {
            var value = new JsonObject();
            value.addProperty("radius", rl.radius());
            value.addProperty("sigma", rl.sigma());
            value.addProperty("iterations", rl.iterations());
            jsonObject.add("richardsonLucyDeconvolutionParams", value);
        });
        jsonObject.addProperty("forcePolynomial", src.isForcePolynomial());
        src.forcedPolynomial().ifPresent(forcedPolynomial -> jsonObject.addProperty("forcedPolynomial", forcedPolynomial));
        jsonObject.addProperty("spectrumVFlip", src.isSpectrumVFlip());
        jsonObject.addProperty("ellipseFittingMode", src.ellipseFittingMode().toString());
        return jsonObject;
    }
}
