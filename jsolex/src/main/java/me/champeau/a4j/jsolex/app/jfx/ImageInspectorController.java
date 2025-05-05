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

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.math.image.ImageMath;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.findMetadataFile;
import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class ImageInspectorController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageInspectorController.class);
    private static final List<String> DEFAULT_EXTENSIONS = Stream.of(ImageFormat.PNG, ImageFormat.JPG, ImageFormat.TIF, ImageFormat.FITS)
        .map(ImageFormat::extension).toList();

    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private ListView<CandidateImageDescriptor> imageList;
    @FXML
    private ToggleButton discardButton;
    @FXML
    private ToggleButton keepButton;
    @FXML
    private ToggleButton setBestButton;
    @FXML
    private ImageView bestImageView;
    @FXML
    private ImageView currentImageView;
    @FXML
    private Pane imagePane;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private Label summaryLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;

    private Stage stage;
    private ProcessParams processParams;
    private Map<Integer, List<File>> filesByIndex;
    private Map<Integer, File> serFilesByIndex;
    private Consumer<? super ImageInspectorController> onClose;
    private Map<Integer, List<CandidateImageDescriptor>> images;
    private File outputDirectory;
    private File tempFile;
    private Path discardedDir;
    private final ImageMath imageMath = ImageMath.newInstance();
    private final IntegerProperty currentImageIndex = new SimpleIntegerProperty(0);
    private final Map<CandidateImageDescriptor, Image> imageCache = new WeakHashMap<>();
    private final Map<Integer, ImageSelection> selections = new HashMap<>();
    private final Set<File> deletedFiles = new HashSet<>();
    private final Map<File, File> movedFiles = new HashMap<>();

    public static void create(ProcessParams processParams,
                              Map<Integer, List<CandidateImageDescriptor>> images,
                              Map<Integer, List<File>> filesPerIndex,
                              Map<Integer, File> serFilesByIndex,
                              File outputDirectory,
                              Consumer<? super ImageInspectorController> onClose) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "image-selector");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (ImageInspectorController) fxmlLoader.getController();
            var stage = new Stage();
            controller.setup(stage, processParams, images, filesPerIndex, serFilesByIndex, outputDirectory, onClose);
            var scene = new Scene(node);
            stage.setTitle(I18N.string(JSolEx.class, "image-selector", "frame.title"));
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setMaximized(true);
            stage.setAlwaysOnTop(true);
            stage.showAndWait();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private void setup(Stage stage,
                       ProcessParams processParams,
                       Map<Integer, List<CandidateImageDescriptor>> images,
                       Map<Integer, List<File>> filesPerIndex,
                       Map<Integer, File> serFilesByIndex,
                       File outputDirectory,
                       Consumer<? super ImageInspectorController> onClose) {
        this.stage = stage;
        this.images = images;
        this.processParams = processParams;
        this.filesByIndex = filesPerIndex;
        this.onClose = onClose;
        this.tempFile = createTmpFile();
        this.outputDirectory = outputDirectory;
        this.serFilesByIndex = serFilesByIndex;
        this.discardedDir = outputDirectory.toPath().resolve("discarded");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.prefWidthProperty().bind(stage.widthProperty());
        scrollPane.prefHeightProperty().bind(stage.heightProperty());

        imagePane.prefWidthProperty().bind(scrollPane.widthProperty());
        imagePane.prefHeightProperty().bind(scrollPane.heightProperty());

        // Assuming scrollPane is your container for the image views:
        bestImageView.fitWidthProperty().bind(scrollPane.widthProperty().divide(2).subtract(5));
        bestImageView.fitHeightProperty().bind(scrollPane.heightProperty());
        bestImageView.setPreserveRatio(true);

        currentImageView.fitWidthProperty().bind(scrollPane.widthProperty().divide(2).subtract(5));
        currentImageView.fitHeightProperty().bind(scrollPane.heightProperty());
        currentImageView.setPreserveRatio(true);

        imageList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(CandidateImageDescriptor item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.title());
                }
            }
        });
        imageList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
            Platform.runLater(() -> {
                displaySelectedImage();
                updateBestImageView();
            })
        );
        previousButton.disableProperty().bind(currentImageIndex.lessThanOrEqualTo(0));
        nextButton.disableProperty().bind(currentImageIndex.greaterThanOrEqualTo(images.size() - 1));
        previousButton.setOnAction(e -> currentImageIndex.set(currentImageIndex.get() - 1));
        nextButton.setOnAction(e -> currentImageIndex.set(currentImageIndex.get() + 1));
        currentImageIndex.addListener((obs, old, current) -> {
            updateImage();
            updateSummary();
        });
        discardButton.setOnAction(e -> {
            var selection = selections.get(currentImageIndex.get());
            if (selection != null) {
                selection.setState(SelectionState.DISCARD);
                keepButton.setSelected(false);
                setBestButton.setSelected(false);
                updateSummary();
            }
        });
        discardButton.setGraphic(new FontIcon("fltfal-delete-forever-24:24:CRIMSON"));
        keepButton.setOnAction(e -> {
            var selection = selections.get(currentImageIndex.get());
            if (selection != null) {
                selection.setState(SelectionState.KEEP);
                discardButton.setSelected(false);
                setBestButton.setSelected(false);
                updateSummary();
            }
        });
        keepButton.setGraphic(new FontIcon("fltfal-like-16:24:DARKGREEN"));
        setBestButton.setOnAction(e -> {
            var selection = selections.get(currentImageIndex.get());
            if (selection != null) {
                for (var value : selections.values()) {
                    if (value.getState() == SelectionState.BEST) {
                        value.setState(SelectionState.KEEP);
                    }
                }
                selection.setState(SelectionState.BEST);
                discardButton.setSelected(false);
                keepButton.setSelected(false);
                updateSummary();
                updateBestImageView();
            }
        });
        setBestButton.setGraphic(new FontIcon("fltfmz-star-add-24:24:DARKKHAKI"));
        var loader = new Loader(Map.of(), Broadcaster.NO_OP);
        var bestIndex = new AtomicInteger(0);
        var bestSharpness = new AtomicReference<Double>();
        // create selection map
        images.forEach((index, generatedImages) -> {
            var imageSelection = new ImageSelection(index, generatedImages);
            findMainImage(generatedImages).ifPresent(c -> {
                var path = findImageFile(c);
                if (path != null) {
                    var loaded = (ImageWrapper) loader.load(Map.of("file", path.toAbsolutePath().toString()));
                    if (loaded instanceof RGBImage rgb) {
                        loaded = rgb.toMono();
                    }
                    if (loaded instanceof ImageWrapper32 mono) {
                        var sharpness = imageMath.estimateSharpness(mono.asImage());
                        if (bestSharpness.get() == null || sharpness > bestSharpness.get()) {
                            bestSharpness.set(sharpness);
                            bestIndex.set(index);
                        }
                    }
                }
            });
            imageSelection.setState(SelectionState.KEEP);
            selections.put(index, imageSelection);
        });
        var imageSelection = selections.get(bestIndex.get());
        if (imageSelection != null) {
            imageSelection.setState(SelectionState.BEST);
        }
        stage.setOnCloseRequest(e -> {
            try {
                tempFile.delete();
            } finally {
                finish();
            }
        });
        updateImage();
        updateSummary();
    }

    private void updateSummary() {
        int total = selections.size();
        int current = currentImageIndex.get() + 1; // assuming index starts at 0
        int discarded = (int) discarded().count();
        int kept = (int) kept().count();

        summaryLabel.setText(String.format(I18N.string(JSolEx.class, "image-selector", "summary"),
            total, kept, discarded, serFilesByIndex.get(currentImageIndex.get()).getName()));

        progressLabel.setText(String.format(I18N.string(JSolEx.class, "image-selector", "progress"), current, total));
        progressBar.setProgress((double) current / total);
    }


    private Stream<ImageSelection> kept() {
        return selections.values().stream().filter(s -> s.getState() == SelectionState.KEEP || s.getState() == SelectionState.BEST);
    }

    private Stream<ImageSelection> discarded() {
        return selections.values().stream().filter(s -> s.getState() == SelectionState.DISCARD);
    }


    private File createTmpFile() {
        File tmpImage;
        try {
            tmpImage = TemporaryFolder.newTempFile("selector", "jsolex.png").toFile();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        return tmpImage;
    }

    private void updateImage() {
        if (images.isEmpty()) {
            bestImageView.setImage(null);
            currentImageView.setImage(null);
            return;
        }

        var currentImages = images.get(currentImageIndex.get());
        var newItemToSelect = findCurrentSelectionInNewCandidateList(imageList.getSelectionModel().getSelectedItem(), currentImages);
        imageList.getItems().setAll(currentImages);
        newItemToSelect.ifPresentOrElse(newItem -> {
            imageList.getSelectionModel().select(newItem);
            var currentImg = getFromCacheOrCreateImage(newItem);
            currentImageView.setImage(currentImg);
        }, () -> {
            var selectedOpt = findMainImage(currentImages);
            if (selectedOpt.isPresent()) {
                var selectedCandidate = selectedOpt.get();
                imageList.getSelectionModel().select(selectedCandidate);
                var currentImg = getFromCacheOrCreateImage(selectedCandidate);
                currentImageView.setImage(currentImg);
            } else {
                currentImageView.setImage(null);
            }
        });
        var state = selections.get(currentImageIndex.get()).getState();
        discardButton.setSelected(state == SelectionState.DISCARD);
        keepButton.setSelected(state == SelectionState.KEEP);
        setBestButton.setSelected(state == SelectionState.BEST);
        var bestIndexOpt = getBestImage();
        if (bestIndexOpt.isPresent()) {
            var bestIndex = bestIndexOpt.get();
            var bestImages = images.get(bestIndex);
            var bestCandidateOpt = findMainImage(bestImages);
            if (bestCandidateOpt.isPresent()) {
                var bestImg = getFromCacheOrCreateImage(bestCandidateOpt.get());
                bestImageView.setImage(bestImg);
            } else {
                bestImageView.setImage(null);
            }
        } else {
            bestImageView.setImage(null);
        }
    }

    private Optional<CandidateImageDescriptor> findMainImage(List<CandidateImageDescriptor> currentImages) {
        var selected = currentImages.stream()
            .filter(gi -> gi.kind() == GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED)
            .findFirst();
        if (selected.isEmpty()) {
            selected = currentImages.stream()
                .filter(gi -> gi.kind() == GeneratedImageKind.GEOMETRY_CORRECTED)
                .findFirst();
        }
        if (selected.isEmpty()) {
            selected = currentImages.stream()
                .filter(gi -> {
                    var ps = gi.pixelShift();
                    return ps == processParams.spectrumParams().pixelShift();
                })
                .findFirst();
        }
        if (selected.isEmpty()) {
            selected = currentImages.stream().findFirst();
        }
        return selected;
    }

    private void displaySelectedImage() {
        var selectedItem = imageList.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            currentImageView.setImage(null);
            return;
        }
        var image = getFromCacheOrCreateImage(selectedItem);
        currentImageView.setImage(image);
    }

    private void updateBestImageView() {
        Optional<Integer> bestIndexOpt = getBestImage();
        if (bestIndexOpt.isEmpty()) {
            bestImageView.setImage(null);
            return;
        }
        int bestIndex = bestIndexOpt.get();
        List<CandidateImageDescriptor> bestImages = images.get(bestIndex);
        if (bestImages.isEmpty()) {
            bestImageView.setImage(null);
            return;
        }
        // Get the current reference candidate from the ListView
        CandidateImageDescriptor referenceCandidate = imageList.getSelectionModel().getSelectedItem();
        var candidateOpt = findCurrentSelectionInNewCandidateList(referenceCandidate, bestImages);
        // Fallback to the default "main" candidate if no match was found.
        if (candidateOpt.isEmpty()) {
            candidateOpt = findMainImage(bestImages);
        }
        if (candidateOpt.isPresent()) {
            CandidateImageDescriptor bestCandidate = candidateOpt.get();
            Image bestImg = getFromCacheOrCreateImage(bestCandidate);
            bestImageView.setImage(bestImg);
        } else {
            bestImageView.setImage(null);
        }
    }

    private static Optional<CandidateImageDescriptor> findCurrentSelectionInNewCandidateList(CandidateImageDescriptor referenceCandidate,
                                                                                             List<CandidateImageDescriptor> bestImages) {
        Optional<CandidateImageDescriptor> candidateOpt = Optional.empty();
        if (referenceCandidate != null) {
            candidateOpt = bestImages.stream()
                .filter(gi -> gi.title().equals(referenceCandidate.title()))
                .filter(gi -> gi.pixelShift() == referenceCandidate.pixelShift())
                .filter(gi -> gi.kind() == referenceCandidate.kind())
                .findFirst();
        }
        return candidateOpt;
    }

    private Path findImageFile(CandidateImageDescriptor candidate) {
        for (var ext : DEFAULT_EXTENSIONS) {
            var path = candidate.path().resolveSibling(candidate.path().getFileName().toString() + ext);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private Image getFromCacheOrCreateImage(CandidateImageDescriptor selectedItem) {
        var cached = imageCache.get(selectedItem);
        if (cached != null) {
            return cached;
        }
        var path = findImageFile(selectedItem);
        if (path != null) {
            var supportedByJavaFX = path.toString().toLowerCase().endsWith(".png") || path.toString().endsWith(".jpg");
            if (supportedByJavaFX) {
                var image = new Image(path.toFile().toURI().toString());
                imageCache.put(selectedItem, image);
                return image;
            }
        }
        if (path != null) {
            var image = WritableImageSupport.asWritable(Loader.loadImage(path.toFile()));
            imageCache.put(selectedItem, image);
            return image;
        }
        return null;
    }

    @FXML
    public void finish() {
        ConfirmController.open(images.size(), (int) kept().count()).ifPresent(options -> {
            discarded().forEach(selection -> {
                var files = filesByIndex.get(selection.index);
                if (files != null) {
                    if (options.filesAction == DiscardAction.DELETE) {
                        deleteFiles(files);
                    } else if (options.filesAction == DiscardAction.MOVE) {
                        for (var file : files) {
                            try {
                                var relativePath = outputDirectory.toPath().relativize(file.toPath());
                                var destination = discardedDir.resolve(relativePath);
                                createDirectoriesIfNeeded(destination.getParent());
                                Files.move(file.toPath(), destination);
                                deleteParentDirectories(file);
                                movedFiles.put(file, destination.toFile());
                            } catch (IOException e) {
                                LOGGER.warn("Could not move file {}", file, e);
                            }
                        }
                    }
                }
            });
            discarded().forEach(selection -> {
                var serFile = serFilesByIndex.get(selection.index);
                try {
                    findMetadataFile(serFile.toPath()).ifPresent(metadataFile -> {
                        if (options.serAction == DiscardAction.DELETE) {
                            try {
                                Files.delete(metadataFile.toPath());
                                deletedFiles.add(metadataFile);
                            } catch (IOException e) {
                                LOGGER.warn("Could not delete file {}", metadataFile, e);
                            }
                        } else if (options.serAction == DiscardAction.MOVE) {
                            try {
                                var destination = discardedDir.resolve(metadataFile.getName());
                                createDirectoriesIfNeeded(destination.getParent());
                                Files.move(metadataFile.toPath(), destination);
                                movedFiles.put(metadataFile, destination.toFile());
                            } catch (IOException e) {
                                LOGGER.warn("Could not move file {}", metadataFile, e);
                            }
                        }
                    });
                    if (options.serAction == DiscardAction.DELETE) {
                        Files.delete(serFile.toPath());
                        deletedFiles.add(serFile);
                    } else if (options.serAction == DiscardAction.MOVE) {
                        var destination = discardedDir.resolve(serFile.getName());
                        createDirectoriesIfNeeded(destination.getParent());
                        Files.move(serFile.toPath(), destination);
                        movedFiles.put(serFile, destination.toFile());
                    }
                } catch (IOException e) {
                    LOGGER.warn("Could not delete file {}", serFile, e);
                }
            });
            stage.close();
            onClose.accept(this);
        });
    }

    public Set<File> getDeletedFiles() {
        return Collections.unmodifiableSet(deletedFiles);
    }

    public Map<File, File> getMovedFiles() {
        return Collections.unmodifiableMap(movedFiles);
    }

    private void deleteFiles(List<File> files) {
        for (var file : files) {
            var path = file.toPath();
            try {
                if (Files.exists(path)) {
                    Files.delete(path);
                    deletedFiles.add(file);
                }
                deleteParentDirectories(file);
            } catch (IOException ex) {
                LOGGER.warn("Could not delete file {}", file, ex);
            }
        }
    }

    private void deleteParentDirectories(File file) throws IOException {
        var parent = file.getParentFile();
        while (parent != null && !parent.equals(outputDirectory)) {
            var children = parent.listFiles();
            if (children != null && children.length == 0) {
                Files.delete(parent.toPath());
                parent = parent.getParentFile();
            } else {
                break;
            }
        }
    }

    public List<Integer> getDiscardedImages() {
        return discarded().map(s -> s.index).toList();
    }

    public Optional<Integer> getBestImage() {
        return selections.values().stream()
            .filter(s -> s.getState() == SelectionState.BEST)
            .map(s -> s.index)
            .findFirst();
    }

    static class ImageSelection {
        private final Integer index;
        private final List<CandidateImageDescriptor> images;
        private SelectionState state;

        ImageSelection(Integer index, List<CandidateImageDescriptor> images) {
            this.index = index;
            this.images = images;
        }

        public List<CandidateImageDescriptor> getImages() {
            return images;
        }

        SelectionState getState() {
            return state;
        }

        void setState(SelectionState state) {
            this.state = state;
        }
    }

    enum SelectionState {
        DISCARD, KEEP, BEST
    }

    public static class ConfirmController {
        private Stage stage;

        @FXML
        private Label confirmMessage;

        @FXML
        private ChoiceBox<DiscardAction> deleteFiles;

        @FXML
        private ChoiceBox<DiscardAction> deleteSer;

        private ConfirmOptions options;

        @FXML
        public void cancel() {
            options = null;
            stage.close();
        }

        @FXML
        public void confirm() {
            options = new ConfirmOptions(deleteFiles.getValue(), deleteSer.getValue());
            stage.close();
        }

        public static Optional<ConfirmOptions> open(int total, int kept) {
            var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "confirm-image-selection");
            try {
                var node = (Parent) fxmlLoader.load();
                var controller = (ConfirmController) fxmlLoader.getController();
                controller.confirmMessage.setText(String.format(I18N.string(JSolEx.class, "confirm-image-selection", "message"), kept, total));
                var stage = new Stage();
                controller.stage = stage;
                controller.deleteFiles.setDisable(kept == total);
                controller.deleteSer.setDisable(kept == total);
                controller.deleteFiles.getItems().setAll(DiscardAction.values());
                controller.deleteSer.getItems().setAll(DiscardAction.values());
                controller.deleteSer.setValue(DiscardAction.MOVE);
                controller.deleteFiles.setValue(DiscardAction.MOVE);
                var scene = new Scene(node);
                stage.setTitle(I18N.string(JSolEx.class, "confirm-image-selection", "frame.title"));
                stage.setScene(scene);
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setAlwaysOnTop(true);
                stage.showAndWait();
                return Optional.ofNullable(controller.options);
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        }
    }

    public record ConfirmOptions(DiscardAction filesAction, DiscardAction serAction) {
    }

    public enum DiscardAction {
        KEEP,
        DELETE,
        MOVE;

        public String toString() {
            return I18N.string(JSolEx.class, "confirm-image-selection", name().toLowerCase());
        }
    }
}
