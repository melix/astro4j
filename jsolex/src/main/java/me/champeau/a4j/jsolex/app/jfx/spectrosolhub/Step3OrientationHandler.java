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

import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.jfx.Corrector;
import me.champeau.a4j.jsolex.app.jfx.GongOrientationAnalyzer;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.app.jfx.WritableImageSupport;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.app.jfx.bass2000.ComparisonModeManager;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.params.AutocropMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.GONG;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.jfx.bass2000.ComparisonModeManager.ComparisonMode.*;

class Step3OrientationHandler implements StepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Step3OrientationHandler.class);
    private static final int IMAGE_VIEW_SIZE = 400;
    private static final Set<GeneratedImageKind> NON_DISK_KINDS = EnumSet.of(
            GeneratedImageKind.RECONSTRUCTION,
            GeneratedImageKind.RAW,
            GeneratedImageKind.DEBUG,
            GeneratedImageKind.CROPPED,
            GeneratedImageKind.COLLAGE,
            GeneratedImageKind.REDSHIFT,
            GeneratedImageKind.ELLERMAN_BOMBS,
            GeneratedImageKind.FLARES,
            GeneratedImageKind.ACTIVE_REGIONS,
            GeneratedImageKind.VIRTUAL_ECLIPSE,
            GeneratedImageKind.DOPPLER_ECLIPSE);

    private final ProcessParams processParams;
    private final Supplier<List<MultipleImagesViewer.ImageInfo>> imagesSupplier;
    private final ComparisonModeManager comparisonModeManager;
    private final Scaling scaling = new Scaling(Map.of(), Broadcaster.NO_OP, new Crop(Map.of(), Broadcaster.NO_OP));
    private ZoomableImageView userImageView;
    private VBox userImageLoadingOverlay;
    private Label userImageLoadingLabel;
    private MenuButton userImageMenuButton;
    private List<MultipleImagesViewer.ImageInfo> userImages = List.of();
    private boolean userImageManuallySelected;
    private ZoomableImageView gongImageView;
    private VBox gongImageContainer;
    private StackPane gongImageStack;
    private MenuButton gongSiteMenuButton;
    private List<GONG.GongCandidate> gongCandidates = List.of();
    private HBox comparisonControlsBox;
    private Button comparisonModeButton;
    private Label comparisonModeLabel;
    private Slider comparisonModeSlider;
    private Image gongReferenceImage;
    private boolean userImageDisplayed;

    Step3OrientationHandler(ProcessParams processParams, Supplier<List<MultipleImagesViewer.ImageInfo>> imagesSupplier) {
        this.processParams = processParams;
        this.imagesSupplier = imagesSupplier;
        this.comparisonModeManager = new ComparisonModeManager(
                Step3OrientationHandler::message, null, List.of(NORMAL, BLEND, BLINK));
    }

    private static String message(String key) {
        return SpectroSolHubSubmissionController.message(key);
    }

    @Override
    public VBox createContent() {
        var content = new VBox(10);
        content.setPadding(new Insets(10));

        var title = new Label(message("orientation.title"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var instruction = new Label(message("orientation.instruction"));
        instruction.setWrapText(true);

        var warning = new Label(message("orientation.warning"));
        warning.setWrapText(true);
        warning.setMaxWidth(Double.MAX_VALUE);
        warning.setMinHeight(Label.USE_PREF_SIZE);
        warning.setStyle("-fx-background-color: #fff3e0; -fx-text-fill: #e65100; -fx-padding: 10; -fx-border-color: #e65100; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");

        var imageComparisonBox = new HBox(10);
        imageComparisonBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");

        var userImageContainer = new VBox(5);
        userImageContainer.setAlignment(Pos.CENTER);
        HBox.setHgrow(userImageContainer, Priority.ALWAYS);
        var userImageLabel = new Label(message("orientation.your.image"));
        userImageLabel.setStyle("-fx-font-weight: bold;");
        userImageView = new ZoomableImageView();
        userImageView.setPrefSize(IMAGE_VIEW_SIZE, IMAGE_VIEW_SIZE);
        userImageView.setMinSize(200, 200);
        VBox.setVgrow(userImageView, Priority.ALWAYS);
        HBox.setHgrow(userImageView, Priority.ALWAYS);
        userImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        userImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        userImageLoadingLabel = createLoadingLabel(message("orientation.your.image.loading"));
        userImageLoadingOverlay = createLoadingIndicator(userImageLoadingLabel);
        userImageMenuButton = new MenuButton(message("orientation.your.image.pick"));
        userImageMenuButton.getStyleClass().add("image-picker-button");
        userImageMenuButton.setMinWidth(170);
        userImageMenuButton.setDisable(true);
        var userImageStack = new StackPane(userImageView, userImageMenuButton, userImageLoadingOverlay);
        StackPane.setAlignment(userImageMenuButton, Pos.TOP_LEFT);
        StackPane.setMargin(userImageMenuButton, new Insets(6, 6, 6, 10));
        VBox.setVgrow(userImageStack, Priority.ALWAYS);
        HBox.setHgrow(userImageStack, Priority.ALWAYS);
        userImageContainer.getChildren().addAll(userImageLabel, userImageStack);

        gongImageContainer = new VBox(5);
        gongImageContainer.setAlignment(Pos.CENTER);
        HBox.setHgrow(gongImageContainer, Priority.ALWAYS);
        var gongImageLabel = new Label(message("orientation.gong.reference"));
        gongImageLabel.setStyle("-fx-font-weight: bold;");
        gongSiteMenuButton = new MenuButton(message("orientation.gong.pick.site"));
        gongSiteMenuButton.getStyleClass().add("gong-site-button");
        gongSiteMenuButton.setMinWidth(170);
        gongSiteMenuButton.setDisable(true);
        gongImageView = new ZoomableImageView();
        gongImageView.setPrefSize(IMAGE_VIEW_SIZE, IMAGE_VIEW_SIZE);
        gongImageView.setMinSize(200, 200);
        VBox.setVgrow(gongImageView, Priority.ALWAYS);
        HBox.setHgrow(gongImageView, Priority.ALWAYS);
        gongImageView.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gongImageView.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gongImageView.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        gongImageStack = new StackPane();
        gongImageStack.getChildren().addAll(gongImageView, gongSiteMenuButton);
        StackPane.setAlignment(gongSiteMenuButton, Pos.TOP_RIGHT);
        StackPane.setMargin(gongSiteMenuButton, new Insets(6, 10, 6, 6));
        VBox.setVgrow(gongImageStack, Priority.ALWAYS);
        HBox.setHgrow(gongImageStack, Priority.ALWAYS);
        gongImageContainer.getChildren().addAll(gongImageLabel, gongImageStack);

        imageComparisonBox.getChildren().addAll(userImageContainer, gongImageContainer);
        VBox.setVgrow(imageComparisonBox, Priority.ALWAYS);

        comparisonModeManager.setOriginalImageComparisonBox(imageComparisonBox);

        comparisonControlsBox = new HBox(8);
        comparisonControlsBox.setStyle("-fx-alignment: center; -fx-padding: 1;");
        comparisonControlsBox.setVisible(false);
        comparisonControlsBox.setManaged(false);

        comparisonModeLabel = new Label(message("blink.duration"));
        comparisonModeLabel.setStyle("-fx-font-weight: bold;");

        comparisonModeSlider = new Slider(0.0, 100.0, 50.0);
        comparisonModeSlider.setShowTickLabels(true);
        comparisonModeSlider.setShowTickMarks(true);
        comparisonModeSlider.setMajorTickUnit(25.0);
        comparisonModeSlider.setMinorTickCount(4);
        comparisonModeSlider.setPrefWidth(300);
        comparisonModeSlider.valueProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) ->
                comparisonModeManager.handleComparisonSliderChange(newValue));

        comparisonControlsBox.getChildren().addAll(comparisonModeLabel, comparisonModeSlider);

        var controlsBox = new HBox(8);
        controlsBox.setStyle("-fx-alignment: center; -fx-padding: 1;");

        comparisonModeButton = new Button(comparisonModeManager.getComparisonModeButtonText());
        comparisonModeButton.getStyleClass().add("custom-button");
        comparisonModeButton.setOnAction(e -> comparisonModeManager.toggleComparisonMode(
                comparisonControlsBox, comparisonModeButton, comparisonModeLabel, comparisonModeSlider));

        var fullscreenButton = new Button(message("button.fullscreen"));
        fullscreenButton.getStyleClass().add("custom-button");
        fullscreenButton.setOnAction(e -> comparisonModeManager.openFullscreenComparison(null));

        controlsBox.getChildren().addAll(comparisonModeButton, fullscreenButton);

        content.getChildren().addAll(title, instruction, warning, imageComparisonBox, comparisonControlsBox, controlsBox);
        return content;
    }

    @Override
    public void load() {
        userImages = imagesSupplier.get();
        populateUserImageMenu();
        loadGongReferenceImage();
    }

    private void populateUserImageMenu() {
        if (userImageMenuButton == null) {
            return;
        }
        var selectable = userImages.stream()
                .filter(image -> !NON_DISK_KINDS.contains(image.kind()))
                .toList();
        if (selectable.isEmpty()) {
            selectable = userImages;
        }
        var toggleGroup = new ToggleGroup();
        userImageMenuButton.getItems().clear();
        for (var image : selectable) {
            var item = new RadioMenuItem(image.title());
            item.setToggleGroup(toggleGroup);
            item.setUserData(image);
            item.setOnAction(e -> selectUserImage(image));
            userImageMenuButton.getItems().add(item);
        }
        userImageMenuButton.setDisable(selectable.size() <= 1);
    }

    private void selectUserImage(MultipleImagesViewer.ImageInfo image) {
        userImageManuallySelected = true;
        userImageDisplayed = true;
        userImageLoadingLabel.setText(message("orientation.your.image.loading.selected"));
        userImageLoadingOverlay.setVisible(true);
        userImageLoadingOverlay.setManaged(true);
        BackgroundOperations.async(() -> displayUserImage(image));
    }

    private void markSelectedUserImage(MultipleImagesViewer.ImageInfo image) {
        if (userImageMenuButton == null) {
            return;
        }
        for (var item : userImageMenuButton.getItems()) {
            if (item instanceof RadioMenuItem radio && radio.getUserData() == image) {
                radio.setSelected(true);
                return;
            }
        }
    }

    @Override
    public void cleanup() {
        comparisonModeManager.cleanup();
    }

    @Override
    public boolean validate() {
        return true;
    }

    private void loadUserImageWithHeuristic() {
        if (userImageDisplayed) {
            return;
        }
        var representative = findRepresentativeImage(userImages);
        if (representative == null) {
            hideUserImageLoadingOverlay();
            return;
        }
        BackgroundOperations.async(() -> displayUserImage(representative));
    }

    private void selectBestMatchingUserImage(Image gongImage) {
        if (userImageDisplayed) {
            return;
        }
        if (userImages.isEmpty()) {
            hideUserImageLoadingOverlay();
            return;
        }
        BackgroundOperations.async(() -> {
            var representative = findClosestImageToGong(userImages, gongImage);
            if (representative == null) {
                representative = findRepresentativeImage(userImages);
            }
            if (representative != null && !userImageManuallySelected) {
                displayUserImage(representative);
            }
        });
    }

    private void displayUserImage(MultipleImagesViewer.ImageInfo representative) {
        try {
            var wrapper = representative.image();
            if (wrapper instanceof FileBackedImage fbi) {
                wrapper = fbi.unwrapToMemory();
            }
            var image = wrapper.copy();
            image = applyPAngleCorrectionIfNeeded(image, representative.kind());
            image = scaling.rescaleToRadius(image, 225, 512, 512);
            LinearStrechingStrategy.DEFAULT.stretch(image);
            if (image instanceof ImageWrapper32 mono) {
                image = RGBImage.toRGB(mono);
            }
            var writableImage = WritableImageSupport.asWritable(image);
            FxUtils.runLater(() -> {
                userImageDisplayed = true;
                hideUserImageLoadingOverlay();
                markSelectedUserImage(representative);
                userImageView.setImage(writableImage);
                userImageView.resetZoom();
                if (gongReferenceImage != null) {
                    comparisonModeManager.setImages(writableImage, gongReferenceImage);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to load user image for orientation check", e);
            hideUserImageLoadingOverlay();
        }
    }

    private void hideUserImageLoadingOverlay() {
        FxUtils.runLater(() -> {
            if (userImageLoadingOverlay != null) {
                userImageLoadingOverlay.setVisible(false);
                userImageLoadingOverlay.setManaged(false);
            }
        });
    }

    private MultipleImagesViewer.ImageInfo findClosestImageToGong(List<MultipleImagesViewer.ImageInfo> images, Image gongImage) {
        var scored = collectDiskCandidates(images).stream()
                .map(candidate -> scoreAgainstGong(candidate, gongImage))
                .filter(Objects::nonNull)
                .toList();
        var byScore = Comparator.comparingDouble(ScoredCandidate::score);
        return scored.stream()
                .filter(ScoredCandidate::mono)
                .max(byScore)
                .or(() -> scored.stream().max(byScore))
                .map(ScoredCandidate::info)
                .orElse(null);
    }

    private ScoredCandidate scoreAgainstGong(MultipleImagesViewer.ImageInfo candidate, Image gongImage) {
        try {
            var wrapper = candidate.image();
            if (wrapper instanceof FileBackedImage fbi) {
                wrapper = fbi.unwrapToMemory();
            }
            var detection = GongOrientationAnalyzer.detect(wrapper, 0, gongImage, false);
            return new ScoredCandidate(candidate, detection.score(), wrapper instanceof ImageWrapper32);
        } catch (Exception e) {
            LOGGER.debug("Failed to score image against GONG reference", e);
            return null;
        }
    }

    private record ScoredCandidate(MultipleImagesViewer.ImageInfo info, double score, boolean mono) {
    }

    private List<MultipleImagesViewer.ImageInfo> collectDiskCandidates(List<MultipleImagesViewer.ImageInfo> images) {
        var mainShift = processParams != null ? new PixelShift(processParams.spectrumParams().pixelShift()) : new PixelShift(0);
        var zeroShift = new PixelShift(0);
        var byKind = new EnumMap<GeneratedImageKind, MultipleImagesViewer.ImageInfo>(GeneratedImageKind.class);
        var rankByKind = new EnumMap<GeneratedImageKind, Integer>(GeneratedImageKind.class);
        for (var image : images) {
            var kind = image.kind();
            if (NON_DISK_KINDS.contains(kind)) {
                continue;
            }
            var ps = image.image().findMetadata(PixelShift.class).orElse(null);
            var rank = ps != null && ps.equals(mainShift) ? 0 : ps != null && ps.equals(zeroShift) ? 1 : 2;
            var existing = rankByKind.get(kind);
            if (existing == null || rank < existing) {
                rankByKind.put(kind, rank);
                byKind.put(kind, image);
            }
        }
        return new ArrayList<>(byKind.values());
    }

    private void loadGongReferenceImage() {
        if (gongReferenceImage != null) {
            gongImageView.setImage(gongReferenceImage);
            gongImageView.resetZoom();
            return;
        }
        if (processParams == null || processParams.observationDetails().date() == null) {
            loadUserImageWithHeuristic();
            return;
        }
        var observationDate = processParams.observationDetails().date();
        showGongLoadingLabel();
        BackgroundOperations.async(() -> {
            try {
                var candidates = GONG.listGongCandidates(observationDate);
                FxUtils.runLater(() -> populateGongSiteMenu(candidates, observationDate));
                if (candidates.isEmpty()) {
                    FxUtils.runLater(() -> {
                        showGongUnavailableLabel();
                        loadUserImageWithHeuristic();
                    });
                    return;
                }
                loadGongCandidate(candidates.getFirst());
            } catch (Exception e) {
                LOGGER.error("Failed to load GONG reference image", e);
                FxUtils.runLater(() -> {
                    showGongUnavailableLabel();
                    loadUserImageWithHeuristic();
                });
            }
        });
    }

    private void showGongLoadingLabel() {
        swapGongContent(createLoadingIndicator(createLoadingLabel(message("orientation.gong.loading"))));
    }

    private void showGongUnavailableLabel() {
        var label = new Label(message("orientation.gong.unavailable"));
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: orange;");
        swapGongContent(label);
        if (gongSiteMenuButton != null && !gongCandidates.isEmpty()) {
            gongSiteMenuButton.setDisable(false);
        }
    }

    private void swapGongContent(Node content) {
        if (gongImageStack == null) {
            return;
        }
        gongImageStack.getChildren().set(0, content);
    }

    private static Label createLoadingLabel(String text) {
        var label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        label.setWrapText(true);
        label.setMaxWidth(IMAGE_VIEW_SIZE * 0.8);
        return label;
    }

    private static VBox createLoadingIndicator(Label label) {
        var spinner = new ProgressIndicator();
        spinner.setMinSize(48, 48);
        spinner.setMaxSize(48, 48);
        var box = new VBox(12, spinner, label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void populateGongSiteMenu(List<GONG.GongCandidate> candidates, ZonedDateTime requestedDate) {
        gongCandidates = oneCandidatePerSite(candidates);
        if (gongSiteMenuButton == null) {
            return;
        }
        gongSiteMenuButton.getItems().clear();
        if (gongCandidates.isEmpty()) {
            gongSiteMenuButton.setDisable(true);
            return;
        }
        var utcRequested = requestedDate.withZoneSameInstant(ZoneId.of("UTC"));
        var timeFormatter = DateTimeFormatter.ofPattern("HH:mm 'UTC'");
        for (var candidate : gongCandidates) {
            var offsetMinutes = ChronoUnit.MINUTES.between(utcRequested, candidate.timestamp());
            var offsetText = formatOffset(offsetMinutes);
            var label = MessageFormat.format(message("orientation.gong.candidate"),
                candidate.siteDisplayName(),
                candidate.timestamp().format(timeFormatter),
                offsetText);
            var item = new MenuItem(label);
            item.setOnAction(e -> loadGongCandidateAsync(candidate));
            gongSiteMenuButton.getItems().add(item);
        }
        gongSiteMenuButton.setDisable(false);
    }

    private static List<GONG.GongCandidate> oneCandidatePerSite(List<GONG.GongCandidate> candidates) {
        var seen = new HashSet<String>();
        var result = new ArrayList<GONG.GongCandidate>();
        for (var candidate : candidates) {
            if (seen.add(candidate.siteCode())) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void loadGongCandidateAsync(GONG.GongCandidate candidate) {
        if (gongSiteMenuButton != null) {
            gongSiteMenuButton.setDisable(true);
        }
        showGongLoadingLabel();
        BackgroundOperations.async(() -> loadGongCandidate(candidate));
    }

    private void loadGongCandidate(GONG.GongCandidate candidate) {
        try {
            var url = GONG.fetchCandidateImage(candidate);
            if (url.isEmpty()) {
                FxUtils.runLater(() -> {
                    showGongUnavailableLabel();
                    loadUserImageWithHeuristic();
                });
                return;
            }
            try (var inputStream = url.get().openStream()) {
                var image = new Image(inputStream);
                FxUtils.runLater(() -> {
                    if (!image.isError()) {
                        gongReferenceImage = image;
                        restoreGongImageView();
                        gongImageView.setImage(image);
                        gongImageView.resetZoom();
                        if (gongSiteMenuButton != null) {
                            gongSiteMenuButton.setText(candidate.siteDisplayName());
                            gongSiteMenuButton.setDisable(gongCandidates.size() <= 1);
                        }
                        if (userImageView.getImage() != null) {
                            comparisonModeManager.setImages(userImageView.getImage(), image);
                        }
                        selectBestMatchingUserImage(image);
                    } else {
                        showGongUnavailableLabel();
                        loadUserImageWithHeuristic();
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GONG candidate image", e);
            FxUtils.runLater(() -> {
                showGongUnavailableLabel();
                loadUserImageWithHeuristic();
            });
        }
    }

    private void restoreGongImageView() {
        if (gongImageStack == null) {
            return;
        }
        if (gongImageStack.getChildren().get(0) != gongImageView) {
            gongImageStack.getChildren().set(0, gongImageView);
        }
    }

    private static String formatOffset(long minutes) {
        if (minutes == 0) {
            return message("orientation.gong.offset.same");
        }
        if (minutes > 0) {
            return MessageFormat.format(message("orientation.gong.offset.after"), minutes);
        }
        return MessageFormat.format(message("orientation.gong.offset.before"), -minutes);
    }

    private ImageWrapper applyPAngleCorrectionIfNeeded(ImageWrapper image, GeneratedImageKind kind) {
        if (processParams == null || processParams.observationDetails().date() == null) {
            return image;
        }
        boolean pAlreadyCorrected = processParams.geometryParams().isAutocorrectAngleP() && !kind.cannotPerformManualRotation();
        if (!pAlreadyCorrected) {
            var p = SolarParametersUtils.computeSolarParams(processParams.observationDetails().date().toLocalDateTime()).p();
            if (p != 0) {
                image = Corrector.rotate(image, p, processParams.geometryParams().autocropMode() == AutocropMode.OFF);
            }
        }
        return image;
    }

    private MultipleImagesViewer.ImageInfo findRepresentativeImage(List<MultipleImagesViewer.ImageInfo> images) {
        var mainShift = processParams != null ? new PixelShift(processParams.spectrumParams().pixelShift()) : new PixelShift(0);
        var zeroShift = new PixelShift(0);
        MultipleImagesViewer.ImageInfo processedAtMainShift = null;
        MultipleImagesViewer.ImageInfo rawAtMainShift = null;
        MultipleImagesViewer.ImageInfo imageMathAtMainShift = null;
        MultipleImagesViewer.ImageInfo processedAtZeroShift = null;
        MultipleImagesViewer.ImageInfo rawAtZeroShift = null;
        MultipleImagesViewer.ImageInfo imageMathAtZeroShift = null;
        MultipleImagesViewer.ImageInfo continuum = null;
        for (var image : images) {
            var ps = image.image().findMetadata(PixelShift.class).orElse(null);
            if (ps != null && ps.equals(mainShift)) {
                if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) {
                    processedAtMainShift = image;
                } else if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED) {
                    rawAtMainShift = image;
                } else if (image.kind() == GeneratedImageKind.IMAGE_MATH && imageMathAtMainShift == null) {
                    imageMathAtMainShift = image;
                }
            }
            if (ps != null && ps.equals(zeroShift)) {
                if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) {
                    processedAtZeroShift = image;
                } else if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED) {
                    rawAtZeroShift = image;
                } else if (image.kind() == GeneratedImageKind.IMAGE_MATH && imageMathAtZeroShift == null) {
                    imageMathAtZeroShift = image;
                }
            }
            if (image.kind() == GeneratedImageKind.CONTINUUM && continuum == null) {
                continuum = image;
            }
        }
        if (processedAtMainShift != null) {
            return processedAtMainShift;
        }
        if (rawAtMainShift != null) {
            return rawAtMainShift;
        }
        if (imageMathAtMainShift != null) {
            return imageMathAtMainShift;
        }
        if (processedAtZeroShift != null) {
            return processedAtZeroShift;
        }
        if (rawAtZeroShift != null) {
            return rawAtZeroShift;
        }
        if (imageMathAtZeroShift != null) {
            return imageMathAtZeroShift;
        }
        if (continuum != null) {
            return continuum;
        }
        for (var image : images) {
            if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) {
                return image;
            }
        }
        for (var image : images) {
            if (image.kind() == GeneratedImageKind.GEOMETRY_CORRECTED) {
                return image;
            }
        }
        return images.isEmpty() ? null : images.getFirst();
    }
}
