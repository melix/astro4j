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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class UserPresetIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPresetIO.class);
    private static final String USER_PRESETS_FILENAME = "user-presets.json";

    private UserPresetIO() {
    }

    private static Path resolvePresetsFile() {
        var jsolexDir = VersionUtil.getJsolexDir();
        try {
            createDirectoriesIfNeeded(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create JSol'Ex configuration directory", e);
        }
        return jsolexDir.resolve(USER_PRESETS_FILENAME);
    }

    private static Gson createGson() {
        var builder = new Gson().newBuilder();
        builder.registerTypeAdapter(ImageMathParams.class, new ImageMathParamsSerializer());
        return builder.create();
    }

    public static List<UserPreset> loadPresets() {
        var presetsFile = resolvePresetsFile();
        if (!Files.exists(presetsFile)) {
            LOGGER.debug("No user presets file found at: {}", presetsFile);
            return new ArrayList<>();
        }

        try (var reader = FilesUtils.newTextReader(presetsFile)) {
            var gson = createGson();
            var listType = new TypeToken<List<UserPreset>>() {}.getType();
            var presets = gson.<List<UserPreset>>fromJson(reader, listType);
            return presets != null ? new ArrayList<>(presets) : new ArrayList<>();
        } catch (IOException e) {
            LOGGER.error("Failed to load user presets from: " + presetsFile, e);
            return new ArrayList<>();
        }
    }

    public static void savePresets(List<UserPreset> presets) {
        var presetsFile = resolvePresetsFile();
        try {
            createDirectoriesIfNeeded(presetsFile.getParent());
            try (var writer = FilesUtils.newTextWriter(presetsFile)) {
                var gson = createGson();
                gson.toJson(presets, writer);
            }
            LOGGER.debug("Saved {} user presets to: {}", presets.size(), presetsFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save user presets to: " + presetsFile, e);
        }
    }

    public static void savePreset(UserPreset preset) {
        var presets = loadPresets();

        // Remove any existing preset with the same name
        presets.removeIf(existing -> existing.displayName().equals(preset.displayName()));

        // Add the new/updated preset
        presets.add(preset);

        savePresets(presets);
    }

    public static void deletePreset(String displayName) {
        var presets = loadPresets();
        var originalSize = presets.size();

        presets.removeIf(preset -> preset.displayName().equals(displayName));

        if (presets.size() != originalSize) {
            savePresets(presets);
            LOGGER.debug("Deleted user preset: {}", displayName);
        } else {
            LOGGER.warn("No user preset found with name: {}", displayName);
        }
    }

    public static boolean presetExists(String displayName) {
        return loadPresets().stream()
                .anyMatch(preset -> preset.displayName().equals(displayName));
    }

    public static List<String> getPresetNames() {
        return loadPresets().stream()
                .map(UserPreset::displayName)
                .sorted()
                .toList();
    }
}