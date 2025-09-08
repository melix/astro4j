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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class Bass2000ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bass2000ConfigService.class);
    private static final String CONFIG_URL = "https://melix.github.io/astro4j/bass2000.json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    
    private static final Bass2000ConfigService INSTANCE = new Bass2000ConfigService();
    
    private final HttpClient httpClient;
    private final Gson gson;
    private volatile Bass2000Configuration cachedConfig;
    
    private Bass2000ConfigService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.gson = new Gson();
    }
    
    public static Bass2000ConfigService getInstance() {
        return INSTANCE;
    }

    public Optional<Bass2000Configuration> fetchConfiguration() {
        if (cachedConfig != null) {
            return Optional.of(cachedConfig);
        }
        
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(CONFIG_URL))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
                    
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                var config = gson.fromJson(response.body(), Bass2000Configuration.class);
                cachedConfig = config;
                LOGGER.debug("Successfully fetched BASS2000 configuration: enabled={}, requiresVersion={}", 
                            config.enabled(), config.requiresVersion());
                return Optional.of(config);
            } else {
                LOGGER.warn("Failed to fetch BASS2000 configuration: HTTP {}", response.statusCode());
                return Optional.empty();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("Failed to fetch BASS2000 configuration", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    public boolean isBass2000Enabled() {
        var config = fetchConfiguration();
        if (config.isEmpty()) {
            return false;
        }
        
        var cfg = config.get();
        if (!cfg.enabled()) {
            return false;
        }
        
        return isVersionSupported(cfg.requiresVersion());
    }

    public enum DisabledReason {
        ENABLED,
        CONFIG_FETCH_FAILED,
        EXPLICITLY_DISABLED,
        VERSION_TOO_OLD
    }

    public DisabledReason getDisabledReason() {
        var config = fetchConfiguration();
        if (config.isEmpty()) {
            return DisabledReason.CONFIG_FETCH_FAILED;
        }
        
        var cfg = config.get();
        if (!cfg.enabled()) {
            return DisabledReason.EXPLICITLY_DISABLED;
        }
        
        if (!isVersionSupported(cfg.requiresVersion())) {
            return DisabledReason.VERSION_TOO_OLD;
        }
        
        return DisabledReason.ENABLED;
    }

    public String getRequiredVersion() {
        var config = fetchConfiguration();
        return config.map(Bass2000Configuration::requiresVersion).orElse(null);
    }

    private boolean isVersionSupported(String requiredVersion) {
        if (requiredVersion == null || requiredVersion.trim().isEmpty()) {
            return true;
        }
        
        try {
            var currentVersion = VersionUtil.getVersion();
            return compareVersions(currentVersion, requiredVersion) >= 0;
        } catch (Exception e) {
            LOGGER.warn("Failed to compare versions", e);
            return true;
        }
    }

    private int compareVersions(String version1, String version2) {
        var parts1 = version1.split("\\.");
        var parts2 = version2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int v1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int v2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            if (part.contains("-")) {
                part = part.substring(0, part.indexOf("-"));
            }
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}