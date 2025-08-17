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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class GONG {
    private static final Logger LOGGER = LoggerFactory.getLogger(GONG.class);
    private static final Pattern LINK = Pattern.compile("a href=\"([^\"]*)\"");
    private static final Pattern FILENAME = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})[a-zA-Z]+.jpg");
    private static final Lock LOCK = new ReentrantLock();

    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendLiteral("UT: ")
        .append(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        .toFormatter(Locale.US);

    private GONG() {
    }

    public static Optional<URL> fetchGongImage(ZonedDateTime date) {
        return fetchGongImage(date, VersionUtil.getJsolexDir().resolve("gong"));
    }
    
    private static Optional<URL> fetchGongImage(ZonedDateTime date, Path targetFolder) {
        try {
            LOCK.lock();
            var utcDate = date.withZoneSameInstant(ZoneId.of("UTC"));
            var cacheKey = generateCacheKey(utcDate);
            
            Optional<Path> cachedImage = findCachedImage(cacheKey, targetFolder);
            if (cachedImage.isPresent()) {
                try {
                    return Optional.of(cachedImage.get().toUri().toURL());
                } catch (Exception e) {
                    LOGGER.warn("Failed to create URL from cached GONG image", e);
                }
            }
            
            return fetchAndCacheGongImage(utcDate, targetFolder);
        } finally {
            LOCK.unlock();
        }
    }

    private static String generateCacheKey(ZonedDateTime utcDate) {
        var yearmo = String.format("%04d%02d", utcDate.getYear(), utcDate.getMonthValue());
        var yearmoday = String.format("%04d%02d%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth());
        var yearmohourmin = String.format("%04d%02d%02d%02d%02d", utcDate.getYear(), utcDate.getMonthValue(), utcDate.getDayOfMonth(), utcDate.getHour(), utcDate.getMinute());
        return String.format("HA/hav/%s/%s/%s", yearmo, yearmoday, yearmohourmin);
    }
    
    private static Optional<Path> findCachedImage(String cacheKey, Path targetFolder) {
        var cacheDir = targetFolder.resolve(cacheKey);
        if (!Files.exists(cacheDir)) {
            return Optional.empty();
        }
        
        try {
            return Files.list(cacheDir)
                .filter(p -> p.getFileName().toString().endsWith(".jpg"))
                .findFirst();
        } catch (IOException e) {
            LOGGER.warn("Failed to list cached GONG images", e);
            return Optional.empty();
        }
    }
    
    private static Optional<URL> fetchAndCacheGongImage(ZonedDateTime date, Path targetFolder) {
        var yearmo = String.format("%04d%02d", date.getYear(), date.getMonthValue());
        var yearmoday = String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        
        try {
            var baseUrl = new URI(String.format("https://gong2.nso.edu/ftp/HA/hav/%s/%s/", yearmo, yearmoday)).toURL();
            var availableLinks = new ArrayList<String>();
            try (var reader = new BufferedReader(new InputStreamReader(baseUrl.openStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // find all target links (a href="...")
                    var matcher = LINK.matcher(line);
                    while (matcher.find()) {
                        var link = matcher.group(1);
                        if (link.endsWith(".jpg")) {
                            availableLinks.add(link);
                        }
                    }
                }
            }
            
            // links match a date pattern: 20240525000002Bh.jpg
            // and we want to find the closest one to the date we're looking for
            var closest = availableLinks.stream()
                .map(link -> {
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
                        return new Object() {
                            final String target = link;
                            final ZonedDateTime date = linkDate;
                        };
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingLong(l -> Math.abs(l.date.toEpochSecond() - date.toEpochSecond())));
                
            if (closest.isPresent()) {
                var imageUrl = new URI(baseUrl + closest.get().target).toURL();
                
                // Recompute cache key based on the actual image's timestamp, not the requested time
                var actualImageDate = closest.get().date;
                var actualCacheKey = generateCacheKey(actualImageDate);
                
                // Cache the image using the actual image's timestamp
                var cacheDir = targetFolder.resolve(actualCacheKey);
                createDirectoriesIfNeeded(cacheDir);
                var cachedImagePath = cacheDir.resolve(closest.get().target);
                
                LOGGER.info("Downloading GONG image for {} to cache", actualImageDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
                try (var inputStream = imageUrl.openStream()) {
                    Files.copy(inputStream, cachedImagePath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("GONG image cached successfully");
                    return Optional.of(cachedImagePath.toUri().toURL());
                } catch (IOException e) {
                    LOGGER.warn("Failed to cache GONG image, returning direct URL", e);
                    return Optional.of(imageUrl);
                }
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.warn("Failed to fetch GONG image for {}", yearmoday, e);
            return Optional.empty();
        }
        return Optional.empty();
    }
}
