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
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.util.Constants;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.io.File;
import java.util.List;

public class ImageViewer {
    private Node root;
    private StretchingStrategy initialStretchingStrategy = CutoffStretchingStrategy.DEFAULT;
    private StretchingStrategy stretchingStrategy = CutoffStretchingStrategy.DEFAULT;
    private ImageWrapper image;
    private File imageFile;

    @FXML
    private HBox stretchingParams;

    @FXML
    private ImageView imageView;

    private Button saveButton;

    public void init(Node root) {
        this.root = root;
    }

    public DoubleProperty fitWidthProperty() {
        return imageView.fitWidthProperty();
    }

    public void setImage(ImageWrapper image, StretchingStrategy strategy, File imageFile) {
        this.image = image;
        this.imageFile = imageFile;
        configureStretching(strategy);
        saveImage(imageFile);
    }

    private void saveImage(File target) {
        if (image instanceof ImageWrapper32 mono) {
            float[] stretched = stretch(mono.data());
            ImageUtils.writeMonoImage(image.width(), image.height(), stretched, target);
            saveButton.setDisable(true);
        } else {
            var colorImage = (ColorizedImageWrapper) image;
            float[] stretched = stretch(colorImage.mono().data());
            var colorized = colorImage.converter().apply(stretched);
            var r = colorized[0];
            var g = colorized[1];
            var b = colorized[2];
            ImageUtils.writeRgbImage(colorImage.width(), colorImage.height(), r, g, b, target);
        }
        imageView.setImage(new Image(imageFile.toURI().toString()));
    }

    private void configureStretching(StretchingStrategy strategy) {
        this.stretchingStrategy = strategy;
        this.initialStretchingStrategy = strategy;
        switch (strategy) {
            case LinearStrechingStrategy lin -> configureLinearStrategyPanel(lin);
            case ArcsinhStretchingStrategy arcsin -> configureArcsinhStrategyPanel(arcsin);
            default -> {
            }
        }
        var reset = new Button("Reset");
        reset.setOnAction(event -> {
            stretchingParams.getChildren().clear();
            configureStretching(initialStretchingStrategy);
            strechAndDisplay();
        });
        saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveImage(imageFile));
        stretchingParams.getChildren().addAll(reset, saveButton);
        strechAndDisplay();
    }

    private void configureArcsinhStrategyPanel(ArcsinhStretchingStrategy arcsin) {
        var blackpoint = arcsin.getBlackPoint();
        var strech = arcsin.getStretch();
        var blackpointSlider = new Slider(0, Constants.MAX_PIXEL_VALUE / 4, (int) blackpoint);
        var blackpointValue = new Label("" + (int) arcsin.getBlackPoint());
        var blackpointLabel = new Label("Black point: ");
        var strechSlider = new Slider(0, arcsin.getMaxStretch(), (int) strech);
        var pause = new PauseTransition(Duration.millis(500));
        blackpointSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = new ArcsinhStretchingStrategy((float) value, (float) strechSlider.getValue(), arcsin.getMaxStretch());
                blackpointValue.setText(String.format("%.2f", value));
                strechAndDisplay();
            });
            pause.playFromStart();
        });
        var strechSliderValue = new Label("" + (int) arcsin.getStretch());
        var stretchLabel = new Label("Stretch: ");
        strechSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = new ArcsinhStretchingStrategy((float) blackpointSlider.getValue(), (float) value, arcsin.getMaxStretch());
                strechSliderValue.setText(String.format("%.2f", value));
                strechAndDisplay();
            });
            pause.playFromStart();
        });
        stretchingParams.getChildren().addAll(List.of(blackpointLabel, blackpointSlider, blackpointValue, stretchLabel, strechSlider, strechSliderValue));
    }

    private static float linValueOf(double sliderValue) {
        int rnd = (int) Math.round(sliderValue);
        return rnd << 8;
    }

    private void configureLinearStrategyPanel(LinearStrechingStrategy lin) {
        int lo = (int) lin.getLo() >> 8;
        int hi = (int) lin.getHi() >> 8;
        var loSlider = new Slider(0, 255, lo);
        var hiSlider = new Slider(0, 255, hi);
        var loValueLabel = new Label("" + lo);
        var hiValueLabel = new Label("" + hi);
        var loLabel = new Label("Low: ");
        var hiLabel = new Label("High: ");
        loSlider.setBlockIncrement(10);
        hiSlider.setBlockIncrement(10);
        var pause = new PauseTransition(Duration.millis(500));
        loSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = new LinearStrechingStrategy(linValueOf(value), linValueOf(hiSlider.getValue()));
                strechAndDisplay();
                loValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        hiSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = new LinearStrechingStrategy(linValueOf(loSlider.getValue()), linValueOf(value));
                strechAndDisplay();
                hiValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        stretchingParams.getChildren().addAll(List.of(loLabel, loSlider, loValueLabel, hiLabel, hiSlider, hiValueLabel));
    }

    private float[] stretch(float[] data) {
        float[] streched = new float[data.length];
        System.arraycopy(data, 0, streched, 0, data.length);
        stretchingStrategy.stretch(streched);
        return streched;
    }

    private void strechAndDisplay() {
        new Thread(()-> {
            File tmpImage = new File(imageFile + ".tmp");
            var width = image.width();
            var height = image.height();
            // For some reason the image doesn't look as good when using PixelWriter
            // so we write the image in a tmp file and load it from here.
            if (image instanceof ImageWrapper32 mono) {
                var stretched = stretch(mono.data());
                ImageUtils.writeMonoImage(width, height, stretched, tmpImage);
            } else {
                var colorImage = (ColorizedImageWrapper) image;
                var stretched = stretch(colorImage.mono().data());
                var rgb = colorImage.converter().apply(stretched);
                var r = rgb[0];
                var g = rgb[1];
                var b = rgb[2];
                ImageUtils.writeRgbImage(width, height, r, g, b, tmpImage);
            }
            Platform.runLater(() -> {
                imageView.setImage(new Image(tmpImage.toURI().toString()));
                tmpImage.delete();
                saveButton.setDisable(false);
            });
        }).start();
    }

    public Node getRoot() {
        return root;
    }
}
