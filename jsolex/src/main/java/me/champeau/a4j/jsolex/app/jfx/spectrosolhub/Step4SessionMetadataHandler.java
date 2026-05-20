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
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.jfx.FXUtils;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.util.EquipmentDatabaseUtils;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.CreateSessionRequest;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.QuotaResponse;

import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.jfx.spectrosolhub.SpectroSolHubSubmissionController.message;

class Step4SessionMetadataHandler implements StepHandler {
    private final ProcessParams processParams;
    private final SpectralRay detectedSpectralRay;
    private final Step2ImageSelectionHandler imageSelectionHandler;
    private final Step1AuthenticationHandler authHandler;
    private final Consumer<Boolean> validityListener;
    private VBox content;
    private TextField titleField;
    private Label dateLabel;
    private Label spectralLineLabel;
    private TextField telescopeField;
    private TextField cameraField;
    private TextField mountField;
    private TextArea notesArea;
    private CheckBox publishCheckBox;
    private Label quotaLabel;

    Step4SessionMetadataHandler(ProcessParams processParams, SpectralRay detectedSpectralRay, Step2ImageSelectionHandler imageSelectionHandler, Step1AuthenticationHandler authHandler, Consumer<Boolean> validityListener) {
        this.processParams = processParams;
        this.detectedSpectralRay = detectedSpectralRay;
        this.imageSelectionHandler = imageSelectionHandler;
        this.authHandler = authHandler;
        this.validityListener = validityListener;
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
        titleField.textProperty().addListener((obs, oldVal, newVal) -> notifyValidity());
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
        telescopeField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFieldValidationStyle(telescopeField, !newVal.trim().isEmpty());
            notifyValidity();
        });
        grid.add(telescopeField, 1, row++);

        grid.add(new Label(message("camera.label")), 0, row);
        cameraField = new TextField();
        cameraField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFieldValidationStyle(cameraField, !newVal.trim().isEmpty());
            notifyValidity();
        });
        grid.add(cameraField, 1, row++);

        grid.add(new Label(message("mount.label")), 0, row);
        mountField = new TextField();
        mountField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFieldValidationStyle(mountField, !newVal.trim().isEmpty());
            notifyValidity();
        });
        grid.add(mountField, 1, row++);

        grid.add(new Label(message("session.notes.label")), 0, row);
        notesArea = new TextArea();
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(3);
        notesArea.setPrefWidth(400);
        grid.add(notesArea, 1, row);
        var notesHint = new Label(message("session.notes.hint"));
        notesHint.setWrapText(true);
        notesHint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        notesHint.setPrefWidth(250);
        grid.add(notesHint, 2, row++);

        publishCheckBox = new CheckBox(message("session.visibility.public"));
        grid.add(publishCheckBox, 1, row++);

        var publishHint = new Label(message("session.visibility.hint"));
        publishHint.setWrapText(true);
        publishHint.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        publishHint.setPrefWidth(400);
        grid.add(publishHint, 1, row++);

        quotaLabel = new Label();
        quotaLabel.setStyle("-fx-text-fill: gray;");
        quotaLabel.setWrapText(true);
        grid.add(quotaLabel, 0, row, 2, 1);

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
        var mount = obs.mount();
        if ((mount == null || mount.isBlank()) && isSunscanInstrument(obs.instrument())) {
            mount = "Sunscan";
        }
        mountField.setText(mount != null ? mount : "");

        var quota = authHandler.getQuotaResponse();
        if (quota != null) {
            var storageUsed = FXUtils.formatBytes(quota.usedStorageBytes());
            var storageTotal = FXUtils.formatBytes(quota.quotaStorageBytes());
            quotaLabel.setText(MessageFormat.format(message("quota.usage"),
                    quota.usedImageCount(), quota.quotaImageCount(),
                    storageUsed, storageTotal));
        }
        notifyValidity();
    }

    private boolean isFormValid() {
        return !titleField.getText().trim().isEmpty()
                && !telescopeField.getText().trim().isEmpty()
                && !cameraField.getText().trim().isEmpty()
                && !mountField.getText().trim().isEmpty();
    }

    private void notifyValidity() {
        if (validityListener != null) {
            validityListener.accept(isFormValid());
        }
    }

    private static boolean isSunscanInstrument(SpectroHeliograph instrument) {
        return instrument != null && instrument.label() != null
                && instrument.label().toLowerCase(Locale.US).contains("sunscan");
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
        var missing = new ArrayList<String>();
        var telescopeValid = !telescopeField.getText().trim().isEmpty();
        var cameraValid = !cameraField.getText().trim().isEmpty();
        var mountValid = !mountField.getText().trim().isEmpty();
        updateFieldValidationStyle(telescopeField, telescopeValid);
        updateFieldValidationStyle(cameraField, cameraValid);
        updateFieldValidationStyle(mountField, mountValid);
        if (!telescopeValid) {
            missing.add(message("telescope.label"));
        }
        if (!cameraValid) {
            missing.add(message("camera.label"));
        }
        if (!mountValid) {
            missing.add(message("mount.label"));
        }
        if (!missing.isEmpty()) {
            AlertFactory.error(MessageFormat.format(message("session.equipment.required"), String.join(", ", missing))).showAndWait();
            return false;
        }
        return true;
    }

    private static void updateFieldValidationStyle(Node field, boolean isValid) {
        var currentStyle = field.getStyle() != null ? field.getStyle() : "";
        if (isValid) {
            if (currentStyle.contains("-fx-border-color: red")) {
                currentStyle = currentStyle.replaceAll("-fx-border-color: red;", "")
                        .replaceAll("-fx-border-width: 2px;", "")
                        .trim();
                currentStyle = currentStyle.replaceAll("\\s+", " ").replaceAll(";+", ";");
                if (currentStyle.endsWith(";")) {
                    currentStyle = currentStyle.substring(0, currentStyle.length() - 1);
                }
                field.setStyle(currentStyle);
            }
        } else {
            if (!currentStyle.contains("-fx-border-color: red")) {
                if (!currentStyle.isEmpty() && !currentStyle.endsWith(";")) {
                    currentStyle += "; ";
                }
                currentStyle += "-fx-border-color: red; -fx-border-width: 2px;";
                field.setStyle(currentStyle);
            }
        }
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
