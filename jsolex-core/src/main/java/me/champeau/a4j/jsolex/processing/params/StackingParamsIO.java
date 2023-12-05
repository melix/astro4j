/*
 * Copyright 2003-2021 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.expr.impl.MosaicComposition;
import me.champeau.a4j.jsolex.processing.expr.impl.Stacking;
import me.champeau.a4j.jsolex.processing.sun.workflow.StackingWorkflow;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StackingParamsIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackingParamsIO.class);

    private StackingParamsIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = Paths.get(System.getProperty("user.home"), ".jsolex");
        try {
            Files.createDirectories(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("stacking-mosaic.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        builder.registerTypeAdapter(File.class, new FileSerializer());
        return builder.create();
    }

    public static StackingWorkflow.Parameters loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        StackingWorkflow.Parameters params = readFrom(defaultsFile);
        if (params != null) {
            return params;
        }
        LOGGER.info("No config file found at {}. Using default stacking params", defaultsFile);
        return predefined();
    }

    public static StackingWorkflow.Parameters readFrom(Path configFile) {
        Gson gson = newGson();
        if (Files.exists(configFile)) {
            try (var reader = FilesUtils.newTextReader(configFile)) {
                return gson.fromJson(reader, StackingWorkflow.Parameters.class);
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return null;
    }

    public static StackingWorkflow.Parameters predefined() {
        return new StackingWorkflow.Parameters(
            Stacking.DEFAULT_TILE_SIZE,
            Stacking.DEFAULT_OVERLAP_FACTOR,
            false,
            false,
            null,
            true,
            MosaicComposition.DEFAULT_TILE_SIZE,
            MosaicComposition.DEFAULT_OVERLAP_FACTOR,
            null
        );
    }

    public static void saveTo(StackingWorkflow.Parameters params, File destination) {
        try {
            Files.createDirectories(destination.getParentFile().toPath());
            try (var writer = FilesUtils.newTextWriter(destination.toPath())) {
                var gson = newGson();
                writer.write(gson.toJson(params));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveDefaults(StackingWorkflow.Parameters params) {
        var defaultsFile = resolveDefaultsFile();
        saveTo(params, defaultsFile.toFile());
    }

    private static class FileSerializer implements JsonSerializer<File>, JsonDeserializer<File> {

        @Override
        public File deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new File(json.getAsString());
        }

        @Override
        public JsonElement serialize(File src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getAbsolutePath());
        }
    }
}
