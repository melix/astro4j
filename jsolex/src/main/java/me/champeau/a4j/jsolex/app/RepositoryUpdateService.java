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
package me.champeau.a4j.jsolex.app;

import me.champeau.a4j.jsolex.processing.expr.repository.ScriptRepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Service responsible for checking and updating script repositories at startup.
 * This service runs periodic checks to refresh script repositories from remote sources.
 */
public class RepositoryUpdateService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryUpdateService.class);
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);

    private final Configuration configuration;
    private final ScriptRepositoryManager repositoryManager;

    /**
     * Creates a new repository update service with default configuration.
     */
    public RepositoryUpdateService() {
        this.configuration = Configuration.getInstance();
        this.repositoryManager = new ScriptRepositoryManager();
    }

    /**
     * Checks if repository updates are needed at application startup.
     * Updates are performed in a background thread if the check interval has elapsed.
     */
    public void checkAtStartup() {
        var lastCheck = configuration.getLastRepositoryCheckTime();
        var now = Instant.now();
        var timeSinceLastCheck = Duration.between(lastCheck, now);

        if (timeSinceLastCheck.compareTo(CHECK_INTERVAL) >= 0) {
            LOGGER.debug(message("repository.update.last.check.refreshing"), timeSinceLastCheck);
            new Thread(this::checkForUpdates, "repository-startup-check").start();
        } else {
            LOGGER.debug(message("repository.update.last.check.skipping"), timeSinceLastCheck);
        }
    }

    /**
     * Performs the actual check and update of all enabled repositories.
     * This method runs in a background thread and updates repository metadata.
     */
    private void checkForUpdates() {
        try {
            LOGGER.debug(message("repository.update.checking"));
            var repositories = configuration.getScriptRepositories();

            for (var repository : repositories) {
                if (!repository.enabled()) {
                    continue;
                }
                try {
                    repositoryManager.refreshRepository(repository);
                    var updated = repository.withLastCheck(Instant.now());
                    var allRepos = configuration.getScriptRepositories();
                    var index = allRepos.indexOf(repository);
                    if (index >= 0) {
                        allRepos.set(index, updated);
                        configuration.setScriptRepositories(allRepos);
                    }
                    LOGGER.debug(message("repository.updated"), repository.name());
                } catch (Exception e) {
                    LOGGER.warn(message("repository.update.failed"), repository.name(), e);
                }
            }

            configuration.setLastRepositoryCheckTime(Instant.now());
        } catch (Exception e) {
            LOGGER.error(message("repository.update.error"), e);
        }
    }
}
