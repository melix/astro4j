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

/**
 * Service for managing BASS2000 configuration fetched from a remote endpoint.
 * This service handles fetching, caching, and validating configuration settings
 * that control BASS2000 upload functionality.
 */
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

    /**
     * Returns the singleton instance of the Bass2000ConfigService.
     *
     * @return the singleton instance
     */
    public static Bass2000ConfigService getInstance() {
        return INSTANCE;
    }

    /**
     * Fetches the BASS2000 configuration from the remote endpoint.
     * The configuration is cached after the first successful fetch.
     *
     * @return an Optional containing the configuration if successfully fetched, empty otherwise
     */
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

    /**
     * Checks whether BASS2000 functionality is currently enabled.
     * This considers both the remote configuration status and version compatibility.
     *
     * @return true if BASS2000 is enabled and the current version is supported, false otherwise
     */
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

    /**
     * Enumeration of reasons why BASS2000 functionality might be disabled.
     */
    public enum DisabledReason {
        /**
         * BASS2000 is enabled and functional.
         */
        ENABLED,
        /**
         * Failed to fetch the configuration from the remote endpoint.
         */
        CONFIG_FETCH_FAILED,
        /**
         * BASS2000 is explicitly disabled in the remote configuration.
         */
        EXPLICITLY_DISABLED,
        /**
         * The current application version is older than the minimum required version.
         */
        VERSION_TOO_OLD
    }

    /**
     * Determines the reason why BASS2000 is disabled, or confirms it is enabled.
     *
     * @return the reason for BASS2000's current state
     */
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

    /**
     * Retrieves the minimum required application version from the remote configuration.
     *
     * @return the required version string, or null if the configuration could not be fetched
     */
    public String getRequiredVersion() {
        var config = fetchConfiguration();
        return config.map(Bass2000Configuration::requiresVersion).orElse(null);
    }

    private boolean isVersionSupported(String requiredVersion) {
        return VersionUtil.isVersionSupported(requiredVersion);
    }
}