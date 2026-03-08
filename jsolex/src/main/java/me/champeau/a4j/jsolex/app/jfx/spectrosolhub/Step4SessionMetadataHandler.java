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
package me.champeau.a4j.jsolex.app.jfx.spectrosolhub;

import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.util.EquipmentDatabaseUtils;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.CreateSessionRequest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.jfx.spectrosolhub.SpectroSolHubSubmissionController.message;

class Step4SessionMetadataHandler implements StepHandler {
    private final ProcessParams processParams;
    private final SpectralRay detectedSpectralRay;
    private final Step2ImageSelectionHandler imageSelectionHandler;
    private VBox content;
    private TextField titleField;
    private Label dateLabel;
    private Label spectralLineLabel;
    private TextField telescopeField;
    private TextField cameraField;
    private TextField mountField;
    private TextArea notesArea;
    private CheckBox publishCheckBox;

    Step4SessionMetadataHandler(ProcessParams processParams, SpectralRay detectedSpectralRay, Step2ImageSelectionHandler imageSelectionHandler) {
        this.processParams = processParams;
        this.detectedSpectralRay = detectedSpectralRay;
        this.imageSelectionHandler = imageSelectionHandler;
    }

    @Override
    public VBox createContent() {
        content = new VBox(10);
        content.setPadding(new Insets(10));

        var title = new Label(message("session.title"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var instruction = new Label(message("session.instruction"));
        instruction.setWrapText(true);

        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        grid.add(new Label(message("session.name.label")), 0, row);
        titleField = new TextField();
        titleField.setPrefWidth(400);
        grid.add(titleField, 1, row++);

        grid.add(new Label(message("session.date.label")), 0, row);
        dateLabel = new Label();
        grid.add(dateLabel, 1, row++);

        grid.add(new Label(message("session.spectral.line.label")), 0, row);
        spectralLineLabel = new Label();
        grid.add(spectralLineLabel, 1, row++);

        var equipmentTitle = new Label(message("equipment.section.title"));
        equipmentTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        grid.add(equipmentTitle, 0, row++, 2, 1);

        grid.add(new Label(message("telescope.label")), 0, row);
        telescopeField = new TextField();
        grid.add(telescopeField, 1, row++);

        grid.add(new Label(message("camera.label")), 0, row);
        cameraField = new TextField();
        grid.add(cameraField, 1, row++);

        grid.add(new Label(message("mount.label")), 0, row);
        mountField = new TextField();
        grid.add(mountField, 1, row++);

        grid.add(new Label(message("session.notes.label")), 0, row);
        notesArea = new TextArea();
        notesArea.setPrefRowCount(3);
        notesArea.setPrefWidth(400);
        grid.add(notesArea, 1, row++);

        publishCheckBox = new CheckBox(message("session.visibility.public"));
        grid.add(publishCheckBox, 1, row);

        content.getChildren().addAll(title, instruction, grid);
        return content;
    }

    @Override
    public void load() {
        if (processParams == null) {
            return;
        }

        var obs = processParams.observationDetails();
        var spectrum = processParams.spectrumParams();

        var effectiveRay = computeEffectiveSpectralRay();
        var rayLabel = effectiveRay != null ? effectiveRay.label() : (spectrum.ray() != null ? spectrum.ray().label() : "");
        var dateStr = obs.date() != null ? obs.date().format(DateTimeFormatter.ISO_LOCAL_DATE) : "";

        titleField.setText(rayLabel + (dateStr.isEmpty() ? "" : " - " + dateStr));
        dateLabel.setText(obs.date() != null ? obs.date().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "");
        spectralLineLabel.setText(rayLabel);
        telescopeField.setText(obs.telescope() != null ? obs.telescope() : "");
        cameraField.setText(obs.camera() != null ? obs.camera() : "");
        mountField.setText(obs.mount() != null ? obs.mount() : "");
    }

    private SpectralRay computeEffectiveSpectralRay() {
        var selectedImages = imageSelectionHandler.getSelectedImages();
        if (!selectedImages.isEmpty() && selectedImages.stream().allMatch(img ->
                img.image().findMetadata(SpectralRay.class)
                        .filter(r -> SpectralRay.HELIUM_D3.label().equals(r.label()))
                        .isPresent())) {
            return SpectralRay.HELIUM_D3;
        }
        return detectedSpectralRay;
    }

    @Override
    public void cleanup() {
    }

    @Override
    public boolean validate() {
        if (titleField.getText().trim().isEmpty()) {
            AlertFactory.error(message("session.title.required")).showAndWait();
            return false;
        }
        return true;
    }

    private static String[] splitBrandModel(String text) {
        var parts = text.split("\\s+", 2);
        if (parts.length < 2) {
            var vendor = EquipmentDatabaseUtils.normalizeVendor(parts[0]);
            return new String[]{vendor.normalizedName(), parts[0]};
        }
        var vendor = EquipmentDatabaseUtils.normalizeVendor(parts[0]);
        return new String[]{vendor.normalizedName(), parts[1]};
    }

    boolean shouldPublish() {
        return publishCheckBox != null && publishCheckBox.isSelected();
    }

    CreateSessionRequest buildSessionRequest() {
        var obs = processParams != null ? processParams.observationDetails() : null;
        var instrument = obs != null ? obs.instrument() : null;

        CreateSessionRequest.SpectroheliographInfo spectroInfo = null;
        if (instrument != null) {
            spectroInfo = new CreateSessionRequest.SpectroheliographInfo(
                    instrument.label(),
                    instrument.density(),
                    instrument.order(),
                    instrument.slitWidthMicrons(),
                    instrument.slitHeightMillimeters(),
                    instrument.focalLength(),
                    instrument.collimatorFocalLength(),
                    instrument.totalAngleDegrees()
            );
        }

        CreateSessionRequest.TelescopeInfo telescopeInfo = null;
        var telescopeText = telescopeField.getText().trim();
        if (!telescopeText.isBlank()) {
            var parts = splitBrandModel(telescopeText);
            telescopeInfo = new CreateSessionRequest.TelescopeInfo(
                    parts[0],
                    parts[1],
                    obs != null ? obs.focalLength() : null,
                    obs != null ? obs.aperture() : null
            );
        }

        CreateSessionRequest.CameraInfo cameraInfo = null;
        var cameraText = cameraField.getText().trim();
        if (!cameraText.isBlank()) {
            var parts = splitBrandModel(cameraText);
            cameraInfo = new CreateSessionRequest.CameraInfo(
                    parts[0],
                    parts[1],
                    obs != null ? obs.pixelSize() : null
            );
        }

        CreateSessionRequest.MountInfo mountInfo = null;
        var mountText = mountField.getText().trim();
        if (!mountText.isBlank()) {
            var parts = splitBrandModel(mountText);
            mountInfo = new CreateSessionRequest.MountInfo(
                    parts[0],
                    parts[1],
                    obs != null && obs.altAzMode() ? "ALT-AZ" : "EQUATORIAL"
            );
        }

        Double latitude = null;
        Double longitude = null;
        if (obs != null && obs.coordinates() != null) {
            latitude = obs.coordinates().a();
            longitude = obs.coordinates().b();
        }

        String isoDate = null;
        if (obs != null && obs.date() != null) {
            isoDate = obs.date().toInstant().toString();
        }

        var effectiveRay = computeEffectiveSpectralRay();
        var rayLabel = effectiveRay != null ? effectiveRay.label() : "";
        Double wavelengthAngstroms = null;
        if (effectiveRay != null && effectiveRay.wavelength().angstroms() != 0) {
            wavelengthAngstroms = effectiveRay.wavelength().angstroms();
        }
        return new CreateSessionRequest(
                titleField.getText().trim(),
                isoDate,
                rayLabel,
                wavelengthAngstroms,
                spectroInfo,
                telescopeInfo,
                cameraInfo,
                obs != null ? obs.binning() : null,
                mountInfo,
                obs != null ? obs.energyRejectionFilter() : null,
                latitude,
                longitude,
                notesArea.getText().trim(),
                "JSol'Ex",
                VersionUtil.getVersion()
        );
    }
}
