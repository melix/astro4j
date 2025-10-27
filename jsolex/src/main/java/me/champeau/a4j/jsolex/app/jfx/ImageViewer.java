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
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.GlobeStyle;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.ContrastAdjustmentStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CurveTransformStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.stretching.StretchingChain;
import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.math.regression.Ellipse;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.JSolEx.newScene;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newStage;

public class ImageViewer implements WithRootNode {
    private final Lock displayLock = new ReentrantLock();
    private Node root;
    private Stage stage;
    private ContrastAdjustmentStrategy contrastAdjustStrategy = ContrastAdjustmentStrategy.DEFAULT;
    private CurveTransformStrategy curveTransformStrategy = new CurveTransformStrategy(32768, 32768);
    private FileBackedImage image;
    private ImageWrapper stretchedImage;
    private File imageFile;
    private GeneratedImageKind kind;
    private ProcessingEventListener broadcaster;
    private ProgressOperation operation;
    private ProcessParams processParams;
    private StretchingMode stretchingMode = StretchingMode.LINEAR;

    private Label dimensions;
    @FXML
    private VBox stretchingParams;

    @FXML
    private ZoomableImageView imageView;

    @FXML
    private TextArea descriptionArea;

    private CheckBox correctAngleP;
    private Button saveButton;
    private String title;
    private String description;
    private Runnable onDisplayUpdate;
    private int rotation;
    private boolean vflip;
    private boolean firstShow = true;

    private final ListProperty<ImageState> imageHistory = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final IntegerProperty currentImage = new SimpleIntegerProperty(0);
    private Set<ImageViewer> siblings;
    private PauseTransition stretchedImageDebounce;

    public void init(Node root) {
        this.root = root;
        this.stretchedImageDebounce = new PauseTransition(Duration.seconds(10));
        this.stretchedImageDebounce.setOnFinished(e -> {
            if (stretchedImage != null && !(stretchedImage instanceof FileBackedImage)) {
                stretchedImage = FileBackedImage.wrap(stretchedImage);
            }
        });
    }

    public DoubleProperty fitWidthProperty() {
        return imageView.prefWidthProperty();
    }

    public void setup(ProcessingEventListener broadcaster,
                      ProgressOperation operation,
                      String title,
                      String baseName,
                      GeneratedImageKind kind,
                      String description,
                      ImageWrapper image,
                      File imageName,
                      ProcessParams params,
                      Map<String, ImageViewer> popupViews,
                      Set<ImageViewer> siblings) {
        this.broadcaster = broadcaster;
        this.operation = operation;
        this.processParams = params;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.siblings = siblings;
        if (kind == GeneratedImageKind.IMAGE_MATH || kind == GeneratedImageKind.COLLAGE) {
            this.stretchingMode = StretchingMode.NO_STRETCH;
        }
        this.descriptionArea.textProperty().addListener((obs, old, newValue) -> {
            var textElement = this.descriptionArea.lookup(".text");
            if (textElement != null) {
                Platform.runLater(() -> this.descriptionArea.setPrefHeight(textElement.getLayoutBounds().getHeight()));
            }
        });
        if (image != null) {
            var wrapped = FileBackedImage.wrap(image);
            this.image = wrapped;
            this.imageFile = new File(imageName.getParentFile(), baseName + "_" + imageName.getName() + imageDisplayExtension(params));
            recordImage(baseName, image);
            // Initialize correctAngleP synchronously to avoid race conditions
            correctAngleP = new CheckBox(message("correct.p.angle"));
            correctAngleP.getStyleClass().add("check-box");
            correctAngleP.setSelected(processParams.geometryParams().isAutocorrectAngleP());
            correctAngleP.setDisable(kind.cannotPerformManualRotation());
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
                            }, operation, title, baseName, kind, description, wrapped, imageName, processParams, popupViews, siblings);
                            var stage = newStage();
                            var scene = newScene((Parent) node);
                            controller.stage = stage;
                            controller.fitWidthProperty().bind(stage.widthProperty());
                            controller.updateTitle();
                            stage.setWidth(1024);
                            stage.setHeight(768);
                            stage.setScene(scene);
                            stage.setOnCloseRequest(evt -> popupViews.remove(title));
                            stage.show();
                            popupViews.put(title, controller);
                            controller.display();
                            controller.saveButton.setVisible(false);
                        } catch (IOException ex) {
                            throw new ProcessingException(ex);
                        }
                    });
                    Platform.runLater(() -> imageView.getCtxMenu().getItems().add(openInNewWindow));
                }
            });
            maybeAddMeasurementTool(() -> {
                var measureDistance = new MenuItem(I18N.string(JSolEx.class, "measures", "measure.distance"));
                imageView.getCtxMenu().getItems().add(measureDistance);
                return measureDistance;
            });
        }
    }

    private void maybeAddMeasurementTool(Supplier<EventTarget> target) {
        image.findMetadata(Ellipse.class).ifPresent(ellipse -> {
            image.findMetadata(SolarParameters.class).ifPresent(solarParameters -> {
                EventHandler<ActionEvent> handler = e -> BackgroundOperations.async(() -> {
                    var prepared = applyTransformations(image.copy());
                    if (prepared.unwrapToMemory() instanceof ImageWrapper32 mono) {
                        prepared = RGBImage.toRGB(mono);
                    }
                    var op = operation.createChild(I18N.string(JSolEx.class, "measures", "preparing.measure.distance"));
                    broadcaster.onProgress(ProgressEvent.of(op));
                    var withGlobe = new ImageDraw(Map.of(), Broadcaster.NO_OP)
                            .doDrawGlobe(prepared, ellipse, correctAngleP.isSelected() ? 0 : solarParameters.p(), solarParameters.b0(), Color.YELLOW, false, false, GlobeStyle.EQUATORIAL_COORDS, true);
                    try {
                        var preparedFile = TemporaryFolder.newTempFile("prepared", ".png").toFile();
                        var globeFile = TemporaryFolder.newTempFile("globe", ".png").toFile();
                        var imageSaver = new ImageSaver(determineStrategy(), processParams, Set.of(ImageFormat.PNG));
                        imageSaver.save(prepared, preparedFile);
                        imageSaver.save(withGlobe, globeFile);
                        var distanceMeasurementPane = new DistanceMeasurementPane(new Image(preparedFile.toURI().toString()), new Image(globeFile.toURI().toString()), ellipse, solarParameters);
                        Platform.runLater(() -> {
                            var stage = new Stage();
                            stage.setTitle(I18N.string(JSolEx.class, "measures", "measure.distance"));
                            var scene = new Scene(distanceMeasurementPane, 1024, 768);
                            stage.setScene(scene);
                            stage.setMaximized(true);
                            stage.setOnShowing(evt -> distanceMeasurementPane.fitToContainer());
                            stage.showAndWait();
                        });
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    } finally {
                        broadcaster.onProgress(ProgressEvent.of(op.complete()));
                    }
                });
                var targetNode = target.get();
                if (targetNode instanceof MenuItem menuItem) {
                    menuItem.setOnAction(handler);
                } else if (targetNode instanceof Button button) {
                    button.setOnAction(handler);
                }
            });
        });
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
        var fileBackedImage = FileBackedImage.wrap(image);
        imageHistory.add(new ImageState(processParams, baseName, fileBackedImage, imageFile));
    }

    private String imageDisplayExtension(ProcessParams params) {
        var imageFormats = Configuration.getInstance().getImageFormats();
        if (imageFormats.contains(ImageFormat.PNG)) {
            return ImageFormat.PNG.extension();
        } else {
            return imageFormats.stream().filter(e -> e != ImageFormat.FITS).findFirst().orElse(ImageFormat.PNG).extension();
        }
    }

    private void saveImage() {
        var unwrapped = this.image.unwrapToMemory();
        var image = applyTransformations(unwrapped);
        var strategy = determineStrategy();
        var files = new ImageSaver(strategy, processParams, me.champeau.a4j.jsolex.app.Configuration.getInstance().getImageFormats()).save(image, imageFile);
        files.stream()
                .findFirst()
                .ifPresent(file -> imageView.setImagePathForOpeningInExplorer(file.toPath()));
        Platform.runLater(() -> {
            saveButton.setDisable(true);
            imageView.fileSaved();
        });
    }

    private StretchingStrategy determineStrategy() {
        return switch (stretchingMode) {
            case NO_STRETCH -> CutoffStretchingStrategy.DEFAULT;
            case LINEAR -> new StretchingChain(RangeExpansionStrategy.DEFAULT, contrastAdjustStrategy, RangeExpansionStrategy.DEFAULT);
            case CURVE -> curveTransformStrategy;
        };
    }

    private void configureStretching() {
        try {
            displayLock.lock();
            this.contrastAdjustStrategy = ContrastAdjustmentStrategy.DEFAULT;
            this.curveTransformStrategy = new CurveTransformStrategy(32768, 32768);
            var line2 = new HBox(8);
            line2.setAlignment(Pos.CENTER_LEFT);
            var linearStretchParams = new HBox(8);
            linearStretchParams.setAlignment(Pos.CENTER_LEFT);
            configureContrastAdjustment(linearStretchParams);
            configureHGrow(linearStretchParams.getChildren());
            var curveStretchParams = createCurveAdjustment();
            var strategySelector = new ChoiceBox<StretchingMode>();
            strategySelector.getItems().addAll(StretchingMode.values());
            strategySelector.getSelectionModel().select(stretchingMode);
            strategySelector.getSelectionModel().selectedItemProperty().addListener((obj, oldValue, newValue) -> {
                stretchingMode = newValue;
                linearStretchParams.setDisable(stretchingMode != StretchingMode.LINEAR);
                curveStretchParams.setDisable(stretchingMode != StretchingMode.CURVE);
                stretchAndDisplay();
            });
            linearStretchParams.visibleProperty().bind(strategySelector.getSelectionModel().selectedItemProperty().isEqualTo(StretchingMode.LINEAR));
            linearStretchParams.managedProperty().bind(strategySelector.getSelectionModel().selectedItemProperty().isEqualTo(StretchingMode.LINEAR));
            curveStretchParams.visibleProperty().bind(strategySelector.getSelectionModel().selectedItemProperty().isEqualTo(StretchingMode.CURVE));
            curveStretchParams.managedProperty().bind(strategySelector.getSelectionModel().selectedItemProperty().isEqualTo(StretchingMode.CURVE));
            var line1 = new HBox(8);
            line1.setAlignment(Pos.CENTER_LEFT);
            var stretchingLabel = new Label(message("stretching.label"));
            preventEllipsis(stretchingLabel);
            line1.getChildren().addAll(stretchingLabel, strategySelector, linearStretchParams, curveStretchParams);
            var reset = createButton(message("reset"));
            reset.setOnAction(event -> {
                stretchingParams.getChildren().clear();
                configureStretching();
                stretchAndDisplay();
            });
            saveButton = createButton(message("save"));
            saveButton.setOnAction(e -> saveImage());
            dimensions = new Label();
            var zoomLabel = new Label("Zoom");
            var zoomMinus = createButton("-");
            zoomMinus.setOnAction(evt -> imageView.setZoom(imageView.getZoom() * 0.8));
            var zoomPlus = createButton("+");
            zoomPlus.setOnAction(evt -> imageView.setZoom(imageView.getZoom() * 1.2));
            var coordinatesLabel = new Label();
            imageView.setCoordinatesListener((x, y) -> {
                var extra = "";
                var imageForPixelValue = stretchedImage != null ? stretchedImage : image;
                var unwrappedForPixel = imageForPixelValue.unwrapToMemory();
                var xx = x.intValue();
                var yy = y.intValue();
                if (unwrappedForPixel instanceof ImageWrapper32 mono) {
                    if (xx < 0 || xx >= mono.width() || yy < 0 || yy >= mono.height()) {
                        return;
                    }
                    var pixelValue = mono.data()[yy][xx];
                    extra = ", " + String.format("%.0f", pixelValue);
                }
                coordinatesLabel.setText("(" + xx + ", " + yy + extra + ")");
            });
            var applyNextTime = createButton("✔");
            correctAngleP.selectedProperty().addListener((obj, oldValue, newValue) -> {
                stretchAndDisplay();
                if (!Objects.equals(oldValue, newValue)) {
                    applyNextTime.setDisable(false);
                }
            });
            var prevButton = createButton(message("prev.image"));
            prevButton.disableProperty().bind(currentImage.isEqualTo(0));
            prevButton.visibleProperty().bind(imageHistory.sizeProperty().greaterThan(1));
            prevButton.setOnAction(evt -> {
                currentImage.set(currentImage.get() - 1);
                showImage();
            });
            var nextButton = createButton(message("next.image"));
            nextButton.setOnAction(evt -> {
                currentImage.set(currentImage.get() + 1);
                showImage();
            });
            nextButton.disableProperty().bind(currentImage.isEqualTo(imageHistory.sizeProperty().subtract(1)));
            nextButton.visibleProperty().bind(imageHistory.sizeProperty().greaterThan(1));
            var fitButton = createButton("←fit→");
            fitButton.setOnAction(evt -> imageView.resetZoom(true));
            var fitToCenter = createButton("→fit←");
            fitToCenter.disableProperty().bind(imageView.canFitToCenterProperty().map(e -> !e));
            fitToCenter.setOnAction(evt -> imageView.fitToCenter());
            var oneToOneFit = createButton("1:1");
            oneToOneFit.disableProperty().bind(imageView.canFitToCenterProperty().map(e -> !e));
            oneToOneFit.setOnAction(evt -> imageView.oneToOneZoomAndCenter());
            var leftRotate = createButton("↶");
            leftRotate.setOnAction(evt -> {
                rotation = (rotation - 1) % 4;
                applyNextTime.setDisable(false);
                stretchAndDisplay();
            });
            leftRotate.disableProperty().set(kind.cannotPerformManualRotation());
            var rightRotate = createButton("↷");
            rightRotate.setOnAction(evt -> {
                rotation = (rotation + 1) % 4;
                applyNextTime.setDisable(false);

                stretchAndDisplay();
            });
            rightRotate.disableProperty().set(kind.cannotPerformManualRotation());
            var verticalMirror = createButton("⇅");
            verticalMirror.setOnAction(evt -> {
                vflip = !vflip;
                rotation = -rotation;
                applyNextTime.setDisable(false);
                stretchAndDisplay();
            });
            verticalMirror.disableProperty().set(kind.cannotPerformManualRotation());
            var horizontalMirror = createButton("⇄");
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
            applyNextTime.setDisable(true);
            var measureButton = createButton("⚖");
            measureButton.setStyle("-fx-padding: 2; -fx-font-size: 18");
            measureButton.setDisable(true);
            maybeAddMeasurementTool(() -> {
                measureButton.setDisable(false);
                return measureButton;
            });
            line1.getChildren().addAll(reset, saveButton, prevButton, nextButton);
            line2.getChildren().addAll(correctAngleP, zoomLabel, zoomMinus, zoomPlus, fitButton, fitToCenter, oneToOneFit, leftRotate, rightRotate, verticalMirror, horizontalMirror, applyNextTime, measureButton, dimensions, coordinatesLabel);
            var titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold");
            var alignButton = createButton("⌖");
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
                leftRotate.setTooltip(new Tooltip(message("rotate.left")));
                rightRotate.setTooltip(new Tooltip(message("rotate.right")));
                verticalMirror.setTooltip(new Tooltip(message("vertical.flip")));
                horizontalMirror.setTooltip(new Tooltip(message("horizontal.flip")));
                applyNextTime.setTooltip(new Tooltip(message("apply.next.time")));
                measureButton.setTooltip(new Tooltip(message("measure.button.tooltip")));
                var titleBox = new HBox(alignButton, titleLabel, new Label("(" + imageFile.getName() + ")"));
                titleBox.setSpacing(4);
                titleBox.setAlignment(Pos.CENTER_LEFT);
                stretchingParams.getChildren().addAll(titleBox, line1, line2);
                configureHGrow(line1.getChildren());
                configureHGrow(line2.getChildren().stream().filter(e -> !(e instanceof Slider)).toList());
            });
        } finally {
            displayLock.unlock();
        }
    }

    private static void preventEllipsis(Label... labels) {
        for (var label : labels) {
            label.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
        }
    }

    private static void configureHGrow(List<Node> nodes) {
        nodes.forEach(e -> HBox.setHgrow(e, Priority.ALWAYS));
    }

    private static float linValueOf(double sliderValue) {
        var rnd = (int) Math.round(sliderValue);
        return rnd << 8;
    }

    private void configureContrastAdjustment(HBox container) {
        var lo = (int) contrastAdjustStrategy.getMin() >> 8;
        var hi = (int) contrastAdjustStrategy.getMax() >> 8;
        var loSlider = new Slider(0, 255, lo);
        var hiSlider = new Slider(0, 255, hi);
        var loValueLabel = new Label("" + lo);
        var hiValueLabel = new Label("" + hi);
        var loLabel = new Label(message("low") + " ");
        var hiLabel = new Label(message("high") + " ");
        preventEllipsis(loValueLabel, hiValueLabel, loLabel, hiLabel);
        loSlider.setBlockIncrement(10);
        hiSlider.setBlockIncrement(10);
        var pause = new PauseTransition(Duration.millis(500));
        loSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                contrastAdjustStrategy = contrastAdjustStrategy.withRange(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                stretchAndDisplay();
                loValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        hiSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                contrastAdjustStrategy = contrastAdjustStrategy.withRange(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                stretchAndDisplay();
                hiValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        container.getChildren().addAll(List.of(loLabel, loSlider, loValueLabel, hiLabel, hiSlider, hiValueLabel));
    }

    private HBox createCurveAdjustment() {
        var container = new HBox(8);
        container.setAlignment(Pos.CENTER_LEFT);
        var lo = (int) curveTransformStrategy.getIn() >> 8;
        var hi = (int) curveTransformStrategy.getOut() >> 8;
        var loSlider = new Slider(0, 255, lo);
        var hiSlider = new Slider(0, 255, hi);
        var inValueLabel = new Label("" + lo);
        var outValueLabel = new Label("" + hi);
        var loLabel = new Label(message("in") + " ");
        var hiLabel = new Label(message("out") + " ");
        preventEllipsis(loLabel, hiLabel, inValueLabel, outValueLabel);
        loSlider.setBlockIncrement(10);
        hiSlider.setBlockIncrement(10);
        var pause = new PauseTransition(Duration.millis(500));
        loSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                curveTransformStrategy = new CurveTransformStrategy(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                stretchAndDisplay();
                inValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        hiSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            double value = (Double) newValue;
            pause.setOnFinished(e -> {
                curveTransformStrategy = new CurveTransformStrategy(linValueOf(loSlider.getValue()), linValueOf(hiSlider.getValue()));
                stretchAndDisplay();
                outValueLabel.setText("" + (int) (value));
            });
            pause.playFromStart();
        });
        container.getChildren().addAll(List.of(loLabel, loSlider, inValueLabel, hiLabel, hiSlider, outValueLabel));
        configureHGrow(container.getChildren());
        return container;
    }

    private ImageWrapper stretch(ImageWrapper image) {
        var copy = image.copy();
        var strategy = determineStrategy();
        new StretchingChain(strategy).stretch(copy);
        return copy;
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

    private void updateDisplay(Image newImage, boolean resetZoom) {
        try {
            displayLock.lock();
            var imageForDimensions = stretchedImage != null ? stretchedImage : image;
            if (imageForDimensions != null) {
                dimensions.setText(imageForDimensions.width() + "x" + imageForDimensions.height());
            }
            imageView.setImage(newImage);
            imageView.setSolarDisk(image != null ? image.findMetadata(Ellipse.class).orElse(null) : null);
            if (resetZoom) {
                imageView.resetZoom();
            }
            saveButton.setDisable(false);
            if (description != null) {
                descriptionArea.setVisible(true);
                descriptionArea.setText(description);
            } else {
                descriptionArea.setVisible(false);
                descriptionArea.setPrefHeight(0);
            }
            maybeRunOnUpdate();
        } finally {
            displayLock.unlock();
        }
    }

    private void maybeRunOnUpdate() {
        if (onDisplayUpdate != null) {
            onDisplayUpdate.run();
        }
    }

    private void stretchAndDisplay(boolean resetZoom) {
        var unwrapped = this.image.unwrapToMemory();
        var transformedImage = applyTransformations(unwrapped);
        if (stretchingMode == StretchingMode.NO_STRETCH) {
            stretchedImage = transformedImage;
        } else {
            if (transformedImage instanceof ImageWrapper32 mono) {
                stretchedImage = stretch(mono);
            } else if (transformedImage instanceof RGBImage rgb) {
                stretchedImage = stretch(rgb);
            }
        }
        Platform.runLater(() -> stretchedImageDebounce.playFromStart());
        var writable = WritableImageSupport.asWritable(stretchedImage);
        Platform.runLater(() -> updateDisplay(writable, resetZoom));
    }

    private ImageWrapper applyTransformations(ImageWrapper image) {
        double correction = 0;
        if (!kind.cannotPerformManualRotation() && vflip) {
            image = Corrector.verticalFlip(image);
        }

        if (!kind.cannotPerformManualRotation()) {
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
        var result = stretchedImage == null ? image : stretchedImage;
        return result == null ? null : result.unwrapToMemory();
    }

    @Override
    public Node getRoot() {
        return root;
    }

    public synchronized void setImage(String baseName, ProcessParams params, ImageWrapper image, Path path) {
        this.processParams = params;
        this.image = FileBackedImage.wrap(image);
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
            FileBackedImage image,
            File imageFile
    ) {

    }

    private static Button createButton(String text) {
        var button = new Button(text);
        button.getStyleClass().add("image-viewer-button");
        button.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
        return button;
    }

    private enum StretchingMode {
        LINEAR, CURVE, NO_STRETCH;


        @Override
        public String toString() {
            return message("stretching." + name().toLowerCase());
        }
    }
}
