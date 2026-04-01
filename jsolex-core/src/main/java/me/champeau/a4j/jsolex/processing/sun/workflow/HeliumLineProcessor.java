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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.ImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.MidtoneTransferFunctionAutostretchStrategy;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.backgroundModel;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * A workflow dedicated to generating a helium image.
 */
public class HeliumLineProcessor {
    public record HeliumImageKind(boolean extracted) {
        public static final HeliumImageKind DIRECT = new HeliumImageKind(false);
        public static final HeliumImageKind EXTRACTED = new HeliumImageKind(true);
    }

    private static final double CONTINUUM_OFFSET_ANGSTROMS = 0.5;
    private static final float CONTINUUM_REINTRODUCTION_COEFF = 0.8f;
    private static final float CONTRAST_FACTOR = 2.5f;
    private static final float TARGET_DISK_MEDIAN = 0.7f;
    private static final float COLOR_GAMMA = 1.3f;
    private static final float COLOR_G = 0.92f;
    private static final float COLOR_B = 0.25f;

    private final ProcessParams processParams;
    private final ProgressOperation progressOperation;
    private final PixelShiftRange pixelShiftRange;
    private final Map<PixelShift, WorkflowState> imageByPixelShift;
    private final double heliumLineShift;
    private final ImageEmitter imageEmitter;
    private final Broadcaster broadcaster;

    public HeliumLineProcessor(ProcessParams processParams,
                               ProgressOperation progressOperation,
                               PixelShiftRange pixelShiftRange,
                               List<WorkflowState> imageList,
                               double heliumLineShift,
                               ImageEmitter imageEmitter,
                               Broadcaster broadcaster) {
        this.processParams = processParams;
        this.progressOperation = progressOperation;
        this.pixelShiftRange = pixelShiftRange;
        this.imageByPixelShift = imageList.stream().collect(Collectors.toMap(s -> new PixelShift(s.pixelShift()), s -> s, (e1, e2) -> e1, LinkedHashMap::new));
        this.heliumLineShift = heliumLineShift;
        this.imageEmitter = imageEmitter;
        this.broadcaster = broadcaster;
    }

    public void process() {
        var source = findEnhancedImage(new PixelShift(heliumLineShift));
        if (source == null) {
            return;
        }

        var evaluator = new ImageExpressionEvaluator(broadcaster, this::findEnhancedImage);
        evaluator.putInContext(ProgressOperation.class, progressOperation);
        evaluator.putInContext(PixelShiftRange.class, pixelShiftRange);
        var colorProfile = SpectralRayIO.loadDefaults()
                .stream()
                .filter(r -> SpectralRay.HELIUM_D3.label().equals(r.label()))
                .findFirst()
                .orElse(SpectralRay.HELIUM_D3)
                .label();
        if (source.unwrapToMemory() instanceof ImageWrapper32 direct) {
            direct.metadata().put(SpectralRay.class, SpectralRay.HELIUM_D3);
            direct.metadata().put(HeliumImageKind.class, HeliumImageKind.DIRECT);
            imageEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, "helium", message("helium.d3.direct"), "helium-direct", message("helium.direct.description"), direct);
            if (evaluator.functionCall(BuiltinFunction.COLORIZE, Map.of("img", direct, "profile", colorProfile)) instanceof RGBImage colorized) {
                imageEmitter.newColorImage(GeneratedImageKind.COLORIZED, "helium",
                        message("helium.d3.direct.colorized"), "helium-direct-colorized", message("helium.direct.description"), colorized.width(), colorized.height(), new HashMap<>(colorized.metadata()), () -> new float[][][]{colorized.r(), colorized.g(), colorized.b()});
            }
        }
        var continuumImage = createFixedShiftContinuum(evaluator);
        if (continuumImage == null) {
            return;
        }
        var continuum = evaluator.functionCall(BuiltinFunction.ELLIPSE_FIT, Map.of("img", continuumImage));
        var raw = evaluator.minus(source, continuum);
        if (raw instanceof ImageWrapper32 image) {
            LinearStrechingStrategy.DEFAULT.stretch(image);
            var ellipse = image.findMetadata(Ellipse.class).orElse(null);
            var bgModel = backgroundModel(image, 2, 2.5).orElse(null);
            if (bgModel == null) {
                bgModel = new ImageWrapper32(image.width(), image.height(), new float[image.height()][image.width()], Map.of());
            }
            bgModel = (ImageWrapper32) evaluator.mul(0.8, bgModel);
            bgModel = (ImageWrapper32) evaluator.functionCall(BuiltinFunction.DISK_FILL, Map.of("img", bgModel));
            image = (ImageWrapper32) evaluator.minus(image, bgModel);
            var protus = image.copy();
            protus = (ImageWrapper32) evaluator.functionCall(BuiltinFunction.DISK_FILL, Map.of("img", protus, "fill", 0));
            if (ellipse != null) {
                var protusData = protus.data();
                new MidtoneTransferFunctionAutostretchStrategy(-2.5, 0.3, (x, y) -> !ellipse.isWithin(x, y) && protusData[y][x] > 0).stretch(protus);
            } else {
                new ArcsinhStretchingStrategy(0, 10, 10).stretch(protus);
            }
            if (ellipse != null) {
                LinearStrechingStrategy.DEFAULT.stretch(image);

                // Local normalization with masked convolution
                var normKernel = Math.max(3, (int) ((ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 16) | 1);
                var imageMath = ImageMath.newInstance();
                var mask = new float[image.height()][image.width()];
                for (int y = 0; y < image.height(); y++) {
                    for (int x = 0; x < image.width(); x++) {
                        mask[y][x] = ellipse.isWithin(x, y) ? 1f : 0f;
                    }
                }
                // Blur image * mask (not raw image) so outside-disk values
                // don't contaminate the local mean estimate near the limb
                var maskedImageData = new float[image.height()][image.width()];
                var imgData = image.data();
                for (int y = 0; y < image.height(); y++) {
                    for (int x = 0; x < image.width(); x++) {
                        maskedImageData[y][x] = imgData[y][x] * mask[y][x];
                    }
                }
                var blurredImage = imageMath.boxBlur(new Image(image.width(), image.height(), maskedImageData), normKernel);
                var blurredMask = imageMath.boxBlur(new Image(image.width(), image.height(), mask), normKernel);
                localNormalize(image, ImageWrapper32.fromImage(blurredImage), ImageWrapper32.fromImage(blurredMask), ellipse);
                LinearStrechingStrategy.DEFAULT.stretch(image);
            }
            if (ellipse != null) {
                stretchDiskOnly(image, ellipse);
                var denoised = ImageMath.newInstance().convolve(image.asImage(), Kernel33.GAUSSIAN_BLUR);
                var srcData = denoised.data();
                var dstData = image.data();
                for (int y = 0; y < image.height(); y++) {
                    System.arraycopy(srcData[y], 0, dstData[y], 0, image.width());
                }
            }
            if (ellipse != null && continuum instanceof ImageWrapper32 continuumImg) {
                reintroduceContinuum(image, continuumImg, ellipse);
            }
            var blurred = ImageMath.newInstance().convolve(image.asImage(), Kernel33.GAUSSIAN_BLUR);
            image = (ImageWrapper32) evaluator.functionCall(BuiltinFunction.MAX, Map.of("list", List.of(protus, ImageWrapper32.fromImage(blurred))));
            if (ellipse != null) {
                darkenBackground(image, ellipse);
                var bandSize = (int) Math.max(32, (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 8);
                while (bandSize >= 64) {
                    adaptiveBandingReduction(image, bandSize, ellipse);
                    bandSize /= 2;
                }
            }
            image.metadata().put(SpectralRay.class, SpectralRay.HELIUM_D3);
            image.metadata().put(HeliumImageKind.class, HeliumImageKind.EXTRACTED);
            imageEmitter.newMonoImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, "helium", message("helium.d3.processed"), "helium-extracted", message("helium.extracted.description"), image);
            var colorResult = colorizeGoldenYellow(image);
            imageEmitter.newColorImage(GeneratedImageKind.COLORIZED, "helium",
                    message("helium.d3.processed.colorized"), "helium-extracted-colorized", message("helium.extracted.description"), image.width(), image.height(), new HashMap<>(image.metadata()), () -> colorResult);
        }
    }

    private static float[][][] colorizeGoldenYellow(ImageWrapper32 image) {
        var mono = image.data();
        var height = image.height();
        var width = image.width();
        float max = MAX_PIXEL_VALUE;
        var r = new float[height][width];
        var g = new float[height][width];
        var b = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Gamma > 1 darkens midtones to compensate for the high
                // perceived luminance of yellow, preserving feature contrast
                var v = (float) (max * Math.pow(mono[y][x] / max, COLOR_GAMMA));
                r[y][x] = v;
                g[y][x] = v * COLOR_G;
                b[y][x] = v * COLOR_B;
            }
        }
        return new float[][][]{r, g, b};
    }

    private void adaptiveBandingReduction(ImageWrapper32 image, int bandSize, Ellipse ellipse) {
        int maxIterations = 10;
        double convergenceRatio = 0.05;
        double firstPassDeviation = 0;
        for (int iter = 0; iter < maxIterations; iter++) {
            broadcaster.broadcast(progressOperation.update(
                    (double) iter / maxIterations,
                    message("helium.banding.reduction") + " (band=" + bandSize + ", pass=" + (iter + 1) + ")"
            ));
            var deviation = BandingReduction.reduceBanding(image.width(), image.height(), image.data(), bandSize, ellipse);
            if (iter == 0) {
                firstPassDeviation = deviation;
                if (firstPassDeviation < 1e-6) {
                    return;
                }
            } else if (deviation < firstPassDeviation * convergenceRatio) {
                return;
            }
        }
    }

    private static void stretchDiskOnly(ImageWrapper32 image, Ellipse ellipse) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        double sum = 0;
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    sum += data[y][x];
                    count++;
                }
            }
        }
        if (count == 0) {
            return;
        }
        float mean = (float) (sum / count);
        // Softplus transition width: controls how smoothly values
        // compress toward 0 instead of clipping
        float k = mean * 0.15f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    float raw = mean + (data[y][x] - mean) * CONTRAST_FACTOR;
                    // Softplus: smooth, continuous, no clipping
                    // Approaches raw for raw >> k, approaches 0 for raw << -k
                    data[y][x] = Math.min(MAX_PIXEL_VALUE, k * (float) Math.log1p(Math.exp(raw / k)));
                }
            }
        }
        // Adaptive gamma: normalize disk median to consistent brightness
        float normalizedMean = mean / MAX_PIXEL_VALUE;
        if (normalizedMean > 0.01f && normalizedMean < TARGET_DISK_MEDIAN) {
            double gamma = Math.log(TARGET_DISK_MEDIAN) / Math.log(normalizedMean);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    data[y][x] = (float) (MAX_PIXEL_VALUE * Math.pow(data[y][x] / MAX_PIXEL_VALUE, gamma));
                }
            }
        }
    }

    private static void reintroduceContinuum(ImageWrapper32 image, ImageWrapper32 continuum, Ellipse ellipse) {
        var data = image.data();
        var contData = continuum.data();
        var width = image.width();
        var height = image.height();
        float midpoint = MAX_PIXEL_VALUE / 2f;
        float max = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ellipse.isWithin(x, y)) {
                    float centered = contData[y][x] - midpoint;
                    data[y][x] = data[y][x] + CONTINUUM_REINTRODUCTION_COEFF * centered;
                    max = Math.max(max, data[y][x]);
                }
            }
        }
        if (max > 0) {
            float scale = MAX_PIXEL_VALUE / max;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (ellipse.isWithin(x, y)) {
                        data[y][x] = Math.max(0, data[y][x] * scale);
                    }
                }
            }
        }
    }

    private static void darkenBackground(ImageWrapper32 image, Ellipse ellipse) {
        var data = image.data();
        var width = image.width();
        var height = image.height();
        var center = ellipse.center();
        double cx = center.a();
        double cy = center.b();
        double ra = ellipse.semiAxis().a();
        double rb = ellipse.semiAxis().b();
        double feather = Math.min(ra, rb) * 0.05;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!ellipse.isWithin(x, y)) {
                    double dx = (x - cx) / ra;
                    double dy = (y - cy) / rb;
                    double dist = Math.sqrt(dx * dx + dy * dy) - 1.0;
                    double fade = Math.exp(-dist * Math.min(ra, rb) / feather);
                    data[y][x] *= (float) Math.max(0, fade);
                }
            }
        }
    }

    private static void localNormalize(ImageWrapper32 image, ImageWrapper32 blurredImage, ImageWrapper32 blurredMask, Ellipse ellipse) {
        var data = image.data();
        var imgData = blurredImage.data();
        var maskData = blurredMask.data();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (ellipse.isWithin(x, y) && maskData[y][x] > 0.1f) {
                    var localMean = imgData[y][x] / maskData[y][x];
                    if (localMean > 1f) {
                        data[y][x] = data[y][x] / localMean * 32768f;
                    }
                }
            }
        }
    }

    public static List<Double> computeContinuumPixelShifts(ProcessParams processParams) {
        var heliumD3 = SpectralRay.HELIUM_D3.wavelength();
        var lambda0 = processParams.spectrumParams().ray().wavelength();
        var pixelSize = processParams.observationDetails().pixelSize();
        var binning = processParams.observationDetails().binning();
        var instrument = processParams.observationDetails().instrument();
        if (pixelSize == null || binning == null) {
            return List.of();
        }
        var shifts = new ArrayList<Double>();
        for (var offsetAngstroms : new double[]{-CONTINUUM_OFFSET_ANGSTROMS, CONTINUUM_OFFSET_ANGSTROMS}) {
            var targetWavelength = Wavelen.ofAngstroms(heliumD3.angstroms() + offsetAngstroms);
            shifts.add(SpectrumAnalyzer.computePixelShift(pixelSize, binning, lambda0, targetWavelength, instrument));
        }
        return shifts;
    }

    private ImageWrapper32 createFixedShiftContinuum(ImageExpressionEvaluator evaluator) {
        var continuumShifts = computeContinuumPixelShifts(processParams);
        var images = new ArrayList<ImageWrapper>();
        for (var shift : continuumShifts) {
            var pixelShift = new PixelShift(shift);
            if (pixelShiftRange.includes(pixelShift.pixelShift())) {
                var img = findEnhancedImage(pixelShift);
                if (img != null) {
                    images.add(img);
                }
            }
        }
        if (images.isEmpty()) {
            return null;
        }
        if (images.size() == 1) {
            return (ImageWrapper32) images.getFirst().unwrapToMemory();
        }
        return (ImageWrapper32) evaluator.functionCall(BuiltinFunction.AVG, Map.of("list", images));
    }

    private ImageWrapper findEnhancedImage(PixelShift shift) {
        var workflowState = imageByPixelShift.get(shift);
        if (workflowState == null) {
            return null;
        }
        var result = workflowState.<GeometryCorrector.Result>findResult(WorkflowResults.GEOMETRY_CORRECTION);
        return result.map(GeometryCorrector.Result::enhanced).orElse(null);
    }
}
