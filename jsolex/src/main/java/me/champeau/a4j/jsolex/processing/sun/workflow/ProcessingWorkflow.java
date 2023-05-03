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
import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.CoronagraphTask;
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
    private static final double CIRCLE_EPSILON = 0.001d;

    private final ParallelExecutor executor;
    private final ProcessParams processParams;
    private final ImageWrapper32 rawImage;
    private final Double fps;
    private final ImageEmitter rawImagesEmitter;
    private final ImageEmitter debugImagesEmitter;
    private final ImageEmitter processedImagesEmitter;
    private final Broadcaster broadcaster;

    private double tilt;
    private double xyRatio;

    public ProcessingWorkflow(
            Broadcaster broadcaster,
            File rawImagesDirectory,
            File debugImagesDirectory,
            File processedImagesDirectory,
            ParallelExecutor executor,
            ImageWrapper32 rawImage,
            ProcessParams processParams,
            Double fps) {
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.rawImage = rawImage;
        this.processParams = processParams;
        this.fps = fps;
        this.rawImagesEmitter = new ImageEmitter(broadcaster, executor, rawImagesDirectory);
        this.debugImagesEmitter = new ImageEmitter(broadcaster, executor, debugImagesDirectory);
        this.processedImagesEmitter = new ImageEmitter(broadcaster, executor, processedImagesDirectory);
    }

    public void start() {
        rawImagesEmitter.newMonoImage("Raw (Linear)", "linear", rawImage, LinearStrechingStrategy.DEFAULT);
        var ellipseFittingTask = executor.submit(new EllipseFittingTask(broadcaster, rawImage, 10d));
        ellipseFittingTask.thenAccept(r -> performBandingCorrection(r, rawImage).thenAccept(bandingFixed -> geometryCorrection(r, bandingFixed)));

    }

    private void geometryCorrection(EllipseFittingTask.Result result, ImageWrapper32 bandingFixed) {
        var ellipse = result.ellipse();
        this.tilt = processParams.geometryParams().tilt().orElse(ellipse.tiltAngle());
        this.xyRatio = processParams.geometryParams().xyRatio().orElse(ellipse.xyRatio());
        float blackPoint = (float) estimateBlackPoint(bandingFixed.width(), bandingFixed.height(), ellipse, bandingFixed.data()) * 1.2f;
        var tiltDegrees = ellipse.tiltAngle() / Math.PI * 180;
        var geometryParams = processParams.geometryParams();
        boolean isTiltReliable = ellipse.isAlmostCircle(CIRCLE_EPSILON);
        var tiltString = String.format("%.2f", tiltDegrees);
        if (Math.abs(tiltDegrees) > 1 && isTiltReliable && !geometryParams.tilt().isPresent()) {
            broadcaster.broadcast(new SuggestionEvent("Tilt angle is " + tiltString + ". You should try to reduce it to less than 1°"));
        }
        var correctionAngle = isTiltReliable ? -ellipse.tiltAngle() : 0d;
        LOGGER.info("Tilt angle: {}°", tiltString);
        if (geometryParams.tilt().isPresent()) {
            correctionAngle = -geometryParams.tilt().getAsDouble() / 180d * Math.PI;
            LOGGER.info("Overriding tilt angle to {}°", String.format("%.2f", geometryParams.tilt().getAsDouble()));
        }
        if (!isTiltReliable) {
            LOGGER.info("Will not apply rotation correction as sun disk is almost a circle (and therefore tilt angle is not reliable)");
        }
        processedImagesEmitter.newMonoImage("Banding fixed", "banding-fixed", bandingFixed, new ArcsinhStretchingStrategy(blackPoint, 7, 20));
        executor.submit(new GeometryCorrector(broadcaster, bandingFixed, ellipse, correctionAngle, blackPoint, fps, geometryParams.xyRatio())).thenAccept(geometryFixed -> {
            broadcaster.broadcast(OutputImageDimensionsDeterminedEvent.of("geometry corrected", geometryFixed.width(), geometryFixed.height()));
            executor.submit(() -> produceEdgeDetectionImage(result, geometryFixed));
            executor.submit(() -> produceStretchedImage(blackPoint, geometryFixed));
            executor.submit(() -> produceProcessedImages(blackPoint, geometryFixed, processParams));
            executor.submit(() -> produceCoronagraph(blackPoint, geometryFixed, processParams));
        });
    }

    private void produceProcessedImages(float blackPoint, ImageWrapper32 corrected, ProcessParams params) {
        CutoffStretchingStrategy.DEFAULT.stretch(corrected.data());
        params.spectrumParams().ray().getColorCurve().ifPresent(curve -> {
            processedImagesEmitter.newColorImage("Colorized (" + curve.ray() + ")", "colorized", corrected, new ArcsinhStretchingStrategy(blackPoint, 10, 200), mono -> convertToRGB(curve, mono));
        });
    }

    private static float[][] convertToRGB(ColorCurve curve, float[] mono) {
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
    }

    private CompletableFuture<ImageWrapper32> performBandingCorrection(EllipseFittingTask.Result r, ImageWrapper32 geometryFixed) {
        return executor.submit(new ImageBandingCorrector(broadcaster, geometryFixed, r.ellipse(), processParams.bandingCorrectionParams()));
    }

    private Future<Void> produceStretchedImage(float blackPoint, ImageWrapper32 geometryFixed) {
        return processedImagesEmitter.newMonoImage("Stretched", "streched", geometryFixed, new ArcsinhStretchingStrategy(blackPoint, 10, 100));
    }

    private void produceEdgeDetectionImage(EllipseFittingTask.Result result, ImageWrapper32 geometryFixed) {
        if (processParams.debugParams().generateDebugImages()) {
            debugImagesEmitter.newMonoImage("Edge detection", "edge-detection", geometryFixed, LinearStrechingStrategy.DEFAULT, debugImage -> {
                var samples = result.samples();
                Arrays.fill(debugImage, 0f);
                fill(result.ellipse(), debugImage, geometryFixed.width(), (int) Constants.MAX_PIXEL_VALUE / 4);
                for (Point2D sample : samples) {
                    var x = sample.x();
                    var y = sample.y();
                    debugImage[(int) Math.round(x + y * geometryFixed.width())] = Constants.MAX_PIXEL_VALUE;
                }
            });
        }
    }

    private void produceCoronagraph(float blackPoint, ImageWrapper32 geometryFixed, ProcessParams params) {
        executor.submit(new EllipseFittingTask(broadcaster, geometryFixed, 6d))
                .thenAccept(fitting -> executor.submit(new CoronagraphTask(broadcaster, geometryFixed, fitting, blackPoint)).thenAccept(coronagraph -> {
                            processedImagesEmitter.newMonoImage("Coronagraph", "protus", coronagraph, LinearStrechingStrategy.DEFAULT);
                        }
                ));
    }

    public double getTilt() {
        return tilt;
    }

    public double getXyRatio() {
        return xyRatio;
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
