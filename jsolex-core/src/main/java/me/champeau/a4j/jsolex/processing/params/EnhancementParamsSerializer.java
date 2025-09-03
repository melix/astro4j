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
import me.champeau.a4j.jsolex.processing.spectrum.FlatCreator;
import me.champeau.a4j.jsolex.processing.sun.workflow.JaggingCorrection;

import java.lang.reflect.Type;
import java.nio.file.Path;

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

    private static String getNullableString(JsonObject obj, String key) {
        var element = obj.get(key);
        return element == null ? null : element.getAsString();
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
            var masterFlat = getNullableString(obj, "masterFlatFile");
            var slitDetectionSigma = getNullableDouble(obj, "slitDetectionSigma");
            if (slitDetectionSigma == null) {
                slitDetectionSigma = FlatCreator.DEFAULT_SLIT_DETECTION_SIGMA;
            }
            var jaggingCorrectionParams = obj.getAsJsonObject("jaggingCorrectionParams");
            JaggingCorrectionParams jaggingCorrection = new JaggingCorrectionParams(false, JaggingCorrection.DEFAULT_SIGMA);
            if (jaggingCorrectionParams != null) {
                var enabled = getNullableBoolean(jaggingCorrectionParams, "enabled");
                var sigma = getNullableDouble(jaggingCorrectionParams, "sigma");
                jaggingCorrection = new JaggingCorrectionParams(enabled, sigma == null ? JaggingCorrection.DEFAULT_SIGMA : sigma);
            }
            SharpeningParams sharpeningParams = SharpeningParams.none();
            var sharpening = obj.getAsJsonObject("sharpeningParams");
            if (sharpening != null) {
                var method = sharpening.get("method");
                if (method != null) {
                    var sharpeningMethod = SharpeningMethod.valueOf(method.getAsString());
                    switch (sharpeningMethod) {
                        case NONE -> sharpeningParams = SharpeningParams.none();
                        case SHARPEN -> {
                            var kernelSize = getNullableInt(sharpening, "kernelSize");
                            sharpeningParams = SharpeningParams.sharpen(kernelSize == null ? 3 : kernelSize);
                        }
                        case UNSHARP_MASK -> {
                            var kernelSize = getNullableInt(sharpening, "kernelSize");
                            var strength = getNullableDouble(sharpening, "strength");
                            sharpeningParams = SharpeningParams.unsharpMask(kernelSize == null ? 3 : kernelSize, strength == null ? 1.0 : strength);
                        }
                    }
                }
            }
            return new EnhancementParams(artificialFlatCorrection, loPercentile, hiPercentile, order, masterFlat==null ? null : Path.of(masterFlat), slitDetectionSigma, jaggingCorrection, sharpeningParams);
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
        obj.addProperty("masterFlatFile", src.masterFlatFile() == null ? null : src.masterFlatFile().toString());
        obj.addProperty("slitDetectionSigma", src.slitDetectionSigma());
        var jaggingCorrectionParams = new JsonObject();
        jaggingCorrectionParams.addProperty("enabled", src.jaggingCorrectionParams().enabled());
        jaggingCorrectionParams.addProperty("sigma", src.jaggingCorrectionParams().sigma());
        obj.add("jaggingCorrectionParams", jaggingCorrectionParams);
        var sharpeningParams = new JsonObject();
        switch (src.sharpeningParams()) {
            case SharpeningParams.None none -> {
                sharpeningParams.addProperty("method", "NONE");
            }
            case SharpeningParams.Sharpen sharpen -> {
                sharpeningParams.addProperty("method", "SHARPEN");
                sharpeningParams.addProperty("kernelSize", sharpen.kernelSize());
            }
            case SharpeningParams.UnsharpMask unsharpMask -> {
                sharpeningParams.addProperty("method", "UNSHARP_MASK");
                sharpeningParams.addProperty("kernelSize", unsharpMask.kernelSize());
                sharpeningParams.addProperty("strength", unsharpMask.strength());
            }
        }
        obj.add("sharpeningParams", sharpeningParams);
        return obj;
    }
}
