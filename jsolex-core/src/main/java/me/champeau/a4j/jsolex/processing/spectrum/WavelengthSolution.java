/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

/**
 * Which wavelength falls where on the sensor, around the line a window is centred on.
 * <p>
 * The dispersion of a grating is not constant: it varies with the wavelength through the
 * diffraction angle, so a single number of angstroms per pixel only describes a narrow
 * window. Over the full height of a sensor the error reaches several pixels, which is far
 * more than the width of a line, so the reference spectrum stops lining up with the
 * observation at the edges of the window however good the identification is.
 * <p>
 * The wavelength is therefore taken as the solution of {@code dlambda/dn = dispersion(lambda)}
 * rather than as a proportion. That solution is expanded to the third order around the
 * centre of the window, which over a thousand pixels stays within a thousandth of a pixel
 * of the exact integration while costing two multiplications per sample, cheap enough to
 * sit in the innermost loop of the identification.
 */
final class WavelengthSolution {
    /**
     * Step used to differentiate the dispersion, in angstroms. Wide enough for the
     * differences not to be dominated by rounding, narrow enough for the derivatives to
     * describe the dispersion locally.
     */
    private static final double DERIVATIVE_STEP_ANGSTROMS = 0.5;

    private final double anchorAngstroms;
    private final double scale;
    private final double first;
    private final double second;
    private final double third;

    private WavelengthSolution(double anchorAngstroms, double scale, double first, double second, double third) {
        this.anchorAngstroms = anchorAngstroms;
        this.scale = scale;
        this.first = first;
        this.second = second;
        this.third = third;
    }

    /**
     * The solution around a line.
     *
     * @param instrument the spectroheliograph
     * @param anchorAngstroms the wavelength at the centre of the window
     * @param pixelSizeMicrons the sensor pixel size multiplied by the binning
     * @param dispersionScale the ratio of the real dispersion to its theoretical value,
     * which stretches the whole solution rather than only its linear part
     * @return the solution
     */
    static WavelengthSolution around(SpectroHeliograph instrument,
                                     double anchorAngstroms,
                                     double pixelSizeMicrons,
                                     double dispersionScale) {
        var here = dispersionAt(instrument, anchorAngstroms, pixelSizeMicrons);
        var above = dispersionAt(instrument, anchorAngstroms + DERIVATIVE_STEP_ANGSTROMS, pixelSizeMicrons);
        var below = dispersionAt(instrument, anchorAngstroms - DERIVATIVE_STEP_ANGSTROMS, pixelSizeMicrons);
        var slope = (above - below) / (2 * DERIVATIVE_STEP_ANGSTROMS);
        var curvature = (above - 2 * here + below) / (DERIVATIVE_STEP_ANGSTROMS * DERIVATIVE_STEP_ANGSTROMS);
        // Differentiating dlambda/dn = D(lambda) gives the successive derivatives of the
        // solution: D, then D.D', then D.(D'^2 + D.D''), divided by the usual factorials.
        return new WavelengthSolution(anchorAngstroms, dispersionScale,
                here, here * slope / 2, here * (slope * slope + here * curvature) / 6);
    }

    /**
     * The dispersion at the centre of the window, in angstroms per pixel. It is only the
     * local value: the whole point of this class is that it is not the same everywhere.
     */
    double angstromsPerPixel() {
        return scale * first;
    }

    /**
     * The wavelength at a distance from the centre of the window.
     *
     * @param pixelShift the distance from the centre of the window, in pixels
     * @return the wavelength, in angstroms
     */
    double wavelengthAt(double pixelShift) {
        // Scaling the dispersion is the same as walking the unscaled solution faster.
        var distance = scale * pixelShift;
        return anchorAngstroms + distance * (first + distance * (second + distance * third));
    }

    private static double dispersionAt(SpectroHeliograph instrument, double angstroms, double pixelSizeMicrons) {
        return SpectrumAnalyzer.computeSpectralDispersion(instrument, Wavelen.ofAngstroms(angstroms), pixelSizeMicrons)
                .angstromsPerPixel();
    }
}
