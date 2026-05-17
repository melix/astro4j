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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class TemporaryFolder {
    /**
     * System property which, when set to an existing directory, overrides the location
     * where JSol'Ex stores its temporary files. When unset or pointing to an invalid
     * directory, the standard {@code java.io.tmpdir} is used instead.
     */
    public static final String TEMP_DIR_PROPERTY = "jsolex.tmpdir";
    private static final String JSOLEX_PREFIX = "jsolex";

    private TemporaryFolder() {
    }

    private static Path baseTempDir() {
        Path root = null;
        var configured = System.getProperty(TEMP_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            var candidate = Path.of(configured);
            if (Files.isDirectory(candidate)) {
                root = candidate;
            }
        }
        if (root == null) {
            root = Path.of(System.getProperty("java.io.tmpdir"));
        }
        return root.resolve(JSOLEX_PREFIX);
    }

    private static void cleanupStaleDirectories(Path baseTempDir) {
        if (Files.isDirectory(baseTempDir)) {
            try (var list = Files.list(baseTempDir)) {
                list.forEach(p -> Thread.startVirtualThread(() -> {
                    try {
                        if (!Files.isDirectory(p)) {
                            // old temp file, old version of JSol'Ex, delete it!
                            Files.delete(p);
                        } else {
                            try {
                                var pid = Long.parseLong(p.toFile().getName());
                                if (ProcessHandle.of(pid).isEmpty()) {
                                    deleteRecursively(p);
                                }
                            } catch (NumberFormatException e) {
                                // not a PID, delete
                                deleteRecursively(p);
                            }
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }));
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private static final class Holder {
        private static final Path TEMP_DIR = initialize();

        private static Path initialize() {
            var baseDir = baseTempDir();
            cleanupStaleDirectories(baseDir);
            baseDir.toFile().deleteOnExit();
            var dir = baseDir.resolve(Long.toString(ProcessHandle.current().pid()));
            dir.toFile().deleteOnExit();
            return dir;
        }
    }

    public static Path tempDir() {
        return Holder.TEMP_DIR;
    }

    public static Path newTempFile(String prefix, String suffix) throws IOException {
        var tempDir = tempDir();
        createDirectoriesIfNeeded(tempDir);
        var tempFile = Files.createTempFile(tempDir, prefix, suffix);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }


    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var list = Files.list(path)) {
                list.forEach(p -> {
                    try {
                        deleteRecursively(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        Files.delete(path);
    }

    public static Path newTempDir(String dirName) throws IOException {
        var tempDir = tempDir().resolve(dirName);
        tempDir.toFile().deleteOnExit();
        createDirectoriesIfNeeded(tempDir);
        return tempDir;
    }

    public static Path newUniqueTempDir(String prefix) throws IOException {
        var tempDir = tempDir();
        createDirectoriesIfNeeded(tempDir);
        var dir = Files.createTempDirectory(tempDir, prefix);
        dir.toFile().deleteOnExit();
        return dir;
    }
}
