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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class CaptureSoftwareMetadataHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureSoftwareMetadataHelper.class);

    private CaptureSoftwareMetadataHelper() {

    }

    public static Optional<CaptureMetadata> readSharpcapMetadata(File serFile) {
        var baseName = computeSerFileBasename(serFile);
        File sharpcapFile = new File(serFile.getParentFile(), baseName + ".CameraSettings.txt");
        if (sharpcapFile.exists()) {
            try {
                var lines = tryRead(sharpcapFile);
                var camera = lines.get(0).substring(1, lines.get(0).length() - 1);
                if (camera.contains("(")) {
                    camera = camera.substring(0, camera.indexOf('(')).trim();
                }
                var binning = 1;
                for (var line : lines) {
                    if (line.startsWith("Binning=")) {
                        binning = parseBinning(line);
                        break;
                    }
                }
                LOGGER.info(message("found.metadata"), "Sharpcap", camera, binning);
                return Optional.of(new CaptureMetadata(camera, binning));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static String computeSerFileBasename(File serFile) {
        return serFile.getName().substring(0, serFile.getName().lastIndexOf("."));
    }

    private static int parseBinning(String line) {
        int binning;
        var value = line.substring(line.indexOf('=') + 1);
        if (value.contains("x")) {
            value = value.substring(0, value.indexOf("x"));
        }
        binning = Integer.parseInt(value.trim());
        return binning;
    }

    public static Optional<CaptureMetadata> readFireCaptureMetadata(File serFile) {
        var baseName = computeSerFileBasename(serFile);
        File filecaptureFile = new File(serFile.getParentFile(), baseName + ".txt");
        if (filecaptureFile.exists()) {
            try {
                var lines = tryRead(filecaptureFile);
                if (lines.get(0).toLowerCase(Locale.US).contains("firecapture")) {
                    String camera = null;
                    Integer binning = null;
                    for (var line : lines) {
                        if (line.startsWith("Binning=")) {
                            var tmp = line.substring(line.indexOf('=') + 1);
                            binning = parseBinning(tmp);
                        } else if (line.startsWith("Camera=")) {
                            camera = line.substring(line.indexOf('=') + 1).trim();
                        }
                        if (camera != null && binning != null) {
                            LOGGER.info(message("found.metadata"), "Firecapture", camera, binning);
                            return Optional.of(new CaptureMetadata(camera, binning));
                        }
                    }
                }
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<String> tryRead(File filecaptureFile) throws IOException {
        for (var charset : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII)) {
            try {
                return Files.readAllLines(filecaptureFile.toPath(), charset);
            } catch (IOException e) {
                // ignore
            }
        }
        return List.of("unknown");
    }

    public record CaptureMetadata(
        String camera,
        int binning
    ) {

    }
}
