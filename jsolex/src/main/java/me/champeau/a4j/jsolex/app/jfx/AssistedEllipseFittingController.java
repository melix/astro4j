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
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Controller for the assisted ellipse fitting dialog
 */
public class AssistedEllipseFittingController {

    @FXML
    private VBox editContent;
    
    @FXML
    private VBox previewContent;

    @FXML
    private Button resetButton;
    
    @FXML
    private Button applyButton;
    
    @FXML
    private Button cancelButton;
    
    private EllipseFittingImageView imageView;
    private GeometryPreviewPane previewPane;
    private Stage stage;
    private ImageWrapper32 originalImage;
    private Ellipse initialEllipse;
    private Ellipse currentEllipse;
    private Consumer<Ellipse> onEllipseApplied;
    private boolean applied = false;
    
    @FXML
    private void initialize() {
        setupImageView();
        setupPreviewPane();
        setupEventHandlers();
        applyButton.setDisable(true);
    }
    
    private void setupImageView() {
        imageView = new EllipseFittingImageView();
        imageView.setOnEllipseChanged(this::onEllipseChanged);
        editContent.getChildren().add(imageView);
    }
    
    private void setupPreviewPane() {
        previewPane = new GeometryPreviewPane();
        previewContent.getChildren().add(previewPane);
    }
    
    private void setupEventHandlers() {
        resetButton.setOnAction(_ -> resetEllipse());
        applyButton.setOnAction(_ -> applyEllipse());
        cancelButton.setOnAction(_ -> cancel());
    }

    /**
     * Sets up the dialog with the provided image and initial ellipse
     */
    public void setup(ImageWrapper32 source, Ellipse initialEllipse) {
        var image = source.copy();
        LinearStrechingStrategy.DEFAULT.stretch(image);
        this.originalImage = image;
        this.initialEllipse = initialEllipse;
        this.currentEllipse = initialEllipse;
        
        Platform.runLater(() -> {
            var fxImage = WritableImageSupport.asWritable(image);
            imageView.setImage(fxImage);
            
            previewPane.setOriginalImage(image);
            
            imageView.enableInteractiveEllipseMode(true);
            
            if (initialEllipse != null) {
                imageView.setInteractiveEllipse(initialEllipse);
                enableButtons();
                updatePreview();
            } else {
                createDefaultEllipse();
            }
        });
    }
    
    /**
     * Creates a default ellipse at the center of the image
     */
    private void createDefaultEllipse() {
        if (originalImage == null) return;
        
        var width = originalImage.width();
        var height = originalImage.height();
        var centerX = width / 2.0;
        var centerY = height / 2.0;
        var radiusX = Math.min(width, height) * 0.4;
        var radiusY = radiusX * 0.9; // Slightly elliptical
        
        var defaultEllipse = createEllipseFromParameters(centerX, centerY, radiusX, radiusY);
        
        imageView.setInteractiveEllipse(defaultEllipse);
        this.currentEllipse = defaultEllipse;
        enableButtons();
        updatePreview();
    }
    
    /**
     * Creates an ellipse from geometric parameters
     */
    private Ellipse createEllipseFromParameters(double centerX,
                                                double centerY,
                                                double radiusX,
                                                double radiusY) {
        var numPoints = 36;
        var points = new Point2D[numPoints];
        
        for (var i = 0; i < numPoints; i++) {
            var angle = 2 * Math.PI * i / numPoints;
            var x = radiusX * Math.cos(angle);
            var y = radiusY * Math.sin(angle);
            
            var rotatedX = x * Math.cos(0) - y * Math.sin(0);
            var rotatedY = x * Math.sin(0) + y * Math.cos(0);
            
            var finalX = centerX + rotatedX;
            var finalY = centerY + rotatedY;
            
            points[i] = new Point2D(finalX, finalY);
        }
        
        return new EllipseRegression(Arrays.asList(points)).solve();
    }
    
    /**
     * Called when the ellipse is changed by user interaction
     */
    private void onEllipseChanged(Ellipse ellipse) {
        this.currentEllipse = ellipse;
        enableButtons();
        updatePreview();
    }
    
    private void enableButtons() {
        applyButton.setDisable(false);
    }
    
    private void resetEllipse() {
        if (initialEllipse != null) {
            imageView.setInteractiveEllipse(initialEllipse);
            this.currentEllipse = initialEllipse;
        } else {
            createDefaultEllipse();
        }
        updatePreview();
    }
    
    private void updatePreview() {
        if (currentEllipse != null) {
            previewPane.updateEllipse(currentEllipse);
        }
    }
    
    private void applyEllipse() {
        if (currentEllipse != null && onEllipseApplied != null) {
            applied = true;
            onEllipseApplied.accept(currentEllipse);
        }
        close();
    }
    
    private void cancel() {
        close();
    }
    
    private void close() {
        if (stage != null) {
            stage.close();
        }
    }
    
    public void setOnEllipseApplied(Consumer<Ellipse> callback) {
        this.onEllipseApplied = callback;
    }
    
    public boolean isApplied() {
        return applied;
    }

    /**
     * Shows the assisted ellipse fitting dialog
     */
    public static CompletableFuture<Ellipse> showDialog(Stage parent, ImageWrapper32 image, Ellipse initialEllipse) {
        return showDialog(parent, image, initialEllipse, null, 0, 0);
    }
    
    /**
     * Shows the assisted ellipse fitting dialog with batch context
     */
    public static CompletableFuture<Ellipse> showDialog(Stage parent, ImageWrapper32 image, Ellipse initialEllipse, String fileName, int currentFile, int totalFiles) {
        var future = new CompletableFuture<Ellipse>();
        
        Platform.runLater(() -> {
            try {
                var loader = I18N.fxmlLoader(JSolEx.class, "assisted-ellipse-fitting");
                var root = (Parent) loader.load();
                var controller = (AssistedEllipseFittingController) loader.getController();
                
                var stage = new Stage();

                var title = fileName != null
                    ? String.format("%s - %s (%d/%d)", 
                        I18N.string(JSolEx.class, "assisted-ellipse-fitting", "frame.title"),
                        fileName, currentFile, totalFiles)
                    : I18N.string(JSolEx.class, "assisted-ellipse-fitting", "frame.title");
                stage.setTitle(title);
                stage.initOwner(parent);
                stage.initModality(Modality.APPLICATION_MODAL);
                var scene = new Scene(root);
                stage.setScene(scene);
                stage.setResizable(true);

                controller.stage = stage;
                controller.setup(image, initialEllipse);
                
                controller.setOnEllipseApplied(future::complete);
                
                stage.setOnHidden(_ -> {
                    if (!controller.isApplied()) {
                        future.complete(null);
                    }
                });
                
                stage.show();
                
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
}