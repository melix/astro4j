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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
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
    private final BooleanBinding allowFileOpen;
    private BiConsumer<? super Double, ? super Double> onCoordinatesListener;
    private Consumer<? super Double> onZoomChanged;

    private double zoom = 0;
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
                if (zoom == 1.0) {
                    zoom = getWidth() / imageView.getImage().getWidth();
                } else {
                    zoom = 1.0;
                }
                triggerOnZoomChanged();
            }
        });
        widthProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() > 0 && zoom == 0) {
                resetZoom();
            }
        });
        ctxMenu = new ContextMenu();
        var showFile = new MenuItem(message("show.in.files"));
        allowFileOpen = Bindings.createBooleanBinding(() -> imagePath == null || !Files.exists(imagePath));
        showFile.disableProperty().bind(allowFileOpen);
        showFile.setOnAction(e -> ExplorerSupport.openInExplorer(imagePath));
        ctxMenu.getItems().add(showFile);

        setOnContextMenuRequested(e -> ctxMenu.show(ZoomableImageView.this, e.getScreenX(), e.getScreenY()));

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

    public void setImagePath(Path imagePath) {
        this.imagePath = imagePath;
    }

    public void setImage(Image image) {
        imageView.setImage(image);
        imageView.setPreserveRatio(true);
    }

    public void fileSaved() {
        allowFileOpen.invalidate();
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

    public void alignWith(ZoomableImageView other) {
        var otherImage = other.getImage();
        if (otherImage.getWidth() == getImage().getWidth() && otherImage.getHeight() == getImage().getHeight()) {
            setZoom(other.getZoom());
            scrollPane.setHvalue(other.scrollPane.getHvalue());
            scrollPane.setVvalue(other.scrollPane.getVvalue());
        }
    }

    public void setOnZoomChanged(Consumer<? super Double> consumer) {
        onZoomChanged = consumer;
    }

    public void setCoordinatesListener(BiConsumer<? super Double, ? super Double> consumer) {
        onCoordinatesListener = consumer;
    }

    public void resetZoom() {
        var image = imageView.getImage();
        if (image == null) {
            zoom = 0;
            return;
        }
        var oldZoom = zoom;
        var width = getWidth();
        if (width == 0) {
            return;
        }
        zoom = width / image.getWidth();
        if (zoom != oldZoom) {
            triggerOnZoomChanged();
            applyZoom();
        }
    }

    public double getZoom() {
        return zoom;
    }
}
