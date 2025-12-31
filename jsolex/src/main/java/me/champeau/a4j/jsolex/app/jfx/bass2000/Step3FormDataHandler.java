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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.math.tuples.DoublePair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

class Step3FormDataHandler implements StepHandler {
    private final ProcessParamsSupplier processParamsSupplier;
    private final FormValidator formValidator = new FormValidator();
    private final FileNameGenerator fileNameGenerator;
    private Runnable validationChangeListener;

    private ChoiceBox<SpectralRay> wavelengthField;
    private TextField observerNameField;
    private TextField observerEmailField;
    private TextField siteLatitudeField;
    private TextField siteLongitudeField;
    private CoordinateMapView coordinateMap;
    private CheckBox focalReducerCheckbox;
    private TextField mountNameField;
    private TextField telescopeNameField;
    private TextField telescopeFocalLengthField;
    private TextField apertureField;
    private TextField stopField;
    private TextField erfField;
    private TextField cameraNameField;
    private TextField pixelSizeField;
    private ComboBox<String> binningField;
    private TextField spectrographNameField;
    private TextField slitWidthField;
    private TextField slitHeightField;
    private TextField gratingDensityField;
    private TextField collimatorFocalLengthField;
    private TextField cameraLensFocalLengthField;
    private TextField orderField;
    private TextField totalAngleField;
    private Label filenamePreviewLabel;

    private final List<TextField> requiredFields = new ArrayList<>();
    private final List<CheckBox> requiredCheckboxes = new ArrayList<>();
    private final List<ComboBox<String>> requiredComboBoxes = new ArrayList<>();

    interface ProcessParamsSupplier {
        ProcessParams findProcessParams();
    }

    Step3FormDataHandler(ProcessParamsSupplier processParamsSupplier, FileNameGenerator fileNameGenerator) {
        this.processParamsSupplier = processParamsSupplier;
        this.fileNameGenerator = fileNameGenerator;
        initializeFields();
    }

    void setValidationChangeListener(Runnable listener) {
        this.validationChangeListener = listener;
    }

    private void initializeFields() {
        wavelengthField = new ChoiceBox<>();
        wavelengthField.getItems().addAll(Bass2000SubmissionController.ACCEPTED_SPECTRAL_RAYS.keySet());

        observerNameField = new TextField();
        observerEmailField = new TextField();
        siteLatitudeField = new TextField();
        siteLongitudeField = new TextField();
        coordinateMap = new CoordinateMapView();
        focalReducerCheckbox = new CheckBox();
        mountNameField = new TextField();
        telescopeNameField = new TextField();
        telescopeFocalLengthField = new TextField();
        apertureField = new TextField();
        stopField = new TextField();
        erfField = new TextField();
        cameraNameField = new TextField();
        pixelSizeField = new TextField();
        binningField = new ComboBox<>();
        spectrographNameField = new TextField();
        slitWidthField = new TextField();
        slitHeightField = new TextField();
        gratingDensityField = new TextField();
        collimatorFocalLengthField = new TextField();
        cameraLensFocalLengthField = new TextField();
        orderField = new TextField();
        totalAngleField = new TextField();
        filenamePreviewLabel = new Label();

        // Set up focal reducer for file name generator
        fileNameGenerator.setFocalReducerCheckbox(focalReducerCheckbox);

        // Set up coordinate map bindings
        setupCoordinateMapBindings();
    }

    @Override
    public VBox createContent() {
        var content = new VBox(10);

        var headerLabel = new Label(message("metadata.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var instructionLabel = new Label(message("metadata.instruction"));
        instructionLabel.setStyle("-fx-font-size: 14px;");

        var formGrid = createForm();

        var scrollPane = new ScrollPane();
        var formContent = new VBox(12);
        formContent.getChildren().addAll(headerLabel, instructionLabel, formGrid);
        formContent.setStyle("-fx-padding: 8;");

        scrollPane.setContent(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        content.getChildren().add(scrollPane);
        return content;
    }

    @Override
    public void load() {
        populateFormFromProcessParams();
        formValidator.validateAllFieldsVisually();
        updateFilenamePreview();
    }

    @Override
    public void cleanup() {
        // No cleanup needed for step 3
    }

    @Override
    public boolean validate() {
        return formValidator.validateForm();
    }

    // Data extraction methods
    public SpectralRay getSelectedWavelength() {
        return wavelengthField.getValue();
    }

    public ObservationDetails getObservationDetails() {
        var observer = observerNameField.getText().trim();
        var email = observerEmailField.getText().trim();
        var telescope = telescopeNameField.getText().trim();
        var mount = mountNameField.getText().trim();
        var telescopeFocalLength = parseInt(telescopeFocalLengthField.getText().trim());
        var aperture = parseInt(apertureField.getText().trim());
        var stop = parseIntOrNull(stopField.getText().trim());
        var energyRejectionFilter = erfField.getText().trim().isEmpty() ? null : erfField.getText().trim();
        var siteLatitude = parseDouble(siteLatitudeField.getText().trim());
        var siteLongitude = parseDouble(siteLongitudeField.getText().trim());
        var coordinates = new DoublePair(siteLatitude, siteLongitude);
        var camera = cameraNameField.getText().trim();
        var binning = parseInt(binningField.getValue());
        var pixelSize = parseDouble(pixelSizeField.getText().trim());

        var currentObservationDetails = processParamsSupplier.findProcessParams().observationDetails();

        return new ObservationDetails(
                observer,
                email,
                getSpectroHeliograph(),
                telescope,
                mount,
                telescopeFocalLength,
                aperture,
                stop,
                energyRejectionFilter,
                coordinates,
                currentObservationDetails.date(),
                camera,
                binning,
                pixelSize,
                currentObservationDetails.forceCamera(),
                currentObservationDetails.showCoordinatesInDetails(),
                currentObservationDetails.altAzMode()
        );
    }

    public SpectroHeliograph getSpectroHeliograph() {
        var spectrographName = spectrographNameField.getText().trim();

        var collimatorFocalLength = parseDouble(collimatorFocalLengthField.getText().trim());
        var cameraLensFocalLength = parseDouble(cameraLensFocalLengthField.getText().trim());
        var gratingDensity = parseInt(gratingDensityField.getText().trim());
        var order = parseInt(orderField.getText().trim());
        var totalAngle = parseDouble(totalAngleField.getText().trim());
        var slitWidth = parseDouble(slitWidthField.getText().trim());
        var slitHeight = parseDouble(slitHeightField.getText().trim());

        var currentObservationDetails = processParamsSupplier.findProcessParams().observationDetails();

        return new SpectroHeliograph(
                spectrographName,
                totalAngle,
                cameraLensFocalLength,
                collimatorFocalLength,
                gratingDensity,
                order,
                slitWidth,
                slitHeight,
                currentObservationDetails.instrument().spectrumVFlip()
        );
    }

    public boolean hasFocalReducer() {
        return focalReducerCheckbox.isSelected();
    }

    private GridPane createForm() {
        var formGrid = new GridPane();
        formGrid.getStyleClass().add("form-grid");
        formGrid.setHgap(15);
        formGrid.setVgap(8);
        formGrid.setStyle("-fx-padding: 5;");

        var col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);

        var col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);

        var col3 = new ColumnConstraints();
        col3.setHgrow(Priority.ALWAYS);

        formGrid.getColumnConstraints().addAll(col1, col2, col3);

        var row = 0;

        addSectionHeader(formGrid, message("observer.section.title"), row++);

        addFormField(formGrid, message("instrument.wavelength.label"), wavelengthField, 0, row++, true);

        observerNameField.setPromptText(message("observer.name.prompt"));
        observerNameField.setPrefWidth(300);
        var nameContainer = createValidatedTextField(message("observer.name.label"), observerNameField, true, null);

        observerEmailField.setPromptText(message("observer.email.prompt"));
        observerEmailField.setPrefWidth(300);
        var emailContainer = createValidatedTextField(message("observer.email.label"), observerEmailField, true, null);

        var observerContainer = new VBox(5);
        observerContainer.getChildren().addAll(nameContainer, emailContainer);
        formGrid.add(observerContainer, 0, row);

        siteLatitudeField.setPromptText(message("site.latitude.prompt"));
        siteLatitudeField.setPrefWidth(150);
        var latitudeContainer = createValidatedTextField(message("site.latitude.label"), siteLatitudeField, true, this::updateMapCoordinates);

        siteLongitudeField.setPromptText(message("site.longitude.prompt"));
        siteLongitudeField.setPrefWidth(150);
        var longitudeContainer = createValidatedTextField(message("site.longitude.label"), siteLongitudeField, true, this::updateMapCoordinates);

        var coordinatesContainer = new VBox(5);

        var decimalHint = new Label(message("site.coordinates.decimal.hint"));
        decimalHint.setWrapText(true);
        decimalHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        coordinatesContainer.getChildren().addAll(latitudeContainer, longitudeContainer, decimalHint);
        formGrid.add(coordinatesContainer, 1, row);

        var mapContainer = new VBox(5);
        var mapHeaderBox = new HBox(5);
        var mapLabel = new Label(message("map.label"));
        mapLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #495057;");
        var resetButton = new Button(message("map.reset"));
        resetButton.setStyle("-fx-font-size: 10px;");
        resetButton.setOnAction(e -> coordinateMap.resetZoom());
        mapHeaderBox.getChildren().addAll(mapLabel, resetButton);
        HBox.setHgrow(mapLabel, Priority.ALWAYS);

        var mapHelpLabel = new Label(message("map.help"));
        mapHelpLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        mapContainer.getChildren().addAll(mapHeaderBox, coordinateMap, mapHelpLabel);
        formGrid.add(mapContainer, 2, row);
        row++;

        addSectionHeader(formGrid, message("filename.preview.section.title"), row++);

        var filenamePreviewContainer = new VBox(5);
        var filenameLabel = new Label(message("filename.preview.label"));
        filenameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #495057;");

        filenamePreviewLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 8; -fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 4; -fx-background-radius: 4;");
        filenamePreviewLabel.setWrapText(true);
        filenamePreviewLabel.setMaxWidth(Double.MAX_VALUE);

        var filenameHint = new Label(message("filename.preview.hint"));
        filenameHint.setWrapText(true);
        filenameHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #666; -fx-font-style: italic;");

        filenamePreviewContainer.getChildren().addAll(filenameLabel, filenamePreviewLabel, filenameHint);
        formGrid.add(filenamePreviewContainer, 0, row++, 3, 1);

        addSectionHeader(formGrid, message("instrument.section.title"), row++);

        focalReducerCheckbox.setText(message("instrument.focal.reducer.checkbox"));
        addFormField(formGrid, message("instrument.focal.reducer.label"), focalReducerCheckbox, 0, row++, false);

        mountNameField.setPromptText(message("mount.name.prompt"));
        addFormField(formGrid, message("mount.name.label"), mountNameField, 0, row, true);

        telescopeNameField.setPromptText(message("instrument.name.prompt"));
        addFormField(formGrid, message("instrument.name.label"), telescopeNameField, 1, row, true);

        telescopeFocalLengthField.setPromptText("e.g., 1000");
        addFormField(formGrid, "Telescope Focal Length (mm)", telescopeFocalLengthField, 2, row++, true);

        apertureField.setPromptText(message("instrument.aperture.prompt"));
        addFormField(formGrid, message("instrument.aperture.label"), apertureField, 0, row, true);

        stopField.setPromptText(message("instrument.stop.prompt"));
        addFormField(formGrid, message("instrument.stop.label"), stopField, 1, row, false);

        erfField.setPromptText(message("instrument.erf.prompt"));
        addFormField(formGrid, message("instrument.erf.label"), erfField, 2, row++, false);

        cameraNameField.setPromptText(message("instrument.camera.name.prompt"));
        addFormField(formGrid, message("instrument.camera.name.label"), cameraNameField, 0, row, true);

        pixelSizeField.setPromptText(message("instrument.pixel.size.only.prompt"));
        addFormField(formGrid, message("instrument.pixel.size.only.label"), pixelSizeField, 1, row, true);
        pixelSizeField.setDisable(true);
        pixelSizeField.setEditable(false);

        binningField.getItems().addAll("1", "2", "3", "4");
        binningField.setPromptText(message("instrument.binning.prompt"));
        addFormField(formGrid, message("instrument.binning.label"), binningField, 2, row++, true);
        binningField.setDisable(true);
        binningField.setEditable(false);
        formGrid.add(new Label(message("instrument.pixels.binning.note")), 0, row++, 3, 1);

        addSectionHeader(formGrid, message("spectrograph.section.title"), row++);

        spectrographNameField.setPromptText(message("spectrograph.name.prompt"));
        addFormField(formGrid, message("spectrograph.name.label"), spectrographNameField, 0, row, true);

        slitWidthField.setPromptText(message("spectrograph.slit.width.prompt"));
        addFormField(formGrid, message("spectrograph.slit.width.label"), slitWidthField, 1, row, true);

        slitHeightField.setPromptText(message("spectrograph.slit.height.prompt"));
        addFormField(formGrid, message("spectrograph.slit.height.label"), slitHeightField, 2, row++, true);

        gratingDensityField.setPromptText(message("spectrograph.grating.density.prompt"));
        addFormField(formGrid, message("spectrograph.grating.density.label"), gratingDensityField, 0, row, true);

        collimatorFocalLengthField.setPromptText(message("spectrograph.collimator.focal.length.prompt"));
        addFormField(formGrid, message("spectrograph.collimator.focal.length.label"), collimatorFocalLengthField, 1, row, true);

        cameraLensFocalLengthField.setPromptText(message("spectrograph.camera.lens.focal.length.prompt"));
        addFormField(formGrid, message("spectrograph.camera.lens.focal.length.label"), cameraLensFocalLengthField, 2, row++, true);

        orderField.setPromptText(message("spectrograph.order.prompt"));
        addFormField(formGrid, message("spectrograph.order.label"), orderField, 0, row, true);

        totalAngleField.setPromptText(message("spectrograph.total.angle.prompt"));
        addFormField(formGrid, message("spectrograph.total.angle.label"), totalAngleField, 1, row++, true);

        setupFilenamePreviewListeners();

        return formGrid;
    }

    private void addSectionHeader(GridPane grid, String title, int row) {
        var headerLabel = new Label(title);
        headerLabel.getStyleClass().add("panel-section-title");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333; -fx-padding: 8 0 3 0;");
        grid.add(headerLabel, 0, row, 3, 1);
    }

    private VBox createValidatedTextField(String labelText, TextField field, boolean required, Runnable extraValidationAction) {
        var container = new VBox(3);
        container.setMaxWidth(Double.MAX_VALUE);

        var label = new Label(labelText);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPrefWidth(Region.USE_COMPUTED_SIZE);
        label.setStyle(required ?
            "-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #495057;" :
            "-fx-font-size: 12px; -fx-text-fill: #495057;");

        field.getStyleClass().add("text-field");

        container.getChildren().add(label);
        container.getChildren().add(field);

        if (required) {
            requiredFields.add(field);
            var errorLabel = formValidator.createErrorLabel();
            formValidator.registerFieldWithErrorLabel(field, errorLabel);
            container.getChildren().add(errorLabel);

            field.textProperty().addListener((obs, old, val) -> {
                var isValid = formValidator.isFieldValid(field);
                formValidator.updateFieldValidationStyle(field, isValid);
                if (extraValidationAction != null) {
                    extraValidationAction.run();
                }
                if (validationChangeListener != null) {
                    validationChangeListener.run();
                }
            });
        }

        return container;
    }

    private void addFormField(GridPane grid, String labelText, Node field, int column, int row, boolean required) {
        if (field instanceof TextField textField) {
            textField.setPrefWidth(180);
            var fieldContainer = createValidatedTextField(labelText, textField, required, null);
            if (field instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
            grid.add(fieldContainer, column, row);
            return;
        }

        var fieldContainer = new VBox(3);
        fieldContainer.setMaxWidth(Double.MAX_VALUE);

        var label = new Label(labelText);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPrefWidth(Region.USE_COMPUTED_SIZE);
        label.setStyle(required ?
            "-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #495057;" :
            "-fx-font-size: 12px; -fx-text-fill: #495057;");

        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        if (field instanceof CheckBox checkBox) {
            checkBox.getStyleClass().add("check-box");
            if (required) {
                requiredCheckboxes.add(checkBox);
                checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    var isValid = checkBox.isSelected();
                    formValidator.updateFieldValidationStyle(checkBox, isValid);
                    if (validationChangeListener != null) {
                        validationChangeListener.run();
                    }
                });
            }
        }
        if (field instanceof ComboBox<?> comboBox) {
            comboBox.getStyleClass().add("choice-box");
            if (required) {
                @SuppressWarnings("unchecked")
                var stringComboBox = (ComboBox<String>) comboBox;
                requiredComboBoxes.add(stringComboBox);
                stringComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                    var isValid = stringComboBox.getValue() != null;
                    formValidator.updateFieldValidationStyle(stringComboBox, isValid);
                    if (validationChangeListener != null) {
                        validationChangeListener.run();
                    }
                });
            }
        }
        if (field instanceof ChoiceBox<?> choiceBox) {
            choiceBox.getStyleClass().add("choice-box");
        }

        fieldContainer.getChildren().addAll(label, field);
        grid.add(fieldContainer, column, row);
    }

    private void populateFormFromProcessParams() {
        formValidator.setRequiredFields(requiredFields, requiredCheckboxes, requiredComboBoxes);
        formValidator.setSpecialFields(
            siteLatitudeField, siteLongitudeField,
            apertureField, pixelSizeField,
            collimatorFocalLengthField, cameraLensFocalLengthField,
            gratingDensityField, totalAngleField,
            slitWidthField, slitHeightField,
            orderField, observerEmailField,
            mountNameField, cameraNameField,
            telescopeNameField, spectrographNameField
        );

        var processParams = processParamsSupplier.findProcessParams();
        var spectralRay = processParams.spectrumParams().ray();
        if (spectralRay != null) {
            var wavelength = spectralRay.wavelength().angstroms();
            var closestRay = Bass2000SubmissionController.ACCEPTED_SPECTRAL_RAYS.keySet().stream()
                    .filter(ray -> Math.abs(ray.wavelength().angstroms() - wavelength) <= Bass2000SubmissionController.TOLERANCE_ANGSTROMS)
                    .min(Comparator.comparingDouble(ray -> Math.abs(ray.wavelength().angstroms() - wavelength)));
            closestRay.ifPresentOrElse(wavelengthField::setValue, () -> wavelengthField.setValue(Bass2000SubmissionController.BASS2000_HA));
        }

        var spectrograph = processParams.observationDetails().instrument();
        if (spectrograph != null) {
            spectrographNameField.setText(spectrograph.label());
            collimatorFocalLengthField.setText(String.format(Locale.US, "%.1f", spectrograph.collimatorFocalLength()));
            cameraLensFocalLengthField.setText(String.format(Locale.US, "%.1f", spectrograph.focalLength()));
            gratingDensityField.setText(String.format(Locale.US, "%.0f", (double) spectrograph.density()));
            orderField.setText(String.valueOf(spectrograph.order()));
            totalAngleField.setText(String.format(Locale.US, "%.1f", spectrograph.totalAngleDegrees()));
            slitWidthField.setText(String.format(Locale.US, "%.3f", spectrograph.slitWidthMicrons()));
            slitHeightField.setText(String.format(Locale.US, "%.1f", spectrograph.slitHeightMillimeters()));
        }

        var observationDetails = processParams.observationDetails();

        if (observationDetails.aperture() != null) {
            apertureField.setText(String.valueOf(observationDetails.aperture()));
        }
        if (observationDetails.stop() != null) {
            stopField.setText(String.valueOf(observationDetails.stop()));
        }

        if (observationDetails.camera() != null) {
            cameraNameField.setText(observationDetails.camera());
        }
        if (observationDetails.pixelSize() != null) {
            pixelSizeField.setText(String.format(Locale.US, "%.2f", observationDetails.pixelSize()));
        }
        if (observationDetails.binning() != null) {
            binningField.setValue(String.valueOf(observationDetails.binning()));
        }
        if (observationDetails.telescope() != null) {
            telescopeNameField.setText(observationDetails.telescope());
        }
        if (observationDetails.focalLength() != null) {
            telescopeFocalLengthField.setText(String.valueOf(observationDetails.focalLength()));
        }
        if (observationDetails.mount() != null) {
            mountNameField.setText(observationDetails.mount());
        }
        if (observationDetails.coordinates() != null) {
            var coords = observationDetails.coordinates();
            siteLatitudeField.setText(String.format(Locale.US, "%.4f", coords.a()));
            siteLongitudeField.setText(String.format(Locale.US, "%.4f", coords.b()));
            coordinateMap.setCoordinates(coords.a(), coords.b());
        }
        if (observationDetails.observer() != null && !observationDetails.observer().isBlank()) {
            observerNameField.setText(observationDetails.observer());
        }
        if (observationDetails.email() != null && !observationDetails.email().isBlank()) {
            observerEmailField.setText(observationDetails.email());
        }
        if (observationDetails.energyRejectionFilter() != null) {
            erfField.setText(observationDetails.energyRejectionFilter());
        }
    }


    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Integer parseIntOrNull(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void setupCoordinateMapBindings() {
        siteLatitudeField.textProperty().addListener((obs, oldVal, newVal) -> updateMapCoordinates());
        siteLongitudeField.textProperty().addListener((obs, oldVal, newVal) -> updateMapCoordinates());

        coordinateMap.latitudeProperty().addListener((obs, oldVal, newVal) -> updateCoordinateFields());
        coordinateMap.longitudeProperty().addListener((obs, oldVal, newVal) -> updateCoordinateFields());
    }

    private void updateMapCoordinates() {
        try {
            var latText = siteLatitudeField.getText().trim();
            var lonText = siteLongitudeField.getText().trim();

            if (!latText.isEmpty() && !lonText.isEmpty()) {
                var latitude = Double.parseDouble(latText);
                var longitude = Double.parseDouble(lonText);
                coordinateMap.setCoordinates(latitude, longitude);
            }
        } catch (NumberFormatException e) {
        }
    }

    private void updateCoordinateFields() {
        var latitude = coordinateMap.latitudeProperty().get();
        var longitude = coordinateMap.longitudeProperty().get();

        if (latitude != 0.0 || longitude != 0.0) {
            siteLatitudeField.setText(String.format(Locale.US, "%.4f", latitude));
            siteLongitudeField.setText(String.format(Locale.US, "%.4f", longitude));
        }
    }

    private void setupFilenamePreviewListeners() {
        wavelengthField.valueProperty().addListener((obs, old, val) -> updateFilenamePreview());
        focalReducerCheckbox.selectedProperty().addListener((obs, old, val) -> updateFilenamePreview());
        mountNameField.textProperty().addListener((obs, old, val) -> updateFilenamePreview());
        telescopeNameField.textProperty().addListener((obs, old, val) -> updateFilenamePreview());
        telescopeFocalLengthField.textProperty().addListener((obs, old, val) -> updateFilenamePreview());
        apertureField.textProperty().addListener((obs, old, val) -> updateFilenamePreview());
        cameraNameField.textProperty().addListener((obs, old, val) -> updateFilenamePreview());
        spectrographNameField.textProperty().addListener((obs, old, val) -> updateFilenamePreview());
    }

    private void updateFilenamePreview() {
        try {
            var processParams = processParamsSupplier.findProcessParams();
            if (processParams == null) {
                filenamePreviewLabel.setText(message("filename.preview.incomplete"));
                return;
            }

            var selectedWavelength = wavelengthField.getValue();
            if (selectedWavelength == null) {
                filenamePreviewLabel.setText(message("filename.preview.incomplete"));
                return;
            }

            var updatedParams = processParams
                    .withObservationDetails(getObservationDetails())
                    .withSpectrumParams(processParams.spectrumParams().withRay(selectedWavelength));

            var tempImage = new me.champeau.a4j.jsolex.processing.util.ImageWrapper32(1, 1, new float[][]{{0}}, java.util.Map.of(
                    me.champeau.a4j.jsolex.processing.params.ProcessParams.class, updatedParams
            ));

            var maybeFilename = fileNameGenerator.generateFileName(tempImage);
            if (maybeFilename.isPresent()) {
                filenamePreviewLabel.setText(maybeFilename.get() + ".fits");
            } else {
                filenamePreviewLabel.setText(message("filename.preview.incomplete"));
            }
        } catch (Exception e) {
            filenamePreviewLabel.setText(message("filename.preview.incomplete"));
        }
    }
}