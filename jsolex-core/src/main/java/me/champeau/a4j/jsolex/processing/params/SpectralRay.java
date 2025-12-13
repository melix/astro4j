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
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines the most common spectral lines used with Sol'Ex and when possible,
 * defines a color curve to perform automatic coloring of images.
 * See https://en.wikipedia.org/wiki/Fraunhofer_lines for wavelenths
 * @param label the spectral line label
 * @param colorCurve the color curve for automatic coloring
 * @param wavelength the wavelength
 * @param emission true if this is an emission line
 * @param automaticScripts automatic scripts to run
 */
public record SpectralRay(String label, ColorCurve colorCurve, Wavelen wavelength, boolean emission, List<Path> automaticScripts) {
    public static final SpectralRay AUTO = new SpectralRay("Autodetect", null, Wavelen.ofAngstroms(0), false, List.of());
    public static final SpectralRay CALCIUM_K = new SpectralRay("Calcium (K)", null, Wavelen.ofNanos(393.366), false, List.of());
    public static final SpectralRay CALCIUM_H = new SpectralRay("Calcium (H)", null, Wavelen.ofNanos(396.847), false, List.of());
    public static final SpectralRay CA_IRON_G = new SpectralRay("Calcium+Iron+CH (G)", null, Wavelen.ofNanos(430.782), false, List.of());
    public static final SpectralRay H_BETA = new SpectralRay("H-beta", null, Wavelen.ofNanos(486.134), false, List.of());
    public static final SpectralRay MAGNESIUM_b1 = new SpectralRay("Magnesium (b1)", null, Wavelen.ofNanos(518.362), false, List.of());
    public static final SpectralRay IRON_E2 = new SpectralRay("Iron (E2)", null, Wavelen.ofNanos(527.039), false, List.of());
    public static final SpectralRay MERCURY_e = new SpectralRay("Mercury (e)", null, Wavelen.ofNanos(546.073), false, List.of());
    public static final SpectralRay HELIUM_D3 = new SpectralRay("Helium (D3)", null, Wavelen.ofNanos(587.562), true, List.of());
    public static final SpectralRay IRON_FE1 = new SpectralRay("Iron (Fe I)", null, Wavelen.ofNanos(588.38166), false, List.of());
    public static final SpectralRay SODIUM_D2 = new SpectralRay("Sodium (D2)", null, Wavelen.ofNanos(588.995), false, List.of());
    public static final SpectralRay SODIUM_D1 = new SpectralRay("Sodium (D1)", null, Wavelen.ofNanos(589.592), false, List.of());
    public static final SpectralRay H_ALPHA = new SpectralRay("H-alpha", KnownCurves.H_ALPHA, Wavelen.ofNanos(656.281d), false, List.of());
    public static final SpectralRay OTHER = new SpectralRay("Other", null, Wavelen.ofNanos(0), false, List.of());

    private static final List<SpectralRay> PREDEFINED = Stream.concat(Stream.concat(Stream.of(AUTO), Stream.of(
        CALCIUM_K,
        CALCIUM_H,
        CA_IRON_G,
        H_BETA,
        IRON_E2,
        H_ALPHA,
        IRON_FE1,
        SODIUM_D1,
        SODIUM_D2,
        MERCURY_e,
        HELIUM_D3,
        MAGNESIUM_b1
    ).sorted(Comparator.comparingDouble(r -> r.wavelength.angstroms()))), Stream.of(OTHER)).toList();

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

        if (Double.compare(wavelength.angstroms(), that.wavelength.angstroms()) != 0) {
            return false;
        }
        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = label.hashCode();
        temp = Double.doubleToLongBits(wavelength.angstroms());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        String extra = "";
        var angstroms = wavelength.angstroms();
        if (angstroms != 0) {
            angstroms = Math.round(angstroms * 100.0) / 100.0;
            extra = " (" + angstroms + "Å)";
        }
        return label + extra;
    }

    public int[] toRGB() {
        var rgb = toSimpleRGB();

        return improveEsthetics(rgb);
    }

    public int[] toSimpleRGB() {
        double factor;
        double r, g, b;
        var wavelength = this.wavelength().nanos();
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

    private static int[] improveEsthetics(int[] rgb) {
        float[] hsl = rgbToHsl(rgb[0], rgb[1], rgb[2]);
        hsl[1] *= 0.85;
        hsl[2] += (1.0f - hsl[2]) * 0.45;

        return hslToRgb(hsl[0], hsl[1], hsl[2]);
    }

    private static float[] rgbToHsl(int r, int g, int b) {
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, l = (max + min) / 2.0f;

        if (max == min) {
            h = s = 0.0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2.0f - max - min) : d / (max + min);
            if (max == rf) {
                h = (gf - bf) / d + (gf < bf ? 6.0f : 0.0f);
            } else if (max == gf) {
                h = (bf - rf) / d + 2.0f;
            } else {
                h = (rf - gf) / d + 4.0f;
            }
            h /= 6.0f;
        }

        return new float[]{h, s, l};
    }

    private static int[] hslToRgb(float h, float s, float l) {
        float r, g, b;

        if (s == 0.0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
            float p = 2.0f * l - q;
            r = hueToRgb(p, q, h + 1.0f / 3.0f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0f / 3.0f);
        }

        return new int[]{
            (int) (r * 255),
            (int) (g * 255),
            (int) (b * 255)
        };
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0f) t += 1.0f;
        if (t > 1.0f) t -= 1.0f;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    }

    public static List<SpectralRay> predefined() {
        return PREDEFINED;
    }

    /**
     * Creates a copy with a different wavelength.
     *
     * @param wavelength the new wavelength
     * @return the updated spectral ray
     */
    public SpectralRay withWavelength(Wavelen wavelength) {
        return new SpectralRay(label, colorCurve, wavelength, emission, automaticScripts);
    }

    /**
     * Creates a copy with different automatic scripts.
     *
     * @param automaticScripts the automatic scripts
     * @return the updated spectral ray
     */
    public SpectralRay withAutomaticScripts(List<Path> automaticScripts) {
        return new SpectralRay(label, colorCurve, wavelength, emission, automaticScripts);
    }
}
