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
    RECONSTRUCTION(DisplayCategory.RECONSTRUCTION, DirectoryKind.RAW),
    RAW(DisplayCategory.RAW, DirectoryKind.RAW),
    DEBUG(DisplayCategory.DEBUG, DirectoryKind.DEBUG),
    GEOMETRY_CORRECTED(DisplayCategory.PROCESSED, DirectoryKind.PROCESSED),
    GEOMETRY_CORRECTED_PROCESSED(DisplayCategory.PROCESSED, DirectoryKind.PROCESSED),
    VIRTUAL_ECLIPSE(DisplayCategory.MISC, DirectoryKind.PROCESSED),
    DOPPLER_ECLIPSE(DisplayCategory.MISC, DirectoryKind.PROCESSED),
    COLORIZED(DisplayCategory.COLORIZED, DirectoryKind.PROCESSED),
    CONTINUUM(DisplayCategory.PROCESSED, DirectoryKind.PROCESSED),
    MIXED(DisplayCategory.COLORIZED, DirectoryKind.PROCESSED),
    DOPPLER(DisplayCategory.COLORIZED, DirectoryKind.PROCESSED),
    NEGATIVE(DisplayCategory.COLORIZED, DirectoryKind.PROCESSED),
    TECHNICAL_CARD(DisplayCategory.MISC, DirectoryKind.PROCESSED),
    IMAGE_MATH(DisplayCategory.IMAGE_MATH, DirectoryKind.CUSTOM),
    COMPOSITION(DisplayCategory.PROCESSED, DirectoryKind.PROCESSED),
    CROPPED(DisplayCategory.MISC, DirectoryKind.PROCESSED),
    REDSHIFT(DisplayCategory.PHENOMENA, DirectoryKind.PROCESSED),
    ACTIVE_REGIONS(DisplayCategory.COLORIZED, DirectoryKind.PROCESSED),
    ELLERMAN_BOMBS(DisplayCategory.PHENOMENA, DirectoryKind.PROCESSED),
    FLARES(DisplayCategory.PHENOMENA, DirectoryKind.PROCESSED),
    ;

    private final DisplayCategory displayCategory;
    private final DirectoryKind directoryKind;

    GeneratedImageKind(DisplayCategory displayCategory, DirectoryKind directoryKind) {
        this.displayCategory = displayCategory;
        this.directoryKind = directoryKind;
    }

    public DisplayCategory displayCategory() {
        return displayCategory;
    }

    public DirectoryKind directoryKind() {
        return directoryKind;
    }

    public boolean cannotPerformManualRotation() {
        return this == GeneratedImageKind.IMAGE_MATH
                || this == GeneratedImageKind.DEBUG
                || this == GeneratedImageKind.TECHNICAL_CARD
                || this == GeneratedImageKind.CROPPED
                || this == GeneratedImageKind.REDSHIFT
                || this == GeneratedImageKind.ELLERMAN_BOMBS
                || this == GeneratedImageKind.FLARES;
    }
}
