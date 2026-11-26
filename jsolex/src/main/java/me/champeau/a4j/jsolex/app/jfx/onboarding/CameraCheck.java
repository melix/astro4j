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

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.util.EquipmentDatabaseUtils;

import java.io.File;
import java.util.Optional;

import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formatNumber;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.message;

final class CameraCheck {
    private CameraCheck() {

    }

    static Optional<ProcessParams> confirm(Stage owner, File serFile, ProcessParams params) {
        var details = params.observationDetails();
        var metadata = CaptureSoftwareMetadataHelper.readMetadata(serFile);
        if (metadata.isEmpty() || details.forceCamera()
                || CaptureSoftwareMetadataHelper.currentCameraContainsMetadata(details.camera(), metadata.get().camera())) {
            return Optional.of(params);
        }
        var fileCamera = metadata.get().camera();
        var filePixelSize = EquipmentDatabaseUtils.cameraToPixelSizeMicrons(fileCamera);
        if (filePixelSize == null || (details.pixelSize() != null && Math.abs(details.pixelSize() - filePixelSize) < 0.01)) {
            return Optional.of(params);
        }
        var configuredCamera = details.camera() == null || details.camera().isBlank() ? message("home.camera.unknown") : details.camera();
        var configuredPixelSize = details.pixelSize() == null ? "?" : formatNumber(details.pixelSize());
        var alert = AlertFactory.warning(message("camera.check.content")
                .replace("{fileCamera}", fileCamera)
                .replace("{filePixelSize}", formatNumber(filePixelSize))
                .replace("{camera}", configuredCamera)
                .replace("{pixelSize}", configuredPixelSize));
        alert.initOwner(owner);
        alert.setTitle(message("camera.check.title"));
        alert.setHeaderText(message("camera.check.header"));
        alert.getDialogPane().setMinWidth(720);
        alert.setOnShown(e -> alert.getDialogPane().getScene().getWindow().sizeToScene());
        var keep = new ButtonType(message("camera.check.keep"), ButtonBar.ButtonData.NO);
        var useFile = new ButtonType(message("camera.check.use.file"), ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(keep, useFile, ButtonType.CANCEL);
        var answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) {
            return Optional.empty();
        }
        if (answer.get() == keep) {
            return Optional.of(params);
        }
        var updated = params.withObservationDetails(details
                .withCamera(fileCamera)
                .withPixelSize(filePixelSize)
                .withBinning(metadata.get().binning()));
        ProcessParams.saveDefaults(updated);
        return Optional.of(updated);
    }
}
