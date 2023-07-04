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
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class FileNamingPatternsIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileNamingPatternsIO.class);

    private FileNamingPatternsIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = Paths.get(System.getProperty("user.home"), ".jsolex");
        try {
            Files.createDirectories(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("filename-patterns.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        return builder.create();
    }

    public static List<NamedPattern> loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        List<NamedPattern> patterns = readFrom(defaultsFile);
        if (patterns != null) {
            return patterns;
        }
        LOGGER.info("No config file found at {}. Using default patterns", defaultsFile);
        return FileNamingPatternsIO.predefined();
    }

    public static List<NamedPattern> predefined() {
        return FileNamingStrategy.DEFAULTS;
    }

    @SuppressWarnings("unchecked")
    public static List<NamedPattern> readFrom(Path configFile) {
        Gson gson = newGson();
        if (Files.exists(configFile)) {
            try (var reader = FilesUtils.newTextReader(configFile)) {
                var object = gson.fromJson(reader, TypeToken.getParameterized(List.class, NamedPattern.class));
                return fix((List<NamedPattern>) object);
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return null;
    }

    private static List<NamedPattern> fix(List<NamedPattern> patterns) {
        return patterns.stream()
                .map(pattern -> {
                    var fixed = pattern;
                    if (pattern.dateFormat() == null) {
                        fixed = fixed.withDateFormat(FileNamingStrategy.DEFAULT_DATE_FORMAT);
                    }
                    if (fixed.datetimeFormat() == null) {
                        fixed = fixed.withDateTimeFormat(FileNamingStrategy.DEFAULT_DATETIME_FORMAT);
                    }
                    if (fixed.datetimeFormat().contains(":")) {
                        fixed = fixed.withDateTimeFormat(fixed.datetimeFormat().replace(":", ""));
                    }
                    if (fixed.dateFormat().contains(":")) {
                        fixed = fixed.withDateFormat(fixed.dateFormat().replace(":", ""));
                    }
                    return fixed;
                }).toList();
    }

    public static void saveTo(List<NamedPattern> patterns, File destination) {
        try {
            Files.createDirectories(destination.getParentFile().toPath());
            try (var writer = FilesUtils.newTextWriter(destination.toPath())) {
                var gson = newGson();
                writer.write(gson.toJson(patterns));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveDefaults(List<NamedPattern> patterns) {
        var defaultsFile = resolveDefaultsFile();
        saveTo(patterns, defaultsFile.toFile());
    }

}
