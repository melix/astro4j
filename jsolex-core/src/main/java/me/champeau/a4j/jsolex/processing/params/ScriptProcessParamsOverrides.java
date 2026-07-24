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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Process parameter overrides declared by scripts. The overrides are expressed using
 * the same structure as the {@code config.json} file which JSol'Ex writes next to the
 * generated images, so any process parameter can be overridden without the scripting
 * language having to know about it.
 */
public final class ScriptProcessParamsOverrides {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptProcessParamsOverrides.class);

    private ScriptProcessParamsOverrides() {
    }

    /**
     * Converts the raw text of an override value into a JSON value. Values are typed
     * leniently: {@code true} and {@code false} become booleans, numbers become numbers,
     * and anything else is kept as a string.
     *
     * @param value the raw value
     * @return the corresponding JSON value
     */
    public static JsonPrimitive toJsonValue(String value) {
        var trimmed = value.trim();
        var lowerCase = trimmed.toLowerCase(Locale.US);
        if ("true".equals(lowerCase) || "false".equals(lowerCase)) {
            return new JsonPrimitive(Boolean.parseBoolean(lowerCase));
        }
        try {
            return new JsonPrimitive(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            // not an integer, fall through
        }
        try {
            return new JsonPrimitive(Double.parseDouble(trimmed));
        } catch (NumberFormatException e) {
            return new JsonPrimitive(trimmed);
        }
    }

    /**
     * Sets a value at the supplied dot-separated path, creating intermediate objects
     * as needed.
     *
     * @param target the object to update
     * @param path the dot-separated path
     * @param value the value to set
     */
    public static void putAtPath(JsonObject target, String path, JsonElement value) {
        var segments = path.split("\\.");
        var current = target;
        for (int i = 0; i < segments.length - 1; i++) {
            var segment = segments[i];
            var child = current.get(segment);
            if (child == null || !child.isJsonObject()) {
                child = new JsonObject();
                current.add(segment, child);
            }
            current = child.getAsJsonObject();
        }
        current.add(segments[segments.length - 1], value);
    }

    /**
     * Flattens an override object into dot-separated paths.
     *
     * @param overrides the overrides
     * @return the values, indexed by dot-separated path, in declaration order
     */
    public static Map<String, String> flatten(JsonObject overrides) {
        var result = new LinkedHashMap<String, String>();
        flattenInto(overrides, "", result);
        return result;
    }

    /**
     * Builds an override object from values indexed by dot-separated paths.
     *
     * @param values the values, indexed by dot-separated path
     * @return the corresponding override object
     */
    public static JsonObject fromFlattened(Map<String, String> values) {
        var result = new JsonObject();
        for (var entry : values.entrySet()) {
            putAtPath(result, entry.getKey(), toJsonValue(entry.getValue()));
        }
        return result;
    }

    /**
     * Lists the paths of the process parameters which can be overridden, that is to say
     * all the scalar values of the configuration.
     *
     * @param params the process parameters to introspect
     * @return the overridable paths, in configuration order
     */
    public static List<String> overridablePaths(ProcessParams params) {
        var gson = ProcessParamsIO.newGsonBuilder().create();
        var flattened = new LinkedHashMap<String, String>();
        flattenInto(gson.toJsonTree(params).getAsJsonObject(), "", flattened);
        return List.copyOf(flattened.keySet());
    }

    private static void flattenInto(JsonObject source, String prefix, Map<String, String> target) {
        for (var entry : source.entrySet()) {
            var path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            var value = entry.getValue();
            if (value.isJsonObject()) {
                flattenInto(value.getAsJsonObject(), path, target);
            } else if (value.isJsonPrimitive()) {
                target.put(path, value.getAsString());
            }
        }
    }

    /**
     * Merges the overrides declared by several scripts. When two scripts override the
     * same parameter with different values, the first one wins and the conflict is logged.
     *
     * @param overrides the overrides, in script declaration order
     * @return the merged overrides
     */
    public static JsonObject merge(Iterable<JsonObject> overrides) {
        var merged = new JsonObject();
        for (var override : overrides) {
            mergeInto(merged, override.deepCopy(), "", false);
        }
        return merged;
    }

    /**
     * Applies the overrides to the supplied process parameters.
     *
     * @param params the process parameters
     * @param overrides the overrides declared by scripts
     * @return the process parameters with the overrides applied
     */
    public static ProcessParams apply(ProcessParams params, JsonObject overrides) {
        if (overrides.isEmpty()) {
            return params;
        }
        var gson = ProcessParamsIO.newGsonBuilder().create();
        var tree = gson.toJsonTree(params).getAsJsonObject();
        var applicable = new JsonObject();
        for (var entry : overrides.entrySet()) {
            if (tree.has(entry.getKey())) {
                applicable.add(entry.getKey(), entry.getValue());
            } else {
                LOGGER.warn("Ignoring unknown process parameter override '{}'", entry.getKey());
            }
        }
        if (applicable.isEmpty()) {
            return params;
        }
        mergeInto(tree, applicable, "", true);
        try {
            return gson.fromJson(tree, ProcessParams.class);
        } catch (RuntimeException e) {
            LOGGER.warn("Ignoring process parameter overrides, they could not be applied: {}", e.getMessage());
            return params;
        }
    }

    private static void mergeInto(JsonObject target, JsonObject source, String prefix, boolean overwrite) {
        for (var entry : source.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            var existing = target.get(key);
            var path = prefix.isEmpty() ? key : prefix + "." + key;
            if (existing != null && existing.isJsonObject() && value.isJsonObject()) {
                mergeInto(existing.getAsJsonObject(), value.getAsJsonObject(), path, overwrite);
            } else if (existing == null || overwrite) {
                target.add(key, value);
            } else if (!existing.equals(value)) {
                LOGGER.warn("Conflicting override for '{}': keeping {} and ignoring {}", path, existing, value);
            }
        }
    }
}
