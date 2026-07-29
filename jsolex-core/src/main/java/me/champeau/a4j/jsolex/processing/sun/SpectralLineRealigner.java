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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineLocator;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Makes sure the distortion polynomial describes the line the user asked for.
 * <p>
 * Line detection tracks the darkest line of the spectrum, which is not always
 * the requested one: a neighbour can be deeper. This happens in particular on
 * narrow crop windows selected around a faint reference line. When an explicit
 * wavelength is requested, the requested wavelength is located in the window by
 * matching the observed profile against the reference spectrum, then the
 * polynomial is refitted on that line.
 */
public class SpectralLineRealigner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectralLineRealigner.class);

    /**
     * Offsets below this are within the noise of the polynomial fit itself, so
     * refitting would only add jitter.
     */
    private static final double MIN_SIGNIFICANT_OFFSET_PIXELS = 1;

    private SpectralLineRealigner() {
    }

    /**
     * Refits the polynomial on the requested line when the initial fit locked on a different one.
     *
     * @param averageImage the average image of the scan
     * @param width the image width
     * @param height the image height
     * @param trimmedSer whether the source is a JSol'Ex trimmed SER file
     * @param analysis the initial analysis
     * @param details the requested line and the instrument configuration, may be null
     * @return the realigned analysis, or the initial one when no confident match was found
     */
    public static SpectrumFrameAnalyzer.Result realign(float[][] averageImage,
                                                       int width,
                                                       int height,
                                                       boolean trimmedSer,
                                                       SpectrumFrameAnalyzer.Result analysis,
                                                       SpectrumAnalyzer.QueryDetails details) {
        if (!isUsable(details)) {
            return analysis;
        }
        var maybePolynomial = analysis.distortionPolynomial();
        if (maybePolynomial.isEmpty()) {
            return analysis;
        }
        var polynomial = maybePolynomial.get();
        var ray = details.line();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(details.instrument(),
                ray.wavelength(),
                details.pixelSize() * details.binning());
        var profile = SpectrumAnalyzer.computeDataPoints(details,
                polynomial,
                analysis.leftBorder().orElse(0),
                analysis.rightBorder().orElse(width),
                width,
                height,
                averageImage);
        var located = SpectralLineLocator.locate(profile, ray.wavelength(), dispersion, height / 2d);
        if (located.isEmpty()) {
            return analysis;
        }
        var match = located.get();
        if (Math.abs(match.pixelOffset()) < MIN_SIGNIFICANT_OFFSET_PIXELS) {
            return analysis;
        }
        var mid = (analysis.leftBorder().orElse(0) + analysis.rightBorder().orElse(width)) / 2;
        var target = polynomial.applyAsDouble(mid) + match.pixelOffset();
        if (target < 0 || target >= height) {
            return analysis;
        }
        var realigned = new SpectrumFrameAnalyzer(width, height, trimmedSer, null)
                .analyzeAround(averageImage, target);
        if (realigned.distortionPolynomial().isEmpty()) {
            return analysis;
        }
        LOGGER.info(String.format(Locale.US, message("realigned.on.requested.line"),
                ray.label(), match.pixelOffset(), match.score()));
        return realigned;
    }

    private static boolean isUsable(SpectrumAnalyzer.QueryDetails details) {
        return details != null
               && details.line() != null
               && details.line().wavelength().angstroms() > 0
               && details.pixelSize() > 0
               && details.binning() > 0
               && details.instrument() != null;
    }
}
