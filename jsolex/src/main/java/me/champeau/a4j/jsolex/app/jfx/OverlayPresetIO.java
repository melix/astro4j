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
package me.champeau.a4j.jsolex.app.jfx;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public final class OverlayPresetIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayPresetIO.class);
    private static final String PRESETS_FILENAME = "overlay-presets.json";
    private static final String LEGACY_FILENAME = "overlay-preset.json";

    private OverlayPresetIO() {
    }

    private static Path jsolexDir() {
        var dir = VersionUtil.getJsolexDir();
        try {
            createDirectoriesIfNeeded(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create JSol'Ex configuration directory", e);
        }
        return dir;
    }

    public static List<OverlayPreset> loadAll() {
        var dir = jsolexDir();
        var file = dir.resolve(PRESETS_FILENAME);
        if (!Files.exists(file)) {
            return migrateLegacyIfNeeded(dir);
        }
        try (var reader = FilesUtils.newTextReader(file)) {
            var listType = new TypeToken<List<OverlayPreset>>() {}.getType();
            List<OverlayPreset> presets = new Gson().fromJson(reader, listType);
            return presets != null ? new ArrayList<>(presets) : new ArrayList<>();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load overlay presets from {}", file, e);
            return new ArrayList<>();
        }
    }

    private static List<OverlayPreset> migrateLegacyIfNeeded(Path dir) {
        var legacy = dir.resolve(LEGACY_FILENAME);
        if (!Files.exists(legacy)) {
            return new ArrayList<>();
        }
        try (var reader = FilesUtils.newTextReader(legacy)) {
            var state = new Gson().fromJson(reader, ImageOverlayState.class);
            if (state == null) {
                return new ArrayList<>();
            }
            var preset = new OverlayPreset("Default", state);
            var migrated = new ArrayList<OverlayPreset>();
            migrated.add(preset);
            saveAll(migrated);
            Files.deleteIfExists(legacy);
            LOGGER.info("Migrated legacy overlay preset to multi-preset storage");
            return migrated;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to migrate legacy overlay preset from {}", legacy, e);
            return new ArrayList<>();
        }
    }

    private static void saveAll(List<OverlayPreset> presets) {
        var file = jsolexDir().resolve(PRESETS_FILENAME);
        try (var writer = FilesUtils.newTextWriter(file)) {
            new Gson().toJson(presets, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save overlay presets to: " + file, e);
        }
    }

    public static void save(String name, ImageOverlayState state) {
        var presets = loadAll();
        presets.removeIf(p -> p.name().equals(name));
        presets.add(new OverlayPreset(name, state.asPreset()));
        saveAll(presets);
    }

    public static void delete(String name) {
        var presets = loadAll();
        if (presets.removeIf(p -> p.name().equals(name))) {
            saveAll(presets);
        }
    }

    public static List<String> names() {
        return loadAll().stream().map(OverlayPreset::name).sorted().toList();
    }
}
