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

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import me.champeau.a4j.math.regression.Ellipse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class ZoomableImageView extends HBox {
    private final PauseTransition pause = new PauseTransition(Duration.millis(250));
    private final ScrollPane scrollPane;
    private final ImageView imageView;
    private final ContextMenu ctxMenu;
    private final BooleanBinding allowFileOpen;
    private final BooleanBinding canFitToCenter = new BooleanBinding() {
        @Override
        protected boolean computeValue() {
            return canFitToCenter();
        }
    };
    private final List<Runnable> pendingOperations = new ArrayList<>();
    private BiConsumer<? super Double, ? super Double> onCoordinatesListener;
    private Consumer<? super Double> onZoomChanged;
    private Ellipse solardisk;

    private double zoom = 0;
    private Path imagePath;

    // Fields for rectangle selection
    private double startX;
    private double startY;
    private final Rectangle selectionRectangle;
    private final Label selectionLabel;
    private final BooleanProperty isSelectingRectangle = new SimpleBooleanProperty();
    private RectangleSelectionListener rectangleSelectionListener;

    public ZoomableImageView() {
        super();
        this.scrollPane = new ScrollPane();
        this.imageView = new ImageView();

        scrollPane.setPannable(true);
        scrollPane.setContent(imageView);

        imageView.setOnScroll(this::handleScroll);
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
                fitToCenter();
            }
        });
        heightProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() > 0 && zoom == 0) {
                fitToCenter();
            }
        });
        ctxMenu = new ContextMenu();
        var showFile = new MenuItem(message("show.in.files"));
        allowFileOpen = Bindings.createBooleanBinding(() -> imagePath == null || !Files.exists(imagePath));
        showFile.disableProperty().bind(allowFileOpen);
        showFile.setOnAction(e -> ExplorerSupport.openInExplorer(imagePath));
        ctxMenu.getItems().add(showFile);

        setOnContextMenuRequested(e -> ctxMenu.show(ZoomableImageView.this, e.getScreenX(), e.getScreenY()));

        // Add selection rectangle
        selectionRectangle = new Rectangle();
        selectionRectangle.setFill(Color.rgb(255, 0, 0, 0.3));
        selectionRectangle.setStroke(Color.RED);
        selectionLabel = new Label();
        selectionLabel.setTextFill(Color.RED);
        disableSelection();

        var pane = new Pane(imageView, selectionRectangle, selectionLabel);
        scrollPane.setContent(pane);
        scrollPane.pannableProperty().bind(isSelectingRectangle.not());
        scrollPane.addEventFilter(ScrollEvent.ANY, event -> {
            if (isSelectingRectangle.get()) {
                event.consume();
            }
        });
        addSelectionHandlers();

        getChildren().add(scrollPane);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && isSelectingRectangle.get()) {
                e.consume();
                disableSelection();
            }
        });
        setOnKeyReleased(e -> {
            if (isSelectingRectangle.get() && e.getCode() == KeyCode.CONTROL) {
                handleSelectionFinished(getWidth() / 2, getHeight() / 2);
            }
        });
    }

    private void disableSelection() {
        isSelectingRectangle.set(false);
        selectionRectangle.setVisible(false);
        selectionLabel.setVisible(false);
    }

    private void addSelectionHandlers() {
        imageView.setOnMousePressed(this::handleMousePressed);
        imageView.setOnMouseMoved(this::handleMouseMoved);
        imageView.setOnMouseDragged(this::handleMouseMoved);
        imageView.setOnMouseReleased(this::handleMouseReleased);
        selectionRectangle.setOnMouseReleased(this::handleMouseReleased);
        imageView.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (isSelectingRectangle.get() && event.isControlDown()) {
                event.consume();
            }
        });
    }

    private void handleMousePressed(MouseEvent event) {
        if (rectangleSelectionListener != null && event.getButton() == MouseButton.PRIMARY && event.isControlDown()) {
            isSelectingRectangle.set(true);
            startX = event.getX();
            startY = event.getY();
            selectionRectangle.setX(startX);
            selectionRectangle.setY(startY);
            selectionRectangle.setWidth(0);
            selectionRectangle.setHeight(0);
            selectionRectangle.setVisible(true);
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        if (event.isControlDown() && isSelectingRectangle.get()) {
            double endX = event.getX();
            double endY = event.getY();
            selectionRectangle.setX(Math.min(startX, endX));
            selectionRectangle.setY(Math.min(startY, endY));
            selectionRectangle.setWidth(Math.abs(endX - startX));
            selectionRectangle.setHeight(Math.abs(endY - startY));
            var selectionWidth = (int) Math.round(selectionRectangle.getWidth() / zoom);
            var selectionHeight = (int) Math.round(selectionRectangle.getHeight() / zoom);
            selectionLabel.setText(selectionWidth + "x" + selectionHeight);
            selectionLabel.setLayoutX(selectionRectangle.getX() + selectionRectangle.getWidth() / 2);
            selectionLabel.setLayoutY(selectionRectangle.getY() + selectionRectangle.getHeight() / 2);
            selectionLabel.setVisible(true);
        }
        if (onCoordinatesListener != null) {
            onCoordinatesListener.accept(event.getX() / zoom, event.getY() / zoom);
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && isSelectingRectangle.get()) {
            handleSelectionFinished(event.getScreenX(), event.getScreenY());
        }
    }

    private void handleSelectionFinished(double x, double y) {
        pause.setOnFinished(unused -> {
            if (selectionRectangle.getWidth() > 0 && selectionRectangle.getHeight() > 0) {
                showSelectionMenu(x, y);
            }
        });
        pause.playFromStart();
    }

    private void showSelectionMenu(double screenX, double screenY) {
        var selectionMenu = new ContextMenu();
        selectionMenu.setOnHidden(e -> disableSelection());
        var items = selectionMenu.getItems();
        var image = imageView.getImage();
        var selectionX = (int) Math.max(0, Math.round(selectionRectangle.getX() / zoom));
        var selectionY = (int) Math.max(0, Math.round(selectionRectangle.getY() / zoom));
        var selectionWidth = (int) Math.min(image.getWidth() - selectionX, Math.round(selectionRectangle.getWidth() / zoom));
        var selectionHeight = (int) Math.min(image.getHeight() - selectionY, Math.round(selectionRectangle.getHeight() / zoom));
        if (selectionWidth < 0) {
            selectionX += selectionWidth;
            selectionWidth = -selectionWidth;
        }
        if (selectionHeight < 0) {
            selectionY += selectionHeight;
            selectionHeight = -selectionHeight;
        }
        int finalSelectionY = selectionY;
        int finalSelectionX = selectionX;
        int finalSelectionWidth = selectionWidth;
        int finalSelectionHeight = selectionHeight;
        if (rectangleSelectionListener.supports(RectangleSelectionListener.ActionKind.CREATE_ANIM_OR_PANEL)) {
            var action1 = new MenuItem(message("create.animation.panel"));
            action1.setOnAction(e -> rectangleSelectionListener.onSelectRegion(RectangleSelectionListener.ActionKind.CREATE_ANIM_OR_PANEL, finalSelectionX, finalSelectionY, finalSelectionWidth, finalSelectionHeight));
            items.add(action1);
        }
        if (rectangleSelectionListener.supports(RectangleSelectionListener.ActionKind.CROP)) {
            var action2 = new MenuItem(message("crop"));
            action2.setOnAction(e -> rectangleSelectionListener.onSelectRegion(RectangleSelectionListener.ActionKind.CROP, finalSelectionX, finalSelectionY, finalSelectionWidth, finalSelectionHeight));
            items.add(action2);
        }
        if (rectangleSelectionListener.supports(RectangleSelectionListener.ActionKind.IMAGEMATH_CROP)) {
            var action3 = new MenuItem(message("imagemath.crop"));
            action3.setOnAction(e -> rectangleSelectionListener.onSelectRegion(RectangleSelectionListener.ActionKind.IMAGEMATH_CROP, finalSelectionX, finalSelectionY, finalSelectionWidth, finalSelectionHeight));
            items.add(action3);
        }
        var action4 = new MenuItem(message("cancel"));
        items.add(action4);
        selectionMenu.show(ZoomableImageView.this, screenX, screenY);
    }

    private void handleScroll(ScrollEvent event) {
        if (event.isControlDown() && !isSelectingRectangle.get()) {
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

    public void setRectangleSelectionListener(RectangleSelectionListener rectangleSelectionListener) {
        this.rectangleSelectionListener = rectangleSelectionListener;
    }

    public void setImagePathForOpeningInExplorer(Path imagePath) {
        this.imagePath = imagePath;
    }

    public void setImage(Image image) {
        imageView.setImage(image);
        imageView.setPreserveRatio(true);
    }

    public void setSolarDisk(Ellipse ellipse) {
        this.solardisk = ellipse;
        this.canFitToCenter.invalidate();
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
        var img = getImage();
        if (img == null) {
            pendingOperations.add(() -> alignWith(other));
            return;
        }
        if (otherImage.getWidth() == img.getWidth() && otherImage.getHeight() == getImage().getHeight()) {
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
        resetZoom(false);
    }

    public void resetZoom(boolean useMax) {
        var image = imageView.getImage();
        if (image == null) {
            zoom = 0;
            return;
        }

        var width = getWidth();
        var height = getHeight();

        if (width == 0 || height == 0) {
            return;
        }

        var widthZoom = width / image.getWidth();
        var heightZoom = height / image.getHeight();

        var newZoom = useMax ? Math.max(widthZoom, heightZoom) : Math.min(widthZoom, heightZoom);

        if (newZoom != zoom) {
            zoom = newZoom;
            triggerOnZoomChanged();
            applyZoom();
        }
        for (var pendingOperation : pendingOperations) {
            pendingOperation.run();
        }
        pendingOperations.clear();
    }

    public double getZoom() {
        return zoom;
    }

    public void fitToCenter() {
        if (!canFitToCenter()) {
            resetZoom(true);
            return;
        }

        var image = imageView.getImage();
        var center = solardisk.center();
        var diameter = solardisk.semiAxis().a() + solardisk.semiAxis().b();
        var width = getWidth();
        var height = getHeight();

        // Calculate zoom level to ensure the solar disk takes 80% of the window width or height, whichever is smaller
        var zoom = Math.min(width / diameter * 0.8, height / diameter * 0.8);
        setZoom(zoom);

        // Calculate the coordinates to center the sun in the viewport
        var viewportWidth = width / zoom;
        var viewportHeight = height / zoom;
        var x = center.a() - viewportWidth / 2;
        var y = center.b() - viewportHeight / 2;

        // Set the scroll values to center the sun
        scrollPane.setHvalue(x / (image.getWidth() - viewportWidth));
        scrollPane.setVvalue(y / (image.getHeight() - viewportHeight));
    }

    public void oneToOneZoomAndCenter() {
        if (!canFitToCenter()) {
            return;
        }

        var image = imageView.getImage();
        var center = solardisk.center();
        var width = getWidth();
        var height = getHeight();

        setZoom(1.0);

        // Calculate the coordinates to center the sun in the viewport
        var x = center.a() - width / 2;
        var y = center.b() - height / 2;

        // Set the scroll values to center the sun
        scrollPane.setHvalue(x / (image.getWidth() - width));
        scrollPane.setVvalue(y / (image.getHeight() - height));
    }

    public boolean canFitToCenter() {
        var image = imageView.getImage();
        if (solardisk == null || image == null) {
            return false;
        }
        return true;
    }

    public ObservableValue<Boolean> canFitToCenterProperty() {
        return canFitToCenter;
    }
}
