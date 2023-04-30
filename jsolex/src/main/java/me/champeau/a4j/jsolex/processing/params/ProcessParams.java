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

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record ProcessParams(
        SpectrumParams spectrumParams,
        ObservationDetails observationDetails,
        DebugParams debugParams
) {

    private static Path resolveDefaultsFile() {
        var jsolexDir = Paths.get(System.getProperty("user.home"), ".jsolex");
        try {
            Files.createDirectories(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("process-params.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        builder.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer());
        return builder.create();
    }

    public static ProcessParams loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        if (Files.exists(defaultsFile)) {
            try (var reader = new InputStreamReader(new FileInputStream(defaultsFile.toFile()), StandardCharsets.UTF_8)) {
                Gson gson = newGson();
                var params = gson.fromJson(reader, ProcessParams.class);
                if (params != null) {
                    return params;
                }
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return new ProcessParams(
                new SpectrumParams(SpectralRay.H_ALPHA, SpectralRay.H_ALPHA.getDetectionThreshold()),
                new ObservationDetails(
                        "",
                        "Sol'Ex",
                        "",
                        null,
                        LocalDateTime.now().atZone(ZoneId.of("UTC")),
                        ""
                ),
                new DebugParams(false)
        );
    }

    public static void saveDefaults(ProcessParams params) {
        var defaultsFile = resolveDefaultsFile();
        try (var writer = new OutputStreamWriter(new FileOutputStream(defaultsFile.toFile()), StandardCharsets.UTF_8)) {
            var gson = newGson();
            writer.write(gson.toJson(params));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class ZonedDateTimeSerializer implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {

        @Override
        public ZonedDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return ZonedDateTime.parse(json.getAsString());
        }

        @Override
        public JsonElement serialize(ZonedDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }
}
