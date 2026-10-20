/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx;

import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.ImageMathEditor.ScriptLanguage;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Sample scripts bundled with JSol'Ex. They are copied to a directory managed by the application,
 * so that they can be used like any other script file without the user having to save them.
 */
public final class SampleScripts {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleScripts.class);

    private static final List<SampleScript> ALL = List.of(
            new SampleScript("helium-processing", ScriptLanguage.IMAGEMATH, false),
            new SampleScript("continuum-animation", ScriptLanguage.IMAGEMATH, false),
            new SampleScript("doppler", ScriptLanguage.IMAGEMATH, false),
            new SampleScript("virtual-eclipse", ScriptLanguage.IMAGEMATH, false),
            new SampleScript("globe", ScriptLanguage.IMAGEMATH, false),
            new SampleScript("enhanced-invert", ScriptLanguage.IMAGEMATH, false),
            new SampleScript("python-demo", ScriptLanguage.PYTHON, false),
            new SampleScript("stacking", ScriptLanguage.IMAGEMATH, true),
            new SampleScript("aggressive-stacking-ha", ScriptLanguage.IMAGEMATH, true),
            new SampleScript("aggressive-stacking-ca", ScriptLanguage.IMAGEMATH, true),
            new SampleScript("halpha-animation", ScriptLanguage.PYTHON, true)
    );

    private SampleScripts() {
    }

    /**
     * Returns the sample scripts which can be used in the given mode.
     * @param batchMode whether the scripts are used in batch mode
     * @return the sample scripts
     */
    public static List<SampleScript> available(boolean batchMode) {
        return ALL.stream().filter(s -> batchMode || !s.batchOnly()).toList();
    }

    /**
     * Returns the sample script corresponding to the given file, if the file
     * is the installed copy of a sample script.
     * @param file a script file
     * @return the sample script, or empty if the file is not a sample
     */
    public static Optional<SampleScript> find(File file) {
        if (!isInSamplesDir(file.toPath())) {
            return Optional.empty();
        }
        return ALL.stream().filter(s -> s.fileName().equals(file.getName())).findFirst();
    }

    /**
     * Returns true if the given path is in the directory where sample scripts are installed.
     * @param path a path
     * @return true if the path is in the samples directory
     */
    public static boolean isInSamplesDir(Path path) {
        return path.toAbsolutePath().normalize().startsWith(samplesDir());
    }

    /**
     * Updates the installed copies of the sample scripts with the version bundled with this release.
     */
    public static void refreshInstalled() {
        for (var sample : ALL) {
            if (Files.exists(sample.installedFile())) {
                try {
                    sample.install();
                } catch (IOException e) {
                    LOGGER.warn("Unable to update sample script {}", sample.fileName(), e);
                }
            }
        }
    }

    private static Path samplesDir() {
        return VersionUtil.getJsolexDir().resolve("samples").toAbsolutePath().normalize();
    }

    /**
     * A sample script.
     * @param id the identifier of the script, which is also its file name without extension
     * @param language the language of the script
     * @param batchOnly whether the script only makes sense in batch mode
     */
    public record SampleScript(String id, ScriptLanguage language, boolean batchOnly) {
        /**
         * Returns the file name of the script.
         * @return the file name
         */
        public String fileName() {
            return id + language.getExtension();
        }

        /**
         * Returns the localized name of the script.
         * @return the name
         */
        public String label() {
            return JSolEx.message("script." + id.replace('-', '.'));
        }

        /**
         * Reads the script bundled with the application.
         * @return the script text
         * @throws IOException if the script cannot be read
         */
        public String readBundled() throws IOException {
            try (var stream = SampleScripts.class.getResourceAsStream("/me/champeau/a4j/jsolex/templates/" + fileName())) {
                if (stream == null) {
                    throw new IOException("Missing sample script " + fileName());
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        /**
         * Copies the bundled script to the samples directory, replacing any previous copy.
         * @return the installed file
         * @throws IOException if the script cannot be copied
         */
        public File install() throws IOException {
            var target = installedFile();
            var text = readBundled();
            if (!Files.exists(target) || !Files.readString(target).equals(text)) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, text);
            }
            return target.toFile();
        }

        private Path installedFile() {
            return samplesDir().resolve(fileName());
        }
    }
}
