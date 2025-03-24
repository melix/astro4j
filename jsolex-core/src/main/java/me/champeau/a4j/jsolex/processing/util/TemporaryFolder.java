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

public class TemporaryFolder {
    private static final Path TEMP_DIR = tempDir(ProcessHandle.current().pid());
    private static final String JSOLEX_PREFIX = "jsolex";

    private TemporaryFolder() {
    }

    static {
        var baseTempDir = tempDir(null);
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

    private static Path tempDir(Long pid) {
        var baseDir = Path.of(System.getProperty("java.io.tmpdir")).resolve(JSOLEX_PREFIX);
        var dir = pid == null ? baseDir : baseDir.resolve(pid.toString());
        dir.toFile().deleteOnExit();
        return dir;
    }

    public static Path tempDir() {
        return TEMP_DIR;
    }

    public static Path newTempFile(String prefix, String suffix) throws IOException {
        if (!Files.isDirectory(TEMP_DIR)) {
            Files.createDirectories(TEMP_DIR);
        }
        var tempFile = Files.createTempFile(TEMP_DIR, prefix, suffix);
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
        var tempDir = TEMP_DIR.resolve(dirName);
        tempDir.toFile().deleteOnExit();
        Files.createDirectories(tempDir);
        return tempDir;
    }
}
