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
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.maybeDisplayTiltImage;

/**
 * This class encapsulates the processing workflow.
 */
public class ProcessingWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingWorkflow.class);

    private final ForkJoinContext executor;
    private final ProcessParams processParams;
    private final List<WorkflowState> states;
    private final WorkflowState state;
    private final Double fps;
    private final ImageEmitter rawImagesEmitter;
    private final ImageEmitter debugImagesEmitter;
    private final ImageEmitter processedImagesEmitter;
    private final Broadcaster broadcaster;
    private final int currentStep;

    public ProcessingWorkflow(
            Broadcaster broadcaster,
            Path outputDirectory,
            ForkJoinContext executor,
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
        this.rawImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, Constants.TYPE_RAW, outputDirectory);
        this.debugImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, Constants.TYPE_DEBUG, outputDirectory);
        this.processedImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, Constants.TYPE_PROCESSED, outputDirectory);
        this.currentStep = currentStep;
    }

    public void start() {
        var image = state.image();
        rawImagesEmitter.newMonoImage(GeneratedImageKind.RAW, message(Constants.TYPE_RAW), "recon", image, CutoffStretchingStrategy.DEFAULT);
        rawImagesEmitter.newMonoImage(GeneratedImageKind.RAW_STRETCHED, message("raw.linear"), "linear", image, LinearStrechingStrategy.DEFAULT);
        var existingFitting = state.findResult(WorkflowResults.MAIN_ELLIPSE_FITTING);
        if (existingFitting.isEmpty()) {
            throw new ProcessingException("Expected to find the result of ellipse fitting but none was found");
        } else {
            EllipseFittingTask.Result r = (EllipseFittingTask.Result) existingFitting.get();
            var bandingFixed = performBandingCorrection(r, image).get();
            state.recordResult(WorkflowResults.BANDING_CORRECTION, bandingFixed);
            geometryCorrection(r, bandingFixed);
        }
    }

    private void logIfFirstStep(String message, Object... params) {
        if (currentStep == 0) {
            LOGGER.info(message, params);
        }
    }

    private void geometryCorrection(EllipseFittingTask.Result result, ImageWrapper32 bandingFixed) {
        var ellipse = result.ellipse();
        var tilt = processParams.geometryParams().tilt().orElseGet(() -> estimateTilt(bandingFixed, ellipse));
        float blackPoint = (float) estimateBlackPoint(bandingFixed, ellipse) * 1.2f;
        var tiltDegrees = tilt / Math.PI * 180;
        var geometryParams = processParams.geometryParams();
        var tiltString = String.format("%.2f", tiltDegrees);
        if (Math.abs(tiltDegrees) > 1 && geometryParams.tilt().isEmpty()) {
            broadcaster.broadcast(new SuggestionEvent(SuggestionEvent.SuggestionKind.TILT, message("tilt.angle") + " " + tiltString + ". " + message("try.less.one.degree")));
        }
        Double forcedTilt = null;
        logIfFirstStep("Tilt angle: {}°", tiltString);
        if (geometryParams.tilt().isPresent()) {
            forcedTilt = -geometryParams.tilt().getAsDouble() / 180d * Math.PI;
            LOGGER.info(message("overriding.tilt"), String.format("%.2f", geometryParams.tilt().getAsDouble()));
        }
        Double ratio = geometryParams.xyRatio().isPresent() ? geometryParams.xyRatio().getAsDouble() : null;
        executor.submitAndThen(new GeometryCorrector(broadcaster, bandingFixed, ellipse, forcedTilt, fps, ratio, blackPoint, processParams, debugImagesEmitter, state), g -> {
            var geometryFixed = maybeSharpen(g);
            state.recordResult(WorkflowResults.GEOMETRY_CORRECTION, g);
            broadcaster.broadcast(OutputImageDimensionsDeterminedEvent.of(message("geometry.corrected"), geometryFixed.width(), geometryFixed.height()));
            processedImagesEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED, message("disk"), "disk", geometryFixed, LinearStrechingStrategy.DEFAULT);
            executor.async(() -> {
                produceStretchedImage(blackPoint, geometryFixed);
                if (isMainShift() && shouldProduce(GeneratedImageKind.NEGATIVE)) {
                    produceNegativeImage(blackPoint, geometryFixed);
                }
            });
            if (isMainShift() && shouldProduce(GeneratedImageKind.COLORIZED)) {
                executor.async(() -> produceColorizedImage(blackPoint, geometryFixed, processParams));
            }
            if (isMainShift()) {
                executor.async(() -> produceCoronagraph(blackPoint, geometryFixed));
            }
            if (shouldProduce(GeneratedImageKind.DOPPLER) && isHighestShift()) {
                produceDopplerImage(blackPoint);
            }
            return null;
        }).get();
    }

    private boolean isMainShift() {
        return state.pixelShift() == processParams.spectrumParams().pixelShift();
    }

    private boolean isHighestShift() {
        var shifts = processParams.requestedImages().pixelShifts();
        return state.pixelShift() == shifts.get(shifts.size() - 1);
    }

    private ImageWrapper32 maybeSharpen(GeometryCorrector.Result g) {
        if (processParams.geometryParams().isSharpen()) {
            return ImageWrapper32.fromImage(ImageMath.newInstance().convolve(g.corrected().asImage(), Kernel33.SHARPEN2));
        }
        return g.corrected();
    }

    private double estimateTilt(ImageWrapper32 bandingFixed, Ellipse ellipse) {
        logIfFirstStep("Ellipse rotation angle is {}", ellipse.rotationAngle() * 180 / Math.PI);
        Point2D min = null;
        Point2D max = null;
        for (double alpha = -Math.PI; alpha <= Math.PI; alpha += 0.005d) {
            var p = ellipse.toCartesian(alpha);
            if (min == null || p.x() < min.x()) {
                min = p;
            }
            if (max == null || p.x() > max.x()) {
                max = p;
            }
        }
        double angle = 0;
        if (max != null) {
            var dx = max.x() - min.x();
            var dy = max.y() - min.y();
            angle = Math.atan2(dy, dx);
        }
        maybeDisplayTiltImage(processParams, processedImagesEmitter, bandingFixed, ellipse, min, max);
        return angle;
    }

    private void produceDopplerImage(float blackPoint) {
        if (!SpectralRay.H_ALPHA.equals(processParams.spectrumParams().ray())) {
            return;
        }
        executor.async(() -> {
            var dopplerShift = processParams.spectrumParams().dopplerShift();
            int lookupShift = processParams.spectrumParams().switchRedBlueChannels() ? -dopplerShift : dopplerShift;
            var first = states.stream().filter(s -> s.pixelShift() == lookupShift).findFirst();
            var second = states.stream().filter(s -> s.pixelShift() == -lookupShift).findFirst();
            first.ifPresent(s1 -> second.ifPresent(s2 -> {
                s1.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(i1 -> s2.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(i2 -> {
                    var grey1 = ((GeometryCorrector.Result) i1).corrected();
                    var grey2 = ((GeometryCorrector.Result) i2).corrected();
                    var width = grey1.width();
                    var height = grey1.height();
                    processedImagesEmitter.newColorImage(GeneratedImageKind.DOPPLER,
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
                processedImagesEmitter.newColorImage(GeneratedImageKind.COLORIZED, MessageFormat.format(message("colorized"), curve.ray()), "colorized", corrected, new ArcsinhStretchingStrategy(blackPoint, 10, 200), mono -> ImageUtils.convertToRGB(curve, mono))
        );
    }

    private Supplier<ImageWrapper32> performBandingCorrection(EllipseFittingTask.Result r, ImageWrapper32 geometryFixed) {
        return executor.submit(new ImageBandingCorrector(broadcaster, geometryFixed, r.ellipse(), processParams.bandingCorrectionParams()));
    }

    private Supplier<Void> produceStretchedImage(float blackPoint, ImageWrapper32 geometryFixed) {
        return processedImagesEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_STRETCHED, message("stretched"), "streched", geometryFixed, new ArcsinhStretchingStrategy(blackPoint, 10, 100));
    }

    private Supplier<Void> produceNegativeImage(float blackPoint, ImageWrapper32 geometryFixed) {
        var negated = geometryFixed.asImage().copy();
        NegativeImageStrategy.DEFAULT.stretch(negated.data());
        return processedImagesEmitter.newMonoImage(GeneratedImageKind.NEGATIVE, message("negative"), "negative", ImageWrapper32.fromImage(negated), new ArcsinhStretchingStrategy(blackPoint, 10, 100));
    }

    private void produceCoronagraph(float blackPoint, ImageWrapper32 geometryFixed) {
        state.<GeometryCorrector.Result>findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(result -> {
            Ellipse diskEllipse = result.correctedCircle();
            var produceVirtualEclipse = diskEllipse != null && shouldProduce(GeneratedImageKind.VIRTUAL_ECLIPSE);
            var produceMixed = diskEllipse != null && shouldProduce(GeneratedImageKind.MIXED);
            if (produceVirtualEclipse || produceMixed) {
                executor.submitAndThen(new CoronagraphTask(broadcaster, geometryFixed, diskEllipse, blackPoint), coronagraph -> {
                    processedImagesEmitter.newMonoImage(GeneratedImageKind.VIRTUAL_ECLIPSE, message("protus"), "protus", coronagraph, LinearStrechingStrategy.DEFAULT);
                    if (produceMixed) {
                        executor.async(() -> {
                            var data = geometryFixed.data();
                            var copy = new float[data.length];
                            System.arraycopy(data, 0, copy, 0, data.length);
                            LinearStrechingStrategy.DEFAULT.stretch(copy);
                            var width = geometryFixed.width();
                            var height = geometryFixed.height();
                            float[] mix = new float[data.length];
                            var coronaData = coronagraph.data();
                            var filtered = new float[coronaData.length];
                            System.arraycopy(coronaData, 0, filtered, 0, filtered.length);
                            prefilter(diskEllipse, filtered, width, height);
                            for (int y = 0; y < height; y++) {
                                for (int x = 0; x < width; x++) {
                                    var index = x + y * width;
                                    if (diskEllipse.isWithin(x, y)) {
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
                                processedImagesEmitter.newColorImage(GeneratedImageKind.MIXED, message("mix"), "mix", mixedImage, new ArcsinhStretchingStrategy(blackPoint, 10, 200), mono -> ImageUtils.convertToRGB(curve, mono));
                            } else {
                                processedImagesEmitter.newMonoImage(GeneratedImageKind.MIXED, message("mix"), "mix", mixedImage, LinearStrechingStrategy.DEFAULT);
                            }
                        });
                    }
                    return null;
                });
            }
        });
    }

    private boolean shouldProduce(GeneratedImageKind virtualEclipse) {
        return processParams.requestedImages().isEnabled(virtualEclipse);
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
