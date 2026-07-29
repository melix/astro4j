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
import me.champeau.a4j.jsolex.processing.params.ConditionalFlip;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
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
import java.util.function.Function;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Provides distortion polynomials extracted from a directory of reference (non saturated) scans.
 * When the solar disk is saturated, the spectral line cannot be reliably detected, so the polynomial
 * of the reference scan which is the closest in time is used instead.
 * <p>
 * The directory is listed on every lookup, so that reference scans captured while a batch is running
 * are taken into account as soon as they appear. Only the per file results are cached: the observation
 * date, which requires opening the scan, and the computed polynomial. Both are immutable for a given
 * file, so in batch mode several saturated scans sharing the same reference only trigger a single
 * computation.
 */
public class ReferencePolynomialProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferencePolynomialProvider.class);
    private static final ReferencePolynomialProvider INSTANCE = new ReferencePolynomialProvider();
    private static final Duration MAX_RECOMMENDED_GAP = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, Optional<ZonedDateTime>> dateCache = new ConcurrentHashMap<>();
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
     * @param flipConditions     the active conditional flips; references on the other side of a pivot
     *                           date are excluded because a pier flip changes the flexions
     * @param rootOperation      the root progress operation
     * @param details            the requested line and instrument configuration, used to make sure the
     *                           reference polynomial describes the requested line and not simply the
     *                           darkest one; may be null
     * @return the polynomial to use, or empty if no usable reference could be found
     */
    public Optional<DoubleQuadruplet> findPolynomial(File referenceDirectory,
                                                     ZonedDateTime observationDate,
                                                     File currentFile,
                                                     ColorMode colorMode,
                                                     boolean vflip,
                                                     boolean trustBitDepth,
                                                     List<ConditionalFlip> flipConditions,
                                                     ProgressOperation rootOperation,
                                                     SpectrumAnalyzer.QueryDetails details) {
        if (referenceDirectory == null || !referenceDirectory.isDirectory()) {
            LOGGER.warn(message("saturated.disk.invalid.directory"), referenceDirectory);
            return Optional.empty();
        }
        var entries = listReferences(referenceDirectory, trustBitDepth);
        var currentPath = currentFile != null ? currentFile.getAbsolutePath() : null;
        var selection = selectReference(entries, observationDate, currentPath, flipConditions);
        if (selection.nearest() == null) {
            if (selection.excludedByPivot() > 0) {
                LOGGER.warn(message("saturated.disk.no.reference.same.side"), referenceDirectory);
            } else {
                LOGGER.warn(message("saturated.disk.no.reference"), referenceDirectory);
            }
            return Optional.empty();
        }
        var nearestGap = selection.nearestGap();
        if (nearestGap.compareTo(MAX_RECOMMENDED_GAP) > 0) {
            LOGGER.warn(message("saturated.disk.reference.far"), new File(selection.nearest().path()).getName(), formatGap(nearestGap));
        }
        var reference = selection.nearest();
        var polynomial = polynomialCache.computeIfAbsent(fileKey(new File(reference.path()), details),
                key -> computePolynomial(new File(reference.path()), colorMode, vflip, trustBitDepth, rootOperation, details));
        polynomial.ifPresent(p -> LOGGER.info(message("saturated.disk.using.reference"), new File(reference.path()).getName(), formatGap(nearestGap)));
        return polynomial;
    }

    static Selection selectReference(List<ReferenceEntry> entries,
                                     ZonedDateTime observationDate,
                                     String currentPath,
                                     List<ConditionalFlip> flipConditions) {
        var observationLocalDate = observationDate.toLocalDateTime();
        ReferenceEntry nearest = null;
        Duration nearestGap = null;
        var excludedByPivot = 0;
        for (var entry : entries) {
            if (entry.path().equals(currentPath)) {
                continue;
            }
            var entryLocalDate = entry.date().toLocalDateTime();
            if (flipConditions.stream().anyMatch(condition -> !condition.isSameSide(observationLocalDate, entryLocalDate))) {
                excludedByPivot++;
                continue;
            }
            var gap = Duration.between(observationDate, entry.date()).abs();
            if (nearestGap == null || gap.compareTo(nearestGap) < 0) {
                nearest = entry;
                nearestGap = gap;
            }
        }
        return new Selection(nearest, nearestGap, excludedByPivot);
    }

    private List<ReferenceEntry> listReferences(File referenceDirectory, boolean trustBitDepth) {
        return listReferences(referenceDirectory,
                file -> dateCache.computeIfAbsent(fileKey(file), key -> readDate(file, trustBitDepth)));
    }

    /**
     * Lists the reference scans of a directory. The directory is listed on every call, so that scans
     * appearing while a batch is running are taken into account.
     *
     * @param referenceDirectory the directory to list
     * @param dateResolver       resolves the observation date of a scan, empty if it cannot be read
     * @return the readable reference scans and their dates
     */
    static List<ReferenceEntry> listReferences(File referenceDirectory, Function<File, Optional<ZonedDateTime>> dateResolver) {
        var files = referenceDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".ser"));
        var entries = new ArrayList<ReferenceEntry>();
        if (files == null) {
            return entries;
        }
        for (var file : files) {
            dateResolver.apply(file).ifPresent(date -> entries.add(new ReferenceEntry(file.getAbsolutePath(), date)));
        }
        return entries;
    }

    private static Optional<ZonedDateTime> readDate(File file, boolean trustBitDepth) {
        try (var reader = SerFileReader.of(file, trustBitDepth)) {
            return Optional.of(reader.header().metadata().utcDateTime());
        } catch (Exception e) {
            LOGGER.warn(message("saturated.disk.unreadable.reference"), file.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DoubleQuadruplet> computePolynomial(File referenceFile,
                                                         ColorMode colorMode,
                                                         boolean vflip,
                                                         boolean trustBitDepth,
                                                         ProgressOperation rootOperation,
                                                         SpectrumAnalyzer.QueryDetails details) {
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
            // Reused by every saturated scan, so it must describe the requested line
            result = SpectralLineRealigner.realign(averageImage,
                    geometry.width(),
                    geometry.height(),
                    header.isJSolexTrimmedSer(),
                    result,
                    details);
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

    private static String fileKey(File file) {
        return file.getAbsolutePath() + "@" + file.lastModified();
    }

    /**
     * The realigned polynomial depends on the requested line and on the instrument
     * configuration, so those have to take part in the cache key.
     */
    private static String fileKey(File file, SpectrumAnalyzer.QueryDetails details) {
        if (details == null || details.line() == null || details.instrument() == null) {
            return fileKey(file);
        }
        return fileKey(file) + "#" + details.line().label() + "/" + details.pixelSize() + "/" + details.binning()
               + "/" + details.instrument().label();
    }

    static String formatGap(Duration gap) {
        var minutes = gap.toMinutes();
        if (minutes < 1) {
            return gap.toSeconds() + " s";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        return String.format(Locale.US, "%dh%02dm", minutes / 60, minutes % 60);
    }

    record ReferenceEntry(String path, ZonedDateTime date) {
    }

    record Selection(ReferenceEntry nearest, Duration nearestGap, int excludedByPivot) {
    }
}
