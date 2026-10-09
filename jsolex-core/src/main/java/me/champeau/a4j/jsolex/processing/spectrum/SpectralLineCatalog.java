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
package me.champeau.a4j.jsolex.processing.spectrum;

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catalog of known "interesting" spectral lines, used to identify which lines other than the one
 * being studied fall within a captured spectral window. The data comes from the {@code interesting-lines.txt}
 * resource (also used by the spectrum browser).
 */
public final class SpectralLineCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectralLineCatalog.class);
    private static final List<CatalogLine> DEFAULTS = loadDefaults();
    private static final String TELLURIC_ID = "Atm";
    private static final Pattern TEX_GREEK_LETTER = Pattern.compile("\\$\\\\(\\w+)\\$");

    /**
     * How far a line of the catalog may be from a wavelength to name it.
     */
    public static final double CATALOG_TOLERANCE_ANGSTROMS = 0.5;

    private SpectralLineCatalog() {
    }

    public static List<CatalogLine> defaults() {
        return DEFAULTS;
    }

    /**
     * Finds the catalog lines whose position falls within the given pixel shift range, excluding the studied
     * line itself (at pixel shift 0). Callers that handle some lines through a dedicated process (for example
     * the Helium D3 emission-line extraction) are responsible for filtering those out.
     *
     * @param lambda0    the wavelength of the studied line (pixel shift 0)
     * @param pixelSize  the sensor pixel size, in micrometers
     * @param binning    the binning
     * @param instrument the spectroheliograph
     * @param range      the available pixel shift range
     * @return the lines found within the window, each with its computed pixel shift
     */
    public static List<LineInWindow> findLinesInWindow(Wavelen lambda0,
                                                       double pixelSize,
                                                       int binning,
                                                       SpectroHeliograph instrument,
                                                       PixelShiftRange range) {
        var result = new ArrayList<LineInWindow>();
        for (var line : DEFAULTS) {
            var pixelShift = SpectrumAnalyzer.computePixelShift(pixelSize, binning, lambda0, line.wavelength(), instrument);
            if (pixelShift == 0 || !range.includes(pixelShift)) {
                continue;
            }
            result.add(new LineInWindow(line.shortName(), line.wavelength(), pixelShift));
        }
        return result;
    }

    /**
     * Finds the catalog line closest to a wavelength, when there is one near enough to
     * be that line. Used to name a wavelength which was measured rather than chosen.
     *
     * @param wavelength the wavelength to name
     * @param toleranceAngstroms how far a catalog line may be to still be a match
     * @return the closest line within the tolerance, if any
     */
    public static Optional<CatalogLine> findClosest(Wavelen wavelength, double toleranceAngstroms) {
        CatalogLine closest = null;
        var closestDistance = Double.MAX_VALUE;
        for (var line : DEFAULTS) {
            var distance = Math.abs(line.wavelength().angstroms() - wavelength.angstroms());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = line;
            }
        }
        return closestDistance <= toleranceAngstroms ? Optional.of(closest) : Optional.empty();
    }

    /**
     * The lines of the solar line list, sorted by wavelength. Unlike the {@link #defaults() catalog},
     * which only holds lines of particular interest, it holds thousands of photospheric lines.
     *
     * @return the lines, sorted by wavelength
     */
    public static List<SolarLine> solarLines() {
        return SolarLines.LINES;
    }

    /**
     * Names a wavelength. A line of the catalog is preferred when one lies within
     * {@link #CATALOG_TOLERANCE_ANGSTROMS}, otherwise the strongest line of the solar line list
     * within the given tolerance is used.
     *
     * @param wavelength the wavelength to name
     * @param toleranceAngstroms how far a line of the solar line list may be to still be a match
     * @return the name of the line, if any
     */
    public static Optional<String> nameOf(Wavelen wavelength, double toleranceAngstroms) {
        return findClosest(wavelength, CATALOG_TOLERANCE_ANGSTROMS)
                .map(CatalogLine::shortName)
                .or(() -> findStrongest(wavelength, toleranceAngstroms).map(SolarLine::name));
    }

    /**
     * Finds the line of the solar line list with the largest equivalent width within the tolerance.
     *
     * @param wavelength the wavelength to look around
     * @param toleranceAngstroms how far a line may be to still be a match
     * @return the strongest line within the tolerance, if any
     */
    public static Optional<SolarLine> findStrongest(Wavelen wavelength, double toleranceAngstroms) {
        var angstroms = wavelength.angstroms();
        SolarLine strongest = null;
        for (var line : linesBetween(angstroms - toleranceAngstroms, angstroms + toleranceAngstroms)) {
            if (strongest == null || line.equivalentWidth() > strongest.equivalentWidth()) {
                strongest = line;
            }
        }
        return Optional.ofNullable(strongest);
    }

    /**
     * The lines of the solar line list within a wavelength range.
     *
     * @param minAngstroms the lower bound, inclusive
     * @param maxAngstroms the upper bound, inclusive
     * @return the lines in the range, sorted by wavelength
     */
    public static List<SolarLine> linesBetween(double minAngstroms, double maxAngstroms) {
        var lines = SolarLines.LINES;
        var from = firstIndexAtOrAbove(lines, minAngstroms);
        var to = firstIndexAtOrAbove(lines, Math.nextUp(maxAngstroms));
        return from < to ? lines.subList(from, to) : List.of();
    }

    private static int firstIndexAtOrAbove(List<SolarLine> lines, double angstroms) {
        int low = 0;
        int high = lines.size();
        while (low < high) {
            var middle = (low + high) >>> 1;
            if (lines.get(middle).wavelength().angstroms() < angstroms) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static List<SolarLine> loadSolarLines() {
        var lines = new ArrayList<SolarLine>();
        var resource = SpectralLineCatalog.class.getResourceAsStream("/moore-lines.csv");
        if (resource == null) {
            LOGGER.warn("Unable to find the moore-lines.csv resource");
            return List.of();
        }
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            reader.readLine();
            String cur;
            while ((cur = reader.readLine()) != null) {
                var parts = cur.split(",", -1);
                if (parts.length < 3) {
                    continue;
                }
                var id = parts[2].trim();
                if (id.isEmpty() || TELLURIC_ID.equals(id)) {
                    continue;
                }
                var equivalentWidth = parts[1].isBlank() ? 0 : Double.parseDouble(parts[1]);
                lines.add(new SolarLine(Wavelen.ofAngstroms(Double.parseDouble(parts[0])), displayName(id), equivalentWidth));
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to read the solar line list", e);
            return List.of();
        }
        lines.sort(Comparator.comparingDouble(line -> line.wavelength().angstroms()));
        return List.copyOf(lines);
    }

    /**
     * Turns the TeX notation of greek letters used for the Balmer lines (e.g. {@code H$\alpha$})
     * into the notation of the catalog (e.g. {@code H-alpha}).
     */
    private static String displayName(String id) {
        return TEX_GREEK_LETTER.matcher(id).replaceAll("-$1");
    }

    private static List<CatalogLine> loadDefaults() {
        var lines = new ArrayList<CatalogLine>();
        var resource = SpectralLineCatalog.class.getResourceAsStream("interesting-lines.txt");
        if (resource == null) {
            LOGGER.warn("Unable to find the interesting-lines.txt resource");
            return List.of();
        }
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String cur;
            while ((cur = reader.readLine()) != null) {
                if (cur.startsWith("#") || cur.isBlank()) {
                    continue;
                }
                var parts = cur.split(";");
                if (parts.length == 4) {
                    lines.add(new CatalogLine(
                            Wavelen.ofAngstroms(Double.parseDouble(parts[0])),
                            parts[1],
                            parts[2],
                            Integer.parseInt(parts[3])
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to read the spectral line catalog", e);
            return List.of();
        }
        return List.copyOf(lines);
    }

    /**
     * A spectral line from the catalog.
     *
     * @param wavelength the wavelength
     * @param element    the element or line name (e.g. "Ca", "H-epsilon")
     * @param identifier the line identifier (e.g. "K", "Ba-ε")
     * @param difficulty the identification difficulty (0 = easiest, 3 = hardest)
     */
    public record CatalogLine(Wavelen wavelength, String element, String identifier, int difficulty) {
        /**
         * The full display name, e.g. {@code "H-epsilon (Ba-ε)"} or {@code "Ca (K)"}.
         */
        public String fullName() {
            return element + " (" + identifier + ")";
        }

        /**
         * A concise name for markers and image labels: the bare line name for named lines (e.g. {@code "H-epsilon"}),
         * but keeping the identifier for bare element symbols so several lines of the same element remain distinct
         * (e.g. {@code "Na (D2)"}, {@code "Ca (K)"}).
         */
        public String shortName() {
            return element.length() <= 2 ? fullName() : element;
        }
    }

    /**
     * A line of the solar line list.
     *
     * @param wavelength the wavelength, in air
     * @param name the identification, e.g. {@code "Si I"}, {@code "CN"} or {@code "Na I D2"}
     * @param equivalentWidth the equivalent width in milliangstroms, 0 when unknown
     */
    public record SolarLine(Wavelen wavelength, String name, double equivalentWidth) {
    }

    private static final class SolarLines {
        private static final List<SolarLine> LINES = loadSolarLines();
    }

    /**
     * A catalog line located within a captured spectral window.
     *
     * @param name       the display name (see {@link CatalogLine#shortName()})
     * @param wavelength the wavelength
     * @param pixelShift the pixel shift at which the line appears, relative to the studied line
     */
    public record LineInWindow(String name, Wavelen wavelength, double pixelShift) {
    }
}
