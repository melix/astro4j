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
import java.nio.file.Files;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class CaptureSoftwareMetadataHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureSoftwareMetadataHelper.class);

    private CaptureSoftwareMetadataHelper() {

    }

    public static Optional<CaptureMetadata> readSharpcapMetadata(File serFile) {
        var baseName = serFile.getName().substring(0, serFile.getName().lastIndexOf("."));
        File sharpcapFile = new File(serFile.getParentFile(), baseName + ".CameraSettings.txt");
        if (sharpcapFile.exists()) {
            try {
                var lines = Files.readAllLines(sharpcapFile.toPath());
                var camera = lines.get(0).substring(1, lines.get(0).length() - 1);
                if (camera.contains("(")) {
                    camera = camera.substring(0, camera.indexOf('(')).trim();
                }
                var binning = 1;
                for (var line : lines) {
                    if (line.startsWith("Binning")) {
                        binning = Integer.parseInt(line.substring(line.indexOf('=') + 1).trim());
                        break;
                    }
                }
                LOGGER.info(message("found.metadata"), "Sharpcap", camera, binning);
                return Optional.of(new CaptureMetadata(camera, binning));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static Optional<CaptureMetadata> readFireCaptureMetadata(File serFile) {
        var baseName = serFile.getName().substring(0, serFile.getName().lastIndexOf("."));
        File filecaptureFile = new File(serFile.getParentFile(), baseName + ".txt");
        if (filecaptureFile.exists()) {
            try {
                var lines = Files.readAllLines(filecaptureFile.toPath());
                if (lines.get(0).contains("Firecapture")) {
                    String camera = null;
                    Integer binning = null;
                    for (var line : lines) {
                        if (line.startsWith("Binning")) {
                            var tmp = line.substring(line.indexOf('=') + 1);
                            binning = Integer.parseInt(tmp.substring(0, tmp.indexOf("x")).trim());
                        } else if (line.startsWith("Camera")) {
                            camera = line.substring(line.indexOf('=') + 1).trim();
                        }
                        if (camera != null && binning != null) {
                            LOGGER.info(message("found.metadata"), "Firecapture", camera, binning);
                            return Optional.of(new CaptureMetadata(camera, binning));
                        }
                    }
                }
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public record CaptureMetadata(
        String camera,
        int binning
    ) {

    }
}
