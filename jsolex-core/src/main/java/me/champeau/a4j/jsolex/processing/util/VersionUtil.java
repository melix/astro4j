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
import java.nio.file.Paths;
import java.util.Properties;

public class VersionUtil {

    private VersionUtil() {
    }

    public static String getVersion() {
        return Holder.VERSION;
    }

    public static String getFullVersion() {
        return Holder.FULL_VERSION;
    }

    public static boolean isSnapshot() {
        return Holder.IS_SNAPSHOT;
    }

    public static Path getJsolexDir() {
        var path = Paths.get(System.getProperty("user.home"), ".jsolex" + (isSnapshot() ? "-devel" : ""));
        if (isSnapshot() && !Files.exists(path)) {
            var origFile = Paths.get(System.getProperty("user.home"), ".jsolex");
            if (Files.exists(origFile)) {
                // copy the original directory to the new one
                try {
                    Files.createDirectories(path);
                    try (var walker = Files.walk(origFile)) {
                        walker.forEach(source -> {
                            try {
                                var target = path.resolve(origFile.relativize(source));
                                if (Files.isRegularFile(source)) {
                                    Files.createDirectories(target.getParent());
                                    Files.copy(source, target);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return path;
    }

    public static Properties readSchemaVersions() {
        var schemas = getJsolexDir().resolve("schemas.txt");
        if (!Files.exists(schemas)) {
            writeSchemaVersions(new Properties());
        }
        var props = new Properties();
        try (var reader = Files.newBufferedReader(schemas)) {
            props.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props;
    }

    public static void writeSchemaVersions(Properties props) {
        var schemas = getJsolexDir().resolve("schemas.txt");
        try (var writer = Files.newBufferedWriter(schemas)) {
            props.store(writer, "Schema versions for jsolex");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Holder {
        private static final String VERSION = getVersion();
        private static final String FULL_VERSION = getFullVersion();
        private static final boolean IS_SNAPSHOT = getFullVersion().contains("-SNAPSHOT");

        private static String getVersion() {
            String version = getFullVersion();
            if (version.contains("-SNAPSHOT")) {
                version = version.substring(0, version.indexOf("-SNAPSHOT"));
            }
            return version;
        }

        private static String getFullVersion() {
            String version = "";
            try {
                version = new String(VersionUtil.class.getResourceAsStream("/version.txt").readAllBytes(), "utf-8").trim();
            } catch (IOException e) {
                version = "unknown";
            }
            return version;
        }
    }
}
