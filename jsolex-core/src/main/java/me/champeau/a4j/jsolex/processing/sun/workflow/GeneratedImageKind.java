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
package me.champeau.a4j.jsolex.processing.sun.workflow;

/**
 * The different kinds of images that this software
 * supports.
 */
public enum GeneratedImageKind {
    RECONSTRUCTION(DisplayCategory.RECONSTRUCTION),
    RAW(DisplayCategory.RAW),
    DEBUG(DisplayCategory.DEBUG),
    GEOMETRY_CORRECTED(DisplayCategory.PROCESSED),
    GEOMETRY_CORRECTED_PROCESSED(DisplayCategory.PROCESSED),
    VIRTUAL_ECLIPSE(DisplayCategory.MISC),
    COLORIZED(DisplayCategory.COLORIZED),
    CONTINUUM(DisplayCategory.PROCESSED),
    MIXED(DisplayCategory.COLORIZED),
    DOPPLER(DisplayCategory.COLORIZED),
    NEGATIVE(DisplayCategory.COLORIZED),
    TECHNICAL_CARD(DisplayCategory.MISC),
    IMAGE_MATH(DisplayCategory.IMAGE_MATH),
    COMPOSITION(DisplayCategory.PROCESSED);

    private final DisplayCategory displayCategory;

    GeneratedImageKind(DisplayCategory displayCategory) {
        this.displayCategory = displayCategory;
    }

    public DisplayCategory displayCategory() {
        return displayCategory;
    }

    public boolean shouldRotateImage() {
        return this == GeneratedImageKind.IMAGE_MATH
               || this == GeneratedImageKind.DEBUG
               || this == GeneratedImageKind.TECHNICAL_CARD;
    }
}
