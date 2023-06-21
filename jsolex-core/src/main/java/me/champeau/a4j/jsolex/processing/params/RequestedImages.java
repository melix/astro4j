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
package me.champeau.a4j.jsolex.processing.params;

import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Stores information about the images to generate.
 * @param images the image kinds
 * @param pixelShifts the requested pixel shifts
 */
public record RequestedImages(
        Set<GeneratedImageKind> images,
        List<Integer> pixelShifts
) {
    public static final Set<GeneratedImageKind> FULL_MODE = EnumSet.of(
            GeneratedImageKind.DEBUG,
            GeneratedImageKind.RECONSTRUCTION,
            GeneratedImageKind.RAW_STRETCHED,
            GeneratedImageKind.VIRTUAL_ECLIPSE,
            GeneratedImageKind.COLORIZED,
            GeneratedImageKind.MIXED,
            GeneratedImageKind.NEGATIVE,
            GeneratedImageKind.DOPPLER,
            GeneratedImageKind.GEOMETRY_CORRECTED_STRETCHED,
            GeneratedImageKind.CONTINUUM
    );
    public static final Set<GeneratedImageKind> QUICK_MODE = EnumSet.of(
            GeneratedImageKind.DEBUG,
            GeneratedImageKind.RECONSTRUCTION,
            GeneratedImageKind.RAW_STRETCHED,
            GeneratedImageKind.GEOMETRY_CORRECTED_STRETCHED
    );
    public boolean isEnabled(GeneratedImageKind kind) {
        return images.contains(kind);
    }
}
