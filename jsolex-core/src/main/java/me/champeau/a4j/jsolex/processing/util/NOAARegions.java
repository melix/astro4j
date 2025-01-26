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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class NOAARegions {

    private static final Logger LOGGER = LoggerFactory.getLogger(NOAARegions.class);
    private static final String WAREHOUSE_BASE_URL = "ftp://ftp.swpc.noaa.gov/pub/warehouse/";
    private static final String SRS_BASE_URL = "ftp://ftp.swpc.noaa.gov/pub/forecasts/SRS";
    private static final Lock LOCK = new ReentrantLock();

    public static List<NOAAActiveRegion> findActiveRegions(ZonedDateTime date) {
        return findActiveRegions(date, VersionUtil.getJsolexDir().resolve("srs"));
    }

    private static List<NOAAActiveRegion> findActiveRegions(ZonedDateTime date, Path targetFolder) {
        try {
            LOCK.lock();
            Optional<String> srs = fetchSRS(date, targetFolder);
            return srs.map(NOAARegions::parseRegions).stream()
                .flatMap(regions -> regions.stream().map(r -> r.adjustForTime(date)))
                .toList();
        } finally {
            LOCK.unlock();
        }
    }

    private static List<NOAAActiveRegion> parseRegions(String data) {
        List<String> lines = Arrays.stream(data.split("\n"))
            .map(line -> line.replaceAll("\\s+", " "))
            .toList();
        boolean inTable = false;
        List<NOAAActiveRegion> regions = new ArrayList<>();
        for (String line : lines) {
            if (line.equals("Nmbr Location Lo Area Z LL NN Mag Type")) {
                inTable = true;
                continue;
            } else if (line.startsWith("IA. H-alpha Plages without Spots")) {
                break;
            }
            if (inTable) {
                String[] parts = line.split(" ");
                if (parts.length >= 8) {
                    String id = parts[0];
                    String location = parts[1];
                    char latDir = location.charAt(0);
                    char lonDir = location.charAt(3);
                    int latDeg = Integer.parseInt(location.substring(1, 3));
                    int lonDeg = Integer.parseInt(location.substring(4));
                    if ('S' == latDir) {
                        latDeg = -latDeg;
                    }
                    if ('E' == lonDir) {
                        lonDeg = -lonDeg;
                    }
                    regions.add(new NOAAActiveRegion(id, latDeg, lonDeg));
                }
            }
        }
        return Collections.unmodifiableList(regions);
    }

    public static void downloadArchive(int year, Path targetFolder) throws URISyntaxException, IOException {
        var fileName = year + "_SRS.tar.gz";
        var url = WAREHOUSE_BASE_URL + year + "/" + fileName;
        var conn = new URI(url).toURL().openConnection();
        conn.connect();
        try (var in = conn.getInputStream()) {
            var target = targetFolder.resolve(year + "_SRS.tar.gz");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        untar(targetFolder.resolve(year + "_SRS.tar.gz"), targetFolder);
    }

    private static Optional<String> fetchSRS(ZonedDateTime date, Path targetFolder) {
        // convert date to UTC
        var utcDate = date.withZoneSameInstant(ZoneId.of("UTC"));
        var year = utcDate.getYear();
        var month = utcDate.getMonthValue();
        var day = utcDate.getDayOfMonth();
        var srsDir = targetFolder.resolve(year + "_SRS");
        var srsFile = srsDir.resolve(year + String.format("%02d", month) + String.format("%02d", day) + "SRS.txt");
        if (!Files.exists(srsFile)) {
            try {
                Files.createDirectories(srsDir);
                downloadSRSData(targetFolder, utcDate, year, month, day);
            } catch (Exception ex) {
                LOGGER.warn(String.format(message("download.srs.failed"), year, month, day), ex);
                return Optional.empty();
            }
        }
        try {
            return Optional.of(Files.readString(srsFile));
        } catch (IOException e) {
            LOGGER.warn(String.format(message("read.srs.failed"), year, month, day), e);
        }
        return Optional.empty();
    }

    private static void downloadSRSData(Path targetFolder, ZonedDateTime utcDate, int year, int month, int day) throws IOException, URISyntaxException {
        LOGGER.info(String.format(message("download.srs.data"), year, month, day));
        // There are 3 options:
        // 1. the date is < 75 days, in which case we can get the file from the recent archive
        // 2. the date is > 75 days, in which case we need to download the archive
        //   a. if it's the same year, files are in the same year folder
        //   b. if it's a different year, we need to download the archive
        var now = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
        var days = Duration.between(utcDate, now).toDays();
        if (days < 75) {
            downloadSingleRecentFile(year, month, day, targetFolder);
        } else {
            if (year == now.getYear()) {
                downloadSingleSameYearFile(year, month, day, targetFolder);
            } else {
                downloadArchive(year, targetFolder);
            }
        }
        LOGGER.info(message("download.srs.data.done"));
    }

    private static void untar(Path tarGzFile, Path targetFolder) throws IOException {
        try (var fileInputStream = Files.newInputStream(tarGzFile);
             var gzipInputStream = new GZIPInputStream(fileInputStream);
             var tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream)) {

            TarArchiveEntry entry;
            while ((entry = tarArchiveInputStream.getNextEntry()) != null) {
                var entryPath = targetFolder.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(tarArchiveInputStream, entryPath);
                }
            }
        }
    }

    private static void downloadSingleRecentFile(int year, int month, int day, Path targetFolder) throws IOException, URISyntaxException {
        var url = String.format(SRS_BASE_URL + "/%02d%02dSRS.txt", month, day);
        var conn = new URI(url).toURL().openConnection();
        conn.connect();
        var yearFolder = targetFolder.resolve(String.valueOf(year) + "_SRS");
        Files.createDirectories(yearFolder);
        try (var in = conn.getInputStream()) {
            var target = yearFolder.resolve(String.format("%02d%02d%02dSRS.txt", year, month, day));
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void downloadSingleSameYearFile(int year, int month, int day, Path targetFolder) throws IOException, URISyntaxException {
        var url = String.format(WAREHOUSE_BASE_URL + "/%02d/SRS/%02d%02d%02dSRS.txt", year, year, month, day);
        var conn = new URI(url).toURL().openConnection();
        conn.connect();
        var yearFolder = targetFolder.resolve(year + "_SRS");
        Files.createDirectories(yearFolder);
        try (var in = conn.getInputStream()) {
            var target = yearFolder.resolve(String.format("%02d%02d%02dSRS.txt", year, month, day));
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
