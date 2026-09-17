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
package me.champeau.a4j.jsolex.processing.expr.repository;

import java.nio.file.Path;
import java.util.Optional;

public record RemoteScript(
    ScriptRepository repository,
    String filename,
    Path localPath,
    String author,
    String title,
    String version,
    Optional<PendingUpdate> pendingUpdate
) {
    /**
     * A newer version of the script which is available in the repository but was not installed
     * because it requires a more recent version of JSol'Ex.
     *
     * @param version the version of the script available remotely
     * @param requiredVersion the minimal JSol'Ex version required by that script
     */
    public record PendingUpdate(String version, String requiredVersion) {
    }
}
