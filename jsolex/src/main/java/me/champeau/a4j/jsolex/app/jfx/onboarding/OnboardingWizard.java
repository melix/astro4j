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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.FXUtils;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.Setup;
import me.champeau.a4j.jsolex.processing.params.SetupsIO;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;
import me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper;
import me.champeau.a4j.jsolex.processing.util.EquipmentDatabaseUtils;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.SerFileReader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.acceptSerFileDrop;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.addRow;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formGrid;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.formatNumber;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.helpIcon;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.label;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.message;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.parsePositive;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.smallField;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.text;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.withUnit;

/**
 * The assistant shown to new users: it reads what it can from a SER file, asks for the
 * spectroheliograph and, when needed, the camera, then lets the user start processing.
 * The telescope, mount and observer are optional steps.
 */
public final class OnboardingWizard {
    private static final int FILE_STEP = 0;
    private static final int INSTRUMENT_STEP = 1;
    private static final int SCOPE_STEP = 2;
    private static final int OBSERVER_STEP = 3;
    private static final List<String> STEP_KEYS = List.of("step.file", "step.instrument", "step.scope", "step.observer");
    private static final double CARD_IMAGE_WIDTH = 190;

    /**
     * The outcome of the assistant.
     *
     * @param params the saved parameters
     * @param serFile the file to process, if the user chose one
     */
    public record Result(ProcessParams params, Optional<File> serFile) {
    }

    private record Variant(SpectroHeliograph instrument, boolean isNew) {
    }

    private record Missing(String key, int step) {
    }

    private final Stage stage;
    private final EquipmentForm form = new EquipmentForm();
    private final List<SpectroHeliograph> instruments = SpectroHeliographsIO.loadDefaults();
    private final Map<InstrumentFamily, Image> images = new EnumMap<>(InstrumentFamily.class);
    private final Map<InstrumentFamily, Variant> selectedVariants = new EnumMap<>(InstrumentFamily.class);
    private final HBox steps = new HBox();
    private final VBox body = new VBox(14);
    private final HBox footer = new HBox(10);
    private final Label dispersion = text("", "onboarding-dispersion");
    private final Label altAzInfo = label("scope.altaz.info", "onboarding-info");
    private final TextField specName = new TextField();
    private final TextField specFocal = smallField();
    private final TextField specCollimator = smallField();
    private final TextField specDensity = smallField();
    private final TextField specSlitWidth = smallField();
    private final TextField specSlitHeight = smallField();

    private int step = FILE_STEP;
    private File serFile;
    private Header header;
    private CaptureSoftwareMetadataHelper.CaptureMetadata captureMetadata;
    private boolean withoutFile;
    private boolean cameraCorrected;
    private InstrumentFamily family;
    private boolean editingSpecs;
    private Result result;

    private OnboardingWizard(Stage owner) {
        stage = FXUtils.newStage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(message("wizard.title"));
        for (var candidate : InstrumentFamily.values()) {
            candidate.image().ifPresent(path -> images.put(candidate, loadBundledImage(path)));
        }
        if (ProcessParamsIO.hasSavedDefaults()) {
            var details = ProcessParams.loadDefaults().observationDetails();
            form.load(details);
            if (details.instrument() != null) {
                var current = InstrumentFamily.of(details.instrument());
                variantsOf(current).stream()
                        .filter(variant -> variant.instrument().equals(details.instrument()))
                        .findFirst()
                        .ifPresent(variant -> {
                            family = current;
                            selectedVariants.put(current, variant);
                        });
            }
        }
        form.onChange(this::refreshStatus);
        for (var field : List.of(specName, specFocal, specCollimator, specDensity, specSlitWidth, specSlitHeight)) {
            field.textProperty().addListener((obs, oldValue, newValue) -> refreshStatus());
        }
        specName.setPromptText(message("new.instrument.name.prompt"));
        steps.getStyleClass().add("onboarding-steps");
        steps.setAlignment(Pos.CENTER_LEFT);
        body.getStyleClass().add("onboarding-body");
        footer.getStyleClass().add("onboarding-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        var scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("onboarding-scroll");
        var root = new BorderPane(scrollPane);
        root.setTop(steps);
        root.setBottom(footer);
        root.getStyleClass().add("onboarding-root");
        root.setPrefSize(960, 720);
        acceptSerFileDrop(root, this::selectFile);
        stage.setScene(JSolEx.newScene(root));
    }

    /**
     * Shows the assistant and waits until it is closed.
     *
     * @param owner the owner window
     * @param initialFile a SER file to start from, or null
     * @return the result, empty if the user closed the assistant without finishing it
     */
    public static Optional<Result> show(Stage owner, File initialFile) {
        var wizard = new OnboardingWizard(owner);
        if (initialFile != null) {
            wizard.selectFile(initialFile);
        }
        wizard.render();
        wizard.stage.showAndWait();
        return Optional.ofNullable(wizard.result);
    }

    private static Image loadBundledImage(String path) {
        try (var in = JSolEx.class.getResourceAsStream(path)) {
            return new Image(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean hasSource() {
        return serFile != null || withoutFile;
    }

    private boolean isCameraReadFromFile() {
        return captureMetadata != null && !cameraCorrected
                && EquipmentDatabaseUtils.cameraToPixelSizeMicrons(captureMetadata.camera()) != null;
    }

    private Optional<Double> effectivePixelSize() {
        if (family == InstrumentFamily.SUNSCAN) {
            return SetupsIO.findSunscan().map(Setup::pixelSize);
        }
        return form.pixelSizeValue();
    }

    private int effectiveBinning() {
        return family == InstrumentFamily.SUNSCAN ? 1 : form.binningValue();
    }

    private List<Variant> variantsOf(InstrumentFamily candidate) {
        var available = candidate.variantsAmong(instruments);
        if (available.isEmpty()) {
            available = candidate.variantsAmong(SpectroHeliographsIO.predefined());
        }
        var variants = new ArrayList<Variant>();
        available.forEach(instrument -> variants.add(new Variant(instrument, false)));
        if (candidate == InstrumentFamily.OTHER) {
            variants.add(new Variant(SpectroHeliograph.SOLEX.withLabel(""), true));
        }
        return variants;
    }

    private Optional<Variant> selectedVariant() {
        return Optional.ofNullable(family).map(selectedVariants::get);
    }

    private Optional<SpectroHeliograph> resolveInstrument() {
        var variant = selectedVariant();
        if (variant.isEmpty()) {
            return Optional.empty();
        }
        var base = variant.get().instrument();
        if (!editingSpecs) {
            return Optional.of(base);
        }
        var focal = parsePositive(specFocal.getText());
        var collimator = parsePositive(specCollimator.getText());
        var density = parsePositive(specDensity.getText());
        var slitWidth = parsePositive(specSlitWidth.getText());
        var slitHeight = parsePositive(specSlitHeight.getText());
        if (focal.isEmpty() || collimator.isEmpty() || density.isEmpty() || slitWidth.isEmpty() || slitHeight.isEmpty()) {
            return Optional.empty();
        }
        String name;
        if (variant.get().isNew()) {
            name = specName.getText().isBlank() ? message("new.instrument.default.name") : specName.getText().trim();
        } else {
            name = customName(base);
        }
        return Optional.of(base.withLabel(name)
                .withCameraFocalLength(focal.get())
                .withCollimatorFocalLength(collimator.get())
                .withDensity((int) Math.round(density.get()))
                .withSlitWidthMicrons(slitWidth.get())
                .withSlitHeightMillimeters(slitHeight.get()));
    }

    private static String customName(SpectroHeliograph base) {
        return message("custom.instrument").replace("{name}", base.label());
    }

    private List<Missing> missing() {
        var missing = new ArrayList<Missing>();
        if (effectivePixelSize().isEmpty()) {
            missing.add(new Missing("missing.camera", FILE_STEP));
        }
        if (resolveInstrument().isEmpty()) {
            missing.add(new Missing("missing.instrument", INSTRUMENT_STEP));
        }
        return missing;
    }

    private boolean isReady() {
        return hasSource() && missing().isEmpty();
    }

    private boolean isStepDone(int index) {
        if (index == FILE_STEP) {
            return hasSource() && effectivePixelSize().isPresent();
        }
        if (index == INSTRUMENT_STEP) {
            return resolveInstrument().isPresent();
        }
        return false;
    }

    private void render() {
        renderSteps();
        renderBody();
        renderFooter();
    }

    private void refreshStatus() {
        renderSteps();
        renderFooter();
        updateDispersion();
        altAzInfo.setVisible(form.isAltAz());
        altAzInfo.setManaged(form.isAltAz());
    }

    private void goTo(int index) {
        step = index;
        render();
    }

    private void renderSteps() {
        steps.getChildren().clear();
        for (int i = 0; i < STEP_KEYS.size(); i++) {
            if (i == SCOPE_STEP) {
                steps.getChildren().add(new Separator(Orientation.VERTICAL));
            }
            var current = i == step;
            var done = !current && isStepDone(i);
            var number = new Label(done ? "✓" : String.valueOf(i + 1));
            number.getStyleClass().add("onboarding-step-number");
            var labels = new VBox(label(STEP_KEYS.get(i), "onboarding-step-label"));
            labels.setAlignment(Pos.CENTER_LEFT);
            if (i >= SCOPE_STEP) {
                labels.getChildren().add(label("optional", "onboarding-step-optional"));
            }
            var node = new HBox(8, number, labels);
            node.setAlignment(Pos.CENTER_LEFT);
            if (current) {
                node.getStyleClass().add("onboarding-step-current");
            } else if (done) {
                node.getStyleClass().add("onboarding-step-done");
            }
            if (hasSource()) {
                var target = i;
                node.setCursor(Cursor.HAND);
                node.setOnMouseClicked(e -> goTo(target));
            }
            HBox.setHgrow(node, Priority.ALWAYS);
            node.setMaxWidth(Double.MAX_VALUE);
            steps.getChildren().add(node);
        }
    }

    private void renderBody() {
        body.getChildren().clear();
        switch (step) {
            case FILE_STEP -> renderFileStep();
            case INSTRUMENT_STEP -> renderInstrumentStep();
            case SCOPE_STEP -> renderScopeStep();
            default -> renderObserverStep();
        }
    }

    private void addTitle(String titleKey, String subtitleKey, boolean optional) {
        var title = new HBox(10, label(titleKey, "onboarding-title"));
        title.setAlignment(Pos.CENTER_LEFT);
        if (optional) {
            title.getChildren().add(label("optional", "onboarding-tag"));
        }
        body.getChildren().addAll(title, label(subtitleKey, "onboarding-subtitle"));
    }

    private void renderFileStep() {
        if (!hasSource()) {
            addTitle("file.title", "file.subtitle", false);
            var choose = new Button(message("choose.file"));
            choose.getStyleClass().add("primary-button");
            choose.setOnAction(e -> chooseFile());
            var dropZone = new VBox(8, label("drop.title", "onboarding-drop-title"), label("drop.subtitle", "onboarding-muted"), choose);
            dropZone.setAlignment(Pos.CENTER);
            dropZone.getStyleClass().add("onboarding-drop-zone");
            dropZone.setMaxWidth(640);
            var noFile = new Hyperlink(message("no.file"));
            noFile.setOnAction(e -> {
                withoutFile = true;
                render();
            });
            var column = new VBox(14, dropZone, noFile);
            column.setAlignment(Pos.CENTER);
            column.setPadding(new Insets(30, 0, 0, 0));
            body.getChildren().add(column);
        } else if (withoutFile) {
            addTitle("nofile.title", "nofile.subtitle", false);
            body.getChildren().add(block(form.cameraGrid(), label("camera.sunscan.hint", "onboarding-muted")));
            var haveFile = new Hyperlink(message("nofile.have.file"));
            haveFile.setOnAction(e -> {
                withoutFile = false;
                render();
            });
            body.getChildren().add(haveFile);
        } else {
            addTitle("file.selected.title", "file.selected.subtitle", false);
            var otherFile = new Hyperlink(message("choose.other.file"));
            otherFile.setOnAction(e -> chooseFile());
            body.getChildren().add(blockRow(new VBox(2,
                    label("file", "onboarding-muted"),
                    text(serFile.getName(), "onboarding-value"),
                    text(describe(header), "onboarding-muted")), otherFile));
            if (isCameraReadFromFile()) {
                var wrongCamera = new Hyperlink(message("camera.wrong"));
                wrongCamera.setOnAction(e -> {
                    cameraCorrected = true;
                    render();
                });
                var pixelSize = EquipmentDatabaseUtils.cameraToPixelSizeMicrons(captureMetadata.camera());
                var summary = message("camera.summary")
                        .replace("{camera}", captureMetadata.camera())
                        .replace("{pixelSize}", formatNumber(pixelSize))
                        .replace("{binning}", String.valueOf(captureMetadata.binning()));
                body.getChildren().add(blockRow(new VBox(2,
                        label("camera", "onboarding-muted"),
                        text(summary, "onboarding-value"),
                        label("camera.read", "onboarding-ok")), wrongCamera));
            } else {
                var question = captureMetadata == null || cameraCorrected
                        ? message(cameraCorrected ? "camera.correct" : "camera.missing")
                        : message("camera.unknown.pixel").replace("{camera}", captureMetadata.camera());
                body.getChildren().add(block(text(question, "onboarding-question"), form.cameraGrid(), label("camera.sunscan.hint", "onboarding-muted")));
            }
        }
    }

    private static String describe(Header header) {
        var geometry = header.geometry();
        var date = header.metadata().utcDateTime()
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(LocaleUtils.getConfiguredLocale()));
        return message("file.info")
                .replace("{width}", String.valueOf(geometry.width()))
                .replace("{height}", String.valueOf(geometry.height()))
                .replace("{frames}", String.valueOf(header.frameCount()))
                .replace("{date}", date);
    }

    private void renderInstrumentStep() {
        var title = new HBox(8, label("instrument.title", "onboarding-title"), helpIcon("instrument.help"));
        title.setAlignment(Pos.CENTER_LEFT);
        body.getChildren().addAll(title, label("instrument.subtitle", "onboarding-subtitle"));
        var cards = new HBox(14);
        for (var candidate : InstrumentFamily.values()) {
            cards.getChildren().add(card(candidate));
        }
        body.getChildren().add(cards);
        if (family != null) {
            body.getChildren().add(variantPanel());
            var hint = message("family." + family.key() + ".hint");
            if (!hint.isEmpty()) {
                body.getChildren().add(text(hint, "onboarding-muted"));
            }
        }
        updateDispersion();
    }

    private Node card(InstrumentFamily candidate) {
        Node picture;
        var image = images.get(candidate);
        if (image != null) {
            var view = new ImageView(image);
            var height = image.getWidth() * 10 / 16;
            view.setViewport(new Rectangle2D(0, (image.getHeight() - height) / 2, image.getWidth(), height));
            view.setFitWidth(CARD_IMAGE_WIDTH);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            picture = view;
        } else {
            var placeholder = new StackPane(text("?", "onboarding-placeholder-mark"));
            placeholder.getStyleClass().add("onboarding-placeholder");
            placeholder.setPrefSize(CARD_IMAGE_WIDTH, CARD_IMAGE_WIDTH * 10 / 16);
            placeholder.setMaxSize(CARD_IMAGE_WIDTH, CARD_IMAGE_WIDTH * 10 / 16);
            picture = placeholder;
        }
        var card = new VBox(4, picture,
                label("family." + candidate.key(), "onboarding-card-title"),
                label("family." + candidate.key() + ".desc", "onboarding-muted"));
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("onboarding-card");
        if (candidate == family) {
            card.getStyleClass().add("onboarding-card-selected");
        }
        card.setOnMouseClicked(e -> selectFamily(candidate));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void selectFamily(InstrumentFamily candidate) {
        family = candidate;
        var variant = selectedVariants.computeIfAbsent(candidate, f -> variantsOf(f).getFirst());
        selectVariant(variant);
    }

    private void selectVariant(Variant variant) {
        selectedVariants.put(family, variant);
        editingSpecs = variant.isNew();
        if (editingSpecs) {
            fillSpecs(variant.instrument());
        }
        render();
    }

    private void fillSpecs(SpectroHeliograph instrument) {
        specFocal.setText(formatNumber(instrument.focalLength()));
        specCollimator.setText(formatNumber(instrument.collimatorFocalLength()));
        specDensity.setText(String.valueOf(instrument.density()));
        specSlitWidth.setText(formatNumber(instrument.slitWidthMicrons()));
        specSlitHeight.setText(formatNumber(instrument.slitHeightMillimeters()));
    }

    private Node variantPanel() {
        var selected = selectedVariant().orElseThrow();
        var list = new VBox();
        list.getStyleClass().add("onboarding-variant-list");
        var listTitle = new HBox(6, label("family." + family.key() + ".question", "onboarding-panel-title"), helpIcon("family." + family.key() + ".question.help"));
        listTitle.setAlignment(Pos.CENTER_LEFT);
        listTitle.setPadding(new Insets(10, 16, 6, 16));
        list.getChildren().add(listTitle);
        for (var variant : variantsOf(family)) {
            var name = variant.isNew() ? message("new.instrument") : variant.instrument().label();
            var row = new VBox(1, text(name, "onboarding-variant-name"));
            var noteKey = variant.isNew() ? Optional.of("new.instrument.note") : InstrumentFamily.noteKey(variant.instrument());
            noteKey.ifPresent(key -> row.getChildren().add(label(key, "onboarding-muted")));
            row.getStyleClass().add("onboarding-variant-row");
            if (variant.equals(selected)) {
                row.getStyleClass().add("onboarding-variant-row-selected");
            }
            row.setOnMouseClicked(e -> selectVariant(variant));
            list.getChildren().add(row);
        }
        list.setMinWidth(290);
        list.setPrefWidth(290);
        var specs = formGrid();
        specs.setVgap(6);
        var instrument = selected.instrument();
        if (editingSpecs) {
            var row = 0;
            if (selected.isNew()) {
                addRow(specs, row++, "spec.name", "spec.name.help", specName, null);
            }
            addRow(specs, row++, "spec.focal", "spec.focal.help", withUnit(specFocal, "mm"), null);
            addRow(specs, row++, "spec.collimator", "spec.collimator.help", withUnit(specCollimator, "mm"), null);
            addRow(specs, row++, "spec.density", "spec.density.help", withUnit(specDensity, message("lines.per.mm")), null);
            addRow(specs, row++, "spec.slit.width", "spec.slit.width.help", withUnit(specSlitWidth, "µm"), null);
            addRow(specs, row, "spec.slit.height", "spec.slit.height.help", withUnit(specSlitHeight, "mm"), null);
        } else {
            addRow(specs, 0, "spec.focal", "spec.focal.help", text(formatNumber(instrument.focalLength()) + " mm"), null);
            addRow(specs, 1, "spec.collimator", "spec.collimator.help", text(formatNumber(instrument.collimatorFocalLength()) + " mm"), null);
            addRow(specs, 2, "spec.density", "spec.density.help", text(instrument.density() + " " + message("lines.per.mm")), null);
            addRow(specs, 3, "spec.slit", "spec.slit.help", text(formatNumber(instrument.slitWidthMicrons()) + " µm × " + formatNumber(instrument.slitHeightMillimeters()) + " mm"), null);
        }
        Node action;
        if (selected.isNew()) {
            action = label("new.instrument.help", "onboarding-muted");
        } else if (editingSpecs) {
            var reset = new Hyperlink(message("specs.reset"));
            reset.setOnAction(e -> {
                editingSpecs = false;
                render();
            });
            action = new VBox(2, text(message("specs.saved.as").replace("{name}", customName(instrument)), "onboarding-muted"), reset);
        } else {
            var edit = new Hyperlink(message("specs.edit"));
            edit.setOnAction(e -> {
                editingSpecs = true;
                fillSpecs(instrument);
                render();
            });
            action = edit;
        }
        var details = new VBox(8, label("characteristics", "onboarding-panel-title"), specs, dispersion, action);
        details.setPadding(new Insets(10, 16, 12, 16));
        HBox.setHgrow(details, Priority.ALWAYS);
        var panel = new HBox(list, details);
        panel.getStyleClass().add("onboarding-variant-panel");
        return panel;
    }

    private void updateDispersion() {
        var instrument = resolveInstrument();
        var pixelSize = effectivePixelSize();
        if (instrument.isPresent() && pixelSize.isPresent()) {
            var value = Equipment.angstromsPerPixel(instrument.get(), pixelSize.get(), effectiveBinning());
            dispersion.setText(message(family == InstrumentFamily.SUNSCAN ? "dispersion.sunscan" : "dispersion")
                    .replace("{value}", String.format(Locale.US, "%.3f", value)));
        } else if (pixelSize.isEmpty()) {
            dispersion.setText(message("dispersion.needs.camera"));
        } else {
            dispersion.setText(message("dispersion.needs.specs"));
        }
    }

    private void renderScopeStep() {
        addTitle("scope.title", "scope.subtitle", true);
        body.getChildren().addAll(form.scopeGrid(), altAzInfo);
        altAzInfo.setVisible(form.isAltAz());
        altAzInfo.setManaged(form.isAltAz());
    }

    private void renderObserverStep() {
        addTitle("observer.title", "observer.subtitle", true);
        body.getChildren().addAll(form.observerGrid(form.isAltAz()), label("observer.privacy", "onboarding-info"));
    }

    private Node block(Node... children) {
        var block = new VBox(12, children);
        block.getStyleClass().add("onboarding-block");
        return block;
    }

    private Node blockRow(Node content, Node action) {
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var row = new HBox(14, content, spacer, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("onboarding-block");
        return row;
    }

    private void renderFooter() {
        footer.getChildren().clear();
        var later = new Hyperlink(message("later"));
        later.setOnAction(e -> stage.close());
        var ready = isReady();
        var status = new TextFlow();
        status.getStyleClass().add(ready ? "onboarding-ok" : "onboarding-warn");
        if (!hasSource()) {
            status.getChildren().add(statusText(message("status.choose.file")));
        } else if (!ready) {
            status.getChildren().add(statusText(message("status.missing") + " "));
            var missing = missing();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) {
                    status.getChildren().add(statusText(" " + message("and") + " "));
                }
                var item = missing.get(i);
                if (item.step() == step) {
                    status.getChildren().add(statusText(message(item.key())));
                } else {
                    var link = new Hyperlink(message(item.key()));
                    link.setPadding(Insets.EMPTY);
                    link.setOnAction(e -> goTo(item.step()));
                    status.getChildren().add(link);
                }
            }
        } else if (withoutFile) {
            status.getChildren().add(statusText(message("status.ready.nofile")));
        } else {
            status.getChildren().add(statusText(message("status.ready").replace("{file}", serFile.getName())));
        }
        HBox.setHgrow(status, Priority.ALWAYS);
        footer.getChildren().addAll(later, status);
        if (step > FILE_STEP) {
            var previous = new Button(message("previous"));
            previous.getStyleClass().add("default-button");
            previous.setOnAction(e -> goTo(step - 1));
            footer.getChildren().add(previous);
        }
        if (step < OBSERVER_STEP) {
            var next = new Button(message(switch (step) {
                case FILE_STEP -> "next";
                case INSTRUMENT_STEP -> "next.scope";
                default -> "next.observer";
            }));
            next.getStyleClass().add(step == FILE_STEP && !ready ? "primary-button" : "default-button");
            next.setDisable(step == FILE_STEP ? !hasSource() : step == INSTRUMENT_STEP && family == null);
            next.setOnAction(e -> goTo(step + 1));
            footer.getChildren().add(next);
        }
        var launch = new Button(message(withoutFile ? "save.equipment" : "launch"));
        launch.getStyleClass().add(ready ? "primary-button" : "default-button");
        launch.setDisable(!ready);
        launch.setOnAction(e -> finish());
        footer.getChildren().add(launch);
    }

    private static Text statusText(String value) {
        var text = new Text(value);
        text.getStyleClass().add("onboarding-status-text");
        return text;
    }

    private void chooseFile() {
        var chooser = new FileChooser();
        chooser.getExtensionFilters().add(JSolEx.serFilesExtensionFilter());
        Configuration.getInstance().findLastOpenDirectory().ifPresent(dir -> chooser.setInitialDirectory(dir.toFile()));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            selectFile(file);
        }
    }

    private void selectFile(File file) {
        Header fileHeader;
        try (var reader = SerFileReader.of(file)) {
            fileHeader = reader.header();
        } catch (Exception e) {
            var alert = AlertFactory.error(message("file.unreadable").replace("{file}", file.getName()));
            alert.initOwner(stage);
            alert.showAndWait();
            return;
        }
        serFile = file;
        header = fileHeader;
        withoutFile = false;
        cameraCorrected = false;
        captureMetadata = CaptureSoftwareMetadataHelper.readMetadata(file).orElse(null);
        if (captureMetadata != null) {
            form.setCamera(captureMetadata.camera(), captureMetadata.binning());
        }
        render();
    }

    private void finish() {
        var instrument = resolveInstrument().orElseThrow();
        var params = ProcessParams.loadDefaults();
        var forceCamera = captureMetadata != null && cameraCorrected;
        var details = form.applyTo(params.observationDetails()).withForceCamera(forceCamera);
        if (family == InstrumentFamily.SUNSCAN) {
            details = Equipment.withSunscanCamera(details);
        }
        params = Equipment.withInstrument(params.withObservationDetails(details), instrument);
        if (!instruments.contains(instrument)) {
            Equipment.saveInstrument(instrument);
        }
        ProcessParams.saveDefaults(params);
        Equipment.saveSetup(Equipment.toSetup(message("my.equipment"), details));
        result = new Result(params, Optional.ofNullable(serFile));
        stage.close();
    }
}
