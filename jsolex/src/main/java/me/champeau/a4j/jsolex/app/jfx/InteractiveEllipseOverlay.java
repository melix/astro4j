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

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
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

import static javafx.scene.input.ScrollEvent.*;

/**
 * Interactive overlay for manipulating ellipse parameters through visual handles.
 */
public class InteractiveEllipseOverlay {
    private static final double HANDLE_RADIUS = 12.0;
    private static final Color ELLIPSE_COLOR = Color.CYAN;
    private static final Color HANDLE_COLOR = Color.YELLOW;
    private static final Color SELECTED_HANDLE_COLOR = Color.ORANGE;
    private static final double VIEWPORT_MARGIN = 20.0;
    
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
    private ScrollPane scrollPane;
    private double contentOffsetX = 0;
    private double contentOffsetY = 0;
    
    // Rectangle corner positions (in image coordinates)
    private double topLeftX, topLeftY;
    private double topRightX, topRightY;
    private double bottomLeftX, bottomLeftY;
    private double bottomRightX, bottomRightY;
    private double rotHandleX, rotHandleY;
    
    private enum HandleType {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, ROTATION
    }

    /**
     * Creates a new instance with initialized visual elements.
     */
    public InteractiveEllipseOverlay() {
        ellipseShape = new Ellipse();
        ellipseShape.setFill(Color.TRANSPARENT);
        ellipseShape.setStroke(ELLIPSE_COLOR);
        ellipseShape.setStrokeWidth(2.0);
        ellipseShape.setStrokeType(StrokeType.OUTSIDE);
        
        ellipseShape.addEventFilter(SCROLL, event -> {
            var imageView = findImageViewInParent(ellipseShape);
            if (imageView != null) {
                imageView.fireEvent(event.copyFor(event.getSource(), imageView));
            }
        });
        
        cornerHandles = new Circle[4];
        for (var i = 0; i < 4; i++) {
            cornerHandles[i] = createHandle(HANDLE_COLOR);
        }
        
        rectangleLines = new Line[4];
        for (var i = 0; i < 4; i++) {
            rectangleLines[i] = new Line();
            rectangleLines[i].setStroke(Color.GRAY);
            rectangleLines[i].setStrokeWidth(1.0);
            rectangleLines[i].getStrokeDashArray().addAll(5.0, 5.0);
            
            final var line = rectangleLines[i];
            line.addEventFilter(SCROLL, event -> {
                var imageView = findImageViewInParent(line);
                if (imageView != null) {
                    imageView.fireEvent(event.copyFor(event.getSource(), imageView));
                }
            });
        }
        
        rotationHandle = createHandle(Color.LIGHTGREEN);
        
        setupEventHandlers();
        
        setVisible(false);
    }
    
    private Circle createHandle(Color color) {
        var handle = new Circle(HANDLE_RADIUS);
        handle.setFill(color);
        handle.setStroke(Color.BLACK);
        handle.setStrokeWidth(1.0);
        
        handle.addEventFilter(SCROLL, event -> {
            var imageView = findImageViewInParent(handle);
            if (imageView != null) {
                imageView.fireEvent(event.copyFor(event.getSource(), imageView));
            }
        });
        
        return handle;
    }
    
    private Node findImageViewInParent(Node node) {
        var parent = node.getParent();
        if (parent != null) {
            for (var child : ((Pane) parent).getChildren()) {
                if (child instanceof ImageView) {
                    return child;
                }
            }
        }
        return null;
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
        
        var source = (Node) event.getSource();
        var containerCoords = source.getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
        var mouseX = (containerCoords.getX() - contentOffsetX) / zoom;
        var mouseY = (containerCoords.getY() - contentOffsetY) / zoom;
        
        
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
        
        ensureAnchorsAreVisible();
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
        
        ellipseShape.setCenterX(scaledCenterX + contentOffsetX);
        ellipseShape.setCenterY(scaledCenterY + contentOffsetY);
        ellipseShape.setRadiusX(scaledRadiusX);
        ellipseShape.setRadiusY(scaledRadiusY);
        ellipseShape.setRotate(Math.toDegrees(rotation));
        
        cornerHandles[0].setCenterX(topLeftX * zoom + contentOffsetX);
        cornerHandles[0].setCenterY(topLeftY * zoom + contentOffsetY);
        cornerHandles[1].setCenterX(topRightX * zoom + contentOffsetX);
        cornerHandles[1].setCenterY(topRightY * zoom + contentOffsetY);
        cornerHandles[2].setCenterX(bottomLeftX * zoom + contentOffsetX);
        cornerHandles[2].setCenterY(bottomLeftY * zoom + contentOffsetY);
        cornerHandles[3].setCenterX(bottomRightX * zoom + contentOffsetX);
        cornerHandles[3].setCenterY(bottomRightY * zoom + contentOffsetY);
        
        rectangleLines[0].setStartX(topLeftX * zoom + contentOffsetX);
        rectangleLines[0].setStartY(topLeftY * zoom + contentOffsetY);
        rectangleLines[0].setEndX(topRightX * zoom + contentOffsetX);
        rectangleLines[0].setEndY(topRightY * zoom + contentOffsetY);
        
        rectangleLines[1].setStartX(topRightX * zoom + contentOffsetX);
        rectangleLines[1].setStartY(topRightY * zoom + contentOffsetY);
        rectangleLines[1].setEndX(bottomRightX * zoom + contentOffsetX);
        rectangleLines[1].setEndY(bottomRightY * zoom + contentOffsetY);
        
        rectangleLines[2].setStartX(bottomRightX * zoom + contentOffsetX);
        rectangleLines[2].setStartY(bottomRightY * zoom + contentOffsetY);
        rectangleLines[2].setEndX(bottomLeftX * zoom + contentOffsetX);
        rectangleLines[2].setEndY(bottomLeftY * zoom + contentOffsetY);
        
        rectangleLines[3].setStartX(bottomLeftX * zoom + contentOffsetX);
        rectangleLines[3].setStartY(bottomLeftY * zoom + contentOffsetY);
        rectangleLines[3].setEndX(topLeftX * zoom + contentOffsetX);
        rectangleLines[3].setEndY(topLeftY * zoom + contentOffsetY);
        
        rotationHandle.setCenterX(rotHandleX * zoom + contentOffsetX);
        rotationHandle.setCenterY(rotHandleY * zoom + contentOffsetY);
    }

    /**
     * Sets the ellipse to display and manipulate.
     *
     * @param ellipse the ellipse parameters
     */
    public void setEllipse(me.champeau.a4j.math.regression.Ellipse ellipse) {
        this.currentEllipse = ellipse;
        updateHandlePositionsFromEllipse();
        updateVisualElements();
        ensureAnchorsAreVisible();
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

    /**
     * Returns the current ellipse.
     *
     * @return the current ellipse
     */
    public me.champeau.a4j.math.regression.Ellipse getEllipse() {
        return currentEllipse;
    }

    /**
     * Sets the zoom level for display.
     *
     * @param zoom the zoom factor
     */
    public void setZoom(double zoom) {
        this.zoom = zoom;
        updateVisualElements();
        ensureAnchorsAreVisible();
    }

    /**
     * Sets whether the overlay is active and visible.
     *
     * @param active true to activate the overlay
     */
    public void setActive(boolean active) {
        this.isActive = active;
        setVisible(active);
    }

    /**
     * Returns whether the overlay is active.
     *
     * @return true if the overlay is active
     */
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

    /**
     * Sets a listener to be notified when the ellipse changes.
     *
     * @param listener the listener to notify on ellipse changes
     */
    public void setOnEllipseChanged(Consumer<me.champeau.a4j.math.regression.Ellipse> listener) {
        this.onEllipseChanged = listener;
    }
    
    private void notifyEllipseChanged() {
        if (onEllipseChanged != null && currentEllipse != null) {
            onEllipseChanged.accept(currentEllipse);
        }
    }

    /**
     * Returns all visual nodes for adding to a scene graph.
     *
     * @return list of visual nodes
     */
    public List<Node> getNodes() {
        return List.of(
            ellipseShape,
            cornerHandles[0], cornerHandles[1], cornerHandles[2], cornerHandles[3],
            rectangleLines[0], rectangleLines[1], rectangleLines[2], rectangleLines[3],
            rotationHandle
        );
    }

    /**
     * Sets the scroll pane for viewport management.
     *
     * @param scrollPane the scroll pane containing the overlay
     */
    public void setScrollPane(ScrollPane scrollPane) {
        this.scrollPane = scrollPane;
    }
    
    private void ensureAnchorsAreVisible() {
        if (scrollPane == null || scrollPane.getContent() == null) {
            return;
        }
        
        var requiredBounds = calculateRequiredBounds();
        expandViewportToInclude(requiredBounds);
    }
    
    private Bounds calculateRequiredBounds() {
        var scaledPositions = new double[][] {
            {topLeftX * zoom, topLeftY * zoom},
            {topRightX * zoom, topRightY * zoom},
            {bottomLeftX * zoom, bottomLeftY * zoom},
            {bottomRightX * zoom, bottomRightY * zoom},
            {rotHandleX * zoom, rotHandleY * zoom}
        };
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (var pos : scaledPositions) {
            minX = Math.min(minX, pos[0] - HANDLE_RADIUS - VIEWPORT_MARGIN);
            minY = Math.min(minY, pos[1] - HANDLE_RADIUS - VIEWPORT_MARGIN);
            maxX = Math.max(maxX, pos[0] + HANDLE_RADIUS + VIEWPORT_MARGIN);
            maxY = Math.max(maxY, pos[1] + HANDLE_RADIUS + VIEWPORT_MARGIN);
        }
        
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }
    
    private void expandViewportToInclude(Bounds requiredBounds) {
        var content = scrollPane.getContent();
        
        if (content instanceof Pane pane) {
            Node imageView = null;
            for (var child : pane.getChildren()) {
                if (child instanceof ImageView) {
                    imageView = child;
                    break;
                }
            }
            
            if (imageView == null) return;
            
            var currentImageBounds = imageView.getBoundsInLocal();
            
            var expandLeft = Math.max(0, -requiredBounds.getMinX());
            var expandTop = Math.max(0, -requiredBounds.getMinY());
            var expandRight = Math.max(0, requiredBounds.getMaxX() - currentImageBounds.getMaxX());
            var expandBottom = Math.max(0, requiredBounds.getMaxY() - currentImageBounds.getMaxY());
            
            var tolerance = 5.0;
            if (expandLeft < tolerance) expandLeft = 0;
            if (expandTop < tolerance) expandTop = 0;
            if (expandRight < tolerance) expandRight = 0;
            if (expandBottom < tolerance) expandBottom = 0;
            
            var newWidth = currentImageBounds.getWidth() + expandLeft + expandRight;
            var newHeight = currentImageBounds.getHeight() + expandTop + expandBottom;
            
            if (expandLeft > 0 || expandTop > 0 || expandRight > 0 || expandBottom > 0) {
                var currentPaneWidth = pane.getPrefWidth();
                var currentPaneHeight = pane.getPrefHeight();
                
                if (Math.abs(newWidth - currentPaneWidth) < tolerance && 
                    Math.abs(newHeight - currentPaneHeight) < tolerance &&
                    Math.abs(expandLeft - contentOffsetX) < tolerance &&
                    Math.abs(expandTop - contentOffsetY) < tolerance) {
                    return;
                }
                
                pane.setPrefWidth(newWidth);
                pane.setPrefHeight(newHeight);
                pane.setMinWidth(newWidth);
                pane.setMinHeight(newHeight);

                contentOffsetX = expandLeft;
                contentOffsetY = expandTop;
                
                imageView.setLayoutX(expandLeft);
                imageView.setLayoutY(expandTop);
                
                updateVisualElements();
            }
        }
    }
    
}