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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public abstract class SpectralRayIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectralRayIO.class);
    private static final String SCHEMA_KEY = "spectral-ray";
    private static final int SCHEMA_VERSION = 3;
    /**
     * Lines shipped in earlier versions which have to be dropped from existing user
     * files, since rays which are no longer predefined are otherwise kept as user
     * defined ones. "Mercury (e)" is a lamp reference wavelength rather than a solar
     * absorption line; the two others were detection modes disguised as lines.
     */
    private static final Set<String> OBSOLETE_LABELS = Set.of("Mercury (e)", "Autodetect", "Free search");

    private SpectralRayIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = VersionUtil.getJsolexDir();
        try {
            createDirectoriesIfNeeded(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("spectral-rays.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        builder.registerTypeAdapter(SpectralRay.class, new SpectralRaySerializer());
        return builder.create();
    }

    public static List<SpectralRay> loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        List<SpectralRay> rays = readFrom(defaultsFile);
        if (rays != null) {
            return upgradeSchema(rays);
        }
        LOGGER.debug("No config file found at {}. Using default rays", defaultsFile);
        return SpectralRay.predefined();
    }

    private static List<SpectralRay> upgradeSchema(List<SpectralRay> rays) {
        var schemas = VersionUtil.readSchemaVersions();
        Object version = schemas.get(SCHEMA_KEY);
        if (version instanceof String str) {
            version = Integer.parseInt(str);
        }
        if (version == null || (int) version < SCHEMA_VERSION) {
            LOGGER.info("Updating spectral rays file using latest data");
            var newRays = mergeWithPredefined(rays);
            schemas.put(SCHEMA_KEY, String.valueOf(SCHEMA_VERSION));
            VersionUtil.writeSchemaVersions(schemas);
            saveDefaults(newRays);
            return newRays;
        }
        return rays;
    }

    /**
     * Merges the rays of a user file with the predefined ones: predefined lines
     * are added or refreshed, user defined ones are kept, and lines which are no
     * longer shipped are dropped.
     *
     * @param rays the rays read from the user file
     * @return the merged rays, sorted by wavelength
     */
    static List<SpectralRay> mergeWithPredefined(List<SpectralRay> rays) {
        List<SpectralRay> newRays = new ArrayList<>();
        var foundRays = new ArrayList<SpectralRay>();
        for (SpectralRay ray : SpectralRay.predefined()) {
            var found = rays.stream().filter(r -> r.equals(ray)).findFirst();
            if (found.isPresent()) {
                var current = found.get();
                foundRays.add(current);
                if (current.label().equals(SpectralRay.HELIUM_D3.label())) {
                    current = new SpectralRay(current.label(), SpectralRay.HELIUM_D3.colorCurve(), SpectralRay.HELIUM_D3.wavelength(), true, current.automaticScripts());
                }
                if (current.colorCurve() == null && !current.label().equals(SpectralRay.H_ALPHA.label())) {
                    current = new SpectralRay(current.label(), ray.colorCurve(), current.wavelength(), current.emission(), current.automaticScripts());
                }
                newRays.add(current);
            } else {
                newRays.add(ray);
            }
        }
        for (var ray : rays) {
            if (!foundRays.contains(ray) && !OBSOLETE_LABELS.contains(ray.label())) {
                newRays.add(ray);
            }
        }
        return newRays.stream().sorted(Comparator.comparingDouble(r -> r.wavelength().angstroms())).toList();
    }

    @SuppressWarnings("unchecked")
    public static List<SpectralRay> readFrom(Path configFile) {
        Gson gson = newGson();
        if (Files.exists(configFile)) {
            try (var reader = FilesUtils.newTextReader(configFile)) {
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
            createDirectoriesIfNeeded(destination.getParentFile().toPath());
            try (var writer = FilesUtils.newTextWriter(destination.toPath())) {
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

    public static String serializeToJson(SpectralRay ray) {
        return newGson().toJson(ray);
    }

    public static SpectralRay deserializeJson(String json) {
        return newGson().fromJson(json, SpectralRay.class);
    }

}
