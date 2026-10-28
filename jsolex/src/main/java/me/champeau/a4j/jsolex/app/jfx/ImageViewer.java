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
import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.listeners.CloudModelFitDisplay;
import me.champeau.a4j.jsolex.app.listeners.MetadataComparator;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.FileCounts;
import me.champeau.a4j.jsolex.processing.expr.impl.Colorize;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.GlobeStyle;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudInversionResult;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudMap;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudMapRenderer;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.StretchRecipe;
import me.champeau.a4j.jsolex.processing.stretching.StretchStep;
import me.champeau.a4j.jsolex.processing.stretching.StretchStepKind;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataSupport;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.math.regression.Ellipse;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.JSolEx.newScene;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newStage;

/**
 * Controller for the image viewer.
 */
public class ImageViewer implements WithRootNode {
    private final Lock displayLock = new ReentrantLock();
    private Node root;
    private Stage stage;
    private final AtomicReference<ImageWrapper> stretchedImage = new AtomicReference<>();
    private final AtomicReference<StretchRecipe> activeRecipe = new AtomicReference<>(StretchRecipe.NONE);
    private final AtomicReference<StretchCache> stretchCache = new AtomicReference<>();
    private final AtomicReference<PendingStretch> pendingStretch = new AtomicReference<>();
    private final AtomicLong stretchGeneration = new AtomicLong();
    private final AtomicBoolean stretchRunning = new AtomicBoolean();
    private final AtomicBoolean stretchPending = new AtomicBoolean();
    private final AtomicBoolean pendingResetZoom = new AtomicBoolean();
    private final Map<StretchStepKind, StretchStep> stepsByKind = new EnumMap<>(StretchStepKind.class);
    private StretchRecipe initialRecipe = StretchRecipe.NONE;
    private int selectedIndex = -1;
    private StretchStepEditor stretchEditor;
    private boolean fitUntilUserZooms;
    private boolean unsavedChanges = true;
    private FileBackedImage image;
    private File imageFile;
    private GeneratedImageKind kind;
    private String baseName;
    private String description;
    private File imageName;
    private Map<String, ImageViewer> popupViews;
    private ProcessingEventListener broadcaster;
    private ProgressOperation operation;
    private ProcessParams processParams;
    private FileCounts fileCounts = FileCounts.SINGLE_FILE;

    private Label dimensions;
    private VBox stretchingParams;
    private ChoiceBox<StretchChoice> stretchChoice;
    private HBox stretchParameters;
    private HBox stretchChain;
    private HBox inlineStretchEditor;
    private HBox stretchEditorLine;
    private Button resetStretchButton;
    private ZoomableImageView imageView;
    private StackPane imageContainer;
    private ProgressIndicator loadingIndicator;

    private CheckBox correctAngleP;
    private Button saveButton;
    private String title;
    private Runnable onDisplayUpdate;
    private Consumer<ImageWrapper> onStretchedImageUpdate;
    private int rotation;
    private boolean vflip;
    private boolean firstShow = true;
    private ImageOverlayState overlays = ImageOverlayState.DEFAULT;
    private GlobeStyle overlayGlobeStyle;
    private boolean annotationWarningSuppressed;
    private Function<ImageWrapper, ImageViewer> annotatedCopyFactory;
    private OverlayPanel overlayPanel;
    private EarthDragLayer earthLayer;
    private DraggableTextOverlayLayer signatureLayer;
    private DraggableTextOverlayLayer obsDetailsLayer;
    private DraggableTextOverlayLayer solarParamsLayer;
    private final List<DraggableTextOverlayLayer> textAreaLayers = new ArrayList<>();

    private final ListProperty<ImageState> imageHistory = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final IntegerProperty currentImage = new SimpleIntegerProperty(0);
    private Set<ImageViewer> siblings;
    private PauseTransition stretchedImageDebounce;

    /**
     * Creates a new instance.
     */
    public ImageViewer() {
    }

    /**
     * Builds the image viewer scene graph and initializes it. This replaces the
     * former FXML-based construction to avoid the per-image cost of parsing the
     * FXML and reapplying inline styles on the JavaFX application thread.
     */
    public void init() {
        this.imageView = new ZoomableImageView();
        imageView.widthProperty().addListener((obs, oldValue, newValue) -> refitIfPending());
        imageView.heightProperty().addListener((obs, oldValue, newValue) -> refitIfPending());
        imageView.addEventFilter(ScrollEvent.ANY, e -> fitUntilUserZooms = false);
        imageView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> fitUntilUserZooms = false);
        imageView.getCtxMenu().setOnShowing(e -> populateContextMenu(imageView.getCtxMenu()));
        this.loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxWidth(80);
        loadingIndicator.setMaxHeight(80);
        loadingIndicator.setVisible(false);
        loadingIndicator.setMouseTransparent(true);
        this.imageContainer = new StackPane(imageView, loadingIndicator);
        imageView.setSelectionOverlayHost(imageContainer);
        this.stretchingParams = new VBox();
        stretchingParams.getStyleClass().add("image-viewer-toolbar");
        stretchingParams.setAlignment(Pos.CENTER_LEFT);
        stretchingParams.setSpacing(5);
        stretchingParams.setPadding(new Insets(10));
        var pane = new BorderPane();
        pane.getStyleClass().add("image-viewer-root");
        pane.setPadding(new Insets(8));
        pane.setTop(stretchingParams);
        pane.setCenter(imageContainer);
        BorderPane.setMargin(stretchingParams, new Insets(0, 0, 8, 0));
        this.root = pane;
        this.stretchedImageDebounce = new PauseTransition(Duration.seconds(10));
        this.stretchedImageDebounce.setOnFinished(e -> {
            stretchCache.set(null);
            var current = stretchedImage.get();
            if (current != null && !(current instanceof FileBackedImage)) {
                stretchedImage.compareAndSet(current, FileBackedImage.wrap(current));
            }
        });
    }

    /**
     * Returns the fit width property.
     *
     * @return the fit width property
     */
    public DoubleProperty fitWidthProperty() {
        return imageView.prefWidthProperty();
    }

    /**
     * Configures the image viewer.
     *
     * @param broadcaster the event broadcaster
     * @param operation   the progress operation
     * @param title       the title
     * @param baseName    the base name
     * @param kind        the generated image kind
     * @param description the description
     * @param image       the image
     * @param imageName   the image file name
     * @param params      the process parameters
     * @param popupViews  the popup views
     * @param siblings    the sibling viewers
     */
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
        this.siblings = siblings;
        this.baseName = baseName;
        this.description = description;
        this.imageName = imageName;
        this.popupViews = popupViews;
        if (description != null && !description.isBlank()) {
            var helpOverlay = new ImageHelpOverlay(title, description, kind);
            FxUtils.runLater(() -> {
                // Allow BorderPane to shrink with SplitPane
                if (root instanceof BorderPane bp) {
                    bp.setMinSize(0, 0);
                }
                imageContainer.setMinSize(0, 0);
                imageView.setMinSize(0, 0);
                // Make entire overlay mouse-transparent, then re-enable just for button
                helpOverlay.setMouseTransparent(true);
                imageContainer.getChildren().add(helpOverlay);
                // Add button separately with mouse events enabled
                var button = helpOverlay.createStandaloneButton();
                // Let the button ignore the mouse while a selection is being drawn or edited,
                // so the crop area can be extended into the corner the button occupies.
                button.mouseTransparentProperty().bind(imageView.selectingProperty());
                imageContainer.getChildren().add(button);
            });
        }
        if (image != null) {
            var wrapped = FileBackedImage.wrap(image);
            this.image = wrapped;
            initialRecipe = computeInitialRecipe();
            useRecipe(initialRecipe, initialRecipe.steps().size() - 1);
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
            });
        }
    }

    private void openInNewWindow() {
        var controller = new ImageViewer();
        controller.init();
        controller.setup(new ProcessingEventListener() {
        }, operation, title, baseName, kind, description, image, imageName, processParams, popupViews, siblings);
        var stage = newStage();
        var scene = newScene((Parent) controller.getRoot());
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
    }

    private void populateActionsMenu(MenuButton menu) {
        menu.getItems().clear();
        addCommonActionItems(menu.getItems(), false);
    }

    private void populateContextMenu(ContextMenu menu) {
        menu.getItems().clear();
        addCommonActionItems(menu.getItems(), true);
        menu.getItems().add(new SeparatorMenuItem());
        var showFile = new MenuItem(message("show.in.files"));
        showFile.disableProperty().bind(imageView.cannotOpenInExplorerProperty());
        showFile.setOnAction(e -> imageView.openInExplorer());
        menu.getItems().add(showFile);
    }

    private void addCommonActionItems(List<MenuItem> items, boolean includeCrop) {
        for (var kind : RectangleSelectionListener.ActionKind.values()) {
            if (!includeCrop && kind == RectangleSelectionListener.ActionKind.CROP) {
                continue;
            }
            if (imageView.supportsSelectionAction(kind)) {
                var item = new MenuItem(ZoomableImageView.actionLabel(kind));
                item.setOnAction(e -> imageView.startSelection(kind));
                items.add(item);
            }
        }
        if (!items.isEmpty()) {
            items.add(new SeparatorMenuItem());
        }
        var metadata = new MenuItem(message("show.metadata"));
        metadata.setOnAction(e -> showMetadataDialog());
        items.add(metadata);
        if (popupViews != null && !popupViews.containsKey(title)) {
            var openNew = new MenuItem(message("open.new.window"));
            openNew.setOnAction(e -> openInNewWindow());
            items.add(openNew);
        }
    }

    private void showMetadataDialog() {
        var stretched = getStretchedImage();
        if (stretched == null) {
            return;
        }
        var sections = new VBox();
        sections.getStyleClass().add("metadata-list");
        stretched.metadata().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(new MetadataComparator()))
                .map(e -> MetadataSupport.render(e.getKey(), e.getValue()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(block -> sections.getChildren().add(renderMetadataBlock(block)));

        var scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("metadata-scroll");

        var header = new Label(title);
        header.getStyleClass().add("metadata-header");
        var root = new BorderPane(scroll);
        root.setTop(header);
        root.getStyleClass().add("metadata-dialog");

        var stage = newStage();
        stage.setTitle(message("show.metadata"));
        stage.setScene(newScene(root));
        stage.setWidth(540);
        stage.setHeight(620);
        if (getRoot().getScene() != null) {
            stage.initOwner(getRoot().getScene().getWindow());
        }
        stage.show();
    }

    private VBox renderMetadataBlock(String block) {
        var group = new VBox();
        group.getStyleClass().add("metadata-group");
        for (var rawLine : block.split("\n")) {
            if (rawLine.isBlank()) {
                continue;
            }
            var trimmed = rawLine.strip();
            var label = new Label(trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed);
            label.setWrapText(true);
            label.getStyleClass().add(rawLine.startsWith(" ") || trimmed.startsWith("- ") ? "metadata-item" : "metadata-section");
            group.getChildren().add(label);
        }
        return group;
    }

    private void maybeAddMeasurementTool(Supplier<EventTarget> target) {
        image.findMetadata(Ellipse.class).ifPresent(ellipse -> {
            image.findMetadata(SolarParameters.class).ifPresent(solarParameters -> {
                EventHandler<ActionEvent> handler = e -> BackgroundOperations.async(() -> {
                    var prepared = getStretchedImage().copy();
                    if (prepared.unwrapToMemory() instanceof ImageWrapper32 mono) {
                        prepared = RGBImage.toRGB(mono);
                    }
                    var transformedEllipse = prepared.findMetadata(Ellipse.class).orElse(ellipse);
                    var op = operation.createChild(I18N.string(JSolEx.class, "measures", "preparing.measure.distance"));
                    broadcaster.onProgress(ProgressEvent.of(op));
                    var withGlobe = new ImageDraw(Map.of(), Broadcaster.NO_OP)
                            .doDrawGlobe(prepared, transformedEllipse, correctAngleP.isSelected() ? 0 : solarParameters.p(), solarParameters.b0(), Color.YELLOW, false, false, GlobeStyle.EQUATORIAL_COORDS, true);
                    try {
                        var preparedFile = TemporaryFolder.newTempFile("prepared", ".png").toFile();
                        var globeFile = TemporaryFolder.newTempFile("globe", ".png").toFile();
                        var imageSaver = new ImageSaver(CutoffStretchingStrategy.DEFAULT, processParams, Set.of(ImageFormat.PNG));
                        imageSaver.save(prepared, preparedFile);
                        imageSaver.save(withGlobe, globeFile);
                        var distanceMeasurementPane = new DistanceMeasurementPane(new Image(preparedFile.toURI().toString()), new Image(globeFile.toURI().toString()), transformedEllipse, solarParameters);
                        FxUtils.runLater(() -> {
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

    private void maybeAdd3DView(Supplier<EventTarget> target) {
        if (!OpenGLAvailability.isAvailable()) {
            return;
        }
        image.findMetadata(Ellipse.class).ifPresent(ignored -> {
            EventHandler<ActionEvent> handler = evt -> BackgroundOperations.async(() -> {
                var sourceDir = imageFile != null ? imageFile.getParentFile() : null;
                SingleImage3DViewer.show(getStretchedImage().unwrapToMemory(), title, processParams, sourceDir);
            });
            var targetNode = target.get();
            if (targetNode instanceof Button button) {
                button.setOnAction(handler);
            }
        });
    }

    /**
     * Returns the zoomable image view.
     *
     * @return the zoomable image view
     */
    public ZoomableImageView getImageView() {
        return imageView;
    }

    /**
     * Resets the zoom if it is set to zero.
     */
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

    /**
     * Sets the factory used to create a copy of this image in a new tab. The factory
     * receives the image to display and returns the newly created viewer.
     *
     * @param annotatedCopyFactory the factory
     */
    public void setAnnotatedCopyFactory(Function<ImageWrapper, ImageViewer> annotatedCopyFactory) {
        this.annotatedCopyFactory = annotatedCopyFactory;
    }

    /**
     * Suppresses the warning shown when saving an annotated image. This is used when
     * an untouched copy of the original already exists (for instance after the image
     * has been duplicated), so baking the annotations does not lose anything.
     */
    public void suppressAnnotationWarning() {
        this.annotationWarningSuppressed = true;
    }

    private void saveImage() {
        if (shouldWarnBeforeAnnotatedSave()) {
            promptAnnotatedSave();
            return;
        }
        doSaveImage();
    }

    private boolean shouldWarnBeforeAnnotatedSave() {
        return Platform.isFxApplicationThread()
                && hasActiveOverlays()
                && !annotationWarningSuppressed
                && annotatedCopyFactory != null;
    }

    private void promptAnnotatedSave() {
        var alert = AlertFactory.confirmation(message("annotation.save.message"));
        alert.setTitle(message("annotation.save.title"));
        alert.setHeaderText(message("annotation.save.header"));
        var copyButton = new ButtonType(message("annotation.save.copy"), ButtonBar.ButtonData.OTHER);
        var overwriteButton = new ButtonType(message("annotation.save.overwrite"), ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().setAll(copyButton, overwriteButton, ButtonType.CANCEL);
        var choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
            return;
        }
        if (choice.get() == copyButton) {
            saveAnnotationsOnCopy();
        } else {
            annotationWarningSuppressed = true;
            doSaveImage();
        }
    }

    private void saveAnnotationsOnCopy() {
        var copy = annotatedCopyFactory.apply(image.unwrapToMemory().copy());
        if (copy != null) {
            copy.transferOverlaysFrom(this);
            copy.doSaveImage();
        }
        removeAnnotations();
    }

    private void transferOverlaysFrom(ImageViewer source) {
        annotationWarningSuppressed = true;
        overlays = source.overlays;
        overlayGlobeStyle = source.overlayGlobeStyle;
        rotation = source.rotation;
        vflip = source.vflip;
        if (correctAngleP != null && source.correctAngleP != null) {
            correctAngleP.setSelected(source.correctAngleP.isSelected());
        }
        displayLock.lock();
        try {
            stepsByKind.putAll(source.stepsByKind);
            initialRecipe = source.initialRecipe;
            useRecipe(source.activeRecipe.get(), source.selectedIndex);
            invalidateStretchCache();
        } finally {
            displayLock.unlock();
        }
        FxUtils.runLater(() -> {
            refreshStretchControls();
            syncEarthLayer();
            syncSignatureLayer();
            syncObsDetailsLayer();
            syncSolarParamsLayer();
            syncTextAreaLayers();
            redrawOverlays();
        });
    }

    private void removeAnnotations() {
        overlays = ImageOverlayState.DEFAULT;
        overlayGlobeStyle = null;
        if (overlayPanel != null) {
            overlayPanel.updateState(overlays);
        }
        FxUtils.runLater(() -> {
            syncEarthLayer();
            syncSignatureLayer();
            syncObsDetailsLayer();
            syncSolarParamsLayer();
            syncTextAreaLayers();
            redrawOverlays();
        });
    }

    private void doSaveImage() {
        var toSave = hasActiveOverlays() ? applyOverlaysToStretchedImage() : getStretchedImage();
        var files = new ImageSaver(CutoffStretchingStrategy.DEFAULT, processParams, Configuration.getInstance().getImageFormats()).save(toSave, imageFile);
        files.stream()
                .findFirst()
                .ifPresent(file -> imageView.setImagePathForOpeningInExplorer(file.toPath()));
        unsavedChanges = false;
        FxUtils.runLater(() -> {
            refreshSaveButton();
            imageView.fileSaved();
        });
    }

    private StretchRecipe imageRecipe() {
        return image == null ? StretchRecipe.NONE : image.findMetadata(StretchRecipe.class).orElse(StretchRecipe.NONE);
    }

    private boolean hasSolarDisk() {
        return image != null && image.findMetadata(Ellipse.class).isPresent();
    }

    private StretchRecipe computeInitialRecipe() {
        var hasSolarDisk = hasSolarDisk();
        var recipe = new StretchRecipe(imageRecipe().steps().stream()
                .filter(step -> hasSolarDisk || !step.requiresSolarDisk())
                .toList());
        if (!recipe.isEmpty()) {
            return recipe;
        }
        if (kind == GeneratedImageKind.IMAGE_MATH || kind == GeneratedImageKind.COLLAGE || kind == GeneratedImageKind.CLOUD_MODEL) {
            return StretchRecipe.NONE;
        }
        return StretchRecipe.of(StretchStep.defaultsOf(StretchStepKind.LINEAR));
    }

    private void useRecipe(StretchRecipe recipe, int index) {
        for (var step : recipe.steps()) {
            stepsByKind.put(step.kind(), step);
        }
        selectedIndex = recipe.isEmpty() ? -1 : Math.clamp(index, 0, recipe.steps().size() - 1);
        activeRecipe.set(recipe);
    }

    private StretchStep selectedStep() {
        return selectedIndex < 0 ? null : activeRecipe.get().steps().get(selectedIndex);
    }

    private void changeRecipe(StretchRecipe recipe, int index) {
        useRecipe(recipe, index);
        refreshStretchControls();
        stretchAndDisplay();
    }

    private void resetStretch() {
        var selected = selectedStep();
        if (selected == null) {
            return;
        }
        var initialSteps = initialRecipe.steps();
        var isInitialStep = selectedIndex < initialSteps.size() && initialSteps.get(selectedIndex).kind() == selected.kind();
        var reset = isInitialStep ? initialSteps.get(selectedIndex) : StretchStep.defaultsOf(selected.kind());
        changeRecipe(activeRecipe.get().replace(selectedIndex, reset), selectedIndex);
    }

    private void addStretch() {
        var recipe = activeRecipe.get().append(StretchStep.defaultsOf(StretchStepKind.LINEAR));
        changeRecipe(recipe, recipe.steps().size() - 1);
    }

    private void removeStretch(int index) {
        var recipe = activeRecipe.get().remove(index);
        changeRecipe(recipe, index <= selectedIndex ? selectedIndex - 1 : selectedIndex);
    }

    private void selectStretch(int index) {
        if (index != selectedIndex) {
            if (stretchEditor != null) {
                stretchEditor.flush();
            }
            selectedIndex = index;
            refreshStretchControls();
        }
    }

    private List<StretchChoice> stretchChoices() {
        var hasSolarDisk = hasSolarDisk();
        var choices = new ArrayList<StretchChoice>();
        if (activeRecipe.get().steps().size() <= 1) {
            choices.add(new StretchChoice(null));
        }
        for (var stepKind : StretchStepKind.values()) {
            if (hasSolarDisk || !stepKind.requiresSolarDisk()) {
                choices.add(new StretchChoice(stepKind));
            }
        }
        return choices;
    }

    private void onStretchChoiceChanged(StretchChoice choice) {
        if (choice == null) {
            return;
        }
        var selected = selectedStep();
        if (choice.kind() == (selected == null ? null : selected.kind())) {
            return;
        }
        if (choice.kind() == null) {
            changeRecipe(StretchRecipe.NONE, -1);
        } else {
            var step = stepsByKind.computeIfAbsent(choice.kind(), StretchStep::defaultsOf);
            var recipe = activeRecipe.get();
            changeRecipe(selected == null ? StretchRecipe.of(step) : recipe.replace(selectedIndex, step), Math.max(0, selectedIndex));
        }
    }

    private void onStretchSettingsChanged(int index, StretchStep step) {
        var steps = activeRecipe.get().steps();
        if (index >= steps.size() || steps.get(index).kind() != step.kind()) {
            return;
        }
        stepsByKind.put(step.kind(), step);
        activeRecipe.set(activeRecipe.get().replace(index, step));
        stretchAndDisplay();
    }

    private void refreshStretchControls() {
        if (stretchChoice == null) {
            return;
        }
        var steps = activeRecipe.get().steps();
        var chained = steps.size() > 1;
        var chips = new ArrayList<Node>();
        if (chained) {
            for (int i = 0; i < steps.size(); i++) {
                if (i > 0) {
                    var arrow = new Label("→");
                    arrow.getStyleClass().add("stretch-chain-arrow");
                    chips.add(arrow);
                }
                chips.add(createStretchChip(i, steps.get(i)));
            }
        }
        stretchChain.getChildren().setAll(chips);
        setShown(stretchChain, chained);
        var selected = selectedStep();
        setShown(stretchEditorLine, selected != null);
        resetStretchButton.setDisable(selected == null);
        var editorIndent = stretchEditorLine.getChildren().getFirst();
        if (chained) {
            inlineStretchEditor.getChildren().clear();
            stretchEditorLine.getChildren().setAll(editorIndent, stretchChoice, stretchParameters);
        } else {
            inlineStretchEditor.getChildren().setAll(stretchChoice);
            stretchEditorLine.getChildren().setAll(editorIndent, stretchParameters);
        }
        stretchChoice.getItems().setAll(stretchChoices());
        stretchChoice.setValue(new StretchChoice(selected == null ? null : selected.kind()));
        showStretchParameters();
    }

    private HBox createStretchChip(int index, StretchStep step) {
        var name = new Label(StretchStepEditor.stepLabel(step.kind()));
        var remove = new Label("×");
        remove.getStyleClass().add("stretch-chip-remove");
        remove.setOnMouseClicked(e -> {
            e.consume();
            removeStretch(index);
        });
        var chip = new HBox(6, name, remove);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("stretch-chip");
        if (index == selectedIndex) {
            chip.getStyleClass().add("stretch-chip-selected");
        }
        chip.setOnMouseClicked(e -> selectStretch(index));
        remove.setTooltip(new Tooltip(message("stretch.remove")));
        return chip;
    }

    private static Tooltip createHelpTooltip(StretchStepKind stepKind) {
        var help = new Tooltip(StretchStepEditor.stepHelp(stepKind));
        help.setWrapText(true);
        help.setMaxWidth(420);
        return help;
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void showStretchParameters() {
        if (stretchParameters == null) {
            return;
        }
        if (stretchEditor != null) {
            stretchEditor.detach();
            stretchEditor = null;
        }
        var selected = selectedStep();
        if (selected == null) {
            stretchParameters.getChildren().clear();
        } else {
            var index = selectedIndex;
            stretchEditor = new StretchStepEditor(selected, step -> onStretchSettingsChanged(index, step));
            var layout = stretchEditor.inlineLayout();
            HBox.setHgrow(layout, Priority.ALWAYS);
            stretchParameters.getChildren().setAll(layout);
        }
        stretchChoice.setTooltip(selected == null ? null : createHelpTooltip(selected.kind()));
    }

    private void configureStretching() {
        try {
            displayLock.lock();
            var line2 = new HBox(8);
            line2.setAlignment(Pos.CENTER_LEFT);
            var line3 = new HBox(8);
            line3.setAlignment(Pos.CENTER_LEFT);
            var choice = new ChoiceBox<StretchChoice>();
            choice.valueProperty().addListener((obs, oldValue, newValue) -> onStretchChoiceChanged(newValue));
            var parameters = new HBox(8);
            parameters.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(parameters, Priority.ALWAYS);
            var line1 = new HBox(8);
            line1.setAlignment(Pos.CENTER_LEFT);
            var stretchingLabel = new Label(message("stretching.label"));
            preventEllipsis(stretchingLabel);
            var chain = new HBox(6);
            chain.setAlignment(Pos.CENTER_LEFT);
            var inlineEditor = new HBox(8);
            inlineEditor.setAlignment(Pos.CENTER_LEFT);
            var stretchSpacer = new Region();
            HBox.setHgrow(stretchSpacer, Priority.ALWAYS);
            var addStretchButton = createButton(message("stretch.add"));
            addStretchButton.setOnAction(e -> addStretch());
            var editorIndent = new Region();
            editorIndent.minWidthProperty().bind(stretchingLabel.widthProperty());
            var editorLine = new HBox(8, editorIndent);
            editorLine.setAlignment(Pos.CENTER_LEFT);
            var reset = createButton(message("reset"));
            reset.setOnAction(event -> resetStretch());
            line1.getChildren().addAll(stretchingLabel, chain, inlineEditor, addStretchButton, stretchSpacer);
            saveButton = createButton("💾 " + message("save"));
            saveButton.setOnAction(e -> saveImage());
            saveButton.disableProperty().addListener((obs, was, disabled) -> updateSaveButtonStyle(disabled));
            updateSaveButtonStyle(saveButton.isDisable());
            var openExplorerButton = createButton("📂 " + message("open.in.explorer"));
            openExplorerButton.setOnAction(e -> imageView.openInExplorer());
            openExplorerButton.disableProperty().bind(imageView.cannotOpenInExplorerProperty());
            dimensions = new Label();
            var zoomLabel = new Label(I18N.string(JSolEx.class, "common", "zoom"));
            var zoomMinus = createIconButton("-");
            zoomMinus.setOnAction(evt -> {
                clearPendingAlignment();
                imageView.setZoomAroundCenter(imageView.getZoom() * 0.8);
            });
            var zoomPlus = createIconButton("+");
            zoomPlus.setOnAction(evt -> {
                clearPendingAlignment();
                imageView.setZoomAroundCenter(imageView.getZoom() * 1.2);
            });
            var coordinatesLabel = new Label();
            imageView.setOnZoomChanged(z -> {
                if (earthLayer != null) {
                    earthLayer.applyLayout();
                }
                if (signatureLayer != null) {
                    signatureLayer.applyLayout();
                }
                if (obsDetailsLayer != null) {
                    obsDetailsLayer.applyLayout();
                }
                if (solarParamsLayer != null) {
                    solarParamsLayer.applyLayout();
                }
                for (var layer : textAreaLayers) {
                    layer.applyLayout();
                }
            });
            imageView.setCoordinatesListener((x, y) -> {
                var extra = "";
                var imageForPixelValue = displayedImage();
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
                var cloudValue = cloudModelValue(xx, yy);
                if (cloudValue != null) {
                    extra = ", " + cloudValue;
                }
                coordinatesLabel.setText("(" + xx + ", " + yy + extra + ")");
            });
            if (broadcaster instanceof CloudModelFitDisplay display && image.findMetadata(CloudInversionResult.class).isPresent()) {
                imageView.setClickListener((x, y) -> image.findMetadata(CloudInversionResult.class)
                        .ifPresent(current -> display.showCloudModelFit(current, x, y)));
            }
            var applyNextTime = createIconButton("✔");
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
            fitButton.setOnAction(evt -> {
                clearPendingAlignment();
                imageView.resetZoom(true);
            });
            var fitToCenter = createButton("→fit←");
            fitToCenter.disableProperty().bind(imageView.canFitToCenterProperty().map(e -> !e));
            fitToCenter.setOnAction(evt -> {
                clearPendingAlignment();
                imageView.fitToCenter();
            });
            var oneToOneFit = createButton("1:1");
            oneToOneFit.setOnAction(evt -> {
                clearPendingAlignment();
                imageView.oneToOneZoomAndCenter();
            });
            var leftRotate = createIconButton("↶");
            leftRotate.setOnAction(evt -> {
                rotation = (rotation - 1) % 4;
                applyNextTime.setDisable(false);
                stretchAndDisplay();
            });
            leftRotate.disableProperty().set(kind.cannotPerformManualRotation());
            var rightRotate = createIconButton("↷");
            rightRotate.setOnAction(evt -> {
                rotation = (rotation + 1) % 4;
                applyNextTime.setDisable(false);

                stretchAndDisplay();
            });
            rightRotate.disableProperty().set(kind.cannotPerformManualRotation());
            var verticalMirror = createIconButton("⇅");
            verticalMirror.setOnAction(evt -> {
                vflip = !vflip;
                rotation = -rotation;
                applyNextTime.setDisable(false);
                stretchAndDisplay();
            });
            verticalMirror.disableProperty().set(kind.cannotPerformManualRotation());
            var horizontalMirror = createIconButton("⇄");
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
            var measureButton = createIconButton("⚖");
            measureButton.setDisable(true);
            maybeAddMeasurementTool(() -> {
                measureButton.setDisable(false);
                return measureButton;
            });
            var view3dButton = createIconButton("3D");
            view3dButton.setVisible(false);
            view3dButton.setManaged(false);
            maybeAdd3DView(() -> {
                view3dButton.setVisible(true);
                view3dButton.setManaged(true);
                return view3dButton;
            });
            var overlayButton = createIconButton("⊕");
            overlayButton.setOnAction(evt -> toggleOverlayPanel(overlayButton));
            var cropButton = createIconButton("⛶");
            cropButton.setOnAction(evt -> imageView.startCropSelection());
            var actionsMenu = new MenuButton(message("actions.menu"));
            actionsMenu.getStyleClass().add("image-viewer-button");
            actionsMenu.setOnShowing(e -> populateActionsMenu(actionsMenu));
            populateActionsMenu(actionsMenu);
            line1.getChildren().addAll(reset, prevButton, nextButton);
            var readoutSpacer = new Region();
            line2.getChildren().addAll(zoomLabel, zoomMinus, zoomPlus, fitButton, fitToCenter, oneToOneFit, verticalSeparator(), saveButton, openExplorerButton);
            line3.getChildren().addAll(correctAngleP, leftRotate, rightRotate, verticalMirror, horizontalMirror, applyNextTime, verticalSeparator(), cropButton, overlayButton, measureButton, view3dButton, verticalSeparator(), actionsMenu, readoutSpacer, dimensions, coordinatesLabel);
            var titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold");
            var alignButton = createIconButton("⌖");
            alignButton.setOnAction(evt -> {
                onDisplayUpdate = null;
                for (var viewer : siblings) {
                    if (viewer != this) {
                        viewer.onDisplayUpdate = () -> {
                            viewer.fitUntilUserZooms = false;
                            viewer.imageView.alignWith(imageView);
                        };
                    }
                }
            });

            FxUtils.runLater(() -> {
                alignButton.setTooltip(new Tooltip(message("align.images")));
                cropButton.setTooltip(new Tooltip(message("crop")));
                leftRotate.setTooltip(new Tooltip(message("rotate.left")));
                rightRotate.setTooltip(new Tooltip(message("rotate.right")));
                verticalMirror.setTooltip(new Tooltip(message("vertical.flip")));
                horizontalMirror.setTooltip(new Tooltip(message("horizontal.flip")));
                applyNextTime.setTooltip(new Tooltip(message("apply.next.time")));
                measureButton.setTooltip(new Tooltip(message("measure.button.tooltip")));
                view3dButton.setTooltip(new Tooltip(message("view3d.button.tooltip")));
                overlayButton.setTooltip(new Tooltip(message("overlay.tooltip")));
                var titleBox = new HBox(alignButton, titleLabel, new Label("(" + imageFile.getName() + ")"));
                titleBox.setSpacing(4);
                titleBox.setAlignment(Pos.CENTER_LEFT);
                stretchChoice = choice;
                stretchParameters = parameters;
                stretchChain = chain;
                inlineStretchEditor = inlineEditor;
                stretchEditorLine = editorLine;
                resetStretchButton = reset;
                stretchingParams.getChildren().addAll(titleBox, line1, editorLine, line2, line3);
                refreshStretchControls();
                configureHGrow(line2.getChildren().stream().filter(e -> !(e instanceof Slider) && !(e instanceof Separator)).toList());
                configureHGrow(line3.getChildren().stream().filter(e -> !(e instanceof Slider) && !(e instanceof Separator)).toList());
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

    void display() {
        if (image != null && firstShow) {
            firstShow = false;
            stretchAndDisplay(true);
        } else if (image != null && stretchedImage.get() == null) {
            stretchAndDisplay(false);
        } else if (image != null) {
            BackgroundOperations.async(() -> {
                var currentStretchedImage = stretchedImage.get();
                maybeRunOnUpdate();
                if (onStretchedImageUpdate != null && currentStretchedImage != null) {
                    onStretchedImageUpdate.accept(currentStretchedImage.unwrapToMemory());
                }
            });
        }
    }

    private void showLoadingIndicator() {
        if (loadingIndicator != null) {
            if (Platform.isFxApplicationThread()) {
                loadingIndicator.setVisible(true);
            } else {
                FxUtils.runLater(() -> loadingIndicator.setVisible(true));
            }
        }
    }

    private void hideLoadingIndicator() {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(false);
        }
    }

    private void stretchAndDisplay() {
        unsavedChanges = true;
        invalidateStretchCache();
        stretchAndDisplay(false);
    }

    private void redrawOverlays() {
        stretchAndDisplay(false);
    }

    private void updateDisplay(Image newImage, boolean resetZoom) {
        try {
            displayLock.lock();
            var imageForDimensions = displayedImage();
            if (imageForDimensions != null) {
                dimensions.setText(imageForDimensions.width() + "x" + imageForDimensions.height());
            }
            imageView.setImage(newImage);
            imageView.setSolarDisk(image != null ? image.findMetadata(Ellipse.class).orElse(null) : null);
            if (resetZoom) {
                fitUntilUserZooms = true;
                imageView.resetZoom();
            }
            refreshSaveButton();
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

    private void refitIfPending() {
        if (fitUntilUserZooms && imageView.getWidth() > 0 && imageView.getHeight() > 0) {
            imageView.resetZoom();
        }
    }

    private void clearPendingAlignment() {
        fitUntilUserZooms = false;
        onDisplayUpdate = null;
        for (var viewer : siblings) {
            viewer.onDisplayUpdate = null;
        }
    }

    private void stretchAndDisplay(boolean resetZoom) {
        var current = stretchedImage.get();
        if (current != null) {
            showStretched(current, resetZoom);
            return;
        }
        if (resetZoom) {
            pendingResetZoom.set(true);
        }
        stretchPending.set(true);
        startPendingStretches();
    }

    private void startPendingStretches() {
        if (stretchRunning.compareAndSet(false, true)) {
            showLoadingIndicator();
            BackgroundOperations.async(this::runPendingStretches);
        }
    }

    private void runPendingStretches() {
        try {
            while (stretchPending.getAndSet(false)) {
                var resetZoom = pendingResetZoom.getAndSet(false);
                var generation = stretchGeneration.get();
                var stretched = computeStretchedImage();
                if (generation == stretchGeneration.get()) {
                    showStretched(stretched, resetZoom);
                } else if (resetZoom) {
                    pendingResetZoom.set(true);
                }
            }
        } finally {
            stretchRunning.set(false);
            if (stretchPending.get()) {
                startPendingStretches();
            } else {
                FxUtils.runLater(() -> {
                    if (!stretchRunning.get()) {
                        hideLoadingIndicator();
                    }
                });
            }
        }
    }

    private void showStretched(ImageWrapper stretched, boolean resetZoom) {
        if (onStretchedImageUpdate != null) {
            onStretchedImageUpdate.accept(stretched);
        }
        FxUtils.runLater(() -> stretchedImageDebounce.playFromStart());
        var writable = renderForDisplay(stretched);
        FxUtils.runLater(() -> updateDisplay(writable, resetZoom));
    }

    private ImageWrapper computeStretchedImage() {
        while (true) {
            var current = stretchedImage.get();
            if (current != null) {
                return current;
            }
            var generation = stretchGeneration.get();
            var pending = pendingStretch.get();
            if (pending != null && pending.generation() == generation) {
                return pending.result().join();
            }
            var computation = new PendingStretch(generation, new CompletableFuture<>());
            if (pendingStretch.compareAndSet(pending, computation)) {
                return runStretch(computation);
            }
        }
    }

    private ImageWrapper runStretch(PendingStretch computation) {
        try {
            var result = applyTransformations(stretch(image, activeRecipe.get()));
            var generation = computation.generation();
            if (generation == stretchGeneration.get()) {
                stretchedImage.compareAndSet(null, result);
                if (generation != stretchGeneration.get()) {
                    stretchedImage.compareAndSet(result, null);
                } else {
                    FxUtils.runLater(() -> stretchedImageDebounce.playFromStart());
                }
            }
            computation.result().complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            computation.result().completeExceptionally(e);
            throw e;
        } finally {
            pendingStretch.compareAndSet(computation, null);
        }
    }

    private ImageWrapper stretch(FileBackedImage source, StretchRecipe recipe) {
        var unwrapped = source.unwrapToMemory();
        if (recipe.isEmpty() || !(unwrapped instanceof ImageWrapper32 || unwrapped instanceof RGBImage)) {
            return unwrapped.findMetadata(StretchRecipe.class).isPresent() ? StretchRecipe.withoutRecipe(unwrapped) : unwrapped;
        }
        var lastStep = recipe.steps().getLast();
        var prefix = recipe.prefix(recipe.steps().size() - 1);
        var cache = stretchCache.get();
        ImageWrapper work;
        if (cache != null && cache.source() == source && cache.prefix().equals(prefix)) {
            work = cache.image().copy();
        } else {
            work = StretchRecipe.withoutRecipe(unwrapped);
            if (!prefix.isEmpty()) {
                prefix.applyTo(work);
                stretchCache.set(new StretchCache(source, prefix, work.copy()));
            }
        }
        StretchRecipe.of(lastStep).applyTo(work);
        return work;
    }

    private Image renderForDisplay(ImageWrapper stretched) {
        return OverlayRenderer.renderToFx(
                applyColorize(stretched),
                overlays,
                effectiveGlobeStyle(),
                processParams,
                kind,
                baseImageIsPCorrected()
        );
    }

    private ImageWrapper displayedImage() {
        var current = stretchedImage.get();
        return current != null ? current : image;
    }

    private ImageWrapper applyColorize(ImageWrapper img) {
        if (img == null || overlays == null || !overlays.isColorizeActive()) {
            return img;
        }
        if (!(img.unwrapToMemory() instanceof ImageWrapper32 mono)) {
            return img;
        }
        ImageWrapper colorized;
        if (overlays.colorizeProfile() != null) {
            var result = new Colorize(Map.of(), Broadcaster.NO_OP)
                    .colorize(Map.of("img", mono, "profile", overlays.colorizeProfile()));
            if (!(result instanceof ImageWrapper colorizedImage)) {
                return img;
            }
            colorized = colorizedImage;
        } else {
            var rgb = hexToRgb(overlays.colorizeColor());
            if (rgb == null) {
                return img;
            }
            colorized = RGBImage.fromMono(mono, data -> Colorize.doColorize(mono.width(), mono.height(), data.data(), rgb));
        }
        if (overlays.colorizePreserveBrightness() && colorized.unwrapToMemory() instanceof RGBImage rgbImage) {
            return preserveBrightness(mono, rgbImage);
        }
        return colorized;
    }

    private static RGBImage preserveBrightness(ImageWrapper32 mono, RGBImage rgb) {
        var src = mono.data();
        var r = rgb.r();
        var g = rgb.g();
        var b = rgb.b();
        for (int y = 0; y < rgb.height(); y++) {
            for (int x = 0; x < rgb.width(); x++) {
                var target = src[y][x];
                var luminance = 0.299f * r[y][x] + 0.587f * g[y][x] + 0.114f * b[y][x];
                if (luminance > 1e-4f) {
                    var scale = target / luminance;
                    r[y][x] = Math.min(Constants.MAX_PIXEL_VALUE, r[y][x] * scale);
                    g[y][x] = Math.min(Constants.MAX_PIXEL_VALUE, g[y][x] * scale);
                    b[y][x] = Math.min(Constants.MAX_PIXEL_VALUE, b[y][x] * scale);
                } else {
                    r[y][x] = target;
                    g[y][x] = target;
                    b[y][x] = target;
                }
            }
        }
        return rgb;
    }

    private static int[] hexToRgb(String hex) {
        if (hex == null || hex.length() != 6) {
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void invalidateStretchCache() {
        stretchGeneration.incrementAndGet();
        stretchedImage.set(null);
    }

    private String defaultColorizeProfile() {
        if (processParams != null && processParams.spectrumParams() != null && processParams.spectrumParams().ray() != null) {
            return processParams.spectrumParams().ray().label();
        }
        return null;
    }

    private GlobeStyle effectiveGlobeStyle() {
        if (overlayGlobeStyle != null) {
            return overlayGlobeStyle;
        }
        if (processParams != null && processParams.extraParams() != null && processParams.extraParams().globeStyle() != null) {
            return processParams.extraParams().globeStyle();
        }
        return GlobeStyle.EQUATORIAL_COORDS;
    }

    private boolean baseImageIsPCorrected() {
        return correctAngleP != null && correctAngleP.isSelected();
    }

    private void toggleOverlayPanel(Node anchor) {
        if (overlayPanel == null) {
            boolean hasEllipse = image != null && image.findMetadata(Ellipse.class).isPresent();
            boolean colorizable = image != null && image.unwrapToMemory() instanceof ImageWrapper32;
            overlayPanel = new OverlayPanel(
                    kind,
                    hasEllipse,
                    colorizable,
                    defaultColorizeProfile(),
                    overlays,
                    effectiveGlobeStyle(),
                    newState -> {
                        overlays = newState;
                        markEdited();
                        if (overlays.drawEarth() && overlays.earthX() == null) {
                            placeEarthDefault();
                            if (overlayPanel != null) {
                                overlayPanel.updateState(overlays);
                            }
                        }
                        FxUtils.runLater(() -> {
                            syncEarthLayer();
                            syncSignatureLayer();
                            syncObsDetailsLayer();
                            syncSolarParamsLayer();
                            syncTextAreaLayers();
                        });
                        BackgroundOperations.async(this::redrawOverlays);
                    },
                    style -> {
                        overlayGlobeStyle = style;
                        markEdited();
                        BackgroundOperations.async(this::redrawOverlays);
                    },
                    () -> {
                        overlays = overlays.withEarthPosition(null, null);
                        markEdited();
                        if (overlayPanel != null) {
                            overlayPanel.updateState(overlays);
                        }
                        FxUtils.runLater(this::syncEarthLayer);
                    },
                    () -> {
                        overlays = overlays.withSignaturePosition(null, null);
                        markEdited();
                        if (overlayPanel != null) {
                            overlayPanel.updateState(overlays);
                        }
                        FxUtils.runLater(this::syncSignatureLayer);
                    },
                    () -> {
                        overlays = overlays.withObsDetailsPosition(null, null);
                        markEdited();
                        if (overlayPanel != null) {
                            overlayPanel.updateState(overlays);
                        }
                        FxUtils.runLater(this::syncObsDetailsLayer);
                    },
                    () -> {
                        overlays = overlays.withSolarParamsPosition(null, null);
                        markEdited();
                        if (overlayPanel != null) {
                            overlayPanel.updateState(overlays);
                        }
                        FxUtils.runLater(this::syncSolarParamsLayer);
                    }
            );
            overlayPanel.setOnShownStateChanged(shown -> setLayersInteractive(shown));
        }
        overlayPanel.toggle(anchor);
    }

    private boolean isOverlayPanelShown() {
        return overlayPanel != null && overlayPanel.isShowing();
    }

    private void setLayersInteractive(boolean interactive) {
        if (earthLayer != null) {
            earthLayer.setMouseTransparent(!interactive);
            earthLayer.setCursor(interactive ? javafx.scene.Cursor.MOVE : javafx.scene.Cursor.DEFAULT);
        }
        var textLayers = new ArrayList<>(textAreaLayers);
        textLayers.add(signatureLayer);
        textLayers.add(obsDetailsLayer);
        textLayers.add(solarParamsLayer);
        for (var layer : textLayers) {
            if (layer != null) {
                layer.setMouseTransparent(!interactive);
                layer.setCursor(interactive ? javafx.scene.Cursor.MOVE : javafx.scene.Cursor.DEFAULT);
            }
        }
    }

    public void hideOverlayPanel() {
        if (overlayPanel != null) {
            overlayPanel.hide();
        }
    }

    private void placeEarthDefault() {
        var ellipse = image != null ? image.findMetadata(Ellipse.class).orElse(null) : null;
        if (ellipse == null) {
            return;
        }
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var semiAxis = ellipse.semiAxis();
        var radius = (semiAxis.a() + semiAxis.b()) / 2d;
        double sunDiameterPixels = semiAxis.a() + semiAxis.b();
        double earthSizePixels = sunDiameterPixels * (12_742.0 / 1_391_400.0);
        int imageWidth = image.width();
        int imageHeight = image.height();
        int maxX = Math.max(0, imageWidth - (int) Math.ceil(earthSizePixels));
        int maxY = Math.max(0, imageHeight - (int) Math.ceil(earthSizePixels));
        int defaultX = Math.max(0, Math.min(maxX, (int) (cx - radius * 0.85)));
        int defaultY = Math.max(0, Math.min(maxY, (int) (cy + radius * 0.65)));
        overlays = overlays.withEarthPosition(defaultX, defaultY);
    }

    private void syncEarthLayer() {
        var pane = imageView.getImagePane();
        if (overlays == null || !overlays.drawEarth() || overlays.earthX() == null || overlays.earthY() == null) {
            if (earthLayer != null) {
                pane.getChildren().remove(earthLayer);
                earthLayer = null;
            }
            return;
        }
        var ellipse = image != null ? image.findMetadata(Ellipse.class).orElse(null) : null;
        if (ellipse == null) {
            return;
        }
        if (earthLayer == null) {
            earthLayer = new EarthDragLayer(imageView, ellipse, (x, y) -> {
                overlays = overlays.withEarthPosition(x, y);
                if (overlayPanel != null) {
                    overlayPanel.updateState(overlays);
                }
                markEdited();
            });
            pane.getChildren().add(earthLayer);
        }
        earthLayer.place(overlays.earthX(), overlays.earthY());
        setLayersInteractive(isOverlayPanelShown());
    }

    public void setFileCounts(FileCounts fileCounts) {
        this.fileCounts = fileCounts;
        if (overlays != null && overlays.drawObservationDetails()) {
            FxUtils.runLater(this::syncObsDetailsLayer);
        }
    }

    private void syncObsDetailsLayer() {
        var pane = imageView.getImagePane();
        if (overlays == null || !overlays.drawObservationDetails() || processParams == null) {
            if (obsDetailsLayer != null) {
                pane.getChildren().remove(obsDetailsLayer);
                obsDetailsLayer = null;
            }
            return;
        }
        if (obsDetailsLayer == null) {
            obsDetailsLayer = new DraggableTextOverlayLayer(imageView, (x, y) -> {
                overlays = overlays.withObsDetailsPosition(x, y);
                if (overlayPanel != null) {
                    overlayPanel.updateState(overlays);
                }
                markEdited();
            });
            pane.getChildren().add(obsDetailsLayer);
        }
        var draw = new ImageDraw(Map.of(ProcessParams.class, processParams, FileCounts.class, fileCounts), Broadcaster.NO_OP);
        var content = draw.computeObservationDetailsContent(displayedImage(), overlays.obsDetailsTemplate());
        obsDetailsLayer.update(content, null, autoFontSize(), overlays.obsDetailsColor());
        if (overlays.obsDetailsX() == null || overlays.obsDetailsY() == null) {
            placeObsDetailsDefault();
        }
        obsDetailsLayer.place(overlays.obsDetailsX(), overlays.obsDetailsY());
        setLayersInteractive(isOverlayPanelShown());
    }

    private void syncSolarParamsLayer() {
        var pane = imageView.getImagePane();
        if (overlays == null || !overlays.drawSolarParameters() || image == null) {
            if (solarParamsLayer != null) {
                pane.getChildren().remove(solarParamsLayer);
                solarParamsLayer = null;
            }
            return;
        }
        if (solarParamsLayer == null) {
            solarParamsLayer = new DraggableTextOverlayLayer(imageView, (x, y) -> {
                overlays = overlays.withSolarParamsPosition(x, y);
                if (overlayPanel != null) {
                    overlayPanel.updateState(overlays);
                }
                markEdited();
            });
            pane.getChildren().add(solarParamsLayer);
        }
        var draw = new ImageDraw(Map.of(ProcessParams.class, processParams), Broadcaster.NO_OP);
        var content = draw.computeSolarParametersContent(displayedImage());
        solarParamsLayer.update(content, null, autoFontSize(), overlays.solarParamsColor());
        if (overlays.solarParamsX() == null || overlays.solarParamsY() == null) {
            placeSolarParamsDefault();
        }
        solarParamsLayer.place(overlays.solarParamsX(), overlays.solarParamsY());
        setLayersInteractive(isOverlayPanelShown());
    }

    private int autoFontSize() {
        var ellipse = image != null ? image.findMetadata(Ellipse.class).orElse(null) : null;
        double radius = ellipse != null
                ? (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d
                : (image != null ? image.width() / 2d : 800);
        return (int) Math.max(12, Math.round(radius * 1.2 / 48));
    }

    private void placeObsDetailsDefault() {
        if (image == null) {
            return;
        }
        int margin = (int) Math.max(40, image.width() * 0.03);
        overlays = overlays.withObsDetailsPosition(margin, margin);
    }

    private void placeSolarParamsDefault() {
        if (image == null) {
            return;
        }
        int margin = (int) Math.max(40, image.width() * 0.03);
        int fontSize = autoFontSize();
        int longestLine = 22;
        int estimatedWidth = (int) Math.round(longestLine * fontSize * 0.6);
        int defaultX = Math.max(margin, image.width() - margin - estimatedWidth);
        overlays = overlays.withSolarParamsPosition(defaultX, margin);
    }

    private void syncSignatureLayer() {
        var pane = imageView.getImagePane();
        if (overlays == null || !overlays.drawSignature() || overlays.signatureText() == null) {
            if (signatureLayer != null) {
                pane.getChildren().remove(signatureLayer);
                signatureLayer = null;
            }
            return;
        }
        if (signatureLayer == null) {
            signatureLayer = new DraggableTextOverlayLayer(imageView, (x, y) -> {
                overlays = overlays.withSignaturePosition(x, y);
                if (overlayPanel != null) {
                    overlayPanel.updateState(overlays);
                }
                markEdited();
            });
            pane.getChildren().add(signatureLayer);
        }
        signatureLayer.update(overlays.signatureText(), overlays.signatureFontFamily(), overlays.signatureFontSize(), overlays.signatureColor(), overlays.signatureFontWeight());
        if (overlays.signatureX() == null || overlays.signatureY() == null) {
            placeSignatureDefault();
        }
        var sx = clampToImageWidth(overlays.signatureX() != null ? overlays.signatureX() : 0);
        var sy = clampToImageHeight(overlays.signatureY() != null ? overlays.signatureY() : 0);
        if ((overlays.signatureX() != null && sx != overlays.signatureX()) || (overlays.signatureY() != null && sy != overlays.signatureY())) {
            overlays = overlays.withSignaturePosition(sx, sy);
        }
        signatureLayer.place(sx, sy);
        setLayersInteractive(isOverlayPanelShown());
    }

    private int clampToImageWidth(int x) {
        return image != null ? Math.max(0, Math.min(x, image.width())) : x;
    }

    private int clampToImageHeight(int y) {
        return image != null ? Math.max(0, Math.min(y, image.height())) : y;
    }

    private void syncTextAreaLayers() {
        var pane = imageView.getImagePane();
        var areas = overlays != null ? overlays.textAreas() : List.<TextAreaOverlay>of();
        while (textAreaLayers.size() > areas.size()) {
            var removed = textAreaLayers.removeLast();
            pane.getChildren().remove(removed);
        }
        while (textAreaLayers.size() < areas.size()) {
            int index = textAreaLayers.size();
            var layer = new DraggableTextOverlayLayer(imageView, (x, y) -> {
                var current = overlays.textAreas();
                if (index < current.size()) {
                    overlays = overlays.withTextAreaAt(index, current.get(index).withPosition(x, y));
                    if (overlayPanel != null) {
                        overlayPanel.updateState(overlays);
                    }
                    markEdited();
                }
            });
            textAreaLayers.add(layer);
            pane.getChildren().add(layer);
        }
        for (int i = 0; i < areas.size(); i++) {
            var area = overlays.textAreas().get(i);
            if (area.x() == null || area.y() == null) {
                overlays = overlays.withTextAreaAt(i, area.withPosition(defaultTextAreaX(i), defaultTextAreaY(i)));
                area = overlays.textAreas().get(i);
            }
            int x = clampToImageWidth(area.x());
            int y = clampToImageHeight(area.y());
            if (x != area.x() || y != area.y()) {
                overlays = overlays.withTextAreaAt(i, area.withPosition(x, y));
                area = overlays.textAreas().get(i);
            }
            var layer = textAreaLayers.get(i);
            layer.update(area.text(), area.fontFamily(), area.fontSize(), area.color(), area.fontWeight());
            layer.place(x, y);
        }
        setLayersInteractive(isOverlayPanelShown());
    }

    private int defaultTextAreaX(int index) {
        if (image == null) {
            return 0;
        }
        int marginX = (int) Math.max(40, image.width() * 0.03);
        return marginX + index * 24;
    }

    private int defaultTextAreaY(int index) {
        if (image == null) {
            return 0;
        }
        return (int) Math.round(image.height() * 0.4) + index * 24;
    }

    private void placeSignatureDefault() {
        if (image == null) {
            return;
        }
        int imageWidth = image.width();
        int imageHeight = image.height();
        int marginX = (int) Math.max(40, imageWidth * 0.03);
        int marginY = (int) Math.max(40, imageHeight * 0.03);
        int fontSize = overlays.signatureFontSize() != null ? overlays.signatureFontSize() : ImageDraw.DEFAULT_SIGNATURE_SIZE;
        var lines = overlays.signatureText() != null ? overlays.signatureText().split("\n", -1) : new String[]{""};
        int approxHeight = (int) Math.round(fontSize * 1.25 * lines.length);
        int defaultX = marginX;
        int defaultY = imageHeight - marginY - approxHeight;
        if (defaultY < 0) {
            defaultY = 0;
        }
        overlays = overlays.withSignaturePosition(defaultX, defaultY);
    }

    private String cloudModelValue(int x, int y) {
        var result = image.findMetadata(CloudInversionResult.class).orElse(null);
        var map = image.findMetadata(CloudMap.class).orElse(null);
        if (result == null || map == null) {
            return null;
        }
        return CloudMapRenderer.describe(result, map, x, y).orElse(null);
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

    /**
     * Returns the stretched image.
     *
     * @return the stretched image
     */
    public ImageWrapper getStretchedImage() {
        if (image == null) {
            return null;
        }
        return computeStretchedImage().unwrapToMemory();
    }

    public ImageWrapper getStretchedImageWithOverlays() {
        var base = getStretchedImage();
        if (base == null || !hasActiveOverlays()) {
            return base;
        }
        return applyOverlaysToStretchedImage();
    }

    /**
     * Returns the original (unstretched) image displayed by this viewer, including
     * its metadata, as used for session export.
     *
     * @return the image, or {@code null} if none
     */
    public ImageWrapper getImage() {
        return image;
    }

    public GeneratedImageKind getKind() {
        return kind;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ProcessParams getProcessParams() {
        return processParams;
    }

    public boolean hasActiveOverlays() {
        return overlays != null
                && (overlays.drawGlobe()
                || overlays.drawObservationDetails()
                || overlays.drawSolarParameters()
                || overlays.drawEarth()
                || overlays.drawProminenceScale()
                || overlays.drawActiveRegions()
                || overlays.drawSignature()
                || overlays.isColorizeActive()
                || !overlays.textAreas().isEmpty());
    }

    private ImageWrapper applyOverlaysToStretchedImage() {
        var baked = OverlayRenderer.apply(
                applyColorize(computeStretchedImage().unwrapToMemory()),
                overlays,
                effectiveGlobeStyle(),
                processParams,
                kind,
                baseImageIsPCorrected()
        );
        if (overlays.drawEarth() && overlays.earthX() != null && overlays.earthY() != null) {
            baked = OverlayRenderer.bakeEarth(baked, overlays.earthX(), overlays.earthY(), processParams);
        }
        if (overlays.drawSignature() && overlays.signatureX() != null && overlays.signatureY() != null) {
            baked = OverlayRenderer.bakeSignature(baked, overlays, overlays.signatureX(), overlays.signatureY(), processParams);
        }
        if (overlays.drawObservationDetails() && overlays.obsDetailsX() != null && overlays.obsDetailsY() != null) {
            baked = OverlayRenderer.bakeObsDetails(baked, overlays, overlays.obsDetailsX(), overlays.obsDetailsY(), processParams, fileCounts);
        }
        if (overlays.drawSolarParameters() && overlays.solarParamsX() != null && overlays.solarParamsY() != null) {
            baked = OverlayRenderer.bakeSolarParameters(baked, overlays, overlays.solarParamsX(), overlays.solarParamsY(), processParams);
        }
        for (var area : overlays.textAreas()) {
            if (area.x() != null && area.y() != null) {
                baked = OverlayRenderer.bakeTextArea(baked, area, area.x(), area.y(), processParams);
            }
        }
        return baked;
    }

    /**
     * Sets the callback to invoke when the stretched image is updated.
     *
     * @param onStretchedImageUpdate the callback
     */
    public void setOnStretchedImageUpdate(Consumer<ImageWrapper> onStretchedImageUpdate) {
        this.onStretchedImageUpdate = onStretchedImageUpdate;
    }

    @Override
    public Node getRoot() {
        return root;
    }

    /**
     * Updates the displayed image.
     *
     * @param baseName the base name
     * @param params   the process parameters
     * @param image    the image
     * @param path     the image path
     */
    public synchronized void setImage(String baseName, ProcessParams params, ImageWrapper image, Path path) {
        this.processParams = params;
        this.image = FileBackedImage.wrap(image);
        this.imageFile = new File(path.toFile().getParentFile(), baseName + "_" + path.getFileName() + imageDisplayExtension(processParams));
        this.imageView.setImagePathForOpeningInExplorer(path);
        recordImage(baseName, image);
        currentImage.set(imageHistory.size() - 1);
        showImage();
    }

    /**
     * Displays the current image from the history.
     */
    public void showImage() {
        var currentState = imageHistory.get(currentImage.get());
        this.processParams = currentState.processParams;
        this.image = currentState.image;
        this.imageFile = currentState.imageFile;
        FxUtils.runLater(() -> {
            updateTitle();
            var newInitialRecipe = computeInitialRecipe();
            if (!newInitialRecipe.equals(initialRecipe)) {
                initialRecipe = newInitialRecipe;
                useRecipe(newInitialRecipe, newInitialRecipe.steps().size() - 1);
                refreshStretchControls();
            }
            imageView.setImage(new Image(imageFile.toURI().toString()));
            imageView.setSolarDisk(image.findMetadata(Ellipse.class).orElse(null));
            stretchAndDisplay();
        });
    }

    private void updateTitle() {
        if (stage != null) {
            stage.titleProperty().set(title + " (" + imageFile.getName() + ") - " + currentImage.get());
        }
    }

    private record StretchCache(FileBackedImage source, StretchRecipe prefix, ImageWrapper image) {
    }

    private record PendingStretch(long generation, CompletableFuture<ImageWrapper> result) {
    }

    private record StretchChoice(StretchStepKind kind) {
        @Override
        public String toString() {
            return kind == null ? message("stretch.step.none") : StretchStepEditor.stepLabel(kind);
        }
    }

    private record ImageState(
            ProcessParams processParams,
            String baseName,
            FileBackedImage image,
            File imageFile
    ) {

    }

    private void updateSaveButtonStyle(boolean disabled) {
        saveButton.getStyleClass().remove("save-button-pending");
        if (!disabled) {
            saveButton.getStyleClass().add("save-button-pending");
        }
    }

    private void markEdited() {
        unsavedChanges = true;
        refreshSaveButton();
    }

    private void refreshSaveButton() {
        if (saveButton != null) {
            saveButton.setDisable(!unsavedChanges);
        }
    }

    private static Button createButton(String text) {
        var button = new Button(text);
        button.getStyleClass().add("image-viewer-button");
        button.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
        return button;
    }

    private static Button createIconButton(String glyph) {
        var button = new Button(glyph);
        button.getStyleClass().addAll("image-viewer-button", "image-viewer-icon-button");
        return button;
    }

    private static Separator verticalSeparator() {
        var separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("toolbar-separator");
        return separator;
    }
}
