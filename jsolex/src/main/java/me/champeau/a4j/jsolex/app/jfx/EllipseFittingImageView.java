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

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.function.Consumer;

/**
 * Image view with interactive ellipse fitting support.
 */
public class EllipseFittingImageView extends ZoomableImageView {
    private final PointBasedEllipseOverlay ellipseOverlay = new PointBasedEllipseOverlay();

    /**
     * Creates a new ellipse fitting image view.
     */
    public EllipseFittingImageView() {
        super();
        initializeEllipseOverlay();
    }

    private void initializeEllipseOverlay() {
        var scrollPane = getScrollPane();
        if (scrollPane.getContent() instanceof Pane pane) {
            pane.getChildren().addAll(ellipseOverlay.getNodes());
            ellipseOverlay.getNodes().forEach(Node::toFront);
            ellipseOverlay.setScrollPane(scrollPane);
        }
    }

    @Override
    protected boolean shouldHandleMouseEvent(MouseEvent event) {
        return !isInInteractiveEllipseMode();
    }

    @Override
    protected void onZoomChanged(double zoom) {
        super.onZoomChanged(zoom);
        ellipseOverlay.setZoom(zoom);
    }

    @Override
    public void setImage(Image image) {
        super.setImage(image);
    }

    /**
     * Enables or disables interactive ellipse mode.
     * @param enabled true to enable, false to disable
     */
    public void enableInteractiveEllipseMode(boolean enabled) {
        ellipseOverlay.setActive(enabled);
        if (enabled) {
            ellipseOverlay.getNodes().forEach(Node::toFront);
        }
    }

    /**
     * Sets the ellipse to display in interactive mode.
     * @param ellipse the ellipse to display
     */
    public void setInteractiveEllipse(Ellipse ellipse) {
        ellipseOverlay.setEllipse(ellipse);
        ellipseOverlay.getNodes().forEach(Node::toFront);
    }

    /**
     * Sets a listener for ellipse changes.
     * @param listener the listener to notify when the ellipse changes
     */
    public void setOnEllipseChanged(Consumer<Ellipse> listener) {
        ellipseOverlay.setOnEllipseChanged(listener);
    }

    /**
     * Checks if interactive ellipse mode is active.
     * @return true if in interactive mode
     */
    public boolean isInInteractiveEllipseMode() {
        return ellipseOverlay.isActive();
    }

    /**
     * Sets the opacity of the ellipse overlay.
     * @param opacity the opacity value (0.0-1.0)
     */
    public void setEllipseOpacity(double opacity) {
        ellipseOverlay.setOpacity(opacity);
    }
}