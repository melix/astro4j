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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import me.champeau.a4j.jsolex.app.Configuration;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class Bass2000FtpListingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bass2000FtpListingService.class);
    private static final String DUMMY_FTP = "ftp://dummy";

    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "^_\\d{2}_\\d{2}_\\d{2}Z_(?:dp-?\\d+_)?.+?_(Ha|Cak|Cah|Ha2cb|Cah1v|Cak1v)_(\\d{8}_\\d{6})(?:\\.fits?)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final DateTimeFormatter CAPTURE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private Bass2000FtpListingService() {
    }

    /**
     * A remote submission discovered by scanning the BASS2000 FTP server.
     *
     * @param filename the file name as found on the server
     * @param captureTime the capture time parsed from the filename (UTC)
     * @param lineCode the spectral line code (e.g. Ha, Cak, Cah)
     */
    record RemoteSubmission(String filename, LocalDateTime captureTime, String lineCode) {
        Duration timeDeltaTo(LocalDateTime other) {
            return Duration.between(captureTime, other).abs();
        }
    }

    /**
     * Lists submissions on the BASS2000 FTP server for the given observation date and line code.
     *
     * @param observationDate the local date of the observation
     * @param lineCode the line code to match (Ha, Cak, Cah)
     * @return a list of remote submissions, possibly empty; never null
     */
    static List<RemoteSubmission> listSubmissions(LocalDate observationDate, String lineCode) {
        var config = Configuration.getInstance();
        var ftpUrl = config.getBass2000FtpUrl();
        if (DUMMY_FTP.equals(ftpUrl)) {
            return List.of();
        }
        URI uri;
        try {
            uri = new URI(ftpUrl);
        } catch (URISyntaxException e) {
            LOGGER.debug("Invalid FTP URL when listing BASS2000 submissions: {}", ftpUrl, e);
            return List.of();
        }
        var ftpHost = uri.getHost();
        var ftpPort = uri.getPort() == -1 ? 21 : uri.getPort();
        var ftpPath = uri.getPath();

        var ftpClient = new FTPClient();
        try {
            ftpClient.setConnectTimeout(10_000);
            ftpClient.setDefaultTimeout(10_000);
            ftpClient.connect(ftpHost, ftpPort);
            ftpClient.setSoTimeout(15_000);
            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                return List.of();
            }
            if (!ftpClient.login("anonymous", "")) {
                return List.of();
            }
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
            if (!ftpClient.changeWorkingDirectory(ftpPath)) {
                return List.of();
            }
            var names = ftpClient.listNames();
            return filterSubmissions(names, observationDate, lineCode);
        } catch (IOException e) {
            LOGGER.debug("Unable to list BASS2000 FTP directory {}", ftpUrl, e);
            return List.of();
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    if (ftpClient.getReplyCode() != 0) {
                        ftpClient.logout();
                    }
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {
            }
        }
    }

    static List<RemoteSubmission> filterSubmissions(String[] names, LocalDate observationDate, String lineCode) {
        if (names == null) {
            return List.of();
        }
        return Stream.of(names)
                .map(Bass2000FtpListingService::parseFilename)
                .flatMap(Optional::stream)
                .filter(s -> matchesLine(s.lineCode(), lineCode))
                .filter(s -> s.captureTime().toLocalDate().equals(observationDate))
                .toList();
    }

    static Optional<RemoteSubmission> parseFilename(String name) {
        if (name == null) {
            return Optional.empty();
        }
        var baseName = name;
        var slash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (slash >= 0) {
            baseName = baseName.substring(slash + 1);
        }
        var matcher = FILENAME_PATTERN.matcher(baseName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var lineCode = matcher.group(1);
        var captureStr = matcher.group(2);
        try {
            var captureTime = LocalDateTime.parse(captureStr, CAPTURE_FORMATTER);
            return Optional.of(new RemoteSubmission(baseName, captureTime, lineCode));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static boolean matchesLine(String parsedCode, String requestedCode) {
        if (requestedCode == null) {
            return true;
        }
        return groupOf(parsedCode).equalsIgnoreCase(groupOf(requestedCode));
    }

    private static String groupOf(String code) {
        var upper = code.toUpperCase();
        if (upper.startsWith("HA")) {
            return "HA";
        }
        if (upper.startsWith("CAK")) {
            return "CAK";
        }
        if (upper.startsWith("CAH")) {
            return "CAH";
        }
        return upper;
    }
}
