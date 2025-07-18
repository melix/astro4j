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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.concurrent.CompletableFuture;

/**
 * A pane that shows a preview of geometric correction applied to an image
 */
public class GeometryPreviewPane extends BorderPane {
    private final ZoomableImageView previewView;
    private final ProgressIndicator progressIndicator;
    private final Label statusLabel;
    
    private ImageWrapper32 originalImage;
    private Ellipse currentEllipse;
    private CompletableFuture<Void> currentPreview;
    
    public GeometryPreviewPane() {
        // Create image viewer
        previewView = new ZoomableImageView();
        
        // Create progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(50, 50);
        
        // Create status label
        statusLabel = new Label(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "select.ellipse.to.preview"));
        
        // Layout - single preview image that fills the space
        var previewBox = new VBox(5);
        previewBox.getChildren().addAll(
            new Label(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "geometry.corrected.preview")),
            previewView
        );
        
        var statusBox = new HBox(10);
        statusBox.getChildren().addAll(statusLabel, progressIndicator);
        
        setCenter(previewBox);
        setBottom(statusBox);
    }
    
    /**
     * Sets the original image to preview
     */
    public void setOriginalImage(ImageWrapper32 image) {
        this.originalImage = image;
        if (image != null) {
            Platform.runLater(() -> {
                statusLabel.setText("");
                var fxImage = WritableImageSupport.asWritable(image);
                previewView.setImage(fxImage);
            });
        }
    }
    
    /**
     * Updates the ellipse and generates a new preview
     */
    public void updateEllipse(Ellipse ellipse) {
        this.currentEllipse = ellipse;
        generatePreview();
    }
    
    /**
     * Generates the geometry correction preview
     */
    private void generatePreview() {
        if (originalImage == null || currentEllipse == null) {
            return;
        }
        
        // Cancel any existing preview generation
        if (currentPreview != null && !currentPreview.isDone()) {
            currentPreview.cancel(true);
        }
        
        Platform.runLater(() -> {
            progressIndicator.setVisible(true);
            statusLabel.setText(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "generating.preview"));
        });
        
        BackgroundOperations.async(() -> {
            try {
                // Apply geometry correction
                var correctedImage = applyGeometryCorrection(originalImage, currentEllipse);
                
                Platform.runLater(() -> {
                    if (!Thread.currentThread().isInterrupted()) {
                        var fxImage = WritableImageSupport.asWritable(correctedImage);
                        previewView.setImage(fxImage);
                        progressIndicator.setVisible(false);
                        statusLabel.setText("");
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> progressIndicator.setVisible(false));
            }
        });
    }
    
    /**
     * Applies geometry correction to the image using the provided ellipse
     */
    private ImageWrapper32 applyGeometryCorrection(ImageWrapper32 image, Ellipse ellipse) {
        try {
            // Create a simplified geometry corrector
            var theta = ellipse.rotationAngle();
            var m = Math.tan(-theta);
            var semiAxis = ellipse.semiAxis();
            var a = semiAxis.a();
            var b = semiAxis.b();
            var cos = Math.cos(theta);
            var sin = Math.sin(theta);
            var shear = (m * cos * a * a + sin * b * b) / (b * b * cos - a * a * m * sin);
            
            // Apply shear transformation
            var sheared = applyShear(image, shear);
            
            // Apply X/Y ratio correction
            var xyRatio = a / b;
            var corrected = applyXYRatioCorrection(sheared, xyRatio);
            
            return corrected;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply geometry correction", e);
        }
    }
    
    /**
     * Applies shear transformation to correct tilt
     */
    private ImageWrapper32 applyShear(ImageWrapper32 image, double shear) {
        var width = image.width();
        var height = image.height();
        var data = image.data();
        
        // Calculate new dimensions after shear
        var newWidth = (int) (width + Math.abs(shear) * height);
        var newHeight = height;
        
        var newData = new float[newHeight][newWidth];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var newX = (int) Math.round(x + shear * y);
                if (newX >= 0 && newX < newWidth) {
                    newData[y][newX] = data[y][x];
                }
            }
        }
        
        return new ImageWrapper32(newWidth, newHeight, newData, image.metadata());
    }
    
    /**
     * Applies X/Y ratio correction to fix aspect ratio
     */
    private ImageWrapper32 applyXYRatioCorrection(ImageWrapper32 image, double xyRatio) {
        var width = image.width();
        var height = image.height();
        var data = image.data();
        
        // Calculate new dimensions
        var newWidth = (int) (width / xyRatio);
        var newHeight = height;
        
        var newData = new float[newHeight][newWidth];
        
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                var srcX = (int) Math.round(x * xyRatio);
                if (srcX >= 0 && srcX < width) {
                    newData[y][x] = data[y][srcX];
                }
            }
        }
        
        return new ImageWrapper32(newWidth, newHeight, newData, image.metadata());
    }

}