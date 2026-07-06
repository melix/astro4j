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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Provides distortion polynomials extracted from a directory of reference (non saturated) scans.
 * When the solar disk is saturated, the spectral line cannot be reliably detected, so the polynomial
 * of the reference scan which is the closest in time is used instead.
 * <p>
 * Results are cached both at the directory level (list of reference files and their dates) and at the
 * file level (computed polynomial), so that in batch mode several saturated scans sharing the same
 * reference file only trigger a single computation.
 */
public class ReferencePolynomialProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferencePolynomialProvider.class);
    private static final ReferencePolynomialProvider INSTANCE = new ReferencePolynomialProvider();
    private static final Duration MAX_RECOMMENDED_GAP = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, List<ReferenceEntry>> directoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Optional<DoubleQuadruplet>> polynomialCache = new ConcurrentHashMap<>();

    private ReferencePolynomialProvider() {
    }

    public static ReferencePolynomialProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Finds the distortion polynomial to apply to a saturated scan, using the reference scan which is
     * the closest in time.
     *
     * @param referenceDirectory the directory containing the non saturated reference scans
     * @param observationDate    the observation date of the scan being processed
     * @param currentFile        the file being processed (excluded from the reference candidates)
     * @param colorMode          the color mode used to convert frames
     * @param vflip              whether the spectrum is vertically flipped
     * @param trustBitDepth      whether the SER declared bit depth should be trusted
     * @param rootOperation      the root progress operation
     * @return the polynomial to use, or empty if no usable reference could be found
     */
    public Optional<DoubleQuadruplet> findPolynomial(File referenceDirectory,
                                                     ZonedDateTime observationDate,
                                                     File currentFile,
                                                     ColorMode colorMode,
                                                     boolean vflip,
                                                     boolean trustBitDepth,
                                                     ProgressOperation rootOperation) {
        if (referenceDirectory == null || !referenceDirectory.isDirectory()) {
            LOGGER.warn(message("saturated.disk.invalid.directory"), referenceDirectory);
            return Optional.empty();
        }
        var entries = directoryCache.computeIfAbsent(directoryKey(referenceDirectory), key -> scanDirectory(referenceDirectory, trustBitDepth));
        var currentPath = currentFile != null ? currentFile.getAbsolutePath() : null;
        ReferenceEntry nearest = null;
        Duration nearestGap = null;
        for (var entry : entries) {
            if (entry.path().equals(currentPath)) {
                continue;
            }
            var gap = Duration.between(observationDate, entry.date()).abs();
            if (nearestGap == null || gap.compareTo(nearestGap) < 0) {
                nearest = entry;
                nearestGap = gap;
            }
        }
        if (nearest == null) {
            LOGGER.warn(message("saturated.disk.no.reference"), referenceDirectory);
            return Optional.empty();
        }
        if (nearestGap.compareTo(MAX_RECOMMENDED_GAP) > 0) {
            LOGGER.warn(message("saturated.disk.reference.far"), new File(nearest.path()).getName(), formatGap(nearestGap));
        }
        var reference = nearest;
        var polynomial = polynomialCache.computeIfAbsent(fileKey(new File(reference.path())),
                key -> computePolynomial(new File(reference.path()), colorMode, vflip, trustBitDepth, rootOperation));
        polynomial.ifPresent(p -> LOGGER.info(message("saturated.disk.using.reference"), new File(reference.path()).getName()));
        return polynomial;
    }

    private List<ReferenceEntry> scanDirectory(File referenceDirectory, boolean trustBitDepth) {
        var files = referenceDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".ser"));
        var entries = new ArrayList<ReferenceEntry>();
        if (files == null) {
            return entries;
        }
        for (var file : files) {
            try (var reader = SerFileReader.of(file, trustBitDepth)) {
                var date = reader.header().metadata().utcDateTime();
                entries.add(new ReferenceEntry(file.getAbsolutePath(), date));
            } catch (Exception e) {
                LOGGER.warn(message("saturated.disk.unreadable.reference"), file.getName(), e.getMessage());
            }
        }
        return entries;
    }

    private Optional<DoubleQuadruplet> computePolynomial(File referenceFile,
                                                         ColorMode colorMode,
                                                         boolean vflip,
                                                         boolean trustBitDepth,
                                                         ProgressOperation rootOperation) {
        LOGGER.info(message("saturated.disk.computing.reference"), referenceFile.getName());
        try (var reader = SerFileReader.of(referenceFile, trustBitDepth)) {
            var header = reader.header();
            var geometry = header.geometry();
            var converter = ImageUtils.createImageConverter(colorMode, vflip);
            var creator = new AverageImageCreator(converter, rootOperation, Broadcaster.NO_OP);
            creator.computeAverageImage(reader);
            var averageImage = creator.getAverageImage();
            var analyzer = new SpectrumFrameAnalyzer(geometry.width(), geometry.height(), header.isJSolexTrimmedSer(), null);
            var result = analyzer.analyze(averageImage);
            var polynomial = result.distortionQuadruplet();
            if (polynomial.isEmpty()) {
                LOGGER.warn(message("saturated.disk.reference.no.polynomial"), referenceFile.getName());
            }
            return polynomial;
        } catch (Exception e) {
            LOGGER.warn(message("saturated.disk.reference.failed"), referenceFile.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String directoryKey(File directory) {
        return directory.getAbsolutePath() + "@" + directory.lastModified();
    }

    private static String fileKey(File file) {
        return file.getAbsolutePath() + "@" + file.lastModified();
    }

    private static String formatGap(Duration gap) {
        var minutes = gap.toMinutes();
        if (minutes < 60) {
            return minutes + " min";
        }
        return String.format(Locale.US, "%dh%02dm", minutes / 60, minutes % 60);
    }

    private record ReferenceEntry(String path, ZonedDateTime date) {
    }
}
