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
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.Cursor;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.math.image.Image;

import java.io.File;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * A view for displaying spectroheliographic reconstruction with synchronized spectrum and solar image views.
 */
public class ReconstructionView extends BorderPane implements WithRootNode {
    private final ImageView spectrumView;
    private final ZoomableImageView solarView;
    private final Canvas spectrumViewOverlay;
    private final Canvas solarViewOverlay;
    private final Semaphore lock = new Semaphore(1);

    private final byte[] solarImageData;
    private final int solarImageWidth;
    private final int solarImageHeight;
    private Image spectrumImage;

    /**
     * Creates a reconstruction view with synchronized spectrum and solar image displays.
     *
     * @param solarView        the zoomable solar image view
     * @param solarImageData   the raw BGRA solar image data ({@code 4 * width * height} bytes)
     * @param solarImageWidth  the width in pixels of the solar buffer
     * @param solarImageHeight the height in pixels of the solar buffer
     */
    public ReconstructionView(ZoomableImageView solarView, byte[] solarImageData, int solarImageWidth, int solarImageHeight) {
        this.spectrumView = new ImageView();
        this.solarView = solarView;
        this.spectrumViewOverlay = new Canvas();
        this.solarViewOverlay = new Canvas();
        this.solarImageData = solarImageData;
        this.solarImageWidth = solarImageWidth;
        this.solarImageHeight = solarImageHeight;
        spectrumView.setPreserveRatio(true);
        var solarViewStack = new StackPane(solarView, solarViewOverlay);
        var spectrumScrollPane = new ScrollPane(spectrumView);
        spectrumScrollPane.setPannable(false);
        spectrumScrollPane.setMouseTransparent(true);
        spectrumScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        spectrumScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        spectrumScrollPane.setFitToWidth(false);
        spectrumScrollPane.maxWidthProperty().bind(solarView.getScrollPane().maxWidthProperty());
        var spectrumViewStack = new StackPane(spectrumScrollPane, spectrumViewOverlay);
        solarViewOverlay.setMouseTransparent(true);
        spectrumViewOverlay.setMouseTransparent(true);
        solarView.getImageView().layoutBoundsProperty().addListener((unused1, unused2, bounds) -> {
            var w = bounds.getWidth();
            spectrumView.setFitWidth(w);
            spectrumViewOverlay.setWidth(w);
        });
        solarView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        solarView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // ZoomableImageView is an HBox: without this, the scroll pane is laid out at its
        // preferred (zoomed content) width and overflows the visible area, making the right
        // edge unreachable. Growing it to fill keeps the viewport at the visible width.
        HBox.setHgrow(solarView.getScrollPane(), Priority.ALWAYS);
        solarView.getScrollPane().hvalueProperty().addListener((unused1, unused2, hvalue) -> {
            spectrumScrollPane.setHvalue(hvalue.doubleValue());
        });
        installDragToPan(solarView);
        installViewportWidthCap(solarView);
        spectrumViewOverlay.heightProperty().bind(spectrumView.layoutBoundsProperty().map(Bounds::getHeight));
        solarViewOverlay.widthProperty().bind(solarView.layoutBoundsProperty().map(Bounds::getWidth));
        solarViewOverlay.heightProperty().bind(solarView.layoutBoundsProperty().map(Bounds::getHeight));
        solarViewStack.setAlignment(Pos.BASELINE_LEFT);
        spectrumViewStack.setAlignment(Pos.TOP_LEFT);
        solarViewStack.setMinSize(0, 0);
        solarView.setMinSize(0, 0);
        setTop(spectrumViewStack);
        setCenter(solarViewStack);
        // Add help overlay to solar view stack
        var helpOverlay = new ImageHelpOverlay(
                message("reconstruction"),
                message("recon.view.help"),
                GeneratedImageKind.RECONSTRUCTION
        );
        helpOverlay.setMouseTransparent(true);
        solarViewStack.getChildren().add(helpOverlay);
        var helpButton = helpOverlay.createStandaloneButton();
        solarViewStack.getChildren().add(helpButton);
        spectrumViewStack.setOnContextMenuRequested(event -> {
            var menu = new ContextMenu();
            var save = new MenuItem(message("save.image"));
            save.setOnAction(e -> FxUtils.runLater(() -> {
                var fileChooser = new FileChooser();
                fileChooser.setTitle(message("save.image"));
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
                var file = fileChooser.showSaveDialog(solarView.getScene().getWindow());
                if (file != null) {
                    if (!file.getName().toLowerCase().endsWith(".png")) {
                        file = new File(file.getAbsolutePath() + ".png");
                    }
                    ImageUtils.writeMonoImage(spectrumImage.width(), spectrumImage.height(), spectrumImage.data(), file, Set.of(ImageFormat.PNG));
                }
            }));
            menu.getItems().add(save);
            menu.show(spectrumViewStack, event.getScreenX(), event.getScreenY());
        });
    }

    /**
     * Installs drag-to-pan on the solar view. A press only records the starting
     * scroll position; the actual panning happens on drag and consumes the event,
     * which both prevents the native scroll-pane panning from running twice and
     * stops a drag from being interpreted as a pixel-selection click. A plain
     * click (no drag) is left untouched so pixel selection keeps working.
     */
    private void installDragToPan(ZoomableImageView solarView) {
        var scrollPane = solarView.getScrollPane();
        var dragAnchor = new double[]{0, 0, 0, 0};
        solarView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.isControlDown()) {
                return;
            }
            dragAnchor[0] = e.getSceneX();
            dragAnchor[1] = e.getSceneY();
            dragAnchor[2] = scrollPane.getHvalue();
            dragAnchor[3] = scrollPane.getVvalue();
        });
        solarView.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!e.isPrimaryButtonDown() || e.isControlDown()) {
                return;
            }
            var content = (Region) scrollPane.getContent();
            var viewport = scrollPane.getViewportBounds();
            var hmax = content.getWidth() - viewport.getWidth();
            var vmax = content.getHeight() - viewport.getHeight();
            if (hmax > 0) {
                var hvalue = dragAnchor[2] - (e.getSceneX() - dragAnchor[0]) / hmax;
                scrollPane.setHvalue(Math.max(0, Math.min(1, hvalue)));
            }
            if (vmax > 0) {
                var vvalue = dragAnchor[3] - (e.getSceneY() - dragAnchor[1]) / vmax;
                scrollPane.setVvalue(Math.max(0, Math.min(1, vvalue)));
            }
            solarView.setCursor(Cursor.MOVE);
            e.consume();
        });
        solarView.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> solarView.setCursor(Cursor.DEFAULT));
    }

    /**
     * Caps the solar scroll pane to the width that is actually on screen, measured in scene
     * coordinates as the distance between the scroll pane's left edge and the right edge of the
     * main tab pane. The reconstruction view's container inflates with the zoomed image and
     * overflows the (fixed) tab pane; without this cap the scroll pane extends past the visible
     * area, so the image's right edge is parked off screen and can never be scrolled into view.
     * Using live geometry avoids depending on a reported sidebar width, which is unreliable.
     */
    private void installViewportWidthCap(ZoomableImageView solarView) {
        var scrollPane = solarView.getScrollPane();
        Runnable update = () -> {
            var scene = getScene();
            if (scene == null || !(scene.lookup("#mainPane") instanceof Region mainPane)) {
                return;
            }
            var left = scrollPane.localToScene(scrollPane.getLayoutBounds()).getMinX();
            var right = mainPane.localToScene(mainPane.getLayoutBounds()).getMaxX();
            var target = right - left;
            if (target > 0 && Math.abs(target - scrollPane.getMaxWidth()) > 0.5) {
                scrollPane.setMaxWidth(target);
            }
        };
        scrollPane.localToSceneTransformProperty().addListener((o, a, b) -> update.run());
        sceneProperty().addListener((o, oldScene, scene) -> {
            if (scene != null) {
                scene.widthProperty().addListener((o2, a2, b2) -> update.run());
                FxUtils.runLater(update);
            }
        });
    }

    /**
     * Returns the lock used for synchronizing access to the reconstruction view.
     *
     * @return the semaphore lock
     */
    public Semaphore getLock() {
        return lock;
    }

    /**
     * Returns the raw solar image data.
     *
     * @return the solar image data array
     */
    public byte[] getSolarImageData() {
        return solarImageData;
    }

    /**
     * Returns the pixel width of the solar buffer (and matching {@code WritableImage}).
     */
    public int getSolarImageWidth() {
        return solarImageWidth;
    }

    /**
     * Returns the pixel height of the solar buffer (and matching {@code WritableImage}).
     */
    public int getSolarImageHeight() {
        return solarImageHeight;
    }

    /**
     * Returns the spectrum image view.
     *
     * @return the spectrum image view
     */
    public ImageView getSpectrumView() {
        return spectrumView;
    }

    /**
     * Returns the zoomable solar image view.
     *
     * @return the solar image view
     */
    public ZoomableImageView getSolarView() {
        return solarView;
    }

    /**
     * Returns the vertical scroll offset of the solar view.
     *
     * @return the Y offset in pixels
     */
    public double getOffsetY() {
        var scrollPane = solarView.getScrollPane();
        var content = (Region) scrollPane.getContent();
        double contentHeight = content.getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        return scrollPane.getVvalue() * (contentHeight - viewportHeight);
    }

    /**
     * Returns the horizontal scroll offset of the solar view.
     *
     * @return the X offset in pixels
     */
    public double getOffsetX() {
        var scrollPane = solarView.getScrollPane();
        var content = (Region) scrollPane.getContent();
        double contentWidth = content.getWidth();
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        return scrollPane.getHvalue() * (contentWidth - viewportWidth);
    }

    /**
     * Returns the canvas overlay for the spectrum view.
     *
     * @return the spectrum view overlay canvas
     */
    public Canvas getSpectrumViewOverlay() {
        return spectrumViewOverlay;
    }

    /**
     * Returns the canvas overlay for the solar view.
     *
     * @return the solar view overlay canvas
     */
    public Canvas getSolarViewOverlay() {
        return solarViewOverlay;
    }

    @Override
    public Node getRoot() {
        return this;
    }

    /**
     * Sets the spectrum image to be displayed.
     *
     * @param spectrumImage the spectrum image
     */
    public void setSpectrumImage(Image spectrumImage) {
        this.spectrumImage = spectrumImage;
    }
}
