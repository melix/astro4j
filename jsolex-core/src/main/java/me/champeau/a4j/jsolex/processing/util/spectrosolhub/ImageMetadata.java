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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.regression.Ellipse;

public record ImageMetadata(
        // Solar parameters
        Double solarB0,
        Double solarL0,
        Double solarP,
        Integer carringtonRotation,

        // Disk geometry
        Integer centerX,
        Integer centerY,
        Integer solarRadius,

        // Ellipse parameters (cartesian coefficients)
        Double ellipseA,
        Double ellipseB,
        Double ellipseC,
        Double ellipseD,
        Double ellipseE,
        Double ellipseF,

        // Wavelength
        Double wavelengthAngstroms,
        Double wavebandAngstromsPerPixel,

        // Instrument / spectroheliograph
        String spectroId,
        String camera,
        String instrument,
        Float focalLength,
        Float pixelSizeMm,
        Float collimatorFocalLength,
        Float cameraFocalLength,
        Integer grating,
        Integer gratingOrder,
        Float shgAngle,
        Float slitWidth,
        Float slitHeight,
        Integer binning,
        Integer diaphragm,
        String energyRejectionFilter,
        Integer aperture,

        // Observer / observation
        String observer,
        String dateObs,
        Double siteLat,
        Double siteLong,
        String contact,
        String filename,

        // Processing
        String spectralLine,
        String physicalParameter,
        Integer waveUnit,
        Double pixelShift,
        Boolean pAngleCorrected
) {

    public static ImageMetadata fromImage(ImageWrapper image, GeneratedImageKind kind) {
        var params = image.findMetadata(ProcessParams.class).orElse(null);
        if (params == null) {
            return null;
        }
        var obs = params.observationDetails();
        var instrumentConfig = obs.instrument();

        // Solar parameters
        Double b0 = null;
        Double l0 = null;
        Double p = null;
        Integer carRot = null;
        var solarParams = image.findMetadata(SolarParameters.class).orElse(null);
        if (solarParams != null) {
            b0 = Math.toDegrees(solarParams.b0());
            l0 = Math.toDegrees(solarParams.l0());
            p = Math.toDegrees(solarParams.p());
            carRot = solarParams.carringtonRotation();
        }

        // Ellipse / disk geometry
        Integer cx = null;
        Integer cy = null;
        Integer radius = null;
        Double ellA = null, ellB = null, ellC = null, ellD = null, ellE = null, ellF = null;
        var ellipse = image.findMetadata(Ellipse.class).orElse(null);
        if (ellipse != null) {
            var center = ellipse.center();
            cx = (int) Math.round(center.a());
            cy = (int) Math.round(center.b());
            radius = (int) Math.round((ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2);
            var cart = ellipse.getCartesianCoefficients();
            ellA = cart.a();
            ellB = cart.b();
            ellC = cart.c();
            ellD = cart.d();
            ellE = cart.e();
            ellF = cart.f();
        }

        // Wavelength: use image-level SpectralRay if available, otherwise fall back to ProcessParams ray
        Double wavelength = null;
        Double waveband = null;
        var imageSpectralRay = image.findMetadata(SpectralRay.class).orElse(null);
        var effectiveRay = imageSpectralRay != null ? imageSpectralRay : params.spectrumParams().ray();
        var declaredWavelength = effectiveRay.wavelength();
        var wavelengthNanos = declaredWavelength.nanos();
        var pixelSize = obs.pixelSize();
        var binningVal = obs.binning();
        if (wavelengthNanos != 0 && pixelSize != null && pixelSize > 0 && binningVal != null && binningVal > 0) {
            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrumentConfig, Wavelen.ofNanos(wavelengthNanos), pixelSize * binningVal);
            waveband = dispersion.angstromsPerPixel();

            if (imageSpectralRay != null) {
                // Image has its own spectral ray, use its wavelength directly
                wavelength = Math.round(wavelengthNanos * 10 * 1000.0) / 1000.0;
            } else {
                // Adjust wavelength by pixel shift
                double wl = wavelengthNanos;
                var ps = image.findMetadata(PixelShift.class).map(PixelShift::pixelShift);
                if (ps.isPresent()) {
                    wl += ps.get() * dispersion.nanosPerPixel();
                }
                // Convert to Angstroms and round to 3 decimal places
                wavelength = Math.round(wl * 10 * 1000.0) / 1000.0;
            }
        }

        // Instrument details
        Float fl = obs.focalLength() != null ? obs.focalLength().floatValue() : null;
        Float pxSize = pixelSize != null ? (float) (pixelSize / 1000.0) : null;
        int apertureVal = obs.aperture() != null ? obs.aperture() : 0;
        int stopVal = obs.stop() != null ? obs.stop() : apertureVal;

        // Observer
        String dateObsStr = obs.date() != null ? obs.date().toInstant().toString() : null;
        Double siteLat = null;
        Double siteLong = null;
        if (obs.coordinates() != null) {
            siteLat = obs.coordinates().a();
            siteLong = obs.coordinates().b();
        }

        // Pixel shift
        Double pixelShiftVal = image.findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(null);

        // P angle correction is only applied to images that support rotation
        boolean pCorrected = params.geometryParams().isAutocorrectAngleP() && !kind.cannotPerformManualRotation();

        return new ImageMetadata(
                b0, l0, p, carRot,
                cx, cy, radius,
                ellA, ellB, ellC, ellD, ellE, ellF,
                wavelength, waveband,
                instrumentConfig.bass2000Id(),
                obs.camera(),
                instrumentConfig.label(),
                fl != null ? fl : (float) 0,
                pxSize,
                (float) instrumentConfig.collimatorFocalLength(),
                (float) instrumentConfig.focalLength(),
                instrumentConfig.density(),
                instrumentConfig.order(),
                (float) instrumentConfig.totalAngleDegrees(),
                (float) (instrumentConfig.slitWidthMicrons() / 1000.0),
                (float) instrumentConfig.slitHeightMillimeters(),
                binningVal,
                stopVal,
                obs.energyRejectionFilter(),
                apertureVal == 0 ? null : apertureVal,
                obs.observer(),
                dateObsStr,
                siteLat,
                siteLong,
                obs.email(),
                null,
                effectiveRay.label(),
                "Intensity",
                -10,
                pixelShiftVal,
                pCorrected
        );
    }
}
