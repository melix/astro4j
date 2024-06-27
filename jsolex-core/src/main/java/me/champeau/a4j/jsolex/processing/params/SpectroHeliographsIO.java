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
import com.google.gson.reflect.TypeToken;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class SpectroHeliographsIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectroHeliographsIO.class);

    private SpectroHeliographsIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = VersionUtil.getJsolexDir();
        try {
            Files.createDirectories(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("shgs.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        return builder.create();
    }

    public static List<SpectroHeliograph> loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        List<SpectroHeliograph> patterns = readFrom(defaultsFile);
        if (patterns != null) {
            return patterns;
        }
        LOGGER.debug(message("no.config.file.found"), defaultsFile, "SHG");
        return SpectroHeliographsIO.predefined();
    }

    public static List<SpectroHeliograph> predefined() {
        return List.of(SpectroHeliograph.SOLEX);
    }

    @SuppressWarnings("unchecked")
    public static List<SpectroHeliograph> readFrom(Path configFile) {
        Gson gson = newGson();
        if (Files.exists(configFile)) {
            try (var reader = FilesUtils.newTextReader(configFile)) {
                var object = gson.fromJson(reader, TypeToken.getParameterized(List.class, SpectroHeliograph.class));
                return (List<SpectroHeliograph>) object;
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return null;
    }

    public static void saveTo(List<SpectroHeliograph> shgs, File destination) {
        try {
            Files.createDirectories(destination.getParentFile().toPath());
            try (var writer = FilesUtils.newTextWriter(destination.toPath())) {
                var gson = newGson();
                writer.write(gson.toJson(shgs));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveDefaults(List<SpectroHeliograph> patterns) {
        var defaultsFile = resolveDefaultsFile();
        saveTo(patterns, defaultsFile.toFile());
    }
}
