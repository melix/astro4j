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
package me.champeau.a4j.jsolex.processing.util;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import me.champeau.a4j.jsolex.processing.params.OutputMetadata;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageMetadata;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;
import me.champeau.a4j.ser.ImageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes and deserializes the polymorphic metadata map carried by an
 * {@link ImageWrapper} to JSON. Only an allowlist of well-known metadata types is
 * handled; entries of any other type are silently ignored on write, and unknown
 * keys are skipped on read. Any entry that fails to (de)serialize is dropped with
 * a warning rather than failing the whole operation, which keeps session files
 * forward-compatible across application versions.
 */
public final class MetadataIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataIO.class);

    private static final List<Class<?>> ALLOWLIST = List.of(
            ProcessParams.class,
            PixelShift.class,
            PixelShiftRange.class,
            TransformationHistory.class,
            GeneratedImageMetadata.class,
            ReferenceCoords.class,
            SourceInfo.class,
            MetadataTable.class,
            SolarParameters.class,
            Redshifts.class,
            ActiveRegions.class,
            OutputMetadata.class,
            Ellipse.class,
            ImageMetadata.class
    );

    private static final Map<String, Class<?>> BY_NAME = buildIndex();

    private static final Gson GSON = ProcessParamsIO.newGsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, type, ctx) -> new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, type, ctx) -> LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .registerTypeAdapter(Ellipse.class,
                    (JsonSerializer<Ellipse>) (src, type, ctx) -> ctx.serialize(src.getCartesianCoefficients()))
            .registerTypeAdapter(Ellipse.class,
                    (JsonDeserializer<Ellipse>) (json, type, ctx) -> Ellipse.ofCartesian(ctx.deserialize(json, DoubleSextuplet.class)))
            .create();

    private MetadataIO() {
    }

    private static Map<String, Class<?>> buildIndex() {
        var index = new LinkedHashMap<String, Class<?>>();
        for (var clazz : ALLOWLIST) {
            index.put(clazz.getName(), clazz);
        }
        return index;
    }

    /**
     * Serializes the allowlisted entries of a metadata map to a JSON string.
     *
     * @param metadata the metadata map (keyed by metadata class)
     * @return a JSON object string keyed by fully qualified class name
     */
    public static String serialize(Map<Class<?>, Object> metadata) {
        var root = new JsonObject();
        for (var entry : metadata.entrySet()) {
            var clazz = entry.getKey();
            if (!BY_NAME.containsKey(clazz.getName())) {
                continue;
            }
            try {
                root.add(clazz.getName(), GSON.toJsonTree(entry.getValue(), clazz));
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not serialize metadata of type {}: {}", clazz.getName(), ex.getMessage());
            }
        }
        return GSON.toJson(root);
    }

    /**
     * Rebuilds a metadata map from a JSON string produced by {@link #serialize}.
     * Unknown types and entries that fail to deserialize are skipped.
     *
     * @param json the JSON string
     * @return a mutable metadata map keyed by metadata class
     */
    public static Map<Class<?>, Object> deserialize(String json) {
        var result = MutableMap.<Class<?>, Object>of();
        if (json == null || json.isBlank()) {
            return result;
        }
        var root = JsonParser.parseString(json).getAsJsonObject();
        for (var entry : root.entrySet()) {
            var clazz = BY_NAME.get(entry.getKey());
            if (clazz == null) {
                LOGGER.debug("Skipping unknown metadata type {}", entry.getKey());
                continue;
            }
            try {
                var value = GSON.fromJson(entry.getValue(), clazz);
                if (value != null) {
                    result.put(clazz, value);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not deserialize metadata of type {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        return result;
    }
}
