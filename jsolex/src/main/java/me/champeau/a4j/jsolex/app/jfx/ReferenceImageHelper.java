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

import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.GONG;
import me.champeau.a4j.jsolex.processing.util.GONG.GongCandidate;
import me.champeau.a4j.jsolex.processing.util.GONG.GongResolution;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.JSolEx.IMAGE_FILES_EXTENSIONS;
import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Helper class for managing reference image downloading and display.
 * Provides UI components for downloading GONG H-alpha images, letting the user
 * pick the observatory and the image size.
 */
public final class ReferenceImageHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceImageHelper.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm 'UTC'");

    private final Stage rootStage;
    private final Tab referenceImageTab;
    private final Supplier<ImageWrapper> activeImageSupplier;

    private List<GongCandidate> gongCandidates = List.of();
    private Button detectFlipsButton;
    private Label flipResultLabel;

    /**
     * Creates a new reference image helper.
     *
     * @param rootStage           the root application stage
     * @param referenceImageTab   the tab for displaying reference images
     * @param activeImageSupplier supplies the currently active image in the main viewer, or {@code null} if none
     */
    public ReferenceImageHelper(Stage rootStage, Tab referenceImageTab, Supplier<ImageWrapper> activeImageSupplier) {
        this.rootStage = rootStage;
        this.referenceImageTab = referenceImageTab;
        this.activeImageSupplier = activeImageSupplier;
    }

    /**
     * Initializes the reference image tab with default date/time.
     */
    public void initialize() {
        createReferenceImageInterface(null);
    }

    /**
     * Creates or updates the reference image interface.
     *
     * @param serFileDate the date from the SER file, or null for current date
     */
    public void createReferenceImageInterface(ZonedDateTime serFileDate) {
        gongCandidates = List.of();

        var root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setFillWidth(true);

        var titleLabel = new Label(message("reference.image.selector"));
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // GONG organizes images by UTC date and the requested date/time is
        // interpreted as UTC, so the defaults must be derived in UTC too.
        var reference = serFileDate != null
            ? serFileDate.withZoneSameInstant(ZoneId.of("UTC"))
            : ZonedDateTime.now(ZoneId.of("UTC"));
        var defaultDate = reference.toLocalDate();
        var datePicker = new DatePicker(defaultDate);
        datePicker.setMaxWidth(Double.MAX_VALUE);

        var defaultTime = reference.toLocalTime();
        var hourField = createTimeField(defaultTime.getHour(), 0, 23);
        hourField.setText(String.format("%02d", defaultTime.getHour()));
        var minuteField = createTimeField(defaultTime.getMinute(), 0, 59);
        minuteField.setText(String.format("%02d", defaultTime.getMinute()));
        var timeBox = new HBox(5, hourField, new Label(":"), minuteField);
        timeBox.setAlignment(Pos.CENTER_LEFT);

        var sizeSelector = createSizeSelector();
        sizeSelector.setMaxWidth(Double.MAX_VALUE);

        var form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        var labels = new ColumnConstraints();
        labels.setHalignment(HPos.LEFT);
        labels.setMinWidth(Region.USE_PREF_SIZE);
        var fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        form.getColumnConstraints().addAll(labels, fields);
        form.addRow(0, new Label(message("select.date")), datePicker);
        form.addRow(1, new Label(message("select.time")), timeBox);
        form.addRow(2, new Label(message("gong.size")), sizeSelector);

        var downloadButton = new Button(message("download.gong.image"));
        downloadButton.getStyleClass().add("primary-button");
        downloadButton.setMaxWidth(Double.MAX_VALUE);
        var siteMenuButton = new MenuButton(message("gong.pick.site"));
        siteMenuButton.setMaxWidth(Double.MAX_VALUE);
        siteMenuButton.setDisable(true);

        var imageView = new ZoomableImageView();
        imageView.setMinSize(200, 200);

        detectFlipsButton = new Button(message("gong.detect.flips"));
        detectFlipsButton.getStyleClass().add("default-button");
        detectFlipsButton.setMaxWidth(Double.MAX_VALUE);
        detectFlipsButton.setVisible(false);
        detectFlipsButton.setManaged(false);
        detectFlipsButton.setOnAction(e -> detectFlips(imageView));

        flipResultLabel = new Label();
        flipResultLabel.setWrapText(true);
        flipResultLabel.setMaxWidth(Double.MAX_VALUE);
        flipResultLabel.setVisible(false);
        flipResultLabel.setManaged(false);

        var statusLabel = new Label(message("gong.image.placeholder"));
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 13px;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setAlignment(Pos.CENTER);
        var imageArea = new StackPane(imageView, statusLabel);
        imageArea.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-background-color: -fx-control-inner-background;");
        VBox.setVgrow(imageArea, Priority.ALWAYS);
        setupImageContextMenu(imageView);

        downloadButton.setOnAction(e -> {
            var selectedDate = datePicker.getValue();
            var hour = Integer.parseInt(hourField.getText());
            var minute = Integer.parseInt(minuteField.getText());
            var localDateTime = LocalDateTime.of(selectedDate, LocalTime.of(hour, minute));
            var zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of("UTC"));
            loadCandidates(zonedDateTime, sizeSelector.getValue(), downloadButton, siteMenuButton, imageView, statusLabel);
        });

        root.getChildren().addAll(titleLabel, form, downloadButton, siteMenuButton, detectFlipsButton, flipResultLabel, imageArea);

        var scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        FxUtils.runLater(() -> referenceImageTab.setContent(scrollPane));
    }

    private void loadCandidates(ZonedDateTime requestedDate,
                                GongResolution resolution,
                                Button downloadButton,
                                MenuButton siteMenuButton,
                                ZoomableImageView imageView,
                                Label statusLabel) {
        downloadButton.setText(message("downloading.gong.image"));
        downloadButton.setDisable(true);
        siteMenuButton.setDisable(true);

        BackgroundOperations.async(() -> {
            var candidates = GONG.oneCandidatePerSite(GONG.listGongCandidates(requestedDate, resolution));
            FxUtils.runLater(() -> {
                gongCandidates = candidates;
                populateSiteMenu(candidates, requestedDate, siteMenuButton, downloadButton, imageView, statusLabel);
                if (candidates.isEmpty()) {
                    showStatus(imageView, statusLabel, message("no.image.available"));
                    downloadButton.setText(message("download.gong.image"));
                    downloadButton.setDisable(false);
                } else {
                    loadCandidate(candidates.getFirst(), downloadButton, siteMenuButton, imageView, statusLabel);
                }
            });
        });
    }

    private void populateSiteMenu(List<GongCandidate> candidates,
                                  ZonedDateTime requestedDate,
                                  MenuButton siteMenuButton,
                                  Button downloadButton,
                                  ZoomableImageView imageView,
                                  Label statusLabel) {
        siteMenuButton.getItems().clear();
        if (candidates.isEmpty()) {
            siteMenuButton.setText(message("gong.pick.site"));
            siteMenuButton.setDisable(true);
            return;
        }
        var utcRequested = requestedDate.withZoneSameInstant(ZoneId.of("UTC"));
        for (var candidate : candidates) {
            var offsetMinutes = ChronoUnit.MINUTES.between(utcRequested, candidate.timestamp());
            var label = MessageFormat.format(message("gong.candidate"),
                candidate.siteDisplayName(),
                candidate.timestamp().format(TIME_FORMATTER),
                formatOffset(offsetMinutes));
            var item = new MenuItem(label);
            item.setOnAction(e -> loadCandidate(candidate, downloadButton, siteMenuButton, imageView, statusLabel));
            siteMenuButton.getItems().add(item);
        }
        siteMenuButton.setDisable(false);
    }

    private void loadCandidate(GongCandidate candidate,
                               Button downloadButton,
                               MenuButton siteMenuButton,
                               ZoomableImageView imageView,
                               Label statusLabel) {
        downloadButton.setText(message("downloading.gong.image"));
        downloadButton.setDisable(true);
        siteMenuButton.setDisable(true);

        BackgroundOperations.async(() -> {
            var optionalURL = GONG.fetchCandidateImage(candidate);
            FxUtils.runLater(() -> {
                optionalURL.ifPresentOrElse(
                    url -> {
                        statusLabel.setVisible(false);
                        imageView.setVisible(true);
                        imageView.setImage(new Image(url.toExternalForm()));
                        imageView.resetZoom();
                        siteMenuButton.setText(candidate.siteDisplayName());
                        updateFlipControls(imageView);
                    },
                    () -> showStatus(imageView, statusLabel, message("no.image.available")));
                downloadButton.setText(message("download.gong.image"));
                downloadButton.setDisable(false);
                siteMenuButton.setDisable(gongCandidates.size() <= 1);
            });
        });
    }

    private void showStatus(ZoomableImageView imageView, Label statusLabel, String text) {
        imageView.setImage(null);
        imageView.setVisible(false);
        statusLabel.setText(text);
        statusLabel.setVisible(true);
        updateFlipControls(imageView);
    }

    private void updateFlipControls(ZoomableImageView imageView) {
        var show = imageView.getImage() != null && activeImageSupplier != null && activeImageSupplier.get() != null;
        detectFlipsButton.setVisible(show);
        detectFlipsButton.setManaged(show);
        flipResultLabel.setVisible(false);
        flipResultLabel.setManaged(false);
        flipResultLabel.setText("");
    }

    private void detectFlips(ZoomableImageView imageView) {
        var gong = imageView.getImage();
        var active = activeImageSupplier == null ? null : activeImageSupplier.get();
        if (gong == null || active == null) {
            updateFlipControls(imageView);
            return;
        }
        detectFlipsButton.setDisable(true);
        detectFlipsButton.setText(message("gong.detect.flips.computing"));
        flipResultLabel.setVisible(false);
        flipResultLabel.setManaged(false);

        BackgroundOperations.async(() -> {
            try {
                var pAngle = active.findMetadata(ProcessParams.class)
                    .map(p -> SolarParametersUtils.computeSolarParams(p.observationDetails().date().toLocalDateTime()).p())
                    .orElse(0.0);
                var detection = GongOrientationAnalyzer.detect(active, pAngle, gong, true);
                FxUtils.runLater(() -> {
                    detectFlipsButton.setDisable(false);
                    detectFlipsButton.setText(message("gong.detect.flips"));
                    flipResultLabel.setText(describeFlips(detection));
                    flipResultLabel.setManaged(true);
                    flipResultLabel.setVisible(true);
                });
            } catch (Exception ex) {
                LOGGER.error("GONG flip detection failed", ex);
                FxUtils.runLater(() -> {
                    detectFlipsButton.setDisable(false);
                    detectFlipsButton.setText(message("gong.detect.flips"));
                });
            }
        });
    }

    private static String describeFlips(GongOrientationAnalyzer.FlipDetection detection) {
        if (!detection.hasFlip()) {
            return message("gong.detect.flips.none");
        }
        if (detection.horizontalFlip() && detection.verticalFlip()) {
            return message("gong.detect.flips.both");
        }
        if (detection.horizontalFlip()) {
            return message("gong.detect.flips.horizontal");
        }
        return message("gong.detect.flips.vertical");
    }

    private static ComboBox<GongResolution> createSizeSelector() {
        var selector = new ComboBox<GongResolution>();
        selector.getItems().addAll(GongResolution.LOW, GongResolution.FULL);
        selector.setValue(GongResolution.FULL);
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(GongResolution resolution) {
                if (resolution == null) {
                    return "";
                }
                return switch (resolution) {
                    case LOW -> message("gong.size.low");
                    case FULL -> message("gong.size.full");
                };
            }

            @Override
            public GongResolution fromString(String string) {
                return GongResolution.FULL;
            }
        });
        return selector;
    }

    private static String formatOffset(long minutes) {
        if (minutes == 0) {
            return message("gong.offset.same");
        }
        if (minutes > 0) {
            return MessageFormat.format(message("gong.offset.after"), minutes);
        }
        return MessageFormat.format(message("gong.offset.before"), -minutes);
    }

    private TextField createTimeField(int defaultValue, int min, int max) {
        var field = new TextField();
        field.setPrefColumnCount(2);
        field.setPrefWidth(40);
        field.setMaxWidth(40);
        field.setMinWidth(40);
        field.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String value) {
                var asInt = super.fromString(value);
                if (asInt == null) {
                    return defaultValue;
                }
                return Math.max(min, Math.min(max, asInt));
            }

            @Override
            public String toString(Integer value) {
                return value == null ? "00" : String.format("%02d", value);
            }
        }));
        return field;
    }

    private void setupImageContextMenu(ZoomableImageView imageView) {
        var contextMenu = new ContextMenu();
        var save = new MenuItem(message("save.gong.image"));
        save.setOnAction(ev -> saveGongImage(imageView.getImage()));
        contextMenu.getItems().add(save);
        imageView.getImageView().setOnContextMenuRequested(ev -> {
            if (imageView.getImage() != null) {
                contextMenu.show(imageView, ev.getScreenX(), ev.getScreenY());
            }
        });
    }

    private void saveGongImage(Image image) {
        if (image == null) {
            return;
        }
        var fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(IMAGE_FILES_EXTENSIONS);
        var file = fileChooser.showSaveDialog(rootStage);
        if (file != null) {
            BackgroundOperations.async(() -> {
                try {
                    var outputFile = file;
                    var extIndex = outputFile.getName().lastIndexOf(".");
                    if (extIndex == -1) {
                        outputFile = new File(file.getParent(), file.getName() + ".png");
                    }
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), outputFile.getName().substring(outputFile.getName().lastIndexOf(".") + 1), outputFile);
                } catch (IOException ex) {
                    LOGGER.error(message("error.cannot.save.image"), ex);
                }
            });
        }
    }
}
