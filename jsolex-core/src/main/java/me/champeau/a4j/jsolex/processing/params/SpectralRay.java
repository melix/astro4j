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

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.color.KnownCurves;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines the most common spectral lines used with Sol'Ex and when possible,
 * defines a color curve to perform automatic coloring of images.
 * See https://en.wikipedia.org/wiki/Fraunhofer_lines for wavelenths
 */
public record SpectralRay(String label, ColorCurve colorCurve, double wavelength) {
    public static final SpectralRay AUTO = new SpectralRay("Autodetect", null, 0);
    public static final SpectralRay CALCIUM_K = new SpectralRay("Calcium (K)", null, 393.366);
    public static final SpectralRay CALCIUM_H = new SpectralRay("Calcium (H)", null, 396.847);
    public static final SpectralRay CA_IRON_G = new SpectralRay("Calcium+Iron+CH (G)", null, 430.782);
    public static final SpectralRay H_BETA = new SpectralRay("H-beta", null, 486.134);
    public static final SpectralRay MAGNESIUM_b1 = new SpectralRay("Magnesium (b1)", null, 518.362);
    public static final SpectralRay IRON_E2 = new SpectralRay("Iron (E2)", null, 527.039);
    public static final SpectralRay MERCURY_e = new SpectralRay("Mercury (e)", null, 546.073);
    public static final SpectralRay HELIUM_D3 = new SpectralRay("Helium (D3)", null, 587.562);
    public static final SpectralRay SODIUM_D2 = new SpectralRay("Sodium (D2)", null, 588.995);
    public static final SpectralRay SODIUM_D1 = new SpectralRay("Sodium (D1)", null, 589.592);
    public static final SpectralRay H_ALPHA = new SpectralRay("H-alpha", KnownCurves.H_ALPHA, 656.281d);
    public static final SpectralRay OTHER = new SpectralRay("Other", null, 0);

    private static final List<SpectralRay> PREDEFINED = Stream.concat(Stream.concat(Stream.of(AUTO), Stream.of(
        CALCIUM_K,
        CALCIUM_H,
        CA_IRON_G,
        H_BETA,
        IRON_E2,
        H_ALPHA,
        SODIUM_D1,
        SODIUM_D2,
        MERCURY_e,
        HELIUM_D3,
        MAGNESIUM_b1
    ).sorted(Comparator.comparingDouble(SpectralRay::wavelength))), Stream.of(OTHER)).toList();

    public Optional<ColorCurve> getColorCurve() {
        return Optional.ofNullable(colorCurve);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SpectralRay that = (SpectralRay) o;

        if (Double.compare(wavelength, that.wavelength) != 0) {
            return false;
        }
        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = label.hashCode();
        temp = Double.doubleToLongBits(wavelength);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return label;
    }

    public int[] toRGB() {
        double factor;
        double r, g, b;

        if ((wavelength >= 380) && (wavelength < 440)) {
            r = -(wavelength - 440) / (440 - 380);
            g = 0.0;
            b = 1.0;
        } else if ((wavelength >= 440) && (wavelength < 490)) {
            r = 0.0;
            g = (wavelength - 440) / (490 - 440);
            b = 1.0;
        } else if ((wavelength >= 490) && (wavelength < 510)) {
            r = 0.0;
            g = 1.0;
            b = -(wavelength - 510) / (510 - 490);
        } else if ((wavelength >= 510) && (wavelength < 580)) {
            r = (wavelength - 510) / (580 - 510);
            g = 1.0;
            b = 0.0;
        } else if ((wavelength >= 580) && (wavelength < 645)) {
            r = 1.0;
            g = -(wavelength - 645) / (645 - 580);
            b = 0.0;
        } else if ((wavelength >= 645) && (wavelength < 781)) {
            r = 1.0;
            g = 0.0;
            b = 0.0;
        } else {
            r = 0.0;
            g = 0.0;
            b = 0.0;
        }

        if ((wavelength >= 380) && (wavelength < 420)) {
            factor = 0.3 + 0.7 * (wavelength - 380) / (420 - 380);
        } else if ((wavelength >= 420) && (wavelength < 701)) {
            factor = 1.0;
        } else if ((wavelength >= 701) && (wavelength < 781)) {
            factor = 0.3 + 0.7 * (780 - wavelength) / (780 - 700);
        } else {
            factor = 0.0;
        }


        int[] rgb = new int[3];

        rgb[0] = r == 0.0 ? 0 : (int) Math.round(255 * Math.pow(r * factor, 0.7));
        rgb[1] = g == 0.0 ? 0 : (int) Math.round(255 * Math.pow(g * factor, 0.7));
        rgb[2] = b == 0.0 ? 0 : (int) Math.round(255 * Math.pow(b * factor, 0.7));

        return rgb;
    }

    public static List<SpectralRay> predefined() {
        return PREDEFINED;
    }
}
