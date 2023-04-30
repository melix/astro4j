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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.app.util.Constants;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageBandingCorrector;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * This class encapsulates the processing workflow.
 */
public class ProcessingWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingWorkflow.class);

    private final ParallelExecutor executor;
    private final ProcessParams processParams;
    private final ImageWrapper32 rawImage;
    private final int width;
    private final int newHeight;
    private final float[] outputBuffer;
    private final Double fps;
    private final ImageEmitter rawImagesEmitter;
    private final ImageEmitter debugImagesEmitter;
    private final ImageEmitter processedImagesEmitter;
    private final Broadcaster broadcaster;

    public ProcessingWorkflow(
            Broadcaster broadcaster,
            File rawImagesDirectory,
            File debugImagesDirectory,
            File processedImagesDirectory,
            ParallelExecutor executor,
            ImageWrapper32 rawImage,
            int width,
            int newHeight,
            float[] outputBuffer,
            ProcessParams processParams,
            Double fps) {
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.rawImage = rawImage;
        this.width = width;
        this.newHeight = newHeight;
        this.outputBuffer = outputBuffer;
        this.processParams = processParams;
        this.fps = fps;
        this.rawImagesEmitter = new ImageEmitter(broadcaster, executor, rawImagesDirectory);
        this.debugImagesEmitter = new ImageEmitter(broadcaster, executor, debugImagesDirectory);
        this.processedImagesEmitter = new ImageEmitter(broadcaster, executor, processedImagesDirectory);
    }

    public void start() {
        rawImagesEmitter.newMonoImage("Raw (Linear)", "linear", rawImage, LinearStrechingStrategy.DEFAULT);
        var ellipseFittingTask = executor.submit(new EllipseFittingTask(broadcaster, rawImage));
        ellipseFittingTask.thenAccept(this::geometryCorrection);

    }

    private void geometryCorrection(EllipseFittingTask.Result result) {
        var ellipse = result.ellipse();
        float blackPoint = (float) estimateBlackPoint(width, newHeight, ellipse, outputBuffer) * 1.2f;
        var tiltDegrees = ellipse.tiltAngle() / Math.PI * 180;
        var tiltString = String.format("%.2f", tiltDegrees);
        if (Math.abs(tiltDegrees) > 1) {
            broadcaster.broadcast(new SuggestionEvent("Tilt angle is " + tiltString + ". You should try to reduce it to less than 1°"));
        }
        LOGGER.info("Tilt angle: {}°", tiltString);
        executor.submit(new GeometryCorrector(broadcaster, rawImage, ellipse, blackPoint, fps)).thenAccept(geometryFixed -> {
            broadcaster.broadcast(OutputImageDimensionsDeterminedEvent.of("geometry corrected", geometryFixed.width(), geometryFixed.height()));
            produceCoronagraph(blackPoint, geometryFixed);
            produceEdgeDetectionImage(result, ellipse, geometryFixed);
            produceStretchedImage(blackPoint, geometryFixed);
            performBandingCorrection(geometryFixed)
                    .thenAccept(corrected -> produceProcessedImages(blackPoint, geometryFixed, corrected, processParams))
                    .thenAccept(unused -> broadcaster.broadcast(new ProcessingDoneEvent(System.nanoTime())));
        });
    }

    private void produceProcessedImages(float blackPoint, ImageWrapper32 geometryFixed, float[] corrected, ProcessParams params) {
        CutoffStretchingStrategy.DEFAULT.stretch(corrected);
        var correctedImg = new ImageWrapper32(geometryFixed.width(), geometryFixed.height(), corrected);
        processedImagesEmitter.newMonoImage("Banding fixed", "banding-fixed", correctedImg, new ArcsinhStretchingStrategy(blackPoint, 7, 20));
        params.spectrumParams().ray().getColorCurve().ifPresent(curve -> {
            processedImagesEmitter.newColorImage("Colorized (" + curve.ray() + ")", "colorized", correctedImg, new ArcsinhStretchingStrategy(blackPoint, 10, 200), mono -> {
                LinearStrechingStrategy.DEFAULT.stretch(mono);
                float[] r = new float[mono.length];
                float[] g = new float[mono.length];
                float[] b = new float[mono.length];
                for (int i = 0; i < mono.length; i++) {
                    var rgb = curve.toRGB(mono[i]);
                    r[i] = (float) rgb.a();
                    g[i] = (float) rgb.b();
                    b[i] = (float) rgb.c();
                }
                return new float[][]{r, g, b};
            });
        });
    }

    private CompletableFuture<float[]> performBandingCorrection(ImageWrapper32 geometryFixed) {
        return executor.submit(new ImageBandingCorrector(broadcaster, geometryFixed));
    }

    private Future<Void> produceStretchedImage(float blackPoint, ImageWrapper32 geometryFixed) {
        return processedImagesEmitter.newMonoImage("Stretched", "streched", geometryFixed, new ArcsinhStretchingStrategy(blackPoint, 10, 100));
    }

    private void produceEdgeDetectionImage(EllipseFittingTask.Result result, Ellipse ellipse, ImageWrapper32 geometryFixed) {
        if (processParams.debugParams().generateDebugImages()) {
            debugImagesEmitter.newMonoImage("Edge detection", "edge-detection", geometryFixed, LinearStrechingStrategy.DEFAULT, debugImage -> {
                var samples = result.samples();
                Arrays.fill(debugImage, 0f);
                fill(ellipse, debugImage, geometryFixed.width(), (int) Constants.MAX_PIXEL_VALUE / 4);
                for (Point2D sample : samples) {
                    var x = sample.x();
                    var y = sample.y();
                    debugImage[(int) Math.round(x + y * geometryFixed.width())] = Constants.MAX_PIXEL_VALUE;
                }
            });
        }
    }

    private void produceCoronagraph(float blackPoint, ImageWrapper32 geometryFixed) {
        executor.submit(new EllipseFittingTask(broadcaster, geometryFixed)).thenAccept(fitting -> processedImagesEmitter.newMonoImage("Coronagraph", "protus", geometryFixed, LinearStrechingStrategy.DEFAULT, buffer -> {
            new ArcsinhStretchingStrategy(blackPoint * .25f, 5000, 20000).stretch(buffer);
            fill(fitting.ellipse(), buffer, geometryFixed.width(), 0);
        }));
    }

    private static double estimateBlackPoint(int width, int newHeight, Ellipse ellipse, float[] buffer) {
        double blackEstimate = 0d;
        int cpt = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < newHeight; y++) {
                if (!ellipse.isWithin(x, y)) {
                    blackEstimate = blackEstimate + (buffer[x + y * width] - blackEstimate) / (++cpt);
                }
            }
        }
        LOGGER.info("Black estimate {}", blackEstimate);
        return blackEstimate;
    }


    private static void fill(Ellipse ellipse, float[] image, int width, int color) {
        int height = image.length / width;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (ellipse.isWithin(x, y)) {
                    image[x + y * width] = color;
                }
            }
        }
    }

}
