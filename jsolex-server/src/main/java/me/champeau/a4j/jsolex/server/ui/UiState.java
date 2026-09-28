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
 * State of the application.
 *
 * @param busy whether a task (processing, script...) is running
 * @param taskCount the number of running tasks
 * @param currentTask the label of the current task, or an empty string
 * @param progress the progress of the current task, between 0 and 1, or -1 if indeterminate
 * @param outputScale the ratio between physical and logical screen pixels
 */
@Serdeable
@Schema(description = "State of the application")
public record UiState(@Schema(description = "Whether a task (processing, script...) is running") boolean busy,
                      @Schema(description = "Number of running tasks") int taskCount,
                      @Schema(description = "Label of the current task, or an empty string") String currentTask,
                      @Schema(description = "Progress of the current task between 0 and 1, or -1 if indeterminate") double progress,
                      @Schema(description = "Ratio between physical and logical screen pixels. Multiply bounds by this value to obtain physical pixels, e.g. for xdotool.") double outputScale) {
}
