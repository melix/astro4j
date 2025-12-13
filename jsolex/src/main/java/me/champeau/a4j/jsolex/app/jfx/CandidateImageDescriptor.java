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
package me.champeau.a4j.jsolex.app.jfx;

import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.nio.file.Path;

/**
 * Describes an image that has been generated as a candidate for the final image,
 * before manual validation.
 * @param kind the type of image that was generated
 * @param title the display title for the image
 * @param path the file path where the image is stored
 * @param pixelShift the pixel shift used to generate this image
 */
public record CandidateImageDescriptor(
    GeneratedImageKind kind,
    String title,
    Path path,
    double pixelShift
) {

}
