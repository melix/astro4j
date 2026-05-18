/*
 * Copyright 2023-2025 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.sunscan;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller for the "Import from SunScan" dialog. It lists the scans available on a
 * SunScan device, lets the user pick the ones to process, downloads their {@code scan.ser}
 * files into a chosen folder and hands them over to the regular processing pipeline.
 */
public class SunscanImportController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SunscanImportController.class);
    private static final String SUNSCAN_DIR_KEY = "sunscan";

    private static final Map<String, String> LINE_NAMES = Map.ofEntries(
            Map.entry("halpha", "H-alpha"),
            Map.entry("hbeta", "H-beta"),
            Map.entry("hgamma", "H-gamma"),
            Map.entry("hdelta", "H-delta"),
            Map.entry("hepsilon", "H-epsilon"),
            Map.entry("sodium", "Sodium"),
            Map.entry("feI", "Fe I"),
            Map.entry("feX", "Fe X"),
            Map.entry("feXIV", "Fe XIV"),
            Map.entry("mgI1", "Mg I"),
            Map.entry("mgI2", "Mg I"),
            Map.entry("mgI3", "Mg I"),
            Map.entry("heI", "Helium (D3)"),
            Map.entry("caIIK", "Ca II K"),
            Map.entry("caIIH", "Ca II H")
    );

    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML
    private javafx.scene.control.TextField hostField;
    @FXML
    private Button connectButton;
    @FXML
    private Label statusLabel;
    @FXML
    private CheckBox selectAllCheckBox;
    @FXML
    private TableView<ScanRow> scansTable;
    @FXML
    private TableColumn<ScanRow, Boolean> selectedColumn;
    @FXML
    private TableColumn<ScanRow, String> nameColumn;
    @FXML
    private TableColumn<ScanRow, String> dateColumn;
    @FXML
    private TableColumn<ScanRow, String> lineColumn;
    @FXML
    private TableColumn<ScanRow, String> statusColumn;
    @FXML
    private javafx.scene.control.TextField destinationField;
    @FXML
    private Button browseButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button importButton;

    private Stage stage;
    private Configuration configuration;
    private Consumer<List<File>> onImport;
    private SunscanClient client;
    private boolean busy;

    public void setup(Stage stage, Configuration configuration, Consumer<List<File>> onImport) {
        this.stage = stage;
        this.configuration = configuration;
        this.onImport = onImport;

        selectedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectedColumn));
        selectedColumn.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        nameColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getName()));
        dateColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getDate()));
        lineColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getLine()));
        statusColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().getStatus()));
        scansTable.setEditable(true);

        selectAllCheckBox.selectedProperty().addListener((obs, old, selected) -> {
            for (var row : scansTable.getItems()) {
                row.selectedProperty().set(selected);
            }
        });

        hostField.setText(configuration.getSunscanHost());
        configuration.findLastOpenDirectory(SUNSCAN_DIR_KEY)
                .ifPresent(dir -> destinationField.setText(dir.toString()));
        progressBar.setVisible(false);

        autoDetect();
    }

    private void autoDetect() {
        statusLabel.setText(message("detecting"));
        var thread = new Thread(() -> {
            var detected = SunscanClient.autoDetect();
            Platform.runLater(() -> {
                if (client != null) {
                    return;
                }
                if (detected.isPresent()) {
                    client = detected.get();
                    hostField.setText(client.baseUrl());
                    loadScans();
                } else {
                    statusLabel.setText(message("not.detected"));
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void connect() {
        var host = hostField.getText();
        if (host == null || host.isBlank()) {
            statusLabel.setText(message("missing.host"));
            return;
        }
        client = new SunscanClient(host);
        loadScans();
    }

    private void loadScans() {
        if (client == null) {
            return;
        }
        setBusy(true);
        statusLabel.setText(message("connecting"));
        var current = client;
        var thread = new Thread(() -> {
            try {
                var scans = current.listAllScans();
                Platform.runLater(() -> {
                    setBusy(false);
                    configuration.setSunscanHost(current.baseUrl());
                    var rows = new ArrayList<ScanRow>();
                    for (var scan : scans) {
                        rows.add(new ScanRow(scan));
                    }
                    scansTable.setItems(FXCollections.observableArrayList(rows));
                    selectAllCheckBox.setSelected(false);
                    statusLabel.setText(rows.isEmpty()
                            ? message("no.scans")
                            : message("scans.found").formatted(rows.size(), current.baseUrl()));
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LOGGER.warn("Unable to list SunScan scans", e);
                Platform.runLater(() -> {
                    setBusy(false);
                    statusLabel.setText(message("connection.failed").formatted(current.baseUrl()));
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void browseDestination() {
        var chooser = new DirectoryChooser();
        var current = destinationField.getText();
        if (current != null && !current.isBlank()) {
            var dir = new File(current);
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
        }
        var selected = chooser.showDialog(stage);
        if (selected != null) {
            destinationField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void importSelected() {
        if (busy) {
            return;
        }
        var selected = scansTable.getItems().stream()
                .filter(row -> row.selectedProperty().get())
                .map(ScanRow::scan)
                .toList();
        if (selected.isEmpty()) {
            statusLabel.setText(message("no.selection"));
            return;
        }
        var destinationText = destinationField.getText();
        if (destinationText == null || destinationText.isBlank()) {
            statusLabel.setText(message("missing.destination"));
            return;
        }
        var destination = Path.of(destinationText);
        var current = client;
        setBusy(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        var thread = new Thread(() -> {
            var downloaded = new ArrayList<File>();
            try {
                for (var i = 0; i < selected.size(); i++) {
                    var scan = selected.get(i);
                    var target = destination.resolve(scan.folderName()).resolve("scan.ser");
                    var index = i;
                    Platform.runLater(() -> statusLabel.setText(
                            message("downloading").formatted(scan.folderName(), index + 1, selected.size())));
                    current.downloadScan(scan, target, (bytes, total) -> {
                        if (total > 0) {
                            var fraction = (index + (double) bytes / total) / selected.size();
                            Platform.runLater(() -> progressBar.setProgress(fraction));
                        }
                    });
                    downloaded.add(target.toFile());
                }
                Platform.runLater(() -> {
                    setBusy(false);
                    progressBar.setProgress(1);
                    configuration.updateLastOpenDirectory(destination, SUNSCAN_DIR_KEY);
                    stage.close();
                    onImport.accept(downloaded);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LOGGER.error("Unable to download SunScan scan", e);
                Platform.runLater(() -> {
                    setBusy(false);
                    progressBar.setVisible(false);
                    statusLabel.setText(message("download.failed").formatted(e.getMessage()));
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void cancel() {
        stage.close();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        connectButton.setDisable(busy);
        importButton.setDisable(busy);
        browseButton.setDisable(busy);
        hostField.setDisable(busy);
    }

    private static String message(String key) {
        return I18N.string(SunscanImportController.class, "sunscan-import", key);
    }

    /**
     * Row model backing the scans table.
     */
    public class ScanRow {
        private final SunscanScan scan;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

        ScanRow(SunscanScan scan) {
            this.scan = scan;
        }

        SunscanScan scan() {
            return scan;
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public String getName() {
            return scan.folderName();
        }

        public String getDate() {
            try {
                return dateFormatter.format(Instant.ofEpochSecond(scan.creationDate()));
            } catch (RuntimeException e) {
                return "";
            }
        }

        public String getLine() {
            var tag = scan.tag();
            if (tag == null || tag.isBlank()) {
                return "";
            }
            return LINE_NAMES.getOrDefault(tag, tag);
        }

        public String getStatus() {
            var status = scan.status();
            if (status == null || status.isBlank()) {
                return "";
            }
            var label = message("status." + status);
            return label.isEmpty() ? status : label;
        }
    }
}
