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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CoordinateMapView extends Canvas {
    private final static Logger LOGGER = LoggerFactory.getLogger(CoordinateMapView.class);

    private static final double MAP_WIDTH = 400;
    private static final double MAP_HEIGHT = 200;
    private static final String WORLD_MAP_RESOURCE = "/me/champeau/a4j/jsolex/app/world-map-nasa-topo.jpg";

    private final DoubleProperty latitude = new SimpleDoubleProperty();
    private final DoubleProperty longitude = new SimpleDoubleProperty();
    private Image worldMapImage;

    private double zoomLevel = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double lastMouseX;
    private double lastMouseY;
    private boolean dragging = false;

    CoordinateMapView() {
        super(MAP_WIDTH, MAP_HEIGHT);
        loadWorldMap();
        setupListeners();
        setupMouseHandlers();
        drawMap();
    }

    private void loadWorldMap() {
        try {
            var imageStream = getClass().getResourceAsStream(WORLD_MAP_RESOURCE);
            if (imageStream != null) {
                worldMapImage = new Image(imageStream);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load world map image: " + e.getMessage());
        }
    }

    private void setupListeners() {
        latitude.addListener((obs, oldVal, newVal) -> drawMap());
        longitude.addListener((obs, oldVal, newVal) -> drawMap());
    }

    private void setupMouseHandlers() {
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        setOnMouseClicked(this::handleMouseClicked);
        setOnScroll(this::handleScroll);
    }

    private void handleMousePressed(MouseEvent event) {
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        dragging = false;
    }

    private void handleMouseDragged(MouseEvent event) {
        dragging = true;
        var deltaX = event.getX() - lastMouseX;
        var deltaY = event.getY() - lastMouseY;

        offsetX += deltaX;
        offsetY += deltaY;

        constrainOffsets();

        lastMouseX = event.getX();
        lastMouseY = event.getY();

        drawMap();
    }

    private void handleMouseReleased(MouseEvent event) {
    }

    private void handleMouseClicked(MouseEvent event) {
        if (!dragging && event.getClickCount() == 1) {
            var worldX = (event.getX() - offsetX) / zoomLevel;
            var worldY = (event.getY() - offsetY) / zoomLevel;

            var longitude = (worldX / getWidth()) * 360 - 180;
            var latitude = 90 - (worldY / getHeight()) * 180;

            latitude = Math.max(-90, Math.min(90, latitude));
            longitude = Math.max(-180, Math.min(180, longitude));

            this.latitude.set(latitude);
            this.longitude.set(longitude);
        }
    }

    private void handleScroll(ScrollEvent event) {
        var mouseX = event.getX();
        var mouseY = event.getY();

        var oldZoom = zoomLevel;
        var zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
        zoomLevel *= zoomFactor;
        zoomLevel = Math.max(0.5, Math.min(50.0, zoomLevel));

        var actualZoomFactor = zoomLevel / oldZoom;

        offsetX = mouseX - (mouseX - offsetX) * actualZoomFactor;
        offsetY = mouseY - (mouseY - offsetY) * actualZoomFactor;

        constrainOffsets();
        drawMap();
        event.consume();
    }

    private void constrainOffsets() {
        var scaledWidth = getWidth() * zoomLevel;
        var scaledHeight = getHeight() * zoomLevel;

        var maxOffsetX = scaledWidth - getWidth();
        var maxOffsetY = scaledHeight - getHeight();

        offsetX = Math.max(-maxOffsetX, Math.min(0, offsetX));
        offsetY = Math.max(-maxOffsetY, Math.min(0, offsetY));
    }

    private void drawMap() {
        var gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(zoomLevel, zoomLevel);

        if (worldMapImage != null) {
            gc.drawImage(worldMapImage, 0, 0, getWidth(), getHeight());
        } else {
            drawSimpleGrid(gc);
        }

        drawGridOverlay(gc);

        gc.restore();

        drawCoordinateIndicator(gc);
        drawZoomInfo(gc);
    }

    private void drawZoomInfo(GraphicsContext gc) {
        var zoomText = String.format("%.1fx", zoomLevel);
        gc.setFill(Color.BLACK);
        gc.fillRoundRect(5, 5, 40, 20, 5, 5);
        gc.setFill(Color.WHITE);
        gc.fillText(zoomText, 10, 18);
    }

    public void resetZoom() {
        zoomLevel = 1.0;
        offsetX = 0.0;
        offsetY = 0.0;
        drawMap();
    }

    private void drawSimpleGrid(GraphicsContext gc) {
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.setStroke(Color.GRAY);
        gc.setLineWidth(0.5);

        for (int lat = -60; lat <= 60; lat += 30) {
            var y = latitudeToY(lat);
            gc.strokeLine(0, y, getWidth(), y);
        }

        for (int lon = -120; lon <= 120; lon += 60) {
            var x = longitudeToX(lon);
            gc.strokeLine(x, 0, x, getHeight());
        }

        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(1);
        gc.strokeLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
        gc.strokeLine(getWidth() / 2, 0, getWidth() / 2, getHeight());

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);
        gc.strokeRect(0, 0, getWidth(), getHeight());
    }

    private void drawGridOverlay(GraphicsContext gc) {
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(0.5);
        gc.setGlobalAlpha(0.7);

        for (int lat = -60; lat <= 60; lat += 30) {
            var y = latitudeToY(lat);
            gc.strokeLine(0, y, getWidth(), y);
        }

        for (int lon = -120; lon <= 120; lon += 60) {
            var x = longitudeToX(lon);
            gc.strokeLine(x, 0, x, getHeight());
        }

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
        gc.strokeLine(getWidth() / 2, 0, getWidth() / 2, getHeight());

        gc.setGlobalAlpha(1.0);
    }

    private void drawCoordinateIndicator(GraphicsContext gc) {
        var lat = latitude.get();
        var lon = longitude.get();

        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return;
        }

        var worldX = longitudeToX(lon);
        var worldY = latitudeToY(lat);

        var screenX = worldX * zoomLevel + offsetX;
        var screenY = worldY * zoomLevel + offsetY;

        if (screenX < -10 || screenX > getWidth() + 10 || screenY < -10 || screenY > getHeight() + 10) {
            return;
        }

        gc.setFill(Color.BLACK);
        gc.fillOval(screenX - 6, screenY - 6, 14, 14);

        gc.setFill(Color.RED);
        gc.fillOval(screenX - 5, screenY - 5, 12, 12);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(screenX - 5, screenY - 5, 12, 12);

        gc.setFill(Color.WHITE);
        gc.fillOval(screenX - 1.5, screenY - 1.5, 3, 3);
    }

    private double longitudeToX(double longitude) {
        return ((longitude + 180) / 360) * getWidth();
    }

    private double latitudeToY(double latitude) {
        return ((90 - latitude) / 180) * getHeight();
    }

    public DoubleProperty latitudeProperty() {
        return latitude;
    }

    public DoubleProperty longitudeProperty() {
        return longitude;
    }

    public void setCoordinates(double latitude, double longitude) {
        this.latitude.set(latitude);
        this.longitude.set(longitude);
    }
}