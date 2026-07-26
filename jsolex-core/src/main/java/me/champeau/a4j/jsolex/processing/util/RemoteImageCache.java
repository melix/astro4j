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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

/**
 * On-disk cache for images fetched from observatory archives which publish their
 * files as browsable directory listings, such as GONG or SDO.
 */
public final class RemoteImageCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteImageCache.class);
    private static final Pattern LINK = Pattern.compile("a href=\"([^\"]*)\"");
    private static final Lock LOCK = new ReentrantLock();

    private RemoteImageCache() {
    }

    /**
     * Returns the {@code .jpg} links found in the directory listing at the given URL.
     * A listing which can no longer change is cached on disk, so that it is only
     * fetched from the server once.
     *
     * @param listingUrl the directory listing to read
     * @param listingCacheFile where the listing is cached
     * @param immutable whether the listing can still change on the server
     * @return the list of jpg file names
     */
    public static List<String> listJpgLinks(URL listingUrl, Path listingCacheFile, boolean immutable) throws IOException {
        LOCK.lock();
        try {
            if (immutable && Files.exists(listingCacheFile)) {
                return Files.readAllLines(listingCacheFile);
            }
            var links = new ArrayList<String>();
            try (var reader = new BufferedReader(new InputStreamReader(listingUrl.openStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var matcher = LINK.matcher(line);
                    while (matcher.find()) {
                        var link = matcher.group(1);
                        if (link.endsWith(".jpg")) {
                            links.add(link);
                        }
                    }
                }
            }
            if (immutable && !links.isEmpty()) {
                createDirectoriesIfNeeded(listingCacheFile.getParent());
                Files.write(listingCacheFile, links);
            }
            return links;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Downloads an image unless it is already present in the cache, and returns the
     * URL it can be read from. If the image cannot be cached, the remote URL is
     * returned so that callers can still use it.
     *
     * @param imageUrl the remote image
     * @param cachedImagePath where the image is cached
     * @return the URL to read the image from
     */
    public static Optional<URL> fetchImage(URL imageUrl, Path cachedImagePath) {
        LOCK.lock();
        try {
            if (Files.exists(cachedImagePath)) {
                try {
                    return Optional.of(cachedImagePath.toUri().toURL());
                } catch (Exception e) {
                    LOGGER.warn("Failed to create URL from cached image {}", cachedImagePath, e);
                }
            }
            try {
                createDirectoriesIfNeeded(cachedImagePath.getParent());
                LOGGER.debug("Downloading {} to cache", imageUrl);
                try (var inputStream = imageUrl.openStream()) {
                    Files.copy(inputStream, cachedImagePath, StandardCopyOption.REPLACE_EXISTING);
                    return Optional.of(cachedImagePath.toUri().toURL());
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to cache image, returning direct URL", e);
                return Optional.of(imageUrl);
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Returns true when the given date is on a day which is fully in the past, in
     * which case no new image can be published for it any more.
     */
    public static boolean isPastUtcDay(ZonedDateTime utcDate) {
        return utcDate.toLocalDate().isBefore(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDate());
    }
}
