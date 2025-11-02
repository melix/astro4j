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

public class ExplorerSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerSupport.class);

    private ExplorerSupport() {

    }

    public static void openInExplorer(Path imagePath) {
        if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            Desktop.getDesktop().browseFileDirectory(imagePath.toFile());
        } else {
            // try a generic "open" call for unsupported platforms, which will
            // open the directory but not focus on the file
            try {
                var builder = new ProcessBuilder();
                builder.command("open", imagePath.getParent().toAbsolutePath().toString());
                builder.start();
            } catch (IOException ex) {
                try {
                    if (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows")) {
                        var builder = new ProcessBuilder();
                        builder.command("explorer.exe", "/select,\"" + imagePath.toAbsolutePath() + "\"");
                        builder.start();
                    } else {
                        LOGGER.info(message("info.opening.files.not.supported"));
                    }
                } catch (IOException ex2) {
                    LOGGER.info(message("info.opening.files.not.supported"));
                }
            }
        }
    }
}
