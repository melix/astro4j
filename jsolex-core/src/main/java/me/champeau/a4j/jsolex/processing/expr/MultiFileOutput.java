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
package me.champeau.a4j.jsolex.processing.expr;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents multiple file outputs where only one should be displayed to the user,
 * but all files should be saved. This is used when generating animations in multiple
 * formats (e.g., both MP4 and GIF).
 *
 * @param allFiles All generated files
 * @param displayFile The file that should be displayed in the UI (typically GIF if available)
 */
public record MultiFileOutput(List<Path> allFiles, Path displayFile) implements FileOutputResult {
}
