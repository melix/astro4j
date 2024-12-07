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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.stretching.ContrastAdjustmentStrategy;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.stretching.StretchingChain;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.math.regression.Ellipse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class ImageViewer implements WithRootNode {
    private final Lock displayLock = new ReentrantLock();
    private Node root;
    private Stage stage;
    private ContrastAdjustmentStrategy stretchingStrategy = ContrastAdjustmentStrategy.DEFAULT;
    private ImageWrapper image;
    private ImageWrapper displayImage;
    private ImageWrapper stretchedImage;
    private File imageFile;
    private GeneratedImageKind kind;
    private ProcessingEventListener broadcaster;
    private ProcessParams processParams;

    private Label dimensions;
    @FXML
    private VBox stretchingParams;

    @FXML
    private ZoomableImageView imageView;

    private CheckBox correctAngleP;
    private Button saveButton;
    private String title;
    private Runnable onDisplayUpdate;
    private int rotation;
    private boolean vflip;
    private boolean firstShow = true;

    private final ListProperty<ImageState> imageHistory = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final IntegerProperty currentImage = new SimpleIntegerProperty(0);
    private Set<ImageViewer> siblings;

    public void init(Node root) {
        this.root = root;
    }

    public DoubleProperty fitWidthProperty() {
        return imageView.prefWidthProperty();
    }

    public void setup(ProcessingEventListener broadcaster,
                      String title,
                      String baseName,
                      GeneratedImageKind kind,
                      ImageWrapper image,
                      File imageName,
                      ProcessParams params,
                      Map<String, ImageViewer> popupViews,
                      Set<ImageViewer> siblings) {
        this.broadcaster = broadcaster;
        this.processParams = params;
        this.kind = kind;
        this.title = title;
        this.siblings = siblings;
        if (image != null) {
            this.image = image instanceof FileBackedImage fbi ? fbi.unwrapToMemory() : image;
            this.imageFile = new File(imageName.getParentFile(), baseName + "_" + imageName.getName() + imageDisplayExtension(params));
            recordImage(baseName, image);
            BackgroundOperations.async(() -> {
                configureStretching();
                if (params.extraParams().autosave()) {
                    saveImage();
                }
                if (!popupViews.containsKey(title)) {
                    var openInNewWindow = new MenuItem(message("open.new.window"));
                    openInNewWindow.setOnAction(e -> {
                        try {
                            var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "imageview");
                            var node = (Node) fxmlLoader.load();
                            var controller = (ImageViewer) fxmlLoader.getController();
                            controller.init(node);
                            controller.setup(new ProcessingEventListener() {
                            }, title, baseName, kind, image, imageFile, processParams, popupViews, siblings);
                            var stage = new Stage();
                            var scene = new Scene((Parent) node);
                            controller.stage = stage;
                            controller.saveButton.setVisible(false);
                            controller.fitWidthProperty().bind(stage.widthProperty());
                            controller.updateTitle();
                            stage.setWidth(1024);
                            stage.setHeight(768);
                            stage.setScene(scene);
                            stage.setOnCloseRequest(evt -> popupViews.remove(title));
                            stage.show();
                            popupViews.put(title, controller);
                            controller.display();
                        } catch (IOException ex) {
                            throw new ProcessingException(ex);
                        }
                    });
                    imageView.getCtxMenu().getItems().add(openInNewWindow);
                }
            });
        }
    }

    public ZoomableImageView getImageView() {
        return imageView;
    }

    public void maybeResetZoom() {
        if (imageView.getZoom() == 0) {
            imageView.resetZoom();
        }
    }

    private void recordImage(String baseName, ImageWrapper image) {
        imageHistory.add(new ImageState(processParams, baseName, image, imageFile));
    }

    private String imageDisplayExtension(ProcessParams params) {
        var imageFormats = params.extraParams().imageFormats();
        if (imageFormats.contains(ImageFormat.PNG)) {
            return ImageFormat.PNG.extension();
        } else {
            return imageFormats.stream().filter(e -> e != ImageFormat.FITS).findFirst().orElse(ImageFormat.PNG).extension();
        }
    }

    private void saveImage() {
        var image = applyTransformations(this.image);
        var files = new ImageSaver(stretchingStrategy, processParams).save(image, imageFile);
        files.stream()
            .findFirst()
            .ifPresent(file -> imageView.setImagePathForOpeningInExplorer(file.toPath()));
        Platform.runLater(() -> {
            saveButton.setDisable(true);
            imageView.fileSaved();
        });
    }

    private void configureStretching() {
        try {
            displayLock.lock();

            this.stretchingStrategy = ContrastAdjustmentStrategy.DEFAULT;
            var line1 = new HBox(8);
            line1.setAlignment(Pos.CENTER_LEFT);
            var line2 = new HBox(8);
            line2.setAlignment(Pos.CENTER_LEFT);
            configureContrastAdjustment(line1);
            var reset = new Button(message("reset"));
            reset.setOnAction(event -> {
                stretchingParams.getChildren().clear();
                configureStretching();
                stretchAndDisplay();
            });
            saveButton = new Button(message("save"));
            saveButton.setOnAction(e -> saveImage());
            dimensions = new Label();
            var zoomLabel = new Label("Zoom");
            var zoomMinus = new Button("-");
            zoomMinus.setOnAction(evt -> imageView.setZoom(imageView.getZoom() * 0.8));
            var zoomPlus = new Button("+");
            zoomPlus.setOnAction(evt -> imageView.setZoom(imageView.getZoom() * 1.2));
            var coordinatesLabel = new Label();
            imageView.setCoordinatesListener((x, y) -> {
                String extra = "";
                if (displayImage == null) {
                    displayImage = image;
                }
                var xx = x.intValue();
                var yy = y.intValue();
                if (displayImage instanceof ImageWrapper32 mono) {
                    if (xx < 0 || xx >= mono.width() || yy < 0 || yy >= mono.height()) {
                        return;
                    }
                    var pixelValue = mono.data()[yy][xx];
                    extra = ", " + String.format("%.0f", pixelValue);
                }
                coordinatesLabel.setText("(" + xx + ", " + yy + extra + ")");
            });
            correctAngleP = new CheckBox(message("correct.p.angle"));
            correctAngleP.setSelected(processParams.geometryParams().isAutocorrectAngleP());
            var applyNextTime = new Button("✔");
            correctAngleP.selectedProperty().addListener((obj, oldValue, newValue) -> {
                stretchAndDisplay();
                if (!Objects.equals(oldValue, newValue)) {
                    applyNextTime.setDisable(false);
                }
            });
            correctAngleP.setDisable(kind.cannotPerformManualRotation());
            var prevButton = new Button(message("prev.image"));
            prevButton.disableProperty().bind(currentImage.isEqualTo(0));
            prevButton.visibleProperty().bind(imageHistory.sizeProperty().greaterThan(1));
            prevButton.setOnAction(evt -> {
                currentImage.set(currentImage.get() - 1);
                showImage();
            });
            var nextButton = new Button(message("next.image"));
            nextButton.setOnAction(evt -> {
                currentImage.set(currentImage.get() + 1);
                showImage();
            });
            nextButton.disableProperty().bind(currentImage.isEqualTo(imageHistory.sizeProperty().subtract(1)));
            nextButton.visibleProperty().bind(imageHistory.sizeProperty().greaterThan(1));
            var fitButton = new Button("←fit→");
            fitButton.setOnAction(evt -> imageView.resetZoom(true));
            var fitToCenter = new Button("→fit←");
            fitToCenter.disableProperty().bind(imageView.canFitToCenterProperty().map(e -> !e));
            fitToCenter.setOnAction(evt -> imageView.fitToCenter());
            var oneToOneFit = new Button("1:1");
            oneToOneFit.disableProperty().bind(imageView.canFitToCenterProperty().map(e -> !e));
            oneToOneFit.setOnAction(evt -> imageView.oneToOneZoomAndCenter());
            var leftRotate = new Button("↶");
            leftRotate.setTooltip(new Tooltip(message("rotate.left")));
            leftRotate.setOnAction(evt -> {
                rotation = (rotation - 1) % 4;
                applyNextTime.setDisable(false);
                stretchAndDisplay();
            });
            leftRotate.disableProperty().set(kind.cannotPerformManualRotation());
            var rightRotate = new Button("↷");
            rightRotate.setTooltip(new Tooltip(message("rotate.right")));
            rightRotate.setOnAction(evt -> {
                rotation = (rotation + 1) % 4;
                applyNextTime.setDisable(false);

                stretchAndDisplay();
            });
            rightRotate.disableProperty().set(kind.cannotPerformManualRotation());
            var verticalMirror = new Button("⇅");
            verticalMirror.setTooltip(new Tooltip(message("vertical.flip")));
            verticalMirror.setOnAction(evt -> {
                vflip = !vflip;
                rotation = -rotation;
                applyNextTime.setDisable(false);
                stretchAndDisplay();
            });
            verticalMirror.disableProperty().set(kind.cannotPerformManualRotation());
            var horizontalMirror = new Button("⇄");
            horizontalMirror.setTooltip(new Tooltip(message("horizontal.flip")));
            horizontalMirror.setOnAction(evt -> {
                rotation = (rotation + 2) % 4;
                applyNextTime.setDisable(false);
                verticalMirror.fire();
                stretchAndDisplay();
            });
            horizontalMirror.disableProperty().set(kind.cannotPerformManualRotation());
            applyNextTime.setOnAction(evt -> {
                broadcaster.onGenericMessage(GenericMessage.of(new ApplyUserRotation(rotation, correctAngleP.isSelected(), vflip)));
                applyNextTime.setDisable(true);
            });
            applyNextTime.setTooltip(new Tooltip(message("apply.next.time")));
            applyNextTime.setDisable(true);
            line1.getChildren().addAll(reset, saveButton, prevButton, nextButton);
            line2.getChildren().addAll(correctAngleP, zoomLabel, zoomMinus, zoomPlus, fitButton, fitToCenter, oneToOneFit, leftRotate, rightRotate, verticalMirror, horizontalMirror, applyNextTime, dimensions, coordinatesLabel);
            var titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold");
            var alignButton = new Button("⌖");
            alignButton.setStyle("-fx-padding: 2; -fx-font-size: 18");
            alignButton.setOnAction(evt -> {
                onDisplayUpdate = null;
                for (var viewer : siblings) {
                    if (viewer != this) {
                        viewer.onDisplayUpdate = () -> viewer.imageView.alignWith(imageView);
                    }
                }
            });
            Platform.runLater(() -> {
                alignButton.setTooltip(new Tooltip(message("align.images")));
                var titleBox = new HBox(alignButton, titleLabel, new Label("(" + imageFile.getName() + ")"));
                titleBox.setSpacing(4);
                titleBox.setAlignment(Pos.CENTER_LEFT);
                stretchingParams.getChildren().addAll(titleBox, line1, line2);
                line1.getChildren().forEach(e -> HBox.setHgrow(e, Priority.ALWAYS));
                line2.getChildren().stream().filter(e -> !(e instanceof Slider)).forEach(e -> HBox.setHgrow(e, Priority.ALWAYS));
            });
        } finally {
            displayLock.unlock();
        }
    }

    private static float linValueOf(double sliderValue) {
        int rnd = (int) Math.round(sliderValue);
        return rnd << 8;
    }

    private void configureContrastAdjustment(HBox container) {
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
                stretchAndDisplay();
                loValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        hiSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                stretchingStrategy = stretchingStrategy.withRange(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                stretchAndDisplay();
                hiValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        container.getChildren().addAll(List.of(loLabel, loSlider, loValueLabel, hiLabel, hiSlider, hiValueLabel));
    }

    private ImageWrapper stretch(ImageWrapper image) {
        broadcaster.onProgress(ProgressEvent.of(0, message("stretching") + " " + imageFile.getName()));
        try {
            var copy = image.copy();
            new StretchingChain(stretchingStrategy, RangeExpansionStrategy.DEFAULT).stretch(copy);
            return copy;
        } finally {
            broadcaster.onProgress(ProgressEvent.of(1, message("stretching") + " " + imageFile.getName()));
        }
    }

    void display() {
        if (image != null && firstShow) {
            BackgroundOperations.async(() -> stretchAndDisplay(true));
            firstShow = false;
        } else if (image != null) {
            BackgroundOperations.async(this::maybeRunOnUpdate);
        }
    }

    private void stretchAndDisplay() {
        stretchAndDisplay(false);
    }

    private void updateDisplay(List<File> candidateImages, boolean resetZoom) {
        candidateImages.stream()
            .findFirst()
            .ifPresent(tmpImage -> {
                try {
                    displayLock.lock();
                    if (displayImage != null) {
                        dimensions.setText(displayImage.width() + "x" + displayImage.height());
                    }
                    imageView.setImage(new Image(tmpImage.toURI().toString()));
                    imageView.setSolarDisk(image != null ? image.findMetadata(Ellipse.class).orElse(null) : null);
                    if (resetZoom) {
                        imageView.resetZoom();
                    }
                    saveButton.setDisable(false);
                    maybeRunOnUpdate();
                    tmpImage.delete();
                } finally {
                    displayLock.unlock();
                }
            });
    }

    private void maybeRunOnUpdate() {
        if (onDisplayUpdate != null) {
            onDisplayUpdate.run();
        }
    }

    private void stretchAndDisplay(boolean resetZoom) {
        var imageFormats = EnumSet.of(ImageFormat.PNG);
        // For some reason the image doesn't look as good when using PixelWriter
        // so we write the image in a tmp file and load it from here.
        displayImage = applyTransformations(this.image);
        var tmpImage = createTmpFile();
        if (displayImage instanceof ImageWrapper32 mono) {
            stretchedImage = stretch(mono);
            var savedImages = ImageUtils.writeMonoImage(mono.width(), mono.height(), ((ImageWrapper32) stretchedImage).data(), tmpImage, imageFormats);
            Platform.runLater(() -> updateDisplay(savedImages, resetZoom));
        } else if (displayImage instanceof RGBImage rgb) {
            stretchedImage = stretch(rgb);
            var stretched = (RGBImage) stretchedImage;
            var savedImages = ImageUtils.writeRgbImage(rgb.width(), rgb.height(), stretched.r(), stretched.g(), stretched.b(), tmpImage, imageFormats);
            Platform.runLater(() -> updateDisplay(savedImages, resetZoom));
        }
    }

    private ImageWrapper applyTransformations(ImageWrapper image) {
        double correction = 0;
        if (!kind.cannotPerformManualRotation() && vflip) {
            image = Corrector.verticalFlip(image);
        }

        if (!kind.cannotPerformManualRotation()) {
            correction = image.findMetadata(RotationKind.class).orElseGet(() -> processParams.geometryParams().rotation()).angle();
            if (correctAngleP.isSelected()) {
                correction += SolarParametersUtils.computeSolarParams(processParams.observationDetails().date().toLocalDateTime()).p();
            }
            correction += (Math.PI * rotation / 2d);
        }
        if (correction != 0) {
            image = image.copy();
            image = Corrector.rotate(image, correction, processParams.geometryParams().autocropMode() == AutocropMode.OFF);
        }
        return image;
    }

    public ImageWrapper getStretchedImage() {
        return stretchedImage == null ? image : stretchedImage;
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

    @Override
    public Node getRoot() {
        return root;
    }

    public synchronized void setImage(String baseName, ProcessParams params, ImageWrapper image, Path path) {
        this.processParams = params;
        this.image = image instanceof FileBackedImage fbi ? fbi.unwrapToMemory() : image;
        this.imageFile = new File(path.toFile().getParentFile(), baseName + "_" + path.getFileName() + imageDisplayExtension(processParams));
        this.imageView.setImagePathForOpeningInExplorer(path);
        recordImage(baseName, image);
        currentImage.set(imageHistory.size() - 1);
        showImage();
    }

    public void showImage() {
        var currentState = imageHistory.get(currentImage.get());
        this.processParams = currentState.processParams;
        this.image = currentState.image;
        this.imageFile = currentState.imageFile;
        Platform.runLater(() -> {
            updateTitle();
            imageView.setImage(new Image(imageFile.toURI().toString()));
            imageView.setSolarDisk(image.findMetadata(Ellipse.class).orElse(null));
            saveButton.setDisable(true);
            stretchAndDisplay();
        });
    }

    private void updateTitle() {
        if (stage != null) {
            stage.titleProperty().set(title + " (" + imageFile.getName() + ") - " + currentImage.get());
        }
    }

    private record ImageState(
        ProcessParams processParams,
        String baseName,
        ImageWrapper image,
        File imageFile
    ) {

    }

}
