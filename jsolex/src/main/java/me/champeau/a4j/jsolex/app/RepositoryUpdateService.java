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

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Service responsible for checking and updating script repositories at startup.
 * This service runs periodic checks to refresh script repositories from remote sources.
 */
public class RepositoryUpdateService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryUpdateService.class);

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
     * Checks for repository updates at application startup, in a background thread.
     * Each repository is refreshed independently, if it wasn't successfully refreshed recently.
     */
    public void checkAtStartup() {
        new Thread(this::checkForUpdates, "repository-startup-check").start();
    }

    /**
     * Performs the actual check and update of all enabled repositories.
     * This method runs in a background thread.
     */
    private void checkForUpdates() {
        try {
            LOGGER.debug(message("repository.update.checking"));
            for (var repository : configuration.getScriptRepositories()) {
                if (!repository.enabled()) {
                    continue;
                }
                if (!repositoryManager.shouldCheckForUpdates(repository)) {
                    LOGGER.debug(message("repository.update.skipping"), repository.name());
                    continue;
                }
                try {
                    repositoryManager.refreshRepository(repository);
                    LOGGER.debug(message("repository.updated"), repository.name());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LOGGER.warn(message("repository.update.failed"), repository.name(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.error(message("repository.update.error"), e);
        }
    }
}
