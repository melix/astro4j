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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class ZoomableImageView extends HBox {
    private final ScrollPane scrollPane;
    private final ImageView imageView;
    private final ContextMenu ctxMenu;
    private BiConsumer<? super Double, ? super Double> onCoordinatesListener;
    private Consumer<? super Double> onZoomChanged;

    private Tab parentTab;
    private double zoom = 1.0;
    private double lastX = 0;
    private double lastY = 0;
    private Path imagePath;

    public ZoomableImageView() {
        super();
        this.scrollPane = new ScrollPane();
        this.imageView = new ImageView();

        scrollPane.setPannable(true);
        scrollPane.setContent(imageView);

        imageView.setOnScroll(this::handleScroll);
        imageView.setOnMouseMoved(evt -> {
            if (onCoordinatesListener != null) {
                onCoordinatesListener.accept(evt.getX() / zoom, evt.getY() / zoom);
            }
        });
        imageView.setOnMouseClicked(evt -> {
            if (evt.getButton().equals(MouseButton.PRIMARY) && evt.getClickCount() == 2) {
                zoom = 1.0;
                triggerOnZoomChanged();
            }
        });

        ctxMenu = new ContextMenu();
        var showFile = new MenuItem(message("show.in.files"));
        showFile.setOnAction(e -> ExplorerSupport.openInExplorer(imagePath));
        ctxMenu.getItems().add(showFile);

        setOnContextMenuRequested(e -> {
            if (imagePath != null && Files.exists(imagePath)) {
                ctxMenu.show(ZoomableImageView.this, e.getScreenX(), e.getScreenY());
            }
        });

        getChildren().add(scrollPane);
    }

    private void handleScroll(ScrollEvent event) {
        if (event.isControlDown()) {
            double deltaY = event.getDeltaY();
            if (deltaY != 0) {
                double zoomFactor = 1.05;
                if (deltaY < 0) {
                    zoom /= zoomFactor;
                } else {
                    zoom *= zoomFactor;
                }
                zoom = Math.max(0.1, Math.min(zoom, 5));
                triggerOnZoomChanged();
                applyZoom();
            }
            event.consume();
        }
    }

    private void triggerOnZoomChanged() {
        if (onZoomChanged != null) {
            onZoomChanged.accept(zoom);
        }
    }

    private void applyZoom() {
        var image = imageView.getImage();
        imageView.setFitWidth(image.getWidth() * zoom);
        imageView.setFitHeight(image.getHeight() * zoom);
        adjustScrollPane();
    }

    private void adjustScrollPane() {
        var boundsInLocal = imageView.getBoundsInLocal();
        scrollPane.setPrefViewportWidth(boundsInLocal.getWidth());
        scrollPane.setPrefViewportHeight(boundsInLocal.getHeight());
    }

    public Tab getParentTab() {
        return parentTab;
    }

    public void setParentTab(Tab parentTab) {
        this.parentTab = parentTab;
    }

    public void setImagePath(Path imagePath) {
        this.imagePath = imagePath;
    }

    public void setImage(Image image) {
        imageView.setImage(image);
        imageView.setPreserveRatio(true);
        zoom = getWidth() / image.getWidth();
        triggerOnZoomChanged();
        applyZoom();
    }

    public ContextMenu getCtxMenu() {
        return ctxMenu;
    }

    public Image getImage() {
        return imageView.getImage();
    }

    public void setZoom(double v) {
        zoom = v;
        applyZoom();
    }

    public void setOnZoomChanged(Consumer<? super Double> consumer) {
        onZoomChanged = consumer;
    }

    public void setCoordinatesListener(BiConsumer<? super Double, ? super Double> consumer) {
        onCoordinatesListener = consumer;
    }
}
