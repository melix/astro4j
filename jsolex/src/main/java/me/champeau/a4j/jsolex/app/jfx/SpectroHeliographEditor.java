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

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SpectroHeliographEditor {
    @FXML
    public ListView<SpectroHeliograph> elements;
    @FXML
    private TextField label;
    @FXML
    private TextField cameraFocalLength;
    @FXML
    private TextField collimatorFocalLength;
    @FXML
    private TextField totalAngle;
    @FXML
    private TextField density;
    @FXML
    private TextField order;
    @FXML
    private TextField slitWidth;
    @FXML
    private TextField slitHeight;
    @FXML
    private CheckBox spectrumVFlip;

    private List<Control> formFields;

    private Stage stage;
    private SpectroHeliograph selected;

    public static void openEditor(Stage stage, Consumer<? super SpectroHeliographEditor> onCloseRequest) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "shg-editor");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (SpectroHeliographEditor) fxmlLoader.getController();
            controller.setup(stage);
            Scene scene = new Scene(node);
            var currentScene = stage.getScene();
            stage.setTitle(I18N.string(JSolEx.class, "shg-editor", "frame.title"));
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> {
                if (stage.getScene() == scene) {
                    stage.setScene(currentScene);
                    e.consume();
                }
                onCloseRequest.accept(controller);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static TextFormatter<Integer> createPositiveIntFormatter() {
        return new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String value) {
                var x = super.fromString(value);
                return x == null || x < 0 ? 0 : x;
            }
        });
    }

    private static TextFormatter<Double> createPositiveDoubleFormatter() {
        return new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String value) {
                var x = super.fromString(value);
                return x == null || x < 0 ? 0 : x;
            }
        });
    }


    private void fireDisableStatus(boolean disable) {
        formFields.forEach(e -> e.setDisable(disable));
    }

    public void setup(Stage stage) {
        formFields = List.of(
            label,
            cameraFocalLength,
            totalAngle,
            density,
            order,
            spectrumVFlip
        );
        cameraFocalLength.setTextFormatter(createPositiveDoubleFormatter());
        collimatorFocalLength.setTextFormatter(createPositiveDoubleFormatter());
        totalAngle.setTextFormatter(new TextFormatter<>(new DoubleStringConverter() {
            @Override
            public Double fromString(String value) {
                var x = super.fromString(value);
                return x == null || x < 0 ? 0 : x;
            }
        }));
        density.setTextFormatter(createPositiveIntFormatter());
        order.setTextFormatter(createPositiveIntFormatter());
        slitWidth.setTextFormatter(createPositiveDoubleFormatter());
        slitHeight.setTextFormatter(createPositiveDoubleFormatter());
        elements.setCellFactory(new SGHCellFactory());
        var shgs = SpectroHeliographsIO.loadDefaults();
        this.stage = stage;
        var items = elements.getItems();
        items.addAll(shgs);
        var selectionModel = elements.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (items.isEmpty()) {
                return;
            }
            if (updating.compareAndSet(false, true)) {
                var index = newValue.intValue();
                var item = items.get(index);
                label.setText(item.label());
                cameraFocalLength.setText(String.valueOf(item.focalLength()));
                collimatorFocalLength.setText(String.valueOf(item.collimatorFocalLength()));
                totalAngle.setText(String.valueOf(item.totalAngleDegrees()));
                density.setText(String.valueOf(item.density()));
                order.setText(String.valueOf(item.order()));
                slitWidth.setText(String.valueOf(item.slitWidthMicrons()));
                slitHeight.setText(String.valueOf(item.slitHeightMillimeters()));
                spectrumVFlip.setSelected(item.spectrumVFlip());
                updating.set(false);
            }
        });
        ChangeListener<Object> updateValueListener = (obs, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                var newLabel = label.getText();
                var newCameraFocalLength = cameraFocalLength.getText();
                var newCollimatorFocalLength = collimatorFocalLength.getText();
                var newTotalAngle = totalAngle.getText();
                var newDensity = density.getText();
                var newOrder = order.getText();
                var newSlitWidth = slitWidth.getText();
                var newSlitHeight = slitHeight.getText();
                var selectedIndex = selectionModel.getSelectedIndex();
                var vFlipSelected = spectrumVFlip.isSelected();
                if (selectedIndex != -1) {
                    var e = new SpectroHeliograph(newLabel, safeParseDouble(newTotalAngle), safeParseDouble(newCameraFocalLength), safeParseDouble(newCollimatorFocalLength), safeParseInt(newDensity), safeParseInt(newOrder), safeParseDouble(newSlitWidth), safeParseDouble(newSlitHeight),
                        vFlipSelected);
                    items.set(selectedIndex, e);
                    elements.getSelectionModel().select(e);
                }
                updating.set(false);
            }
        };
        label.textProperty().addListener(updateValueListener);
        cameraFocalLength.textProperty().addListener(updateValueListener);
        collimatorFocalLength.textProperty().addListener(updateValueListener);
        totalAngle.textProperty().addListener(updateValueListener);
        density.textProperty().addListener(updateValueListener);
        order.textProperty().addListener(updateValueListener);
        slitWidth.textProperty().addListener(updateValueListener);
        slitHeight.textProperty().addListener(updateValueListener);
        spectrumVFlip.selectedProperty().addListener(updateValueListener);
        if (!shgs.isEmpty()) {
            selectionModel.select(0);
        }
        fireDisableStatus(shgs.isEmpty());
    }

    private static double safeParseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int safeParseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @FXML
    public void close() {
        var items = elements.getItems();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            for (int j = i + 1; j < items.size(); j++) {
                var other = items.get(j);
                if (item.label().equals(other.label())) {
                    // show alert
                    AlertFactory.error(
                        String.format(I18N.string(JSolEx.class, "shg-editor", "duplicate.label"), item.label())
                    ).showAndWait();
                    return;
                }
            }
        }
        if (items.isEmpty()) {
            AlertFactory.error(
                String.format(I18N.string(JSolEx.class, "shg-editor", "empty.items"))
            ).showAndWait();
            return;
        }
        SpectroHeliographsIO.saveDefaults(elements.getItems());
        this.selected = elements.getSelectionModel().getSelectedItem();
        requestClose();
    }

    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    public void cancel() {
        this.selected = null;
        requestClose();
    }

    @FXML
    public void addMissingShgs() {
        for (var shg: SpectroHeliographsIO.predefined()) {
            if (!elements.getItems().contains(shg)) {
                elements.getItems().add(shg);
            }
        }
    }

    public Optional<SpectroHeliograph> getSelected() {
        return Optional.ofNullable(selected);
    }

    @FXML
    public void removeSelectedItem() {
        var idx = elements.getSelectionModel().getSelectedIndex();
        elements.getItems().remove(idx);
        if (elements.getItems().isEmpty()) {
            formFields.forEach(e -> {
                if (e instanceof TextInputControl text) {
                    text.clear();
                } else if (e instanceof CheckBox cb) {
                    cb.setSelected(false);
                }
            });
            fireDisableStatus(true);
        } else {
            elements.getSelectionModel().select(Math.min(idx, elements.getItems().size() - 1));
        }
    }

    @FXML
    public void addNewItem() {
        fireDisableStatus(false);
        var reference = elements.getSelectionModel().getSelectedItem();
        if (reference == null) {
            reference = SpectroHeliograph.SOLEX;
        }
        var newElement = new SpectroHeliograph(
            elements.getItems().isEmpty() ? reference.label() : "Custom " + elements.getItems().size(),
            reference.totalAngleDegrees(),
            reference.focalLength(),
            reference.collimatorFocalLength(),
            reference.density(),
            reference.order(),
            reference.slitWidthMicrons(),
            reference.slitHeightMillimeters(),
            reference.spectrumVFlip()
        );
        elements.getItems().add(newElement);
        elements.getSelectionModel().select(newElement);
    }

    public List<SpectroHeliograph> getItems() {
        return Collections.unmodifiableList(elements.getItems());
    }

    public static class SGHCellFactory implements Callback<ListView<SpectroHeliograph>, ListCell<SpectroHeliograph>> {
        @Override
        public ListCell<SpectroHeliograph> call(ListView<SpectroHeliograph> shgList) {
            return new ListCell<>() {
                @Override
                public void updateItem(SpectroHeliograph shg, boolean empty) {
                    super.updateItem(shg, empty);
                    if (empty || shg == null) {
                        setText(null);
                    } else {
                        var value = shg.label();
                        setText(value);
                        for (SpectroHeliograph item : shgList.getItems()) {
                            if (item != shg && item.label().equals(value)) {
                                setStyle("-fx-border-color: #ff0000");
                            } else {
                                setStyle("");
                            }
                        }
                    }
                }
            };
        }
    }
}
