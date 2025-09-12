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
package me.champeau.a4j.jsolex.app;

import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.math.tuples.IntPair;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public class Configuration {
    private static final String RECENT_FILES = "recent.files";
    private static final String PREFERRED_WIDTH = "preferred.width";
    private static final String PREFERRED_HEIGHT = "preferred.height";
    private static final String LAST_DIRECTORY = "last.directory";
    private static final String CUSTOM_DIR = "custom.dir.";
    private static final String MEMORY_RESTRICTION = "memory.restriction.multiplier";
    private static final String AUTO_START_SERVER = "auto.start.server";
    private static final String AUTO_START_SERVER_PORT = "auto.start.server.port";
    private static final String BASS2000_FTP_URL = "bass2000.ftp.url";
    private static final String PIPP_COMPAT = "pipp.compatibility";
    private static final String SELECTED_LANGUAGE = "selected.language";

    public static final String DEFAULT_SOLAP_URL = "ftp://ftp.obspm.fr/incoming/solap";

    private final Preferences prefs;
    private final List<Path> recentFiles;

    private Configuration() {
        prefs = Preferences.userRoot().node(this.getClass().getName());
        var recentFilesString = prefs.get(RECENT_FILES, "");
        this.recentFiles = Arrays.stream(recentFilesString.split(Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .filter(Files::exists)
                .limit(10)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        FitsUtils.setPippCompatibility(isWritePippCompatibleFits());
        
        // Set system property for locale if configured
        var selectedLanguage = getSelectedLanguage();
        if (selectedLanguage != null) {
            System.setProperty("jsolex.locale", selectedLanguage);
        }
    }

    public static Configuration getInstance() {
        return new Configuration();
    }

    public void loadedSerFile(Path path) {
        recentFiles.remove(path);
        recentFiles.addFirst(path);
        prefs.put(RECENT_FILES, recentFiles.stream().map(Path::toString).limit(10).collect(joining(File.pathSeparator)));
        updateLastOpenDirectory(path.getParent());
    }

    public void rememberDirectoryFor(Path path, DirectoryKind kind) {
        if (kind == DirectoryKind.SER_FILE) {
            loadedSerFile(path);
        } else {
            updateLastOpenDirectory(path.getParent(), kind);
        }
    }

    public void updateLastOpenDirectory(Path directory) {
        updateLastOpenDirectory(directory, DirectoryKind.SER_FILE);
    }

    public void updateLastOpenDirectory(Path directory, DirectoryKind kind) {
        var name = directory.toString();
        var key = kind.dirKey();
        prefs.put(key, name);
    }

    public void updateLastOpenDirectory(Path directory, String customKey) {
        var name = directory.toString();
        var key = CUSTOM_DIR + customKey;
        prefs.put(key, name);
    }

    public int getMemoryRestrictionMultiplier() {
        return prefs.getInt(MEMORY_RESTRICTION, 1);
    }

    public void setMemoryRestrictionMultiplier(int multiplier) {
        prefs.putInt(MEMORY_RESTRICTION, multiplier);
    }

    public List<Path> getRecentFiles() {
        return Collections.unmodifiableList(recentFiles);
    }

    public Optional<Path> findLastOpenDirectory() {
        return findLastOpenDirectory(DirectoryKind.SER_FILE);
    }

    public Optional<Path> findLastOpenDirectory(DirectoryKind kind) {
        var dir = prefs.get(kind.dirKey(), null);
        if (dir != null) {
            var path = Path.of(dir);
            if (Files.exists(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    public Optional<Path> findLastOpenDirectory(String customKey) {
        var dir = prefs.get(CUSTOM_DIR + customKey, null);
        if (dir != null) {
            var path = Path.of(dir);
            if (Files.exists(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    public boolean isAutoStartServer() {
        return prefs.getBoolean(AUTO_START_SERVER, false);
    }

    public boolean isWritePippCompatibleFits() {
        return prefs.getBoolean(PIPP_COMPAT, false);
    }

    public void setWritePippCompatibleFits(boolean pippCompat) {
        prefs.putBoolean(PIPP_COMPAT, pippCompat);
        FitsUtils.setPippCompatibility(pippCompat);
    }

    public void setAutoStartServer(boolean autoStart) {
        prefs.putBoolean(AUTO_START_SERVER, autoStart);
    }

    public int getAutoStartServerPort() {
        return prefs.getInt(AUTO_START_SERVER_PORT, JSolEx.EMBEDDED_SERVER_DEFAULT_PORT);
    }

    public void setAutoStartServerPort(int port) {
        prefs.putInt(AUTO_START_SERVER_PORT, port);
    }


    public IntPair getPreferredDimensions() {
        return new IntPair(
                prefs.getInt(PREFERRED_WIDTH, 1024),
                prefs.getInt(PREFERRED_HEIGHT, 768)
        );
    }

    public void setPreferredWidth(int width) {
        prefs.put(PREFERRED_WIDTH, String.valueOf(width));
    }

    public void setPreferredHeigth(int height) {
        prefs.put(PREFERRED_HEIGHT, String.valueOf(height));
    }

    public int getWatchModeWaitTimeMilis() {
        return prefs.getInt("watch.mode.wait.time", 2500);
    }

    public void setWatchModeWaitTimeMilis(int time) {
        prefs.putInt("watch.mode.wait.time", Math.max(500, time));
    }

    public String getBass2000FtpUrl() {
        return prefs.get(BASS2000_FTP_URL, DEFAULT_SOLAP_URL);
    }

    public void setBass2000FtpUrl(String url) {
        prefs.put(BASS2000_FTP_URL, url);
    }

    public String getSelectedLanguage() {
        return prefs.get(SELECTED_LANGUAGE, null);
    }

    public void setSelectedLanguage(String languageTag) {
        if (languageTag == null) {
            prefs.remove(SELECTED_LANGUAGE);
            System.clearProperty("jsolex.locale");
        } else {
            prefs.put(SELECTED_LANGUAGE, languageTag);
            System.setProperty("jsolex.locale", languageTag);
        }
    }

    public enum DirectoryKind {
        SER_FILE(null),
        FLAT_FILE("flat"),
        IMAGE_MATH("image.math"),
        SPECTRUM_IDENTIFICATION("spectrum.identification");

        private final String dirName;

        DirectoryKind(String dirName) {
            this.dirName = dirName;
        }

        public String dirKey() {
            if (dirName == null) {
                return LAST_DIRECTORY;
            }
            return LAST_DIRECTORY + "." + dirName;
        }
    }
}
