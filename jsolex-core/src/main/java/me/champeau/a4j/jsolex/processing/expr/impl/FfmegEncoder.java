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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FfmegEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmegEncoder.class);

    public static boolean isAvailable() {
        return Holder.isAvailable();
    }

    public static void encode(List<Object> images, File outputFile, int msBetweenFrames) throws IOException {
        // first step is to export each image in a temporary directory
        // and each frame must be named with a sequence number, eg. frame-0001.png
        var tempDir = Files.createTempDirectory("jsolex-ffmpeg-");
        var files = new ArrayList<File>();
        try {
            try (var executor = Executors.newCachedThreadPool()) {
                int sequenceNumber = 0;
                for (Object image : images) {
                    var fileName = String.format("frame-%04d.png", sequenceNumber++);
                    var file = new File(tempDir.toFile(), fileName);
                    files.add(file);
                    executor.submit(() -> {
                        var img = image;
                        if (img instanceof FileBackedImage fileBackedImage) {
                            img = fileBackedImage.unwrapToMemory();
                        }
                        if (img instanceof ImageWrapper32 mono) {
                            ImageUtils.writeMonoImage(mono.width(), mono.height(), mono.data(), file, Set.of(ImageFormat.PNG));
                        } else if (img instanceof ColorizedImageWrapper colorized) {
                            var rgb = colorized.converter().apply(colorized.mono());
                            ImageUtils.writeRgbImage(colorized.width(), colorized.height(), rgb[0], rgb[1], rgb[2], file, Set.of(ImageFormat.PNG));
                        } else if (img instanceof RGBImage rgb) {
                            ImageUtils.writeRgbImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b(), file, Set.of(ImageFormat.PNG));
                        }
                    });
                }
            }
            int framesPerSecond = 1000 / msBetweenFrames;
            // now we can use ffmpeg to encode the video
            var ffmpeg = new ProcessBuilder("ffmpeg",
                "-framerate", framesPerSecond + "",
                "-i", tempDir + "/frame-%04d.png",
                "-y",
                "-crf", "10",
                "-c:v", "libx264",
                outputFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
            try (var reader = new BufferedReader(new java.io.InputStreamReader(ffmpeg.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug(line);
                }
            }
            ffmpeg.waitFor(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Unable to encode video", e);
        } finally {
            // delete all temporary files
            for (File file : files) {
                Path path = file.toPath();
                Files.deleteIfExists(path);
            }
            Files.deleteIfExists(tempDir);
        }
    }

    private static class Holder {
        private static final boolean AVAILABLE;

        static {
            boolean isAvailable;
            try {
                var ffmpeg = new ProcessBuilder("ffmpeg", "-h")
                    .start();
                isAvailable = ffmpeg.waitFor(1, TimeUnit.SECONDS) && ffmpeg.exitValue() == 0;
            } catch (IOException | InterruptedException e) {
                isAvailable = false;
            }
            AVAILABLE = isAvailable;
        }

        public static boolean isAvailable() {
            return AVAILABLE;
        }
    }
}
