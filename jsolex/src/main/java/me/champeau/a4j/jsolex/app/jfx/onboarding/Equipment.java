/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.onboarding;

import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.params.SetupsIO;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;

import java.util.ArrayList;

final class Equipment {
    private Equipment() {

    }

    static ProcessParams withInstrument(ProcessParams params, SpectroHeliograph instrument) {
        return params
                .withObservationDetails(params.observationDetails().withInstrument(instrument))
                .withGeometryParams(params.geometryParams().withSpectrumVFlip(instrument.spectrumVFlip()));
    }

    static double angstromsPerPixel(SpectroHeliograph instrument, double pixelSize, int binning) {
        return SpectrumAnalyzer.computeSpectralDispersion(instrument, SpectralRay.H_ALPHA.wavelength(), pixelSize * binning).angstromsPerPixel();
    }

    static void saveInstrument(SpectroHeliograph instrument) {
        var instruments = new ArrayList<>(SpectroHeliographsIO.loadDefaults());
        instruments.removeIf(existing -> existing.label().equals(instrument.label()));
        instruments.add(instrument);
        SpectroHeliographsIO.saveDefaults(instruments);
    }

    static ObservationDetails withSunscanCamera(ObservationDetails details) {
        var setup = SetupsIO.findSunscan();
        if (setup.isEmpty()) {
            return details;
        }
        var sunscan = setup.get();
        var result = details.withCamera(sunscan.camera()).withBinning(1);
        if (sunscan.pixelSize() != null) {
            result = result.withPixelSize(sunscan.pixelSize());
        }
        if (details.telescope() == null || details.telescope().isBlank()) {
            result = result.withTelescope(sunscan.telescope()).withFocalLength(sunscan.focalLength()).withAperture(sunscan.aperture());
        }
        return result;
    }

    static Setup toSetup(String label, ObservationDetails details) {
        var coordinates = details.coordinates();
        return new Setup(label, details.telescope(), details.focalLength(), details.aperture(), details.stop(),
                details.energyRejectionFilter(), details.camera(), details.pixelSize(),
                coordinates == null ? null : coordinates.a(), coordinates == null ? null : coordinates.b(),
                details.mount(), details.forceCamera(), details.showCoordinatesInDetails(), details.altAzMode());
    }

    static void saveSetup(Setup setup) {
        var setups = new ArrayList<>(SetupsIO.loadDefaults());
        setups.removeIf(existing -> existing.label().equals(setup.label()));
        setups.add(setup);
        SetupsIO.saveDefaults(setups);
    }
}
