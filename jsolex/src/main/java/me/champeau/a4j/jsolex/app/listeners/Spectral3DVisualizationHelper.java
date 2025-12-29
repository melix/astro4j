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
package me.champeau.a4j.jsolex.app.listeners;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.SamplingOptionsDialog;
import me.champeau.a4j.jsolex.app.jfx.SpectralEvolution4DViewer;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineSurface3DViewer;
import me.champeau.a4j.jsolex.app.jfx.SphericalTomography3DViewer;
import me.champeau.a4j.jsolex.app.jfx.SphericalTomographyCreator;
import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineSurfaceDataExtractor;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralProfileEvolutionExtractor;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.spectrum.SphericalTomographyExtractor;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newModalStage;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.computeSerFileBasename;

/**
 * Helper class for 3D visualization of spectral data including surface plots,
 * spectral evolution cubes, and spherical tomography.
 */
final class Spectral3DVisualizationHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Spectral3DVisualizationHelper.class);

    private final DataProvider dataProvider;
    private WeakReference<SpectralEvolution4DViewer> spectral4DViewer;

    /**
     * Interface for accessing required data from the listener.
     */
    interface DataProvider {
        JSolExInterface getOwner();
        File getSerFile();
        Path getOutputDirectory();
        ProcessParams getParams();
        ProcessParams getAdjustedParams();
        ProgressOperation getRootOperation();
        Map<PixelShift, ImageWrapper> getShiftImages();
        DoubleUnaryOperator getPolynomial();
        float[][] getAverageImage();
        Ellipse getMainEllipse();
        AverageImageComputedEvent.AverageImage getCachedAverageImagePayload();
        TrimmingParameters getCachedTrimmingParameters();
        PixelShiftRange getPixelShiftRange();
        RedshiftImagesProcessor createRedshiftProcessor();
    }

    Spectral3DVisualizationHelper(DataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    /**
     * Shows a 3D surface plot of the spectral profile.
     */
    void show3DSpectralProfile() {
        var cachedAverageImagePayload = dataProvider.getCachedAverageImagePayload();
        if (cachedAverageImagePayload == null) {
            return;
        }
        BackgroundOperations.async(() -> {
            var payload = cachedAverageImagePayload;
            var params = payload.adjustedParams();
            var lambda0 = params.spectrumParams().ray().wavelength();
            var binning = params.observationDetails().binning();
            var pixelSize = params.observationDetails().pixelSize();
            if (binning == null || pixelSize == null || lambda0.nanos() <= 0 || pixelSize <= 0 || binning <= 0) {
                return;
            }
            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(
                    params.observationDetails().instrument(),
                    lambda0,
                    pixelSize * binning
            );
            var surfaceData = SpectralLineSurfaceDataExtractor.extractSurfaceData(
                    payload.image().data(),
                    payload.polynomial(),
                    payload.leftBorder(),
                    payload.rightBorder(),
                    payload.image().height(),
                    lambda0,
                    dispersion,
                    150,
                    100
            );
            Platform.runLater(() -> SpectralLineSurface3DViewer.show(
                    surfaceData,
                    I18N.string(JSolEx.class, "spectral-surface-3d", "frame.title")
            ));
        });
    }

    /**
     * Shows the spectral evolution 4D viewer.
     */
    void showSpectralEvolution() {
        var cachedTrimmingParameters = dataProvider.getCachedTrimmingParameters();
        var cachedAverageImagePayload = dataProvider.getCachedAverageImagePayload();
        if (cachedTrimmingParameters == null || cachedAverageImagePayload == null) {
            return;
        }
        var trimParams = cachedTrimmingParameters;
        var payload = cachedAverageImagePayload;
        var params = payload.adjustedParams();
        var lambda0 = params.spectrumParams().ray().wavelength();
        var binning = params.observationDetails().binning();
        var pixelSize = params.observationDetails().pixelSize();
        if (binning == null || pixelSize == null || lambda0.nanos() <= 0 || pixelSize <= 0 || binning <= 0) {
            return;
        }
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(
                params.observationDetails().instrument(),
                lambda0,
                pixelSize * binning
        );

        var dialog = new SamplingOptionsDialog();
        var optionsOpt = dialog.showAndWait(dataProvider.getOwner().getMainStage());
        if (optionsOpt.isEmpty()) {
            return;
        }
        var options = optionsOpt.get();

        var loadingStage = createLoadingStage(I18N.string(JSolEx.class, "spectral-surface-3d", "evolution.title"));
        loadingStage.show();

        var mainEllipse = dataProvider.getMainEllipse();
        BackgroundOperations.async(() -> {
            try (var reader = SerFileReader.of(trimParams.serFile())) {
                var geometry = reader.header().geometry();
                var evolutionData = SpectralProfileEvolutionExtractor.extractEvolution4D(
                        reader,
                        geometry,
                        trimParams.polynomial(),
                        0,
                        geometry.width(),
                        trimParams.minX(),
                        trimParams.maxX(),
                        trimParams.firstFrame(),
                        trimParams.lastFrame(),
                        lambda0,
                        dispersion,
                        options.spatialResolution(),
                        options.wavelengthResolution(),
                        trimParams.verticalFlip(),
                        null
                );
                Platform.runLater(() -> {
                    loadingStage.close();
                    var viewer = SpectralEvolution4DViewer.show(
                            evolutionData,
                            mainEllipse,
                            I18N.string(JSolEx.class, "spectral-surface-3d", "evolution.title")
                    );
                    spectral4DViewer = new WeakReference<>(viewer);
                });
            } catch (Exception e) {
                LOGGER.error("Failed to extract spectral evolution", e);
                Platform.runLater(loadingStage::close);
            }
        });
    }

    /**
     * Shows the spherical tomography dialog and visualization.
     */
    void showSphericalTomography() {
        var cachedAverageImagePayload = dataProvider.getCachedAverageImagePayload();
        if (cachedAverageImagePayload == null) {
            return;
        }
        var payload = cachedAverageImagePayload;
        var adjustedParams = payload.adjustedParams();
        var lambda0 = adjustedParams.spectrumParams().ray().wavelength();
        var binning = adjustedParams.observationDetails().binning();
        var pixelSize = adjustedParams.observationDetails().pixelSize();
        if (binning == null || pixelSize == null || lambda0.nanos() <= 0 || pixelSize <= 0 || binning <= 0) {
            return;
        }
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(
                adjustedParams.observationDetails().instrument(),
                lambda0,
                pixelSize * binning
        );

        var redshiftProcessor = dataProvider.createRedshiftProcessor();
        var pixelShiftRange = dataProvider.getPixelShiftRange();

        Platform.runLater(() -> {
            var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "spherical-tomography-params");
            Parent node;
            try {
                node = fxmlLoader.load();
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
            var controller = fxmlLoader.<SphericalTomographyCreator>getController();
            var stage = newModalStage(dataProvider.getOwner().getMainStage(), node);
            controller.setup(stage, pixelShiftRange, redshiftProcessor, (step, unused) -> {
                var minShift = controller.getMinShift();
                var maxShift = controller.getMaxShift();
                var stepSize = controller.getStepSize();

                generateTomographyImages(minShift, maxShift, stepSize, dispersion, lambda0);
            });
            stage.setTitle(I18N.string(JSolEx.class, "spherical-tomography-params", "frame.title"));
            stage.showAndWait();
        });
    }

    private void generateTomographyImages(double minShift,
                                          double maxShift,
                                          double stepSize,
                                          Dispersion dispersion,
                                          Wavelen lambda0) {
        var loadingStageRef = new AtomicReference<Stage>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            var loadingStage = createLoadingStage(I18N.string(JSolEx.class, "spherical-tomography", "frame.title"));
            loadingStageRef.set(loadingStage);
            loadingStage.show();
            latch.countDown();
        });

        var owner = dataProvider.getOwner();
        var params = dataProvider.getParams();
        var shiftImages = dataProvider.getShiftImages();
        var serFile = dataProvider.getSerFile();
        var outputDirectory = dataProvider.getOutputDirectory();
        var rootOperation = dataProvider.getRootOperation();
        var polynomial = dataProvider.getPolynomial();
        var averageImage = dataProvider.getAverageImage();
        var mainEllipse = dataProvider.getMainEllipse();
        var adjustedParams = dataProvider.getAdjustedParams();

        BackgroundOperations.async(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            var loadingStage = loadingStageRef.get();
            try {
                var requiredShifts = new TreeSet<Double>();
                for (var shift = minShift; shift <= maxShift; shift += stepSize) {
                    requiredShifts.add(Math.round(shift * 100.0) / 100.0);
                }
                if (minShift <= 0 && maxShift >= 0) {
                    requiredShifts.add(0.0);
                }

                var missingShifts = requiredShifts.stream()
                        .filter(d -> !shiftImages.containsKey(new PixelShift(d)))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (!missingShifts.isEmpty()) {
                    var tmpParams = params.withRequestedImages(
                            new RequestedImages(
                                    Set.of(GeneratedImageKind.GEOMETRY_CORRECTED),
                                    Stream.concat(
                                            params.requestedImages().pixelShifts().stream(),
                                            missingShifts.stream()
                                    ).toList(),
                                    missingShifts,
                                    Set.of(),
                                    ImageMathParams.NONE,
                                    false,
                                    false
                            )
                    ).withExtraParams(params.extraParams().withAutosave(false));

                    var solexVideoProcessor = new SolexVideoProcessor(
                            serFile, outputDirectory, 0, tmpParams,
                            LocalDateTime.now(), false,
                            Configuration.getInstance().getMemoryRestrictionMultiplier(),
                            rootOperation
                    );
                    solexVideoProcessor.setRedshifts(new Redshifts(List.of()));
                    solexVideoProcessor.setPolynomial(polynomial);
                    solexVideoProcessor.setAverageImage(averageImage);
                    solexVideoProcessor.addEventListener(new ProcessingEventListener() {
                        @Override
                        public void onProcessingDone(ProcessingDoneEvent e) {
                            shiftImages.putAll(e.getPayload().shiftImages());
                        }

                        @Override
                        public void onProgress(ProgressEvent e) {
                            BatchOperations.submitOneOfAKind("progress", () -> owner.updateProgress(e.getPayload()));
                        }
                    });
                    solexVideoProcessor.setIgnoreIncompleteShifts(true);
                    if (mainEllipse != null) {
                        solexVideoProcessor.setCachedEllipse(mainEllipse);
                    }
                    solexVideoProcessor.process();
                }

                var shellImages = new LinkedHashMap<PixelShift, ImageWrapper>();
                for (double shift : requiredShifts) {
                    var pixelShift = new PixelShift(shift);
                    var image = shiftImages.get(pixelShift);
                    if (image != null) {
                        shellImages.put(pixelShift, image);
                    }
                }

                var tomographyData = SphericalTomographyExtractor.extract(
                        shellImages,
                        dispersion,
                        lambda0,
                        1.0,
                        adjustedParams.contrastEnhancement(),
                        adjustedParams.claheParams(),
                        adjustedParams.autoStretchParams()
                );

                Platform.runLater(() -> {
                    loadingStage.close();
                    SphericalTomography3DViewer.show(
                            tomographyData,
                            I18N.string(JSolEx.class, "spherical-tomography", "frame.title"),
                            adjustedParams
                    );
                });
            } catch (Exception e) {
                LOGGER.error("Failed to generate spherical tomography", e);
                Platform.runLater(loadingStage::close);
            }
        });
    }

    private Stage createLoadingStage(String title) {
        var progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(-1);
        var label = new Label(I18N.string(JSolEx.class, "spectral-surface-3d", "loading"));
        label.setMinSize(Label.USE_PREF_SIZE, Label.USE_PREF_SIZE);
        var vbox = new VBox(10, progressIndicator, label);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(20));
        vbox.setMinWidth(300);
        var scene = newScene(vbox);
        var stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.sizeToScene();
        return stage;
    }

    /**
     * Gets the cached spectral 4D viewer if it still exists.
     */
    SpectralEvolution4DViewer getSpectral4DViewer() {
        return spectral4DViewer != null ? spectral4DViewer.get() : null;
    }
}
