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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class GONG {
    private static final Logger LOGGER = LoggerFactory.getLogger(GONG.class);
    private static final Pattern LINK = Pattern.compile("a href=\"([^\"]*)\"");
    private static final Pattern FILENAME = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})([A-Za-z])h\\.jpg");
    private static final Lock LOCK = new ReentrantLock();
    private static final Map<String, String> SITE_NAMES = Map.of(
        "A", "Boulder",
        "B", "Big Bear",
        "C", "Cerro Tololo",
        "L", "Learmonth",
        "M", "Mauna Loa",
        "T", "El Teide",
        "U", "Udaipur"
    );

    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendLiteral("UT: ")
        .append(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        .toFormatter(Locale.US);

    /**
     * A single GONG H-alpha candidate image on the server. Note that some
     * sites (notably {@code B} for Big Bear) post continuum images in the
     * H-alpha directory, so callers that strictly need H-alpha may want to
     * let the user pick another site rather than silently accept the
     * closest-in-time result.
     */
    public record GongCandidate(URL url, ZonedDateTime timestamp, String siteCode, String fileName) {
        public String siteDisplayName() {
            return SITE_NAMES.getOrDefault(siteCode, "Site " + siteCode);
        }
    }

    private GONG() {
    }

    public static Optional<URL> fetchGongImage(ZonedDateTime date) {
        return fetchGongImage(date, VersionUtil.getJsolexDir().resolve("gong"));
    }

    public static List<GongCandidate> listGongCandidates(ZonedDateTime date) {
        return listGongCandidates(date, VersionUtil.getJsolexDir().resolve("gong"));
    }

    public static Optional<URL> fetchCandidateImage(GongCandidate candidate) {
        return fetchCandidateImage(candidate, VersionUtil.getJsolexDir().resolve("gong"));
    }

    private static Optional<URL> fetchGongImage(ZonedDateTime date, Path targetFolder) {
        var utcDate = date.withZoneSameInstant(ZoneId.of("UTC"));
        var candidates = listGongCandidates(utcDate, targetFolder);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return fetchCandidateImage(candidates.getFirst(), targetFolder);
    }

    private static List<GongCandidate> listGongCandidates(ZonedDateTime date, Path targetFolder) {
        try {
            LOCK.lock();
            var utcDate = date.withZoneSameInstant(ZoneId.of("UTC"));
            var yearmo = String.format("%04d%02d", utcDate.getYear(), utcDate.getMonthValue());
            var yearmoday = String.format("%04d%02d%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth());
            try {
                var baseUrl = new URI(String.format("https://gong2.nso.edu/ftp/HA/hav/%s/%s/", yearmo, yearmoday)).toURL();
                var availableLinks = new ArrayList<String>();
                try (var reader = new BufferedReader(new InputStreamReader(baseUrl.openStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        var matcher = LINK.matcher(line);
                        while (matcher.find()) {
                            var link = matcher.group(1);
                            if (link.endsWith(".jpg")) {
                                availableLinks.add(link);
                            }
                        }
                    }
                }
                var candidates = new ArrayList<GongCandidate>();
                for (var link : availableLinks) {
                    var parts = FILENAME.matcher(link);
                    if (parts.find()) {
                        var linkDate = ZonedDateTime.of(
                            Integer.parseInt(parts.group(1)),
                            Integer.parseInt(parts.group(2)),
                            Integer.parseInt(parts.group(3)),
                            Integer.parseInt(parts.group(4)),
                            Integer.parseInt(parts.group(5)),
                            Integer.parseInt(parts.group(6)),
                            0,
                            ZoneId.of("UTC"));
                        var siteCode = parts.group(7).toUpperCase(Locale.ROOT);
                        candidates.add(new GongCandidate(new URI(baseUrl + link).toURL(), linkDate, siteCode, link));
                    }
                }
                candidates.sort(Comparator.comparingLong(c -> Math.abs(c.timestamp.toEpochSecond() - utcDate.toEpochSecond())));
                return candidates;
            } catch (URISyntaxException | IOException e) {
                LOGGER.warn("Failed to list GONG images for {}", yearmoday, e);
                return List.of();
            }
        } finally {
            LOCK.unlock();
        }
    }

    private static Optional<URL> fetchCandidateImage(GongCandidate candidate, Path targetFolder) {
        try {
            LOCK.lock();
            var cacheDir = targetFolder.resolve(cacheKey(candidate.timestamp));
            var cachedImagePath = cacheDir.resolve(candidate.fileName);
            if (Files.exists(cachedImagePath)) {
                try {
                    return Optional.of(cachedImagePath.toUri().toURL());
                } catch (Exception e) {
                    LOGGER.warn("Failed to create URL from cached GONG image", e);
                }
            }
            try {
                createDirectoriesIfNeeded(cacheDir);
                LOGGER.debug("Downloading GONG image {} to cache", candidate.fileName);
                try (var inputStream = candidate.url.openStream()) {
                    Files.copy(inputStream, cachedImagePath, StandardCopyOption.REPLACE_EXISTING);
                    return Optional.of(cachedImagePath.toUri().toURL());
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to cache GONG image, returning direct URL", e);
                return Optional.of(candidate.url);
            }
        } finally {
            LOCK.unlock();
        }
    }

    private static String cacheKey(ZonedDateTime utcDate) {
        var yearmo = String.format("%04d%02d", utcDate.getYear(), utcDate.getMonthValue());
        var yearmoday = String.format("%04d%02d%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth());
        var yearmohourmin = String.format("%04d%02d%02d%02d%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth(), utcDate.getHour(), utcDate.getMinute());
        return String.format("HA/hav/%s/%s/%s", yearmo, yearmoday, yearmohourmin);
    }
}
