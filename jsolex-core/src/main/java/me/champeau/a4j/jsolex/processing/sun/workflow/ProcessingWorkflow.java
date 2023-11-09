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
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.params.DeconvolutionMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.stretching.NegativeImageStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.CoronagraphTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageBandingCorrector;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Deconvolution;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.maybeDisplayTiltImage;

/**
 * This class encapsulates the processing workflow.
 */
public class ProcessingWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingWorkflow.class);

    private final ImageMath imageMath = ImageMath.newInstance();
    private final ForkJoinContext executor;
    private final Header header;
    private final ProcessParams processParams;
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
        ImageEmitterFactory imageEmitterFactory,
        Header header) {
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.header = header;
        this.state = states.get(currentStep);
        this.processParams = processParams;
        this.fps = fps;
        this.rawImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, Constants.TYPE_RAW, outputDirectory);
        this.debugImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, Constants.TYPE_DEBUG, outputDirectory);
        this.processedImagesEmitter = imageEmitterFactory.newEmitter(broadcaster, executor, Constants.TYPE_PROCESSED, outputDirectory);
        this.currentStep = currentStep;
    }

    public void start(Runnable onComplete) {
        try {
            var reconstructed = state.image();
            reconstructed.metadata().put(PixelShift.class, new PixelShift(state.pixelShift()));
            rawImagesEmitter.newMonoImage(GeneratedImageKind.RECONSTRUCTION, message(Constants.TYPE_RAW), "recon", reconstructed);
            var existingFitting = state.findResult(WorkflowResults.MAIN_ELLIPSE_FITTING);
            if (existingFitting.isPresent()) {
                EllipseFittingTask.Result r = (EllipseFittingTask.Result) existingFitting.get();
                var ellipse = r.ellipse();
                var bandingFixed = performBandingCorrection(ellipse).get();
                state.recordResult(WorkflowResults.BANDING_CORRECTION, bandingFixed);
                geometryCorrection(r, bandingFixed);
                state.<GeometryCorrector.Result>findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(geometryCorrected ->
                    rawImagesEmitter.newMonoImage(GeneratedImageKind.RAW_STRETCHED, message("raw.linear"), "linear", geometryCorrected.corrected())
                );
            } else {
                rawImagesEmitter.newMonoImage(GeneratedImageKind.RAW_STRETCHED, message("raw.linear"), "linear", reconstructed);
                var clahe = reconstructed.copy();
                var claheParams = processParams.claheParams();
                ClaheStrategy.of(claheParams).stretch(clahe.width(), clahe.height(), clahe.data());
                TransformationHistory.recordTransform(clahe, "CLAHE (tile size: " + claheParams.tileSize() + ", clip limit: " + claheParams.clipping() + ", bins: " + claheParams.bins() + ")");
                processedImagesEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, message("processed"), "clahe", clahe);
            }
        } finally {
            onComplete.run();
        }
    }

    private void logIfFirstStep(String message, Object... params) {
        if (currentStep == 0) {
            LOGGER.info(message, params);
        }
    }

    private Supplier<ImageWrapper32> imageSupplier(WorkflowResults step) {
        return () -> state.findResult(step).map(r -> {
            if (r instanceof GeometryCorrector.Result geo) {
                return geo.corrected();
            }
            if (r instanceof ImageWrapper32 img) {
                return img;
            }
            throw new IllegalStateException("Unexpected result type " + r);
        }).orElse(null);
    }

    private void geometryCorrection(EllipseFittingTask.Result result, ImageWrapper32 bandingFixed) {
        var ellipse = result.ellipse();
        var tilt = processParams.geometryParams().tilt().orElseGet(() -> estimateTilt(bandingFixed, ellipse));
        float blackPoint = (float) AnalysisUtils.estimateBlackPoint(bandingFixed, ellipse) * 1.2f;
        LOGGER.info(message("black.estimate"), String.format("%.2f", blackPoint));
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
        executor.submitAndThen(new GeometryCorrector(broadcaster, imageSupplier(WorkflowResults.BANDING_CORRECTION), ellipse, forcedTilt, fps, ratio, blackPoint, processParams, debugImagesEmitter, state, executor, header), g -> {
                var kind = GeneratedImageKind.GEOMETRY_CORRECTED;
                var geometryFixed = g.corrected();
                if (state.pixelShift() == Constants.CONTINUUM_SHIFT) {
                    kind = GeneratedImageKind.CONTINUUM;
                }
                if (!state.isInternal()) {
                    processedImagesEmitter.newMonoImage(kind, message("disk"), "disk", geometryFixed);
                }
                g = performEnhancements(g);
                var enhanced = g.enhanced();
                state.recordResult(WorkflowResults.GEOMETRY_CORRECTION, g);
                if (state.isInternal()) {
                    return null;
                }
                broadcaster.broadcast(OutputImageDimensionsDeterminedEvent.of(message("geometry.corrected"), geometryFixed.width(), geometryFixed.height()));
                if (enhanced != geometryFixed) {
                    processedImagesEmitter.newMonoImage(kind, message("enhanced"), "deconv", enhanced);
                }
                executor.async(() -> produceStretchedImage(enhanced, processParams.claheParams()));
                if (isMainShift() && shouldProduce(GeneratedImageKind.NEGATIVE)) {
                    executor.async(() -> produceNegativeImage(enhanced));
                }
                if (isMainShift() && shouldProduce(GeneratedImageKind.COLORIZED)) {
                    executor.async(() -> produceColorizedImage(blackPoint, enhanced, processParams));
                }
                if (isMainShift()) {
                    executor.async(() -> produceCoronagraph(blackPoint, enhanced));
                }
                return null;
            }).

            get();

    }

    private boolean isMainShift() {
        return state.pixelShift() == processParams.spectrumParams().pixelShift();
    }

    private GeometryCorrector.Result performEnhancements(GeometryCorrector.Result g) {
        var corrected = g.corrected();
        var image = corrected.asImage();
        List<String> enhancements = new ArrayList<>();
        if (processParams.geometryParams().deconvolutionMode() == DeconvolutionMode.RICHARDSON_LUCY) {
            var deconv = new Deconvolution(imageMath);
            var deconvolutionParams = processParams.geometryParams().richardsonLucyDeconvolutionParams();
            if (deconvolutionParams.isPresent()) {
                var radius = deconvolutionParams.get().radius();
                var sigma = deconvolutionParams.get().sigma();
                var kernel = Deconvolution.generateGaussianPSF(radius, sigma);
                var iterations = deconvolutionParams.get().iterations();
                for (int i = 0; i < iterations; i++) {
                    broadcaster.broadcast(ProgressEvent.of(1d / iterations * i, message("deconvolution") + " " + (i + 1) + "/" + iterations));
                    image = deconv.richardsonLucy(image, kernel, 1);
                }
                enhancements.add("Richardson-Lucy deconvolution (" + iterations + " iterations, radius: " + radius + ", sigma: " + sigma + ")");
                broadcaster.broadcast(ProgressEvent.of(1d, message("deconvolution")));
            }
        }
        if (processParams.geometryParams().isSharpen()) {
            enhancements.add("Sharpening");
            image = imageMath.convolve(image, Kernel33.SHARPEN2);
        }
        if (!enhancements.isEmpty()) {
            var metadata = new HashMap<>(corrected.metadata());
            var result = new ImageWrapper32(corrected.width(), corrected.height(), image.data(), metadata);
            for (String enhancement : enhancements) {
                TransformationHistory.recordTransform(result, enhancement);
            }
            return g.withEnhanced(result);
        }
        return g;
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

    private void produceColorizedImage(float blackPoint, ImageWrapper32 corrected, ProcessParams params) {
        params.spectrumParams().ray().getColorCurve().ifPresent(curve ->
            processedImagesEmitter.newColorImage(GeneratedImageKind.COLORIZED, MessageFormat.format(message("colorized"), curve.ray()), "colorized", corrected, mono -> {
                createStretchingForColorization(blackPoint).stretch(corrected.width(), corrected.height(), mono);
                return ImageUtils.convertToRGB(curve, mono);
            })
        );
    }

    private static ArcsinhStretchingStrategy createStretchingForColorization(float blackPoint) {
        return new ArcsinhStretchingStrategy(blackPoint, 10, 10);
    }

    private Supplier<ImageWrapper32> performBandingCorrection(Ellipse ellipse) {
        return executor.submit(new ImageBandingCorrector(broadcaster, imageSupplier(WorkflowResults.ROTATED), ellipse, processParams.bandingCorrectionParams()));
    }

    private Supplier<Void> produceStretchedImage(ImageWrapper32 geometryFixed, ClaheParams claheParams) {
        var clahe = geometryFixed.copy();
        ClaheStrategy.of(claheParams).stretch(clahe.width(), clahe.height(), clahe.data());
        TransformationHistory.recordTransform(clahe, "CLAHE (tile size: " + claheParams.tileSize() + ", clip limit: " + claheParams.clipping() + ", bins: " + claheParams.bins() + ")");
        var supplier = processedImagesEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, message("processed"), "clahe", clahe);
        if (shouldProduce(GeneratedImageKind.TECHNICAL_CARD) && isMainShift()) {
            produceTechnicalCard(clahe);
        }
        return supplier;
    }

    private void produceTechnicalCard(ImageWrapper32 clahe) {
        var details = clahe.copy();
        var context = SolexVideoProcessor.createMetadata(processParams);
        var rotate = new Rotate(executor, context);
        var crop = new Crop(executor, context);
        var draw = new ImageDraw(executor, context);
        var rotation = processParams.geometryParams().rotation();
        if (rotation.angle() != 0) {
            details = switch (rotation) {
                case LEFT -> (ImageWrapper32) rotate.rotateRadians(List.of(details, -Math.PI / 2d, -1, 1));
                case RIGHT -> (ImageWrapper32) rotate.rotateRadians(List.of(details, Math.PI / 2d, -1, 1));
                case NONE -> details;
            };
        }
        var cropped = crop.autocrop2(List.of(details, 1.2d));
        var decorated = (ImageWrapper32) draw.drawSolarParameters(List.of(
            draw.drawObservationDetails(List.of(
                draw.drawGlobe(List.of(cropped))
            ))
        ));
        decorated.metadata().put(RotationKind.class, RotationKind.NONE);
        processedImagesEmitter.newMonoImage(GeneratedImageKind.TECHNICAL_CARD, message("technical.card"), "card", decorated);
    }

    private Supplier<Void> produceNegativeImage(ImageWrapper32 geometryFixed) {
        var negated = geometryFixed.copy();
        new ClaheStrategy(128, 512, .8f).stretch(geometryFixed.width(), geometryFixed.height(), negated.data());
        NegativeImageStrategy.DEFAULT.stretch(geometryFixed.width(), geometryFixed.height(), negated.data());
        TransformationHistory.recordTransform(negated, "Negative");
        return processedImagesEmitter.newMonoImage(GeneratedImageKind.NEGATIVE, message("negative"), "negative", negated);
    }

    private void produceCoronagraph(float blackPoint, ImageWrapper32 geometryFixed) {
        state.<GeometryCorrector.Result>findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(result -> {
            Ellipse diskEllipse = result.correctedCircle();
            var produceVirtualEclipse = diskEllipse != null && shouldProduce(GeneratedImageKind.VIRTUAL_ECLIPSE);
            var produceMixed = diskEllipse != null && shouldProduce(GeneratedImageKind.MIXED);
            if (produceVirtualEclipse || produceMixed) {
                executor.submitAndThen(new CoronagraphTask(broadcaster, imageSupplier(WorkflowResults.GEOMETRY_CORRECTION), diskEllipse, blackPoint), coronagraph -> {
                    processedImagesEmitter.newMonoImage(GeneratedImageKind.VIRTUAL_ECLIPSE, message("protus"), "protus", coronagraph);
                    if (produceMixed) {
                        executor.async(() -> {
                            var data = geometryFixed.data();
                            var copy = new float[data.length];
                            System.arraycopy(data, 0, copy, 0, data.length);
                            createStretchingForColorization(blackPoint).stretch(geometryFixed.width(), geometryFixed.height(), copy);
                            ClaheStrategy.of(processParams.claheParams()).stretch(geometryFixed.width(), geometryFixed.height(), copy);
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
                            for (int i = 0; i < 2; i++) {
                                bilinearSmoothing(diskEllipse, width, height, data);
                            }
                            var mixedImage = new ImageWrapper32(width, height, mix, MutableMap.of(Ellipse.class, diskEllipse));
                            var colorCurve = processParams.spectrumParams().ray().getColorCurve();
                            if (colorCurve.isPresent()) {
                                var curve = colorCurve.get();
                                processedImagesEmitter.newColorImage(GeneratedImageKind.MIXED, message("mix"), "mix", mixedImage, mono -> ImageUtils.convertToRGB(curve, mono));
                            } else {
                                processedImagesEmitter.newMonoImage(GeneratedImageKind.MIXED, message("mix"), "mix", mixedImage);
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

}
