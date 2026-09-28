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

import java.util.List;

/**
 * A visible node of the scene graph.
 *
 * @param ref the node reference within its window: the child indices from the root, separated by slashes (the root is {@code ""})
 * @param id the JavaFX id of the node, or null
 * @param type the simple class name of the node
 * @param text the text displayed by the node (labels, buttons, text fields...), or null
 * @param styleClasses the CSS style classes
 * @param disabled whether the node is disabled
 * @param focused whether the node has the focus
 * @param selected whether the node is selected, for check boxes, radio buttons and toggle buttons, or null
 * @param bounds the node bounds on screen
 * @param visibleBounds the part of the node visible on screen once clipped by its ancestors, or null if entirely hidden
 * @param children the visible children, empty if none or if the depth limit was reached
 */
@Serdeable
@Schema(description = "A visible node of the JavaFX scene graph")
public record UiNode(@Schema(description = "Node reference within its window: the slash-separated child indices from the window root, e.g. 0/2/1. The root is the empty string.") String ref,
                     @Schema(description = "JavaFX id of the node", nullable = true) String id,
                     @Schema(description = "Simple class name of the node, e.g. Button, Label, TextField") String type,
                     @Schema(description = "Text displayed by the node (labels, buttons, text fields...)", nullable = true) String text,
                     @Schema(description = "CSS style classes") List<String> styleClasses,
                     @Schema(description = "Whether the node is disabled") boolean disabled,
                     @Schema(description = "Whether the node has the keyboard focus") boolean focused,
                     @Schema(description = "Whether the node is selected, for check boxes, radio buttons and toggle buttons", nullable = true) Boolean selected,
                     @Schema(description = "Node bounds on screen, including any part hidden by a scroll pane or a clip") UiBounds bounds,
                     @Schema(description = "Part of the node actually visible on screen, once clipped by scroll panes and clips of its ancestors. Null when the node is entirely hidden, e.g. scrolled out of view: scroll first, then aim at the center of these bounds", nullable = true) UiBounds visibleBounds,
                     @Schema(description = "Visible children, empty if none or if the depth limit was reached") List<UiNode> children) {
}
