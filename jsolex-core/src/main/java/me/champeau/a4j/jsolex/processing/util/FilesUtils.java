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

import me.champeau.a4j.jsolex.processing.expr.FileOutputResult;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class FilesUtils {
    // An ordered list of charsets
    private static final List<Charset> STANDARD_CHARSETS = List.of(
            StandardCharsets.UTF_8,
            Charset.defaultCharset(),
            StandardCharsets.ISO_8859_1,
            StandardCharsets.US_ASCII,
            StandardCharsets.UTF_16
    );

    /**
     * Creates the directories in the given path if they do not exist.
     * This is not strictly needed to perform a check, but it avoids
     * an internal exception which is annoying when debugging.
     */
    public static void createDirectoriesIfNeeded(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private static <T> T withCharsetDetection(Producer<Charset, T> producer) {
        for (Charset charset : STANDARD_CHARSETS) {
            try {
                return producer.apply(charset);
            } catch (Exception ex) {
                // skip to next one
            }
        }
        for (Charset charset : Charset.availableCharsets().values()) {
            try {
                return producer.apply(charset);
            } catch (Exception ex) {
                // skip to next one
            }
        }
        throw new IllegalStateException("Unable to determine charset");
    }

    public static List<String> readAllLines(Path file) {
        if (Files.exists(file)) {
            return withCharsetDetection(charset -> Files.readAllLines(file, charset));
        } else {
            return List.of();
        }
    }

    public static String readString(Path file) throws IOException {
        if (Files.exists(file)) {
            return withCharsetDetection(charset -> Files.readString(file, charset));
        } else {
            return "";
        }
    }

    public static Reader newTextReader(Path file) throws IOException {
        return new StringReader(readString(file));
    }

    public static Writer newTextWriter(Path file) throws FileNotFoundException {
        return new OutputStreamWriter(new FileOutputStream(file.toFile()), Charset.defaultCharset());
    }

    public static void writeString(String string, Path destination) throws IOException {
        Files.writeString(destination, string, Charset.defaultCharset(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @FunctionalInterface
    private interface Producer<F, T> {
        T apply(F from) throws Exception;

        default T handleException(F from) {
            try {
                return apply(from);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Saves all files from a FileOutputResult (both display and non-display files)
     * to the output directory using the provided base name, and returns the path
     * where the display file was saved.
     *
     * @param fileOutput the file output result containing all files
     * @param outputDirectory the directory where files should be saved
     * @param baseName the base name for the files (extension will be appended)
     * @return the path where the display file was saved, or null if no display file
     * @throws IOException if an I/O error occurs
     */
    public static Path saveAllFilesAndGetDisplayPath(
            FileOutputResult fileOutput,
            Path outputDirectory,
            String baseName
    ) throws IOException {
        Path displayPath = null;
        for (var file : fileOutput.allFiles()) {
            var fileName = file.getFileName().toString();
            var ext = fileName.substring(fileName.lastIndexOf("."));
            var targetPath = outputDirectory.resolve(baseName + ext);
            createDirectoriesIfNeeded(targetPath.getParent());
            Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
            if (file.equals(fileOutput.displayFile())) {
                displayPath = targetPath;
            }
        }
        return displayPath;
    }

    /**
     * Saves only the non-display files from a FileOutputResult to the output directory
     * using the provided base name. The display file is not processed by this method
     * and should be handled separately by the caller (typically passed to an emitter).
     *
     * @param fileOutput the file output result containing all files
     * @param outputDirectory the directory where files should be saved
     * @param baseName the base name for the files (extension will be appended)
     * @throws IOException if an I/O error occurs
     */
    public static void saveNonDisplayFiles(
            FileOutputResult fileOutput,
            Path outputDirectory,
            String baseName
    ) throws IOException {
        var displayFile = fileOutput.displayFile();
        for (var file : fileOutput.allFiles()) {
            if (!file.equals(displayFile)) {
                var fileName = file.getFileName().toString();
                var extension = fileName.substring(fileName.lastIndexOf("."));
                var targetFile = outputDirectory.resolve(baseName + extension);
                createDirectoriesIfNeeded(targetFile.getParent());
                Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

}
