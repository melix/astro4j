/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.server.ui;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Criteria to find nodes. Null criteria are ignored; all the others must match.
 *
 * @param window the window identifier, or null for all windows
 * @param id the exact JavaFX id
 * @param text a substring of the displayed text, case insensitive
 * @param type the exact simple class name
 */
@Serdeable
@Schema(description = "Criteria to find nodes. Null criteria are ignored; all the others must match.")
public record UiQuery(@Schema(description = "Window identifier, or null to search all windows", nullable = true) String window,
                      @Schema(description = "Exact JavaFX id", nullable = true) String id,
                      @Schema(description = "Case-insensitive substring of the displayed text", nullable = true) String text,
                      @Schema(description = "Exact simple class name of the node, e.g. Button", nullable = true) String type) {
}
