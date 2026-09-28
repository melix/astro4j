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
 * A window of the application.
 *
 * @param id the window identifier, stable for the lifetime of the window
 * @param title the window title, or null for popups and menus
 * @param type the kind of window, e.g. {@code Stage}, {@code ContextMenu}
 * @param focused whether the window has the focus
 * @param bounds the window bounds on screen
 */
@Serdeable
@Schema(description = "A window currently showing")
public record UiWindow(@Schema(description = "Window identifier, stable for the lifetime of the window. Pass it as the window parameter of the other endpoints.") String id,
                       @Schema(description = "Window title, or null for popups and menus", nullable = true) String title,
                       @Schema(description = "Kind of window, e.g. Stage, ContextMenu, Tooltip") String type,
                       @Schema(description = "Whether the window has the keyboard focus") boolean focused,
                       @Schema(description = "Window bounds on screen") UiBounds bounds) {
}
