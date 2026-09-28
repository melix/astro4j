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
 * Bounds of a window or a node, in screen coordinates (JavaFX logical pixels).
 * Multiply by {@link UiState#outputScale()} to obtain physical pixels.
 *
 * @param x the left edge
 * @param y the top edge
 * @param width the width
 * @param height the height
 */
@Serdeable
@Schema(description = "Bounds in screen coordinates, expressed in JavaFX logical pixels. Multiply every value by UiState.outputScale to obtain physical pixels, e.g. for xdotool.")
public record UiBounds(@Schema(description = "Left edge, in logical screen pixels") double x,
                       @Schema(description = "Top edge, in logical screen pixels") double y,
                       @Schema(description = "Width, in logical pixels") double width,
                       @Schema(description = "Height, in logical pixels") double height) {
}
