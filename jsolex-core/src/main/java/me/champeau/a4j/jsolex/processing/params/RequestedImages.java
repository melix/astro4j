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
 *
 * @param images the image kinds
 * @param pixelShifts the requested pixel shifts
 */
public record RequestedImages(
    Set<GeneratedImageKind> images,
    List<Double> pixelShifts,
    Set<Double> internalPixelShifts,
    ImageMathParams mathImages
) {
    public static final Set<GeneratedImageKind> FULL_MODE = EnumSet.of(
        GeneratedImageKind.RECONSTRUCTION,
        GeneratedImageKind.RAW,
        GeneratedImageKind.VIRTUAL_ECLIPSE,
        GeneratedImageKind.COLORIZED,
        GeneratedImageKind.MIXED,
        GeneratedImageKind.NEGATIVE,
        GeneratedImageKind.DOPPLER,
        GeneratedImageKind.GEOMETRY_CORRECTED,
        GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
        GeneratedImageKind.CONTINUUM,
        GeneratedImageKind.TECHNICAL_CARD,
        GeneratedImageKind.COMPOSITION,
        GeneratedImageKind.REDSHIFT
    );
    public static final Set<GeneratedImageKind> FULL_MODE_WITH_DEBUG = EnumSet.of(
        GeneratedImageKind.DEBUG,
        GeneratedImageKind.RAW,
        GeneratedImageKind.RECONSTRUCTION,
        GeneratedImageKind.VIRTUAL_ECLIPSE,
        GeneratedImageKind.COLORIZED,
        GeneratedImageKind.MIXED,
        GeneratedImageKind.NEGATIVE,
        GeneratedImageKind.DOPPLER,
        GeneratedImageKind.GEOMETRY_CORRECTED,
        GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
        GeneratedImageKind.CONTINUUM,
        GeneratedImageKind.TECHNICAL_CARD,
        GeneratedImageKind.COMPOSITION,
        GeneratedImageKind.REDSHIFT
    );
    public static final Set<GeneratedImageKind> QUICK_MODE = EnumSet.of(
        GeneratedImageKind.RECONSTRUCTION,
        GeneratedImageKind.RAW,
        GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
        GeneratedImageKind.COMPOSITION
    );
    public static final Set<GeneratedImageKind> QUICK_MODE_WITH_DEBUG = EnumSet.of(
        GeneratedImageKind.DEBUG,
        GeneratedImageKind.RAW,
        GeneratedImageKind.RECONSTRUCTION,
        GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
        GeneratedImageKind.COMPOSITION
    );

    public boolean isEnabled(GeneratedImageKind kind) {
        return images.contains(kind);
    }

    public RequestedImages withMathImages(ImageMathParams mathImages) {
        return new RequestedImages(images, pixelShifts, internalPixelShifts, mathImages);
    }

    public RequestedImages withImages(Set<GeneratedImageKind> images) {
        return new RequestedImages(images, pixelShifts, internalPixelShifts, mathImages);
    }

    public RequestedImages withPixelShifts(List<Double> pixelShifts) {
        return new RequestedImages(images, pixelShifts, internalPixelShifts, mathImages);
    }

    public RequestedImages withInternalPixelShifts(Set<Double> internalPixelShifts) {
        return new RequestedImages(images, pixelShifts, internalPixelShifts, mathImages);
    }
}
