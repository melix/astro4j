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
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.params.SetupsIO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

public class SetupEditor {
    public static final String DEFAULT_NAME = "My setup";
    @FXML
    public ListView<Setup> elements;
    @FXML
    public CheckBox forceCamera;
    @FXML
    public CheckBox showInDetails;
    @FXML
    private TextField label;
    @FXML
    private TextField telescope;
    @FXML
    private TextField focalLength;
    @FXML
    private TextField aperture;
    @FXML
    private TextField stop;
    @FXML
    private TextField energyRejectionFilter;
    @FXML
    private TextField mount;
    @FXML
    private TextField camera;
    @FXML
    private TextField pixelSize;
    @FXML
    private TextField latitude;
    @FXML
    private TextField longitude;
    @FXML
    private CheckBox altAzMode;

    private List<Control> formFields;

    private Stage stage;
    private Setup selected;

    public static void openEditor(Stage stage, Consumer<? super SetupEditor> onCloseRequest) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "setup-editor");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (SetupEditor) fxmlLoader.getController();
            controller.setup(stage);
            Scene scene = newScene(node);
            var currentScene = stage.getScene();
            stage.setTitle(I18N.string(JSolEx.class, "setup-editor", "frame.title"));
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
                return x == null || x < 0 ? null : x;
            }
        });
    }

    public void setup(Stage stage) {
        formFields = List.of(
            label,
            telescope,
            mount,
            focalLength,
            aperture,
            stop,
            energyRejectionFilter,
            camera,
            pixelSize,
            latitude,
            longitude,
            showInDetails,
            forceCamera,
            altAzMode
        );
        label.setTextFormatter(new TextFormatter<>(new StringConverter<String>() {
            @Override
            public String toString(String object) {
                if (object == null || object.isEmpty()) {
                    return DEFAULT_NAME;
                }
                return object;
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        }));
        focalLength.setTextFormatter(createPositiveIntFormatter());
        aperture.setTextFormatter(createPositiveIntFormatter());
        stop.setTextFormatter(createPositiveIntFormatter());
        pixelSize.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        this.stage = stage;
        var items = elements.getItems();
        var selectionModel = elements.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (items.isEmpty()) {
                return;
            }
            if (updating.compareAndSet(false, true)) {
                var index = newValue.intValue();
                var item = items.get(index);
                label.setText(nullable(item.label(), s -> s));
                telescope.setText(nullable(item.telescope(), s -> s));
                mount.setText(nullable(item.mount(), s -> s));
                focalLength.setText(nullable(item, i -> String.valueOf(i.focalLength())));
                aperture.setText(nullable(item.aperture(), String::valueOf));
                stop.setText(nullable(item.stop(), String::valueOf));
                energyRejectionFilter.setText(nullable(item.energyRejectionFilter(), s -> s));
                camera.setText(nullable(item.camera(), s -> s));
                pixelSize.setText(nullable(item.pixelSize(), String::valueOf));
                latitude.setText(nullable(item.latitude(), String::valueOf));
                longitude.setText(nullable(item.longitude(), String::valueOf));
                forceCamera.setSelected(item.forceCamera());
                showInDetails.setSelected(item.showCoordinatesInDetails());
                altAzMode.setSelected(item.altAzMode());
                updating.set(false);
            }
        });
        ChangeListener<Object> updateValueListener = (obs, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                var newLabel = label.getText();
                var newTelescope = telescope.getText();
                var newMount = mount.getText();
                var newFocalLength = focalLength.getText();
                var newAperture = aperture.getText();
                var newStop = stop.getText();
                var newEnergyRejectionFilter = energyRejectionFilter.getText();
                var newCamera = camera.getText();
                var newPixelSize = pixelSize.getText();
                var newLatitude = latitude.getText();
                var newLongitude = longitude.getText();
                var newForceCamera = forceCamera.isSelected();
                var newShowInDetails = showInDetails.isSelected();
                var selectedIndex = selectionModel.getSelectedIndex();
                var altAz = altAzMode.isSelected();
                if (selectedIndex != -1) {
                    try {
                        var e = new Setup(
                            newLabel,
                            newTelescope,
                            nullable(newFocalLength, Integer::valueOf),
                            nullable(newAperture, Integer::valueOf),
                            nullable(newStop, Integer::valueOf),
                            nullable(newEnergyRejectionFilter, s -> s.isEmpty() ? null : s),
                            newCamera,
                            nullable(newPixelSize, SetupEditor::safeParseDouble),
                            nullable(newLatitude, SetupEditor::safeParseDouble),
                            nullable(newLongitude, SetupEditor::safeParseDouble),
                            newMount,
                            newForceCamera,
                            newShowInDetails,
                            altAz
                        );
                        items.set(selectedIndex, e);
                        elements.getSelectionModel().select(e);
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                updating.set(false);
            }
        };
        formFields.forEach(e -> {
            if (e instanceof TextInputControl text) {
                text.textProperty().addListener(updateValueListener);
            } else if (e instanceof CheckBox cb) {
                cb.selectedProperty().addListener(updateValueListener);
            }
        });
        items.addAll(SetupsIO.loadDefaults());
        if (items.isEmpty()) {
            fireDisableStatus(true);
        } else {
            elements.getSelectionModel().select(0);
        }
    }

    private static Double safeParseDouble(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static <T, V> V nullable(T obj, Function<T, V> transformer) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String str && str.isEmpty()) {
            return null;
        }
        return transformer.apply(obj);
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
        SetupsIO.saveDefaults(elements.getItems());
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

    public Optional<Setup> getSelected() {
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

    private void fireDisableStatus(boolean disable) {
        formFields.forEach(e -> e.setDisable(disable));
    }

    @FXML
    public void addNewItem() {
        fireDisableStatus(false);
        var selected = elements.getSelectionModel().getSelectedItem();
        Setup newElement;
        if (selected != null) {
            newElement = new Setup(
                selected.label() + " (Copy)",
                selected.telescope(),
                selected.focalLength(),
                selected.aperture(),
                selected.stop(),
                selected.energyRejectionFilter(),
                selected.camera(),
                selected.pixelSize(),
                selected.latitude(),
                selected.longitude(),
                selected.mount(),
                selected.forceCamera(),
                selected.showCoordinatesInDetails(),
                selected.altAzMode()
            );
        } else {
            newElement = new Setup(
                DEFAULT_NAME + " " + getItems().size(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false
            );
        }
        elements.getItems().add(newElement);
        elements.getSelectionModel().select(newElement);
    }

    public List<Setup> getItems() {
        return Collections.unmodifiableList(elements.getItems());
    }

}
