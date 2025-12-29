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
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.GONG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static me.champeau.a4j.jsolex.app.JSolEx.IMAGE_FILES_EXTENSIONS;
import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Helper class for managing reference image downloading and display.
 * Provides UI components for downloading GONG H-alpha images.
 */
public final class ReferenceImageHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceImageHelper.class);

    private final Stage rootStage;
    private final Tab referenceImageTab;

    /**
     * Creates a new reference image helper.
     *
     * @param rootStage         the root application stage
     * @param referenceImageTab the tab for displaying reference images
     */
    public ReferenceImageHelper(Stage rootStage, Tab referenceImageTab) {
        this.rootStage = rootStage;
        this.referenceImageTab = referenceImageTab;
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
        var vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10));

        var titleLabel = new Label(message("reference.image.selector"));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        var datePickerBox = new HBox();
        datePickerBox.setAlignment(Pos.CENTER);
        datePickerBox.setSpacing(10);

        var dateLabel = new Label(message("select.date"));
        dateLabel.setMinWidth(Region.USE_PREF_SIZE);
        var defaultDate = serFileDate != null ? serFileDate.toLocalDate() : LocalDate.now();
        var datePicker = new DatePicker(defaultDate);
        datePicker.setPrefWidth(120);
        datePicker.setMaxWidth(120);

        var timeLabel = new Label(message("select.time"));
        timeLabel.setMinWidth(Region.USE_PREF_SIZE);
        var defaultTime = serFileDate != null ? serFileDate.toLocalTime() : LocalTime.now(ZoneId.of("UTC"));
        var hourField = createTimeField(defaultTime.getHour(), 0, 23);
        hourField.setText(String.format("%02d", defaultTime.getHour()));

        var minuteField = createTimeField(defaultTime.getMinute(), 0, 59);
        minuteField.setText(String.format("%02d", defaultTime.getMinute()));

        var colonLabel = new Label(":");
        var timeBox = new HBox(5);
        timeBox.setAlignment(Pos.CENTER);
        timeBox.getChildren().addAll(hourField, colonLabel, minuteField);

        datePickerBox.getChildren().addAll(dateLabel, datePicker, timeLabel, timeBox);

        var downloadButton = new Button(message("download.gong.image"));
        downloadButton.getStyleClass().add("primary-button");
        var messageLabel = new Label();
        var imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(400);
        imageView.setFitHeight(400);

        downloadButton.setOnAction(e -> {
            downloadButton.setText(message("downloading.gong.image"));
            downloadButton.setDisable(true);

            var selectedDate = datePicker.getValue();
            var hour = Integer.parseInt(hourField.getText());
            var minute = Integer.parseInt(minuteField.getText());
            var localDateTime = LocalDateTime.of(selectedDate, LocalTime.of(hour, minute));
            var zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of("UTC"));

            BackgroundOperations.async(() -> {
                var optionalURL = GONG.fetchGongImage(zonedDateTime);
                Platform.runLater(() -> {
                    messageLabel.setManaged(false);
                    messageLabel.setText("");
                    optionalURL.ifPresentOrElse(
                            url -> {
                                imageView.setImage(new Image(url.toExternalForm()));
                                setupImageContextMenu(imageView);
                            },
                            () -> {
                                messageLabel.setManaged(true);
                                messageLabel.setText(message("no.image.available"));
                                imageView.setImage(null);
                            });
                    downloadButton.setText(message("download.gong.image"));
                    downloadButton.setDisable(false);
                });
            });
        });

        vbox.getChildren().addAll(titleLabel, datePickerBox, downloadButton, messageLabel, imageView);

        var scrollPane = new ScrollPane(vbox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        Platform.runLater(() -> referenceImageTab.setContent(scrollPane));
    }

    private TextField createTimeField(int defaultValue, int min, int max) {
        var field = new TextField();
        field.setPrefColumnCount(2);
        field.setPrefWidth(30);
        field.setMaxWidth(30);
        field.setMinWidth(30);
        field.setStyle("-fx-pref-width: 30px; -fx-max-width: 30px; -fx-min-width: 30px;");
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

    private void setupImageContextMenu(ImageView imageView) {
        var contextMenu = new ContextMenu();
        var save = new MenuItem(message("save.gong.image"));
        save.setOnAction(ev -> saveGongImage(imageView.getImage()));
        contextMenu.getItems().add(save);
        imageView.setOnContextMenuRequested(ev -> contextMenu.show(imageView, ev.getScreenX(), ev.getScreenY()));
    }

    private void saveGongImage(Image image) {
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
