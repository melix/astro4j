/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.onboarding;

import javafx.collections.FXCollections;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.util.EquipmentDatabaseUtils;
import me.champeau.a4j.math.tuples.DoublePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.addRow;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formGrid;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formatNumber;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.message;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.parseNumber;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.parsePositive;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.smallField;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.text;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.withUnit;

final class EquipmentForm {
    private final TextField camera = new TextField();
    private final TextField pixelSize = smallField();
    private final Label pixelSizeNote = text("");
    private final ChoiceBox<Integer> binning = new ChoiceBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 7, 8));
    private final TextField telescope = new TextField();
    private final TextField focalLength = smallField();
    private final TextField aperture = smallField();
    private final TextField mount = new TextField();
    private final RadioButton equatorial = new RadioButton(message("mount.equatorial"));
    private final RadioButton altAz = new RadioButton(message("mount.altaz"));
    private final TextField observer = new TextField();
    private final TextField email = new TextField();
    private final TextField latitude = new TextField();
    private final TextField longitude = new TextField();
    private final List<Runnable> listeners = new ArrayList<>();

    EquipmentForm() {
        camera.setPromptText("ZWO ASI178MM");
        pixelSize.setPromptText("2.4");
        binning.setValue(1);
        telescope.setPromptText(message("telescope.prompt"));
        focalLength.setPromptText("480");
        aperture.setPromptText("80");
        mount.setPromptText(message("mount.prompt"));
        observer.setPromptText(message("observer.prompt"));
        email.setPromptText(message("email.prompt"));
        latitude.setPromptText("48.85");
        longitude.setPromptText("2.35");
        var mountType = new ToggleGroup();
        equatorial.setToggleGroup(mountType);
        altAz.setToggleGroup(mountType);
        equatorial.setSelected(true);
        camera.textProperty().addListener((obs, oldValue, newValue) -> {
            var inferred = EquipmentDatabaseUtils.cameraToPixelSizeMicrons(newValue);
            if (inferred != null) {
                pixelSize.setText(formatNumber(inferred));
            }
            updatePixelSizeNote();
            fireChanged();
        });
        pixelSize.textProperty().addListener((obs, oldValue, newValue) -> {
            updatePixelSizeNote();
            fireChanged();
        });
        binning.valueProperty().addListener((obs, oldValue, newValue) -> fireChanged());
        mountType.selectedToggleProperty().addListener((obs, oldValue, newValue) -> fireChanged());
        updatePixelSizeNote();
    }

    void onChange(Runnable listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }

    private void updatePixelSizeNote() {
        pixelSizeNote.getStyleClass().removeAll("onboarding-ok", "onboarding-warn");
        var inferred = EquipmentDatabaseUtils.cameraToPixelSizeMicrons(camera.getText());
        var entered = pixelSizeValue();
        if (inferred != null && entered.isPresent() && Math.abs(entered.get() - inferred) < 0.01) {
            pixelSizeNote.setText(message("pixel.size.found"));
            pixelSizeNote.getStyleClass().add("onboarding-ok");
        } else if (inferred != null) {
            pixelSizeNote.setText(message("pixel.size.differs").replace("{value}", formatNumber(inferred)));
            pixelSizeNote.getStyleClass().add("onboarding-warn");
        } else if (camera.getText() == null || camera.getText().isBlank()) {
            pixelSizeNote.setText(message("pixel.size.auto"));
        } else if (entered.isPresent()) {
            pixelSizeNote.setText("");
        } else {
            pixelSizeNote.setText(message("pixel.size.unknown"));
            pixelSizeNote.getStyleClass().add("onboarding-warn");
        }
    }

    String cameraValue() {
        return camera.getText() == null ? "" : camera.getText().trim();
    }

    Optional<Double> pixelSizeValue() {
        return parsePositive(pixelSize.getText());
    }

    int binningValue() {
        return binning.getValue() == null ? 1 : binning.getValue();
    }

    boolean isAltAz() {
        return altAz.isSelected();
    }

    void setCamera(String cameraName, int binningValue) {
        camera.setText(cameraName);
        binning.setValue(binningValue);
    }

    void load(ObservationDetails details) {
        camera.setText(nullToEmpty(details.camera()));
        if (details.pixelSize() != null) {
            pixelSize.setText(formatNumber(details.pixelSize()));
        }
        binning.setValue(details.binning() == null ? 1 : details.binning());
        telescope.setText(nullToEmpty(details.telescope()));
        focalLength.setText(details.focalLength() == null ? "" : String.valueOf(details.focalLength()));
        aperture.setText(details.aperture() == null ? "" : String.valueOf(details.aperture()));
        mount.setText(nullToEmpty(details.mount()));
        (details.altAzMode() ? altAz : equatorial).setSelected(true);
        observer.setText(nullToEmpty(details.observer()));
        email.setText(nullToEmpty(details.email()));
        if (details.coordinates() != null) {
            latitude.setText(String.valueOf(details.coordinates().a()));
            longitude.setText(String.valueOf(details.coordinates().b()));
        }
    }

    void load(Setup setup) {
        camera.setText(nullToEmpty(setup.camera()));
        pixelSize.setText(setup.pixelSize() == null ? "" : formatNumber(setup.pixelSize()));
        telescope.setText(nullToEmpty(setup.telescope()));
        focalLength.setText(setup.focalLength() == null ? "" : String.valueOf(setup.focalLength()));
        aperture.setText(setup.aperture() == null ? "" : String.valueOf(setup.aperture()));
        mount.setText(nullToEmpty(setup.mount()));
        (setup.altAzMode() ? altAz : equatorial).setSelected(true);
        latitude.setText(setup.latitude() == null ? "" : String.valueOf(setup.latitude()));
        longitude.setText(setup.longitude() == null ? "" : String.valueOf(setup.longitude()));
    }

    ObservationDetails applyTo(ObservationDetails details) {
        var lat = parseNumber(latitude.getText());
        var lon = parseNumber(longitude.getText());
        var coordinates = lat.isPresent() && lon.isPresent() ? new DoublePair(lat.get(), lon.get()) : null;
        var result = details
                .withCamera(cameraValue())
                .withBinning(binningValue())
                .withTelescope(telescope.getText().trim())
                .withFocalLength(parseInteger(focalLength.getText()))
                .withAperture(parseInteger(aperture.getText()))
                .withMount(mount.getText().trim())
                .withAltAzMode(isAltAz())
                .withObserver(observer.getText().trim())
                .withEmail(email.getText().trim())
                .withCoordinates(coordinates);
        return pixelSizeValue().map(result::withPixelSize).orElse(result);
    }

    void addCameraRows(GridPane grid, int firstRow) {
        addRow(grid, firstRow, "camera", "camera.help", camera, null);
        addRow(grid, firstRow + 1, "pixel.size", "pixel.size.help", withUnit(pixelSize, "µm"), pixelSizeNote);
        addRow(grid, firstRow + 2, "binning", "binning.help", binning, text(message("binning.note"), "onboarding-muted"));
    }

    GridPane cameraGrid() {
        var grid = formGrid();
        addCameraRows(grid, 0);
        return grid;
    }

    GridPane scopeGrid() {
        var grid = formGrid();
        addRow(grid, 0, "telescope", "telescope.help", telescope, null);
        addRow(grid, 1, "focal.length", "focal.length.help", withUnit(focalLength, "mm"), null);
        addRow(grid, 2, "aperture", "aperture.help", withUnit(aperture, "mm"), null);
        addRow(grid, 3, "mount", "mount.help", mount, null);
        addRow(grid, 4, "mount.type", "mount.type.help", new HBox(16, equatorial, altAz), null);
        return grid;
    }

    GridPane observerGrid(boolean coordinatesRecommended) {
        var grid = formGrid();
        addRow(grid, 0, "observer", "observer.help", observer, null);
        addRow(grid, 1, "email", "email.help", email, null);
        addRow(grid, 2, "latitude", "latitude.help", latitude, coordinatesRecommended ? text(message("coordinates.recommended"), "onboarding-warn") : null);
        addRow(grid, 3, "longitude", "longitude.help", longitude, null);
        return grid;
    }

    private static Integer parseInteger(String text) {
        return parsePositive(text).map(value -> (int) Math.round(value)).orElse(null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
