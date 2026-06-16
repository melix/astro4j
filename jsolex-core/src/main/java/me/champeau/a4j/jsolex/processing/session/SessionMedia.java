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

import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.nio.file.Path;

/**
 * A generated media file (animation or video) to be exported to, or restored
 * from, a session file.
 *
 * @param kind        the kind of generated image, used to place it in the right viewer category
 * @param title       the display title
 * @param description an optional description (may be {@code null})
 * @param file        the media file on disk
 * @param type        the media type
 */
public record SessionMedia(GeneratedImageKind kind,
                           String title,
                           String description,
                           Path file,
                           Type type) {
    public enum Type {
        VIDEO,
        GIF
    }
}
