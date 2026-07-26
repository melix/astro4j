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
package me.champeau.a4j.jsolex.processing.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Access to the browse images published by the Solar Dynamics Observatory, which
 * are useful to compare an observation with a space based one taken at the same
 * time.
 * <p>
 * The images are published as one directory per day, each containing the frames of
 * every channel at several resolutions, named {@code YYYYMMDD_HHMMSS_<res>_<channel>.jpg}
 * with timestamps in UTC.
 */
public class SDO {
    private static final Logger LOGGER = LoggerFactory.getLogger(SDO.class);
    private static final Pattern FILENAME = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})_(\\d+)_(\\w+)\\.jpg");
    private static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * The channels which are sampled on the HMI detector. Everything else, including
     * the composites which blend a magnetogram with an AIA channel, is published on
     * the AIA grid.
     */
    private static final Set<String> HMI_CHANNELS = Set.of("HMIB", "HMIBC", "HMII", "HMIIC", "HMIIF", "HMID");

    /**
     * Plate scale of the AIA detector, in arcseconds per pixel, for a full resolution
     * 4096 pixels wide frame.
     */
    private static final double AIA_ARCSEC_PER_PIXEL = 0.600;

    /**
     * Plate scale of the HMI detector, in arcseconds per pixel, for a full resolution
     * 4096 pixels wide frame.
     */
    private static final double HMI_ARCSEC_PER_PIXEL = 0.504;

    private static final int FULL_RESOLUTION = 4096;

    /**
     * A single SDO browse image available on the server.
     *
     * @param url where the image can be downloaded from
     * @param timestamp the UTC date and time the frame was taken
     * @param channel the channel code, for example {@code 0094} or {@code HMIIC}
     * @param resolution the width and height of the frame, in pixels
     * @param fileName the name of the file on the server
     */
    public record SdoCandidate(URL url, ZonedDateTime timestamp, String channel, int resolution, String fileName) {
        /**
         * Computes the radius of the solar disk in this frame, in pixels. The browse
         * images preserve the plate scale of the detector, so the disk grows and
         * shrinks over the year as the Earth-Sun distance varies.
         *
         * @return the radius of the solar disk, in pixels
         */
        public double solarDiskRadius() {
            var solarParams = SolarParametersUtils.computeSolarParams(timestamp.withZoneSameInstant(UTC).toLocalDateTime());
            var radiusArcsec = Math.toDegrees(solarParams.apparentSize()) * 1800;
            var arcsecPerPixel = arcsecPerPixel(channel) * FULL_RESOLUTION / resolution;
            return radiusArcsec / arcsecPerPixel;
        }
    }

    private SDO() {
    }

    private static double arcsecPerPixel(String channel) {
        return HMI_CHANNELS.contains(channel.toUpperCase(Locale.ROOT)) ? HMI_ARCSEC_PER_PIXEL : AIA_ARCSEC_PER_PIXEL;
    }

    /**
     * Returns the browse image whose timestamp is the closest to the requested date.
     *
     * @param date the requested date
     * @param channel the channel code
     * @param resolution the width of the frame, in pixels
     * @return the closest frame, if any is available
     */
    public static Optional<SdoCandidate> findClosest(ZonedDateTime date, String channel, int resolution) {
        return findClosest(date, channel, resolution, defaultCacheFolder());
    }

    public static Optional<SdoCandidate> findClosest(ZonedDateTime date, String channel, int resolution, Path targetFolder) {
        var utcDate = date.withZoneSameInstant(UTC);
        // the cadence of a channel can exceed the distance to the day boundary, so the
        // neighbouring days are searched as well
        var candidates = new ArrayList<SdoCandidate>();
        candidates.addAll(listCandidates(utcDate.minusDays(1), channel, resolution, targetFolder));
        candidates.addAll(listCandidates(utcDate, channel, resolution, targetFolder));
        candidates.addAll(listCandidates(utcDate.plusDays(1), channel, resolution, targetFolder));
        return candidates.stream()
            .min(Comparator.comparingLong(c -> Math.abs(c.timestamp.toEpochSecond() - utcDate.toEpochSecond())));
    }

    /**
     * Lists the browse images available for the day of the requested date, for a
     * single channel and resolution.
     *
     * @param date the requested date
     * @param channel the channel code
     * @param resolution the width of the frame, in pixels
     * @param targetFolder the cache folder
     * @return the frames available that day, sorted by increasing distance to the requested date
     */
    public static List<SdoCandidate> listCandidates(ZonedDateTime date, String channel, int resolution, Path targetFolder) {
        var utcDate = date.withZoneSameInstant(UTC);
        var day = String.format("%04d/%02d/%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth());
        try {
            var baseUrl = new URI("https://sdo.gsfc.nasa.gov/assets/img/browse/" + day + "/").toURL();
            var listingCacheFile = targetFolder.resolve(day).resolve("listing.txt");
            var availableLinks = RemoteImageCache.listJpgLinks(baseUrl, listingCacheFile, RemoteImageCache.isPastUtcDay(utcDate));
            var candidates = new ArrayList<SdoCandidate>();
            for (var link : availableLinks) {
                var parts = FILENAME.matcher(link);
                if (parts.find()) {
                    if (Integer.parseInt(parts.group(7)) != resolution || !parts.group(8).equalsIgnoreCase(channel)) {
                        continue;
                    }
                    var linkDate = ZonedDateTime.of(
                        Integer.parseInt(parts.group(1)),
                        Integer.parseInt(parts.group(2)),
                        Integer.parseInt(parts.group(3)),
                        Integer.parseInt(parts.group(4)),
                        Integer.parseInt(parts.group(5)),
                        Integer.parseInt(parts.group(6)),
                        0,
                        UTC);
                    candidates.add(new SdoCandidate(new URI(baseUrl + link).toURL(), linkDate, parts.group(8), resolution, link));
                }
            }
            candidates.sort(Comparator.comparingLong(c -> Math.abs(c.timestamp.toEpochSecond() - utcDate.toEpochSecond())));
            return candidates;
        } catch (FileNotFoundException e) {
            // the day directory does not exist yet, which is expected for the current
            // day before any image has been published
            LOGGER.debug("No SDO images available for {}", day);
            return List.of();
        } catch (URISyntaxException | IOException e) {
            LOGGER.warn("Failed to list SDO images for {}", day, e);
            return List.of();
        }
    }

    /**
     * Downloads a browse image, or returns it from the cache if it was already fetched.
     *
     * @param candidate the image to download
     * @return the URL the image can be read from
     */
    public static Optional<URL> fetchCandidateImage(SdoCandidate candidate) {
        return fetchCandidateImage(candidate, defaultCacheFolder());
    }

    public static Optional<URL> fetchCandidateImage(SdoCandidate candidate, Path targetFolder) {
        var utcDate = candidate.timestamp.withZoneSameInstant(UTC);
        var cachedImagePath = targetFolder
            .resolve(String.format("%04d/%02d/%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth()))
            .resolve(candidate.fileName);
        return RemoteImageCache.fetchImage(candidate.url, cachedImagePath);
    }

    private static Path defaultCacheFolder() {
        return VersionUtil.getJsolexDir().resolve("sdo");
    }
}
