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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class FfmegEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmegEncoder.class);
    private static final Pattern PROGRESS_FRAME = Pattern.compile("frame=(\\d+)");

    public static boolean isAvailable() {
        return Holder.isAvailable();
    }

    public static boolean encode(Broadcaster broadcaster,
                              ProgressOperation operation,
                              List<Object> images,
                              File outputFile,
                              int msBetweenFrames) throws IOException {
        // first step is to export each image in a temporary directory
        // and each frame must be named with a sequence number, eg. frame-0001.png
        var tempDir = TemporaryFolder.newTempDir("jsolex-ffmpeg-");
        List<File> frames = null;
        var progressOperation = operation.createChild("Exporting frames");
        try {
            broadcaster.broadcast(progressOperation);
            var progress = new AtomicInteger(0);
            frames = IntStream.range(0, images.size())
                .parallel()
                .mapToObj(i -> {
                    var fileName = String.format("frame-%04d.png", i);
                    var file = new File(tempDir.toFile(), fileName);
                    var img = images.get(i);
                    if (img instanceof FileBackedImage fileBackedImage) {
                        img = fileBackedImage.unwrapToMemory();
                    }
                    if (img instanceof ImageWrapper32 mono) {
                        ImageUtils.writeMonoImage(mono.width(), mono.height(), mono.data(), file, Set.of(ImageFormat.PNG));
                    } else if (img instanceof RGBImage rgb) {
                        ImageUtils.writeRgbImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b(), file, Set.of(ImageFormat.PNG));
                    }
                    broadcaster.broadcast(progressOperation.update(progress.incrementAndGet() / (double) images.size()));
                    return file;
                })
                .toList();
            broadcaster.broadcast(progressOperation.update(1, "Exporting frames"));
            broadcaster.broadcast(progressOperation.update(0, "Encoding (FFMPEG)"));
            var success = encodeFrameDirectory(tempDir, outputFile, msBetweenFrames,
                encoded -> broadcaster.broadcast(progressOperation.update(Math.min(1.0, encoded / (double) images.size()), "Encoding (FFMPEG)")));
            broadcaster.broadcast(progressOperation.complete());
            if (!success) {
                return false;
            }
        } finally {
            if (frames != null) {
                // delete all temporary files
                for (File file : frames) {
                    Path path = file.toPath();
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(tempDir);
        }
        return true;
    }

    /**
     * Encodes a directory of PNG frames named {@code frame-NNNN.png} into an H.264 video.
     *
     * @param framesDir the directory containing the frames
     * @param outputFile the output video file
     * @param msBetweenFrames the delay between frames, in milliseconds
     * @param encodedFrames receives the number of frames encoded so far, as encoding progresses
     * @return true if encoding succeeded
     * @throws IOException if ffmpeg cannot be started or is interrupted
     */
    public static boolean encodeFrameDirectory(Path framesDir, File outputFile, int msBetweenFrames, IntConsumer encodedFrames) throws IOException {
        double framesPerSecond = 1000d / msBetweenFrames;
        try {
            var ffmpeg = new ProcessBuilder("ffmpeg",
                "-framerate", framesPerSecond + "",
                "-i", framesDir + "/frame-%04d.png",
                "-y",
                "-crf", "10",
                "-c:v", "libx264",
                "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2",
                "-pix_fmt", "yuv420p",
                "-nostats",
                "-progress", "pipe:1",
                outputFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
            var sb = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(ffmpeg.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var matcher = PROGRESS_FRAME.matcher(line);
                    if (matcher.matches()) {
                        encodedFrames.accept(Integer.parseInt(matcher.group(1)));
                    } else {
                        sb.append(line).append("\n");
                    }
                }
            }
            LOGGER.debug("FFMPEG output: {}", sb);
            ffmpeg.waitFor(10, TimeUnit.MINUTES);
            return ffmpeg.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Unable to encode video", e);
        }
    }

    private static class Holder {
        private static final boolean AVAILABLE;

        static {
            boolean isAvailable;
            try {
                var ffmpeg = new ProcessBuilder("ffmpeg", "-h")
                    .start();
                isAvailable = ffmpeg.waitFor(5, TimeUnit.SECONDS) && ffmpeg.exitValue() == 0;
            } catch (IOException | InterruptedException e) {
                isAvailable = false;
            }
            if (Boolean.getBoolean("disable.ffmpeg")) {
                System.out.println("FFMPEG support disabled via system property");
                isAvailable = false;
            }
            AVAILABLE = isAvailable;
        }

        public static boolean isAvailable() {
            return AVAILABLE;
        }
    }
}
