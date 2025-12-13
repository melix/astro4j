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

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
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
    private Image spectrumImage;

    /**
     * Creates a reconstruction view with synchronized spectrum and solar image displays.
     *
     * @param solarView the zoomable solar image view
     * @param solarImageData the raw solar image data
     * @param parentWidth the parent container width property for layout binding
     */
    public ReconstructionView(ZoomableImageView solarView, byte[] solarImageData, ReadOnlyDoubleProperty parentWidth) {
        this.spectrumView = new ImageView();
        this.solarView = solarView;
        this.spectrumViewOverlay = new Canvas();
        this.solarViewOverlay = new Canvas();
        this.solarImageData = solarImageData;
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
        solarView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        solarView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        solarView.getScrollPane().hvalueProperty().addListener((unused1, unused2, hvalue) -> {
            spectrumScrollPane.setHvalue(hvalue.doubleValue());
        });
        spectrumViewOverlay.heightProperty().bind(spectrumView.layoutBoundsProperty().map(Bounds::getHeight));
        solarViewOverlay.widthProperty().bind(solarView.layoutBoundsProperty().map(Bounds::getWidth));
        solarViewOverlay.heightProperty().bind(solarView.layoutBoundsProperty().map(Bounds::getHeight));
        solarViewStack.setAlignment(Pos.BASELINE_LEFT);
        spectrumViewStack.setAlignment(Pos.BASELINE_LEFT);
        var box = new VBox();
        var help = new Label(message("recon.view.help"));
        help.setAlignment(Pos.BASELINE_CENTER);
        help.maxWidthProperty().bind(parentWidth);
        box.getChildren().addAll(help, spectrumViewStack);
        setTop(box);
        setCenter(solarViewStack);
        box.setOnContextMenuRequested(event -> {
            var menu = new ContextMenu();
            var save = new MenuItem(message("save.image"));
            save.setOnAction(e -> Platform.runLater(() -> {
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
            menu.show(box, event.getScreenX(), event.getScreenY());
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
