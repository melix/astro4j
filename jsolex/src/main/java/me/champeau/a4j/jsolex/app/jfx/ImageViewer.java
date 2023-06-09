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

import javafx.animation.PauseTransition;
import javafx.beans.property.DoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.ConstrastAdjustmentStrategy;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class ImageViewer {
    private Node root;
    private ConstrastAdjustmentStrategy stretchingStrategy = ConstrastAdjustmentStrategy.DEFAULT;
    private ImageWrapper image;
    private File imageFile;
    private ProcessingEventListener broadcaster;
    private ProcessParams processParams;

    @FXML
    private HBox stretchingParams;

    @FXML
    private ZoomableImageView imageView;

    private Button saveButton;
    private TabPane tabPane;
    private ForkJoinContext forkJoinContext;

    public void init(Node root, TabPane tabPane, ForkJoinContext cpuExecutor) {
        this.root = root;
        this.tabPane = tabPane;
        this.forkJoinContext = cpuExecutor;
    }

    public DoubleProperty fitWidthProperty() {
        return imageView.prefWidthProperty();
    }

    public void setup(ProcessingEventListener broadcaster,
                      String baseName,
                      ImageWrapper image,
                      File imageName,
                      ProcessParams params) {
        this.broadcaster = broadcaster;
        this.image = image;
        this.imageFile = new File(imageName.getParentFile(), baseName + "_" + imageName.getName() + imageDisplayExtension(params));
        this.processParams = params;
        configureStretching();
        if (params.extraParams().autosave()) {
            saveImage(imageFile);
        }
    }

    private String imageDisplayExtension(ProcessParams params) {
        var imageFormats = params.extraParams().imageFormats();
        if (imageFormats.contains(ImageFormat.PNG)) {
            return ImageFormat.PNG.extension();
        } else {
            return imageFormats.stream().filter(e -> e != ImageFormat.FITS).findFirst().orElse(ImageFormat.PNG).extension();
        }
    }

    private void saveImage(File target) {
        forkJoinContext.async(() -> {
            imageView.setImagePath(target.toPath());
            new ImageSaver(stretchingStrategy, processParams).save(image, target);
            BatchOperations.submit(() -> {
                imageView.setImage(new Image(imageFile.toURI().toString()));
                saveButton.setDisable(true);
            });
        });
    }

    private void configureStretching() {
        this.stretchingStrategy = ConstrastAdjustmentStrategy.DEFAULT;
        configureContrastAdjustment();
        var reset = new Button(message("reset"));
        reset.setOnAction(event -> {
            stretchingParams.getChildren().clear();
            configureStretching();
            strechAndDisplay();
        });
        saveButton = new Button(message("save"));
        saveButton.setOnAction(e -> saveImage(imageFile));
        var dimensions = new Label();
        dimensions.setText(image.width() + "x" + image.height());
        var zoomLabel = new Label("Zoom");
        var zoomSlider = new Slider(0.1, 5, 0.1);
        zoomSlider.setMajorTickUnit(1);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setValue(1.0);
        imageView.setOnZoomChanged(zoomSlider::setValue);
        var coordinatesLabel = new Label();
        imageView.setCoordinatesListener((x, y) -> coordinatesLabel.setText("(" + x.intValue() + "," + y.intValue() + ")"));
        zoomSlider.valueProperty().addListener((obj, oldValue, newValue) -> imageView.setZoom(newValue.doubleValue()));
        stretchingParams.getChildren().addAll(reset, saveButton, zoomLabel, zoomSlider, dimensions, coordinatesLabel);
        strechAndDisplay();
    }

    private static float linValueOf(double sliderValue) {
        int rnd = (int) Math.round(sliderValue);
        return rnd << 8;
    }

    private void configureContrastAdjustment() {
        int lo = (int) stretchingStrategy.getMin() >> 8;
        int hi = (int) stretchingStrategy.getMax() >> 8;
        var loSlider = new Slider(0, 255, lo);
        var hiSlider = new Slider(0, 255, hi);
        var loValueLabel = new Label("" + lo);
        var hiValueLabel = new Label("" + hi);
        var loLabel = new Label(message("low") + " ");
        var hiLabel = new Label(message("high") + " ");
        loSlider.setBlockIncrement(10);
        hiSlider.setBlockIncrement(10);
        var pause = new PauseTransition(Duration.millis(500));
        loSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = stretchingStrategy.withRange(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                strechAndDisplay();
                loValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        hiSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = stretchingStrategy.withRange(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                strechAndDisplay();
                hiValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        stretchingParams.getChildren().addAll(List.of(loLabel, loSlider, loValueLabel, hiLabel, hiSlider, hiValueLabel));
    }

    private float[] stretch(int width, int height, float[] data) {
        broadcaster.onProgress(ProgressEvent.of(0, message("stretching") + " " + imageFile.getName()));
        try {
            float[] streched = new float[data.length];
            System.arraycopy(data, 0, streched, 0, data.length);
            stretchingStrategy.stretch(width, height, streched);
            return streched;
        } finally {
            broadcaster.onProgress(ProgressEvent.of(1, message("stretching") + " " + imageFile.getName()));
        }
    }

    private float[][] stretch(int width, int height, float[] r, float[] g, float[] b) {
        broadcaster.onProgress(ProgressEvent.of(0, message("stretching") + " " + imageFile.getName()));
        try {
            float[] rr = new float[r.length];
            float[] gg = new float[g.length];
            float[] bb = new float[b.length];
            System.arraycopy(r, 0, rr, 0, r.length);
            System.arraycopy(g, 0, gg, 0, g.length);
            System.arraycopy(b, 0, bb, 0, b.length);
            var rgb = new float[][]{rr, gg, bb};
            stretchingStrategy.stretch(width, height, rgb);
            return rgb;
        } finally {
            broadcaster.onProgress(ProgressEvent.of(1, message("stretching") + " " + imageFile.getName()));
        }
    }

    private void strechAndDisplay() {
        forkJoinContext.async(() -> {
            File tmpImage = createTmpFile();
            var width = image.width();
            var height = image.height();
            Runnable updateDisplay = () -> {
                imageView.setImage(new Image(tmpImage.toURI().toString()));
                tabPane.getSelectionModel().select(imageView.getParentTab());
                tmpImage.delete();
                saveButton.setDisable(false);
            };
            var imageFormats = EnumSet.of(ImageFormat.PNG);
            // For some reason the image doesn't look as good when using PixelWriter
            // so we write the image in a tmp file and load it from here.
            if (image instanceof ImageWrapper32 mono) {
                var stretched = stretch(mono.width(), mono.height(), mono.data());
                forkJoinContext.async(() -> {
                    ImageUtils.writeMonoImage(width, height, stretched, tmpImage, imageFormats);
                    BatchOperations.submit(updateDisplay);
                });
            } else if (image instanceof ColorizedImageWrapper colorImage) {
                var stretched = stretch(colorImage.width(), colorImage.height(), colorImage.mono().data());
                var rgb = colorImage.converter().apply(stretched);
                var r = rgb[0];
                var g = rgb[1];
                var b = rgb[2];
                forkJoinContext.async(() -> {
                    ImageUtils.writeRgbImage(width, height, r, g, b, tmpImage, imageFormats);
                    BatchOperations.submit(updateDisplay);
                });
            } else if (image instanceof RGBImage rgb) {
                var stretched = stretch(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b());
                forkJoinContext.async(() -> {
                    ImageUtils.writeRgbImage(rgb.width(), rgb.height(), stretched[0], stretched[1], stretched[2], tmpImage, imageFormats);
                    BatchOperations.submit(updateDisplay);
                });
            }
        });
    }

    private File createTmpFile() {
        File tmpImage;
        try {
            tmpImage = Files.createTempFile(imageFile.getName(), "jsolex.png").toFile();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        return tmpImage;
    }

    public Node getRoot() {
        return root;
    }

    public void setTab(Tab tab) {
        imageView.setParentTab(tab);
    }
}
