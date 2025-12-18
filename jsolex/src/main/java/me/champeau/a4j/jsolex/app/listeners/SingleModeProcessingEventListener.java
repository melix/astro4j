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
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Transform;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.CustomAnimationCreator;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.OpenGLAvailability;
import me.champeau.a4j.jsolex.app.jfx.ReconstructionView;
import me.champeau.a4j.jsolex.app.jfx.RectangleSelectionListener;
import me.champeau.a4j.jsolex.app.jfx.SamplingOptionsDialog;
import me.champeau.a4j.jsolex.app.jfx.ScriptErrorDialog;
import me.champeau.a4j.jsolex.app.jfx.SpectralEvolution4DViewer;
import me.champeau.a4j.jsolex.app.jfx.SpectralLineSurface3DViewer;
import me.champeau.a4j.jsolex.app.jfx.SphericalTomography3DViewer;
import me.champeau.a4j.jsolex.app.jfx.SphericalTomographyCreator;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.EllipseFittingRequestEvent;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
import me.champeau.a4j.jsolex.processing.event.GeometryDetectedEvent;
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
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.event.ReconstructionDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.SpectralLineDetectedEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.event.TrimmingParametersDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.FileOutputResult;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.impl.Animate;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.OutputMetadata;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineAnalysis;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineSurfaceDataExtractor;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralProfileEvolutionExtractor;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.spectrum.SphericalTomographyExtractor;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;
import me.champeau.a4j.jsolex.processing.util.MetadataSupport;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.JSolEx.newScene;
import static me.champeau.a4j.jsolex.app.jfx.BatchOperations.blockingUntilResultAvailable;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newModalStage;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.computeSerFileBasename;
import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

/**
 * Event listener for single mode processing that handles processing events,
 * image generation, and user interface updates during Sol'Ex video file processing.
 * This listener manages the visualization of processed images, histograms, spectral profiles,
 * and provides script execution capabilities through the ImageMath language.
 */
public class SingleModeProcessingEventListener implements ProcessingEventListener, ImageMathScriptExecutor, Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleModeProcessingEventListener.class);
    private static final String[] RGB_COLORS = {"red", "green", "blue"};
    private static final int BINS = 256;

    private final Map<SuggestionEvent.SuggestionKind, String> suggestions = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Double, ReconstructionView> imageViews;
    private final JSolExInterface owner;
    private final ProgressOperation rootOperation;
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
    private float[][] averageImage;

    private ProcessParams params;
    private ProcessParams adjustedParams;
    private Header header;
    private PixelShiftRange pixelShiftRange;
    private DoubleUnaryOperator polynomial;
    private Map<Class, Object> scriptExecutionContext;
    private ImageEmitter imageEmitter;
    private ImageMathScriptExecutor imageScriptExecutor;
    private final Map<String, Object> pendingVariables = new HashMap<>();
    private long sd;
    private long ed;
    private final Map<PixelShift, ImageWrapper> shiftImages;
    private int width;
    private int height;
    private Ellipse mainEllipse;
    private ProgressOperation reconstructionProgress;

    private final AtomicInteger cropCount = new AtomicInteger();
    private final AtomicInteger animCount = new AtomicInteger();
    private final Map<String, List<ImageWrapper>> scriptImagesByLabel = new HashMap<>();

    private static final long MIN_UI_UPDATE_INTERVAL_MS = 50;
    private final AtomicLong lastUIUpdateTime = new AtomicLong(0);

    private Supplier<Parent> profileViewFactory;
    private SpectralLineAnalysis.LineStatistics currentLineStatistics;
    private Integer currentColumn;
    private float[][] currentSpectrumFrameData;
    private AverageImageComputedEvent.AverageImage cachedAverageImagePayload;
    private TrimmingParameters cachedTrimmingParameters;
    private Button show3DButton;
    private Button showEvolutionButton;
    private Button showTomographyButton;
    private WeakReference<SpectralEvolution4DViewer> spectral4DViewer;

    /**
     * Creates a new single mode processing event listener.
     *
     * @param owner           the JSol'Ex interface that owns this listener
     * @param rootOperation   the root progress operation for tracking overall processing progress
     * @param baseName        the base name for generated files
     * @param serFile         the SER video file being processed, or null if processing other formats
     * @param outputDirectory the directory where processed images will be saved
     * @param params          the processing parameters
     * @param processingDate  the date and time when processing started
     * @param popupViews      map of image viewer popup windows
     */
    public SingleModeProcessingEventListener(JSolExInterface owner,
                                             ProgressOperation rootOperation,
                                             String baseName,
                                             File serFile,
                                             Path outputDirectory,
                                             ProcessParams params,
                                             LocalDateTime processingDate,
                                             Map<String, ImageViewer> popupViews) {
        this.owner = owner;
        this.rootOperation = rootOperation;
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

    private ReconstructionView createImageView(double pixelShift) {
        var buffer = new byte[3 * width * height];
        var reconstructionView = blockingUntilResultAvailable(() -> owner.getImagesViewer().addImage(this,
                rootOperation,
                message("image.reconstruction"), baseName,
                GeneratedImageKind.RECONSTRUCTION, null, null, null, params, popupViews, new PixelShift(pixelShift),
                viewer -> {
                    var parentWidth = owner.getImagesViewer().widthProperty();
                    viewer.getImageView().getScrollPane().maxWidthProperty().bind(parentWidth);
                    return new ReconstructionView(viewer.getImageView(), buffer);
                },
                viewer -> {

                }));
        var imageView = reconstructionView.getSolarView();
        var image = new WritableImage(width, height);
        imageView.setImage(image);
        imageView.resetZoom();
        return reconstructionView;
    }

    public void onPartialReconstruction(PartialReconstructionEvent event) {
        var payload = event.getPayload();
        int y = payload.line();
        if (reconstructionProgress == null) {
            reconstructionProgress = rootOperation.createChild(message("reconstructing"));
        }
        onProgress(ProgressEvent.of(reconstructionProgress.update((y + 1d) / height, message("reconstructing"))));

        if (!payload.display()) {
            return;
        }

        var reconstructionView = getOrCreateImageView(event);
        var imageView = reconstructionView.getSolarView();
        imageView.resetZoom();
        WritableImage image = (WritableImage) imageView.getImage();
        double[] line = payload.data();
        byte[] rgb = reconstructionView.getSolarImageData();

        // Update the current row in the rgb buffer.
        for (int x = 0; x < line.length; x++) {
            int v = (int) Math.round(line[x]);
            byte c = (byte) (v >> 8);
            int offset = 3 * (y * width + x);
            rgb[offset] = c;
            rgb[offset + 1] = c;
            rgb[offset + 2] = c;
        }

        // Ensure the spectrum image is ready.
        var spectrum = payload.spectrum();
        var pixelformat = PixelFormat.getByteRgbInstance();
        var spectrumView = reconstructionView.getSpectrumView();
        if (spectrumView.getImage() == null) {
            spectrumView.setImage(new WritableImage(spectrum.width(), spectrum.height()));
        }
        WritableImage spectrumImage = (WritableImage) spectrumView.getImage();

        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastUIUpdateTime.get();

        if (currentTime - lastUpdate >= MIN_UI_UPDATE_INTERVAL_MS) {
            if (lastUIUpdateTime.compareAndSet(lastUpdate, currentTime)) {
                var lock = reconstructionView.getLock();
                if (lock.tryAcquire()) {
                    Thread.startVirtualThread(() -> {
                        var spectrumBuffer = convertSpectrumImage(spectrum);
                        Platform.runLater(() -> {
                            try {
                                spectrumImage.getPixelWriter().setPixels(
                                        0, 0,
                                        spectrum.width(), spectrum.height(),
                                        pixelformat,
                                        spectrumBuffer,
                                        0,
                                        3 * spectrum.width()
                                );
                                image.getPixelWriter().setPixels(
                                        0, 0,
                                        width, y + 1,
                                        pixelformat,
                                        rgb,
                                        0,
                                        3 * width
                                );
                            } finally {
                                lock.release();
                            }
                        });
                    });
                }
            }
        }
    }

    private void forceFinalUIUpdate(ReconstructionView reconstructionView) {
        var lock = reconstructionView.getLock();
        if (lock.tryAcquire()) {
            Thread.startVirtualThread(() -> {
                Platform.runLater(() -> {
                    try {
                        var solarView = reconstructionView.getSolarView();
                        var solarImage = (WritableImage) solarView.getImage();
                        var spectrumView = reconstructionView.getSpectrumView();
                        var spectrumImage = (WritableImage) spectrumView.getImage();

                        if (solarImage != null && spectrumImage != null) {
                            var rgb = reconstructionView.getSolarImageData();
                            var pixelformat = PixelFormat.getByteRgbInstance();
                            int imageWidth = (int) solarImage.getWidth();
                            int imageHeight = (int) solarImage.getHeight();
                            int expectedSize = 3 * imageWidth * imageHeight;
                            if (rgb.length == expectedSize) {
                                solarImage.getPixelWriter().setPixels(
                                        0, 0,
                                        imageWidth, imageHeight,
                                        pixelformat,
                                        rgb,
                                        0,
                                        3 * imageWidth
                                );
                            }
                        }
                    } finally {
                        lock.release();
                    }
                });
            });
        }
    }

    @Override
    public void onReconstructionDone(ReconstructionDoneEvent e) {
        var serFileReader = e.getPayload().reader();
        var frameCount = serFileReader.header().frameCount();
        var converter = ImageUtils.createImageConverter(params.videoParams().colorMode(), params.geometryParams().isSpectrumVFlip());
        var pixelformat = PixelFormat.getByteRgbInstance();
        reconstructionProgress = null;

        // Force final UI updates for all reconstruction views to ensure complete display
        imageViews.entrySet().forEach(entry -> {
            var view = entry.getValue();
            forceFinalUIUpdate(view);
        });

        imageViews.entrySet().forEach(entry -> {
            var pixelShift = entry.getKey();
            var view = entry.getValue();
            var solarViewOverlay = view.getSolarViewOverlay();
            var solarView = view.getSolarView();
            stretchReconstructionView(solarView);
            solarView.setOnMouseClicked(evt -> {
                var gso = solarViewOverlay.getGraphicsContext2D();
                var spectrumViewOverlay = view.getSpectrumViewOverlay();
                var gsp = spectrumViewOverlay.getGraphicsContext2D();
                gso.clearRect(0, 0, solarViewOverlay.getWidth(), solarViewOverlay.getHeight());
                gsp.clearRect(0, 0, spectrumViewOverlay.getWidth(), spectrumViewOverlay.getHeight());
                var zoom = solarView.getZoom();
                var yOffset = view.getOffsetY();
                var xOffset = view.getOffsetX();
                var frameNb = (evt.getY() + yOffset) / zoom;
                if (frameNb < 0 || frameNb >= frameCount) {
                    return;
                }
                var xIndex = (int) Math.round((evt.getX() + xOffset) / zoom);
                gso.setFill(Color.RED);
                gso.fillRect(0, evt.getY(), solarViewOverlay.getWidth(), 1);
                gso.fillRect(evt.getX(), 0, 1, solarViewOverlay.getHeight());
                gso.fillText(xIndex + "," + (int) Math.round(frameNb), evt.getX() + 20, evt.getY() + 20);
                gso.setFill(Color.GREEN);
                gso.setFont(Font.font(gso.getFont().getFamily(), FontWeight.BOLD, 24));
                gso.fillText(String.format(Locale.US, "Frame %.0f", frameNb), 10, 30);
                if (polynomial != null) {
                    var x = (evt.getX() + xOffset) / zoom;
                    // draw a cross on the spectrum view
                    var spectrumY = polynomial.applyAsDouble(x) * zoom - pixelShift;
                    gsp.setFill(Color.RED);
                    var cw = Math.max(4, 4 * zoom);
                    gsp.fillRect(evt.getX() - cw, spectrumY, 2 * cw, 1);
                    gsp.fillRect(evt.getX(), spectrumY - cw, 1, 2 * cw);
                }
                try (var reader = serFileReader.reopen()) {
                    reader.seekFrame((int) frameNb);
                    var currentFrame = reader.currentFrame().data();
                    var geometry = reader.header().geometry();
                    var buffer = converter.createBuffer(geometry);
                    converter.convert((int) frameNb, currentFrame, geometry, buffer);
                    var spectrum = new Image(geometry.width(), geometry.height(), buffer);
                    var imageBytes = convertSpectrumImage(spectrum);
                    view.setSpectrumImage(spectrum);
                    Platform.runLater(() -> {
                        var pixelWriter = ((WritableImage) view.getSpectrumView().getImage()).getPixelWriter();
                        pixelWriter.setPixels(0, 0, geometry.width(), geometry.height(), pixelformat, imageBytes, 0, 3 * geometry.width());
                    });
                    if (polynomial != null) {

                        // Store the clicked column and update the profile tab
                        currentColumn = xIndex;
                        currentSpectrumFrameData = spectrum.data();
                        if (profileViewFactory != null) {
                            Platform.runLater(() -> profileTab.setContent(profileViewFactory.get()));
                        }
                    }
                } catch (Exception ex) {
                    throw new ProcessingException(ex);
                }
                // Synchronize with 4D viewer if open
                LOGGER.info("Checking 4D viewer sync: spectral4DViewer={}", spectral4DViewer);
                if (spectral4DViewer != null) {
                    var viewer = spectral4DViewer.get();
                    LOGGER.info("4D viewer from WeakReference: {}", viewer);
                    if (viewer != null) {
                        int clickedFrame = (int) Math.round(frameNb);
                        int clickedSlit = xIndex;
                        double clickedPixelShift = pixelShift;
                        LOGGER.info("Calling setPositionFromClick({}, {}, {})", clickedFrame, clickedSlit, clickedPixelShift);
                        Platform.runLater(() -> {
                            viewer.setPositionFromClick(clickedFrame, clickedSlit, clickedPixelShift);
                            viewer.bringToFront();
                        });
                    }
                }
            });
        });
    }

    private void stretchReconstructionView(ZoomableImageView solarView) {
        BackgroundOperations.async(() -> {
            Platform.runLater(() -> {
                WritableImage image = (WritableImage) solarView.getImage();
                var pixelReader = image.getPixelReader();
                try {
                    // iterate over all pixels to find max value
                    double max = 0;
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            var v = pixelReader.getArgb(x, y) & 0xFF;
                            if (v > max) {
                                max = v;
                            }
                        }
                    }

                    // stretch colors
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            var v = pixelReader.getArgb(x, y) & 0xFF;
                            v = (int) (255 * v / max);
                            image.getPixelWriter().setArgb(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                }
            });
        });
    }

    private static byte[] convertSpectrumImage(Image spectrum) {
        int width = spectrum.width();
        int height = spectrum.height();
        var spectrumBuffer = new byte[3 * width * height];

        // Single pass: find max and store normalized values
        int max = 0;
        double[] normalizedValues = new double[width * height];
        float[][] data = spectrum.data();

        for (int yy = 0; yy < height; yy++) {
            float[] row = data[yy];
            for (int xx = 0; xx < width; xx++) {
                double v = 255.0 * row[xx] / Constants.MAX_PIXEL_VALUE;
                normalizedValues[yy * width + xx] = v;
                int intV = (int) v;
                if (intV > max) {
                    max = intV;
                }
            }
        }

        // Second pass: apply stretching and convert to RGB bytes
        double maxInverse = max > 0 ? 255.0 / max : 0.0;
        for (int i = 0; i < normalizedValues.length; i++) {
            byte s = (byte) (normalizedValues[i] * maxInverse);
            int offset = 3 * i;
            spectrumBuffer[offset] = s;
            spectrumBuffer[offset + 1] = s;
            spectrumBuffer[offset + 2] = s;
        }

        return spectrumBuffer;
    }

    private synchronized ReconstructionView getOrCreateImageView(PartialReconstructionEvent event) {
        return imageViews.computeIfAbsent(event.getPayload().pixelShift(), this::createImageView);
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var title = payload.displayTitle() != null ? payload.displayTitle() : payload.title();
        var generatedImageKind = payload.kind();
        var imageWrapper = payload.image();
        var pixelShift = imageWrapper.findMetadata(PixelShift.class);
        Platform.runLater(() -> {
            var addedImageViewer = owner.getImagesViewer().addImage(this,
                    rootOperation,
                    title,
                    baseName,
                    generatedImageKind,
                    payload.description(),
                    imageWrapper,
                    payload.path().toAbsolutePath().toFile(),
                    adjustedParams != null ? adjustedParams : params,
                    popupViews,
                    pixelShift.orElse(null),
                    viewer -> viewer,
                    viewer -> viewer.setOnStretchedImageUpdate(stretchedImage -> {
                        BackgroundOperations.async(() -> {
                            var histogram = showHistogram(stretchedImage);
                            Platform.runLater(() -> {
                                statsTab.setContent(histogram);
                                showMetadata(stretchedImage.metadata());
                            });
                        });
                    }));
            var pixelShiftRange = imageWrapper.findMetadata(PixelShiftRange.class).orElse(new PixelShiftRange(-15, 15, .25));
            int imageWidth = imageWrapper.width();
            int imageHeight = imageWrapper.height();
            var imagePath = event.getPayload().path();
            addedImageViewer.getImageView().setRectangleSelectionListener(new RectangleSelectionListener() {
                @Override
                public boolean supports(ActionKind kind) {
                    if (kind.isCrop()) {
                        if (kind == ActionKind.IMAGEMATH_CROP) {
                            var stretchedImage = addedImageViewer.getStretchedImage();
                            return stretchedImage.findMetadata(ReferenceCoords.class).isPresent();
                        }
                        return true;
                    }
                    if (kind == ActionKind.EXTRACT_SER_FRAMES) {
                        // Only available if we have reference coordinates and a SER file
                        var stretchedImage = addedImageViewer.getStretchedImage();
                        return stretchedImage.findMetadata(ReferenceCoords.class).isPresent() && serFile != null;
                    }
                    if (adjustedParams == null) {
                        return false;
                    }
                    if (generatedImageKind == GeneratedImageKind.TECHNICAL_CARD) {
                        return kind != ActionKind.CREATE_ANIM_OR_PANEL;
                    }
                    return true;
                }

                @Override
                public void onSelectRegion(ActionKind kind, int x, int y, int width, int height) {
                    BackgroundOperations.async(() -> {
                        var stretchedImage = addedImageViewer.getStretchedImage();
                        stretchedImage.findMetadata(ReferenceCoords.class).ifPresentOrElse(coord -> {
                            var cx = x + width / 2d;
                            var cy = y + height / 2d;
                            var orig = coord.determineOriginalCoordinates(new Point2D(cx, cy), ReferenceCoords.GEO_CORRECTION);
                            int xx = Math.max(0, (int) orig.x() - width / 2);
                            int yy = Math.max(0, (int) orig.y() - height / 2);
                            if (kind == ActionKind.IMAGEMATH_CROP) {
                                LOGGER.info(JSolEx.message("info.script.crop"), xx, yy, width, height);
                            } else if (kind == ActionKind.CROP) {
                                performCropping(stretchedImage, x, y, width, height);
                            } else if (kind == ActionKind.CREATE_ANIM_OR_PANEL) {
                                createAnimationOrPanel(xx, yy, width, height);
                            } else if (kind == ActionKind.EXTRACT_SER_FRAMES) {
                                extractSerFramesToMp4(x, y, width, height, coord);
                            }
                        }, () -> {
                            if (kind == ActionKind.CROP) {
                                performCropping(stretchedImage, x, y, width, height);
                            }
                        });
                    });
                }

                private void createAnimationOrPanel(int x, int y, int width, int height) {
                    var redshiftProcessor = new RedshiftImagesProcessor(
                            shiftImages,
                            adjustedParams,
                            serFile,
                            outputDirectory,
                            owner,
                            SingleModeProcessingEventListener.this,
                            imageEmitter,
                            List.of(),
                            polynomial,
                            averageImage,
                            rootOperation,
                            createNamingStrategy(),
                            0,
                            computeSerFileBasename(serFile),
                            mainEllipse
                    );
                    Platform.runLater(() -> {
                        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "custom-anim-panel");
                        Parent node;
                        try {
                            node = fxmlLoader.load();
                        } catch (IOException e) {
                            throw new ProcessingException(e);
                        }
                        var controller = fxmlLoader.<CustomAnimationCreator>getController();
                        var stage = newModalStage(owner.getMainStage(), node);
                        controller.setup(stage, adjustedParams, pixelShiftRange, imageWidth, imageHeight, x, y, width, height, redshiftProcessor, animCount.getAndIncrement());
                        stage.setTitle(I18N.string(JSolEx.class, "custom-anim-panel", "frame.title"));
                        stage.showAndWait();
                    });
                }

                private void performCropping(ImageWrapper stretchedImage, int x, int y, int width, int height) {
                    BackgroundOperations.async(() -> {
                        var crop = new Crop(Map.of(), SingleModeProcessingEventListener.this);
                        var cropped = crop.crop(Map.of("img", stretchedImage, "left", x, "top", y, "width", width, "height", height));
                        if (cropped instanceof ImageWrapper croppedImage) {
                            var id = cropCount.getAndIncrement();
                            var imageName = "cropped-" + id;
                            var title = String.format(message("cropped.image"), id);
                            var path = adjustedParams != null
                                    ? outputDirectory.resolve(createNamingStrategy().render(0, null, Constants.TYPE_CUSTOM, imageName, computeSerFileBasename(serFile), croppedImage))
                                    : imagePath.getParent().resolve("cropped-" + id);
                            broadcast(new ImageGeneratedEvent(
                                    new GeneratedImage(
                                            GeneratedImageKind.CROPPED,
                                            title,
                                            path,
                                            croppedImage,
                                            null
                                    )
                            ));
                        }
                    });
                }

                private void extractSerFramesToMp4(int x, int y, int width, int height, ReferenceCoords coord) {
                    BackgroundOperations.async(() -> {
                        try {
                            var topLeft = coord.determineOriginalCoordinates(new Point2D(x, y), ReferenceCoords.NO_LIMIT);
                            var bottomRight = coord.determineOriginalCoordinates(new Point2D(x + width, y + height), ReferenceCoords.NO_LIMIT);

                            try (var serReader = SerFileReader.of(serFile)) {
                                var header = serReader.header();
                                var originalWidth = header.geometry().width();
                                var originalHeight = header.geometry().height();

                                int totalFrames = header.frameCount();
                                double minY = Math.min(topLeft.y(), bottomRight.y());
                                double maxY = Math.max(topLeft.y(), bottomRight.y());
                                double minX = Math.min(topLeft.x(), bottomRight.x());
                                double maxX = Math.max(topLeft.x(), bottomRight.x());

                                int startFrame = Math.max(0, (int) Math.round(minY));
                                int endFrame = Math.min(totalFrames - 1, (int) Math.round(maxY));
                                int frameCount = Math.max(1, endFrame - startFrame + 1);
                                int cropLeft = Math.max(0, (int) Math.round(minX));
                                int cropRight = Math.min(originalWidth - 1, (int) Math.round(maxX));

                                if (cropLeft > cropRight) {
                                    int temp = cropLeft;
                                    cropLeft = cropRight;
                                    cropRight = temp;
                                }

                                int cropWidth = Math.max(1, cropRight - cropLeft + 1);

                                // Validate bounds
                                if (startFrame >= totalFrames || endFrame < 0 || frameCount <= 0) {
                                    LOGGER.error(JSolEx.message("error.invalid.frame.bounds"), startFrame, endFrame, totalFrames, frameCount);
                                    throw new IllegalArgumentException("Invalid frame selection bounds");
                                }
                                if (cropLeft >= originalWidth || cropRight < 0 || cropWidth <= 0) {
                                    LOGGER.error(JSolEx.message("error.invalid.crop.bounds"), cropLeft, cropRight, originalWidth, cropWidth);
                                    throw new IllegalArgumentException("Invalid crop selection bounds");
                                }

                                // Create a list to hold the extracted frames
                                var frames = new ArrayList<ImageWrapper>();

                                // Extract frames from the selected frame range with width-only cropping (preserve full height)
                                for (int frameIndex = startFrame; frameIndex <= endFrame; frameIndex++) {
                                    serReader.seekFrame(frameIndex);
                                    var frame = serReader.currentFrame();

                                    // Convert frame data to ImageWrapper
                                    var converter = ImageUtils.createImageConverter(header.geometry().colorMode());
                                    var buffer = converter.createBuffer(header.geometry());
                                    converter.convert(frameIndex, frame.data(), header.geometry(), buffer);
                                    var frameImage = new ImageWrapper32(header.geometry().width(), originalHeight, buffer, Map.of());

                                    // Crop the frame: width-only cropping, preserve full height
                                    var cropFunction = new Crop(Map.of(), SingleModeProcessingEventListener.this);
                                    var croppedFrame = (ImageWrapper) cropFunction.crop(Map.of(
                                            "img", frameImage,
                                            "left", cropLeft,
                                            "top", 0,  // Start from top (preserve full height)
                                            "width", cropWidth,
                                            "height", originalHeight  // Use full height
                                    ));

                                    frames.add(croppedFrame);

                                    // Update progress
                                    if ((frameIndex - startFrame) % 10 == 0) {
                                        int totalFramesToExtract = endFrame - startFrame + 1;
                                        int currentFrame = frameIndex - startFrame + 1;
                                        var progressOp = rootOperation.createChild("Extracting frame " + currentFrame + "/" + totalFramesToExtract);
                                        progressOp.update(currentFrame / (double) totalFramesToExtract, "Extracting frame " + currentFrame + "/" + totalFramesToExtract);
                                        broadcast(progressOp);
                                    }
                                }

                                if (frames.isEmpty()) {
                                    throw new IllegalStateException("No frames were extracted - cannot create animation");
                                }

                                var animate = new Animate(Map.of(AnimationFormat.class, Configuration.getInstance().getAnimationFormats()), SingleModeProcessingEventListener.this);
                                Object animationResult;
                                try {
                                    animationResult = animate.createAnimation(Map.of("images", frames, "delay", 25));
                                } catch (Exception e) {
                                    throw new RuntimeException("Animation creation failed: " + e.getMessage(), e);
                                }
                                if (animationResult instanceof FileOutputResult fileOutput) {
                                    var baseFilename = createNamingStrategy().render(0, null, Constants.TYPE_CUSTOM,
                                            "ser-extract-" + System.currentTimeMillis(),
                                            computeSerFileBasename(serFile), null);

                                    var filesToMove = fileOutput.allFiles();
                                    var displayFile = fileOutput.displayFile();
                                    Path displayOutputPath = null;

                                    for (var sourceFile : filesToMove) {
                                        var extension = sourceFile.getFileName().toString().substring(sourceFile.getFileName().toString().lastIndexOf('.'));
                                        var outputPath = outputDirectory.resolve(baseFilename + extension);
                                        Files.createDirectories(outputPath.getParent());
                                        Files.move(sourceFile, outputPath, StandardCopyOption.REPLACE_EXISTING);

                                        if (sourceFile.equals(displayFile)) {
                                            displayOutputPath = outputPath;
                                        }
                                    }

                                    if (displayOutputPath != null) {
                                        broadcast(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, "SER Frame Extract", displayOutputPath));
                                    }
                                } else {
                                    throw new RuntimeException("Animation creation returned unexpected result type: " +
                                            (animationResult != null ? animationResult.getClass().getName() : "null"));
                                }
                            }

                        } catch (Exception e) {
                            broadcast(new NotificationEvent(new Notification(
                                    Notification.AlertType.ERROR,
                                    "SER Frame Extraction Failed",
                                    "Animation Creation Error",
                                    "Failed to extract SER frames to MP4: " + e.getMessage()
                            )));
                        }
                    });
                }
            });
            var imageViewer = popupViews.get(title);
            if (imageViewer != null) {
                imageViewer.setImage(baseName, adjustedParams != null ? adjustedParams : params, imageWrapper, payload.path());
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

    private BarChart<String, Number> showHistogram(ImageWrapper imageWrapper) {
        var histogram = prepareHistogram(imageWrapper);
        var histograms = cachedHistograms.computeIfAbsent(imageWrapper, unused -> {
            if (imageWrapper instanceof ImageWrapper32 mono) {
                return List.of(new CachedHistogram(Histogram.of(
                        new Image(mono.width(), mono.height(), mono.data()), BINS
                ), "grey"));
            } else if (imageWrapper instanceof RGBImage rgbImage) {
                var rgb = new float[][][]{rgbImage.r(), rgbImage.g(), rgbImage.b()};
                List<CachedHistogram> result = new ArrayList<>(rgb.length);
                for (int i = 0; i < rgb.length; i++) {
                    float[][] channel = rgb[i];
                    result.add(new CachedHistogram(Histogram.of(
                            new Image(rgbImage.width(), rgbImage.height(), channel), BINS
                    ), RGB_COLORS[i]));
                }
                return result;
            }
            return List.of();
        });
        histograms.forEach(c -> addSeries(histogram, c.histogram, c.color));
        return histogram;
    }

    private BarChart<String, Number> prepareHistogram(ImageWrapper imageWrapper) {
        var histogram = createHistogramChart();
        registerSaveChartAction(new GraphData.HistogramData(histogram, () -> showHistogram(imageWrapper)));
        return histogram;
    }

    private static BarChart<String, Number> createHistogramChart() {
        var xAxis = new CategoryAxis();
        var yAxis = new NumberAxis();
        xAxis.setLabel(message("pixel.value"));
        yAxis.setLabel(message("pixel.count"));
        var histogramChart = new BarChart<>(xAxis, yAxis);
        histogramChart.setTitle(message("image.histogram"));
        histogramChart.setBarGap(0);
        histogramChart.setLegendVisible(false);
        return histogramChart;
    }

    private static void addSeries(BarChart<String, Number> chart, Histogram histogram, String color) {
        var series = new XYChart.Series<String, Number>();
        for (int i = 0; i < histogram.values().length; i++) {
            var d = new XYChart.Data<String, Number>(String.valueOf(i), histogram.values()[i]);
            series.getData().add(d);
        }

        // Add the series to the bar chart
        chart.getData().add(series);
        for (XYChart.Data<String, Number> d : series.getData()) {
            d.getNode().setStyle("-fx-bar-fill: " + color + ";");
        }
    }

    @Override
    public void onFileGenerated(FileGeneratedEvent event) {
        var payload = event.getPayload();
        var filePath = payload.path();
        var fileName = filePath.toFile().getName();
        var displayTitle = payload.displayTitle() != null ? payload.displayTitle() : payload.title();
        var description = payload.description();
        if (fileName.endsWith(".mp4")) {
            Platform.runLater(() -> owner.getImagesViewer().addVideo(payload.kind(), displayTitle, filePath, description));
        } else if (fileName.endsWith(".gif")) {
            Platform.runLater(() -> owner.getImagesViewer().addAnimatedGif(payload.kind(), displayTitle, filePath, description));
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
        Platform.runLater(() -> {
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
        scriptImagesByLabel.clear();
        var spectralRay = params.spectrumParams().ray();
        if (spectralRay != null && !SpectralRay.AUTO.equals(spectralRay)) {
            owner.updateSpectralLineIndicator(spectralRay, false);
        }
        Platform.runLater(() -> {
            var logsTab = owner.getLogsTab();
            var tabPane = logsTab.getTabPane();
            if (tabPane != null) {
                tabPane.getSelectionModel().select(logsTab);
            }
        });
    }

    @Override
    public void onSpectralLineDetected(SpectralLineDetectedEvent e) {
        var detectedRay = e.getPayload().spectralRay();
        owner.updateSpectralLineIndicator(detectedRay, true);
    }

    @Override
    public void onGeometryDetected(GeometryDetectedEvent e) {
        var geometry = e.getPayload();
        owner.updateGeometryIndicators(geometry.tiltDegrees(), geometry.xyRatio());
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        var payload = e.getPayload();
        imageEmitter = payload.customImageEmitter();
        scriptExecutionContext = prepareExecutionContext(payload);
        shiftImages.putAll(payload.shiftImages());
        Platform.runLater(this::updateSpectral3DButtonsState);
        pixelShiftRange = payload.pixelShiftRange();
        mainEllipse = payload.mainEllipse();
        imageScriptExecutor = new JSolExScriptExecutor(
                shift -> {
                    var minShift = shiftImages.keySet().stream().mapToDouble(PixelShift::pixelShift).min().orElse(0d);
                    var maxShift = shiftImages.keySet().stream().mapToDouble(PixelShift::pixelShift).max().orElse(0d);
                    double lookup = shift.pixelShift();
                    if (lookup < minShift) {
                        LOGGER.warn(String.format(message("cropping.window.invalid.shift"), lookup, minShift));
                        lookup = minShift;
                    } else if (lookup > maxShift) {
                        LOGGER.warn(String.format(message("cropping.window.invalid.shift"), lookup, maxShift));
                        lookup = maxShift;
                    }
                    return shiftImages.get(new PixelShift(lookup));
                },
                new HashMap<>(scriptExecutionContext),
                this,
                null
        );
        for (var entry : pendingVariables.entrySet()) {
            imageScriptExecutor.putVariable(entry.getKey(), entry.getValue());
        }
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
        LOGGER.info(message("processing.done"));
        var finishedString = String.format(message("finished.in"), seconds);
        LOGGER.info(finishedString);
        owner.prepareForScriptExecution(this, params, rootOperation, SectionKind.SINGLE);
        suggestions.clear();
        System.gc();
        broadcast(rootOperation.update(1, finishedString));
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
                averageImage,
                rootOperation,
                createNamingStrategy(),
                0,
                computeSerFileBasename(serFile),
                mainEllipse
        ));
        owner.prepareForGongImageDownload(processParams);
        executeSingleFileBatchScripts();
        Platform.runLater(() -> {
            var tabPane = profileTab.getTabPane();
            if (tabPane != null) {
                tabPane.getSelectionModel().select(profileTab);
            }
        });
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
        context.put(AnimationFormat.class, Configuration.getInstance().getAnimationFormats());
        return context;
    }

    @Override
    public void onProgress(ProgressEvent e) {
        owner.updateProgress(e.getPayload().progress(), e.getPayload().taskPath());
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
    public void setIncludesDir(Path includesDir) {
        imageScriptExecutor.setIncludesDir(includesDir);
    }

    @Override
    public ImageMathScriptResult execute(String script, SectionKind kind) {
        var sd = System.nanoTime();
        // perform a first pass just to check if they are missing image shifts
        Set<Double> missingShifts = determineShiftsRequiredInScript(script);
        shiftImages.keySet().stream().map(PixelShift::pixelShift).toList().forEach(missingShifts::remove);
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(missingShifts);
        }
        var result = imageScriptExecutor.execute(script, kind);
        var namingStrategy = createNamingStrategy();
        var outputsMetadata = extractOutputsMetadata(script);
        var language = LocaleUtils.getConfiguredLocale().getLanguage();
        ImageMathScriptExecutor.render(result, imageEmitter, (outputLabel, fileOutput) -> {
            var baseName = namingStrategy.render(0, null, Constants.TYPE_CUSTOM, outputLabel, computeSerFileBasename(serFile), null);
            try {
                var displayPath = FilesUtils.saveAllFilesAndGetDisplayPath(fileOutput, outputDirectory, baseName);
                if (displayPath != null) {
                    var metadata = outputsMetadata.get(outputLabel);
                    var displayTitle = metadata != null ? metadata.getDisplayTitle(language) : null;
                    var description = metadata != null ? metadata.getDisplayDescription(language) : null;
                    onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, outputLabel, displayPath, description, displayTitle));
                }
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        }, outputsMetadata, language);
        var invalidExpressions = result.invalidExpressions();
        if (!invalidExpressions.isEmpty()) {
            Platform.runLater(() -> ScriptErrorDialog.showErrors(invalidExpressions));
        }
        var dur = java.time.Duration.ofNanos(System.nanoTime() - sd);
        var secs = dur.toSeconds() + (dur.toMillisPart() / 1000d);
        onProgress(ProgressEvent.of(rootOperation.complete(String.format(Constants.message("script.completed.in.format"), secs))));
        return result;
    }

    private static Map<String, OutputMetadata> extractOutputsMetadata(String script) {
        return ImageMathParameterExtractor.extractOutputsMetadataOnly(script);
    }

    private static Map<String, OutputMetadata> extractOutputsMetadata(File scriptFile) {
        return ImageMathParameterExtractor.extractOutputsMetadataOnly(scriptFile.toPath());
    }

    @Override
    public void removeVariable(String variable) {
        if (imageScriptExecutor != null) {
            imageScriptExecutor.removeVariable(variable);
        }
    }

    private void restartProcessForMissingShifts(Set<Double> missingShifts) {
        var outOfRange = missingShifts.stream()
                .filter(s -> s > pixelShiftRange.maxPixelShift())
                .findAny();
        if (outOfRange.isPresent()) {
            missingShifts.add(pixelShiftRange.maxPixelShift());
        }
        outOfRange = missingShifts.stream()
                .filter(s -> s < pixelShiftRange.minPixelShift())
                .findAny();
        if (outOfRange.isPresent()) {
            missingShifts.add(pixelShiftRange.minPixelShift());
        }
        missingShifts.removeIf(s -> !pixelShiftRange.includes(s));
        LOGGER.warn(message("restarting.process.missing.shifts"), missingShifts.stream().map(d -> String.format("%.2f", d)).toList());
        // restart processing to include missing images
        var tmpParams = params.withRequestedImages(
                new RequestedImages(Set.of(GeneratedImageKind.GEOMETRY_CORRECTED),
                        Stream.concat(params.requestedImages().pixelShifts().stream(), missingShifts.stream()).toList(),
                        missingShifts,
                        Set.of(),
                        ImageMathParams.NONE,
                        false,
                        false)
        ).withExtraParams(params.extraParams().withAutosave(false));
        var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, tmpParams, LocalDateTime.now(), false, Configuration.getInstance().getMemoryRestrictionMultiplier(), rootOperation);
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
        solexVideoProcessor.setForceDetectActiveRegions(params.requestedImages().isEnabled(GeneratedImageKind.ACTIVE_REGIONS));
        if (mainEllipse != null) {
            solexVideoProcessor.setCachedEllipse(mainEllipse);
        }
        solexVideoProcessor.process();
    }

    @Override
    public <T> Optional<T> getVariable(String name) {
        return imageScriptExecutor.getVariable(name);
    }

    @Override
    public <T> void putInContext(Class<T> key, Object value) {
        if (imageScriptExecutor != null) {
            imageScriptExecutor.putInContext(key, value);
        }
    }

    @Override
    public void putVariable(String name, Object value) {
        if (imageScriptExecutor != null) {
            imageScriptExecutor.putVariable(name, value);
        } else {
            pendingVariables.put(name, value);
        }
    }

    @Override
    public Map<String, Object> getVariables() {
        if (imageScriptExecutor != null) {
            return imageScriptExecutor.getVariables();
        }
        return pendingVariables;
    }

    private Set<Double> determineShiftsRequiredInScript(String script) {
        var collectingExecutor = new DefaultImageScriptExecutor(
                ShiftCollectingImageExpressionEvaluator.zeroImages(),
                scriptExecutionContext
        );
        getVariables().forEach(collectingExecutor::putVariable);
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
        } else if (event instanceof ReconstructionDoneEvent e) {
            onReconstructionDone(e);
        } else if (event instanceof SpectralLineDetectedEvent e) {
            onSpectralLineDetected(e);
        } else if (event instanceof GeometryDetectedEvent e) {
            onGeometryDetected(e);
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
        var payload = e.getPayload();
        cachedAverageImagePayload = payload;
        Platform.runLater(this::updateSpectral3DButtonsState);
        adjustedParams = payload.adjustedParams();
        polynomial = payload.polynomial();
        averageImage = payload.image().data();
        profileViewFactory = () -> {
            var xAxis = new CategoryAxis();
            var yAxis = new NumberAxis();
            xAxis.setLabel(message("wavelength"));
            yAxis.setLabel(message("intensity"));
            var lineChart = new LineChart<>(xAxis, yAxis);
            var series = new XYChart.Series<String, Number>();
            var image = payload.image();
            var width = image.width();
            var height = image.height();
            var start = payload.leftBorder();
            var end = payload.rightBorder();
            var data = image.data();
            var polynomial = payload.polynomial();
            var params = payload.adjustedParams();
            var lambda0 = params.spectrumParams().ray().wavelength();
            var binning = params.observationDetails().binning();
            var pixelSize = params.observationDetails().pixelSize();
            var canDrawReference = binning != null && pixelSize != null && lambda0.nanos() > 0 && pixelSize > 0 && binning > 0;
            series.setName(formatLegend(params.observationDetails().instrument(), lambda0, binning, pixelSize));
            var dispersion = canDrawReference ? SpectrumAnalyzer.computeSpectralDispersion(params.observationDetails().instrument(), lambda0, pixelSize * binning) : null;
            var range = PixelShiftRange.computePixelShiftRange(start, end, height, polynomial);
            var dataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();
            for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
                double cpt = 0;
                double val = 0;
                for (int x = start; x < end; x++) {
                    var v = polynomial.applyAsDouble(x);
                    var exactNy = v + pixelShift;
                    int lowerNy = (int) Math.floor(exactNy);
                    int upperNy = (int) Math.ceil(exactNy);

                    if (lowerNy >= 0 && upperNy < height) {
                        var lowerValue = data[lowerNy][x];
                        var upperValue = data[upperNy][x];
                        var interpolatedValue = lowerValue + (upperValue - lowerValue) * (exactNy - lowerNy);

                        val += interpolatedValue;
                        cpt++;
                    }
                }
                if (cpt > 0) {
                    Wavelen wl = canDrawReference ? computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                    dataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, val / cpt));
                }
            }
            lineChart.getData().add(series);

            List<SpectrumAnalyzer.DataPoint> referenceDataPoints;
            if (canDrawReference) {
                var referenceSeries = new XYChart.Series<String, Number>();
                referenceSeries.setName(message("reference.intensity"));
                lineChart.getData().add(referenceSeries);
                referenceDataPoints = normalizeDatapoints(SpectrumAnalyzer.findReferenceDatapoints(dataPoints), null).dataPoints();
                for (var dataPoint : referenceDataPoints) {
                    addDataPointToSeries(dataPoint, referenceSeries);
                }
            } else {
                referenceDataPoints = null;
            }
            var normalizedDataPoints = normalizeDatapoints(dataPoints, null);
            for (var dataPoint : normalizedDataPoints.dataPoints()) {
                addDataPointToSeries(dataPoint, series);
            }

            // Compute line statistics from the normalized data, using reference for continuum if available
            currentLineStatistics = SpectralLineAnalysis.computeStatistics(normalizedDataPoints.dataPoints(), referenceDataPoints);

            // Add FWHM visualization to the chart
            addFWHMVisualization(lineChart, currentLineStatistics, normalizedDataPoints.dataPoints());

            NormalizedDataPoints normalizedFrameDataPoints;
            NormalizedDataPoints normalizedLineDataPoints;
            SpectralLineAnalysis.LineStatistics frameLineStatistics = null;
            if (currentColumn != null && currentColumn >= start && currentColumn < end) {
                var frameDataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();
                var lineDataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();

                // Add frame profile curve - averaged across all columns for the current frame
                var frameSeries = new XYChart.Series<String, Number>();
                frameSeries.setName(message("frame.intensity"));
                lineChart.getData().add(frameSeries);

                for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
                    double cpt = 0;
                    double val = 0;
                    for (int x = start; x < end; x++) {
                        var v = polynomial.applyAsDouble(x);
                        var exactNy = v + pixelShift;
                        int lowerNy = (int) Math.floor(exactNy);
                        int upperNy = (int) Math.ceil(exactNy);

                        if (lowerNy >= 0 && upperNy < height) {
                            var lowerValue = currentSpectrumFrameData[lowerNy][x];
                            var upperValue = currentSpectrumFrameData[upperNy][x];
                            var interpolatedValue = lowerValue + (upperValue - lowerValue) * (exactNy - lowerNy);

                            val += interpolatedValue;
                            cpt++;
                        }
                    }
                    if (cpt > 0) {
                        Wavelen wl = canDrawReference ? computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                        frameDataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, val / cpt));
                    }
                }

                for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
                    int x = currentColumn;
                    var v = polynomial.applyAsDouble(x);
                    var exactNy = v + pixelShift;
                    int lowerNy = (int) Math.floor(exactNy);
                    int upperNy = (int) Math.ceil(exactNy);

                    if (lowerNy >= 0 && upperNy < height) {
                        var lowerValue = currentSpectrumFrameData[lowerNy][x];
                        var upperValue = currentSpectrumFrameData[upperNy][x];
                        var interpolatedValue = lowerValue + (upperValue - lowerValue) * (exactNy - lowerNy);

                        Wavelen wl = canDrawReference ? computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                        lineDataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, interpolatedValue));
                    }
                }

                normalizedFrameDataPoints = normalizeDatapoints(frameDataPoints, null);
                for (var dataPoint : normalizedFrameDataPoints.dataPoints()) {
                    addDataPointToSeries(dataPoint, frameSeries);
                }

                var lineSeries = new XYChart.Series<String, Number>();
                // Compute wavelength for the clicked column
                String lineLabel;
                if (canDrawReference) {
                    var columnPixelShift = polynomial.applyAsDouble(currentColumn);
                    var wavelength = computeWavelength(columnPixelShift, lambda0, dispersion);
                    lineLabel = message("line.intensity") + " (" + String.format(Locale.US, "%.2f Å", wavelength.angstroms()) + ")";
                } else {
                    lineLabel = message("line.intensity") + " (" + currentColumn + ")";
                }
                lineSeries.setName(lineLabel);
                lineChart.getData().add(lineSeries);

                // Normalize line data independently
                normalizedLineDataPoints = normalizeDatapoints(lineDataPoints, null);
                for (var dataPoint : normalizedLineDataPoints.dataPoints()) {
                    addDataPointToSeries(dataPoint, lineSeries);
                }

                // Compute statistics for the line profile at clicked position, using reference for continuum if available
                frameLineStatistics = SpectralLineAnalysis.computeStatistics(normalizedLineDataPoints.dataPoints(), referenceDataPoints);
            } else {
                normalizedFrameDataPoints = null;
                normalizedLineDataPoints = null;
            }

            // Create statistics panel
            var statsToDisplay = frameLineStatistics != null ? frameLineStatistics : currentLineStatistics;
            var statisticsPanel = createStatisticsPanel(statsToDisplay, canDrawReference);

            // Create the CSV writer with statistics
            final var finalReferenceDataPoints = referenceDataPoints;
            final var finalNormalizedFrameDataPoints = normalizedFrameDataPoints;
            final var finalNormalizedLineDataPoints = normalizedLineDataPoints;
            final var finalNormalizedDataPoints = normalizedDataPoints;
            final var finalStatsToDisplay = statsToDisplay;
            Supplier<LineChart<?, ?>> chartSupplier = () -> createProfileChart(
                    params, payload, range, finalNormalizedDataPoints, finalReferenceDataPoints,
                    finalNormalizedFrameDataPoints, finalNormalizedLineDataPoints, finalStatsToDisplay,
                    canDrawReference, dispersion, lambda0
            );
            registerSaveChartAction(new GraphData.ProfileData(lineChart, chartSupplier, writer -> {
                writer.print("pixel_shift;wavelength;reference_intensity;intensity");
                if (currentColumn != null) {
                    writer.print(";frame_intensity;line_intensity");
                }
                writer.println();
                for (var dataPoint : normalizedDataPoints.dataPoints()) {
                    var referenceDataPoint = finalReferenceDataPoints != null ? finalReferenceDataPoints.stream()
                            .filter(dp -> dp.pixelShift() == dataPoint.pixelShift())
                            .findFirst()
                            .orElse(new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 0d)) : new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 0d);
                    writer.printf(Locale.US, "%.2f;%.2f;%.2f;%.2f", dataPoint.pixelShift(), dataPoint.wavelen().angstroms(), referenceDataPoint.intensity(), dataPoint.intensity());
                    if (currentColumn != null && finalNormalizedFrameDataPoints != null) {
                        var frameIntensity = finalNormalizedFrameDataPoints.dataPoints().stream()
                                .filter(dp -> dp.pixelShift() == dataPoint.pixelShift())
                                .findFirst()
                                .map(SpectrumAnalyzer.DataPoint::intensity)
                                .orElse(0d);
                        var lineIntensity = finalNormalizedLineDataPoints.dataPoints().stream()
                                .filter(dp -> dp.pixelShift() == dataPoint.pixelShift())
                                .findFirst()
                                .map(SpectrumAnalyzer.DataPoint::intensity)
                                .orElse(0d);
                        writer.printf(Locale.US, ";%.2f;%.2f", frameIntensity, lineIntensity);
                    }
                    writer.println();
                }
            }, finalStatsToDisplay, canDrawReference));

            // Create button bar above the chart
            show3DButton = new Button(I18N.string(JSolEx.class, "spectral-surface-3d", "show.3d.profile"));
            show3DButton.getStyleClass().add("image-viewer-button");
            show3DButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
            show3DButton.setOnAction(evt -> show3DSpectralProfile());
            show3DButton.setDisable(cachedAverageImagePayload == null);
            showEvolutionButton = new Button(I18N.string(JSolEx.class, "spectral-surface-3d", "show.spectral.cube"));
            showEvolutionButton.getStyleClass().add("image-viewer-button");
            showEvolutionButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
            showEvolutionButton.setOnAction(evt -> showSpectralEvolution());
            showEvolutionButton.setDisable(cachedAverageImagePayload == null || cachedTrimmingParameters == null);
            showTomographyButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "show.tomography"));
            showTomographyButton.getStyleClass().add("image-viewer-button");
            showTomographyButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
            showTomographyButton.setOnAction(evt -> showSphericalTomography());
            var openGLAvailable = OpenGLAvailability.isAvailable();
            showTomographyButton.setVisible(openGLAvailable);
            showTomographyButton.setManaged(openGLAvailable);
            showTomographyButton.setDisable(shiftImages.isEmpty() || cachedAverageImagePayload == null);
            var buttonBar = new HBox(10);
            buttonBar.setPadding(new Insets(5, 10, 5, 10));
            buttonBar.setAlignment(Pos.CENTER_RIGHT);
            buttonBar.getChildren().addAll(show3DButton, showEvolutionButton, showTomographyButton);

            // Create the main layout with chart and statistics
            var mainPane = new BorderPane();
            mainPane.setTop(buttonBar);
            mainPane.setCenter(lineChart);
            mainPane.setBottom(statisticsPanel);

            return mainPane;
        };
        if (Platform.isFxApplicationThread()) {
            profileTab.setContent(profileViewFactory.get());
        } else {
            Platform.runLater(() -> profileTab.setContent(profileViewFactory.get()));
        }

    }

    @Override
    public void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {
        cachedTrimmingParameters = e.getPayload();
        Platform.runLater(this::updateSpectral3DButtonsState);
        owner.setTrimmingParameters(cachedTrimmingParameters);
    }

    private void updateSpectral3DButtonsState() {
        if (show3DButton != null) {
            show3DButton.setDisable(cachedAverageImagePayload == null);
        }
        if (showEvolutionButton != null) {
            showEvolutionButton.setDisable(cachedAverageImagePayload == null || cachedTrimmingParameters == null);
        }
        if (showTomographyButton != null) {
            showTomographyButton.setDisable(shiftImages.isEmpty() || cachedAverageImagePayload == null);
        }
    }

    @Override
    public void onEllipseFittingRequest(EllipseFittingRequestEvent e) {
        owner.showEllipseFittingDialog(e.image(), e.initialEllipse())
                .thenAccept(result -> e.resultFuture().complete(result))
                .exceptionally(throwable -> {
                    e.resultFuture().completeExceptionally(throwable);
                    return null;
                });
    }

    private Parent createStatisticsPanel(SpectralLineAnalysis.LineStatistics stats, boolean hasWavelength) {
        var statsBox = new HBox(20);
        statsBox.setPadding(new Insets(10));
        statsBox.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        var titleLabel = new Label(message("line.statistics"));
        titleLabel.setFont(Font.font(titleLabel.getFont().getFamily(), FontWeight.BOLD, 12));

        if (stats == null) {
            statsBox.getChildren().addAll(titleLabel, new Label("N/A"));
            return statsBox;
        }

        var depthBox = createStatItem(message("line.depth"), String.format(Locale.US, "%.2f%%", stats.lineDepth() * 100));
        String fwhmValue = stats.hasFWHMData()
                ? (hasWavelength ? String.format(Locale.US, "%.3f Å", stats.fwhm()) : String.format(Locale.US, "%.2f px", stats.fwhm()))
                : "N/A";
        var fwhmBox = createStatItem(message("line.fwhm"), fwhmValue);
        var continuumBox = createStatItem(message("line.continuum"), String.format(Locale.US, "%.1f", stats.continuum()));
        var centerBox = createStatItem(message("line.center"), hasWavelength
                ? String.format(Locale.US, "%.3f Å", stats.lineCenterWavelength())
                : String.format(Locale.US, "%.2f px", stats.lineCenterWavelength()));

        statsBox.getChildren().addAll(titleLabel, depthBox, fwhmBox, continuumBox, centerBox);
        return statsBox;
    }

    private void show3DSpectralProfile() {
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

    private void showSpectralEvolution() {
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
        var optionsOpt = dialog.showAndWait(owner.getMainStage());
        if (optionsOpt.isEmpty()) {
            return;
        }
        var options = optionsOpt.get();

        var loadingStage = createLoadingStage(I18N.string(JSolEx.class, "spectral-surface-3d", "evolution.title"));
        loadingStage.show();

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
                var ellipseForViewer = mainEllipse;
                Platform.runLater(() -> {
                    loadingStage.close();
                    var viewer = SpectralEvolution4DViewer.show(
                            evolutionData,
                            ellipseForViewer,
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

    private void showSphericalTomography() {
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

        // Create RedshiftImagesProcessor for generating missing images
        var redshiftProcessor = new RedshiftImagesProcessor(
                shiftImages,
                adjustedParams,
                serFile,
                outputDirectory,
                owner,
                this,
                imageEmitter,
                List.of(),
                polynomial,
                averageImage,
                rootOperation,
                createNamingStrategy(),
                0,
                computeSerFileBasename(serFile),
                mainEllipse
        );

        Platform.runLater(() -> {
            var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "spherical-tomography-params");
            Parent node;
            try {
                node = fxmlLoader.load();
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
            var controller = fxmlLoader.<SphericalTomographyCreator>getController();
            var stage = newModalStage(owner.getMainStage(), node);
            controller.setup(stage, pixelShiftRange, redshiftProcessor, (step, unused) -> {
                double minShift = controller.getMinShift();
                double maxShift = controller.getMaxShift();
                double stepSize = controller.getStepSize();

                // Generate images for the requested range
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
        // Create loading stage on FX thread using CountDownLatch for synchronization
        var loadingStageRef = new AtomicReference<Stage>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            var loadingStage = createLoadingStage(I18N.string(JSolEx.class, "spherical-tomography", "frame.title"));
            loadingStageRef.set(loadingStage);
            loadingStage.show();
            latch.countDown();
        });

        BackgroundOperations.async(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            var loadingStage = loadingStageRef.get();
            try {
                // Generate the list of required shifts
                var requiredShifts = new TreeSet<Double>();
                for (double shift = minShift; shift <= maxShift; shift += stepSize) {
                    requiredShifts.add(Math.round(shift * 100.0) / 100.0);
                }
                // Always include pixel shift 0 (line center)
                if (minShift <= 0 && maxShift >= 0) {
                    requiredShifts.add(0.0);
                }

                // Find missing shifts and generate them
                var missingShifts = requiredShifts.stream()
                        .filter(d -> !shiftImages.containsKey(new PixelShift(d)))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (!missingShifts.isEmpty()) {
                    // Use the RedshiftImagesProcessor pattern to generate missing images
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
                            BatchOperations.submitOneOfAKind("progress", () ->
                                    owner.updateProgress(e.getPayload().progress(), e.getPayload().task()));
                        }
                    });
                    solexVideoProcessor.setIgnoreIncompleteShifts(true);
                    if (mainEllipse != null) {
                        solexVideoProcessor.setCachedEllipse(mainEllipse);
                    }
                    solexVideoProcessor.process();
                }

                // Now extract tomography data using only the requested shifts
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

    private VBox createStatItem(String label, String value) {
        var labelNode = new Label(label);
        labelNode.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");
        var valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        var box = new VBox(2, labelNode, valueNode);
        return box;
    }

    private void addFWHMVisualization(LineChart<String, Number> chart, SpectralLineAnalysis.LineStatistics stats, List<SpectrumAnalyzer.DataPoint> dataPoints) {
        if (stats == null || dataPoints.isEmpty()) {
            return;
        }

        // Get the first and last data point labels for spanning horizontal lines
        String firstLabel = formatWavelength(dataPoints.getFirst().pixelShift(), dataPoints.getFirst().wavelen());
        String lastLabel = formatWavelength(dataPoints.getLast().pixelShift(), dataPoints.getLast().wavelen());

        // Add continuum (baseline) horizontal line - green dashed
        var continuumSeries = new XYChart.Series<String, Number>();
        continuumSeries.setName(message("line.continuum"));
        continuumSeries.getData().add(new XYChart.Data<>(firstLabel, stats.continuum()));
        continuumSeries.getData().add(new XYChart.Data<>(lastLabel, stats.continuum()));
        chart.getData().add(continuumSeries);

        // Add line minimum horizontal line - blue dashed
        var minSeries = new XYChart.Series<String, Number>();
        minSeries.setName(message("line.center"));
        minSeries.getData().add(new XYChart.Data<>(firstLabel, stats.lineMinIntensity()));
        minSeries.getData().add(new XYChart.Data<>(lastLabel, stats.lineMinIntensity()));
        chart.getData().add(minSeries);

        // Add half-max horizontal line (FWHM) if data is available - red solid
        XYChart.Series<String, Number> halfMaxSeries = null;
        String blueLabel = null;
        String redLabel = null;
        double halfMaxIntensity = stats.halfMaxIntensity();

        if (stats.hasFWHMData()) {
            // Use direct crossing measurements
            double blueWl = stats.blueHalfMaxWavelength();
            double redWl = stats.redHalfMaxWavelength();

            double minBlueDist = Double.MAX_VALUE;
            double minRedDist = Double.MAX_VALUE;

            for (var dp : dataPoints) {
                double wl = dp.wavelen().angstroms();
                double blueDist = Math.abs(wl - blueWl);
                double redDist = Math.abs(wl - redWl);

                if (blueDist < minBlueDist) {
                    minBlueDist = blueDist;
                    blueLabel = formatWavelength(dp.pixelShift(), dp.wavelen());
                }
                if (redDist < minRedDist) {
                    minRedDist = redDist;
                    redLabel = formatWavelength(dp.pixelShift(), dp.wavelen());
                }
            }
        }

        if (blueLabel != null && redLabel != null) {
            halfMaxSeries = new XYChart.Series<>();
            halfMaxSeries.setName("FWHM");
            halfMaxSeries.getData().add(new XYChart.Data<>(blueLabel, halfMaxIntensity));
            halfMaxSeries.getData().add(new XYChart.Data<>(redLabel, halfMaxIntensity));
            chart.getData().add(halfMaxSeries);
        }

        // Apply styling after chart is rendered
        final var finalHalfMaxSeries = halfMaxSeries;
        Platform.runLater(() -> {
            // Style continuum line - green dashed
            if (continuumSeries.getNode() != null) {
                continuumSeries.getNode().setStyle("-fx-stroke: #27ae60; -fx-stroke-width: 2px; -fx-stroke-dash-array: 8 4;");
            }
            for (var data : continuumSeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-background-color: #27ae60, white; -fx-background-insets: 0, 2; -fx-background-radius: 5px; -fx-padding: 5px;");
                }
            }

            // Style line minimum - blue dashed
            if (minSeries.getNode() != null) {
                minSeries.getNode().setStyle("-fx-stroke: #3498db; -fx-stroke-width: 2px; -fx-stroke-dash-array: 8 4;");
            }
            for (var data : minSeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setStyle("-fx-background-color: #3498db, white; -fx-background-insets: 0, 2; -fx-background-radius: 5px; -fx-padding: 5px;");
                }
            }

            // Style FWHM line - purple solid with larger markers
            if (finalHalfMaxSeries != null) {
                if (finalHalfMaxSeries.getNode() != null) {
                    finalHalfMaxSeries.getNode().setStyle("-fx-stroke: #9b59b6; -fx-stroke-width: 3px;");
                }
                for (var data : finalHalfMaxSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setStyle("-fx-background-color: #9b59b6, white; -fx-background-insets: 0, 2; -fx-background-radius: 7px; -fx-padding: 7px;");
                    }
                }
            }

            // Style legend symbols to match line colors
            for (var node : chart.lookupAll(".chart-legend-item-symbol")) {
                for (var styleClass : node.getStyleClass()) {
                    if (styleClass.startsWith("series")) {
                        int seriesIndex = Integer.parseInt(styleClass.substring(6));
                        int dataSeriesCount = chart.getData().size();
                        // The stats series are added after the data series
                        // Continuum is at dataSeriesCount - 3 (or -2 if no FWHM)
                        // Min is at dataSeriesCount - 2 (or -1 if no FWHM)
                        // FWHM is at dataSeriesCount - 1
                        if (finalHalfMaxSeries != null) {
                            if (seriesIndex == dataSeriesCount - 3) {
                                node.setStyle("-fx-background-color: #27ae60;");
                            } else if (seriesIndex == dataSeriesCount - 2) {
                                node.setStyle("-fx-background-color: #3498db;");
                            } else if (seriesIndex == dataSeriesCount - 1) {
                                node.setStyle("-fx-background-color: #9b59b6;");
                            }
                        } else {
                            if (seriesIndex == dataSeriesCount - 2) {
                                node.setStyle("-fx-background-color: #27ae60;");
                            } else if (seriesIndex == dataSeriesCount - 1) {
                                node.setStyle("-fx-background-color: #3498db;");
                            }
                        }
                        break;
                    }
                }
            }

            addLegendToggleHandlers(chart);
        });
    }

    private static void addLegendToggleHandlers(XYChart<?, ?> chart) {
        for (var node : chart.lookupAll(".chart-legend-item")) {
            if (node instanceof Label legendLabel) {
                legendLabel.setCursor(Cursor.HAND);
                var seriesName = legendLabel.getText();
                legendLabel.setOnMouseClicked(event -> {
                    for (var s : chart.getData()) {
                        if (s.getName().equals(seriesName)) {
                            toggleSeriesVisibility(s, legendLabel);
                            break;
                        }
                    }
                });
            }
        }
    }

    private static void toggleSeriesVisibility(XYChart.Series<?, ?> series, Label legendLabel) {
        var node = series.getNode();
        if (node != null) {
            boolean visible = !node.isVisible();
            node.setVisible(visible);
            for (var data : series.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setVisible(visible);
                }
            }
            legendLabel.setOpacity(visible ? 1.0 : 0.4);
        }
    }

    private LineChart<String, Number> createProfileChart(
            ProcessParams params,
            AverageImageComputedEvent.AverageImage payload,
            PixelShiftRange range,
            NormalizedDataPoints normalizedDataPoints,
            List<SpectrumAnalyzer.DataPoint> referenceDataPoints,
            NormalizedDataPoints normalizedFrameDataPoints,
            NormalizedDataPoints normalizedLineDataPoints,
            SpectralLineAnalysis.LineStatistics stats,
            boolean canDrawReference,
            Dispersion dispersion,
            Wavelen lambda0) {

        var xAxis = new CategoryAxis();
        var yAxis = new NumberAxis();
        xAxis.setLabel(message("wavelength"));
        yAxis.setLabel(message("intensity"));
        var chart = new LineChart<>(xAxis, yAxis);

        // Main intensity series
        var series = new XYChart.Series<String, Number>();
        var binning = params.observationDetails().binning();
        var pixelSize = params.observationDetails().pixelSize();
        series.setName(formatLegend(params.observationDetails().instrument(), lambda0, binning, pixelSize));
        chart.getData().add(series);

        for (var dataPoint : normalizedDataPoints.dataPoints()) {
            addDataPointToSeries(dataPoint, series);
        }

        // Reference intensity series
        if (canDrawReference && referenceDataPoints != null) {
            var referenceSeries = new XYChart.Series<String, Number>();
            referenceSeries.setName(message("reference.intensity"));
            chart.getData().add(referenceSeries);
            for (var dataPoint : referenceDataPoints) {
                addDataPointToSeries(dataPoint, referenceSeries);
            }
        }

        // Frame intensity series (if available)
        if (normalizedFrameDataPoints != null) {
            var frameSeries = new XYChart.Series<String, Number>();
            frameSeries.setName(message("frame.intensity"));
            chart.getData().add(frameSeries);
            for (var dataPoint : normalizedFrameDataPoints.dataPoints()) {
                addDataPointToSeries(dataPoint, frameSeries);
            }
        }

        // Line intensity series (if available)
        if (normalizedLineDataPoints != null && currentColumn != null) {
            var lineSeries = new XYChart.Series<String, Number>();
            String lineLabel;
            if (canDrawReference) {
                var columnPixelShift = payload.polynomial().applyAsDouble(currentColumn);
                var wavelength = computeWavelength(columnPixelShift, lambda0, dispersion);
                lineLabel = message("line.intensity") + " (" + String.format(Locale.US, "%.2f Å", wavelength.angstroms()) + ")";
            } else {
                lineLabel = message("line.intensity") + " (" + currentColumn + ")";
            }
            lineSeries.setName(lineLabel);
            chart.getData().add(lineSeries);
            for (var dataPoint : normalizedLineDataPoints.dataPoints()) {
                addDataPointToSeries(dataPoint, lineSeries);
            }
        }

        // Add FWHM visualization
        addFWHMVisualization(chart, stats, normalizedDataPoints.dataPoints());

        return chart;
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
                var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, newParams, LocalDateTime.now(), false, Configuration.getInstance().getMemoryRestrictionMultiplier(), rootOperation);
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
                solexVideoProcessor.setCachedEllipse(mainEllipse);
                solexVideoProcessor.process();
            }));
            menu.show(node, event.getScreenX(), event.getScreenY());
        });
    }

    private <T extends XYChart<?, ?>> void registerSaveChartAction(GraphData graphData) {
        var chart = graphData.chart();
        var chartFactory = graphData.graphFactory();
        var name = graphData.name();

        // For the main chart, snapshot parent if it's a profile (to include stats panel)
        Supplier<Node> nodeSupplier = () -> (graphData instanceof GraphData.ProfileData && chart.getParent() != null)
                ? chart.getParent()
                : chart;

        var menu = createChartContextMenu(graphData, name, nodeSupplier);

        var openInNewWindow = new MenuItem(message("open.in.new.window"));
        openInNewWindow.setOnAction(evt -> Platform.runLater(() -> {
            var newWindow = new Stage();
            newWindow.setTitle(message(message("profile")));

            var pane = new BorderPane();
            var newChart = chartFactory.get();
            pane.setCenter(newChart);

            if (graphData instanceof GraphData.ProfileData profileData && profileData.statistics() != null) {
                pane.setBottom(createStatisticsPanel(profileData.statistics(), profileData.hasWavelength()));
            }

            var popupMenu = createChartContextMenu(graphData, name, () -> pane);
            newChart.setOnContextMenuRequested(menuEvt -> popupMenu.show(newChart, menuEvt.getScreenX(), menuEvt.getScreenY()));

            var scene = new Scene(pane, 800, 600);
            newWindow.setScene(scene);
            newWindow.show();

            Platform.runLater(() -> addLegendToggleHandlers(newChart));
        }));

        menu.getItems().add(openInNewWindow);
        chart.setOnContextMenuRequested(menuEvent -> menu.show(chart, menuEvent.getScreenX(), menuEvent.getScreenY()));
    }

    private ContextMenu createChartContextMenu(GraphData graphData, String name, Supplier<Node> nodeToSnapshot) {
        var menu = new ContextMenu();

        var saveToFile = new MenuItem(message("save.to.file"));
        saveToFile.setOnAction(evt -> saveChartToFile(name, nodeToSnapshot.get()));
        menu.getItems().add(saveToFile);

        if (graphData instanceof GraphData.ProfileData profileData) {
            var exportToCsv = new MenuItem(message("export.to.csv"));
            exportToCsv.setOnAction(evt -> exportProfileToCsv(name, profileData));
            menu.getItems().add(exportToCsv);
        }

        return menu;
    }

    private void saveChartToFile(String name, Node node) {
        var snapshotParameters = new SnapshotParameters();
        snapshotParameters.setTransform(Transform.scale(2, 2));
        var writable = node.snapshot(snapshotParameters, null);
        try {
            var namingStrategy = createNamingStrategy();
            var outputFile = outputDirectory.resolve(namingStrategy.render(0, null, Constants.TYPE_DEBUG, name, baseName, null) + ".png");
            var bufferedImage = SwingFXUtils.fromFXImage(writable, null);
            createDirectoriesIfNeeded(outputFile.getParent());
            ImageIO.write(bufferedImage, "png", outputFile.toFile());
            LOGGER.info(message("chart.saved"), outputFile);
            showFileSavedAlert(message("chart.saved.title"), "Chart saved to: " + outputFile);
        } catch (IOException ex) {
            throw new ProcessingException(ex);
        }
    }

    private void exportProfileToCsv(String name, GraphData.ProfileData profileData) {
        var namingStrategy = createNamingStrategy();
        var outputFile = outputDirectory.resolve(namingStrategy.render(0, null, Constants.TYPE_DEBUG, name, baseName, null) + ".csv");
        try {
            createDirectoriesIfNeeded(outputFile.getParent());
            try (var writer = new PrintWriter(new FileWriter(outputFile.toFile(), StandardCharsets.UTF_8))) {
                profileData.csvWriter().accept(writer);
                LOGGER.info(message("csv.saved"), outputFile);
                showFileSavedAlert(message("csv.saved.title"), "CSV saved to: " + outputFile);
            }
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static void showFileSavedAlert(String title, String message) {
        var alert = AlertFactory.info();
        alert.setTitle(title);
        var textArea = new TextArea(message);
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
    }

    private static String formatWavelength(double pixelShift, Wavelen wavelength) {
        if (wavelength.angstroms() > 0) {
            return String.format(Locale.US, "%.2f", wavelength.angstroms());
        }
        return String.format(Locale.US, "%.2fpx", pixelShift);
    }

    private static Wavelen computeWavelength(double pixelShift, Wavelen lambda, Dispersion dispersion) {
        if (dispersion == null) {
            return lambda;
        }
        return lambda.plus(pixelShift, dispersion);
    }

    private static String formatLegend(SpectroHeliograph instrument, Wavelen lambda0, Integer binning, Double pixelSize) {
        if (binning != null && pixelSize != null && lambda0 != null) {
            double pixSize = pixelSize;
            if (lambda0.angstroms() > 0 && pixSize > 0) {
                double disp = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, pixelSize * binning).angstromsPerPixel();
                return String.format(message("intensity.legend"), pixelSize, binning, disp);
            }
        }
        return message("intensity");
    }

    private static NormalizedDataPoints normalizeDatapoints(List<SpectrumAnalyzer.DataPoint> dataPoints, Double maxIntensity) {
        double maxSeriesIntensity = dataPoints.stream().mapToDouble(SpectrumAnalyzer.DataPoint::intensity).max().orElse(0);
        double maxRef = maxIntensity != null ? maxIntensity : maxSeriesIntensity;
        return new NormalizedDataPoints(dataPoints.stream()
                .map(dataPoint -> new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 100 * dataPoint.intensity() / maxRef))
                .toList(),
                maxSeriesIntensity);
    }

    private record CachedHistogram(Histogram histogram, String color) {
    }

    private static Point2D applyRotation(Point2D point, Point2D center, double angle) {
        var dx = point.x() - center.x();
        var dy = point.y() - center.y();
        var cos = Math.cos(angle);
        var sin = Math.sin(angle);
        return new Point2D(
                center.x() + dx * cos - dy * sin,
                center.y() + dx * sin + dy * cos
        );
    }

    private static boolean isWithinImageBounds(Point2D topLeft, Point2D bottomRight, ImageWrapper image) {
        return topLeft.x() >= 0 && topLeft.x() < image.width() &&
                topLeft.y() >= 0 && topLeft.y() < image.height() &&
                bottomRight.x() >= 0 && bottomRight.x() < image.width() &&
                bottomRight.y() >= 0 && bottomRight.y() < image.height();
    }

    private record NormalizedDataPoints(List<SpectrumAnalyzer.DataPoint> dataPoints, double maxIntensity) {

    }

    @Override
    public void onScriptExecutionResult(ScriptExecutionResultEvent e) {
        var images = e.getPayload().imagesByLabel();
        for (Map.Entry<String, ImageWrapper> entry : images.entrySet()) {
            scriptImagesByLabel.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>())
                    .add(entry.getValue());
        }
    }

    private boolean hasBatchScriptExpressions() {
        var scriptFiles = adjustedParams.combinedImageMathParams().scriptFiles();
        if (scriptFiles.isEmpty()) {
            return false;
        }
        return scriptFiles.stream().anyMatch(file -> {
            try {
                return Files.readString(file.toPath()).contains("[[batch]]");
            } catch (IOException e) {
                return false;
            }
        });
    }

    /**
     * In case we're in a single file processing (serFile != null) but have batch script expressions,
     * execute them now. We would have collected images during single file processing and will create
     * a list which only contains one image per label.
     */
    private void executeSingleFileBatchScripts() {
        try {
            var scriptFiles = adjustedParams.combinedImageMathParams().scriptFiles();
            owner.prepareForScriptExecution(this, params, rootOperation, ImageMathScriptExecutor.SectionKind.BATCH);
            if (scriptFiles.isEmpty()) {
                return;
            }

            var namingStrategy = createNamingStrategy();
            var ctx = new HashMap<>(scriptExecutionContext);
            ctx.put(ImageEmitter.class, imageEmitter);

            var batchScriptExecutor = new JSolExScriptExecutor(
                    idx -> {
                        throw new IllegalStateException("Cannot call img() in batch outputs. Use variables to store images instead");
                    },
                    ctx,
                    this,
                    null
            );

            for (Map.Entry<String, List<ImageWrapper>> entry : scriptImagesByLabel.entrySet()) {
                batchScriptExecutor.putVariable(entry.getKey(), entry.getValue());
            }

            var parameterValues = adjustedParams.combinedImageMathParams().parameterValues();
            for (File scriptFile : scriptFiles) {
                var fileParams = parameterValues.get(scriptFile);
                if (fileParams != null) {
                    for (var entry : fileParams.entrySet()) {
                        batchScriptExecutor.putVariable(entry.getKey(), entry.getValue());
                    }
                }
                executeSingleFileBatchScript(namingStrategy, batchScriptExecutor, scriptFile);
            }
        } catch (Exception e) {
            LOGGER.error(JSolEx.message("error.batch.scripts.single.file"), e);
        }
    }

    private void executeSingleFileBatchScript(FileNamingStrategy namingStrategy, ImageMathScriptExecutor batchScriptExecutor, File scriptFile) {
        owner.updateProgress(0, String.format(message("executing.script"), scriptFile));
        ImageMathScriptResult result;
        try {
            result = batchScriptExecutor.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.BATCH);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        try {
            processScriptErrors(result);
            var outputsMetadata = extractOutputsMetadata(scriptFile);
            renderSingleFileBatchOutputs(namingStrategy, result, outputsMetadata);
        } finally {
            owner.updateProgress(1, String.format(message("executing.script"), scriptFile));
        }
    }

    private void renderSingleFileBatchOutputs(FileNamingStrategy namingStrategy, ImageMathScriptResult result, Map<String, OutputMetadata> outputsMetadata) {
        if (result.imagesByLabel().isEmpty() && result.filesByLabel().isEmpty()) {
            return;
        }
        var language = LocaleUtils.getConfiguredLocale().getLanguage();
        Platform.runLater(() -> {
            var tabPane = owner.getTabs();
            var imagesViewerTab = owner.getImagesViewerTab();
            tabPane.getTabs().add(imagesViewerTab);
            tabPane.getSelectionModel().select(imagesViewerTab);
        });
        result.imagesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var label = entry.getKey();
            var metadata = outputsMetadata.get(label);
            var displayTitle = metadata != null ? metadata.getDisplayTitle(language) : null;
            var description = metadata != null ? metadata.getDisplayDescription(language) : null;
            var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, label, "batch", entry.getValue());
            var outputFile = new File(outputDirectory.toFile(), name);
            onImageGenerated(new ImageGeneratedEvent(
                    new GeneratedImage(GeneratedImageKind.IMAGE_MATH, label, outputFile.toPath(), entry.getValue(), description, displayTitle)
            ));
        });
        result.filesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var label = entry.getKey();
            var metadata = outputsMetadata.get(label);
            var displayTitle = metadata != null ? metadata.getDisplayTitle(language) : null;
            var description = metadata != null ? metadata.getDisplayDescription(language) : null;
            var fileOutput = entry.getValue();
            var baseName = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, label, "batch", null);
            try {
                var displayPath = FilesUtils.saveAllFilesAndGetDisplayPath(fileOutput, outputDirectory, baseName);
                // Only fire display event for the designated display file
                if (displayPath != null) {
                    onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, label, displayPath, description, displayTitle));
                }
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        });
    }

    private void processScriptErrors(ImageMathScriptResult result) {
        var invalidExpressions = result.invalidExpressions();
        if (!invalidExpressions.isEmpty()) {
            Platform.runLater(() -> ScriptErrorDialog.showErrors(invalidExpressions));
        }
    }

    /**
     * Checks if a SER video file is associated with this processing session.
     *
     * @return true if a SER file is being processed, false otherwise
     */
    public boolean hasSerFile() {
        return serFile != null;
    }

    sealed interface GraphData {

        XYChart<?, ?> chart();

        Supplier<? extends XYChart<?, ?>> graphFactory();

        String name();

        record ProfileData(XYChart<?, ?> chart, Supplier<? extends XYChart<?, ?>> graphFactory,
                           Consumer<? super PrintWriter> csvWriter,
                           SpectralLineAnalysis.LineStatistics statistics,
                           boolean hasWavelength) implements GraphData {
            public String name() {
                return "profile";
            }
        }

        record HistogramData(XYChart<?, ?> chart, Supplier<? extends XYChart<?, ?>> graphFactory) implements GraphData {
            public String name() {
                return "histogram";
            }
        }
    }

}
