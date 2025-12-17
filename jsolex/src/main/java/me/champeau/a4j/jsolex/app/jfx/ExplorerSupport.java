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
package me.champeau.a4j.jsolex.app.jfx;

import me.champeau.a4j.jsolex.app.JSolEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Utility class for opening files in the system file explorer.
 */
public class ExplorerSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerSupport.class);

    private ExplorerSupport() {

    }

    /**
     * Opens the system file explorer and selects the specified file.
     * @param imagePath the path to the file to show
     */
    public static void openInExplorer(Path imagePath) {
        if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            Desktop.getDesktop().browseFileDirectory(imagePath.toFile());
            return;
        }
        var osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        var absolutePath = imagePath.toAbsolutePath().toString();
        if (osName.contains("windows")) {
            runCommand("explorer.exe", "/select," + absolutePath);
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            runCommand("open", "-R", absolutePath);
        } else {
            openInLinuxFileManager(imagePath);
        }
    }

    private static void openInLinuxFileManager(Path path) {
        // Try D-Bus FileManager1 interface first (works with most modern file managers)
        var fileUri = path.toAbsolutePath().toUri().toString();
        boolean success = runCommandAndWait(
                "gdbus", "call",
                "--session",
                "--dest", "org.freedesktop.FileManager1",
                "--object-path", "/org/freedesktop/FileManager1",
                "--method", "org.freedesktop.FileManager1.ShowItems",
                "['" + fileUri + "']", ""
        );
        if (!success) {
            // Fallback: open the parent directory (won't select the file)
            runCommand("xdg-open", path.getParent().toAbsolutePath().toString());
        }
    }

    private static void runCommand(String... command) {
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            LOGGER.info(message("info.opening.files.not.supported"));
        }
    }

    private static boolean runCommandAndWait(String... command) {
        try {
            int exitCode = new ProcessBuilder(command).start().waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
