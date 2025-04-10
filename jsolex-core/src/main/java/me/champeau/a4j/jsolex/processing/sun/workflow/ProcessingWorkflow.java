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
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.expr.impl.Colorize;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.params.AutoStretchParams;
import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.params.ContrastEnhancement;
import me.champeau.a4j.jsolex.processing.params.DeconvolutionMode;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.NegativeImageStrategy;
import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.tasks.CoronagraphTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageBandingCorrector;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Deconvolution;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy.neutralizeBg;
import static me.champeau.a4j.jsolex.processing.sun.ImageUtils.bilinearSmoothing;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.maybeDisplayTiltImage;

/**
 * This class encapsulates the processing workflow.
 */
public class ProcessingWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingWorkflow.class);

    private final ImageMath imageMath = ImageMath.newInstance();
    private final Header header;
    private final ProcessParams processParams;
    private final WorkflowState state;
    private final Double fps;
    private final ImageEmitter imagesEmitter;
    private final ProgressOperation rootOperation;
    private final Broadcaster broadcaster;
    private final int currentStep;
    private final Path serFile;

    public ProcessingWorkflow(
            ProgressOperation rootOperation,
            Broadcaster broadcaster,
            Path outputDirectory,
            List<WorkflowState> states,
            int currentStep,
            ProcessParams processParams,
            Double fps,
            ImageEmitterFactory imageEmitterFactory,
            Path serFile,
            Header header) {
        this.rootOperation = rootOperation;
        this.broadcaster = broadcaster;
        this.header = header;
        this.state = states.get(currentStep);
        this.processParams = processParams;
        this.fps = fps;
        this.imagesEmitter = imageEmitterFactory.newEmitter(broadcaster, outputDirectory);
        this.currentStep = currentStep;
        this.serFile = serFile;
    }

    private ProgressOperation newOperation(String task) {
        return rootOperation.createChild(task);
    }

    public void start() {
        emitReconImage();
        var existingFitting = state.findResult(WorkflowResults.MAIN_ELLIPSE_FITTING);
        if (existingFitting.isPresent()) {
            EllipseFittingTask.Result r = (EllipseFittingTask.Result) existingFitting.get();
            var ellipse = r.ellipse();
            var bandingFixed = performBandingCorrection(ellipse);
            state.recordResult(WorkflowResults.BANDING_CORRECTION, bandingFixed);
            geometryCorrection(r, bandingFixed);
        } else {
            var clahe = state.image().copy();
            var claheParams = processParams.claheParams();
            ClaheStrategy.of(claheParams).stretch(clahe);
            TransformationHistory.recordTransform(clahe, "CLAHE (tile size: " + claheParams.tileSize() + ", clip limit: " + claheParams.clipping() + ", bins: " + claheParams.bins() + ")");
            imagesEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, null, message("processed"), "clahe", String.format(message("contrast.enhanced.description"), state.pixelShift(), "CLAHE"), clahe);
        }
    }

    private void emitReconImage() {
        var reconstructed = state.image();
        reconstructed.metadata().put(PixelShift.class, new PixelShift(state.pixelShift()));
        imagesEmitter.newMonoImage(GeneratedImageKind.RAW, null, message(Constants.TYPE_RAW), "recon", String.format(message("recon.description"), state.pixelShift()), reconstructed);
    }

    private void logIfFirstStep(String message, Object... params) {
        if (currentStep == 0) {
            LOGGER.info(message, params);
        }
    }

    private Supplier<ImageWrapper32> imageSupplier(WorkflowResults step) {
        return () -> (ImageWrapper32) state.findResult(step).map(r -> {
            if (r instanceof GeometryCorrector.Result geo) {
                return geo.corrected().unwrapToMemory();
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
        var g = new GeometryCorrector(broadcaster, newOperation(message("geometry.correction")), imageSupplier(WorkflowResults.BANDING_CORRECTION), ellipse, forcedTilt, fps, ratio, blackPoint, processParams, imagesEmitter, state, header).get();
        var kind = GeneratedImageKind.GEOMETRY_CORRECTED;
        var geometryFixed = (ImageWrapper32) g.corrected().unwrapToMemory();
        if (state.pixelShift() == processParams.spectrumParams().continuumShift()) {
            kind = GeneratedImageKind.CONTINUUM;
        }
        if (!state.isInternal()) {
            imagesEmitter.newMonoImage(kind, null, message("disk"), "disk", String.format(message("geometry.corrected.description"), state.pixelShift()), geometryFixed);
        }
        g = performEnhancements(g);
        state.recordResult(WorkflowResults.GEOMETRY_CORRECTION, g);
        if (state.isInternal()) {
            return;
        }
        var enhanced = (ImageWrapper32) g.enhanced().unwrapToMemory();
        broadcaster.broadcast(OutputImageDimensionsDeterminedEvent.of(message("geometry.corrected"), geometryFixed.width(), geometryFixed.height()));
        boolean requiresStretched = shouldProduce(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED) || shouldProduce(GeneratedImageKind.COLORIZED) || shouldProduce(GeneratedImageKind.TECHNICAL_CARD);
        var stretched = requiresStretched ? produceStretchedImage(enhanced, processParams.claheParams(), processParams.autoStretchParams(), processParams.contrastEnhancement()) : null;
        if (stretched != null) {
            imagesEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, null, message("processed"), processParams.contrastEnhancement().name().toLowerCase(Locale.US),
                    String.format(message("contrast.enhanced.description"), state.pixelShift(), processParams.contrastEnhancement()), stretched);
        }
        var runnables = new ArrayList<Runnable>();
        if (isMainShift() && shouldProduce(GeneratedImageKind.NEGATIVE)) {
            runnables.add(() -> produceNegativeImage(enhanced));
        }
        if (isMainShift() && shouldProduce(GeneratedImageKind.COLORIZED)) {
            runnables.add(() -> produceColorizedImage(stretched, processParams));
        }
        if (isMainShift()) {
            runnables.add(() -> produceCoronagraph(blackPoint, enhanced));
        }
        if (isMainShift() && shouldProduce(GeneratedImageKind.TECHNICAL_CARD)) {
            produceTechnicalCard(stretched);
        }
        if (shouldProduce(GeneratedImageKind.ACTIVE_REGIONS)) {
            if (isMainShift()) {
                produceActiveRegionsImage(enhanced);
            }
            if (shouldProduce(GeneratedImageKind.CONTINUUM) && state.pixelShift() == processParams.spectrumParams().continuumShift()) {
                produceActiveRegionsImage(enhanced);
            }
        }
        if (processParams.spectrumParams().pixelShift() == 0 && isMainShift() && shouldProduce(GeneratedImageKind.REDSHIFT)) {
            geometryFixed.findMetadata(Redshifts.class).ifPresent(redshifts -> {
                if (!redshifts.redshifts().isEmpty()) {
                    runnables.add(() -> produceRedshiftsImage(geometryFixed, redshifts.redshifts()));
                }
            });
        }
        runnables.stream()
                .parallel()
                .forEach(Runnable::run);
    }

    private void produceActiveRegionsImage(ImageWrapper32 image) {
        imagesEmitter.newColorImage(
                GeneratedImageKind.ACTIVE_REGIONS,
                null,
                message("activeregions"),
                "activeregions",
                message("activeregions.description"),
                image,
                mono -> {
                    new LinearStrechingStrategy(0, .75f * Constants.MAX_PIXEL_VALUE).stretch(mono);
                    var rgb = RGBImage.toRGB(mono);
                    var r = rgb.r();
                    var g = rgb.g();
                    var b = rgb.b();
                    var activeRegionsMetadata = mono.findMetadata(ActiveRegions.class);
                    if (activeRegionsMetadata.isPresent()) {
                        var activeRegions = activeRegionsMetadata.get();
                        var img = ImageDraw.drawActiveRegions(rgb, activeRegions, true, true);
                        r = img.r();
                        g = img.g();
                        b = img.b();
                    }
                    return new float[][][]{r, g, b};
                });
    }

    private void produceRedshiftsImage(ImageWrapper32 geometryFixed, List<RedshiftArea> redshifts) {
        imagesEmitter.newColorImage(GeneratedImageKind.REDSHIFT,
                null, message("redshift"),
                "redshift",
                message("redshift.description"),
                geometryFixed, mono -> {
                    LinearStrechingStrategy.DEFAULT.stretch(mono);
                    var data = mono.data();
                    var r = ImageWrapper.copyData(data);
                    var g = ImageWrapper.copyData(data);
                    var b = ImageWrapper.copyData(data);
                    return new float[][][]{r, g, b};
                }, (gr, img) -> {
                    for (var redshift : redshifts) {
                        var x1 = redshift.x1();
                        var y1 = redshift.y1();
                        var x2 = redshift.x2();
                        var y2 = redshift.y2();
                        if (Math.abs(x2 - x1) < 32) {
                            // grow the area to the minimum size
                            x1 = redshift.maxX() - 16;
                            x2 = redshift.maxX() + 16;
                        }
                        if (Math.abs(y2 - y1) < 32) {
                            // grow the area to the minimum size
                            y1 = redshift.maxY() - 16;
                            y2 = redshift.maxY() + 16;
                        }
                        gr.setStroke(new BasicStroke(2));
                        gr.setColor(Color.RED);
                        gr.setFont(gr.getFont().deriveFont(24f));
                        gr.drawString(String.format("%s (%.2f km/s)", redshift.id(), redshift.kmPerSec()), x2 + 8, y2);
                        gr.drawRect(x1, y1, x2 - x1 + 1, y2 - y1 + 1);
                    }
                });
    }

    private boolean isMainShift() {
        return state.pixelShift() == processParams.spectrumParams().pixelShift();
    }

    private GeometryCorrector.Result performEnhancements(GeometryCorrector.Result g) {
        var corrected = (ImageWrapper32) g.corrected().unwrapToMemory();
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
                var decon = newOperation(message("deconvolution"));
                for (int i = 0; i < iterations; i++) {
                    broadcaster.broadcast(ProgressEvent.of(decon.update(1d / iterations * i, message("deconvolution") + " " + (i + 1) + "/" + iterations)));
                    image = deconv.richardsonLucy(image, kernel, 1);
                }
                enhancements.add("Richardson-Lucy deconvolution (" + iterations + " iterations, radius: " + radius + ", sigma: " + sigma + ")");
                broadcaster.broadcast(ProgressEvent.of(decon.update(1d, message("deconvolution"))));
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
        maybeDisplayTiltImage(processParams, imagesEmitter, bandingFixed, ellipse, min, max);
        return angle;
    }

    private void produceColorizedImage(ImageWrapper32 corrected, ProcessParams params) {
        var ray = params.spectrumParams().ray();
        ray.getColorCurve().ifPresentOrElse(curve ->
                        imagesEmitter.newColorImage(GeneratedImageKind.COLORIZED, null, MessageFormat.format(message("colorized"), curve.ray()), "colorized", String.format(message("colorized.description"), state.pixelShift()), corrected, monoImage -> {
                            var mono = monoImage.data();
                            var analysis = ImageAnalysis.of(monoImage, true);
                            ImageWrapper32 image = new ImageWrapper32(corrected.width(), corrected.height(), mono, MutableMap.of());
                            var bp = 0.5f*Math.max(0, analysis.avg() - analysis.stddev());
                            createStretchingForColorization(bp).stretch(image);
                            return ImageUtils.convertToRGB(curve, mono);
                        })
                , () -> {
                    if (ray.wavelength().nanos() > 0) {
                        imagesEmitter.newColorImage(GeneratedImageKind.COLORIZED, null, MessageFormat.format(message("colorized"), ray.label()), "colorized", String.format(message("colorized.description"), state.pixelShift()), corrected, monoImage -> {
                            var mono = monoImage.data();
                            var rgb = ray.toRGB();
                            var analysis = ImageAnalysis.of(monoImage, true);
                            var bp = 0.5f*Math.max(0, analysis.avg() - analysis.stddev());
                            createStretchingForColorization(bp).stretch(new ImageWrapper32(corrected.width(), corrected.height(), mono, MutableMap.of()));
                            return Colorize.doColorize(corrected.width(), corrected.height(), mono, rgb);
                        });
                    }
                });
    }

    private static StretchingStrategy createStretchingForColorization(float blackPoint) {
        return new ArcsinhStretchingStrategy(blackPoint, 3, 3);
    }

    private ImageWrapper32 performBandingCorrection(Ellipse ellipse) {
        return new ImageBandingCorrector(broadcaster, newOperation(message("banding.correction")), imageSupplier(WorkflowResults.ROTATED), ellipse, processParams.bandingCorrectionParams()).get();
    }

    private ImageWrapper32 produceStretchedImage(ImageWrapper32 geometryFixed, ClaheParams claheParams, AutoStretchParams autoStretchParams, ContrastEnhancement contrastEnhancement) {
        var stretched = geometryFixed.copy();
        switch (contrastEnhancement) {
            case AUTOSTRETCH -> {
                var autohistogramStrategy = new AutohistogramStrategy(autoStretchParams.gamma());
                autohistogramStrategy.stretch(stretched);
                TransformationHistory.recordTransform(stretched, "AutoStretch (gamma: " + autoStretchParams.gamma() + ")");
            }
            case CLAHE -> {
                ClaheStrategy.of(claheParams).stretch(stretched);
                TransformationHistory.recordTransform(stretched, "CLAHE (tile size: " + claheParams.tileSize() + ", clip limit: " + claheParams.clipping() + ", bins: " + claheParams.bins() + ")");
            }
        }
        return stretched;
    }

    private void produceTechnicalCard(ImageWrapper32 clahe) {
        var details = clahe.copy();
        var context = SolexVideoProcessor.createMetadata(processParams, serFile, null, header);
        var rotate = new Rotate(context, broadcaster);
        var crop = new Crop(context, broadcaster);
        var draw = new ImageDraw(context, broadcaster);
        var rotation = processParams.geometryParams().rotation();
        if (rotation.angle() != 0) {
            details = switch (rotation) {
                case LEFT -> (ImageWrapper32) rotate.rotateRadians(Map.of("img", details, "angle", -Math.PI / 2d, "bp", -1, "resize", 1));
                case RIGHT -> (ImageWrapper32) rotate.rotateRadians(Map.of("img", details, "angle", Math.PI / 2d, "bp", -1, "resize", 1));
                case NONE -> details;
            };
        }
        var cropped = crop.autocrop2(Map.of("img", details, "factor", 1.2d));
        var decorated = (ImageWrapper32) draw.drawSolarParameters(Map.of(
                "img", draw.drawObservationDetails(Map.of(
                        "img", draw.drawGlobe(Map.of("img", cropped))
                ))
        ));
        decorated.metadata().put(RotationKind.class, RotationKind.NONE);
        imagesEmitter.newMonoImage(GeneratedImageKind.TECHNICAL_CARD, null, message("technical.card"), "card", message("technical.card.description"), decorated);
    }

    private void produceNegativeImage(ImageWrapper32 geometryFixed) {
        var negated = geometryFixed.copy();
        new ClaheStrategy(128, 512, .8f).stretch(negated);
        NegativeImageStrategy.DEFAULT.stretch(negated);
        TransformationHistory.recordTransform(negated, "Negative");
        imagesEmitter.newMonoImage(GeneratedImageKind.NEGATIVE, null, message("negative"), "negative", String.format(message("negative.description"), state.pixelShift()), negated);
    }

    private void produceCoronagraph(float blackPoint, ImageWrapper32 geometryFixed) {
        state.<GeometryCorrector.Result>findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(result -> {
            Ellipse diskEllipse = result.correctedCircle();
            var produceVirtualEclipse = diskEllipse != null && shouldProduce(GeneratedImageKind.VIRTUAL_ECLIPSE);
            var produceMixed = diskEllipse != null && shouldProduce(GeneratedImageKind.MIXED);
            if (produceVirtualEclipse || produceMixed) {
                var coronagraph = new CoronagraphTask(broadcaster, newOperation(message("generating.images")), imageSupplier(WorkflowResults.GEOMETRY_CORRECTION), diskEllipse, blackPoint).get();
                imagesEmitter.newMonoImage(GeneratedImageKind.VIRTUAL_ECLIPSE, null, message("protus"), "protus", message("protus.description"), coronagraph);
                if (produceMixed) {
                    var data = geometryFixed.data();
                    var copy = ImageWrapper.copyData(data);
                    var work = new ImageWrapper32(geometryFixed.width(), geometryFixed.height(), copy, geometryFixed.metadata());
                    var stretched = produceStretchedImage(work, processParams.claheParams(), processParams.autoStretchParams(), processParams.contrastEnhancement());
                    new ArcsinhStretchingStrategy(blackPoint, 2, 2).stretch(stretched);
                    var width = geometryFixed.width();
                    var height = geometryFixed.height();
                    for (int y = 0; y < stretched.data().length; y++) {
                        System.arraycopy(stretched.data()[y], 0, copy[y], 0, width);
                    }
                    float[][] mix = new float[height][width];
                    var coronaData = coronagraph.data();
                    var filtered = ImageWrapper.copyData(coronaData);
                    neutralizeBg(new ImageWrapper32(width, height, filtered, Map.of()), 2, 3, 0.9f);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            if (diskEllipse.isWithin(x, y)) {
                                mix[y][x] = copy[y][x];
                            } else {
                                mix[y][x] = filtered[y][x];
                            }
                        }
                    }
                    for (int i = 0; i < 2; i++) {
                        bilinearSmoothing(diskEllipse, width, height, data);
                    }
                    var metadata = new HashMap<>(geometryFixed.metadata());
                    metadata.put(Ellipse.class, diskEllipse);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            float d = mix[y][x];
                            mix[y][x] = Math.max(0, Math.min(d, Constants.MAX_PIXEL_VALUE));
                        }
                    }
                    var mixedImage = new ImageWrapper32(width, height, mix, metadata);
                    var ray = processParams.spectrumParams().ray();
                    var colorCurve = ray.getColorCurve();
                    if (colorCurve.isPresent()) {
                        var curve = colorCurve.get();
                        imagesEmitter.newColorImage(GeneratedImageKind.MIXED, null, message("mix"), "mix", message("mix.description"), mixedImage, monoImage -> ImageUtils.convertToRGB(curve, monoImage.data()));
                    } else if (ray.wavelength().nanos() > 0) {
                        imagesEmitter.newColorImage(GeneratedImageKind.MIXED, null, message("mix"), "mix", message("mix.description"), mixedImage, monoImage -> {
                            var rgbColor = ray.toRGB();
                            return Colorize.doColorize(width, height, monoImage.data(), rgbColor);
                        });
                    } else {
                        imagesEmitter.newMonoImage(GeneratedImageKind.MIXED, null, message("mix"), "mix", message("mix.description"), mixedImage);
                    }
                }
            }
        });
    }

    private boolean shouldProduce(GeneratedImageKind kind) {
        return processParams.requestedImages().isEnabled(kind);
    }

    /**
     * The farther we are from the center, and outside of the sun
     * disk, the most likely it's either a protuberance or an artifact.
     * This reduces artifacts by decreasing pixel values for pixels
     * far from the limb.
     *
     * @param ellipse the circle representing the sun disk
     */
    private void prefilter(Ellipse ellipse, float[][] filtered, int width, int height) {
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
                    filtered[y][x] /= scale;
                }
            }
        }
    }

}
