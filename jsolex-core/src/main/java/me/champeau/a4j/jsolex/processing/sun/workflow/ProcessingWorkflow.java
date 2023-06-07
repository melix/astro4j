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

import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.NegativeImageStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.CoronagraphTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageBandingCorrector;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.maybeDisplayTiltImage;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * This class encapsulates the processing workflow.
 */
public class ProcessingWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingWorkflow.class);

    private final ParallelExecutor executor;
    private final ProcessParams processParams;
    private final List<WorkflowState> states;
    private final WorkflowState state;
    private final Double fps;
    private final ImageEmitter rawImagesEmitter;
    private final ImageEmitter debugImagesEmitter;
    private final ImageEmitter processedImagesEmitter;
    private final Broadcaster broadcaster;
    private final int currentStep;

    private double tilt;
    private double xyRatio;

    public ProcessingWorkflow(
            Broadcaster broadcaster,
            File rawImagesDirectory,
            File debugImagesDirectory,
            File processedImagesDirectory,
            ParallelExecutor executor,
            List<WorkflowState> states,
            int currentStep,
            ProcessParams processParams,
            Double fps,
            ImageEmitterFactory imageEmitterFactory) {
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.states = states;
        this.state = states.get(currentStep);
        this.processParams = processParams;
        this.fps = fps;
        this.rawImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, rawImagesDirectory);
        this.debugImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, debugImagesDirectory);
        this.processedImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, processedImagesDirectory);
        this.currentStep = currentStep;
    }

    public void start() {
        var image = state.image();
        rawImagesEmitter.newMonoImage(WorkflowStep.RAW_IMAGE, message("raw"), "recon", image, CutoffStretchingStrategy.DEFAULT);
        rawImagesEmitter.newMonoImage(WorkflowStep.RAW_IMAGE, message("raw.linear"), "linear", image, LinearStrechingStrategy.DEFAULT);
        var ellipseFittingTask = executor.submit(new EllipseFittingTask(broadcaster, image, .25d, processParams, debugImagesEmitter).withPrefilter());
        ellipseFittingTask.thenAccept(r -> {
            state.recordResult(WorkflowStep.ELLIPSE_FITTING, r);
            performBandingCorrection(r, image).thenAccept(bandingFixed -> {
                state.recordResult(WorkflowStep.BANDING_CORRECTION, bandingFixed);
                geometryCorrection(r, bandingFixed);
            });
        });

    }

    private void geometryCorrection(EllipseFittingTask.Result result, ImageWrapper32 bandingFixed) {
        var ellipse = result.ellipse();
        var detectedRatio = ellipse.xyRatio();
        this.tilt = processParams.geometryParams().tilt().orElseGet(() -> estimateTilt(bandingFixed, ellipse));
        this.xyRatio = processParams.geometryParams().xyRatio().orElse(detectedRatio);
        LOGGER.info(message("detected.xy.ratio"), String.format("%.2f", detectedRatio));
        float blackPoint = (float) estimateBlackPoint(bandingFixed, ellipse) * 1.2f;
        var tiltDegrees = this.tilt / Math.PI * 180;
        var geometryParams = processParams.geometryParams();
        var tiltString = String.format("%.2f", tiltDegrees);
        if (Math.abs(tiltDegrees) > 1 && geometryParams.tilt().isEmpty()) {
            broadcaster.broadcast(new SuggestionEvent(message("tilt.angle") + " " + tiltString + ". " + message("try.less.one.degree")));
        }
        var correctionAngle = -this.tilt;
        LOGGER.info("Tilt angle: {}°", tiltString);
        if (geometryParams.tilt().isPresent()) {
            correctionAngle = -geometryParams.tilt().getAsDouble() / 180d * Math.PI;
            LOGGER.info(message("overriding.tilt"), String.format("%.2f", geometryParams.tilt().getAsDouble()));
        }
        var diskEllipse = Optional.<Ellipse>empty();
        if (currentStep > 0) {
            diskEllipse = states.get(0).findResult(WorkflowStep.GEOMETRY_CORRECTION).map(r -> ((GeometryCorrector.Result) r).disk());
        }
        executor.submit(new GeometryCorrector(broadcaster, bandingFixed, ellipse, correctionAngle, fps, geometryParams.xyRatio(), diskEllipse, blackPoint)).thenAccept(g -> {
            var geometryFixed = maybeSharpen(g);
            state.recordResult(WorkflowStep.GEOMETRY_CORRECTION, g);
            broadcaster.broadcast(OutputImageDimensionsDeterminedEvent.of(message("geometry.corrected"), geometryFixed.width(), geometryFixed.height()));
            processedImagesEmitter.newMonoImage(WorkflowStep.GEOMETRY_CORRECTION, message("disk"), "disk", geometryFixed, LinearStrechingStrategy.DEFAULT);
            if (state.isEnabled(WorkflowStep.STRECHED_IMAGE)) {
                executor.submit(() -> {
                    produceStretchedImage(blackPoint, geometryFixed);
                    produceNegativeImage(blackPoint, geometryFixed);
                });
            }
            if (state.isEnabled(WorkflowStep.COLORIZED_IMAGE)) {
                executor.submit(() -> produceColorizedImage(blackPoint, geometryFixed, processParams));
            }
            if (state.isEnabled(WorkflowStep.CORONAGRAPH)) {
                executor.submit(() -> produceCoronagraph(blackPoint, geometryFixed));
            }
            if (state.isEnabled(WorkflowStep.DOPPLER_IMAGE)) {
                produceDopplerImage(blackPoint);
            }
        });
    }

    private ImageWrapper32 maybeSharpen(GeometryCorrector.Result g) {
        if (processParams.geometryParams().isSharpen()) {
            return ImageWrapper32.fromImage(ImageMath.newInstance().convolve(g.corrected().asImage(), Kernel33.SHARPEN2));
        }
        return g.corrected();
    }

    private double estimateTilt(ImageWrapper32 bandingFixed, Ellipse ellipse) {
        var sample = 0.98 * ellipse.semiAxis().a();
        var center = ellipse.center();
        var y0 = (int) (center.b() - sample);
        var y1 = (int) (center.b() + sample);
        var x0 = -1;
        var x1 = -1;
        var width = bandingFixed.width();
        var height = bandingFixed.height();
        for (int x = 0; x < width; x++) {
            if (x0 == -1 && ellipse.isWithin(x, y0)) {
                x0 = x;
            }
            if (x1 == -1 && ellipse.isWithin(x, y1)) {
                x1 = x;
            }
            if (x0 != -1 && x1 != -1) {
                break;
            }
        }
        maybeDisplayTiltImage(processParams, processedImagesEmitter, bandingFixed, y0, y1, x0, x1, width, height, ellipse);
        double dx = x1 - x0;
        double dy = y1 - y0;
        return Math.atan(dx / dy);
    }

    private void produceDopplerImage(float blackPoint) {
        if (processParams.spectrumParams().ray() != SpectralRay.H_ALPHA) {
            return;
        }
        executor.submit(() -> {
            var dopplerShift = processParams.spectrumParams().dopplerShift();
            int lookupShift = processParams.spectrumParams().switchRedBlueChannels() ? -dopplerShift : dopplerShift;
            var first = states.stream().filter(s -> s.pixelShift() == lookupShift).findFirst();
            var second = states.stream().filter(s -> s.pixelShift() == -lookupShift).findFirst();
            first.ifPresent(s1 -> second.ifPresent(s2 -> {
                s1.findResult(WorkflowStep.GEOMETRY_CORRECTION).ifPresent(i1 -> s2.findResult(WorkflowStep.GEOMETRY_CORRECTION).ifPresent(i2 -> {
                    var grey1 = ((GeometryCorrector.Result) i1).corrected();
                    var grey2 = ((GeometryCorrector.Result) i2).corrected();
                    var width = grey1.width();
                    var height = grey1.height();
                    processedImagesEmitter.newColorImage(WorkflowStep.DOPPLER_IMAGE,
                            "Doppler",
                            "doppler",
                            new ArcsinhStretchingStrategy(blackPoint, 1, 20),
                            width,
                            height,
                            () -> toDopplerImage(width, height, grey1, grey2));
                }));
            }));
        });
    }

    private static float[][] toDopplerImage(int width, int height, ImageWrapper32 grey1, ImageWrapper32 grey2) {
        float[] r = new float[width * height];
        float[] g = new float[width * height];
        float[] b = new float[width * height];
        var d1 = grey1.data();
        var d2 = grey2.data();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var idx = x + y * width;
                r[idx] = d1[idx];
                g[idx] = (d1[idx] + d2[idx]) / 2;
                b[idx] = d2[idx];
            }
        }
        return new float[][]{r, g, b};
    }

    private void produceColorizedImage(float blackPoint, ImageWrapper32 corrected, ProcessParams params) {
        CutoffStretchingStrategy.DEFAULT.stretch(corrected.data());
        params.spectrumParams().ray().getColorCurve().ifPresent(curve ->
                processedImagesEmitter.newColorImage(WorkflowStep.COLORIZED_IMAGE, MessageFormat.format(message("colorized"), curve.ray()), "colorized", corrected, new ArcsinhStretchingStrategy(blackPoint, 10, 200), mono -> ImageUtils.convertToRGB(curve, mono))
        );
    }

    private CompletableFuture<ImageWrapper32> performBandingCorrection(EllipseFittingTask.Result r, ImageWrapper32 geometryFixed) {
        return executor.submit(new ImageBandingCorrector(broadcaster, geometryFixed, r.ellipse(), processParams.bandingCorrectionParams()));
    }

    private Future<Void> produceStretchedImage(float blackPoint, ImageWrapper32 geometryFixed) {
        return processedImagesEmitter.newMonoImage(WorkflowStep.STRECHED_IMAGE, message("stretched"), "streched", geometryFixed, new ArcsinhStretchingStrategy(blackPoint, 10, 100));
    }

    private Future<Void> produceNegativeImage(float blackPoint, ImageWrapper32 geometryFixed) {
        var negated = geometryFixed.asImage().copy();
        NegativeImageStrategy.DEFAULT.stretch(negated.data());
        return processedImagesEmitter.newMonoImage(WorkflowStep.NEGATIVE_IMAGE, message("negative"), "negative", ImageWrapper32.fromImage(negated), new ArcsinhStretchingStrategy(blackPoint, 10, 100));
    }

    private void produceCoronagraph(float blackPoint, ImageWrapper32 geometryFixed) {
        executor.submit(new EllipseFittingTask(broadcaster, geometryFixed, .25d))
                .thenAccept(fitting -> executor.submit(new CoronagraphTask(broadcaster, geometryFixed, fitting, blackPoint)).thenAccept(coronagraph -> {
                            processedImagesEmitter.newMonoImage(WorkflowStep.CORONAGRAPH, message("protus"), "protus", coronagraph, LinearStrechingStrategy.DEFAULT);
                            var data = geometryFixed.data();
                            var copy = new float[data.length];
                            System.arraycopy(data, 0, copy, 0, data.length);
                            LinearStrechingStrategy.DEFAULT.stretch(copy);
                            var width = geometryFixed.width();
                            var height = geometryFixed.height();
                            var ellipse = fitting.ellipse();
                            float[] mix = new float[data.length];
                            var coronaData = coronagraph.data();
                            var filtered = new float[coronaData.length];
                            System.arraycopy(coronaData, 0, filtered, 0, filtered.length);
                            prefilter(fitting.ellipse(), filtered, width, height);
                            for (int y = 0; y < height; y++) {
                                for (int x = 0; x < width; x++) {
                                    var index = x + y * width;
                                    if (ellipse.isWithin(x, y)) {
                                        mix[index] = copy[index];
                                    } else {
                                        mix[index] = filtered[index];
                                    }
                                }
                            }
                            var mixedImage = new ImageWrapper32(width, height, mix);
                            var colorCurve = processParams.spectrumParams().ray().getColorCurve();
                            if (colorCurve.isPresent()) {
                                var curve = colorCurve.get();
                                processedImagesEmitter.newColorImage(WorkflowStep.COLORIZED_IMAGE, message("mix"), "mix", mixedImage, new ArcsinhStretchingStrategy(blackPoint, 10, 200), mono -> ImageUtils.convertToRGB(curve, mono));
                            } else {
                                processedImagesEmitter.newMonoImage(WorkflowStep.CORONAGRAPH, message("mix"), "mix", mixedImage, LinearStrechingStrategy.DEFAULT);
                            }
                        })
                );
    }

    /**
     * The farther we are from the center, and outside of the sun
     * disk, the most likely it's either a protuberance or an artifact.
     * This reduces artifacts by decreasing pixel values for pixels
     * far from the limb.
     *
     * @param ellipse the circle representing the sun disk
     */
    private void prefilter(Ellipse ellipse, float[] filtered, int width, int height) {
        var center = ellipse.center();
        var cx = center.a();
        var cy = center.b();
        var radius = ellipse.semiAxis().a();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ellipse.isWithin(x, y)) {
                    var dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                    // compute distance to circle
                    var scale = Math.pow(Math.log(0.99 + dist / radius) / Math.log(2), 10);
                    filtered[x + y * width] /= scale;
                }
            }
        }
    }


    public double getTilt() {
        return tilt;
    }

    public double getXyRatio() {
        return xyRatio;
    }

    private static double estimateBlackPoint(ImageWrapper32 image, Ellipse ellipse) {
        var width = image.width();
        var height = image.height();
        var buffer = image.data();
        double blackEstimate = Double.MAX_VALUE;
        int cpt = 0;
        var cx = width / 2d;
        var cy = height / 2d;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!ellipse.isWithin(x, y)) {
                    var v = buffer[x + y * width];
                    if (v > 0) {
                        var offcenter = 2 * Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (width + height);
                        blackEstimate = blackEstimate + (offcenter * v - blackEstimate) / (++cpt);
                    }
                }
            }
        }
        LOGGER.info(message("black.estimate"), String.format("%.2f", blackEstimate));
        return blackEstimate;
    }

}
