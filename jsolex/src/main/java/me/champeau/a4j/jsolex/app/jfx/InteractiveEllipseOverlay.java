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

import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeType;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.EllipseRegression;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class InteractiveEllipseOverlay {
    private static final double HANDLE_RADIUS = 12.0;
    private static final Color ELLIPSE_COLOR = Color.CYAN;
    private static final Color HANDLE_COLOR = Color.YELLOW;
    private static final Color SELECTED_HANDLE_COLOR = Color.ORANGE;
    
    private final Ellipse ellipseShape;
    private final Circle[] cornerHandles;
    private final Line[] rectangleLines;
    private final Circle rotationHandle;
    
    private boolean isActive = false;
    private boolean isDragging = false;
    private HandleType dragHandleType = HandleType.NONE;
    private double zoom = 1.0;
    
    private Consumer<me.champeau.a4j.math.regression.Ellipse> onEllipseChanged;
    private me.champeau.a4j.math.regression.Ellipse currentEllipse;
    
    // Rectangle corner positions (in image coordinates)
    private double topLeftX, topLeftY;
    private double topRightX, topRightY;
    private double bottomLeftX, bottomLeftY;
    private double bottomRightX, bottomRightY;
    private double rotHandleX, rotHandleY;
    
    private enum HandleType {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, ROTATION
    }
    
    public InteractiveEllipseOverlay() {
        // Create ellipse shape
        ellipseShape = new Ellipse();
        ellipseShape.setFill(Color.TRANSPARENT);
        ellipseShape.setStroke(ELLIPSE_COLOR);
        ellipseShape.setStrokeWidth(2.0);
        ellipseShape.setStrokeType(StrokeType.OUTSIDE);
        
        // Create corner handles (4 handles for rectangle corners)
        cornerHandles = new Circle[4];
        for (var i = 0; i < 4; i++) {
            cornerHandles[i] = createHandle(HANDLE_COLOR);
        }
        
        // Create rectangle lines for visual guidance
        rectangleLines = new Line[4];
        for (var i = 0; i < 4; i++) {
            rectangleLines[i] = new Line();
            rectangleLines[i].setStroke(Color.GRAY);
            rectangleLines[i].setStrokeWidth(1.0);
            rectangleLines[i].getStrokeDashArray().addAll(5.0, 5.0);
        }
        
        // Create rotation handle
        rotationHandle = createHandle(Color.LIGHTGREEN);
        
        // Set up event handlers
        setupEventHandlers();
        
        // Initially hide all elements
        setVisible(false);
    }
    
    private Circle createHandle(Color color) {
        var handle = new Circle(HANDLE_RADIUS);
        handle.setFill(color);
        handle.setStroke(Color.BLACK);
        handle.setStrokeWidth(1.0);
        return handle;
    }
    
    private void setupEventHandlers() {
        cornerHandles[0].setOnMousePressed(e -> startDrag(e, HandleType.TOP_LEFT));
        cornerHandles[0].setOnMouseDragged(this::handleDrag);
        cornerHandles[0].setOnMouseReleased(this::endDrag);
        
        cornerHandles[1].setOnMousePressed(e -> startDrag(e, HandleType.TOP_RIGHT));
        cornerHandles[1].setOnMouseDragged(this::handleDrag);
        cornerHandles[1].setOnMouseReleased(this::endDrag);
        
        cornerHandles[2].setOnMousePressed(e -> startDrag(e, HandleType.BOTTOM_LEFT));
        cornerHandles[2].setOnMouseDragged(this::handleDrag);
        cornerHandles[2].setOnMouseReleased(this::endDrag);
        
        cornerHandles[3].setOnMousePressed(e -> startDrag(e, HandleType.BOTTOM_RIGHT));
        cornerHandles[3].setOnMouseDragged(this::handleDrag);
        cornerHandles[3].setOnMouseReleased(this::endDrag);
        
        rotationHandle.setOnMousePressed(e -> startDrag(e, HandleType.ROTATION));
        rotationHandle.setOnMouseDragged(this::handleDrag);
        rotationHandle.setOnMouseReleased(this::endDrag);
    }
    
    private void startDrag(MouseEvent event, HandleType handleType) {
        isDragging = true;
        dragHandleType = handleType;
        highlightHandle(handleType);
        event.consume();
    }
    
    private void handleDrag(MouseEvent event) {
        if (!isDragging || currentEllipse == null) {
            return;
        }
        
        var source = (javafx.scene.Node) event.getSource();
        var containerCoords = source.getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
        var mouseX = containerCoords.getX() / zoom;
        var mouseY = containerCoords.getY() / zoom;
        
        switch (dragHandleType) {
            case TOP_LEFT:
                topLeftX = mouseX;
                topLeftY = mouseY;
                recalculateEllipseFromRectangle();
                break;
                
            case TOP_RIGHT:
                topRightX = mouseX;
                topRightY = mouseY;
                recalculateEllipseFromRectangle();
                break;
                
            case BOTTOM_LEFT:
                bottomLeftX = mouseX;
                bottomLeftY = mouseY;
                recalculateEllipseFromRectangle();
                break;
                
            case BOTTOM_RIGHT:
                bottomRightX = mouseX;
                bottomRightY = mouseY;
                recalculateEllipseFromRectangle();
                break;
                
            case ROTATION:
                rotHandleX = mouseX;
                rotHandleY = mouseY;
                rotateRectangleFromRotationHandle();
                break;
        }
        
        updateVisualElements();
        
        event.consume();
    }
    
    private void recalculateEllipseFromRectangle() {
        var centerX = (topLeftX + topRightX + bottomLeftX + bottomRightX) / 4.0;
        var centerY = (topLeftY + topRightY + bottomLeftY + bottomRightY) / 4.0;

        var width = Math.sqrt(Math.pow(topRightX - topLeftX, 2) + Math.pow(topRightY - topLeftY, 2));
        var height = Math.sqrt(Math.pow(bottomLeftX - topLeftX, 2) + Math.pow(bottomLeftY - topLeftY, 2));

        var rotation = Math.atan2(topRightY - topLeftY, topRightX - topLeftX);

        var radiusX = width / 2.0;
        var radiusY = height / 2.0;
        
        currentEllipse = createEllipseFromParameters(centerX, centerY, radiusX, radiusY, rotation);
        
        updateRotationHandlePosition(centerX, centerY, rotation, Math.max(radiusX, radiusY));
    }
    
    private void rotateRectangleFromRotationHandle() {
        var centerX = (topLeftX + topRightX + bottomLeftX + bottomRightX) / 4.0;
        var centerY = (topLeftY + topRightY + bottomLeftY + bottomRightY) / 4.0;

        var desiredAngle = Math.atan2(rotHandleY - centerY, rotHandleX - centerX);

        var currentAngle = Math.atan2(topRightY - topLeftY, topRightX - topLeftX);

        var deltaAngle = desiredAngle - currentAngle;
        
        rotatePointAroundCenter(centerX, centerY, deltaAngle);
        
        recalculateEllipseFromRectangle();
    }
    
    private void rotatePointAroundCenter(double centerX, double centerY, double deltaAngle) {
        var cos = Math.cos(deltaAngle);
        var sin = Math.sin(deltaAngle);
        
        var tlx = topLeftX - centerX;
        var tly = topLeftY - centerY;
        topLeftX = centerX + tlx * cos - tly * sin;
        topLeftY = centerY + tlx * sin + tly * cos;
        
        var trx = topRightX - centerX;
        var try_ = topRightY - centerY;
        topRightX = centerX + trx * cos - try_ * sin;
        topRightY = centerY + trx * sin + try_ * cos;
        
        var blx = bottomLeftX - centerX;
        var bly = bottomLeftY - centerY;
        bottomLeftX = centerX + blx * cos - bly * sin;
        bottomLeftY = centerY + blx * sin + bly * cos;
        
        var brx = bottomRightX - centerX;
        var bry = bottomRightY - centerY;
        bottomRightX = centerX + brx * cos - bry * sin;
        bottomRightY = centerY + brx * sin + bry * cos;
    }
    
    private void updateRotationHandlePosition(double centerX, double centerY, double rotation, double maxRadius) {
        var rotHandleDistance = maxRadius * 1.3;
        rotHandleX = centerX + rotHandleDistance * Math.cos(rotation);
        rotHandleY = centerY + rotHandleDistance * Math.sin(rotation);
    }
    
    private void endDrag(MouseEvent event) {
        isDragging = false;
        dragHandleType = HandleType.NONE;
        
        resetHandleColors();
        
        notifyEllipseChanged();
        
        event.consume();
    }
    
    private void highlightHandle(HandleType handleType) {
        resetHandleColors();
        
        switch (handleType) {
            case TOP_LEFT:
                cornerHandles[0].setFill(SELECTED_HANDLE_COLOR);
                break;
            case TOP_RIGHT:
                cornerHandles[1].setFill(SELECTED_HANDLE_COLOR);
                break;
            case BOTTOM_LEFT:
                cornerHandles[2].setFill(SELECTED_HANDLE_COLOR);
                break;
            case BOTTOM_RIGHT:
                cornerHandles[3].setFill(SELECTED_HANDLE_COLOR);
                break;
            case ROTATION:
                rotationHandle.setFill(SELECTED_HANDLE_COLOR);
                break;
        }
    }
    
    private void resetHandleColors() {
        rotationHandle.setFill(Color.LIGHTGREEN);
        for (var handle : cornerHandles) {
            handle.setFill(HANDLE_COLOR);
        }
    }
    
    
    private me.champeau.a4j.math.regression.Ellipse createEllipseFromParameters(
            double centerX,
            double centerY,
            double radiusX,
            double radiusY,
            double rotation) {
        
        var numPoints = 36;
        var points = new Point2D[numPoints];
        
        for (var i = 0; i < numPoints; i++) {
            var angle = 2 * Math.PI * i / numPoints;
            var x = radiusX * Math.cos(angle);
            var y = radiusY * Math.sin(angle);
            
            var rotatedX = x * Math.cos(rotation) - y * Math.sin(rotation);
            var rotatedY = x * Math.sin(rotation) + y * Math.cos(rotation);
            
            points[i] = new Point2D(centerX + rotatedX, centerY + rotatedY);
        }
        
        return new EllipseRegression(Arrays.asList(points)).solve();
    }
    
    private void updateVisualElements() {
        if (currentEllipse == null) {
            return;
        }
        
        var center = currentEllipse.center();
        var radiusX = currentEllipse.semiAxis().a();
        var radiusY = currentEllipse.semiAxis().b();
        var rotation = currentEllipse.rotationAngle();
        
        var scaledCenterX = center.a() * zoom;
        var scaledCenterY = center.b() * zoom;
        var scaledRadiusX = radiusX * zoom;
        var scaledRadiusY = radiusY * zoom;
        
        ellipseShape.setCenterX(scaledCenterX);
        ellipseShape.setCenterY(scaledCenterY);
        ellipseShape.setRadiusX(scaledRadiusX);
        ellipseShape.setRadiusY(scaledRadiusY);
        ellipseShape.setRotate(Math.toDegrees(rotation));
        
        cornerHandles[0].setCenterX(topLeftX * zoom);
        cornerHandles[0].setCenterY(topLeftY * zoom);
        cornerHandles[1].setCenterX(topRightX * zoom);
        cornerHandles[1].setCenterY(topRightY * zoom);
        cornerHandles[2].setCenterX(bottomLeftX * zoom);
        cornerHandles[2].setCenterY(bottomLeftY * zoom);
        cornerHandles[3].setCenterX(bottomRightX * zoom);
        cornerHandles[3].setCenterY(bottomRightY * zoom);
        
        rectangleLines[0].setStartX(topLeftX * zoom);
        rectangleLines[0].setStartY(topLeftY * zoom);
        rectangleLines[0].setEndX(topRightX * zoom);
        rectangleLines[0].setEndY(topRightY * zoom);
        
        rectangleLines[1].setStartX(topRightX * zoom);
        rectangleLines[1].setStartY(topRightY * zoom);
        rectangleLines[1].setEndX(bottomRightX * zoom);
        rectangleLines[1].setEndY(bottomRightY * zoom);
        
        rectangleLines[2].setStartX(bottomRightX * zoom);
        rectangleLines[2].setStartY(bottomRightY * zoom);
        rectangleLines[2].setEndX(bottomLeftX * zoom);
        rectangleLines[2].setEndY(bottomLeftY * zoom);
        
        rectangleLines[3].setStartX(bottomLeftX * zoom);
        rectangleLines[3].setStartY(bottomLeftY * zoom);
        rectangleLines[3].setEndX(topLeftX * zoom);
        rectangleLines[3].setEndY(topLeftY * zoom);
        
        rotationHandle.setCenterX(rotHandleX * zoom);
        rotationHandle.setCenterY(rotHandleY * zoom);
    }
    
    public void setEllipse(me.champeau.a4j.math.regression.Ellipse ellipse) {
        this.currentEllipse = ellipse;
        updateHandlePositionsFromEllipse();
        updateVisualElements();
    }
    
    private void updateHandlePositionsFromEllipse() {
        if (currentEllipse == null) {
            return;
        }
        
        var center = currentEllipse.center();
        var radiusX = currentEllipse.semiAxis().a();
        var radiusY = currentEllipse.semiAxis().b();
        var rotation = currentEllipse.rotationAngle();

        var centerX = center.a();
        var centerY = center.b();

        var cosRot = Math.cos(rotation);
        var sinRot = Math.sin(rotation);

        var tlx = -radiusX;
        var tly = -radiusY;
        topLeftX = centerX + tlx * cosRot - tly * sinRot;
        topLeftY = centerY + tlx * sinRot + tly * cosRot;

        var try_ = -radiusY;
        topRightX = centerX + radiusX * cosRot - try_ * sinRot;
        topRightY = centerY + radiusX * sinRot + try_ * cosRot;
        
        var blx = -radiusX;
        bottomLeftX = centerX + blx * cosRot - radiusY * sinRot;
        bottomLeftY = centerY + blx * sinRot + radiusY * cosRot;
        
        bottomRightX = centerX + radiusX * cosRot - radiusY * sinRot;
        bottomRightY = centerY + radiusX * sinRot + radiusY * cosRot;
        
        var rotHandleDistance = Math.max(radiusX, radiusY) * 1.3;
        rotHandleX = centerX + rotHandleDistance * cosRot;
        rotHandleY = centerY + rotHandleDistance * sinRot;
    }
    
    public me.champeau.a4j.math.regression.Ellipse getEllipse() {
        return currentEllipse;
    }
    
    public void setZoom(double zoom) {
        this.zoom = zoom;
        updateVisualElements();
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
        setVisible(active);
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    private void setVisible(boolean visible) {
        ellipseShape.setVisible(visible);
        rotationHandle.setVisible(visible);
        
        for (var handle : cornerHandles) {
            handle.setVisible(visible);
        }
        
        for (var line : rectangleLines) {
            line.setVisible(visible);
        }
    }
    
    public void setOnEllipseChanged(Consumer<me.champeau.a4j.math.regression.Ellipse> listener) {
        this.onEllipseChanged = listener;
    }
    
    private void notifyEllipseChanged() {
        if (onEllipseChanged != null && currentEllipse != null) {
            onEllipseChanged.accept(currentEllipse);
        }
    }
    
    public List<javafx.scene.Node> getNodes() {
        return List.of(
            ellipseShape,
            cornerHandles[0], cornerHandles[1], cornerHandles[2], cornerHandles[3],
            rectangleLines[0], rectangleLines[1], rectangleLines[2], rectangleLines[3],
            rotationHandle
        );
    }
}