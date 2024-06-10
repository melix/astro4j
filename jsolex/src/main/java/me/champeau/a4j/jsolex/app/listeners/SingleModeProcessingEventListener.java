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

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataSupport;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.jfx.BatchOperations.blockingUntilResultAvailable;

public class SingleModeProcessingEventListener implements ProcessingEventListener, ImageMathScriptExecutor, Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleModeProcessingEventListener.class);
    private static final String[] RGB_COLORS = {"red", "green", "blue"};
    private static final int BINS = 256;

    private final Map<SuggestionEvent.SuggestionKind, String> suggestions = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Double, ZoomableImageView> imageViews;
    private final JSolExInterface owner;
    private final String baseName;
    private final File serFile;
    private final Path outputDirectory;
    private final Map<String, ImageViewer> popupViews;
    private final AtomicInteger concurrentNotifications = new AtomicInteger();
    private final Tab profileTab;
    private final Tab statsTab;
    private final Tab metadataTab;
    private final WeakHashMap<ImageWrapper, List<CachedHistogram>> cachedHistograms = new WeakHashMap<>();
    private final LocalDateTime processingDate;

    private ProcessParams params;
    private ProcessParams adjustedParams;
    private Header header;
    private BarChart<String, Number> histogramChart;
    private Map<Class, Object> scriptExecutionContext;
    private ImageEmitter imageEmitter;
    private ImageMathScriptExecutor imageScriptExecutor;
    private long sd;
    private long ed;
    private final Map<Double, ImageWrapper> shiftImages;
    private int width;
    private int height;

    private Supplier<LineChart<?, ?>> profileGraphFactory;

    public SingleModeProcessingEventListener(JSolExInterface owner,
                                             String baseName,
                                             File serFile,
                                             Path outputDirectory,
                                             ProcessParams params,
                                             LocalDateTime processingDate,
                                             Map<String, ImageViewer> popupViews) {
        this.owner = owner;
        this.baseName = baseName;
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
        this.params = params;
        this.statsTab = owner.getStatsTab();
        this.profileTab = owner.getProfileTab();
        this.metadataTab = owner.getMetadataTab();
        this.popupViews = popupViews;
        this.shiftImages = new HashMap<>();
        this.processingDate = processingDate;
        imageViews = new HashMap<>();
        sd = 0;
        ed = 0;
        width = 0;
        height = 0;
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        LOGGER.info(message("dimensions.determined"), event.getLabel(), event.getWidth(), event.getHeight());
        width = event.getWidth();
        height = event.getHeight();
    }

    private ZoomableImageView createImageView(double pixelShift) {
        var imageViewer = blockingUntilResultAvailable(() -> owner.getImagesViewer().addImage(this,
            message("image.reconstruction"), baseName,
            GeneratedImageKind.RECONSTRUCTION, null, null, params, popupViews, new PixelShift(pixelShift), viewer -> {
            }));
        var imageView = imageViewer.getImageView();
        imageView.setImage(new WritableImage(width, height));
        imageView.resetZoom();
        return imageView;
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        var payload = event.getPayload();
        int y = payload.line();
        if (payload.display()) {
            var imageView = getOrCreateImageView(event);
            imageView.resetZoom();
            WritableImage image = (WritableImage) imageView.getImage();
            double[] line = payload.data();
            byte[] rgb = new byte[3 * line.length];
            for (int x = 0; x < line.length; x++) {
                int v = (int) Math.round(line[x]);
                byte c = (byte) (v >> 8);
                rgb[3 * x] = c;
                rgb[3 * x + SpectrumAnalyzer.DEFAULT_ORDER] = c;
                rgb[3 * x + 2] = c;
            }
            var pixelformat = PixelFormat.getByteRgbInstance();
            onProgress(ProgressEvent.of((y + 1d) / height, message("reconstructing")));
            BatchOperations.submit(() -> {
                image.getPixelWriter().setPixels(0, y, line.length, SpectrumAnalyzer.DEFAULT_ORDER, pixelformat, rgb, 0, 3 * line.length);
            });
        } else {
            onProgress(ProgressEvent.of((y + 1d) / height, message("reconstructing")));
        }
    }

    private synchronized ZoomableImageView getOrCreateImageView(PartialReconstructionEvent event) {
        return imageViews.computeIfAbsent(event.getPayload().pixelShift(), this::createImageView);
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        BatchOperations.submit(() -> {
            var payload = event.getPayload();
            var title = payload.title();
            var kind = payload.kind();
            var imageWrapper = payload.image();
            var pixelShift = imageWrapper.findMetadata(PixelShift.class);
            owner.getImagesViewer().addImage(this,
                title,
                baseName,
                kind,
                imageWrapper,
                payload.path().toFile(),
                adjustedParams != null ? adjustedParams : params,
                popupViews,
                pixelShift.orElse(null),
                viewer -> {
                    showHistogram(viewer.getStretchedImage());
                    showMetadata(imageWrapper.metadata());
                });
            var imageViewer = popupViews.get(title);
            if (imageViewer != null) {
                imageViewer.setImage(baseName, params, imageWrapper, payload.path());
            }
        });
    }

    private void showMetadata(Map<Class<?>, Object> metadata) {
        var metadataPane = new VBox();
        metadataPane.setSpacing(10);
        var metadataContent = new ScrollPane(metadataPane);
        metadataContent.setFitToWidth(true);
        metadataContent.setFitToHeight(true);
        metadataTab.setContent(metadataContent);
        var view = new TextArea();
        view.setEditable(false);
        view.setWrapText(true);
        view.setText(metadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(new MetadataComparator()))
            .map(e -> MetadataSupport.render(e.getKey(), e.getValue()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.joining("\n")));
        metadataContent.setContent(view);
    }

    private void showHistogram(ImageWrapper imageWrapper) {
        prepareHistogram();
        var histograms = cachedHistograms.computeIfAbsent(imageWrapper, unused -> {
            if (imageWrapper instanceof ImageWrapper32 mono) {
                return List.of(new CachedHistogram(Histogram.of(
                    new Image(mono.width(), mono.height(), mono.data()), BINS
                ), "grey"));
            } else if (imageWrapper instanceof ColorizedImageWrapper colorized) {
                var rgb = colorized.converter().apply(colorized.mono());
                List<CachedHistogram> result = new ArrayList<>(rgb.length);
                for (int i = 0; i < rgb.length; i++) {
                    float[] channel = rgb[i];
                    result.add(new CachedHistogram(Histogram.of(
                        new Image(colorized.width(), colorized.height(), channel), BINS
                    ), RGB_COLORS[i]));
                }
                return result;
            } else if (imageWrapper instanceof RGBImage rgbImage) {
                var rgb = new float[][]{rgbImage.r(), rgbImage.g(), rgbImage.b()};
                List<CachedHistogram> result = new ArrayList<>(rgb.length);
                for (int i = 0; i < rgb.length; i++) {
                    float[] channel = rgb[i];
                    result.add(new CachedHistogram(Histogram.of(
                        new Image(rgbImage.width(), rgbImage.height(), channel), BINS
                    ), RGB_COLORS[i]));
                }
                return result;
            }
            return List.of();
        });
        histograms.forEach(c -> addSeries(c.histogram, c.color));
    }

    private void prepareHistogram() {
        if (histogramChart == null) {
            var xAxis = new CategoryAxis();
            var yAxis = new NumberAxis();
            xAxis.setLabel(message("pixel.value"));
            yAxis.setLabel(message("pixel.count"));
            histogramChart = new BarChart<>(xAxis, yAxis);
            histogramChart.setTitle(message("image.histogram"));
            histogramChart.setBarGap(0);
            statsTab.setContent(histogramChart);
            histogramChart.setLegendVisible(false);
            registerSaveChartAction("histogram", histogramChart);
        }
        histogramChart.getData().clear();
    }

    private void addSeries(Histogram histogram, String color) {
        var series = new XYChart.Series<String, Number>();
        for (int i = 0; i < histogram.values().length; i++) {
            var d = new XYChart.Data<String, Number>(String.valueOf(i), histogram.values()[i]);
            series.getData().add(d);
        }

        // Add the series to the bar chart
        histogramChart.getData().add(series);
        for (XYChart.Data<String, Number> d : series.getData()) {
            d.getNode().setStyle("-fx-bar-fill: " + color + ";");
        }
    }

    @Override
    public void onFileGenerated(FileGeneratedEvent event) {
        var filePath = event.getPayload().path();
        if (filePath.toFile().getName().endsWith(".mp4")) {
            BatchOperations.submit(() -> owner.getImagesViewer().addVideo(event.getPayload().kind(), event.getPayload().title(), filePath));
        }
    }

    @Override
    public void onNotification(NotificationEvent e) {
        if (concurrentNotifications.incrementAndGet() > 3) {
            // If there are too many events,
            // there's probably a big problem
            // like many exceptons being thrown
            // so let's not overwhelm the user
            return;
        }
        BatchOperations.submit(() -> {
            var alert = new Alert(Alert.AlertType.valueOf(e.type().name()));
            alert.setResizable(true);
            alert.getDialogPane().setPrefSize(480, 320);
            alert.setTitle(e.title());
            alert.setHeaderText(e.header());
            alert.setContentText(e.message());
            alert.showAndWait();
            concurrentNotifications.decrementAndGet();
        });
    }

    @Override
    public void onSuggestion(SuggestionEvent e) {
        if (!suggestions.containsKey(e.kind())) {
            suggestions.put(e.kind(), e.getPayload());
        }
    }

    @Override
    public void onProcessingStart(ProcessingStartEvent e) {
        var payload = e.getPayload();
        sd = payload.timestamp();
        owner.getRedshiftTab().setDisable(true);
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        var payload = e.getPayload();
        imageEmitter = payload.customImageEmitter();
        scriptExecutionContext = prepareExecutionContext(payload);
        shiftImages.putAll(payload.shiftImages());
        imageScriptExecutor = new JSolExScriptExecutor(
            shiftImages::get,
            new HashMap<>(scriptExecutionContext),
            this,
            null
        );
        ed = payload.timestamp();
        var duration = java.time.Duration.ofNanos(ed - sd);
        double seconds = duration.toMillis() / 1000d;
        var sb = new StringBuilder();
        if (!suggestions.isEmpty()) {
            sb.append(message("suggestions") + " :\n");
            for (String suggestion : suggestions.values()) {
                sb.append("    - ").append(suggestion).append("\n");
            }
        }
        var finishedString = String.format(message("finished.in"), seconds);
        LOGGER.info(message("processing.done"));
        LOGGER.info(finishedString);
        owner.prepareForScriptExecution(this, params);
        suggestions.clear();
        BatchOperations.submit(() -> {
            owner.updateProgress(1.0, finishedString);
            System.gc();
        });
        var redshifts = payload.redshifts();
        var polynomial = payload.polynomial();
        var averageImage = payload.averageImage();
        var processParams = payload.processParams();
        owner.prepareForRedshiftImages(new RedshiftImagesProcessor(
            shiftImages,
            processParams,
            serFile,
            outputDirectory,
            owner,
            this,
            imageEmitter,
            redshifts,
            polynomial,
            averageImage
        ));
        owner.prepareForGongImageDownload(processParams);
    }

    private Map<Class, Object> prepareExecutionContext(ProcessingDoneEvent.Outcome payload) {
        Map<Class, Object> context = new HashMap<>();
        if (payload.ellipse() != null) {
            context.put(Ellipse.class, payload.ellipse());
        }
        if (payload.imageStats() != null) {
            context.put(ImageStats.class, payload.imageStats());
        }
        context.put(SolarParameters.class, SolarParametersUtils.computeSolarParams(params.observationDetails().date().toLocalDateTime()));
        context.put(ProcessParams.class, payload.processParams());
        context.put(ImageEmitter.class, payload.customImageEmitter());
        context.put(PixelShiftRange.class, payload.pixelShiftRange());
        return context;
    }

    @Override
    public void onProgress(ProgressEvent e) {
        BatchOperations.submitOneOfAKind("progress", () -> {
            if (e.getPayload().progress() == SpectrumAnalyzer.DEFAULT_ORDER) {
                owner.hideProgress();
            } else {
                owner.showProgress();
                owner.updateProgress(e.getPayload().progress(), e.getPayload().task());
            }
        });
    }

    @Override
    public void onVideoMetadataAvailable(VideoMetadataEvent event) {
        this.header = event.getPayload();
    }

    @Override
    public void onGenericMessage(GenericMessage<?> e) {
        if (e.getPayload() instanceof ApplyUserRotation params) {
            owner.applyUserRotation(params);
        }
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        // perform a first pass just to check if they are missing image shifts
        Set<Double> missingShifts = determineShiftsRequiredInScript(script);
        missingShifts.removeAll(shiftImages.keySet());
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(missingShifts);
        }
        var result = imageScriptExecutor.execute(script, SectionKind.SINGLE);
        ImageMathScriptExecutor.render(result, imageEmitter);
        var invalidExpressions = result.invalidExpressions();
        var errorCount = invalidExpressions.size();
        if (errorCount > 0) {
            String message = invalidExpressions.stream()
                .map(invalidExpression -> "Expression '" + invalidExpression.label() + "' (" + invalidExpression.expression() + ") : " + invalidExpression.error().getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
            onNotification(new NotificationEvent(new Notification(
                Notification.AlertType.ERROR,
                message("error.processing.script"),
                message("script.errors." + (errorCount == SpectrumAnalyzer.DEFAULT_ORDER ? "single" : "many")),
                message
            )));
        }
        return result;
    }

    private void restartProcessForMissingShifts(Set<Double> missingShifts) {
        LOGGER.warn(message("restarting.process.missing.shifts"), missingShifts.stream().map(d -> String.format("%.2f", d)).toList());
        // restart processing to include missing images
        var tmpParams = params.withRequestedImages(
            new RequestedImages(Set.of(GeneratedImageKind.GEOMETRY_CORRECTED),
                Stream.concat(params.requestedImages().pixelShifts().stream(), missingShifts.stream()).toList(),
                missingShifts,
                Set.of(),
                ImageMathParams.NONE,
                false)
        ).withExtraParams(params.extraParams().withAutosave(false));
        var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, tmpParams, LocalDateTime.now(), false);
        solexVideoProcessor.addEventListener(new ProcessingEventListener() {
            @Override
            public void onProcessingDone(ProcessingDoneEvent e) {
                shiftImages.putAll(e.getPayload().shiftImages());
            }

            @Override
            public void onProgress(ProgressEvent e) {
                BatchOperations.submitOneOfAKind("progress", () -> owner.updateProgress(e.getPayload().progress(), e.getPayload().task()));
            }
        });
        solexVideoProcessor.process();
    }

    private Set<Double> determineShiftsRequiredInScript(String script) {
        var collectingExecutor = new DefaultImageScriptExecutor(
            ShiftCollectingImageExpressionEvaluator.zeroImages(),
            scriptExecutionContext
        );
        var shiftCollectionResult = collectingExecutor.execute(script, SectionKind.SINGLE);
        Set<Double> allShifts = new TreeSet<>();
        allShifts.addAll(shiftCollectionResult.outputShifts());
        allShifts.addAll(shiftCollectionResult.internalShifts());
        return allShifts;
    }

    @Override
    public void broadcast(ProcessingEvent<?> event) {
        if (event instanceof OutputImageDimensionsDeterminedEvent e) {
            onOutputImageDimensionsDetermined(e);
        } else if (event instanceof PartialReconstructionEvent e) {
            onPartialReconstruction(e);
        } else if (event instanceof ImageGeneratedEvent e) {
            onImageGenerated(e);
        } else if (event instanceof FileGeneratedEvent e) {
            onFileGenerated(e);
        } else if (event instanceof NotificationEvent e) {
            onNotification(e);
        } else if (event instanceof SuggestionEvent e) {
            onSuggestion(e);
        } else if (event instanceof ProcessingStartEvent e) {
            onProcessingStart(e);
        } else if (event instanceof ProcessingDoneEvent e) {
            onProcessingDone(e);
        } else if (event instanceof ProgressEvent e) {
            onProgress(e);
        } else if (event instanceof GenericMessage<?> e) {
            onGenericMessage(e);
        } else if (event instanceof VideoMetadataEvent e) {
            onVideoMetadataAvailable(e);
        } else if (event instanceof AverageImageComputedEvent e) {
            onAverageImageComputed(e);
        }
    }

    private FileNamingStrategy createNamingStrategy() {
        return new FileNamingStrategy(
            params.extraParams().fileNamePattern(),
            params.extraParams().datetimeFormat(),
            params.extraParams().dateFormat(),
            processingDate,
            header
        );
    }

    @Override
    public void onAverageImageComputed(AverageImageComputedEvent e) {
        adjustedParams = e.getPayload().adjustedParams();
        profileGraphFactory = () -> {
            var xAxis = new CategoryAxis();
            var yAxis = new NumberAxis();
            xAxis.setLabel(message("wavelength"));
            yAxis.setLabel(message("intensity"));
            var lineChart = new LineChart<>(xAxis, yAxis);
            var series = new XYChart.Series<String, Number>();
            var image = e.getPayload().image();
            var width = image.width();
            var height = image.height();
            var start = e.getPayload().leftBorder();
            var end = e.getPayload().rightBorder();
            var data = image.data();
            var polynomial = e.getPayload().polynomial();
            var params = e.getPayload().adjustedParams();
            var lambda0 = params.spectrumParams().ray().wavelength();
            var binning = params.observationDetails().binning();
            var pixelSize = params.observationDetails().pixelSize();
            var canDrawReference = binning != null && pixelSize != null && lambda0 > 0 && pixelSize > 0 && binning > 0;
            registerSaveChartAction("profile", lineChart);
            series.setName(formatLegend(params.observationDetails().instrument(), lambda0, binning, pixelSize));
            double dispersion = canDrawReference ? SpectrumAnalyzer.computeSpectralDispersionNanosPerPixel(params.observationDetails().instrument(), lambda0, pixelSize * binning) : 0;
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (int x = start; x < end; x++) {
                var v = polynomial.applyAsDouble(x);
                min = Math.min(v, min);
                max = Math.max(v, max);
            }
            if (min == Double.MAX_VALUE) {
                min = 0;
            }
            if (max == -Double.MAX_VALUE) {
                max = height;
            }
            min = Math.max(0, min);
            max = Math.min(height, max);
            double mid = (max + min) / 2.0;
            double range = (max - min) / 2.0;
            var dataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();
            for (int y = (int) range; y < height - range; y++) {
                double cpt = 0;
                double val = 0;
                for (int x = start; x < end; x++) {
                    var v = polynomial.applyAsDouble(x);
                    var shift = v - mid;
                    int ny = (int) Math.round(y + shift);
                    if (ny >= 0 && ny < height) {
                        val += data[width * ny + x];
                        cpt++;
                    }
                }
                if (cpt > 0) {
                    var pixelShift = y - mid;
                    var wl = canDrawReference ? computeWavelength(pixelShift, lambda0, dispersion) : 0;
                    dataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, val / cpt));
                }
            }
            lineChart.getData().add(series);
            if (canDrawReference) {
                var referenceSeries = new XYChart.Series<String, Number>();
                referenceSeries.setName(message("reference.intensity"));
                lineChart.getData().add(referenceSeries);
                var referenceDataPoints = normalizeDatapoints(SpectrumAnalyzer.findReferenceDatapoints(dataPoints));
                for (var dataPoint : referenceDataPoints) {
                    addDataPointToSeries(dataPoint, referenceSeries);
                }
            }
            for (var dataPoint : normalizeDatapoints(dataPoints)) {
                addDataPointToSeries(dataPoint, series);
            }
            return lineChart;
        };
        BatchOperations.submit(() -> profileTab.setContent(profileGraphFactory.get()));
    }

    private void addDataPointToSeries(SpectrumAnalyzer.DataPoint dataPoint, XYChart.Series<String, Number> series) {
        var label = formatWavelength(dataPoint.pixelShift(), dataPoint.wavelen());
        var d = new XYChart.Data<String, Number>(label, dataPoint.intensity());
        var tooltipText = new StringBuilder();
        tooltipText.append(label).append(" ");
        tooltipText.append(String.format("(pixel shift: %.2f)", dataPoint.pixelShift())).append("\n");
        tooltipText.append(message("click.to.reprocess"));
        var tooltip = new Tooltip(tooltipText.toString());
        series.getData().add(d);
        var node = d.getNode();
        tooltip.setShowDelay(Duration.ZERO);
        tooltip.setHideDelay(Duration.ZERO);
        Tooltip.install(node, tooltip);
        node.setOnMouseClicked(event -> {
            var menu = new ContextMenu();
            var process = new MenuItem(message("reprocess"));
            menu.getItems().add(process);
            process.setOnAction(evt -> Thread.startVirtualThread(() -> {
                var newParams = params.withSpectrumParams(
                    params.spectrumParams().withPixelShift(dataPoint.pixelShift())
                ).withRequestedImages(
                    params.requestedImages().withPixelShifts(List.of(dataPoint.pixelShift()))
                );
                var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, newParams, LocalDateTime.now(), false);
                solexVideoProcessor.addEventListener(this);
                solexVideoProcessor.addEventListener(new ProcessingEventListener() {
                    @Override
                    public void onProcessingDone(ProcessingDoneEvent e) {
                        shiftImages.putAll(e.getPayload().shiftImages());
                    }

                    @Override
                    public void onProgress(ProgressEvent e) {
                        BatchOperations.submitOneOfAKind("progress", () -> owner.updateProgress(e.getPayload().progress(), e.getPayload().task()));
                    }
                });
                params = newParams;
                solexVideoProcessor.process();
            }));
            menu.show(node, event.getScreenX(), event.getScreenY());
        });
    }

    private void registerSaveChartAction(String name, XYChart<?, ?> chart) {
        var menu = new ContextMenu();
        var saveToFile = new MenuItem(message("save.to.file"));
        saveToFile.setOnAction(evt -> {
            var snapshotParameters = new SnapshotParameters();
            snapshotParameters.setTransform(Transform.scale(2, 2));
            var writable = chart.snapshot(snapshotParameters, null);
            try {
                var namingStrategy = createNamingStrategy();
                var outputFile = outputDirectory.resolve(namingStrategy.render(0, Constants.TYPE_DEBUG, name, baseName) + ".png");
                var bufferedImage = SwingFXUtils.fromFXImage(writable, null);
                Files.createDirectories(outputFile.getParent());
                ImageIO.write(bufferedImage, "png", outputFile.toFile());
                LOGGER.info(message("chart.saved"), outputFile);
                var alert = AlertFactory.info();
                alert.setTitle(message("chart.saved.title"));
                var textArea = new TextArea("Chart saved to: " + outputFile);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxHeight(Region.USE_PREF_SIZE);
                textArea.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");

                textArea.setPrefRowCount(1);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setFocusTraversable(false);

                alert.getDialogPane().setContent(textArea);
                alert.setHeaderText(null);
                alert.showAndWait();
            } catch (IOException ex) {
                throw new ProcessingException(ex);
            }
        });
        menu.getItems().add(saveToFile);
        chart.setOnContextMenuRequested(menuEvent -> menu.show(chart, menuEvent.getScreenX(), menuEvent.getScreenY()));
        var openInNewWindow = new MenuItem(message("open.in.new.window"));
        openInNewWindow.setOnAction(evt -> BatchOperations.submit(() -> {
            var newWindow = new Stage();
            newWindow.setTitle(message(message("profile")));

            var pane = new BorderPane();
            pane.setCenter(profileGraphFactory.get());

            var scene = new Scene(pane, 800, 600);
            newWindow.setScene(scene);
            newWindow.show();
        }));

        menu.getItems().add(openInNewWindow);
    }

    private static String formatWavelength(double pixelShift, double wavelength) {
        if (wavelength > 0) {
            return String.format(Locale.US, "%.2f", wavelength);
        }
        return String.format(Locale.US, "%.2f", pixelShift);
    }

    private static double computeWavelength(double pixelShift, double lambda, double dispersion) {
        return 10 * (lambda + pixelShift * dispersion);
    }

    private static String formatLegend(SpectroHeliograph instrument, Double lambda0, Integer binning, Double pixelSize) {
        if (binning != null && pixelSize != null && lambda0 != null) {
            double lambda = lambda0;
            double pixSize = pixelSize;
            if (lambda > 0 && pixSize > 0) {
                double disp = 10 * SpectrumAnalyzer.computeSpectralDispersionNanosPerPixel(instrument, lambda, pixelSize * binning);
                return String.format(message("intensity.legend"), pixelSize, binning, disp);
            }
        }
        return message("intensity");
    }

    private static List<SpectrumAnalyzer.DataPoint> normalizeDatapoints(List<SpectrumAnalyzer.DataPoint> dataPoints) {
        var maxRef = dataPoints.stream().mapToDouble(SpectrumAnalyzer.DataPoint::intensity).max().orElse(0);
        return dataPoints.stream()
            .map(dataPoint -> new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 100 * dataPoint.intensity() / maxRef))
            .toList();
    }

    private record CachedHistogram(Histogram histogram, String color) {
    }

}
