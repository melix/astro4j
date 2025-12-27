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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import javafx.scene.control.CheckBox;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.util.EquipmentDatabaseUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

class FileNameGenerator {
    private static final double TOLERANCE_ANGSTROMS = 0.1;

    private static final Map<SpectralRay, String> ACCEPTED_SPECTRAL_RAYS = Map.of(
            SpectralRay.H_ALPHA.withWavelength(Wavelen.ofAngstroms(6562.762)), "Ha",
            SpectralRay.CALCIUM_K.withWavelength(Wavelen.ofAngstroms(3933.663)), "Cak",
            SpectralRay.CALCIUM_H.withWavelength(Wavelen.ofAngstroms(3968.469)), "Cah"
    );

    private static final Map<SpectralRay, String> RAY_TO_NAME = Map.of(
            SpectralRay.H_ALPHA.withWavelength(Wavelen.ofAngstroms(6561.262)), "Ha2cb",
            SpectralRay.CALCIUM_H.withWavelength(Wavelen.ofAngstroms(3966.968)), "Cah1v",
            SpectralRay.CALCIUM_K.withWavelength(Wavelen.ofAngstroms(3932.163)), "Cak1v"
    );

    private CheckBox focalReducerCheckbox;

    FileNameGenerator() {
    }

    void setFocalReducerCheckbox(CheckBox focalReducerCheckbox) {
        this.focalReducerCheckbox = focalReducerCheckbox;
    }

    Optional<String> generateFileName(ImageWrapper generatedBass2000Image) {
        try {
            var processParams = generatedBass2000Image.findMetadata(ProcessParams.class).orElseThrow();
            var instrumeId = generateInstrumentId(processParams);
            var spectralRay = processParams.spectrumParams().ray();
            var date = processParams.observationDetails().date();

            var lineName = wavelengthAbbreviationFor(spectralRay.wavelength().angstroms());
            var dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            var prefixDateStr = date.format(DateTimeFormatter.ofPattern("_HH_mm_ss"));

            return Optional.of(String.format("%sZ_%s_%s_%s", prefixDateStr, instrumeId, lineName, dateStr));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    Optional<String> generateOffBandFileName(ImageWrapper generatedOffBandImage) {
        try {
            var processParams = generatedOffBandImage.findMetadata(ProcessParams.class).orElseThrow();
            var instrumeId = generateInstrumentId(processParams);
            var spectralRay = processParams.spectrumParams().ray();
            var date = processParams.observationDetails().date();

            var offBandName = offBandAbbreviationFor(spectralRay.wavelength().angstroms());
            var dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            var prefixDateStr = date.format(DateTimeFormatter.ofPattern("_HH_mm_ss"));
            var pixelShift = generatedOffBandImage.findMetadata(PixelShift.class)
                    .map(PixelShift::pixelShift)
                    .orElse(0.0);
            var dp = Math.round(pixelShift);
            return Optional.of(String.format("%sZ_dp%d_%s_%s_%s", prefixDateStr, dp, instrumeId, offBandName, dateStr));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String wavelengthAbbreviationFor(double wavelengthAngstroms) {
        return ACCEPTED_SPECTRAL_RAYS.entrySet()
                .stream()
                .filter(e -> Math.abs(e.getKey().wavelength().angstroms() - wavelengthAngstroms) <= TOLERANCE_ANGSTROMS)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    private static String offBandAbbreviationFor(double wavelengthAngstroms) {
        return RAY_TO_NAME.keySet()
                .stream()
                .filter(s -> Math.abs(s.wavelength().angstroms() - wavelengthAngstroms) <= TOLERANCE_ANGSTROMS)
                .map(RAY_TO_NAME::get)
                .findFirst()
                .orElseThrow();
    }

    private String generateInstrumentId(ProcessParams params) {
        var observationDetails = params.observationDetails();
        var instrument = observationDetails.instrument();

        var spectrographName = instrument.bass2000Id();
        if ("UNKNOWN".equals(spectrographName)) {
            throw new ProcessingException(instrument.label() + " is not supported. Use either a Sol'Ex or the SHG 700");
        }
        var aperture = observationDetails.aperture();
        var telescopeFocalLength = observationDetails.focalLength();
        var focalReducer = focalReducerCheckbox.isSelected() ? "O" : "N";
        var telescope = observationDetails.telescope();
        var camera = observationDetails.camera();

        var telescopeParts = toBrandAndModel(telescope);
        var cameraParts = toBrandAndModel(camera);
        var mountParts = toBrandAndModel(observationDetails.mount());

        return String.format("%s_%s_%s_%s_%s_%s_%s",
                spectrographName,
                aperture,
                telescopeFocalLength,
                focalReducer,
                telescopeParts,
                cameraParts,
                mountParts
        );
    }

    private static String toBrandAndModel(String value) {
        if (value == null || value.isBlank() || value.length() < 3) {
            throw new ProcessingException("You need to set both the brand and model, separated with a space, with 3 characters each minimally");
        }
        var uc = value.toUpperCase(Locale.US);
        var parts = uc.split("\\s+");
        if (parts.length < 2) {
            throw new ProcessingException("You need to set both the brand and model, separated with a space, with 3 characters each minimally");
        }
        var brandInput = parts[0];
        var vendor = EquipmentDatabaseUtils.normalizeVendor(brandInput);
        var brand = vendor.code();
        var model = parts[1].replaceAll("[^A-Z0-9]", "");
        return brand + "-" + model;
    }
}