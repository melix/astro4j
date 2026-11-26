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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.LineDetectionMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.acceptSerFileDrop;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formatNumber;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.label;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.message;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.text;

/**
 * The home screen: a single action to process a SER file, and the settings it will use.
 */
public final class HomePane extends StackPane {
    private static final double WIDTH = 520;

    /**
     * The actions the home screen delegates to the application.
     */
    public interface Actions {
        /**
         * Asks the user for a SER file and processes it.
         */
        void chooseAndProcess();

        /**
         * Processes the given SER file.
         *
         * @param serFile the file to process
         */
        void process(File serFile);

        /**
         * Opens the batch mode.
         */
        void openBatch();

        /**
         * Opens a file with the full parameters dialog.
         */
        void openWithSettings();

        /**
         * Opens the assistant.
         */
        void configure();
    }

    private final Stage owner;
    private final Actions actions;
    private final GridPane rows = new GridPane();
    private final HBox revertRow = new HBox(10);

    /**
     * Creates the home screen.
     *
     * @param owner the main window
     * @param actions the actions delegated to the application
     */
    public HomePane(Stage owner, Actions actions) {
        this.owner = owner;
        this.actions = actions;
        var choose = new Button(message("choose.file"));
        choose.getStyleClass().add("primary-button");
        choose.setOnAction(e -> actions.chooseAndProcess());
        var dropZone = new VBox(6, label("home.title", "onboarding-drop-title"), label("home.subtitle", "onboarding-muted"), choose);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.getStyleClass().add("onboarding-drop-zone");
        dropZone.setMaxWidth(WIDTH);
        dropZone.setPrefWidth(WIDTH);
        rows.setHgap(12);
        rows.getStyleClass().add("home-settings-rows");
        var revert = new Hyperlink(message("home.revert"));
        revert.setOnAction(e -> revertToAutomatic());
        revert.setMinWidth(Region.USE_PREF_SIZE);
        var revertSpacer = new Region();
        HBox.setHgrow(revertSpacer, Priority.ALWAYS);
        revertRow.getChildren().addAll(label("home.revert.note", "onboarding-muted"), revertSpacer, revert);
        revertRow.setAlignment(Pos.CENTER_LEFT);
        revertRow.getStyleClass().add("home-settings-footer");
        var settings = new VBox(label("home.settings", "home-settings-header"), rows, revertRow);
        settings.getStyleClass().add("home-settings");
        settings.setMaxWidth(WIDTH);
        settings.setPrefWidth(WIDTH);
        var batch = new Hyperlink(message("home.batch"));
        batch.setOnAction(e -> actions.openBatch());
        var custom = new Hyperlink(message("home.custom"));
        custom.setOnAction(e -> actions.openWithSettings());
        var links = new HBox(22, batch, custom);
        links.setAlignment(Pos.CENTER);
        var column = new VBox(14, dropZone, settings, links);
        column.setAlignment(Pos.CENTER);
        column.setPadding(new Insets(30));
        column.setMaxWidth(WIDTH + 60);
        getChildren().add(column);
        setAlignment(Pos.CENTER);
        acceptSerFileDrop(this, actions::process);
        refresh();
    }

    /**
     * Updates the displayed settings from the saved parameters.
     */
    public void refresh() {
        rows.getChildren().clear();
        var configured = ProcessParamsIO.hasSavedDefaults();
        var params = ProcessParams.loadDefaults();
        if (configured) {
            var details = params.observationDetails();
            var instrument = details.instrument() == null ? "" : details.instrument().label();
            var camera = details.camera() == null || details.camera().isBlank() ? message("home.camera.unknown") : details.camera();
            var pixels = details.pixelSize() == null ? "" : message("home.pixel.size").replace("{value}", formatNumber(details.pixelSize()));
            var edit = new Button(message("home.edit"));
            edit.getStyleClass().add("default-button");
            edit.setOnAction(e -> {
                if (EquipmentDialog.show(owner)) {
                    refresh();
                }
            });
            addRow(0, "home.equipment", instrument + " · " + camera, pixels, false, edit);
        } else {
            var configure = new Button(message("home.configure"));
            configure.getStyleClass().add("default-button");
            configure.setOnAction(e -> actions.configure());
            addRow(0, "home.equipment", message("home.equipment.missing"), message("home.equipment.missing.note"), true, configure);
        }
        var manualLine = params.spectrumParams().detectionMode() == LineDetectionMode.MANUAL;
        if (manualLine) {
            var ray = params.spectrumParams().ray();
            var wavelength = String.format(Locale.US, "%.2f Å", ray.wavelength().angstroms());
            addRow(1, "home.line", ray.label() + " (" + wavelength + ")", message("home.line.manual"), true, null);
        } else {
            addRow(1, "home.line", message("home.line.auto"), message("home.line.auto.note"), false, null);
        }
        var requested = params.requestedImages();
        var customImages = isCustomSelection(requested);
        if (customImages) {
            addRow(2, "home.images", message("home.images.custom"), describeCustomSelection(requested), true, null);
        } else {
            var quick = !requested.images().containsAll(RequestedImages.FULL_MODE);
            addRow(2, "home.images", message(quick ? "home.images.quick" : "home.images.full"), "", false, null);
        }
        var deviates = manualLine || customImages;
        revertRow.setVisible(deviates);
        revertRow.setManaged(deviates);
    }

    private void addRow(int row, String key, String value, String note, boolean changed, Node action) {
        var valueBox = new VBox(1, text(value, changed ? "home-settings-value-changed" : "home-settings-value"));
        if (!note.isEmpty()) {
            valueBox.getChildren().add(text(note, "onboarding-muted"));
        }
        GridPane.setHgrow(valueBox, Priority.ALWAYS);
        var keyLabel = label(key, "onboarding-muted");
        keyLabel.setMinWidth(70);
        rows.add(keyLabel, 0, row);
        rows.add(valueBox, 1, row);
        if (action != null) {
            rows.add(action, 2, row);
        }
        for (var cell : new Node[]{keyLabel, valueBox}) {
            GridPane.setMargin(cell, new Insets(8, 0, 8, 0));
        }
    }

    private static boolean isQuickSelection(Set<GeneratedImageKind> images) {
        var fullOnly = EnumSet.copyOf(RequestedImages.FULL_MODE);
        fullOnly.removeAll(RequestedImages.QUICK_MODE);
        return images.containsAll(RequestedImages.QUICK_MODE) && Collections.disjoint(images, fullOnly);
    }

    private static boolean isCustomSelection(RequestedImages requested) {
        var images = requested.images();
        var preset = images.containsAll(RequestedImages.FULL_MODE) || isQuickSelection(images);
        return !preset || !requested.mathImages().scriptFiles().isEmpty() || requested.applyAutomaticScripts();
    }

    private static String describeCustomSelection(RequestedImages requested) {
        var images = (int) requested.images().stream().filter(kind -> kind != GeneratedImageKind.DEBUG).count();
        var scripts = requested.mathImages().scriptFiles().size();
        var key = switch (scripts) {
            case 0 -> "home.images.custom.note";
            case 1 -> "home.images.custom.note.script";
            default -> "home.images.custom.note.scripts";
        };
        return message(key).replace("{images}", String.valueOf(images)).replace("{scripts}", String.valueOf(scripts));
    }

    private void revertToAutomatic() {
        var params = ProcessParams.loadDefaults();
        var requested = params.requestedImages();
        if (isCustomSelection(requested)) {
            var alert = AlertFactory.confirmation(message("home.revert.confirm"));
            alert.initOwner(owner);
            var answer = alert.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.OK) {
                return;
            }
        }
        var images = EnumSet.copyOf(RequestedImages.FULL_MODE);
        if (requested.images().contains(GeneratedImageKind.DEBUG)) {
            images.add(GeneratedImageKind.DEBUG);
        }
        var reverted = params
                .withSpectrumParams(params.spectrumParams().withDetectionMode(LineDetectionMode.FREE_SEARCH))
                .withRequestedImages(requested.withImages(images).withMathImages(ImageMathParams.NONE).withApplyAutomaticScripts(false));
        ProcessParams.saveDefaults(reverted);
        refresh();
    }

    /**
     * Returns the parameters to process a file with, after checking the camera of the file.
     *
     * @param serFile the file to process
     * @return the parameters, empty if the user cancelled
     */
    public Optional<ProcessParams> paramsFor(File serFile) {
        var params = CameraCheck.confirm(owner, serFile, ProcessParams.loadDefaults());
        refresh();
        return params;
    }
}
