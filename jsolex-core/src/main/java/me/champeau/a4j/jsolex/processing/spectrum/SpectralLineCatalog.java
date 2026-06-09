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
import java.util.List;

/**
 * Catalog of known "interesting" spectral lines, used to identify which lines other than the one
 * being studied fall within a captured spectral window. The data comes from the {@code interesting-lines.txt}
 * resource (also used by the spectrum browser).
 */
public final class SpectralLineCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectralLineCatalog.class);
    private static final List<CatalogLine> DEFAULTS = loadDefaults();

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
     * A catalog line located within a captured spectral window.
     *
     * @param name       the display name (see {@link CatalogLine#shortName()})
     * @param wavelength the wavelength
     * @param pixelShift the pixel shift at which the line appears, relative to the studied line
     */
    public record LineInWindow(String name, Wavelen wavelength, double pixelShift) {
    }
}
