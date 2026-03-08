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
package me.champeau.a4j.jsolex.processing.util.spectrosolhub;

public record CreateSessionRequest(
        String title,
        String observationDate,
        String spectralLine,
        Double customWavelengthAngstroms,
        SpectroheliographInfo spectroheliograph,
        TelescopeInfo telescope,
        CameraInfo camera,
        MountInfo mount,
        String energyRejectionFilter,
        Double latitude,
        Double longitude,
        String notes,
        String softwareName,
        String softwareVersion
) {

    public record SpectroheliographInfo(
            String name,
            Integer gratingDensity,
            Integer gratingOrder,
            Double slitWidthMicrons,
            Double slitHeightMillimeters,
            Double cameraFocalLength,
            Double collimatorFocalLength,
            Double totalAngleDegrees
    ) {
    }

    public record TelescopeInfo(
            String brand,
            String model,
            Integer focalLengthMm,
            Integer apertureMm
    ) {
    }

    public record CameraInfo(
            String brand,
            String model,
            Double pixelSizeUm,
            Integer binning
    ) {
    }

    public record MountInfo(
            String brand,
            String model,
            String type
    ) {
    }
}
