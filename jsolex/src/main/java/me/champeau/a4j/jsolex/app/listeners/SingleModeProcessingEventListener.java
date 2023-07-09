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

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.ImageViewer;
import me.champeau.a4j.jsolex.app.jfx.ZoomableImageView;
import me.champeau.a4j.jsolex.processing.event.DebugEvent;
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
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SingleModeProcessingEventListener implements ProcessingEventListener, ImageMathScriptExecutor, Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleModeProcessingEventListener.class);

    private final Map<SuggestionEvent.SuggestionKind, String> suggestions = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Integer, ZoomableImageView> imageViews;
    private final JSolExInterface owner;
    private final String baseName;
    private final File serFile;
    private final ForkJoinContext forkJoinContext;
    private final ForkJoinContext ioContext;
    private final Path outputDirectory;
    private final ProcessParams params;
    private final TabPane mainPane;
    private final AtomicInteger concurrentNotifications = new AtomicInteger();
    private Map<Class, Object> scriptExecutionContext;
    private ImageEmitter imageEmitter;
    private ImageMathScriptExecutor imageScriptExecutor;
    private long sd;
    private long ed;
    private final Map<Integer, ImageWrapper32> shiftImages;
    private int width;
    private int height;

    public SingleModeProcessingEventListener(JSolExInterface owner,
                                             String baseName,
                                             File serFile,
                                             ForkJoinContext forkJoinContext,
                                             ForkJoinContext ioContext,
                                             Path outputDirectory,
                                             ProcessParams params) {
        this.owner = owner;
        this.baseName = baseName;
        this.serFile = serFile;
        this.forkJoinContext = forkJoinContext;
        this.ioContext = ioContext;
        this.outputDirectory = outputDirectory;
        this.params = params;
        this.mainPane = owner.getMainPane();
        this.shiftImages = new HashMap<>();
        imageViews = new HashMap<>();
        sd = 0;
        ed = 0;
        width = 0;
        height = 0;
    }

    private ImageViewer newImageViewer() {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "imageview");
        try {
            var node = (Node) fxmlLoader.load();
            var controller = (ImageViewer) fxmlLoader.getController();
            controller.init(node, mainPane, owner.getCpuExecutor());
            return controller;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        LOGGER.info(JSolEx.message("dimensions.determined"), event.getLabel(), event.getWidth(), event.getHeight());
        width = event.getWidth();
        height = event.getHeight();
    }

    private ZoomableImageView createImageView(int pixelShift) {
        var imageView = new ZoomableImageView();
        imageView.prefWidthProperty().bind(mainPane.widthProperty());
        imageView.setImage(new WritableImage(width, height));
        var colorAdjust = new ColorAdjust();
        colorAdjust.brightnessProperty().setValue(0.2);
        imageView.setEffect(colorAdjust);
        var scrollPane = new ScrollPane();
        scrollPane.setContent(imageView);
        BatchOperations.submit(() -> {
            String suffix = "";
            if (pixelShift != 0) {
                suffix = " (" + pixelShift + ")";
            }
            var tab = new Tab(JSolEx.message("image.reconstruction") + suffix, scrollPane);
            imageView.setParentTab(tab);
            mainPane.getTabs().add(tab);
        });
        return imageView;
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        var payload = event.getPayload();
        int y = payload.line();
        if (payload.display()) {
            var imageView = getOrCreateImageView(event);
            WritableImage image = (WritableImage) imageView.getImage();
            double[] line = payload.data();
            byte[] rgb = new byte[3 * line.length];
            for (int x = 0; x < line.length; x++) {
                int v = (int) Math.round(line[x]);
                byte c = (byte) (v >> 8);
                rgb[3 * x] = c;
                rgb[3 * x + 1] = c;
                rgb[3 * x + 2] = c;
            }
            var pixelformat = PixelFormat.getByteRgbInstance();
            onProgress(ProgressEvent.of((y + 1d) / height, JSolEx.message("reconstructing")));
            BatchOperations.submit(() -> {
                if (event.getPayload().pixelShift() == 0) {
                    mainPane.getSelectionModel().select(imageView.getParentTab());
                }
                image.getPixelWriter().setPixels(0, y, line.length, 1, pixelformat, rgb, 0, 3 * line.length);
            });
        } else {
            onProgress(ProgressEvent.of((y + 1d) / height, JSolEx.message("reconstructing")));
        }
    }

    private synchronized ZoomableImageView getOrCreateImageView(PartialReconstructionEvent event) {
        return imageViews.computeIfAbsent(event.getPayload().pixelShift(), this::createImageView);
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        BatchOperations.submit(() -> {
            var tab = new Tab(event.getPayload().title());
            var viewer = newImageViewer();
            viewer.fitWidthProperty().bind(mainPane.widthProperty());
            viewer.setTab(tab);
            viewer.setup(this,
                    baseName,
                    event.getPayload().image(),
                    event.getPayload().path().toFile(),
                    params
            );
            tab.setContent(viewer.getRoot());
            mainPane.getTabs().add(tab);
        });
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
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        var payload = e.getPayload();
        imageEmitter = payload.customImageEmitter();
        scriptExecutionContext = prepareExecutionContext(payload);
        shiftImages.putAll(payload.shiftImages());
        imageScriptExecutor = new DefaultImageScriptExecutor(shiftImages::get,
                Collections.unmodifiableMap(scriptExecutionContext),
                this
        );
        ed = payload.timestamp();
        var duration = java.time.Duration.ofNanos(ed - sd);
        double seconds = duration.toMillis() / 1000d;
        var sb = new StringBuilder();
        if (!suggestions.isEmpty()) {
            sb.append(JSolEx.message("suggestions") + " :\n");
            for (String suggestion : suggestions.values()) {
                sb.append("    - ").append(suggestion).append("\n");
            }
        }
        var finishedString = String.format(JSolEx.message("finished.in"), seconds);
        onNotification(new NotificationEvent(
                new Notification(
                        Notification.AlertType.INFORMATION,
                        JSolEx.message("processing.done"),
                        finishedString,
                        sb.toString()
                )));
        owner.prepareForScriptExecution(this, params);
        suggestions.clear();
        BatchOperations.submit(() -> owner.updateProgress(1.0, finishedString));
    }

    private static Map<Class, Object> prepareExecutionContext(ProcessingDoneEvent.Outcome payload) {
        Map<Class, Object> context = new HashMap<>();
        if (payload.ellipse() != null) {
            context.put(Ellipse.class, payload.ellipse());
        }
        if (payload.imageStats() != null) {
            context.put(ImageStats.class, payload.imageStats());
        }
        return context;
    }

    @Override
    public void onProgress(ProgressEvent e) {
        BatchOperations.submitOneOfAKind("progress", () -> {
            if (e.getPayload().progress() == 1) {
                owner.hideProgress();
            } else {
                owner.showProgress();
                owner.updateProgress(e.getPayload().progress(), e.getPayload().task());
            }
        });
    }

    @Override
    public void onDebug(DebugEvent<?> e) {

    }

    @Override
    public ImageMathScriptResult execute(String script) {
        // perform a first pass just to check if they are missing image shifts
        Set<Integer> missingShifts = determineShiftsRequiredInScript(script);
        missingShifts.removeAll(shiftImages.keySet());
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(missingShifts);
        }
        var result = imageScriptExecutor.execute(script);
        ImageMathScriptExecutor.render(result, imageEmitter);
        var invalidExpressions = result.invalidExpressions();
        var errorCount = invalidExpressions.size();
        if (errorCount > 0) {
            String message = invalidExpressions.stream()
                    .map(invalidExpression -> "Expression '" + invalidExpression.label() + "' (" + invalidExpression.expression() + ") : " + invalidExpression.error().getMessage())
                    .collect(Collectors.joining(System.lineSeparator()));
            onNotification(new NotificationEvent(new Notification(
                    Notification.AlertType.ERROR,
                    JSolEx.message("error.processing.script"),
                    JSolEx.message("script.errors." + (errorCount == 1 ? "single" : "many")),
                    message
            )));
        }
        return result;
    }

    private void restartProcessForMissingShifts(Set<Integer> missingShifts) {
        LOGGER.warn(JSolEx.message("restarting.process.missing.shifts"), missingShifts);;
        // restart processing to include missing images
        var tmpParams = params.withRequestedImages(
                new RequestedImages(Set.of(GeneratedImageKind.GEOMETRY_CORRECTED),
                        Stream.concat(params.requestedImages().pixelShifts().stream(), missingShifts.stream()).toList(),
                        missingShifts,
                        ImageMathParams.NONE)
        ).withExtraParams(params.extraParams().withAutosave(false));
        var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, tmpParams, forkJoinContext, ioContext, LocalDateTime.now(), false);
        solexVideoProcessor.addEventListener(new ProcessingEventListener() {
            @Override
            public void onProcessingDone(ProcessingDoneEvent e) {
                shiftImages.putAll(e.getPayload().shiftImages());
            }
        });
        solexVideoProcessor.process();
    }

    private Set<Integer> determineShiftsRequiredInScript(String script) {
        var collectingExecutor = new DefaultImageScriptExecutor(
                ShiftCollectingImageExpressionEvaluator.zeroImages(),
                scriptExecutionContext
        );
        var shiftCollectionResult = collectingExecutor.execute(script);
        Set<Integer> allShifts = new TreeSet<>();
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
        } else if (event instanceof DebugEvent<?> e) {
            onDebug(e);
        } else if (event instanceof VideoMetadataEvent e) {
            onVideoMetadataAvailable(e);
        }
    }
}
