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

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.ser.Header;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class ObservationDetailsPanel extends BaseParameterPanel {

    private final Header serFileHeader;
    private TextField observerName;
    private TextField email;
    private TextField observationDate;
    private TextField latitude;
    private TextField longitude;
    private ChoiceBox<SpectroHeliograph> instrument;
    private TextField camera;
    private TextField telescope;
    private TextField mount;
    private TextField focalLength;
    private TextField aperture;
    private TextField stop;
    private TextField energyRejectionFilter;
    private ChoiceBox<Integer> binning;
    private TextField pixelSize;
    private CheckBox altAzMode;
    private ProcessParamsController controller;
    
    public ObservationDetailsPanel(Header serFileHeader) {
        this.serFileHeader = serFileHeader;
        getStyleClass().add("parameter-panel");
        initializeComponents();
        setupLayout();
    }
    
    private void initializeComponents() {
        observerName = new TextField();
        observerName.setPromptText(I18N.string(JSolEx.class, "process-params", "observer.name"));
        observerName.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "observer.tooltip")));
        
        email = new TextField();
        email.setPromptText(I18N.string(JSolEx.class, "process-params", "contact.email"));
        email.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "contact.email.tooltip")));
        
        observationDate = new TextField();
        observationDate.setText(LocalDate.now().toString());
        observationDate.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "utc.date.tooltip")));
        
        latitude = new TextField();
        latitude.setPromptText("48.8566");
        latitude.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "latitude.tooltip")));
        
        longitude = new TextField();
        longitude.setPromptText("2.3522");
        longitude.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "longitude.tooltip")));
        
        instrument = createChoiceBox();
        instrument.setItems(FXCollections.observableArrayList(SpectroHeliographsIO.loadDefaults()));
        instrument.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "instrument.tooltip")));
        instrument.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpectroHeliograph shg) {
                return shg == null ? "" : shg.label();
            }

            @Override
            public SpectroHeliograph fromString(String string) {
                return null;
            }
        });
        
        instrument.getSelectionModel().selectedItemProperty().addListener((obs, oldInstrument, newInstrument) -> {
            if (newInstrument != null) {
                controller.updateSpectrumVFlipForInstrument(newInstrument);
                controller.notifyObservationDetailsChanged();
            }
        });
        
        camera = new TextField();
        camera.setPromptText("ZWO ASI178MM");
        camera.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "camera.tooltip")));
        
        telescope = new TextField();
        telescope.setPromptText("Celestron C8");
        telescope.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "telescope.tooltip")));
        
        mount = new TextField();
        mount.setPromptText("SkyWatcher EQ6-R Pro");
        mount.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "mount.tooltip")));
        
        focalLength = new TextField();
        focalLength.setPromptText("1000");
        focalLength.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "focal.length.tooltip")));
        
        aperture = new TextField();
        aperture.setPromptText("100");
        aperture.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "aperture.tooltip")));
        
        stop = new TextField();
        stop.setPromptText("55");
        stop.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "stop.tooltip")));
        
        energyRejectionFilter = new TextField();
        energyRejectionFilter.setPromptText("ND 16");
        energyRejectionFilter.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "erf.tooltip")));
        
        binning = createChoiceBox();
        binning.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 7, 8));
        binning.setValue(1);
        binning.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "binning.tooltip")));
        binning.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            controller.notifyObservationDetailsChanged();
        });
        
        pixelSize = new TextField();
        pixelSize.setPromptText("3.76");
        pixelSize.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "pixel.size.tooltip")));
        pixelSize.textProperty().addListener((obs, oldValue, newValue) -> {
            controller.notifyObservationDetailsChanged();
        });
        
        altAzMode = new CheckBox();
        altAzMode.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "altaz.mode.tooltip")));
    }
    
    private void setupLayout() {
        setPadding(new Insets(24));
        setSpacing(24);
        
        var observerSection = createSection("observer");
        var observerGrid = createGrid();
        
        addGridRow(observerGrid, 0, I18N.string(JSolEx.class, "process-params", "observer.name") + ":", observerName, "observer.tooltip");
        addGridRow(observerGrid, 1, I18N.string(JSolEx.class, "process-params", "contact.email") + ":", email, "contact.email.tooltip");
        addGridRow(observerGrid, 2, I18N.string(JSolEx.class, "process-params", "utc.date") + ":", observationDate, "utc.date.tooltip");
        
        observerSection.getChildren().add(observerGrid);
        
        var locationSection = createSection("coords");
        var locationGrid = createGrid();
        
        addGridRow(locationGrid, 0, I18N.string(JSolEx.class, "process-params", "latitude") + ":", latitude, "latitude.tooltip");
        addGridRow(locationGrid, 1, I18N.string(JSolEx.class, "process-params", "longitude") + ":", longitude, "longitude.tooltip");
        
        locationSection.getChildren().add(locationGrid);
        
        var equipmentSection = createSection("instrument");
        var equipmentGrid = createGrid();
        
        addGridRow(equipmentGrid, 0, I18N.string(JSolEx.class, "process-params", "instrument") + ":", createInstrumentBox(), "instrument.tooltip");
        addGridRow(equipmentGrid, 1, I18N.string(JSolEx.class, "process-params", "telescope") + ":", createTelescopeBox(), "telescope.tooltip");
        addGridRow(equipmentGrid, 2, I18N.string(JSolEx.class, "process-params", "mount") + ":", mount, "mount.tooltip");
        addGridRow(equipmentGrid, 3, I18N.string(JSolEx.class, "process-params", "camera") + ":", camera, "camera.tooltip");
        addGridRow(equipmentGrid, 4, I18N.string(JSolEx.class, "process-params", "focal.length") + ":", focalLength, "focal.length.tooltip");
        addGridRow(equipmentGrid, 5, I18N.string(JSolEx.class, "process-params", "aperture") + ":", aperture, "aperture.tooltip");
        addGridRow(equipmentGrid, 6, I18N.string(JSolEx.class, "process-params", "stop") + ":", stop, "stop.tooltip");
        addGridRow(equipmentGrid, 7, I18N.string(JSolEx.class, "process-params", "erf") + ":", energyRejectionFilter, "erf.tooltip");
        addGridRow(equipmentGrid, 8, I18N.string(JSolEx.class, "process-params", "binning") + ":", binning, "binning.tooltip");
        addGridRow(equipmentGrid, 9, I18N.string(JSolEx.class, "process-params", "pixel.size") + ":", pixelSize, "pixel.size.tooltip");
        addGridRow(equipmentGrid, 10, I18N.string(JSolEx.class, "process-params", "altaz.mode") + ":", altAzMode, "altaz.mode.tooltip");

        equipmentSection.getChildren().add(equipmentGrid);
        
        
        getChildren().addAll(observerSection, equipmentSection, locationSection);
    }
    
    private HBox createInstrumentBox() {
        var box = createHBox();
        HBox.setHgrow(instrument, Priority.ALWAYS);

        var selectButton = new Button(I18N.string(JSolEx.class, "process-params", "select.equipment"));
        selectButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "select.equipment")));
        selectButton.getStyleClass().add("default-button");
        selectButton.setOnAction(e -> openInstrumentEditor());
        
        box.getChildren().addAll(instrument, selectButton);
        return box;
    }

    private HBox createTelescopeBox() {
        var box = createHBox();
        HBox.setHgrow(telescope, Priority.ALWAYS);

        var selectButton = new Button(I18N.string(JSolEx.class, "process-params", "select.equipment"));
        selectButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "process-params", "select.equipment")));
        selectButton.getStyleClass().add("default-button");
        selectButton.setOnAction(e -> openSetupEditor());
        
        box.getChildren().addAll(telescope, selectButton);
        return box;
    }
    
    
    public void loadData(ProcessParams params, CaptureSoftwareMetadataHelper.CaptureMetadata metadata) {
        var obsDetails = params.observationDetails();
        
        observerName.setText(obsDetails.observer());
        email.setText(obsDetails.email());

        observationDate.setTextFormatter(new TextFormatter<>(new ZonedDateTimeStringConverter()));
        observationDate.textProperty().set(serFileHeader.metadata().utcDateTime().toString());
        
        if (obsDetails.coordinates() != null) {
            latitude.setText(String.valueOf(obsDetails.coordinates().a()));
            longitude.setText(String.valueOf(obsDetails.coordinates().b()));
        } else {
            latitude.setText("");
            longitude.setText("");
        }
        telescope.setText(obsDetails.telescope());
        mount.setText(obsDetails.mount());
        camera.setText(obsDetails.camera());
        focalLength.setText(obsDetails.focalLength() != null ? String.valueOf(obsDetails.focalLength()) : "");
        aperture.setText(obsDetails.aperture() != null ? String.valueOf(obsDetails.aperture()) : "");
        stop.setText(obsDetails.stop() != null ? String.valueOf(obsDetails.stop()) : "");
        energyRejectionFilter.setText(obsDetails.energyRejectionFilter());
        if (obsDetails.binning() != null) {
            binning.setValue(obsDetails.binning());
        } else {
            binning.setValue(1);
        }
        pixelSize.setText(obsDetails.pixelSize() != null ? String.valueOf(obsDetails.pixelSize()) : "");
        altAzMode.setSelected(obsDetails.altAzMode());
        
        if (!instrument.getItems().isEmpty() && obsDetails.instrument() != null) {
            instrument.setValue(obsDetails.instrument());
        } else if (!instrument.getItems().isEmpty()) {
            instrument.getSelectionModel().selectFirst();
        }
        
        if (metadata != null) {
            if (metadata.camera() != null) {
                camera.setText(metadata.camera());
            }
            binning.setValue(metadata.binning());
        }
    }
    
    public ObservationDetails getObservationDetails() {
        var defaults = ProcessParams.loadDefaults().observationDetails();
        
        var lat = parseDoubleOrNull(latitude.getText());
        var lon = parseDoubleOrNull(longitude.getText());
        var coordinates = (lat != null && lon != null) ? new DoublePair(lat, lon) : null;
        
        return defaults
            .withObserver(observerName.getText())
            .withEmail(email.getText())
            .withDate(observationDate.getText() != null ? ZonedDateTime.parse(observationDate.getText()) : ZonedDateTime.now())
            .withCoordinates(coordinates)
            .withInstrument(instrument.getValue())
            .withTelescope(telescope.getText())
            .withMount(mount.getText())
            .withCamera(camera.getText())
            .withFocalLength(parseIntegerOrNull(focalLength.getText()))
            .withAperture(parseIntegerOrNull(aperture.getText()))
            .withStop(parseIntegerOrNull(stop.getText()))
            .withEnergyRejectionFilter(energyRejectionFilter.getText())
            .withBinning(binning.getValue())
            .withPixelSize(parseDoubleOrNull(pixelSize.getText()))
            .withAltAzMode(altAzMode.isSelected());
    }
    
    
    public boolean isValid() {
        return observerName.getText() != null && !observerName.getText().trim().isEmpty();
    }
    
    public void setController(ProcessParamsController controller) {
        this.controller = controller;
    }
    
    private void openInstrumentEditor() {
        controller.openInstrumentEditor();
    }
    
    private void openSetupEditor() {
        controller.openSetupEditor();
    }
    
    
    public void updateFromSetup(Setup setup) {
        telescope.setText(setup.telescope() != null ? setup.telescope() : "");
        focalLength.setText(setup.focalLength() != null ? String.valueOf(setup.focalLength()) : "");
        aperture.setText(setup.aperture() != null ? String.valueOf(setup.aperture()) : "");
        stop.setText(setup.stop() != null ? String.valueOf(setup.stop()) : "");
        energyRejectionFilter.setText(setup.energyRejectionFilter() != null ? setup.energyRejectionFilter() : "");
        camera.setText(setup.camera() != null ? setup.camera() : "");
        pixelSize.setText(setup.pixelSize() != null ? String.valueOf(setup.pixelSize()) : "");
        latitude.setText(setup.latitude() != null ? String.valueOf(setup.latitude()) : "");
        longitude.setText(setup.longitude() != null ? String.valueOf(setup.longitude()) : "");
        mount.setText(setup.mount() != null ? setup.mount() : "");
        altAzMode.setSelected(setup.altAzMode());
    }
    
    public void updateInstrument(SpectroHeliograph selectedInstrument) {
        instrument.setValue(selectedInstrument);
    }
}