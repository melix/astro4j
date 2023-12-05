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
package me.champeau.a4j.jsolex.app.jfx.stacking;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.listeners.JSolExInterface;
import me.champeau.a4j.jsolex.app.listeners.SingleModeProcessingEventListener;
import me.champeau.a4j.jsolex.processing.expr.impl.MosaicComposition;
import me.champeau.a4j.jsolex.processing.expr.impl.Stacking;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.FileNamingPatternsIO;
import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.sun.workflow.StackingWorkflow;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;

import static me.champeau.a4j.jsolex.app.JSolEx.createFakeHeader;
import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class StackingAndMosaicController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StackingAndMosaicController.class);

    public static final FileChooser.ExtensionFilter IMAGE_FILES_EXTENSIONS = new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.fits");

    private Stage stage;
    private JSolExInterface owner;
    private ForkJoinContext ioExecutor;
    private ForkJoinContext cpuExecutor;
    private Map<String, ImageViewer> popupViewers;

    @FXML
    private Accordion accordionParams;
    @FXML
    private ScrollPane cardsScrollPane;
    @FXML
    private FlowPane cardsPane;
    @FXML
    private TitledPane mosaicParameters;
    @FXML
    private Button proceedButton;
    @FXML
    private ChoiceBox<NamedPattern> namingPattern;
    @FXML
    private Slider stackTileSize;
    @FXML
    private Slider stackOverlap;
    @FXML
    private CheckBox stackForceRecomputeEllipse;
    @FXML
    private CheckBox stackFixGeometry;
    @FXML
    private Label stackTileSizeLabel;
    @FXML
    private Label stackTileOverlapLabel;
    @FXML
    private Slider mosaicTileSize;
    @FXML
    private Slider mosaicOverlap;
    @FXML
    private Label mosaicTileSizeLabel;
    @FXML
    private Label mosaicTileOverlapLabel;
    @FXML
    private CheckBox createMosaic;

    // File formats
    @FXML
    private CheckBox saveFits;
    @FXML
    private CheckBox saveJpg;
    @FXML
    private CheckBox savePng;
    @FXML
    private CheckBox saveTif;

    public void setup(Stage stage, JSolExInterface owner, ProcessParams processParams, ForkJoinContext ioExecutor, ForkJoinContext cpuExecutor, Map<String, ImageViewer> popupViewers) {
        this.stage = stage;
        this.owner = owner;
        this.ioExecutor = ioExecutor;
        this.cpuExecutor = cpuExecutor;
        this.popupViewers = popupViewers;
        accordionParams.setExpandedPane(accordionParams.getPanes().get(0));
        var plusCard = new PlusCard(this);
        cardsPane.getChildren().add(plusCard);
        cardsScrollPane.setStyle("-fx-background-color:transparent;");
        cardsScrollPane.setOnDragDropped(evt -> {
            var card = new PanelCard(this);
            card.handleDragDropped(evt);
            cardsPane.getChildren().add(0, card);
        });
        cardsScrollPane.setOnDragOver(evt -> {
            var hasFiles = evt.getDragboard().hasFiles();
            if (hasFiles) {
                evt.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
        });
        mosaicParameters.disableProperty().bind(Bindings.size(cardsPane.getChildren()).lessThan(3));
        proceedButton.textProperty().bind(
            Bindings.size(cardsPane.getChildren()).map(size -> {
                var key = "stacking";
                if ((int) size > 2) {
                    var atLeastOnePanelHasMoreThanOneFile = cardsPane.getChildren().stream()
                        .filter(PanelCard.class::isInstance)
                        .map(c -> (PanelCard) c)
                        .anyMatch(c -> c.getListView().getItems().size() > 1);
                    if (atLeastOnePanelHasMoreThanOneFile) {
                        key = "stacking.and.mosaic";
                    } else {
                        key = "mosaic";
                    }
                }
                return I18N.string(JSolEx.class, "mosaic-params", "proceed." + key);
            }));
        proceedButton.disableProperty().bind(Bindings.size(cardsPane.getChildren()).lessThan(2));
        var patterns = FXCollections.observableList(FileNamingPatternsIO.loadDefaults());
        namingPattern.getItems().addAll(patterns);
        if (!patterns.isEmpty()) {
            namingPattern.getSelectionModel().selectFirst();
            var pattern = processParams.extraParams().fileNamePattern();
            if (pattern != null) {
                patterns.stream()
                    .filter(p -> p.pattern().equals(pattern))
                    .findFirst()
                    .ifPresent(e -> namingPattern.getSelectionModel().select(e));
            }

        }
        saveFits.setSelected(processParams.extraParams().imageFormats().contains(ImageFormat.FITS));
        saveJpg.setSelected(processParams.extraParams().imageFormats().contains(ImageFormat.JPG));
        savePng.setSelected(processParams.extraParams().imageFormats().contains(ImageFormat.PNG));
        saveTif.setSelected(processParams.extraParams().imageFormats().contains(ImageFormat.TIF));
        stackOverlap.setLabelFormatter(new DoubleToPercentageConverter());
        stackTileSizeLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            var value = stackTileSize.getValue();
            return String.format("%s (%dpx)", I18N.string(JSolEx.class, "mosaic-params", "tile.size"), (int) value);
        }, stackTileSize.valueProperty()));
        stackTileOverlapLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            var value = stackOverlap.getValue();
            return String.format("%s (%d%%)", I18N.string(JSolEx.class, "mosaic-params", "tile.overlap"), (int) (value * 100));
        }, stackOverlap.valueProperty()));
        mosaicOverlap.setLabelFormatter(new DoubleToPercentageConverter());
        mosaicTileSizeLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            var value = mosaicTileSize.getValue();
            return String.format("%s (%dpx)", I18N.string(JSolEx.class, "mosaic-params", "tile.size"), (int) value);
        }, mosaicTileSize.valueProperty()));
        mosaicTileOverlapLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            var value = mosaicOverlap.getValue();
            return String.format("%s (%d%%)", I18N.string(JSolEx.class, "mosaic-params", "tile.overlap"), (int) (value * 100));
        }, mosaicOverlap.valueProperty()));
        createMosaic.setSelected(true);
        stackForceRecomputeEllipse.selectedProperty().addListener((obs, old, val) -> {
            if (Boolean.TRUE.equals(val)) {
                stackFixGeometry.setSelected(true);
                stackFixGeometry.setDisable(true);
            } else {
                stackFixGeometry.setDisable(false);
            }
        });
        resetStackingParams();
        resetMosaicParams();
    }

    public void resetStackingParams() {
        stackOverlap.setValue(Stacking.DEFAULT_OVERLAP_FACTOR);
        stackTileSize.setValue(Stacking.DEFAULT_TILE_SIZE);
        stackForceRecomputeEllipse.setSelected(false);
        stackFixGeometry.setSelected(false);
    }

    public void resetMosaicParams() {
        createMosaic.setSelected(true);
        mosaicOverlap.setValue(MosaicComposition.DEFAULT_OVERLAP_FACTOR);
        mosaicTileSize.setValue(MosaicComposition.DEFAULT_TILE_SIZE);
    }

    public Stage getStage() {
        return stage;
    }

    public FlowPane getCardsPane() {
        return cardsPane;
    }

    @FXML
    private void proceed() {
        var processingDate = LocalDateTime.now();
        var processParams = createProcessParams();
        var panels = cardsPane.getChildren().stream()
            .filter(PanelCard.class::isInstance)
            .map(c -> (PanelCard) c)
            .map(PanelCard::getListView)
            .map(l -> new StackingWorkflow.Panel(l.getItems()))
            .toList();
        var outputDirectory = panels.stream().flatMap(p -> p.files().stream()).findFirst().map(File::getParentFile).orElseThrow();
        var broadcaster = new SingleModeProcessingEventListener(
            owner,
            "",
            null,
            cpuExecutor,
            ioExecutor,
            outputDirectory.toPath(),
            processParams,
            processingDate,
            popupViewers
        );
        var namingStrategy = new FileNamingStrategy(
            processParams.extraParams().fileNamePattern(),
            processParams.extraParams().datetimeFormat(),
            processParams.extraParams().dateFormat(),
            processingDate,
            createFakeHeader(processingDate)
        );
        var params = new StackingWorkflow.Parameters(
            (int) stackTileSize.getValue(),
            (float) stackOverlap.getValue(),
            stackForceRecomputeEllipse.isSelected(),
            stackFixGeometry.isSelected(),
            createMosaic.isSelected(),
            (int) mosaicTileSize.getValue(),
            (float) mosaicOverlap.getValue()
        );
        cpuExecutor.async(() -> {
            long sd = System.nanoTime();
            try {
                var workflow = new StackingWorkflow(cpuExecutor, broadcaster, namingStrategy);
                workflow.execute(params, panels, outputDirectory);
            } finally {
                long ed = System.nanoTime();
                var duration = java.time.Duration.ofNanos(ed - sd);
                double seconds = duration.toMillis() / 1000d;
                LOGGER.info(message(String.format(message("finished.in"), seconds)));
            }
        });
        stage.close();
    }

    public ProcessParams createProcessParams() {
        var params = ProcessParamsIO.loadDefaults();
        var imageFormats = EnumSet.noneOf(ImageFormat.class);
        if (saveFits.isSelected()) {
            imageFormats.add(ImageFormat.FITS);
        }
        if (saveJpg.isSelected()) {
            imageFormats.add(ImageFormat.JPG);
        }
        if (savePng.isSelected()) {
            imageFormats.add(ImageFormat.PNG);
        }
        if (saveTif.isSelected()) {
            imageFormats.add(ImageFormat.TIF);
        }
        return params.withExtraParams(
            params.extraParams().withImageFormats(imageFormats)
        );
    }

}
