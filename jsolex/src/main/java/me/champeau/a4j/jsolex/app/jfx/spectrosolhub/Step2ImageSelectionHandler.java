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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.app.jfx.WritableImageSupport;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.HeliumLineProcessor.HeliumImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ThumbnailGenerator;
import me.champeau.a4j.math.regression.Ellipse;

import javafx.application.Platform;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.jfx.spectrosolhub.SpectroSolHubSubmissionController.message;

class Step2ImageSelectionHandler implements StepHandler {
    private static final int THUMBNAIL_SIZE = 120;
    private static final Set<GeneratedImageKind> EXCLUDED_KINDS = EnumSet.of(
            GeneratedImageKind.RECONSTRUCTION,
            GeneratedImageKind.DEBUG,
            GeneratedImageKind.RAW
    );

    private static final Set<GeneratedImageKind> H_ALPHA_PRESELECTED = EnumSet.of(
            GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
            GeneratedImageKind.MIXED,
            GeneratedImageKind.DOPPLER,
            GeneratedImageKind.VIRTUAL_ECLIPSE,
            GeneratedImageKind.TECHNICAL_CARD
    );

    private static final Set<GeneratedImageKind> DEFAULT_PRESELECTED = EnumSet.of(
            GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED,
            GeneratedImageKind.MIXED
    );

    private static final String CARD_STYLE = "-fx-border-color: lightgray; -fx-border-radius: 4; -fx-background-color: white; -fx-background-radius: 4;";
    private static final String CARD_SELECTED_STYLE = "-fx-border-color: #0078d4; -fx-border-width: 2; -fx-border-radius: 4; -fx-background-color: #f3f9ff; -fx-background-radius: 4;";

    private final Supplier<List<MultipleImagesViewer.ImageInfo>> imagesSupplier;
    private final SpectralRay detectedSpectralRay;
    private final List<MultipleImagesViewer.ImageInfo> eligibleImages = new ArrayList<>();
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private VBox content;
    private FlowPane gallery;
    private VBox loadingPane;
    private CheckBox postProcessCheckBox;
    private int dragSourceIndex = -1;

    Step2ImageSelectionHandler(Supplier<List<MultipleImagesViewer.ImageInfo>> imagesSupplier, SpectralRay detectedSpectralRay) {
        this.imagesSupplier = imagesSupplier;
        this.detectedSpectralRay = detectedSpectralRay;
    }

    @Override
    public VBox createContent() {
        if (content != null) {
            return content;
        }
        content = new VBox(10);
        content.setPadding(new Insets(10));

        var title = new Label(message("images.title"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var instruction = new Label(message("images.instruction"));
        instruction.setWrapText(true);

        var recommendation = new Label(message("images.recommendation"));
        recommendation.setWrapText(true);
        recommendation.setMaxWidth(Double.MAX_VALUE);
        recommendation.setMinHeight(Label.USE_PREF_SIZE);
        recommendation.setStyle("-fx-font-style: italic; -fx-text-fill: #555555; -fx-padding: 4 8 4 8; -fx-background-color: #fff8e1; -fx-background-radius: 4; -fx-border-color: #ffe082; -fx-border-radius: 4;");

        var buttonsBox = new HBox(10);
        buttonsBox.setAlignment(Pos.CENTER_LEFT);
        var selectAll = new Button(message("images.select.all"));
        selectAll.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(true)));
        var deselectAll = new Button(message("images.deselect.all"));
        deselectAll.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(false)));
        buttonsBox.getChildren().addAll(selectAll, deselectAll);

        var reorderHint = new Label(message("images.reorder.hint"));
        reorderHint.setStyle("-fx-font-size: 0.85em; -fx-text-fill: gray;");

        postProcessCheckBox = new CheckBox(message("postprocess.checkbox"));
        postProcessCheckBox.setStyle("-fx-padding: 5 0 0 0;");

        gallery = new FlowPane(10, 10);
        gallery.setPadding(new Insets(5));

        var loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(40, 40);
        var loadingLabel = new Label(message("images.loading"));
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        loadingPane = new VBox(10, loadingIndicator, loadingLabel);
        loadingPane.setAlignment(Pos.CENTER);
        VBox.setVgrow(loadingPane, Priority.ALWAYS);

        content.getChildren().addAll(title, instruction, recommendation, buttonsBox, reorderHint, postProcessCheckBox, loadingPane);

        return content;
    }

    private VBox createImageCard(MultipleImagesViewer.ImageInfo imageInfo, boolean preselected) {
        var imageView = new ImageView();
        imageView.setFitWidth(THUMBNAIL_SIZE);
        imageView.setFitHeight(THUMBNAIL_SIZE);
        imageView.setPreserveRatio(true);
        var thumbnail = ThumbnailGenerator.generateThumbnail(imageInfo.image(), THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        imageView.setImage(WritableImageSupport.asWritable(thumbnail));

        var cb = new CheckBox();
        cb.setSelected(preselected);
        checkBoxes.add(cb);

        var checkOverlay = new StackPane(cb);
        checkOverlay.setAlignment(Pos.TOP_LEFT);
        checkOverlay.setPadding(new Insets(4));

        var imageStack = new StackPane(imageView, checkOverlay);

        var label = new Label(imageInfo.title());
        label.setStyle("-fx-font-size: 0.85em; -fx-text-alignment: center;");
        label.setMaxWidth(THUMBNAIL_SIZE + 10);
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);

        var card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(6));
        card.setPrefWidth(THUMBNAIL_SIZE + 20);

        var moveLeft = new Button("\u25C0");
        moveLeft.setStyle("-fx-font-size: 0.75em; -fx-padding: 1 4 1 4; -fx-min-width: 20;");
        moveLeft.setOnAction(e -> {
            int idx = gallery.getChildren().indexOf(card);
            if (idx > 0) {
                moveImage(idx, idx - 1);
            }
            e.consume();
        });

        var moveRight = new Button("\u25B6");
        moveRight.setStyle("-fx-font-size: 0.75em; -fx-padding: 1 4 1 4; -fx-min-width: 20;");
        moveRight.setOnAction(e -> {
            int idx = gallery.getChildren().indexOf(card);
            if (idx < gallery.getChildren().size() - 1) {
                moveImage(idx, idx + 1);
            }
            e.consume();
        });

        var moveButtons = new HBox(4, moveLeft, moveRight);
        moveButtons.setAlignment(Pos.CENTER);

        card.getChildren().addAll(imageStack, label, moveButtons);
        updateCardStyle(card, cb.isSelected());

        Tooltip.install(card, new Tooltip(imageInfo.title()));

        cb.selectedProperty().addListener((obs, oldVal, newVal) -> updateCardStyle(card, newVal));
        card.setOnMouseClicked(e -> {
            if (!e.isDragDetect()) {
                cb.setSelected(!cb.isSelected());
            }
        });

        card.setOnDragDetected(e -> {
            dragSourceIndex = gallery.getChildren().indexOf(card);
            var db = card.startDragAndDrop(TransferMode.MOVE);
            var content1 = new ClipboardContent();
            content1.putString(String.valueOf(dragSourceIndex));
            db.setContent(content1);
            card.setOpacity(0.5);
            e.consume();
        });

        card.setOnDragOver(e -> {
            if (e.getGestureSource() != card && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        card.setOnDragEntered(e -> {
            if (e.getGestureSource() != card && e.getDragboard().hasString()) {
                card.setStyle(card.getStyle() + "; -fx-effect: dropshadow(gaussian, #0078d4, 6, 0.3, 0, 0);");
            }
            e.consume();
        });

        card.setOnDragExited(e -> {
            int idx = gallery.getChildren().indexOf(card);
            updateCardStyle(card, idx < checkBoxes.size() && checkBoxes.get(idx).isSelected());
            e.consume();
        });

        card.setOnDragDropped(e -> {
            var db = e.getDragboard();
            if (db.hasString()) {
                int targetIndex = gallery.getChildren().indexOf(card);
                if (dragSourceIndex >= 0 && dragSourceIndex != targetIndex) {
                    moveImage(dragSourceIndex, targetIndex);
                }
                e.setDropCompleted(true);
            } else {
                e.setDropCompleted(false);
            }
            e.consume();
        });

        card.setOnDragDone(e -> {
            card.setOpacity(1.0);
            dragSourceIndex = -1;
            e.consume();
        });

        return card;
    }

    private void moveImage(int fromIndex, int toIndex) {
        var movedNode = gallery.getChildren().remove(fromIndex);
        gallery.getChildren().add(toIndex, movedNode);

        var movedImage = eligibleImages.remove(fromIndex);
        eligibleImages.add(toIndex, movedImage);

        var movedCheckBox = checkBoxes.remove(fromIndex);
        checkBoxes.add(toIndex, movedCheckBox);
    }

    private static void updateCardStyle(VBox card, boolean selected) {
        card.setStyle(selected ? CARD_SELECTED_STYLE : CARD_STYLE);
    }

    @Override
    public void load() {
        if (!eligibleImages.isEmpty()) {
            return;
        }
        BackgroundOperations.async(() -> {
            var allImages = imagesSupplier.get();
            var filtered = new ArrayList<MultipleImagesViewer.ImageInfo>();
            for (var imageInfo : allImages) {
                if (EXCLUDED_KINDS.contains(imageInfo.kind())) {
                    continue;
                }
                if (imageInfo.image().findMetadata(Ellipse.class).isEmpty()) {
                    continue;
                }
                filtered.add(imageInfo);
            }
            Platform.runLater(() -> populateGallery(filtered));
        });
    }

    private void populateGallery(List<MultipleImagesViewer.ImageInfo> images) {
        eligibleImages.clear();
        checkBoxes.clear();
        gallery.getChildren().clear();

        var idx = content.getChildren().indexOf(loadingPane);
        if (idx < 0) {
            return;
        }

        if (images.isEmpty()) {
            var noImages = new Label(message("images.none.available"));
            noImages.setStyle("-fx-text-fill: red;");
            content.getChildren().set(idx, noImages);
            return;
        }

        var hasExtractedHeD3 = images.stream().anyMatch(img -> img.image().findMetadata(HeliumImageKind.class)
                .filter(HeliumImageKind::extracted)
                .isPresent());
        var continuumPixelShift = findContinuumPixelShift(images);
        var preselected = new ArrayList<MultipleImagesViewer.ImageInfo>();
        var notPreselected = new ArrayList<MultipleImagesViewer.ImageInfo>();
        for (var imageInfo : images) {
            if (shouldPreselect(imageInfo, hasExtractedHeD3, continuumPixelShift)) {
                preselected.add(imageInfo);
            } else {
                notPreselected.add(imageInfo);
            }
        }
        for (var imageInfo : preselected) {
            eligibleImages.add(imageInfo);
            gallery.getChildren().add(createImageCard(imageInfo, true));
        }
        for (var imageInfo : notPreselected) {
            eligibleImages.add(imageInfo);
            gallery.getChildren().add(createImageCard(imageInfo, false));
        }

        var scrollPane = new ScrollPane(gallery);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        content.getChildren().set(idx, scrollPane);
    }

    private static OptionalDouble findContinuumPixelShift(List<MultipleImagesViewer.ImageInfo> images) {
        for (var imageInfo : images) {
            if (imageInfo.kind() == GeneratedImageKind.CONTINUUM) {
                var ps = imageInfo.image().findMetadata(PixelShift.class);
                if (ps.isPresent()) {
                    return OptionalDouble.of(ps.get().pixelShift());
                }
            }
        }
        return OptionalDouble.empty();
    }

    private boolean shouldPreselect(MultipleImagesViewer.ImageInfo imageInfo, boolean hasExtractedHeD3, OptionalDouble continuumPixelShift) {
        if (hasExtractedHeD3) {
            return imageInfo.image().findMetadata(HeliumImageKind.class)
                    .filter(HeliumImageKind::extracted)
                    .isPresent();
        }
        var kind = imageInfo.kind();
        boolean isHAlpha = detectedSpectralRay != null && SpectralRay.H_ALPHA.label().equals(detectedSpectralRay.label());
        var preselected = isHAlpha ? H_ALPHA_PRESELECTED : DEFAULT_PRESELECTED;
        if (preselected.contains(kind)) {
            return true;
        }
        if (isHAlpha && kind == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED && continuumPixelShift.isPresent()) {
            var ps = imageInfo.image().findMetadata(PixelShift.class);
            return ps.isPresent() && ps.get().pixelShift() == continuumPixelShift.getAsDouble();
        }
        return false;
    }

    @Override
    public void cleanup() {
    }

    @Override
    public boolean validate() {
        if (getSelectedImages().isEmpty()) {
            AlertFactory.error(message("images.none.selected")).showAndWait();
            return false;
        }
        return true;
    }

    boolean wantsPostProcessing() {
        return postProcessCheckBox != null && postProcessCheckBox.isSelected();
    }

    List<MultipleImagesViewer.ImageInfo> getSelectedImages() {
        var selected = new ArrayList<MultipleImagesViewer.ImageInfo>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected()) {
                selected.add(eligibleImages.get(i));
            }
        }
        return selected;
    }
}
