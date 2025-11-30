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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public abstract class SetupsIO {
    private static final Setup SUNSCAN = new Setup("Sunscan by Staros", "Sunscan", 200, 25, null, null, "IMX477", 3.1, null, null, null, false, false, false);

    private SetupsIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = VersionUtil.getJsolexDir();
        try {
            createDirectoriesIfNeeded(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("setups.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        return builder.create();
    }

    public static List<Setup> loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        List<Setup> patterns = readFrom(defaultsFile);
        if (patterns != null) {
            if (patterns.stream().noneMatch(s -> s.label().toLowerCase(Locale.US).contains("sunscan") || (s.telescope() != null && s.telescope().toLowerCase(Locale.US).contains("sunscan")))) {
                patterns = Stream.concat(patterns.stream(), Stream.of(SUNSCAN)).toList();
            }
            return patterns;
        }
        return List.of(SUNSCAN);
    }

    @SuppressWarnings("unchecked")
    public static List<Setup> readFrom(Path configFile) {
        Gson gson = newGson();
        if (Files.exists(configFile)) {
            try (var reader = FilesUtils.newTextReader(configFile)) {
                var object = gson.fromJson(reader, TypeToken.getParameterized(List.class, Setup.class));
                return (List<Setup>) object;
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return null;
    }

    public static void saveTo(List<Setup> patterns, File destination) {
        try {
            createDirectoriesIfNeeded(destination.getParentFile().toPath());
            try (var writer = FilesUtils.newTextWriter(destination.toPath())) {
                var gson = newGson();
                writer.write(gson.toJson(patterns));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveDefaults(List<Setup> patterns) {
        var defaultsFile = resolveDefaultsFile();
        saveTo(patterns, defaultsFile.toFile());
    }

}
