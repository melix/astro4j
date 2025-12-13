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

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Configuration data for BASS2000 upload functionality.
 * This record is deserialized from a remote JSON configuration.
 *
 * @param requiresVersion the minimum application version required for BASS2000 uploads
 * @param enabled whether BASS2000 uploads are currently enabled
 * @param message localized messages keyed by language code (e.g., "en", "fr")
 */
public record Bass2000Configuration(
        @SerializedName("requiresVersion") String requiresVersion,
        @SerializedName("enabled") boolean enabled,
        @SerializedName("message") Map<String, String> message
) {
    /**
     * Constructs a Bass2000Configuration with the specified parameters.
     * If the message map is null, an empty map is used instead.
     *
     * @param requiresVersion the minimum application version required
     * @param enabled whether BASS2000 is enabled
     * @param message localized messages by language code
     */
    public Bass2000Configuration(String requiresVersion, boolean enabled, Map<String, String> message) {
        this.requiresVersion = requiresVersion;
        this.enabled = enabled;
        this.message = message != null ? message : Map.of();
    }

    /**
     * Retrieves a localized message for the specified language.
     * Falls back to English if the requested language is not available.
     *
     * @param language the language code (e.g., "en", "fr")
     * @return the message in the requested language, or English, or empty string if neither exists
     */
    public String getMessage(String language) {
        return message.getOrDefault(language, message.getOrDefault("en", ""));
    }
}