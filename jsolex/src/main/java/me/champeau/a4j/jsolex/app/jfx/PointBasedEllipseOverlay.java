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

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.EllipseRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import static javafx.scene.input.ScrollEvent.SCROLL;

public class PointBasedEllipseOverlay {
    private static final double POINT_RADIUS = 8.0;
    private static final Color ELLIPSE_COLOR = Color.CYAN;
    private static final Color POINT_COLOR = Color.YELLOW;
    private static final Color SELECTED_POINT_COLOR = Color.ORANGE;
    private static final Color PREVIEW_ELLIPSE_COLOR = Color.LIGHTBLUE;
    private static final double CLICK_TOLERANCE = 15.0; // pixels
    private static final double VIEWPORT_MARGIN = 20.0;
    
    private final List<Circle> pointHandles;
    private final List<Point2D> ellipsePoints; // in image coordinates
    private final Ellipse ellipseShape;
    private final Ellipse previewEllipseShape;
    private final Pane overlayPane;
    
    private boolean isActive = false;
    private boolean isDragging = false;
    private int selectedPointIndex = -1;
    private double zoom = 1.0;
    
    private Consumer<me.champeau.a4j.math.regression.Ellipse> onEllipseChanged;
    private me.champeau.a4j.math.regression.Ellipse currentEllipse;
    private ScrollPane scrollPane;
    private double contentOffsetX = 0;
    private double contentOffsetY = 0;
    private Timer debounceTimer;
    private Timeline blinkTimeline;
    
    public PointBasedEllipseOverlay() {
        pointHandles = new ArrayList<>();
        ellipsePoints = new ArrayList<>();
        
        ellipseShape = createEllipseShape(ELLIPSE_COLOR, 2.0);
        ellipseShape.setOpacity(0.7);
        previewEllipseShape = createEllipseShape(PREVIEW_ELLIPSE_COLOR, 1.0);
        previewEllipseShape.getStrokeDashArray().addAll(5.0, 5.0);
        
        overlayPane = new Pane();
        overlayPane.getChildren().addAll(previewEllipseShape, ellipseShape);
        
        setupBlinkAnimation();
        setupEventHandlers();
        setVisible(false);
    }
    
    private Ellipse createEllipseShape(Color color, double strokeWidth) {
        var ellipse = new Ellipse();
        ellipse.setFill(Color.TRANSPARENT);
        ellipse.setStroke(color);
        ellipse.setStrokeWidth(strokeWidth);
        ellipse.setStrokeType(StrokeType.OUTSIDE);
        
        ellipse.addEventFilter(SCROLL, event -> {
            var imageView = findImageViewInParent(ellipse);
            if (imageView != null) {
                imageView.fireEvent(event.copyFor(event.getSource(), imageView));
            }
        });
        
        return ellipse;
    }
    
    private void setupEventHandlers() {
        overlayPane.setOnMouseClicked(this::handleMouseClick);
        overlayPane.setOnMousePressed(this::handleMousePressed);
        overlayPane.setOnMouseDragged(this::handleMouseDragged);
        overlayPane.setOnMouseReleased(this::handleMouseReleased);
    }
    
    private void handleMouseClick(MouseEvent event) {
        if (!isActive || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        
        var clickPoint = getImageCoordinates(event.getX(), event.getY());
        
        // Check if clicking on existing point to remove it (right-click or ctrl+click)
        if (event.isControlDown() || event.getButton() == MouseButton.SECONDARY) {
            var pointIndex = findNearestPoint(clickPoint.x(), clickPoint.y());
            if (pointIndex >= 0) {
                removePoint(pointIndex);
                event.consume();
                return;
            }
        }
        
        // Check if clicking near existing point to select/move it
        var nearestIndex = findNearestPoint(clickPoint.x(), clickPoint.y());
        if (nearestIndex >= 0) {
            selectedPointIndex = nearestIndex;
            highlightPoint(nearestIndex);
        } else {
            // Add new point
            addPoint(clickPoint.x(), clickPoint.y());
        }
        
        event.consume();
    }
    
    private void handleMousePressed(MouseEvent event) {
        if (!isActive || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        
        var clickPoint = getImageCoordinates(event.getX(), event.getY());
        var nearestIndex = findNearestPoint(clickPoint.x(), clickPoint.y());
        
        if (nearestIndex >= 0) {
            selectedPointIndex = nearestIndex;
            isDragging = true;
            highlightPoint(nearestIndex);
        }
        
        event.consume();
    }
    
    private void handleMouseDragged(MouseEvent event) {
        if (!isActive || !isDragging || selectedPointIndex < 0) {
            return;
        }
        
        var newPoint = getImageCoordinates(event.getX(), event.getY());
        updatePoint(selectedPointIndex, newPoint.x(), newPoint.y());
        
        event.consume();
    }
    
    private void handleMouseReleased(MouseEvent event) {
        if (isDragging) {
            isDragging = false;
            selectedPointIndex = -1;
            resetPointColors();
            notifyEllipseChanged();
            ensurePointsAreVisible();
        }
        event.consume();
    }
    
    private Point2D getImageCoordinates(double sceneX, double sceneY) {
        var imageX = (sceneX - contentOffsetX) / zoom;
        var imageY = (sceneY - contentOffsetY) / zoom;
        return new Point2D(imageX, imageY);
    }
    
    private int findNearestPoint(double x, double y) {
        double minDistance = CLICK_TOLERANCE / zoom; // Adjust tolerance for zoom
        int nearestIndex = -1;
        
        for (int i = 0; i < ellipsePoints.size(); i++) {
            var point = ellipsePoints.get(i);
            var distance = Math.sqrt(Math.pow(point.x() - x, 2) + Math.pow(point.y() - y, 2));
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        
        return nearestIndex;
    }
    
    private void addPoint(double x, double y) {
        ellipsePoints.add(new Point2D(x, y));
        
        var handle = createPointHandle();
        pointHandles.add(handle);
        overlayPane.getChildren().add(handle);
        
        updateVisualElements();
        updateEllipseImmediate();
        ensurePointsAreVisible();
    }
    
    private void addPointWithoutUpdate(double x, double y) {
        ellipsePoints.add(new Point2D(x, y));
        
        var handle = createPointHandle();
        pointHandles.add(handle);
        overlayPane.getChildren().add(handle);
    }
    
    private void removePoint(int index) {
        if (index < 0 || index >= ellipsePoints.size()) {
            return;
        }
        
        ellipsePoints.remove(index);
        var handle = pointHandles.remove(index);
        overlayPane.getChildren().remove(handle);
        
        updateVisualElements();
        updateEllipseImmediate();
    }
    
    private void updatePoint(int index, double x, double y) {
        if (index < 0 || index >= ellipsePoints.size()) {
            return;
        }
        
        ellipsePoints.set(index, new Point2D(x, y));
        updateVisualElements();
        updateEllipseWithoutNotification();
    }
    
    private Circle createPointHandle() {
        var handle = new Circle(POINT_RADIUS);
        handle.setFill(POINT_COLOR);
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
    
    private void setupBlinkAnimation() {
        blinkTimeline = new Timeline(
            new KeyFrame(Duration.millis(800), e -> ellipseShape.setOpacity(0.3)),
            new KeyFrame(Duration.millis(1600), e -> ellipseShape.setOpacity(0.7))
        );
        blinkTimeline.setCycleCount(Timeline.INDEFINITE);
    }
    
    private void updateEllipse() {
        if (ellipsePoints.size() >= 3) {
            try {
                var regression = new EllipseRegression(ellipsePoints);
                currentEllipse = regression.solve();
                updateEllipseShape(ellipseShape, currentEllipse);
                previewEllipseShape.setVisible(false);
                ellipseShape.setVisible(true);
                notifyEllipseChanged();
            } catch (Exception e) {
                // If ellipse fitting fails, show preview shape
                currentEllipse = null;
                ellipseShape.setVisible(false);
                updatePreviewEllipse();
            }
        } else {
            // Not enough points for ellipse, show preview if we have points
            currentEllipse = null;
            ellipseShape.setVisible(false);
            updatePreviewEllipse();
        }
    }
    
    private void updateEllipseImmediate() {
        updateEllipse();
    }
    
    private void updateEllipseWithoutNotification() {
        if (ellipsePoints.size() >= 3) {
            try {
                var regression = new EllipseRegression(ellipsePoints);
                currentEllipse = regression.solve();
                updateEllipseShape(ellipseShape, currentEllipse);
                previewEllipseShape.setVisible(false);
                ellipseShape.setVisible(true);
            } catch (Exception e) {
                currentEllipse = null;
                ellipseShape.setVisible(false);
                updatePreviewEllipse();
            }
        } else {
            currentEllipse = null;
            ellipseShape.setVisible(false);
            updatePreviewEllipse();
        }
    }
    
    private void updatePreviewEllipse() {
        if (ellipsePoints.size() < 2) {
            previewEllipseShape.setVisible(false);
            return;
        }
        
        // Show a simple circle preview based on first 2-3 points
        var center = calculateCentroid();
        var avgRadius = calculateAverageRadius(center);
        
        var scaledCenterX = center.x() * zoom + contentOffsetX;
        var scaledCenterY = center.y() * zoom + contentOffsetY;
        var scaledRadius = avgRadius * zoom;
        
        previewEllipseShape.setCenterX(scaledCenterX);
        previewEllipseShape.setCenterY(scaledCenterY);
        previewEllipseShape.setRadiusX(scaledRadius);
        previewEllipseShape.setRadiusY(scaledRadius);
        previewEllipseShape.setRotate(0);
        previewEllipseShape.setVisible(true);
    }
    
    private Point2D calculateCentroid() {
        if (ellipsePoints.isEmpty()) {
            return new Point2D(0, 0);
        }
        
        var sumX = ellipsePoints.stream().mapToDouble(Point2D::x).sum();
        var sumY = ellipsePoints.stream().mapToDouble(Point2D::y).sum();
        
        return new Point2D(sumX / ellipsePoints.size(), sumY / ellipsePoints.size());
    }
    
    private double calculateAverageRadius(Point2D center) {
        if (ellipsePoints.isEmpty()) {
            return 100.0;
        }
        
        return ellipsePoints.stream()
            .mapToDouble(p -> Math.sqrt(Math.pow(p.x() - center.x(), 2) + Math.pow(p.y() - center.y(), 2)))
            .average()
            .orElse(100.0);
    }
    
    private void updateEllipseShape(Ellipse shape, me.champeau.a4j.math.regression.Ellipse ellipse) {
        if (ellipse == null) {
            shape.setVisible(false);
            return;
        }
        
        var center = ellipse.center();
        var radiusX = ellipse.semiAxis().a();
        var radiusY = ellipse.semiAxis().b();
        var rotation = ellipse.rotationAngle();
        
        var scaledCenterX = center.a() * zoom + contentOffsetX;
        var scaledCenterY = center.b() * zoom + contentOffsetY;
        var scaledRadiusX = radiusX * zoom;
        var scaledRadiusY = radiusY * zoom;
        
        shape.setCenterX(scaledCenterX);
        shape.setCenterY(scaledCenterY);
        shape.setRadiusX(scaledRadiusX);
        shape.setRadiusY(scaledRadiusY);
        shape.setRotate(Math.toDegrees(rotation));
        shape.setVisible(true);
    }
    
    private void updateVisualElements() {
        // Update point handles
        for (int i = 0; i < pointHandles.size(); i++) {
            if (i < ellipsePoints.size()) {
                var point = ellipsePoints.get(i);
                var handle = pointHandles.get(i);
                
                handle.setCenterX(point.x() * zoom + contentOffsetX);
                handle.setCenterY(point.y() * zoom + contentOffsetY);
                handle.setVisible(true);
            }
        }
        
        // Update ellipse shapes
        if (currentEllipse != null) {
            updateEllipseShape(ellipseShape, currentEllipse);
        } else {
            updatePreviewEllipse();
        }
    }
    
    private void highlightPoint(int index) {
        resetPointColors();
        if (index >= 0 && index < pointHandles.size()) {
            pointHandles.get(index).setFill(SELECTED_POINT_COLOR);
        }
    }
    
    private void resetPointColors() {
        for (var handle : pointHandles) {
            handle.setFill(POINT_COLOR);
        }
    }
    
    private Node findImageViewInParent(Node node) {
        var parent = node.getParent();
        while (parent != null) {
            if (parent instanceof Pane pane) {
                for (var child : pane.getChildren()) {
                    if (child instanceof ImageView) {
                        return child;
                    }
                }
            }
            parent = parent.getParent();
        }
        return null;
    }
    
    private void notifyEllipseChanged() {
        if (onEllipseChanged != null && currentEllipse != null) {
            onEllipseChanged.accept(currentEllipse);
        }
    }
    
    public void setEllipse(me.champeau.a4j.math.regression.Ellipse ellipse) {
        // Clear existing points and create new ones based on the ellipse
        clearPoints();
        
        if (ellipse != null) {
            this.currentEllipse = ellipse;
            
            // Generate points around the ellipse perimeter for editing
            var center = ellipse.center();
            var radiusX = ellipse.semiAxis().a();
            var radiusY = ellipse.semiAxis().b();
            var rotation = ellipse.rotationAngle();
            
            // Create 8 points around the ellipse
            var numPoints = 8;
            for (int i = 0; i < numPoints; i++) {
                var angle = 2 * Math.PI * i / numPoints;
                var x = radiusX * Math.cos(angle);
                var y = radiusY * Math.sin(angle);
                
                // Apply rotation
                var rotatedX = x * Math.cos(rotation) - y * Math.sin(rotation);
                var rotatedY = x * Math.sin(rotation) + y * Math.cos(rotation);
                
                // Translate to center
                var finalX = center.a() + rotatedX;
                var finalY = center.b() + rotatedY;
                
                addPointWithoutUpdate(finalX, finalY);
            }
            
            // Now update everything once with the correct ellipse
            updateVisualElements();
            updateEllipseShape(ellipseShape, currentEllipse);
            previewEllipseShape.setVisible(false);
            ellipseShape.setVisible(true);
            notifyEllipseChanged();
        }
        
        ensurePointsAreVisible();
    }
    
    private void clearPoints() {
        ellipsePoints.clear();
        for (var handle : pointHandles) {
            overlayPane.getChildren().remove(handle);
        }
        pointHandles.clear();
    }
    
    public me.champeau.a4j.math.regression.Ellipse getEllipse() {
        return currentEllipse;
    }
    
    public boolean hasEnoughPoints() {
        return ellipsePoints.size() >= 3;
    }
    
    public int getPointCount() {
        return ellipsePoints.size();
    }
    
    public void setZoom(double zoom) {
        this.zoom = zoom;
        updateVisualElements();
        ensurePointsAreVisible();
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
        setVisible(active);
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    private void setVisible(boolean visible) {
        overlayPane.setVisible(visible);
    }
    
    public void setOnEllipseChanged(Consumer<me.champeau.a4j.math.regression.Ellipse> listener) {
        this.onEllipseChanged = listener;
    }
    
    public List<Node> getNodes() {
        return List.of(overlayPane);
    }
    
    public void setScrollPane(ScrollPane scrollPane) {
        this.scrollPane = scrollPane;
    }
    
    public void setOpacity(double opacity) {
        ellipseShape.setOpacity(opacity);
        previewEllipseShape.setOpacity(opacity);
    }
    
    private void ensurePointsAreVisible() {
        if (scrollPane == null || scrollPane.getContent() == null || ellipsePoints.isEmpty()) {
            return;
        }
        
        var requiredBounds = calculateRequiredBounds();
        expandViewportToInclude(requiredBounds);
    }
    
    private Bounds calculateRequiredBounds() {
        if (ellipsePoints.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0);
        }
        
        var minX = ellipsePoints.stream().mapToDouble(Point2D::x).min().orElse(0) * zoom;
        var minY = ellipsePoints.stream().mapToDouble(Point2D::y).min().orElse(0) * zoom;
        var maxX = ellipsePoints.stream().mapToDouble(Point2D::x).max().orElse(0) * zoom;
        var maxY = ellipsePoints.stream().mapToDouble(Point2D::y).max().orElse(0) * zoom;
        
        minX -= POINT_RADIUS + VIEWPORT_MARGIN;
        minY -= POINT_RADIUS + VIEWPORT_MARGIN;
        maxX += POINT_RADIUS + VIEWPORT_MARGIN;
        maxY += POINT_RADIUS + VIEWPORT_MARGIN;
        
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