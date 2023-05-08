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

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZoomableImageView extends ImageView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoomableImageView.class);

    private Path imagePath;

    public ZoomableImageView() {
        super();
        setOnScroll(event -> {
            double deltaY = event.getDeltaY();
            if (deltaY != 0) {
                double zoomFactor = 0.95;
                double scaleX = getScaleX();
                double scaleY = getScaleY();
                if (deltaY < 0) {
                    setScaleX(scaleX * zoomFactor);
                    setScaleY(scaleY * zoomFactor);
                } else {
                    setScaleX(scaleX / zoomFactor);
                    setScaleY(scaleY / zoomFactor);
                }
            }
            event.consume();
        });
        var ctxMenu = new ContextMenu();
        var showFile = new MenuItem("Show file in files");
        showFile.setOnAction(e -> {
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
                    LOGGER.info("Opening files not supported on this platform");
                }
            }
        });
        ctxMenu.getItems().add(showFile);
        setOnContextMenuRequested(e -> {
            if (imagePath != null && Files.exists(imagePath)) {
                ctxMenu.show(ZoomableImageView.this, e.getScreenX(), e.getScreenY());
            }
        });
    }

    public void setImagePath(Path imagePath) {
        this.imagePath = imagePath;
    }
}
