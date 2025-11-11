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
import javafx.scene.Parent;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Transform;
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
import me.champeau.a4j.jsolex.app.jfx.ReconstructionView;
import me.champeau.a4j.jsolex.app.jfx.RectangleSelectionListener;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.app.script.JSolExScriptExecutor;
import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.EllipseFittingRequestEvent;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
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
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.jfx.BatchOperations.blockingUntilResultAvailable;
import static me.champeau.a4j.jsolex.processing.sun.CaptureSoftwareMetadataHelper.computeSerFileBasename;
import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

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

    private Supplier<LineChart<?, ?>> profileGraphFactory;
    private Integer currentColumn;
    private float[][] currentSpectrumFrameData;

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
                    return new ReconstructionView(viewer.getImageView(), buffer, parentWidth);
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

                            // Force final solar image update with complete buffer
                            solarImage.getPixelWriter().setPixels(
                                    0, 0,
                                    width, height,
                                    pixelformat,
                                    rgb,
                                    0,
                                    3 * width
                            );

                            // Force final spectrum image update if spectrum data is available
                            var spectrum = reconstructionView.getSpectrumView();
                            if (spectrum.getImage() != null) {
                                // The spectrum image should already be up-to-date from the last partial reconstruction
                                // No additional update needed for spectrum in final flush
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
                        if (profileGraphFactory != null) {
                            Platform.runLater(() -> profileTab.setContent(profileGraphFactory.get()));
                        }
                    }
                } catch (Exception ex) {
                    throw new ProcessingException(ex);
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
        var title = payload.title();
        var generatedImageKind = payload.kind();
        var imageWrapper = payload.image();
        var pixelShift = imageWrapper.findMetadata(PixelShift.class);
        Platform.runLater(() -> {
            var metadata = imageWrapper.metadata();
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
                    viewer -> {
                        BackgroundOperations.async(() -> {
                            var histogram = showHistogram(viewer.getStretchedImage());
                            Platform.runLater(() -> statsTab.setContent(histogram));
                        });
                        showMetadata(metadata);
                    });
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
                            computeSerFileBasename(serFile)
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
                        var stage = new Stage();
                        stage.setScene(new Scene(node));
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
        var filePath = event.getPayload().path();
        var fileName = filePath.toFile().getName();
        if (fileName.endsWith(".mp4")) {
            Platform.runLater(() -> owner.getImagesViewer().addVideo(event.getPayload().kind(), event.getPayload().title(), filePath));
        } else if (fileName.endsWith(".gif")) {
            Platform.runLater(() -> owner.getImagesViewer().addAnimatedGif(event.getPayload().kind(), event.getPayload().title(), filePath));
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
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        var payload = e.getPayload();
        imageEmitter = payload.customImageEmitter();
        scriptExecutionContext = prepareExecutionContext(payload);
        shiftImages.putAll(payload.shiftImages());
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
                computeSerFileBasename(serFile)
        ));
        owner.prepareForGongImageDownload(processParams);
        
        // Execute batch scripts for single file processing (only when not in batch mode)
        if (serFile != null && hasBatchScriptExpressions()) {
            executeSingleFileBatchScripts();
        }
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
        owner.updateProgress(e.getPayload().progress(), e.getPayload().task());
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
        var result = imageScriptExecutor.execute(script, SectionKind.SINGLE);
        var namingStrategy = createNamingStrategy();
        ImageMathScriptExecutor.render(result, imageEmitter, (outputLabel, fileOutput) -> {
            var baseName = namingStrategy.render(0, null, Constants.TYPE_CUSTOM, outputLabel, computeSerFileBasename(serFile), null);
            try {
                var displayPath = FilesUtils.saveAllFilesAndGetDisplayPath(fileOutput, outputDirectory, baseName);
                if (displayPath != null) {
                    onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, outputLabel, displayPath));
                }
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        });
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
        var dur = java.time.Duration.ofNanos(System.nanoTime() - sd);
        var secs = dur.toSeconds() + (dur.toMillisPart() / 1000d);
        onProgress(ProgressEvent.of(rootOperation.complete(String.format(Constants.message("script.completed.in.format"), secs))));
        return result;
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
        } else if (event instanceof ReconstructionDoneEvent e) {
            onReconstructionDone(e);
        } else if (event instanceof SpectralLineDetectedEvent e) {
            onSpectralLineDetected(e);
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
        adjustedParams = payload.adjustedParams();
        polynomial = payload.polynomial();
        averageImage = payload.image().data();
        profileGraphFactory = () -> {
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

            NormalizedDataPoints normalizedFrameDataPoints;
            NormalizedDataPoints normalizedLineDataPoints;
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
            } else {
                normalizedFrameDataPoints = null;
                normalizedLineDataPoints = null;
            }

            registerSaveChartAction(new GraphData.ProfileData(lineChart, profileGraphFactory, writer -> {
                writer.print("pixel_shift;wavelength;reference_intensity;intensity");
                if (currentColumn != null) {
                    writer.print(";frame_intensity;line_intensity");
                }
                writer.println();
                for (var dataPoint : normalizedDataPoints.dataPoints()) {
                    var referenceDataPoint = referenceDataPoints != null ? referenceDataPoints.stream()
                            .filter(dp -> dp.pixelShift() == dataPoint.pixelShift())
                            .findFirst()
                            .orElse(new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 0d)) : new SpectrumAnalyzer.DataPoint(dataPoint.wavelen(), dataPoint.pixelShift(), 0d);
                    writer.printf(Locale.US, "%.2f;%.2f;%.2f;%.2f", dataPoint.pixelShift(), dataPoint.wavelen().angstroms(), referenceDataPoint.intensity(), dataPoint.intensity());
                    if (currentColumn != null && normalizedFrameDataPoints != null) {
                        var frameIntensity = normalizedFrameDataPoints.dataPoints().stream()
                                .filter(dp -> dp.pixelShift() == dataPoint.pixelShift())
                                .findFirst()
                                .map(SpectrumAnalyzer.DataPoint::intensity)
                                .orElse(0d);
                        var lineIntensity = normalizedLineDataPoints.dataPoints().stream()
                                .filter(dp -> dp.pixelShift() == dataPoint.pixelShift())
                                .findFirst()
                                .map(SpectrumAnalyzer.DataPoint::intensity)
                                .orElse(0d);
                        writer.printf(Locale.US, ";%.2f;%.2f", frameIntensity, lineIntensity);
                    }
                    writer.println();
                }
            }));

            return lineChart;
        };
        if (Platform.isFxApplicationThread()) {
            profileTab.setContent(profileGraphFactory.get());
        } else {
            Platform.runLater(() -> profileTab.setContent(profileGraphFactory.get()));
        }

    }

    @Override
    public void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {
        owner.setTrimmingParameters(e.getPayload());
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
                solexVideoProcessor.process();
            }));
            menu.show(node, event.getScreenX(), event.getScreenY());
        });
    }

    private <T extends XYChart<?, ?>> void registerSaveChartAction(GraphData graphData) {
        var menu = new ContextMenu();
        var chart = graphData.chart();
        var chartFactory = graphData.graphFactory();
        var name = graphData.name();
        var saveToFile = new MenuItem(message("save.to.file"));
        saveToFile.setOnAction(evt -> {
            var snapshotParameters = new SnapshotParameters();
            snapshotParameters.setTransform(Transform.scale(2, 2));
            var writable = chart.snapshot(snapshotParameters, null);
            try {
                var namingStrategy = createNamingStrategy();
                var outputFile = outputDirectory.resolve(namingStrategy.render(0, null, Constants.TYPE_DEBUG, name, baseName, null) + ".png");
                var bufferedImage = SwingFXUtils.fromFXImage(writable, null);
                createDirectoriesIfNeeded(outputFile.getParent());
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

        if (graphData instanceof GraphData.ProfileData profileData) {
            var exportToCsv = new MenuItem(message("export.to.csv"));
            exportToCsv.setOnAction(evt -> {
                var namingStrategy = createNamingStrategy();
                var outputFile = outputDirectory.resolve(namingStrategy.render(0, null, Constants.TYPE_DEBUG, name, baseName, null) + ".csv");
                try {
                    createDirectoriesIfNeeded(outputFile.getParent());
                    try (var writer = new PrintWriter(new FileWriter(outputFile.toFile(), StandardCharsets.UTF_8))) {
                        profileData.csvWriter().accept(writer);
                        LOGGER.info(message("csv.saved"), outputFile);
                        var alert = AlertFactory.info();
                        alert.setTitle(message("csv.saved.title"));
                        var textArea = new TextArea("CSV saved to: " + outputFile);
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
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
            });
            menu.getItems().add(exportToCsv);
        }

        var openInNewWindow = new MenuItem(message("open.in.new.window"));
        openInNewWindow.setOnAction(evt -> Platform.runLater(() -> {
            var newWindow = new Stage();
            newWindow.setTitle(message(message("profile")));

            var pane = new BorderPane();
            pane.setCenter(chartFactory.get());

            var scene = new Scene(pane, 800, 600);
            newWindow.setScene(scene);
            newWindow.show();
        }));

        menu.getItems().add(openInNewWindow);
        chart.setOnContextMenuRequested(menuEvent -> menu.show(chart, menuEvent.getScreenX(), menuEvent.getScreenY()));
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
            
            boolean initial = true;
            for (File scriptFile : scriptFiles) {
                if (initial) {
                    owner.prepareForScriptExecution(this, params, rootOperation, ImageMathScriptExecutor.SectionKind.BATCH);
                    initial = false;
                }
                executeSingleFileBatchScript(namingStrategy, batchScriptExecutor, scriptFile);
            }
        } catch (Exception e) {
            LOGGER.error(JSolEx.message("error.batch.scripts.single.file"), e);
        }
    }

    private void executeSingleFileBatchScript(FileNamingStrategy namingStrategy, ImageMathScriptExecutor batchScriptExecutor, File scriptFile) {
        Platform.runLater(() -> owner.updateProgress(0, String.format(message("executing.script"), scriptFile)));
        ImageMathScriptResult result;
        try {
            result = batchScriptExecutor.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.BATCH);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        try {
            processScriptErrors(result);
            renderSingleFileBatchOutputs(namingStrategy, result);
        } finally {
            Platform.runLater(() -> owner.updateProgress(1, String.format(message("executing.script"), scriptFile)));
        }
    }

    private void renderSingleFileBatchOutputs(FileNamingStrategy namingStrategy, ImageMathScriptResult result) {
        if (result.imagesByLabel().isEmpty() && result.filesByLabel().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
                var tabPane = owner.getTabs();
                var imagesViewerTab = owner.getImagesViewerTab();
                tabPane.getTabs().add(imagesViewerTab);
                tabPane.getSelectionModel().select(imagesViewerTab);
        });
        result.imagesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var name = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "batch", entry.getValue());
            var outputFile = new File(outputDirectory.toFile(), name);
            onImageGenerated(new ImageGeneratedEvent(
                new GeneratedImage(GeneratedImageKind.IMAGE_MATH, entry.getKey(), outputFile.toPath(), entry.getValue(), null)
            ));
        });
        result.filesByLabel().entrySet().stream().parallel().forEach(entry -> {
            var fileOutput = entry.getValue();
            var baseName = namingStrategy.render(0, null, Constants.TYPE_PROCESSED, entry.getKey(), "batch", null);
            try {
                var displayPath = FilesUtils.saveAllFilesAndGetDisplayPath(fileOutput, outputDirectory, baseName);
                // Only fire display event for the designated display file
                if (displayPath != null) {
                    onFileGenerated(FileGeneratedEvent.of(GeneratedImageKind.IMAGE_MATH, entry.getKey(), displayPath));
                }
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
        });
    }

    private void processScriptErrors(ImageMathScriptResult result) {
        var invalidExpressions = result.invalidExpressions();
        var errorCount = invalidExpressions.size();
        if (errorCount > 0) {
            String message = invalidExpressions.stream()
                .map(invalidExpression -> "Expression '" + invalidExpression.label() + "' (" + invalidExpression.expression() + ") : " + invalidExpression.error().getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
            onNotification(new NotificationEvent(new Notification(
                Notification.AlertType.ERROR,
                message("error.processing.script"),
                message("script.errors." + (errorCount == 1 ? "single" : "many")),
                message
            )));
        }
    }

    sealed interface GraphData {

        XYChart<?, ?> chart();

        Supplier<? extends XYChart<?, ?>> graphFactory();

        String name();

        record ProfileData(XYChart<?, ?> chart, Supplier<? extends XYChart<?, ?>> graphFactory,
                           Consumer<? super PrintWriter> csvWriter) implements GraphData {
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
