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

import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.math.regression.Ellipse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class ZoomableImageView extends HBox {
    private static final double HANDLE_SIZE = 10;

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
    private BiConsumer<? super Integer, ? super Integer> onClickListener;
    private Consumer<? super Double> onZoomChanged;
    private Ellipse solardisk;

    private double zoom = 0;
    private Path imagePath;
    private Pane imagePane;

    // Fields for rectangle selection (model stored in image-space pixels)
    private double selImgX;
    private double selImgY;
    private double selImgW;
    private double selImgH;
    private double moveStartImgX;
    private double moveStartImgY;
    private double moveStartSelX;
    private double moveStartSelY;
    private RectangleSelectionListener.ActionKind singleActionKind;
    private final Rectangle selectionRectangle;
    private final Label selectionLabel;
    private final EnumMap<HandlePos, Rectangle> handles = new EnumMap<>(HandlePos.class);
    private final BooleanProperty isSelectingRectangle = new SimpleBooleanProperty();
    private RectangleSelectionListener rectangleSelectionListener;
    private Pane selectionOverlayHost;
    private Region selectionActionBar;
    private boolean contextMenuEnabled = true;

    private enum HandlePos {
        NW(Cursor.NW_RESIZE), N(Cursor.N_RESIZE), NE(Cursor.NE_RESIZE),
        E(Cursor.E_RESIZE), SE(Cursor.SE_RESIZE), S(Cursor.S_RESIZE),
        SW(Cursor.SW_RESIZE), W(Cursor.W_RESIZE);

        private final Cursor cursor;

        HandlePos(Cursor cursor) {
            this.cursor = cursor;
        }

        boolean affectsLeft() {
            return this == NW || this == W || this == SW;
        }

        boolean affectsRight() {
            return this == NE || this == E || this == SE;
        }

        boolean affectsTop() {
            return this == NW || this == N || this == NE;
        }

        boolean affectsBottom() {
            return this == SW || this == S || this == SE;
        }
    }

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
            if (evt.getButton().equals(MouseButton.PRIMARY)) {
                if (evt.getClickCount() == 2) {
                    var image = imageView.getImage();
                    if (image == null) {
                        return;
                    }
                    double imageX = evt.getX() / zoom;
                    double imageY = evt.getY() / zoom;
                    boolean zoomingIn = zoom != 1.0;
                    if (zoom == 1.0) {
                        zoom = getWidth() / image.getWidth();
                    } else {
                        zoom = 1.0;
                    }
                    applyZoom();
                    triggerOnZoomChanged();
                    if (zoomingIn) {
                        centerViewportOn(imageX, imageY);
                    }
                } else if (evt.getClickCount() == 1 && onClickListener != null) {
                    int imageX = (int) Math.round(evt.getX() / zoom);
                    int imageY = (int) Math.round(evt.getY() / zoom);
                    onClickListener.accept(imageX, imageY);
                }
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

        setOnContextMenuRequested(e -> {
            if (contextMenuEnabled) {
                ctxMenu.show(ZoomableImageView.this, e.getScreenX(), e.getScreenY());
            }
        });

        // Add selection rectangle
        selectionRectangle = new Rectangle();
        selectionRectangle.setFill(Color.rgb(255, 0, 0, 0.3));
        selectionRectangle.setStroke(Color.RED);
        selectionRectangle.setCursor(Cursor.MOVE);
        selectionRectangle.setOnMousePressed(this::handleSelectionMovePressed);
        selectionRectangle.setOnMouseDragged(this::handleSelectionMoveDragged);
        selectionLabel = new Label();
        selectionLabel.setTextFill(Color.RED);
        createHandles();

        var paneChildren = new ArrayList<Node>();
        paneChildren.add(imageView);
        paneChildren.add(selectionRectangle);
        paneChildren.add(selectionLabel);
        paneChildren.addAll(handles.values());
        this.imagePane = new Pane(paneChildren.toArray(new Node[0]));
        disableSelection();
        scrollPane.setContent(imagePane);
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
    }

    private void disableSelection() {
        isSelectingRectangle.set(false);
        selectionRectangle.setVisible(false);
        selectionLabel.setVisible(false);
        setHandlesVisible(false);
        hideActionBar();
    }

    private void addSelectionHandlers() {
        imageView.setOnMouseMoved(this::handleMouseMoved);
        imageView.setOnMouseDragged(this::handleMouseMoved);
    }

    private void createHandles() {
        for (var pos : HandlePos.values()) {
            var handle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
            handle.setFill(Color.WHITE);
            handle.setStroke(Color.RED);
            handle.setCursor(pos.cursor);
            handle.setOnMousePressed(MouseEvent::consume);
            handle.setOnMouseDragged(event -> {
                handleResize(pos, event);
                event.consume();
            });
            handles.put(pos, handle);
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        // Allow subclasses to control mouse event handling
        if (!shouldHandleMouseEvent(event)) {
            return;
        }
        if (onCoordinatesListener != null) {
            onCoordinatesListener.accept(event.getX() / zoom, event.getY() / zoom);
        }
    }

    /**
     * Starts an interactive crop selection with a default rectangle centered on the
     * image. The user can resize and move the selection before confirming the action.
     */
    public void startCropSelection() {
        startSelection(RectangleSelectionListener.ActionKind.CROP);
    }

    /**
     * Starts an interactive selection scoped to a single action, with a default
     * rectangle centered on the image. The user can resize and move the selection
     * before confirming the action.
     *
     * @param kind the action to perform on confirmation
     */
    public void startSelection(RectangleSelectionListener.ActionKind kind) {
        if (rectangleSelectionListener == null || !rectangleSelectionListener.supports(kind)) {
            return;
        }
        var image = imageView.getImage();
        if (image == null) {
            return;
        }
        selImgW = image.getWidth() / 2;
        selImgH = image.getHeight() / 2;
        selImgX = (image.getWidth() - selImgW) / 2;
        selImgY = (image.getHeight() - selImgH) / 2;
        singleActionKind = kind;
        isSelectingRectangle.set(true);
        selectionRectangle.setVisible(true);
        selectionLabel.setVisible(true);
        enterEditMode();
    }

    /**
     * Indicates whether the current selection listener supports the given action.
     *
     * @param kind the action to check
     * @return true if the action is currently available
     */
    public boolean supportsSelectionAction(RectangleSelectionListener.ActionKind kind) {
        return rectangleSelectionListener != null && rectangleSelectionListener.supports(kind);
    }

    /**
     * Returns the localized label for a selection action.
     *
     * @param kind the action
     * @return the localized label
     */
    public static String actionLabel(RectangleSelectionListener.ActionKind kind) {
        return switch (kind) {
            case CROP -> message("crop");
            case CREATE_ANIM_OR_PANEL -> message("create.animation.panel");
            case IMAGEMATH_CROP -> message("imagemath.crop");
            case EXTRACT_SER_FRAMES -> message("extract.ser.frames");
        };
    }

    private void enterEditMode() {
        setHandlesVisible(true);
        renderSelection();
        showActionBar();
    }

    private void handleSelectionMovePressed(MouseEvent event) {
        if (!isSelectingRectangle.get()) {
            return;
        }
        var p = imagePane.sceneToLocal(event.getSceneX(), event.getSceneY());
        moveStartImgX = p.getX() / zoom;
        moveStartImgY = p.getY() / zoom;
        moveStartSelX = selImgX;
        moveStartSelY = selImgY;
        event.consume();
    }

    private void handleSelectionMoveDragged(MouseEvent event) {
        if (!isSelectingRectangle.get()) {
            return;
        }
        var p = imagePane.sceneToLocal(event.getSceneX(), event.getSceneY());
        var dx = p.getX() / zoom - moveStartImgX;
        var dy = p.getY() / zoom - moveStartImgY;
        selImgX = clamp(moveStartSelX + dx, 0, imageWidth() - selImgW);
        selImgY = clamp(moveStartSelY + dy, 0, imageHeight() - selImgH);
        renderSelection();
        event.consume();
    }

    private void handleResize(HandlePos pos, MouseEvent event) {
        var p = imagePane.sceneToLocal(event.getSceneX(), event.getSceneY());
        var imgX = clamp(p.getX() / zoom, 0, imageWidth());
        var imgY = clamp(p.getY() / zoom, 0, imageHeight());
        var left = selImgX;
        var top = selImgY;
        var right = selImgX + selImgW;
        var bottom = selImgY + selImgH;
        if (pos.affectsLeft()) {
            left = imgX;
        }
        if (pos.affectsRight()) {
            right = imgX;
        }
        if (pos.affectsTop()) {
            top = imgY;
        }
        if (pos.affectsBottom()) {
            bottom = imgY;
        }
        selImgX = Math.min(left, right);
        selImgY = Math.min(top, bottom);
        selImgW = Math.abs(right - left);
        selImgH = Math.abs(bottom - top);
        renderSelection();
    }

    private void renderSelection() {
        var px = selImgX * zoom;
        var py = selImgY * zoom;
        var pw = selImgW * zoom;
        var ph = selImgH * zoom;
        selectionRectangle.setX(px);
        selectionRectangle.setY(py);
        selectionRectangle.setWidth(pw);
        selectionRectangle.setHeight(ph);
        selectionLabel.setText(Math.round(selImgW) + "x" + Math.round(selImgH));
        selectionLabel.setLayoutX(px + pw / 2);
        selectionLabel.setLayoutY(py + ph / 2);
        positionHandles(px, py, pw, ph);
    }

    private void positionHandles(double px, double py, double pw, double ph) {
        var cx = px + pw / 2;
        var cy = py + ph / 2;
        var right = px + pw;
        var bottom = py + ph;
        placeHandle(HandlePos.NW, px, py);
        placeHandle(HandlePos.N, cx, py);
        placeHandle(HandlePos.NE, right, py);
        placeHandle(HandlePos.E, right, cy);
        placeHandle(HandlePos.SE, right, bottom);
        placeHandle(HandlePos.S, cx, bottom);
        placeHandle(HandlePos.SW, px, bottom);
        placeHandle(HandlePos.W, px, cy);
    }

    private void placeHandle(HandlePos pos, double cx, double cy) {
        var handle = handles.get(pos);
        handle.setX(cx - HANDLE_SIZE / 2);
        handle.setY(cy - HANDLE_SIZE / 2);
    }

    private void setHandlesVisible(boolean visible) {
        for (var handle : handles.values()) {
            handle.setVisible(visible);
        }
    }

    private void showActionBar() {
        if (selectionOverlayHost == null || rectangleSelectionListener == null) {
            return;
        }
        hideActionBar();
        var bar = new VBox(6);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(8));
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        bar.setStyle("-fx-background-color: rgba(30, 30, 40, 0.92);"
                + " -fx-background-radius: 10;"
                + " -fx-border-color: rgba(255, 255, 255, 0.2);"
                + " -fx-border-radius: 10;");
        addActionButton(bar, singleActionKind, actionLabel(singleActionKind), true);
        var cancel = new Button(message("cancel"));
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(e -> disableSelection());
        bar.getChildren().add(cancel);
        selectionActionBar = bar;
        selectionOverlayHost.getChildren().add(bar);
        StackPane.setAlignment(bar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(bar, new Insets(0, 0, 20, 0));
    }

    private void addActionButton(Pane bar, RectangleSelectionListener.ActionKind kind, String label, boolean primary) {
        if (!rectangleSelectionListener.supports(kind)) {
            return;
        }
        var button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setDefaultButton(primary);
        button.setOnAction(e -> triggerSelectionAction(kind));
        bar.getChildren().add(button);
    }

    private void triggerSelectionAction(RectangleSelectionListener.ActionKind kind) {
        var bounds = currentSelectionBounds();
        disableSelection();
        rectangleSelectionListener.onSelectRegion(kind, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private int[] currentSelectionBounds() {
        var image = imageView.getImage();
        var x = (int) Math.max(0, Math.round(selImgX));
        var y = (int) Math.max(0, Math.round(selImgY));
        var w = (int) Math.round(selImgW);
        var h = (int) Math.round(selImgH);
        if (image != null) {
            w = (int) Math.min(image.getWidth() - x, w);
            h = (int) Math.min(image.getHeight() - y, h);
        }
        return new int[]{x, y, w, h};
    }

    private void hideActionBar() {
        if (selectionActionBar != null && selectionOverlayHost != null) {
            selectionOverlayHost.getChildren().remove(selectionActionBar);
        }
        selectionActionBar = null;
    }

    private double imageWidth() {
        var image = imageView.getImage();
        return image == null ? 0 : image.getWidth();
    }

    private double imageHeight() {
        var image = imageView.getImage();
        return image == null ? 0 : image.getHeight();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
        triggerOnZoomChanged();
        if (isSelectingRectangle.get()) {
            renderSelection();
        }
    }

    private void adjustScrollPane() {
        var boundsInLocal = imageView.getBoundsInLocal();
        scrollPane.setPrefViewportWidth(boundsInLocal.getWidth());
        scrollPane.setPrefViewportHeight(boundsInLocal.getHeight());
    }

    private void centerViewportOn(double imageX, double imageY) {
        var image = imageView.getImage();
        if (image == null) {
            return;
        }
        scrollPane.layout();
        var viewportBounds = scrollPane.getViewportBounds();
        double viewportWidth = viewportBounds.getWidth();
        double viewportHeight = viewportBounds.getHeight();
        double contentWidth = image.getWidth() * zoom;
        double contentHeight = image.getHeight() * zoom;
        double hmax = Math.max(0, contentWidth - viewportWidth);
        double vmax = Math.max(0, contentHeight - viewportHeight);
        if (hmax > 0) {
            double targetX = imageX * zoom - viewportWidth / 2.0;
            scrollPane.setHvalue(Math.max(0, Math.min(1, targetX / hmax)));
        }
        if (vmax > 0) {
            double targetY = imageY * zoom - viewportHeight / 2.0;
            scrollPane.setVvalue(Math.max(0, Math.min(1, targetY / vmax)));
        }
    }

    public void setRectangleSelectionListener(RectangleSelectionListener rectangleSelectionListener) {
        this.rectangleSelectionListener = rectangleSelectionListener;
    }

    /**
     * Sets the pane used to host the floating selection action bar. The bar is added
     * to this pane (expected to be a {@link StackPane}) while a selection is being edited.
     *
     * @param host the overlay host pane
     */
    public void setSelectionOverlayHost(Pane host) {
        this.selectionOverlayHost = host;
    }

    /**
     * Indicates whether a rectangular selection is currently active (being drawn or edited).
     *
     * @return the selection state as an observable value
     */
    public ObservableValue<Boolean> selectingProperty() {
        return isSelectingRectangle;
    }

    public void setImagePathForOpeningInExplorer(Path imagePath) {
        this.imagePath = imagePath;
    }

    /**
     * Enables or disables the right-click context menu.
     *
     * @param enabled whether the context menu should be shown on right-click
     */
    public void setContextMenuEnabled(boolean enabled) {
        this.contextMenuEnabled = enabled;
    }

    /**
     * Indicates whether the backing image file currently exists on disk.
     *
     * @return true if the file can be revealed in the system file explorer
     */
    public boolean canOpenInExplorer() {
        return imagePath != null && Files.exists(imagePath);
    }

    /**
     * Indicates whether the backing image file is currently missing on disk, i.e.
     * whether revealing it in the file explorer should be disabled.
     *
     * @return an observable that is true when the file cannot be revealed
     */
    public ObservableValue<Boolean> cannotOpenInExplorerProperty() {
        return allowFileOpen;
    }

    /**
     * Reveals the backing image file in the system file explorer.
     */
    public void openInExplorer() {
        if (canOpenInExplorer()) {
            ExplorerSupport.openInExplorer(imagePath);
        }
    }

    /**
     * Sets the image to display.
     *
     * @param image the image to display
     */
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

    public void setZoomAroundCenter(double newZoom) {
        var image = imageView.getImage();
        if (image == null) {
            setZoom(newZoom);
            return;
        }
        scrollPane.layout();
        var viewportBounds = scrollPane.getViewportBounds();
        double viewportWidth = viewportBounds.getWidth();
        double viewportHeight = viewportBounds.getHeight();
        double contentWidth = image.getWidth() * zoom;
        double contentHeight = image.getHeight() * zoom;
        double hmax = Math.max(0, contentWidth - viewportWidth);
        double vmax = Math.max(0, contentHeight - viewportHeight);
        double centeringOffsetX = Math.max(0, (viewportWidth - contentWidth) / 2);
        double centeringOffsetY = Math.max(0, (viewportHeight - contentHeight) / 2);
        double centerXInContent = hmax > 0
                ? scrollPane.getHvalue() * hmax + viewportWidth / 2.0
                : viewportWidth / 2.0 - centeringOffsetX;
        double centerYInContent = vmax > 0
                ? scrollPane.getVvalue() * vmax + viewportHeight / 2.0
                : viewportHeight / 2.0 - centeringOffsetY;
        double imageCenterX = centerXInContent / zoom;
        double imageCenterY = centerYInContent / zoom;
        zoom = newZoom;
        applyZoom();
        centerViewportOn(imageCenterX, imageCenterY);
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
                    FxUtils.runLater(() -> {
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

    public void setClickListener(BiConsumer<? super Integer, ? super Integer> listener) {
        this.onClickListener = listener;
    }

    /**
     * Returns the pane that hosts the image view. Children added to it live in
     * the same coordinate space as the displayed (zoomed) image — multiply
     * image-space coordinates by {@link #getZoom()} to position a node.
     */
    public Pane getImagePane() {
        return imagePane;
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
        if (!useMax && newZoom > 1.0) {
            newZoom = 1.0;
        }

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
        if (image.getWidth() <= width && image.getHeight() <= height) {
            zoom = 1.0;
        }
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
        var image = imageView.getImage();
        if (image == null) {
            return;
        }

        var width = getWidth();
        var height = getHeight();

        setZoom(1.0);

        // Center on solar disk if available, otherwise center on image
        double centerX, centerY;
        if (solardisk != null) {
            var center = solardisk.center();
            centerX = center.a();
            centerY = center.b();
        } else {
            centerX = image.getWidth() / 2;
            centerY = image.getHeight() / 2;
        }

        // Calculate the coordinates to center in the viewport
        var x = centerX - width / 2;
        var y = centerY - height / 2;

        // Set the scroll values to center
        scrollPane.setHvalue(x / (image.getWidth() - width));
        scrollPane.setVvalue(y / (image.getHeight() - height));
    }

    /**
     * Returns the underlying image view.
     *
     * @return the image view
     */
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

    /**
     * Determines whether a mouse event should be handled.
     *
     * @param event the mouse event
     * @return true if the event should be handled
     */
    protected boolean shouldHandleMouseEvent(MouseEvent event) {
        return true;
    }

    /**
     * Called when zoom level changes, providing an extension point for subclasses.
     *
     * @param zoom the new zoom level
     */
    protected void onZoomChanged(double zoom) {
        // Extension point for subclasses
    }
}
