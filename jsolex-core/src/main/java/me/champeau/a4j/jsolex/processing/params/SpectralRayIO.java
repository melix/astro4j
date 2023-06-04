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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class SpectralRayIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectralRayIO.class);

    private SpectralRayIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = Paths.get(System.getProperty("user.home"), ".jsolex");
        try {
            Files.createDirectories(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("spectral-rays.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        return builder.create();
    }

    public static List<SpectralRay> loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        List<SpectralRay> rays = readFrom(defaultsFile);
        if (rays != null) {
            return rays;
        }
        LOGGER.info("No config file found at {}. Using default rays", defaultsFile);
        return SpectralRay.predefined();
    }

    @SuppressWarnings("unchecked")
    public static List<SpectralRay> readFrom(Path configFile) {
        Gson gson = newGson();
        if (Files.exists(configFile)) {
            try (var reader = new InputStreamReader(new FileInputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
                var object = gson.fromJson(reader, TypeToken.getParameterized(List.class, SpectralRay.class));
                return (List<SpectralRay>) object;
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return null;
    }

    public static void saveTo(List<SpectralRay> rays, File destination) {
        try {
            Files.createDirectories(destination.getParentFile().toPath());
            try (var writer = new OutputStreamWriter(new FileOutputStream(destination), StandardCharsets.UTF_8)) {
                var gson = newGson();
                writer.write(gson.toJson(rays));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveDefaults(List<SpectralRay> rays) {
        var defaultsFile = resolveDefaultsFile();
        saveTo(rays, defaultsFile.toFile());
    }

}
