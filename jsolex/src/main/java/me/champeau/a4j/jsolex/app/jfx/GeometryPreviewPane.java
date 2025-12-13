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
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.GeometryUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Map;
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
    private final Crop cropper = new Crop(Map.of(), Broadcaster.NO_OP);

    /**
     * Creates a new geometry preview pane.
     */
    public GeometryPreviewPane() {
        previewView = new ZoomableImageView();

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(50, 50);

        statusLabel = new Label(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "select.ellipse.to.preview"));

        var previewBox = new VBox(5);
        var label = new Label(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "geometry.corrected.preview"));
        label.setWrapText(true);
        previewBox.getChildren().addAll(
                label,
                previewView
        );

        var statusBox = new HBox(10);
        statusBox.getChildren().addAll(statusLabel, progressIndicator);

        setCenter(previewBox);
        setBottom(statusBox);
    }

    /**
     * Sets the original image to preview.
     * @param image the original image
     */
    public void setOriginalImage(ImageWrapper32 image) {
        this.originalImage = image;
        if (image != null) {
            Platform.runLater(() -> {
                statusLabel.setText(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "select.ellipse.to.preview"));
                previewView.setImage(null);
            });
        }
    }

    /**
     * Updates the ellipse and generates a new preview.
     * @param ellipse the new ellipse
     */
    public void updateEllipse(Ellipse ellipse) {
        this.currentEllipse = ellipse;
        generatePreview();
    }

    /**
     * Generates the geometry correction preview
     */
    private void generatePreview() {
        if (originalImage == null) {
            return;
        }
        
        if (currentEllipse == null) {
            Platform.runLater(() -> {
                previewView.setImage(null);
                progressIndicator.setVisible(false);
                statusLabel.setText("Insufficient points for ellipse fitting");
            });
            return;
        }

        if (currentPreview != null && !currentPreview.isDone()) {
            currentPreview.cancel(true);
        }

        Platform.runLater(() -> {
            progressIndicator.setVisible(true);
            statusLabel.setText(I18N.string(JSolEx.class, "assisted-ellipse-fitting", "generating.preview"));
        });

        currentPreview = CompletableFuture.runAsync(() -> {
            try {
                var correctedImage = applyGeometryCorrection(originalImage, currentEllipse);

                Platform.runLater(() -> {
                    var fxImage = WritableImageSupport.asWritable(correctedImage);
                    previewView.setImage(fxImage);
                    progressIndicator.setVisible(false);
                    statusLabel.setText("");
                });

            } catch (Exception e) {
                Platform.runLater(() -> progressIndicator.setVisible(false));
            }
        });
    }

    private ImageWrapper32 applyGeometryCorrection(ImageWrapper32 image, Ellipse ellipse) {
        try {
            boolean disallowDownsampling = image.findMetadata(ProcessParams.class)
                    .map(params -> params.geometryParams().isDisallowDownsampling())
                    .orElse(false);

            var corrected = GeometryUtils.applyGeometryCorrection(
                    image,
                    ellipse,
                    null,
                    null,
                    0.0f,
                    disallowDownsampling
            );

            var correctedEllipse = computeCorrectedEllipse(ellipse, disallowDownsampling);

            corrected.metadata().put(Ellipse.class, correctedEllipse);
            return performAutocrop(corrected);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply geometry correction", e);
        }
    }

    /**
     * Computes the corrected ellipse after geometry transformation
     */
    private Ellipse computeCorrectedEllipse(Ellipse originalEllipse, boolean disallowDownsampling) {
        var theta = originalEllipse.rotationAngle();
        var m = Math.tan(-theta);
        var semiAxis = originalEllipse.semiAxis();
        var a = semiAxis.a();
        var b = semiAxis.b();
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        var shear = (m * cos * a * a + sin * b * b) / (b * b * cos - a * a * m * sin);

        var height = originalImage.height();
        var maxDx = height * shear;
        var shift = maxDx < 0 ? maxDx : 0;

        double sx;
        double sy = Math.abs((a * b * Math.sqrt((a * a * m * m + b * b) / (a * a * sin * sin + b * b * cos * cos)) / (b * b * cos - a * a * m * sin)));

        if (sy < 1 || !disallowDownsampling) {
            sx = 1 / sy;
            sy = 1.0d;
        } else {
            sx = 1.0d;
        }

        return GeometryUtils.computeCorrectedCircle(originalEllipse, shear, shift, sx, sy);
    }

    private ImageWrapper32 performAutocrop(ImageWrapper32 image) {
        var cropResult = cropper.autocrop2(Map.of("img", image, "factor", 1.1));

        var croppedImage = (ImageWrapper32) cropResult;

        return invertColorsOutsideDisk(croppedImage);
    }

    /**
     * Inverts pixel colors outside the solar disk
     */
    private ImageWrapper32 invertColorsOutsideDisk(ImageWrapper32 image) {
        var ellipse = image.findMetadata(Ellipse.class).orElse(null);
        if (ellipse == null) {
            return image;
        }
        var imageCopy = image.copy();
        var data = imageCopy.data();
        var height = imageCopy.height();
        var width = imageCopy.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var originalValue = data[y][x];
                if (!ellipse.isWithin(x, y)) {
                    data[y][x] = Constants.MAX_PIXEL_VALUE - originalValue;
                }
            }
        }

        return imageCopy;
    }


}