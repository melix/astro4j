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
import javafx.application.Platform;
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
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.math.regression.Ellipse;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private ContextMenu rectangleSelectionMenu;

    // Fields for zoom tracking
    private double lastImageX = -1;
    private double lastImageY = -1;
    private double lastMouseScreenX = -1;
    private double lastMouseScreenY = -1;
    private double lastMouseXInViewport = -1;
    private double lastMouseYInViewport = -1;

    public ZoomableImageView() {
        super();
        this.scrollPane = new ScrollPane();
        this.imageView = new ImageView();

        scrollPane.setPannable(true);
        scrollPane.setContent(imageView);

        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScroll);
        imageView.setOnMouseClicked(evt -> {
            if (evt.getButton().equals(MouseButton.PRIMARY) && evt.getClickCount() == 2) {
                if (zoom == 1.0) {
                    zoom = getWidth() / imageView.getImage().getWidth();
                } else {
                    zoom = 1.0;
                }
                applyZoom();
                triggerOnZoomChanged();
            }
        });
        widthProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() > 0 && zoom == 0) {
                if (canFitToCenter()) {
                    fitToCenter();
                } else {
                    resetZoom(false);
                }
            }
        });
        heightProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() > 0 && zoom == 0) {
                if (canFitToCenter()) {
                    fitToCenter();
                } else {
                    resetZoom(false);
                }
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
        // Allow subclasses to control mouse event handling
        if (!shouldHandleMouseEvent(event)) {
            return;
        }
        
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
        // Allow subclasses to control mouse event handling
        if (!shouldHandleMouseEvent(event)) {
            return;
        }
        
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
        // Allow subclasses to control mouse event handling
        if (!shouldHandleMouseEvent(event)) {
            return;
        }
        
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
        if (rectangleSelectionMenu != null && rectangleSelectionMenu.isShowing()) {
            rectangleSelectionMenu.hide();
        }
        rectangleSelectionMenu = new ContextMenu();
        rectangleSelectionMenu.setOnHidden(e -> disableSelection());
        var items = rectangleSelectionMenu.getItems();
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
        if (rectangleSelectionListener.supports(RectangleSelectionListener.ActionKind.EXTRACT_SER_FRAMES)) {
            var action4 = new MenuItem(message("extract.ser.frames"));
            action4.setOnAction(e -> rectangleSelectionListener.onSelectRegion(RectangleSelectionListener.ActionKind.EXTRACT_SER_FRAMES, finalSelectionX, finalSelectionY, finalSelectionWidth, finalSelectionHeight));
            items.add(action4);
        }
        var action5 = new MenuItem(message("cancel"));
        items.add(action5);
        rectangleSelectionMenu.show(ZoomableImageView.this, screenX, screenY);
    }

    private void handleScroll(ScrollEvent event) {
        if (event.isControlDown() && !isSelectingRectangle.get()) {
            double deltaY = event.getDeltaY();
            if (deltaY != 0) {
                var image = imageView.getImage();
                if (image == null) {
                    return;
                }
                double mouseScreenX = event.getScreenX();
                double mouseScreenY = event.getScreenY();

                // Check if mouse position on screen changed - use screen coordinates because
                // local coordinates shift as the scroll position changes
                boolean mouseMovedSignificantly = lastMouseScreenX < 0 ||
                    Math.abs(mouseScreenX - lastMouseScreenX) > 2 ||
                    Math.abs(mouseScreenY - lastMouseScreenY) > 2;

                double zoomFactor = 1.05;
                double newZoom;
                if (deltaY < 0) {
                    newZoom = zoom / zoomFactor;
                } else {
                    newZoom = zoom * zoomFactor;
                }
                newZoom = Math.max(0.1, Math.min(newZoom, 5));

                double imageX;
                double imageY;
                double viewportMouseX;
                double viewportMouseY;
                if (mouseMovedSignificantly) {
                    // Mouse moved - recalculate image coordinates
                    // Use sceneToLocal on imageView to get coordinates directly in image space
                    var pointInImage = imageView.sceneToLocal(event.getSceneX(), event.getSceneY());
                    double fitWidth = imageView.getFitWidth();
                    double fitHeight = imageView.getFitHeight();

                    viewportMouseX = event.getX();
                    viewportMouseY = event.getY();

                    // Convert from scaled image coordinates to original image pixel coordinates
                    double rawImageX;
                    double rawImageY;
                    if (fitWidth > 0 && fitHeight > 0) {
                        rawImageX = pointInImage.getX() * image.getWidth() / fitWidth;
                        rawImageY = pointInImage.getY() * image.getHeight() / fitHeight;
                    } else {
                        rawImageX = pointInImage.getX();
                        rawImageY = pointInImage.getY();
                    }
                    imageX = rawImageX;
                    imageY = rawImageY;

                    // DON'T clamp imageX/Y based on scroll constraints!
                    // We want to track the TRUE image pixel under cursor.
                    // The scroll will be clamped to 0-1 range, and as we zoom in,
                    // eventually the scroll will become achievable and start tracking correctly.

                    // Only clamp to actual image bounds
                    imageX = Math.max(0, Math.min(image.getWidth() - 1, imageX));
                    imageY = Math.max(0, Math.min(image.getHeight() - 1, imageY));

                    // Save for next iteration
                    lastImageX = imageX;
                    lastImageY = imageY;
                    lastMouseScreenX = mouseScreenX;
                    lastMouseScreenY = mouseScreenY;
                    lastMouseXInViewport = viewportMouseX;
                    lastMouseYInViewport = viewportMouseY;
                } else {
                    // Mouse didn't move - reuse all saved coordinates
                    imageX = lastImageX;
                    imageY = lastImageY;
                    viewportMouseX = lastMouseXInViewport;
                    viewportMouseY = lastMouseYInViewport;
                }

                zoom = newZoom;
                triggerOnZoomChanged();
                applyZoom();

                // Force layout to update before calculating scroll positions
                scrollPane.layout();

                // Now calculate and set scroll positions synchronously
                double newContentWidth = image.getWidth() * zoom;
                double newContentHeight = image.getHeight() * zoom;
                var newViewportBounds = scrollPane.getViewportBounds();
                double newViewportWidth = newViewportBounds.getWidth();
                double newViewportHeight = newViewportBounds.getHeight();

                // When content is smaller than viewport, it gets centered.
                // We need to account for this centering when calculating where
                // the image pixel will appear in the viewport.
                double newCenteringOffsetX = Math.max(0, (newViewportWidth - newContentWidth) / 2);
                double newCenteringOffsetY = Math.max(0, (newViewportHeight - newContentHeight) / 2);

                // Position of the target image pixel in content coordinates
                double newMouseXInContent = imageX * zoom;
                double newMouseYInContent = imageY * zoom;

                // Where the target pixel will appear in the viewport, accounting for centering
                // If centered: pixel appears at (newMouseXInContent + centeringOffset)
                // We want it at viewportMouseX, so scroll offset = (newMouseXInContent + centeringOffset) - viewportMouseX
                // But scroll offset must be >= 0, and centering only applies when content < viewport
                double newHmax = Math.max(0, newContentWidth - newViewportWidth);
                double newVmax = Math.max(0, newContentHeight - newViewportHeight);

                double newScrollOffsetX;
                double newScrollOffsetY;
                if (newHmax > 0) {
                    // Content larger than viewport - normal scrolling
                    newScrollOffsetX = newMouseXInContent - viewportMouseX;
                } else {
                    // Content fits in viewport - it will be centered
                    // The pixel will appear at (newMouseXInContent + newCenteringOffsetX) in viewport coords
                    // We can't scroll, so we can only track if the pixel naturally falls under cursor
                    newScrollOffsetX = 0;
                }
                if (newVmax > 0) {
                    newScrollOffsetY = newMouseYInContent - viewportMouseY;
                } else {
                    newScrollOffsetY = 0;
                }

                double rawHvalue = newHmax > 0 ? newScrollOffsetX / newHmax : 0;
                double rawVvalue = newVmax > 0 ? newScrollOffsetY / newVmax : 0;
                double newHvalue = Math.max(0, Math.min(1, rawHvalue));
                double newVvalue = Math.max(0, Math.min(1, rawVvalue));

                // When content fits in viewport or scroll is clamped, the target pixel
                // won't be under the cursor. Adjust tracking to the achievable position.
                if (newHmax <= 0) {
                    // Content centered - the pixel under cursor is determined by centering
                    lastImageX = (viewportMouseX - newCenteringOffsetX) / zoom;
                } else if (rawHvalue < 0 || rawHvalue > 1) {
                    double actualScrollOffsetX = newHvalue * newHmax;
                    lastImageX = (actualScrollOffsetX + viewportMouseX) / zoom;
                }
                if (newVmax <= 0) {
                    lastImageY = (viewportMouseY - newCenteringOffsetY) / zoom;
                } else if (rawVvalue < 0 || rawVvalue > 1) {
                    double actualScrollOffsetY = newVvalue * newVmax;
                    lastImageY = (actualScrollOffsetY + viewportMouseY) / zoom;
                }

                scrollPane.setHvalue(newHvalue);
                scrollPane.setVvalue(newVvalue);
            }
            event.consume();
        }
    }

    private void triggerOnZoomChanged() {
        if (onZoomChanged != null) {
            onZoomChanged.accept(zoom);
        }
        onZoomChanged(zoom);
    }

    private void applyZoom() {
        var image = imageView.getImage();
        imageView.setFitWidth(image.getWidth() * zoom);
        imageView.setFitHeight(image.getHeight() * zoom);
        adjustScrollPane();
        
        // Notify subclasses of zoom change
        onZoomChanged(zoom);
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

    public ScrollPane getScrollPane() {
        return scrollPane;
    }

    public void alignWith(ZoomableImageView other) {
        var otherImage = other.getImage();
        var img = getImage();
        if (img != null) {
            doAlignWith(other, otherImage, img);
        }
    }

    private void doAlignWith(ZoomableImageView other, Image otherImage, Image img) {
        BackgroundOperations.async(() -> {
            if (otherImage.getWidth() == img.getWidth() && otherImage.getHeight() == img.getHeight()) {
                setZoom(other.getZoom());
                var hvalue = other.scrollPane.getHvalue();
                var vvalue = other.scrollPane.getVvalue();
                while (scrollPane.getHvalue() != hvalue || scrollPane.getVvalue() != vvalue) {
                    Platform.runLater(() -> {
                        scrollPane.setHvalue(hvalue);
                        scrollPane.setVvalue(vvalue);
                    });
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
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

    public ImageView getImageView() {
        return imageView;
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

    protected boolean shouldHandleMouseEvent(MouseEvent event) {
        return true;
    }

    protected void onZoomChanged(double zoom) {
        // Extension point for subclasses
    }
}
