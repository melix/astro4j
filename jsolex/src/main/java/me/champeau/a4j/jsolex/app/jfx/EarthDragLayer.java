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

import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.function.BiConsumer;

/**
 * Draggable Earth size reference rendered on top of the zoomable image. The
 * position is stored in image-space pixels and projected to pane coordinates
 * using the host's current zoom factor; the size also follows the zoom.
 */
final class EarthDragLayer extends ImageView {
    private static final double SUN_DIAMETER_KM = 1_391_400;
    private static final double EARTH_DIAMETER_KM = 12_742;
    private static final Image EARTH_IMAGE = new Image(ImageDraw.earthImageStream());

    private final ZoomableImageView host;
    private final BiConsumer<Integer, Integer> onPositionChange;
    private final double earthWidthPixels;
    private final double earthHeightPixels;
    private int imageX;
    private int imageY;
    private double pressSceneX;
    private double pressSceneY;
    private double pressLayoutX;
    private double pressLayoutY;

    EarthDragLayer(ZoomableImageView host, Ellipse ellipse, BiConsumer<Integer, Integer> onPositionChange) {
        super(EARTH_IMAGE);
        this.host = host;
        this.onPositionChange = onPositionChange;
        var semiAxis = ellipse.semiAxis();
        double sunDiameterPixels = semiAxis.a() + semiAxis.b();
        double resolution = sunDiameterPixels / SUN_DIAMETER_KM;
        double baseScale = resolution * EARTH_DIAMETER_KM / EARTH_IMAGE.getWidth();
        this.earthWidthPixels = EARTH_IMAGE.getWidth() * baseScale;
        this.earthHeightPixels = EARTH_IMAGE.getHeight() * baseScale;
        setPreserveRatio(true);
        setCursor(Cursor.MOVE);
        setPickOnBounds(true);
        setOnMousePressed(this::handlePressed);
        setOnMouseDragged(this::handleDragged);
        setOnMouseReleased(e -> onPositionChange.accept(imageX, imageY));
    }

    void place(int imageX, int imageY) {
        this.imageX = imageX;
        this.imageY = imageY;
        applyLayout();
    }

    void applyLayout() {
        double zoom = host.getZoom();
        if (zoom <= 0) {
            zoom = 1.0;
        }
        setFitWidth(earthWidthPixels * zoom);
        setFitHeight(earthHeightPixels * zoom);
        setLayoutX(imageX * zoom);
        setLayoutY(imageY * zoom);
    }

    int getImageX() {
        return imageX;
    }

    int getImageY() {
        return imageY;
    }

    private void handlePressed(MouseEvent e) {
        pressSceneX = e.getSceneX();
        pressSceneY = e.getSceneY();
        pressLayoutX = getLayoutX();
        pressLayoutY = getLayoutY();
        e.consume();
    }

    private void handleDragged(MouseEvent e) {
        double zoom = host.getZoom();
        if (zoom <= 0) {
            zoom = 1.0;
        }
        double newLayoutX = pressLayoutX + (e.getSceneX() - pressSceneX);
        double newLayoutY = pressLayoutY + (e.getSceneY() - pressSceneY);
        imageX = (int) Math.round(newLayoutX / zoom);
        imageY = (int) Math.round(newLayoutY / zoom);
        setLayoutX(newLayoutX);
        setLayoutY(newLayoutY);
        e.consume();
    }
}
