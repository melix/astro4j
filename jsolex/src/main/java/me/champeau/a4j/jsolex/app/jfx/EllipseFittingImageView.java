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

public class EllipseFittingImageView extends ZoomableImageView {
    private final InteractiveEllipseOverlay ellipseOverlay = new InteractiveEllipseOverlay();

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

    public void enableInteractiveEllipseMode(boolean enabled) {
        ellipseOverlay.setActive(enabled);
        if (enabled) {
            ellipseOverlay.getNodes().forEach(Node::toFront);
        }
    }

    public void setInteractiveEllipse(Ellipse ellipse) {
        ellipseOverlay.setEllipse(ellipse);
        ellipseOverlay.getNodes().forEach(Node::toFront);
    }

    public void setOnEllipseChanged(Consumer<Ellipse> listener) {
        ellipseOverlay.setOnEllipseChanged(listener);
    }

    public boolean isInInteractiveEllipseMode() {
        return ellipseOverlay.isActive();
    }
}