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
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SingleModeProcessingEventListener implements ProcessingEventListener, ImageMathScriptExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleModeProcessingEventListener.class);

    private final Map<SuggestionEvent.SuggestionKind, String> suggestions = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Integer, ZoomableImageView> imageViews;
    private final JSolExInterface owner;
    private final String baseName;
    private final ProcessParams params;
    private final TabPane mainPane;
    private final AtomicInteger concurrentNotifications = new AtomicInteger();
    private ImageEmitter imageEmitter;
    private ImageMathScriptExecutor imageScriptExecutor;
    private long sd;
    private long ed;
    private int width;
    private int height;
    private ProcessParams processParams;

    public SingleModeProcessingEventListener(JSolExInterface owner, String baseName, ProcessParams params) {
        this.owner = owner;
        this.baseName = baseName;
        this.params = params;
        this.mainPane = owner.getMainPane();
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
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(mainPane.widthProperty());
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
                    event.getPayload().stretchingStrategy(),
                    event.getPayload().path().toFile(),
                    params
            );
            var scrollPane = new ScrollPane();
            scrollPane.setContent(viewer.getRoot());
            tab.setContent(scrollPane);
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
        processParams = payload.params();
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        var payload = e.getPayload();
        imageEmitter = payload.customImageEmitter();
        imageScriptExecutor = new DefaultImageScriptExecutor(payload.shiftImages()::get, Map.of());
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
    public ImageMathScriptResult execute(List<String> lines) {
        var result = imageScriptExecutor.execute(lines);
        ImageMathScriptExecutor.render(result, imageEmitter);
        return result;
    }
}
