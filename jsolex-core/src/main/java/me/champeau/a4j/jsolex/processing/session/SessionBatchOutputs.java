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
package me.champeau.a4j.jsolex.processing.session;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.List;
import java.util.Map;

/**
 * The outputs collected from the individual files of a batch run, keyed by the
 * label under which a script stored them. Re-running the {@code [[batch]]} section
 * of a script only needs these: they are restored as the script variables the
 * batch section consumes, so no per-file reconstruction is required.
 *
 * @param imagesByLabel images collected per label
 * @param valuesByLabel scalar values (numbers, strings, booleans) collected per label
 */
public record SessionBatchOutputs(Map<String, List<ImageWrapper>> imagesByLabel,
                                  Map<String, List<Object>> valuesByLabel) {
}
