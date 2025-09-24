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
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.CollageComposition;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.CollageParameters;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class CollageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollageController.class);
    private static final DataFormat SLOT_DATA_FORMAT = new DataFormat("application/x-slot-index");
    private static final AtomicInteger COLLAGE_COUNTER = new AtomicInteger(0);

    private Stage stage;
    private MultipleImagesViewer multipleImagesViewer;
    private List<MultipleImagesViewer.ImageInfo> availableImages;
    private List<ImageSlot> imageSlots;
    private Path outputDirectory;
    private ProcessParams processParams;
    private boolean scrollListenersBound = false;

    @FXML
    private HBox imageStripPane;

    @FXML
    private ScrollPane previewScrollPane;

    @FXML
    private GridPane previewGrid;

    @FXML
    private Spinner<Integer> rowsSpinner;

    @FXML
    private Spinner<Integer> columnsSpinner;

    @FXML
    private Spinner<Integer> paddingSpinner;

    @FXML
    private CheckBox maintainAspectRatioCheckBox;

    @FXML
    private CheckBox autoDownscaleCheckBox;

    @FXML
    private TextField maxWidthField;

    @FXML
    private TextField maxHeightField;

    @FXML
    private ColorPicker backgroundColorPicker;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button deselectAllButton;

    @FXML
    private Button resetButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button createCollageButton;

    public void setup(Stage stage, JSolExInterface owner, List<MultipleImagesViewer.ImageInfo> images, Path outputDirectory, ProcessParams processParams) {
        this.stage = stage;
        this.multipleImagesViewer = owner.getImagesViewer();
        this.availableImages = new ArrayList<>(images);
        this.availableImages.sort(
            Comparator.comparing((MultipleImagesViewer.ImageInfo img) -> img.kind().displayCategory().ordinal())
                .thenComparing(img -> img.kind().ordinal())
                .thenComparing(MultipleImagesViewer.ImageInfo::title)
        );
        this.imageSlots = new ArrayList<>();
        this.outputDirectory = outputDirectory;
        this.processParams = processParams;

        initializeSpinners();
        populateImageStrip();
        setupPreviewGrid();
        setupEventHandlers();

        stage.setOnCloseRequest(e -> cancel());
    }

    private void initializeSpinners() {
        rowsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 6, 2));
        columnsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 6, 2));
        paddingSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 10));
        backgroundColorPicker.setValue(Color.BLACK);

        rowsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> setupPreviewGrid());
        columnsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> setupPreviewGrid());
        paddingSpinner.valueProperty().addListener((obs, oldVal, newVal) -> setupPreviewGrid());
        backgroundColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> setupPreviewGrid());
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }

    private void populateImageStrip() {
        imageStripPane.getChildren().clear();

        for (var imageInfo : availableImages) {
            var thumbnail = createImageThumbnail(imageInfo);
            imageStripPane.getChildren().add(thumbnail);
        }
    }

    private VBox createImageThumbnail(MultipleImagesViewer.ImageInfo imageInfo) {
        var imageView = new ImageView();
        imageView.setFitWidth(70);
        imageView.setFitHeight(70);
        imageView.setPreserveRatio(true);
        imageView.setImage(WritableImageSupport.asWritable(imageInfo.image()));

        var label = new Label(imageInfo.title());
        label.setStyle("-fx-font-size: 0.75em; -fx-text-alignment: center;");
        label.setMaxWidth(80);
        label.setWrapText(true);
        label.setMaxHeight(30);

        var container = new VBox(2);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(4));
        container.setPrefHeight(110);
        container.setMaxHeight(110);
        container.getChildren().addAll(imageView, label);
        container.setStyle("-fx-border-color: lightgray; -fx-border-radius: 4; -fx-background-color: white; -fx-background-radius: 4;");

        var tooltip = new Tooltip(imageInfo.title());
        Tooltip.install(container, tooltip);

        container.setOnMouseEntered(e -> container.setStyle("-fx-border-color: #0078d4; -fx-border-radius: 4; -fx-background-color: #f3f9ff; -fx-background-radius: 4;"));
        container.setOnMouseExited(e -> container.setStyle("-fx-border-color: lightgray; -fx-border-radius: 4; -fx-background-color: white; -fx-background-radius: 4;"));

        setupDragSource(container, imageInfo);

        return container;
    }

    private void setupDragSource(VBox thumbnail, MultipleImagesViewer.ImageInfo imageInfo) {
        thumbnail.setOnDragDetected(event -> {
            var dragboard = thumbnail.startDragAndDrop(TransferMode.COPY);
            var content = new ClipboardContent();
            var imageIndex = availableImages.indexOf(imageInfo);
            content.putString(String.valueOf(imageIndex));
            dragboard.setContent(content);

            thumbnail.setStyle("-fx-border-color: #0078d4; -fx-border-width: 2; -fx-border-radius: 4; -fx-background-color: #e6f3ff; -fx-background-radius: 4;");
            event.consume();
        });

        thumbnail.setOnDragDone(event -> {
            thumbnail.setStyle("-fx-border-color: lightgray; -fx-border-radius: 4; -fx-background-color: white; -fx-background-radius: 4;");
            event.consume();
        });
    }

    private void setupPreviewGrid() {
        Platform.runLater(() -> {
            var newRows = rowsSpinner.getValue();
            var newColumns = columnsSpinner.getValue();

            var bgColor = colorToHex(backgroundColorPicker.getValue());
            previewGrid.setStyle("-fx-background-color: " + bgColor + ";");

            if (imageSlots.isEmpty()) {
                rebuildEntireGrid(newRows, newColumns);
                return;
            }

            var savedImages = new MultipleImagesViewer.ImageInfo[imageSlots.size()];
            for (int i = 0; i < imageSlots.size(); i++) {
                savedImages[i] = imageSlots.get(i).imageInfo;
            }

            int currentRows = 0;
            int currentColumns = 0;
            for (var slot : imageSlots) {
                currentRows = Math.max(currentRows, slot.row + 1);
                currentColumns = Math.max(currentColumns, slot.col + 1);
            }

            if (currentRows == newRows && currentColumns == newColumns) {
                updateSlotSizes();
                return;
            }

            rebuildGridWithImages(savedImages, currentRows, currentColumns, newRows, newColumns);
        });
    }

    private void rebuildEntireGrid(int rows, int columns) {
        previewGrid.getChildren().clear();
        previewGrid.getColumnConstraints().clear();
        previewGrid.getRowConstraints().clear();
        imageSlots.clear();

        var availableWidth = previewScrollPane.getWidth() - 60; 
        var availableHeight = previewScrollPane.getHeight() - 60;

        if (availableWidth <= 0) availableWidth = 600;
        if (availableHeight <= 0) availableHeight = 400;

        var gapWidth = (columns - 1) * 8; 
        var gapHeight = (rows - 1) * 8; 

        var maxSlotWidth = (availableWidth - gapWidth) / columns;
        var maxSlotHeight = (availableHeight - gapHeight) / rows;

        var slotSize = Math.min(maxSlotWidth, maxSlotHeight);
        slotSize = Math.max(120, slotSize); 

        var slotWidth = slotSize;
        var slotHeight = slotSize;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                var slot = createImageSlot(row, col, slotWidth, slotHeight);
                imageSlots.add(slot);
                previewGrid.add(slot.container, col, row);
            }
        }

        bindScrollPaneListeners();
    }

    private void rebuildGridWithImages(MultipleImagesViewer.ImageInfo[] savedImages,
                                     int currentRows, int currentColumns,
                                     int newRows, int newColumns) {
        previewGrid.getChildren().clear();
        previewGrid.getColumnConstraints().clear();
        previewGrid.getRowConstraints().clear();

        var newImageSlots = new ArrayList<ImageSlot>();

        var availableWidth = previewScrollPane.getWidth() - 60;
        var availableHeight = previewScrollPane.getHeight() - 60;

        if (availableWidth <= 0) availableWidth = 600;
        if (availableHeight <= 0) availableHeight = 400;

        var gapWidth = (newColumns - 1) * 8;
        var gapHeight = (newRows - 1) * 8;

        var maxSlotWidth = (availableWidth - gapWidth) / newColumns;
        var maxSlotHeight = (availableHeight - gapHeight) / newRows;

        var slotSize = Math.min(maxSlotWidth, maxSlotHeight);
        slotSize = Math.max(120, slotSize);

        var slotWidth = slotSize;
        var slotHeight = slotSize;

        for (int row = 0; row < newRows; row++) {
            for (int col = 0; col < newColumns; col++) {
                var slot = createImageSlot(row, col, slotWidth, slotHeight);

                if (row < currentRows && col < currentColumns) {
                    int oldIndex = row * currentColumns + col;
                    if (oldIndex < savedImages.length && savedImages[oldIndex] != null) {
                        setSlotImage(slot, savedImages[oldIndex]);
                    }
                }

                newImageSlots.add(slot);
                previewGrid.add(slot.container, col, row);
            }
        }

        imageSlots.clear();
        imageSlots.addAll(newImageSlots);
    }

    private void bindScrollPaneListeners() {
        if (!scrollListenersBound) {
            previewScrollPane.widthProperty().addListener((obs, oldVal, newVal) -> updateSlotSizes());
            previewScrollPane.heightProperty().addListener((obs, oldVal, newVal) -> updateSlotSizes());
            scrollListenersBound = true;
        }
    }

    private ImageSlot createImageSlot(int row, int col, double width, double height) {
        var slot = new ImageSlot(row, col);

        var placeholder = new Label("Drop image here");
        placeholder.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        placeholder.setAlignment(Pos.CENTER);

        slot.container = new StackPane();
        slot.container.setPrefSize(width, height);
        slot.container.setMinSize(width, height);
        slot.container.setMaxSize(width, height);
        var bgColor = colorToHex(backgroundColorPicker.getValue());
        slot.container.setStyle("-fx-border-color: #ddd; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: " + bgColor + ";");
        slot.container.getChildren().add(placeholder);

        setupDropTarget(slot);

        return slot;
    }

    private void updateSlotSizes() {
        if (imageSlots.isEmpty()) return;

        var rows = rowsSpinner.getValue();
        var columns = columnsSpinner.getValue();

        var availableWidth = previewScrollPane.getWidth() - 60;
        var availableHeight = previewScrollPane.getHeight() - 60;

        if (availableWidth <= 0 || availableHeight <= 0) return;

        var gapWidth = (columns - 1) * 8;
        var gapHeight = (rows - 1) * 8;

        var maxSlotWidth = (availableWidth - gapWidth) / columns;
        var maxSlotHeight = (availableHeight - gapHeight) / rows;

        var slotSize = Math.min(maxSlotWidth, maxSlotHeight);
        slotSize = Math.max(120, slotSize);

        var slotWidth = slotSize;
        var slotHeight = slotSize;

        for (var slot : imageSlots) {
            slot.container.setPrefSize(slotWidth, slotHeight);
            slot.container.setMinSize(slotWidth, slotHeight);
            slot.container.setMaxSize(slotWidth, slotHeight);
        }
    }

    private void setupDropTarget(ImageSlot slot) {
        slot.container.setOnDragOver(event -> {
            var dragboard = event.getDragboard();
            if (event.getGestureSource() != slot.container &&
                (dragboard.hasString() || dragboard.hasContent(SLOT_DATA_FORMAT))) {

                if (dragboard.hasContent(SLOT_DATA_FORMAT)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                } else {
                    event.acceptTransferModes(TransferMode.COPY);
                }
                slot.container.setStyle("-fx-border-color: #0078d4; -fx-border-style: solid; -fx-border-width: 2; -fx-background-color: #e6f3ff;");
            }
            event.consume();
        });

        slot.container.setOnDragExited(event -> {
            if (slot.imageInfo == null) {
                var bgColor = colorToHex(backgroundColorPicker.getValue());
                slot.container.setStyle("-fx-border-color: #ddd; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: " + bgColor + ";");
            } else {
                slot.container.setStyle("-fx-border-color: lightgray; -fx-border-style: solid; -fx-border-width: 1; -fx-background-color: white;");
            }
            event.consume();
        });

        slot.container.setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            boolean success = false;

            if (dragboard.hasContent(SLOT_DATA_FORMAT)) {
                try {
                    var sourceSlotIndex = Integer.parseInt((String) dragboard.getContent(SLOT_DATA_FORMAT));
                    if (sourceSlotIndex >= 0 && sourceSlotIndex < imageSlots.size()) {
                        var sourceSlot = imageSlots.get(sourceSlotIndex);
                        var targetSlot = slot;

                        if (sourceSlot != targetSlot) {
                            var sourceImageInfo = sourceSlot.imageInfo;
                            var targetImageInfo = targetSlot.imageInfo;

                            clearSlot(sourceSlot);
                            clearSlot(targetSlot);

                            if (sourceImageInfo != null) {
                                setSlotImage(targetSlot, sourceImageInfo);
                            }
                            if (targetImageInfo != null) {
                                setSlotImage(sourceSlot, targetImageInfo);
                            }
                            success = true;
                        }
                    }
                } catch (NumberFormatException e) {
                }
            } else if (dragboard.hasString()) {
                try {
                    var imageIndex = Integer.parseInt(dragboard.getString());
                    if (imageIndex >= 0 && imageIndex < availableImages.size()) {
                        var imageInfo = availableImages.get(imageIndex);
                        setSlotImage(slot, imageInfo);
                        success = true;
                    }
                } catch (NumberFormatException e) {
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });

        slot.container.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && slot.imageInfo != null) {
                clearSlot(slot);
            }
        });
    }

    private void setSlotImage(ImageSlot slot, MultipleImagesViewer.ImageInfo imageInfo) {
        slot.imageInfo = imageInfo;
        slot.container.getChildren().clear();

        var imageView = new ImageView();
        imageView.fitWidthProperty().bind(slot.container.widthProperty().subtract(10));
        imageView.fitHeightProperty().bind(slot.container.heightProperty().subtract(10));
        imageView.setPreserveRatio(true);
        imageView.setImage(WritableImageSupport.asWritable(imageInfo.image()));

        var label = new Label(imageInfo.title());
        label.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-padding: 2 4 2 4; -fx-font-size: 10px; -fx-background-radius: 3;");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        StackPane.setAlignment(label, Pos.BOTTOM_CENTER);

        slot.container.getChildren().addAll(imageView, label);
        slot.container.setStyle("-fx-border-color: lightgray; -fx-border-style: solid; -fx-border-width: 1; -fx-background-color: white;");

        setupSlotDragSource(slot);
    }

    private void setupSlotDragSource(ImageSlot slot) {
        slot.container.setOnDragDetected(event -> {
            if (slot.imageInfo != null) {
                var dragboard = slot.container.startDragAndDrop(TransferMode.MOVE);
                var content = new ClipboardContent();
                var slotIndex = imageSlots.indexOf(slot);
                content.put(SLOT_DATA_FORMAT, String.valueOf(slotIndex));
                dragboard.setContent(content);

                slot.container.setStyle("-fx-border-color: #0078d4; -fx-border-width: 2; -fx-border-style: solid; -fx-background-color: #e6f3ff;");
                event.consume();
            }
        });

        slot.container.setOnDragDone(event -> {
            if (slot.imageInfo != null) {
                slot.container.setStyle("-fx-border-color: lightgray; -fx-border-style: solid; -fx-border-width: 1; -fx-background-color: white;");
            } else {
                var bgColor = colorToHex(backgroundColorPicker.getValue());
                slot.container.setStyle("-fx-border-color: #ddd; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: " + bgColor + ";");
            }
            event.consume();
        });
    }

    private void clearSlot(ImageSlot slot) {
        slot.imageInfo = null;
        slot.container.getChildren().clear();

        slot.container.setOnDragDetected(null);
        slot.container.setOnDragDone(null);

        var placeholder = new Label("Drop image here");
        placeholder.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
        placeholder.setAlignment(Pos.CENTER);

        var bgColor = colorToHex(backgroundColorPicker.getValue());
        slot.container.getChildren().add(placeholder);
        slot.container.setStyle("-fx-border-color: #ddd; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: " + bgColor + ";");
    }

    private void setupEventHandlers() {
        maintainAspectRatioCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        autoDownscaleCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> updatePreview());
    }

    private void updatePreview() {
    }

    @FXML
    private void selectAll() {
        int slotIndex = 0;
        for (var imageInfo : availableImages) {
            if (slotIndex < imageSlots.size()) {
                setSlotImage(imageSlots.get(slotIndex), imageInfo);
                slotIndex++;
            } else {
                break;
            }
        }
    }

    @FXML
    private void deselectAll() {
        for (var slot : imageSlots) {
            clearSlot(slot);
        }
    }

    @FXML
    private void resetToDefaults() {
        int imageCount = availableImages.size();
        int defaultColumns = 2;
        int defaultRows = (int) Math.ceil((double) imageCount / defaultColumns);

        rowsSpinner.getValueFactory().setValue(defaultRows);
        columnsSpinner.getValueFactory().setValue(defaultColumns);
        paddingSpinner.getValueFactory().setValue(10);
        maintainAspectRatioCheckBox.setSelected(true);
        autoDownscaleCheckBox.setSelected(true);
        maxWidthField.setText("4096");
        maxHeightField.setText("4096");
    }

    @FXML
    private void cancel() {
        stage.close();
    }

    @FXML
    private void createCollage() {
        var selectedSlots = getSelectedSlots();
        if (selectedSlots.isEmpty()) {
            return;
        }

        try {
            var parameters = buildCollageParameters(selectedSlots);
            createCollageButton.setDisable(true);

            var task = new Task<Void>() {
                @Override
                protected Void call() {
                    try {
                        var context = Map.<Class<?>, Object>of();
                        var broadcaster = Broadcaster.NO_OP;
                        var collageComposition = new CollageComposition(context, broadcaster);

                        var collageImage = collageComposition.createCollage(parameters);

                        var filename = "collage_" + COLLAGE_COUNTER.incrementAndGet();

                        Platform.runLater(() -> {
                            try {
                                if (multipleImagesViewer != null) {
                                    addCollageToViewer(collageImage, filename);
                                }
                                stage.close();
                            } catch (Exception e) {
                                LOGGER.error("Error adding collage to viewer", e);
                                throw e;
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("Error creating collage", e);
                        Platform.runLater(() -> {
                            createCollageButton.setDisable(false);
                        });
                    }
                    return null;
                }
            };

            BackgroundOperations.async(task);

        } catch (Exception e) {
            LOGGER.error("Error creating collage parameters", e);
            createCollageButton.setDisable(false);
        }
    }

    private List<ImageSlot> getSelectedSlots() {
        return imageSlots.stream()
                .filter(slot -> slot.imageInfo != null)
                .toList();
    }

    private CollageParameters buildCollageParameters(List<ImageSlot> selectedSlots) {
        var imageSelections = selectedSlots.stream()
                .map(slot -> new CollageParameters.ImageSelection(
                        slot.imageInfo.image(),
                        slot.imageInfo.title(),
                        Optional.of(slot.row),
                        Optional.of(slot.col)))
                .toList();

        var maxWidth = Integer.parseInt(maxWidthField.getText());
        var maxHeight = Integer.parseInt(maxHeightField.getText());

        return new CollageParameters(
                imageSelections,
                rowsSpinner.getValue(),
                columnsSpinner.getValue(),
                maxWidth,
                maxHeight,
                maintainAspectRatioCheckBox.isSelected(),
                paddingSpinner.getValue(),
                autoDownscaleCheckBox.isSelected(),
                (float) backgroundColorPicker.getValue().getBrightness()
        );
    }

    private void addCollageToViewer(ImageWrapper collageImage, String filename) {
        if (multipleImagesViewer == null) {
            return;
        }

        try {
            var noOpListener = new ProcessingEventListener() {};
            var noOpOperation = ProgressOperation.root("Collage Creation", _ -> {});
            var processingDate = LocalDateTime.now();
            var namingStrategy = new FileNamingStrategy(
                    processParams.extraParams().fileNamePattern(),
                    processParams.extraParams().datetimeFormat(),
                    processParams.extraParams().dateFormat(),
                    processingDate,
                    JSolEx.createFakeHeader(processingDate));

            var renderedFileName = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, "collage", "collage", collageImage);
            var outputFile = new File(outputDirectory.toFile(), renderedFileName);

            multipleImagesViewer.addImage(
                    noOpListener,
                    noOpOperation,
                    "Collage " + filename,
                    filename,
                    GeneratedImageKind.COLLAGE,
                    I18N.string(JSolEx.class, "collage", "generated.collage"),
                    collageImage,
                    outputFile,
                    ProcessParams.loadDefaults(),
                    Map.of(),
                    new PixelShift(0),
                    viewer -> viewer,
                    _ -> {}
            );
        } catch (Exception e) {
            LOGGER.error("Failed to add collage to viewer", e);
            throw e;
        }
    }

    private static class ImageSlot {
        final int row;
        final int col;
        StackPane container;
        MultipleImagesViewer.ImageInfo imageInfo;

        ImageSlot(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }
}