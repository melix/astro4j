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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.CustomAnimationCreator;
import me.champeau.a4j.jsolex.app.jfx.DifferentialRotationConfigDialog;
import me.champeau.a4j.jsolex.app.jfx.DifferentialVelocityHelpOverlay;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.OpenGLAvailability;
import me.champeau.a4j.jsolex.app.jfx.ReconstructionView;
import me.champeau.a4j.jsolex.app.jfx.RectangleSelectionListener;
import me.champeau.a4j.jsolex.app.jfx.ScriptErrorDialog;
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
import me.champeau.a4j.jsolex.processing.expr.ScriptExecutionContext;
import me.champeau.a4j.jsolex.processing.expr.ShiftCollectingImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.impl.Animate;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.OutputMetadata;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineAnalysis;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.SpectralLinePolynomial;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.DurationFormatter;
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
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.SerFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.jfx.BatchOperations.blockingUntilResultAvailable;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newModalStage;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.computeSerFileBasename;

/**
 * Event listener for single mode processing that handles processing events,
 * image generation, and user interface updates during Sol'Ex video file processing.
 * This listener manages the visualization of processed images, histograms, spectral profiles,
 * and provides script execution capabilities through the ImageMath language.
 */
public class SingleModeProcessingEventListener implements ProcessingEventListener, ImageMathScriptExecutor, Broadcaster, Spectral3DVisualizationHelper.DataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleModeProcessingEventListener.class);
    private static final String[] RGB_COLORS = {"red", "green", "blue"};
    private static final int BINS = 256;
    private static final double SPEED_OF_LIGHT_KM_S = 299792.458;
    private static final double SOLAR_RADIUS_KM = 696000.0;
    // Snodgrass & Ulrich (1990) differential rotation coefficients
    // ω(φ) = A + B·sin²(φ) + C·sin⁴(φ) deg/day
    private static final double SNODGRASS_A = 14.713;
    private static final double SNODGRASS_B = -2.396;
    private static final double SNODGRASS_C = -1.787;

    /**
     * Minimum heliographic latitude (degrees) for the rotation profile scan.
     */
    private static final double ROTATION_PROFILE_MIN_LAT_DEG = -60;
    /**
     * Maximum heliographic latitude (degrees) for the rotation profile scan.
     */
    private static final double ROTATION_PROFILE_MAX_LAT_DEG = 60;
    /**
     * Maximum plausible measured velocity (km/s). Points exceeding this
     * threshold are discarded as outliers (e.g. failed Voigt fits).
     */
    private static final double ROTATION_PROFILE_MAX_VELOCITY_KM_S = 10.0;
    /**
     * Number of pixel columns averaged on each side of the target column when
     * extracting a spectral profile from the reconstructed image. A radius of 2
     * means 5 columns are averaged (target ± 2), which reduces noise.
     */
    private static final int ROTATION_PROFILE_COLUMN_AVERAGING_RADIUS = 4;

    // Voigt fit is more robust for rotation profile measurement
    private static final DopplerMeasurementMethod ROTATION_PROFILE_DOPPLER_METHOD = DopplerMeasurementMethod.VOIGT_FIT;

    // Record to hold velocity measurement with its position data for weighted averaging
    private record VelocityMeasurement(double velocity, double longitudeFraction) {}

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
    private final Tab analysisTab;
    private final WeakHashMap<ImageWrapper, List<CachedHistogram>> cachedHistograms = new WeakHashMap<>();
    private final LocalDateTime processingDate;
    private float[][] averageImage;

    private ProcessParams params;
    private ProcessParams adjustedParams;
    private Header header;
    private PixelShiftRange pixelShiftRange;
    private DoubleUnaryOperator polynomial;
    private ScriptExecutionContext scriptExecutionContext;
    private ImageEmitter imageEmitter;
    private ImageMathScriptExecutor imageScriptExecutor;
    private final Map<String, Object> pendingVariables = new HashMap<>();
    private long sd;
    private final Map<PixelShift, ImageWrapper> shiftImages;
    private int width;
    private int height;
    private Ellipse mainEllipse;
    private ProgressOperation reconstructionProgress;

    private final AtomicInteger cropCount = new AtomicInteger();
    private final AtomicInteger animCount = new AtomicInteger();
    private final Map<String, List<ImageWrapper>> scriptImagesByLabel = new HashMap<>();
    private final Map<String, List<Object>> scriptValuesByLabel = new HashMap<>();

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
    private Button measureVelocityButton;
    private Button customMeasurementButton;
    private DifferentialRotationConfig differentialRotationConfig = DifferentialRotationConfig.defaultConfig();
    private final Spectral3DVisualizationHelper spectral3DHelper;

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
        this.analysisTab = owner.getAnalysisTab();
        this.metadataTab = owner.getMetadataTab();
        this.popupViews = popupViews;
        this.shiftImages = new HashMap<>();
        this.processingDate = processingDate;
        this.spectral3DHelper = new Spectral3DVisualizationHelper(this);
        imageViews = new HashMap<>();
        sd = 0;
        width = 0;
        height = 0;
    }

    // DataProvider interface implementation
    @Override
    public JSolExInterface getOwner() {
        return owner;
    }

    @Override
    public File getSerFile() {
        return serFile;
    }

    @Override
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public ProcessParams getParams() {
        return params;
    }

    @Override
    public ProcessParams getAdjustedParams() {
        return adjustedParams;
    }

    @Override
    public ProgressOperation getRootOperation() {
        return rootOperation;
    }

    @Override
    public Map<PixelShift, ImageWrapper> getShiftImages() {
        return shiftImages;
    }

    @Override
    public DoubleUnaryOperator getPolynomial() {
        return polynomial;
    }

    @Override
    public float[][] getAverageImage() {
        return averageImage;
    }

    @Override
    public Ellipse getMainEllipse() {
        return mainEllipse;
    }

    @Override
    public AverageImageComputedEvent.AverageImage getCachedAverageImagePayload() {
        return cachedAverageImagePayload;
    }

    @Override
    public TrimmingParameters getCachedTrimmingParameters() {
        return cachedTrimmingParameters;
    }

    @Override
    public PixelShiftRange getPixelShiftRange() {
        return pixelShiftRange;
    }

    @Override
    public RedshiftImagesProcessor createRedshiftProcessor() {
        return new RedshiftImagesProcessor(
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
        var y = payload.line();
        var totalLines = payload.totalLines();
        if (reconstructionProgress == null) {
            reconstructionProgress = rootOperation.createChild(message("reconstructing"));
        }
        // Use totalLines from the payload, not the height field which can be overwritten
        // by geometry correction events during reconstruction
        var progress = totalLines > 0 ? (y + 1d) / totalLines : 0;
        onProgress(ProgressEvent.of(reconstructionProgress.update(progress, message("reconstructing"))));

        if (!payload.display()) {
            return;
        }

        var reconstructionView = getOrCreateImageView(event);
        var imageView = reconstructionView.getSolarView();
        imageView.resetZoom();
        var image = (WritableImage) imageView.getImage();
        var line = payload.data();
        var rgb = reconstructionView.getSolarImageData();

        // Update the current row in the rgb buffer.
        for (var x = 0; x < line.length; x++) {
            var v = (int) Math.round(line[x]);
            var c = (byte) (v >> 8);
            var offset = 3 * (y * width + x);
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
        var spectrumImage = (WritableImage) spectrumView.getImage();

        var currentTime = System.currentTimeMillis();
        var lastUpdate = lastUIUpdateTime.get();

        if (currentTime - lastUpdate >= MIN_UI_UPDATE_INTERVAL_MS) {
            if (lastUIUpdateTime.compareAndSet(lastUpdate, currentTime)) {
                var lock = reconstructionView.getLock();
                if (lock.tryAcquire()) {
                    Thread.startVirtualThread(() -> {
                        var spectrumBuffer = SpectrumImageConverter.convertSpectrumImage(spectrum);
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
                            var imageWidth = (int) solarImage.getWidth();
                            var imageHeight = (int) solarImage.getHeight();
                            var expectedSize = 3 * imageWidth * imageHeight;
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
        if (reconstructionProgress != null) {
            onProgress(ProgressEvent.of(reconstructionProgress.complete()));
        }
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
                    var imageBytes = SpectrumImageConverter.convertSpectrumImage(spectrum);
                    view.setSpectrumImage(spectrum);
                    Platform.runLater(() -> {
                        var pixelWriter = ((WritableImage) view.getSpectrumView().getImage()).getPixelWriter();
                        pixelWriter.setPixels(0, 0, geometry.width(), geometry.height(), pixelformat, imageBytes, 0, 3 * geometry.width());
                    });
                    if (polynomial != null) {
                        // Store the clicked column and update the profile tab (detailed mode)
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
                var spectral4DViewer = spectral3DHelper.getSpectral4DViewer();
                LOGGER.info("Checking 4D viewer sync: spectral4DViewer={}", spectral4DViewer);
                if (spectral4DViewer != null) {
                    var clickedFrame = (int) Math.round(frameNb);
                    var clickedSlit = xIndex;
                    double clickedPixelShift = pixelShift;
                    LOGGER.info("Calling setPositionFromClick({}, {}, {})", clickedFrame, clickedSlit, clickedPixelShift);
                    Platform.runLater(() -> {
                        spectral4DViewer.setPositionFromClick(clickedFrame, clickedSlit, clickedPixelShift);
                        spectral4DViewer.bringToFront();
                    });
                }
            });
        });
    }

    private void stretchReconstructionView(ZoomableImageView solarView) {
        var image = (WritableImage) solarView.getImage();
        var pixelReader = image.getPixelReader();
        BackgroundOperations.async(() -> {
            try {
                var pixels = new int[width * height];
                pixelReader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), pixels, 0, width);

                // find max value
                var max = 0;
                for (var pixel : pixels) {
                    var v = pixel & 0xFF;
                    if (v > max) {
                        max = v;
                    }
                }

                if (max > 0) {
                    for (var i = 0; i < pixels.length; i++) {
                        var v = (255 * (pixels[i] & 0xFF)) / max;
                        pixels[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
                    }
                }

                Platform.runLater(() -> image.getPixelWriter().setPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), pixels, 0, width));
            } catch (IndexOutOfBoundsException e) {
            }
        });
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
            var imageWidth = imageWrapper.width();
            var imageHeight = imageWrapper.height();
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
                            var xx = Math.max(0, (int) orig.x() - width / 2);
                            var yy = Math.max(0, (int) orig.y() - height / 2);
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

                                var totalFrames = header.frameCount();
                                var minY = Math.min(topLeft.y(), bottomRight.y());
                                var maxY = Math.max(topLeft.y(), bottomRight.y());
                                var minX = Math.min(topLeft.x(), bottomRight.x());
                                var maxX = Math.max(topLeft.x(), bottomRight.x());

                                var startFrame = Math.max(0, (int) Math.round(minY));
                                var endFrame = Math.min(totalFrames - 1, (int) Math.round(maxY));
                                var frameCount = Math.max(1, endFrame - startFrame + 1);
                                var cropLeft = Math.max(0, (int) Math.round(minX));
                                var cropRight = Math.min(originalWidth - 1, (int) Math.round(maxX));

                                if (cropLeft > cropRight) {
                                    var temp = cropLeft;
                                    cropLeft = cropRight;
                                    cropRight = temp;
                                }

                                var cropWidth = Math.max(1, cropRight - cropLeft + 1);

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
                                var totalFramesToExtract = endFrame - startFrame + 1;
                                var extractionOp = rootOperation.createChild("Extracting frames");

                                // Extract frames from the selected frame range with width-only cropping (preserve full height)
                                for (var frameIndex = startFrame; frameIndex <= endFrame; frameIndex++) {
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

                                    // Update progress every 10 frames
                                    if ((frameIndex - startFrame) % 10 == 0) {
                                        var currentFrame = frameIndex - startFrame + 1;
                                        broadcast(extractionOp.update(currentFrame / (double) totalFramesToExtract, "Extracting frame " + currentFrame + "/" + totalFramesToExtract));
                                    }
                                }
                                broadcast(extractionOp.complete());

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


    private void generateRotationProfileChart(AverageImageComputedEvent.AverageImage payload,
                                               ReferenceCoords refCoords, Ellipse ellipse, SolarParameters solarParams) {
        var params = payload.adjustedParams();
        var lambda0 = params.spectrumParams().ray().wavelength();
        var binning = params.observationDetails().binning();
        var pixelSize = params.observationDetails().pixelSize();
        var canCalibrate = binning != null && pixelSize != null && lambda0.nanos() > 0 && pixelSize > 0 && binning > 0;

        if (!canCalibrate) {
            LOGGER.warn("Cannot generate rotation profile: wavelength calibration not available");
            return;
        }

        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(params.observationDetails().instrument(), lambda0, pixelSize * binning);

        var centerX = ellipse.center().a();
        var centerY = ellipse.center().b();
        var radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
        var b0 = solarParams.b0();
        var angleP = solarParams.p();

        LOGGER.info("=== AUTO ROTATION PROFILE ===");
        LOGGER.info("Lambda0: {} Å, Dispersion: {} Å/pixel", lambda0.angstroms(), dispersion.angstromsPerPixel());

        var polynomial = payload.polynomial();
        var start = payload.leftBorder();
        var end = payload.rightBorder();
        var height = payload.image().height();
        var range = PixelShiftRange.computePixelShiftRange(start, end, height, polynomial);

        // Check for HFLIP/VFLIP to determine actual image orientation
        var hasHFlip = refCoords.operations().stream()
            .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.HFLIP);
        var hasVFlip = refCoords.operations().stream()
            .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.VFLIP);

        // Longitude sign convention: East limb = negative longitude, West limb = positive
        // After LEFT_ROTATION without HFLIP: orientation is mirrored, so signs swap
        var eastLonSign = hasHFlip ? -1 : 1;
        // Latitude sign: VFLIP swaps north/south, so we need to negate latitude
        var latSign = hasVFlip ? -1 : 1;

        // Show progress dialog
        var progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        var progressLabel = new Label(message("doppler.computing.rotation.profile"));
        var progressBox = new VBox(10, progressLabel, progressBar);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(20));
        var progressStage = new Stage();
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(message("doppler.rotation.profile"));
        progressStage.setScene(JSolEx.newScene(progressBox, 350, 100));
        progressStage.setResizable(false);
        progressStage.show();

        // Run computation on a background thread
        var config = differentialRotationConfig;
        Thread.startVirtualThread(() -> {
            var velocityData = computeRotationProfile(
                refCoords, range, polynomial, start, end, height,
                lambda0, dispersion, centerX, centerY, radius, b0, angleP, eastLonSign, latSign,
                config,
                progress -> Platform.runLater(() -> progressBar.setProgress(progress))
            );

            Platform.runLater(() -> {
                progressStage.close();
                if (!velocityData.isEmpty()) {
                    showRotationProfileChart(velocityData, params);
                }
            });
        });
    }

    private List<double[]> computeRotationProfile(ReferenceCoords refCoords,
                                                   PixelShiftRange range, DoubleUnaryOperator polynomial,
                                                   int start, int end, int height,
                                                   Wavelen lambda0, Dispersion dispersion,
                                                   double centerX, double centerY, double radius,
                                                   double b0, double angleP, int eastLonSign, int latSign,
                                                   DifferentialRotationConfig config,
                                                   DoubleConsumer progressCallback) {
        var velocityData = new ArrayList<double[]>();
        int totalPoints = 0;

        var limbLongitude = config.limbLongitudeDeg();
        var longitudeHalfRange = config.longitudeHalfRangeDeg();
        var longitudeStep = config.longitudeStepDeg();
        var latitudeStep = config.latitudeStepDeg();

        var lonMin = limbLongitude - longitudeHalfRange;
        var lonMax = limbLongitude + longitudeHalfRange;
        var totalLatSteps = (int) ((ROTATION_PROFILE_MAX_LAT_DEG - ROTATION_PROFILE_MIN_LAT_DEG) / latitudeStep) + 1;
        var measurementsByLat = new TreeMap<Integer, List<VelocityMeasurement>>();

        var measurement = ROTATION_PROFILE_DOPPLER_METHOD.createMeasurement(config.voigtFitHalfWidthAngstroms());
        try (var reader = SerFileReader.of(serFile)) {
            var totalFrames = reader.header().frameCount();

            for (double latDeg = ROTATION_PROFILE_MIN_LAT_DEG; latDeg <= ROTATION_PROFILE_MAX_LAT_DEG; latDeg += latitudeStep) {
                totalPoints++;
                progressCallback.accept((double) totalPoints / totalLatSteps);
                // Apply latSign to account for VFLIP: if image is vertically flipped, north/south are swapped
                var effectiveLatDeg = latSign * latDeg;
                var latRad = Math.toRadians(effectiveLatDeg);
                var colatitude = Math.PI / 2 - latRad;

                for (double lonDeg = lonMin; lonDeg <= lonMax; lonDeg += longitudeStep) {
                    var eastLonRad = Math.toRadians(eastLonSign * lonDeg);
                    var westLonRad = Math.toRadians(-eastLonSign * lonDeg);

                    var eastCoords = computeSphereCoords(eastLonRad, colatitude, radius, b0, angleP);
                    var eastImgX = (int) Math.round(centerX + eastCoords[0]);
                    var eastImgY = (int) Math.round(centerY + eastCoords[1]);

                    var westCoords = computeSphereCoords(westLonRad, colatitude, radius, b0, angleP);
                    var westImgX = (int) Math.round(centerX + westCoords[0]);
                    var westImgY = (int) Math.round(centerY + westCoords[1]);

                    var eastOrig = refCoords.determineOriginalCoordinates(new Point2D(eastImgX, eastImgY), ReferenceCoords.NO_LIMIT);
                    var westOrig = refCoords.determineOriginalCoordinates(new Point2D(westImgX, westImgY), ReferenceCoords.NO_LIMIT);

                    var eastColumn = (int) Math.round(eastOrig.x());
                    var eastFrame = (int) Math.round(eastOrig.y());
                    var westColumn = (int) Math.round(westOrig.x());
                    var westFrame = (int) Math.round(westOrig.y());

                    if (eastFrame < 0 || eastFrame >= totalFrames || westFrame < 0 || westFrame >= totalFrames) {
                        continue;
                    }

                    var eastFrameData = readFrameData(reader, eastFrame);
                    if (eastFrameData == null) {
                        continue;
                    }

                    var westFrameData = readFrameData(reader, westFrame);
                    if (westFrameData == null) {
                        continue;
                    }

                    var eastProfile = extractPointProfile(eastColumn, eastFrameData, range, polynomial, lambda0, dispersion, true, ROTATION_PROFILE_COLUMN_AVERAGING_RADIUS);
                    var westProfile = extractPointProfile(westColumn, westFrameData, range, polynomial, lambda0, dispersion, true, ROTATION_PROFILE_COLUMN_AVERAGING_RADIUS);

                    if (eastProfile.isEmpty() || westProfile.isEmpty()) {
                        continue;
                    }

                    var eastCenter = measurement.measureLineCenter(eastProfile);
                    var westCenter = measurement.measureLineCenter(westProfile);
                    if (eastCenter.isEmpty() || westCenter.isEmpty()) {
                        continue;
                    }
                    var dopplerShiftAngstroms = westCenter.getAsDouble() - eastCenter.getAsDouble();

                    var measuredVelocity = (dopplerShiftAngstroms / lambda0.angstroms()) * SPEED_OF_LIGHT_KM_S / 2.0;

                    var cosLat = Math.cos(latRad);
                    var sinLon = Math.sin(Math.toRadians(lonDeg));
                    var geometryFactor = cosLat * sinLon;
                    if (Math.abs(geometryFactor) > 0.1) {
                        var equatorialVelocity = Math.abs(measuredVelocity / geometryFactor);
                        if (equatorialVelocity < ROTATION_PROFILE_MAX_VELOCITY_KM_S) {
                            var latBin = (int) latDeg;
                            // longitudeFraction: 0 at limb (best accuracy), 1 at meridian (worst)
                            var longitudeFraction = 1.0 - Math.abs(lonDeg) / limbLongitude;
                            measurementsByLat.computeIfAbsent(latBin, k -> new ArrayList<>())
                                .add(new VelocityMeasurement(equatorialVelocity, longitudeFraction));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Error reading SER file for rotation profile", ex);
            return List.of();
        }

        var sampleRejection = config.sampleRejectionMethod();
        var noiseReduction = config.noiseReductionMethod();

        for (var entry : measurementsByLat.entrySet()) {
            var latBin = entry.getKey();
            var measurements = entry.getValue();
            var velocities = measurements.stream().map(VelocityMeasurement::velocity).toList();
            // For weighted average: weight = 1 - longitudeFraction (higher weight at limb)
            var weights = measurements.stream()
                .map(m -> 1.0 - m.longitudeFraction())
                .toList();
            // Apply sample rejection before aggregation
            var filtered = sampleRejection.filter(velocities, weights);
            var result = noiseReduction.aggregate(filtered.velocities(), filtered.weights());
            velocityData.add(new double[]{latBin, result.value(), result.error()});
        }

        if (velocityData.isEmpty()) {
            LOGGER.warn("No valid velocity measurements obtained");
            return List.of();
        }

        return applyLatitudeSmoothingFilter(velocityData, config.smoothingWindowDeg(), noiseReduction);
    }



    private static List<double[]> applyLatitudeSmoothingFilter(List<double[]> velocityData,
                                                                double windowDeg,
                                                                NoiseReductionMethod noiseReduction) {
        var filtered = new ArrayList<double[]>();
        var halfWindow = windowDeg / 2.0;
        for (int i = 0; i < velocityData.size(); i++) {
            var centerPoint = velocityData.get(i);
            var centerLat = centerPoint[0];
            var windowValues = new ArrayList<Double>();
            var windowErrors = new ArrayList<Double>();
            for (var point : velocityData) {
                if (Math.abs(point[0] - centerLat) <= halfWindow) {
                    windowValues.add(point[1]);
                    windowErrors.add(point[2]);
                }
            }
            if (windowValues.size() == 1) {
                // Single point: preserve original value and error
                filtered.add(new double[]{centerLat, centerPoint[1], centerPoint[2]});
            } else {
                // Compute smoothed value using the noise reduction method
                var result = noiseReduction.aggregate(windowValues, null);
                // Propagate errors properly instead of using spread-based error
                var propagatedError = propagateErrors(windowErrors, noiseReduction);
                filtered.add(new double[]{centerLat, result.value(), propagatedError});
            }
        }
        return filtered;
    }

    /**
     * Propagates measurement errors when combining multiple points.
     * For n measurements with errors σ₁, σ₂, ..., σₙ:
     * - MEDIAN: median(σᵢ) / √n (robust estimate)
     * - AVERAGE: √(Σσᵢ²) / n (standard error propagation)
     * - WEIGHTED_AVERAGE: same as AVERAGE (weights already applied in stage 1)
     */
    private static double propagateErrors(List<Double> errors, NoiseReductionMethod method) {
        int n = errors.size();
        if (n == 0) {
            return 0;
        }
        return switch (method) {
            case MEDIAN -> {
                var sorted = errors.stream().sorted().toList();
                var medianError = sorted.get(sorted.size() / 2);
                yield medianError / Math.sqrt(n);
            }
            case AVERAGE, WEIGHTED_AVERAGE -> {
                var sumSquares = errors.stream().mapToDouble(e -> e * e).sum();
                yield Math.sqrt(sumSquares) / n;
            }
        };
    }

    private float[][] readFrameData(SerFileReader reader, int frameNumber) {
        try {
            var geometry = reader.header().geometry();
            var converter = ImageUtils.createImageConverter(geometry.colorMode());
            reader.seekFrame(frameNumber);
            var currentFrame = reader.currentFrame().data();
            var buffer = converter.createBuffer(geometry);
            converter.convert(frameNumber, currentFrame, geometry, buffer);
            return new Image(geometry.width(), geometry.height(), buffer).data();
        } catch (Exception ex) {
            LOGGER.error("Error reading frame {}", frameNumber, ex);
            return null;
        }
    }

    private void showRotationProfileChart(List<double[]> velocityData, ProcessParams processParams) {
        var chartTitle = buildRotationProfileTitle(processParams);
        var fittedCoeffs = fitDifferentialRotationCoefficients(velocityData);
        var velocityChart = createSingleRotationChart(velocityData, false, fittedCoeffs);
        var angularChart = createSingleRotationChart(velocityData, true, fittedCoeffs);
        var layout = createDualRotationLayout(chartTitle, velocityChart, angularChart, fittedCoeffs);

        // Register save/export actions
        Consumer<? super PrintWriter> csvWriter = writer -> {
            writer.println("latitude;tangential_velocity;mad_tangential;theoretical_tangential;fit_tangential;measured_omega;mad_omega;theoretical_omega;fit_omega");
            for (var point : velocityData) {
                var latDeg = point[0];
                var vEq = point[1];
                var madEq = point[2];
                var cosLat = Math.cos(Math.toRadians(latDeg));
                var theoreticalVEq = snodgrassVelocity(latDeg);
                var fitVEq = fittedCoeffs.tangentialVelocity(latDeg) / cosLat; // Convert back to equatorial
                writer.printf(Locale.US, "%.2f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f%n",
                    latDeg, vEq * cosLat, madEq * cosLat, theoreticalVEq * cosLat, fittedCoeffs.tangentialVelocity(latDeg),
                    velocityToAngularVelocity(vEq), velocityToAngularVelocity(madEq),
                    snodgrassAngularVelocity(latDeg), fittedCoeffs.angularVelocity(latDeg));
            }
            // Add fitted coefficients as a comment at the end
            writer.printf(Locale.US, "%n# Fitted coefficients: A=%.4f, B=%.4f, C=%.4f%n", fittedCoeffs.a(), fittedCoeffs.b(), fittedCoeffs.c());
            writer.printf(Locale.US, "# Snodgrass & Ulrich (1990): A=%.4f, B=%.4f, C=%.4f%n", SNODGRASS_A, SNODGRASS_B, SNODGRASS_C);
        };
        Supplier<VBox> dualLayoutFactory = () -> {
            var vc = createSingleRotationChart(velocityData, false, fittedCoeffs);
            var ac = createSingleRotationChart(velocityData, true, fittedCoeffs);
            return createDualRotationLayout(chartTitle, vc, ac, fittedCoeffs);
        };
        registerSaveChartAction(new GraphData.DifferentialVelocityData(
            velocityChart, dualLayoutFactory, csvWriter));
        registerSaveChartAction(new GraphData.DifferentialVelocityData(
            angularChart, dualLayoutFactory, csvWriter));

        // Display in a new window
        var stage = new Stage();
        stage.setTitle(message("doppler.rotation.profile"));
        var scene = JSolEx.newScene(layout, 1400, 580);
        stage.setScene(scene);
        stage.setOnShown(e -> Platform.runLater(() -> {
            addErrorBarsToChart(velocityChart, velocityData, false);
            addErrorBarsToChart(angularChart, velocityData, true);
        }));
        stage.show();
    }

    private static String buildRotationProfileTitle(ProcessParams processParams) {
        var sb = new StringBuilder();
        var ray = processParams.spectrumParams().ray();
        sb.append(ray.label());
        if (ray.wavelength().angstroms() > 0) {
            sb.append(String.format(Locale.US, " - %.2f\u212B", ray.wavelength().angstroms()));
        }
        var obs = processParams.observationDetails();
        if (obs.date() != null) {
            var utcDate = obs.date().withZoneSameInstant(java.time.ZoneOffset.UTC);
            sb.append(" - ").append(utcDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")));
        }
        if (obs.instrument() != null && obs.instrument().label() != null && !obs.instrument().label().isBlank()) {
            sb.append(" - ").append(obs.instrument().label());
        }
        if (obs.observer() != null && !obs.observer().isBlank()) {
            sb.append(" - ").append(obs.observer());
        }
        return sb.toString();
    }

    private static VBox createDualRotationLayout(String title, LineChart<Number, Number> velocityChart, LineChart<Number, Number> angularChart, DifferentialRotationCoefficients fittedCoeffs) {
        var titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);

        // Display fitted coefficients compared to reference (Snodgrass & Ulrich 1990)
        var coeffsLabel = new Label(String.format(Locale.US,
            "Fitted: ω(φ) = %.3f %+.3f·sin²φ %+.3f·sin⁴φ  |  Snodgrass & Ulrich (1990): ω(φ) = %.3f %+.3f·sin²φ %+.3f·sin⁴φ  (deg/day)",
            fittedCoeffs.a(), fittedCoeffs.b(), fittedCoeffs.c(),
            SNODGRASS_A, SNODGRASS_B, SNODGRASS_C));
        coeffsLabel.setStyle("-fx-font-size: 12px; -fx-font-family: monospace;");
        coeffsLabel.setMaxWidth(Double.MAX_VALUE);
        coeffsLabel.setAlignment(Pos.CENTER);

        var hbox = new HBox(10, velocityChart, angularChart);
        HBox.setHgrow(velocityChart, Priority.ALWAYS);
        HBox.setHgrow(angularChart, Priority.ALWAYS);
        var vbox = new VBox(5, titleLabel, coeffsLabel, hbox);
        VBox.setVgrow(hbox, Priority.ALWAYS);
        return vbox;
    }

    private record ErrorBarParts(Line stem, Line topCap, Line bottomCap, double mad) {}

    private static void addErrorBarsToChart(LineChart<Number, Number> chart, List<double[]> velocityData, boolean angularVelocity) {
        var yAxis = (NumberAxis) chart.getYAxis();
        var measuredSeries = chart.getData().getFirst();
        var dataList = measuredSeries.getData();
        javafx.scene.Group plotArea = null;
        for (var dataItem : dataList) {
            if (dataItem.getNode() != null && dataItem.getNode().getParent() instanceof javafx.scene.Group g) {
                plotArea = g;
                break;
            }
        }
        if (plotArea == null) {
            return;
        }
        var capWidth = 4.0;
        var allParts = new ArrayList<ErrorBarParts>();
        var newChildren = new ArrayList<Node>();
        for (int i = 0; i < dataList.size(); i++) {
            var dataItem = dataList.get(i);
            var node = dataItem.getNode();
            if (node == null || i >= velocityData.size()) {
                continue;
            }
            var mad = velocityData.get(i)[2];
            if (angularVelocity) {
                mad = velocityToAngularVelocity(mad);
            } else {
                mad = mad * Math.cos(Math.toRadians(velocityData.get(i)[0]));
            }
            if (mad <= 0) {
                continue;
            }
            var stem = new Line();
            stem.setStroke(Color.rgb(40, 40, 40, 0.6));
            stem.setStrokeWidth(1.5);
            var topCap = new Line();
            topCap.setStroke(Color.rgb(40, 40, 40, 0.6));
            topCap.setStrokeWidth(1.5);
            var bottomCap = new Line();
            bottomCap.setStroke(Color.rgb(40, 40, 40, 0.6));
            bottomCap.setStrokeWidth(1.5);
            allParts.add(new ErrorBarParts(stem, topCap, bottomCap, mad));
            var errorBarGroup = new javafx.scene.Group(stem, topCap, bottomCap);
            errorBarGroup.setMouseTransparent(true);
            var nodeW = node.getBoundsInLocal().getWidth();
            var nodeH = node.getBoundsInLocal().getHeight();
            errorBarGroup.layoutXProperty().bind(node.layoutXProperty().add(nodeW / 2));
            errorBarGroup.layoutYProperty().bind(node.layoutYProperty().add(nodeH / 2));
            newChildren.add(errorBarGroup);
        }
        plotArea.getChildren().addAll(newChildren);
        Runnable updateHeights = () -> {
            var scale = Math.abs(yAxis.getScale());
            if (scale == 0) {
                return;
            }
            for (var parts : allParts) {
                var h = parts.mad() * scale;
                parts.stem().setStartY(-h);
                parts.stem().setEndY(h);
                parts.topCap().setStartX(-capWidth);
                parts.topCap().setEndX(capWidth);
                parts.topCap().setStartY(-h);
                parts.topCap().setEndY(-h);
                parts.bottomCap().setStartX(-capWidth);
                parts.bottomCap().setEndX(capWidth);
                parts.bottomCap().setStartY(h);
                parts.bottomCap().setEndY(h);
            }
        };
        updateHeights.run();
        yAxis.scaleProperty().addListener((obs, oldVal, newVal) -> updateHeights.run());
    }

    private LineChart<Number, Number> createSingleRotationChart(List<double[]> velocityData, boolean angularVelocity, DifferentialRotationCoefficients fittedCoeffs) {
        var xAxis = new NumberAxis();
        var yAxis = new NumberAxis();
        xAxis.setLabel(message("doppler.latitude"));
        yAxis.setLabel(angularVelocity ? message("doppler.angular.velocity") : message("doppler.tangential.velocity"));

        var lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setCreateSymbols(true);
        lineChart.setLegendVisible(true);

        var measuredSeries = new XYChart.Series<Number, Number>();
        measuredSeries.setName(message("doppler.measured"));
        var yMin = Double.MAX_VALUE;
        var yMax = -Double.MAX_VALUE;
        for (var point : velocityData) {
            var latDeg = point[0];
            var vEq = point[1];
            var mad = point[2];
            double value;
            double madConverted;
            if (angularVelocity) {
                value = velocityToAngularVelocity(vEq);
                madConverted = velocityToAngularVelocity(mad);
            } else {
                var cosLat = Math.cos(Math.toRadians(latDeg));
                value = vEq * cosLat;
                madConverted = mad * cosLat;
            }
            yMin = Math.min(yMin, value - madConverted);
            yMax = Math.max(yMax, value + madConverted);
            measuredSeries.getData().add(new XYChart.Data<>(latDeg, value));
        }
        var theoreticalSeries = new XYChart.Series<Number, Number>();
        theoreticalSeries.setName(message("doppler.theoretical"));
        var fitSeries = new XYChart.Series<Number, Number>();
        fitSeries.setName(message("doppler.fit"));
        for (double lat = ROTATION_PROFILE_MIN_LAT_DEG; lat <= ROTATION_PROFILE_MAX_LAT_DEG; lat += 1.0) {
            double theoreticalValue;
            double fitValue;
            if (angularVelocity) {
                theoreticalValue = snodgrassAngularVelocity(lat);
                fitValue = fittedCoeffs.angularVelocity(lat);
            } else {
                theoreticalValue = snodgrassVelocity(lat) * Math.cos(Math.toRadians(lat));
                fitValue = fittedCoeffs.tangentialVelocity(lat);
            }
            yMin = Math.min(yMin, Math.min(theoreticalValue, fitValue));
            yMax = Math.max(yMax, Math.max(theoreticalValue, fitValue));
            theoreticalSeries.getData().add(new XYChart.Data<>(lat, theoreticalValue));
            fitSeries.getData().add(new XYChart.Data<>(lat, fitValue));
        }
        var rawRange = yMax - yMin;
        var tickUnit = niceTickUnit(rawRange / 8);
        var lowerBound = Math.max(0, Math.floor((yMin - rawRange * 0.05) / tickUnit) * tickUnit);
        var upperBound = Math.ceil((yMax + rawRange * 0.05) / tickUnit) * tickUnit;
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(lowerBound);
        yAxis.setUpperBound(upperBound);
        yAxis.setTickUnit(tickUnit);
        lineChart.getData().add(measuredSeries);
        lineChart.getData().add(theoreticalSeries);
        lineChart.getData().add(fitSeries);

        lineChart.setStyle("CHART_COLOR_1: #3498db; CHART_COLOR_2: #e74c3c; CHART_COLOR_3: #2ecc71;");
        Platform.runLater(() -> {
            if (theoreticalSeries.getNode() != null) {
                theoreticalSeries.getNode().setStyle("-fx-stroke-dash-array: 6 3;");
            }
            for (var data : theoreticalSeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setVisible(false);
                }
            }
            for (var data : fitSeries.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setVisible(false);
                }
            }
        });

        return lineChart;
    }

    private static double niceTickUnit(double roughUnit) {
        var exponent = Math.floor(Math.log10(roughUnit));
        var fraction = roughUnit / Math.pow(10, exponent);
        double niceFraction;
        if (fraction <= 1.5) {
            niceFraction = 1;
        } else if (fraction <= 3) {
            niceFraction = 2;
        } else if (fraction <= 7) {
            niceFraction = 5;
        } else {
            niceFraction = 10;
        }
        return niceFraction * Math.pow(10, exponent);
    }

    private static double snodgrassAngularVelocity(double latDeg) {
        var sinLat = Math.sin(Math.toRadians(latDeg));
        var sinLat2 = sinLat * sinLat;
        var sinLat4 = sinLat2 * sinLat2;
        return SNODGRASS_A + SNODGRASS_B * sinLat2 + SNODGRASS_C * sinLat4;
    }

    private static double snodgrassVelocity(double latDeg) {
        var omegaRadPerSec = Math.toRadians(snodgrassAngularVelocity(latDeg)) / (24.0 * 3600.0);
        return omegaRadPerSec * SOLAR_RADIUS_KM;
    }

    private static double velocityToAngularVelocity(double velocityKmS) {
        return velocityKmS / SOLAR_RADIUS_KM * (180.0 / Math.PI) * 86400.0;
    }

    /**
     * Fitted differential rotation coefficients using the standard formula
     * (often called the "Faye formula"): ω(φ) = A + B·sin²(φ) + C·sin⁴(φ) in deg/day.
     * Reference coefficients are from Snodgrass & Ulrich (1990).
     */
    private record DifferentialRotationCoefficients(double a, double b, double c) {
        double angularVelocity(double latDeg) {
            var sinLat = Math.sin(Math.toRadians(latDeg));
            var sinLat2 = sinLat * sinLat;
            var sinLat4 = sinLat2 * sinLat2;
            return a + b * sinLat2 + c * sinLat4;
        }

        double tangentialVelocity(double latDeg) {
            var omegaRadPerSec = Math.toRadians(angularVelocity(latDeg)) / (24.0 * 3600.0);
            return omegaRadPerSec * SOLAR_RADIUS_KM * Math.cos(Math.toRadians(latDeg));
        }
    }

    /**
     * Fits the Snodgrass-style differential rotation formula to the measured data.
     * Uses sin²(φ) as the independent variable for a quadratic fit:
     * ω = A + B·x + C·x² where x = sin²(φ)
     */
    private static DifferentialRotationCoefficients fitDifferentialRotationCoefficients(List<double[]> velocityData) {
        var points = new Point2D[velocityData.size()];
        var weights = new double[velocityData.size()];
        for (int i = 0; i < velocityData.size(); i++) {
            var p = velocityData.get(i);
            var latDeg = p[0];
            var velocityKmS = p[1];
            var mad = p[2];
            // Convert to angular velocity and use sin²(lat) as x
            var sinLat = Math.sin(Math.toRadians(latDeg));
            var sinLat2 = sinLat * sinLat;
            var omega = velocityToAngularVelocity(velocityKmS);
            points[i] = new Point2D(sinLat2, omega);
            weights[i] = mad > 0 ? 1.0 / mad : 1.0;
        }
        // Fit quadratic: ω = c + b*x + a*x² where x = sin²(φ)
        // secondOrderRegression returns (a, b, c) for y = a*x² + b*x + c
        var coeffs = LinearRegression.secondOrderRegression(points, weights);
        // coeffs.a() is the x² coefficient (C in Snodgrass), coeffs.b() is x coefficient (B), coeffs.c() is constant (A)
        return new DifferentialRotationCoefficients(coeffs.c(), coeffs.b(), coeffs.a());
    }

    private double[] computeSphereCoords(double longitude, double latitude, double radius, double b0, double angleP) {
        // Convert spherical to Cartesian (same formula as ImageDraw.ofSpherical)
        // longitude = 0 is facing Earth, +90 is West limb, -90 is East limb
        // latitude = π/2 is the equator (co-latitude convention)
        var x = Math.sin(longitude) * Math.sin(latitude) * radius;
        var y = Math.cos(latitude) * radius;
        var z = Math.cos(longitude) * Math.sin(latitude) * radius;

        // Rotate around X axis by -b0
        var cosB0 = Math.cos(-b0);
        var sinB0 = Math.sin(-b0);
        var y1 = y * cosB0 - z * sinB0;
        var z1 = y * sinB0 + z * cosB0;

        // Rotate around Z axis by -angleP
        var cosP = Math.cos(-angleP);
        var sinP = Math.sin(-angleP);
        var x2 = x * cosP - y1 * sinP;
        var y2 = x * sinP + y1 * cosP;

        return new double[]{x2, y2};
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
                for (var i = 0; i < rgb.length; i++) {
                    var channel = rgb[i];
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
        for (var i = 0; i < histogram.values().length; i++) {
            var d = new XYChart.Data<String, Number>(String.valueOf(i), histogram.values()[i]);
            series.getData().add(d);
        }

        // Add the series to the bar chart
        chart.getData().add(series);
        for (var d : series.getData()) {
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
        scriptValuesByLabel.clear();
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
        Platform.runLater(() -> {
            updateSpectral3DButtonsState();
            measureVelocityButton.setDisable(false);
            customMeasurementButton.setDisable(false);
        });
        pixelShiftRange = payload.pixelShiftRange();
        mainEllipse = payload.mainEllipse();
        imageScriptExecutor = new JSolExScriptExecutor(
                shift -> {
                    var minShift = shiftImages.keySet().stream().mapToDouble(PixelShift::pixelShift).min().orElse(0d);
                    var maxShift = shiftImages.keySet().stream().mapToDouble(PixelShift::pixelShift).max().orElse(0d);
                    var lookup = shift.pixelShift();
                    if (lookup < minShift) {
                        LOGGER.warn(String.format(message("cropping.window.invalid.shift"), lookup, minShift));
                        lookup = minShift;
                    } else if (lookup > maxShift) {
                        LOGGER.warn(String.format(message("cropping.window.invalid.shift"), lookup, maxShift));
                        lookup = maxShift;
                    }
                    return shiftImages.get(new PixelShift(lookup));
                },
                scriptExecutionContext,
                this,
                null
        );
        for (var entry : pendingVariables.entrySet()) {
            imageScriptExecutor.putVariable(entry.getKey(), entry.getValue());
        }
        var sb = new StringBuilder();
        if (!suggestions.isEmpty()) {
            sb.append(message("suggestions") + " :\n");
            for (var suggestion : suggestions.values()) {
                sb.append("    - ").append(suggestion).append("\n");
            }
        }
        owner.prepareForScriptExecution(this, params, rootOperation, SectionKind.SINGLE);
        suggestions.clear();
        System.gc();
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
        // Calculate duration after all work is done (including scripts)
        var finishedString = String.format(message("finished.in"), DurationFormatter.formatNanos(System.nanoTime() - sd));
        LOGGER.info(message("processing.done"));
        LOGGER.info(finishedString);
        broadcast(rootOperation.update(1, finishedString));
        Platform.runLater(() -> {
            var tabPane = profileTab.getTabPane();
            if (tabPane != null) {
                tabPane.getSelectionModel().select(profileTab);
            }
        });
    }

    private ScriptExecutionContext prepareExecutionContext(ProcessingDoneEvent.Outcome payload) {
        return ScriptExecutionContext.forProcessing(payload.processParams(), serFile.toPath(), payload.pixelShiftRange(), header)
            .ellipse(payload.ellipse())
            .imageStats(payload.imageStats())
            .imageEmitter(payload.customImageEmitter())
            .animationFormats(Configuration.getInstance().getAnimationFormats())
            .progressOperation(rootOperation)
            .serFileReader(payload.serFileReader())
            .spectralLinePolynomial(payload.polynomialCoefficients() != null ? new SpectralLinePolynomial(payload.polynomialCoefficients()) : null)
            .build();
    }

    @Override
    public void onProgress(ProgressEvent e) {
        owner.updateProgress(e.getPayload());
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
        // perform a first pass just to check if they are missing image shifts
        var missingShifts = determineShiftsRequiredInScript(script);
        shiftImages.keySet().stream().map(PixelShift::pixelShift).toList().forEach(missingShifts::remove);
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(missingShifts);
        }
        var result = imageScriptExecutor.execute(script, kind);
        var namingStrategy = createNamingStrategy();
        var outputsMetadata = ScriptExecutionHelper.extractOutputsMetadata(script);
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
        return result;
    }

    @Override
    public ImageMathScriptResult executePythonScript(String script, SectionKind kind) {
        var result = imageScriptExecutor.executePythonScript(script, kind);
        var namingStrategy = createNamingStrategy();
        var outputsMetadata = ScriptExecutionHelper.extractOutputsMetadata(script, "script.py");
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
        return result;
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
                BatchOperations.submitOneOfAKind("progress", () -> owner.updateProgress(e.getPayload()));
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
            // Spectral profile mode
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
            series.setName(SpectralProfileHelper.formatLegend(params.observationDetails().instrument(), lambda0, binning, pixelSize));
            var dispersion = canDrawReference ? SpectrumAnalyzer.computeSpectralDispersion(params.observationDetails().instrument(), lambda0, pixelSize * binning) : null;
            var range = PixelShiftRange.computePixelShiftRange(start, end, height, polynomial);
            var dataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();
            for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
                double cpt = 0;
                double val = 0;
                for (var x = start; x < end; x++) {
                    var v = polynomial.applyAsDouble(x);
                    var exactNy = v + pixelShift;
                    var lowerNy = (int) Math.floor(exactNy);
                    var upperNy = (int) Math.ceil(exactNy);

                    if (lowerNy >= 0 && upperNy < height) {
                        var lowerValue = data[lowerNy][x];
                        var upperValue = data[upperNy][x];
                        var interpolatedValue = lowerValue + (upperValue - lowerValue) * (exactNy - lowerNy);

                        val += interpolatedValue;
                        cpt++;
                    }
                }
                if (cpt > 0) {
                    var wl = canDrawReference ? SpectralProfileHelper.computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                    dataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, val / cpt));
                }
            }
            lineChart.getData().add(series);

            List<SpectrumAnalyzer.DataPoint> referenceDataPoints;
            if (canDrawReference) {
                var referenceSeries = new XYChart.Series<String, Number>();
                referenceSeries.setName(message("reference.intensity"));
                lineChart.getData().add(referenceSeries);
                referenceDataPoints = SpectralProfileHelper.normalizeDatapoints(SpectrumAnalyzer.findReferenceDatapoints(dataPoints), null).dataPoints();
                for (var dataPoint : referenceDataPoints) {
                    addDataPointToSeries(dataPoint, referenceSeries);
                }
            } else {
                referenceDataPoints = null;
            }
            var normalizedDataPoints = SpectralProfileHelper.normalizeDatapoints(dataPoints, null);
            for (var dataPoint : normalizedDataPoints.dataPoints()) {
                addDataPointToSeries(dataPoint, series);
            }

            // Compute line statistics from the normalized data, using reference for continuum if available
            // Pass the real line center (selected wavelength) for accurate reporting
            var realLineCenter = canDrawReference ? lambda0.nanos() : null;
            currentLineStatistics = SpectralLineAnalysis.computeStatistics(normalizedDataPoints.dataPoints(), referenceDataPoints, realLineCenter);

            // Add FWHM visualization to the chart
            addFWHMVisualization(lineChart, currentLineStatistics, normalizedDataPoints.dataPoints());

            SpectralProfileHelper.NormalizedDataPoints normalizedFrameDataPoints;
            SpectralProfileHelper.NormalizedDataPoints normalizedLineDataPoints;
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
                    for (var x = start; x < end; x++) {
                        var v = polynomial.applyAsDouble(x);
                        var exactNy = v + pixelShift;
                        var lowerNy = (int) Math.floor(exactNy);
                        var upperNy = (int) Math.ceil(exactNy);

                        if (lowerNy >= 0 && upperNy < height) {
                            var lowerValue = currentSpectrumFrameData[lowerNy][x];
                            var upperValue = currentSpectrumFrameData[upperNy][x];
                            var interpolatedValue = lowerValue + (upperValue - lowerValue) * (exactNy - lowerNy);

                            val += interpolatedValue;
                            cpt++;
                        }
                    }
                    if (cpt > 0) {
                        var wl = canDrawReference ? SpectralProfileHelper.computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                        frameDataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, val / cpt));
                    }
                }

                for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
                    int x = currentColumn;
                    var v = polynomial.applyAsDouble(x);
                    var exactNy = v + pixelShift;
                    var lowerNy = (int) Math.floor(exactNy);
                    var upperNy = (int) Math.ceil(exactNy);

                    if (lowerNy >= 0 && upperNy < height) {
                        var lowerValue = currentSpectrumFrameData[lowerNy][x];
                        var upperValue = currentSpectrumFrameData[upperNy][x];
                        var interpolatedValue = lowerValue + (upperValue - lowerValue) * (exactNy - lowerNy);

                        var wl = canDrawReference ? SpectralProfileHelper.computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                        lineDataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, interpolatedValue));
                    }
                }

                normalizedFrameDataPoints = SpectralProfileHelper.normalizeDatapoints(frameDataPoints, null);
                for (var dataPoint : normalizedFrameDataPoints.dataPoints()) {
                    addDataPointToSeries(dataPoint, frameSeries);
                }

                var lineSeries = new XYChart.Series<String, Number>();
                // Compute wavelength for the clicked column
                String lineLabel;
                if (canDrawReference) {
                    var columnPixelShift = polynomial.applyAsDouble(currentColumn);
                    var wavelength = SpectralProfileHelper.computeWavelength(columnPixelShift, lambda0, dispersion);
                    lineLabel = message("line.intensity") + " (" + String.format(Locale.US, "%.2f Å", wavelength.angstroms()) + ")";
                } else {
                    lineLabel = message("line.intensity") + " (" + currentColumn + ")";
                }
                lineSeries.setName(lineLabel);
                lineChart.getData().add(lineSeries);

                // Normalize line data independently
                normalizedLineDataPoints = SpectralProfileHelper.normalizeDatapoints(lineDataPoints, null);
                for (var dataPoint : normalizedLineDataPoints.dataPoints()) {
                    addDataPointToSeries(dataPoint, lineSeries);
                }

                // Compute statistics for the line profile at clicked position, using reference for continuum if available
                frameLineStatistics = SpectralLineAnalysis.computeStatistics(normalizedLineDataPoints.dataPoints(), referenceDataPoints, realLineCenter);
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
            show3DButton.setOnAction(evt -> spectral3DHelper.show3DSpectralProfile());
            show3DButton.setDisable(cachedAverageImagePayload == null);
            showEvolutionButton = new Button(I18N.string(JSolEx.class, "spectral-surface-3d", "show.spectral.cube"));
            showEvolutionButton.getStyleClass().add("image-viewer-button");
            showEvolutionButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
            showEvolutionButton.setOnAction(evt -> spectral3DHelper.showSpectralEvolution());
            showEvolutionButton.setDisable(cachedAverageImagePayload == null || cachedTrimmingParameters == null);
            showTomographyButton = new Button(I18N.string(JSolEx.class, "spherical-tomography", "show.tomography"));
            showTomographyButton.getStyleClass().add("image-viewer-button");
            showTomographyButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
            showTomographyButton.setOnAction(evt -> spectral3DHelper.showSphericalTomography());
            var openGLAvailable = OpenGLAvailability.isAvailable();
            showTomographyButton.setVisible(openGLAvailable);
            showTomographyButton.setManaged(openGLAvailable);
            showTomographyButton.setDisable(shiftImages.isEmpty() || cachedAverageImagePayload == null);

            var rightButtons = new HBox(10, show3DButton, showEvolutionButton, showTomographyButton);
            rightButtons.setAlignment(Pos.CENTER_RIGHT);

            var topBar = new BorderPane();
            topBar.setPadding(new Insets(5, 10, 5, 10));
            topBar.setRight(rightButtons);

            // Create the main layout with chart and statistics
            var mainPane = new BorderPane();
            mainPane.setTop(topBar);
            mainPane.setCenter(lineChart);
            mainPane.setBottom(statisticsPanel);

            return mainPane;
        };
        if (Platform.isFxApplicationThread()) {
            profileTab.setContent(profileViewFactory.get());
            analysisTab.setContent(buildAnalysisTabContent(payload));
        } else {
            Platform.runLater(() -> {
                profileTab.setContent(profileViewFactory.get());
                analysisTab.setContent(buildAnalysisTabContent(payload));
            });
        }

    }

    @Override
    public void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {
        cachedTrimmingParameters = e.getPayload();
        Platform.runLater(this::updateSpectral3DButtonsState);
        owner.setTrimmingParameters(cachedTrimmingParameters);
    }

    private Parent buildAnalysisTabContent(AverageImageComputedEvent.AverageImage payload) {
        var mainPane = new BorderPane();
        mainPane.setPadding(new Insets(15));

        // Title with help button
        var titleLabel = new Label(message("analysis.differential.velocity.title"));
        titleLabel.setFont(Font.font(titleLabel.getFont().getFamily(), FontWeight.BOLD, 14));

        // Create help overlay and add it to the root StackPane so it displays over the entire window
        var helpOverlay = new DifferentialVelocityHelpOverlay();
        helpOverlay.setMouseTransparent(true);  // Start mouse-transparent until popup is shown
        var helpButton = helpOverlay.createStandaloneButton();

        // Add only the overlay to the root StackPane
        var rootStackPane = owner.getRootStackPane();
        if (!rootStackPane.getChildren().contains(helpOverlay)) {
            rootStackPane.getChildren().add(helpOverlay);
        }

        var titleBox = new HBox(10, titleLabel, helpButton);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        // Description
        var descriptionLabel = new Label(message("analysis.differential.velocity.description"));
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(500);

        // Note about GONG tab
        var noteLabel = new Label(message("analysis.differential.velocity.note"));
        noteLabel.setWrapText(true);
        noteLabel.setMaxWidth(500);
        noteLabel.setStyle("-fx-font-style: italic;");

        // Warning about seeing conditions
        var warningLabel = new Label(message("analysis.differential.velocity.warning"));
        warningLabel.setWrapText(true);
        warningLabel.setMaxWidth(500);
        warningLabel.setStyle("-fx-text-fill: #856404; -fx-font-weight: bold;");

        // Helper to run the measurement
        Runnable runMeasurement = () -> {
            var referenceImage = shiftImages.entrySet().stream()
                .min(Comparator.comparingDouble(e2 -> Math.abs(e2.getKey().pixelShift())))
                .map(Map.Entry::getValue)
                .orElse(null);
            if (referenceImage == null) {
                return;
            }
            var refCoordsOpt = referenceImage.findMetadata(ReferenceCoords.class);
            var solarParamsOpt = referenceImage.findMetadata(SolarParameters.class);
            var ellipseOpt = referenceImage.findMetadata(Ellipse.class);
            if (refCoordsOpt.isEmpty() || solarParamsOpt.isEmpty() || ellipseOpt.isEmpty()) {
                return;
            }
            generateRotationProfileChart(payload, refCoordsOpt.get(), ellipseOpt.get(), solarParamsOpt.get());
        };

        // Measure button - runs with default/current config
        measureVelocityButton = new Button(message("rotation.measure"));
        measureVelocityButton.getStyleClass().add("primary-button");
        measureVelocityButton.setDisable(true);
        measureVelocityButton.setOnAction(evt -> runMeasurement.run());

        // Customized measurement button - opens config dialog then runs
        customMeasurementButton = new Button(message("rotation.measure.custom"));
        customMeasurementButton.getStyleClass().add("default-button");
        customMeasurementButton.setDisable(true);
        customMeasurementButton.setOnAction(evt -> {
            var result = DifferentialRotationConfigDialog.show(
                measureVelocityButton.getScene().getWindow(),
                differentialRotationConfig
            );
            result.ifPresent(config -> {
                differentialRotationConfig = config;
                runMeasurement.run();
            });
        });

        var buttonBox = new HBox(10, measureVelocityButton, customMeasurementButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        var contentBox = new VBox(15, titleBox, descriptionLabel, noteLabel, warningLabel, buttonBox);
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.setPadding(new Insets(10));

        mainPane.setCenter(contentBox);

        return mainPane;
    }

    private List<SpectrumAnalyzer.DataPoint> extractPointProfile(int column,
                                                                 float[][] frameData,
                                                                 PixelShiftRange range,
                                                                 DoubleUnaryOperator polynomial,
                                                                 Wavelen lambda0,
                                                                 Dispersion dispersion,
                                                                 boolean canCalibrate,
                                                                 int columnAveragingRadius) {
        var dataPoints = new ArrayList<SpectrumAnalyzer.DataPoint>();
        if (frameData == null || frameData.length == 0) {
            return dataPoints;
        }
        var frameHeight = frameData.length;
        var frameWidth = frameData[0].length;

        var centerPolyValue = polynomial.applyAsDouble(column);
        for (var pixelShift = range.minPixelShift(); pixelShift < range.maxPixelShift(); pixelShift++) {
            var centerExactNy = centerPolyValue + pixelShift;
            var centerLowerNy = (int) Math.floor(centerExactNy);
            var centerUpperNy = (int) Math.ceil(centerExactNy);

            if (centerLowerNy >= 0 && centerUpperNy < frameHeight) {
                double sum = 0;
                int count = 0;
                for (int colOffset = -columnAveragingRadius; colOffset <= columnAveragingRadius; colOffset++) {
                    int col = column + colOffset;
                    if (col >= 0 && col < frameWidth) {
                        var colPolyValue = polynomial.applyAsDouble(col);
                        var colExactNy = colPolyValue + pixelShift;
                        var colLowerNy = (int) Math.floor(colExactNy);
                        var colUpperNy = (int) Math.ceil(colExactNy);
                        if (colLowerNy >= 0 && colUpperNy < frameHeight) {
                            var lowerValue = frameData[colLowerNy][col];
                            var upperValue = frameData[colUpperNy][col];
                            sum += lowerValue + (upperValue - lowerValue) * (colExactNy - colLowerNy);
                            count++;
                        }
                    }
                }
                var interpolatedValue = count > 0 ? (float) (sum / count) : 0f;
                var wl = canCalibrate ? SpectralProfileHelper.computeWavelength(pixelShift, lambda0, dispersion) : Wavelen.ofAngstroms(0);
                dataPoints.add(new SpectrumAnalyzer.DataPoint(wl, pixelShift, interpolatedValue));
            }
        }
        return dataPoints;
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
        var fwhmValue = stats.hasFWHMData()
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

    private static HBox createModeBar(Node modeSelector) {
        var bar = new HBox(modeSelector);
        bar.setPadding(new Insets(5, 10, 5, 10));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
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
        var firstLabel = SpectralProfileHelper.formatWavelength(dataPoints.getFirst().pixelShift(), dataPoints.getFirst().wavelen());
        var lastLabel = SpectralProfileHelper.formatWavelength(dataPoints.getLast().pixelShift(), dataPoints.getLast().wavelen());

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
        var halfMaxIntensity = stats.halfMaxIntensity();

        if (stats.hasFWHMData()) {
            // Use direct crossing measurements
            double blueWl = stats.blueHalfMaxWavelength();
            double redWl = stats.redHalfMaxWavelength();

            var minBlueDist = Double.MAX_VALUE;
            var minRedDist = Double.MAX_VALUE;

            for (var dp : dataPoints) {
                var wl = dp.wavelen().angstroms();
                var blueDist = Math.abs(wl - blueWl);
                var redDist = Math.abs(wl - redWl);

                if (blueDist < minBlueDist) {
                    minBlueDist = blueDist;
                    blueLabel = SpectralProfileHelper.formatWavelength(dp.pixelShift(), dp.wavelen());
                }
                if (redDist < minRedDist) {
                    minRedDist = redDist;
                    redLabel = SpectralProfileHelper.formatWavelength(dp.pixelShift(), dp.wavelen());
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
                        var seriesIndex = Integer.parseInt(styleClass.substring(6));
                        var dataSeriesCount = chart.getData().size();
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

            ChartExportHelper.addLegendToggleHandlers(chart);
        });
    }


    private LineChart<String, Number> createProfileChart(
            ProcessParams params,
            AverageImageComputedEvent.AverageImage payload,
            PixelShiftRange range,
            SpectralProfileHelper.NormalizedDataPoints normalizedDataPoints,
            List<SpectrumAnalyzer.DataPoint> referenceDataPoints,
            SpectralProfileHelper.NormalizedDataPoints normalizedFrameDataPoints,
            SpectralProfileHelper.NormalizedDataPoints normalizedLineDataPoints,
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
        series.setName(SpectralProfileHelper.formatLegend(params.observationDetails().instrument(), lambda0, binning, pixelSize));
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
                var wavelength = SpectralProfileHelper.computeWavelength(columnPixelShift, lambda0, dispersion);
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
        var label = SpectralProfileHelper.formatWavelength(dataPoint.pixelShift(), dataPoint.wavelen());
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
                        BatchOperations.submitOneOfAKind("progress", () -> owner.updateProgress(e.getPayload()));
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

        // For the main chart, snapshot parent to include stats panel / dual chart layout
        Supplier<Node> nodeSupplier = () -> {
            if (graphData instanceof GraphData.ProfileData && chart.getParent() != null) {
                return chart.getParent();
            }
            if (graphData instanceof GraphData.DifferentialVelocityData && chart.getParent() != null && chart.getParent().getParent() != null) {
                return chart.getParent().getParent();
            }
            return chart;
        };

        Consumer<? super PrintWriter> csvWriter = switch (graphData) {
            case GraphData.ProfileData profileData -> profileData.csvWriter();
            case GraphData.DifferentialVelocityData rpData -> rpData.csvWriter();
            default -> null;
        };

        var menu = ChartExportHelper.createChartContextMenu(name, nodeSupplier, csvWriter, outputDirectory, baseName, this::createNamingStrategy);

        var openInNewWindow = new MenuItem(message("open.in.new.window"));
        openInNewWindow.setOnAction(evt -> Platform.runLater(() -> {
            var newWindow = new Stage();
            newWindow.setTitle(message(message("profile")));

            Consumer<? super PrintWriter> popupCsvWriter = switch (graphData) {
                case GraphData.ProfileData profileData -> profileData.csvWriter();
                case GraphData.DifferentialVelocityData rpData -> rpData.csvWriter();
                default -> null;
            };

            if (graphData instanceof GraphData.DifferentialVelocityData rpData) {
                var dualLayout = rpData.dualLayoutFactory().get();
                var popupMenu = ChartExportHelper.createChartContextMenu(name, () -> dualLayout, popupCsvWriter, outputDirectory, baseName, this::createNamingStrategy);
                dualLayout.setOnContextMenuRequested(menuEvt -> popupMenu.show(dualLayout, menuEvt.getScreenX(), menuEvt.getScreenY()));
                var scene = JSolEx.newScene(dualLayout, 1400, 550);
                newWindow.setScene(scene);
                newWindow.show();
            } else {
                var pane = new BorderPane();
                var newChart = chartFactory.get();
                pane.setCenter(newChart);

                if (graphData instanceof GraphData.ProfileData profileData && profileData.statistics() != null) {
                    pane.setBottom(createStatisticsPanel(profileData.statistics(), profileData.hasWavelength()));
                }

                var popupMenu = ChartExportHelper.createChartContextMenu(name, () -> pane, popupCsvWriter, outputDirectory, baseName, this::createNamingStrategy);
                newChart.setOnContextMenuRequested(menuEvt -> popupMenu.show(newChart, menuEvt.getScreenX(), menuEvt.getScreenY()));

                var scene = new Scene(pane, 800, 600);
                newWindow.setScene(scene);
                newWindow.show();

                Platform.runLater(() -> ChartExportHelper.addLegendToggleHandlers(newChart));
            }
        }));

        menu.getItems().add(openInNewWindow);
        chart.setOnContextMenuRequested(menuEvent -> menu.show(chart, menuEvent.getScreenX(), menuEvent.getScreenY()));
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


    @Override
    public void onScriptExecutionResult(ScriptExecutionResultEvent e) {
        var images = e.getPayload().imagesByLabel();
        for (var entry : images.entrySet()) {
            scriptImagesByLabel.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>())
                    .add(entry.getValue());
        }
        var values = e.getPayload().valuesByLabel();
        for (var entry : values.entrySet()) {
            scriptValuesByLabel.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>())
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
            var ctx = ScriptExecutionContext.empty().mergeAll(scriptExecutionContext.toMap());
            ctx.put(ImageEmitter.class, imageEmitter);

            var batchScriptExecutor = new JSolExScriptExecutor(
                    idx -> {
                        throw new IllegalStateException("Cannot call img() in batch outputs. Use variables to store images instead");
                    },
                    ctx,
                    this,
                    null
            );

            for (var entry : scriptImagesByLabel.entrySet()) {
                batchScriptExecutor.putVariable(entry.getKey(), entry.getValue());
            }
            for (var entry : scriptValuesByLabel.entrySet()) {
                batchScriptExecutor.putVariable(entry.getKey(), entry.getValue());
            }

            var parameterValues = adjustedParams.combinedImageMathParams().parameterValues();
            for (var scriptFile : scriptFiles) {
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
            ScriptExecutionHelper.processScriptErrors(result);
            var outputsMetadata = ScriptExecutionHelper.extractOutputsMetadata(scriptFile);
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

        record DifferentialVelocityData(
                XYChart<?, ?> chart,
                Supplier<? extends Parent> dualLayoutFactory,
                Consumer<? super PrintWriter> csvWriter
        ) implements GraphData {
            @Override
            public Supplier<? extends XYChart<?, ?>> graphFactory() {
                return null;
            }

            public String name() {
                return "differential-velocity";
            }
        }
    }

}
