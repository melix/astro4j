/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.spectrum.cloud;

import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.CancellationSupport;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.MathUtils;
import me.champeau.a4j.math.RowStrips;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.LeastSquares;
import me.champeau.a4j.math.regression.Polynomial2D;
import me.champeau.a4j.math.tuples.DoubleSextuplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.stream.DoubleStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Inverts the cloud model at every position of the solar disk, from a set of images
 * reconstructed at regularly spaced pixel shifts around the H-alpha line.
 */
public final class CloudInversion {
    private static final int RING_COUNT = 8;
    private static final int REFERENCE_STRIDE = 2;
    private static final int NOISE_STRIDE = 3;
    private static final double NOISE_MIN_MU = 0.5;
    private static final int CALIBRATION_PIXELS = 3000;
    private static final int SHIFT_PIXELS = 20000;
    private static final int SHIFT_DEGREE = 3;
    private static final double MAX_SHIFT_ANGSTROMS = 0.5;
    private static final int SHIFT_ITERATIONS = 3;
    private static final double SHIFT_REJECTION = 3;
    private static final double MIN_WING_RATIO = 0.6;
    private static final double REFERENCE_WING_RATIO = 0.8;
    private static final double POOR_FIT_FACTOR = 10;
    private static final double VELOCITY_HINT_HALF_WIDTH = 1.5;
    private static final double TRIPLE_THRESHOLD = 1.3;
    private static final int MIN_FITTED_SAMPLES = 15;
    private static final int WING_SAMPLES = 3;
    private static final double MAX_STRAY_LIGHT = 0.35;
    private static final double MAX_CALIBRATION_RESIDUAL = 0.05;
    private static final double FAST_FLOW_MIN_OPACITY = 0.1;
    private static final int INITIAL_GPU_BATCH = 16384;
    private static final int MIN_GPU_BATCH = 4096;
    private static final int MAX_GPU_BATCH = 262144;
    private static final long MAX_GPU_BUFFER_BYTES = 256L * 1024 * 1024;
    private static final double GPU_BATCH_SECONDS = 0.5;
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudInversion.class);

    private CloudInversion() {
    }

    /**
     * The options of the inversion.
     *
     * @param nonThermalSpeed the non-thermal speed, in km/s
     * @param fitHalfWidth the half width of the fitted window, in angstroms
     * @param minMu the minimum value of μ, the cosine of the heliocentric angle, of fitted positions
     * @param threeComponents whether to try a three component cloud
     * @param binning the size of the blocks of pixels which are averaged before the inversion, 1 to keep every pixel
     */
    public record Options(double nonThermalSpeed, double fitHalfWidth, double minMu, boolean threeComponents, int binning) {
        public static final Options DEFAULT = new Options(CloudPhysics.DEFAULT_NONTHERMAL_SPEED_KMS, 2.5, 0.3, true, 1);

        public Options {
            if (binning < 1) {
                throw new IllegalArgumentException(String.format(Locale.US, message("cloud.model.invalid.binning"), binning));
            }
        }

        public Options withBinning(int value) {
            return new Options(nonThermalSpeed, fitHalfWidth, minMu, threeComponents, value);
        }

        double tripleThreshold() {
            return threeComponents ? TRIPLE_THRESHOLD : Double.POSITIVE_INFINITY;
        }
    }

    /**
     * The input of the inversion.
     *
     * @param images the images, all of the same size, one per pixel shift
     * @param pixelShifts the pixel shift of each image, in increasing order
     * @param disk the solar disk in the images
     * @param lambda0 the wavelength of the line center, in angstroms
     * @param dispersion the nominal dispersion, in angstroms per pixel
     */
    public record Input(List<ImageWrapper> images, double[] pixelShifts, Ellipse disk, double lambda0, double dispersion) {
    }

    record Rings(double minMu, double[][] references, double[][] sigmas, double[][] profiles, double[][] shapes, double[][] derivatives) {
        void blend(double mu, double[] reference, double[] sigma, double[] profile, double[] shape, double[] derivative) {
            var count = references.length;
            var position = (mu - minMu) / (1 - minMu) * count - 0.5;
            var first = Math.clamp((int) Math.floor(position), 0, count - 1);
            var second = Math.min(first + 1, count - 1);
            var t = Math.clamp(position - first, 0, 1);
            mix(references[first], references[second], t, reference);
            mix(sigmas[first], sigmas[second], t, sigma);
            mix(profiles[first], profiles[second], t, profile);
            mix(shapes[first], shapes[second], t, shape);
            mix(derivatives[first], derivatives[second], t, derivative);
        }

        Rings withNoiseScale(double factor) {
            var scaled = new double[sigmas.length][];
            for (var r = 0; r < sigmas.length; r++) {
                scaled[r] = new double[sigmas[r].length];
                for (var i = 0; i < scaled[r].length; i++) {
                    scaled[r][i] = sigmas[r][i] * factor;
                }
            }
            return new Rings(minMu, references, scaled, profiles, shapes, derivatives);
        }

        private static void mix(double[] a, double[] b, double t, double[] out) {
            for (var i = 0; i < out.length; i++) {
                out[i] = (1 - t) * a[i] + t * b[i];
            }
        }
    }

    /**
     * The large scale wavelength shift of the whole profile over the disk, caused by the solar
     * rotation and by the instrument, modeled as a 2D polynomial of the position.
     *
     * @param coefficients the coefficients of the polynomial, or null when there is no shift
     * @param cx the column of the disk center
     * @param cy the row of the disk center
     * @param radius the radius of the disk
     */
    record ShiftField(double[] coefficients, double cx, double cy, double radius) {
        static final ShiftField NONE = new ShiftField(null, 0, 0, 1);

        double at(int x, int y, double[] terms) {
            if (coefficients == null) {
                return 0;
            }
            return Polynomial2D.evaluate(coefficients, (x - cx) / radius, (y - cy) / radius, SHIFT_DEGREE, terms);
        }
    }

    private record Disk(double cx, double cy, double radius) {
        static Disk of(Ellipse ellipse) {
            var center = ellipse.center();
            var semiAxis = ellipse.semiAxis();
            return new Disk(center.a(), center.b(), (semiAxis.a() + semiAxis.b()) / 2);
        }

        double mu(int x, int y) {
            var dx = (x - cx) / radius;
            var dy = (y - cy) / radius;
            var r2 = dx * dx + dy * dy;
            return r2 >= 1 ? -1 : Math.sqrt(1 - r2);
        }
    }

    /**
     * Performs the inversion.
     *
     * @param input the input
     * @param options the options
     * @param progress receives the progress, between 0 and 1
     * @return the result
     */
    public static CloudInversionResult invert(Input input, Options options, DoubleConsumer progress) {
        var factor = options.binning();
        var monotonic = new MonotonicProgress(progress);
        if (factor == 1) {
            return invertImages(input, options, monotonic, 1);
        }
        var reference = (ImageWrapper32) input.images().getFirst().unwrapToMemory();
        return invertImages(bin(input, factor), options, monotonic, factor).atFullSize(reference.width(), reference.height(), resultMetadata(reference), input.images());
    }

    /**
     * Forwards the progress reported by several threads, never going backwards.
     */
    private static final class MonotonicProgress implements DoubleConsumer {
        private final DoubleConsumer delegate;
        private double highest;

        private MonotonicProgress(DoubleConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void accept(double value) {
            if (value > highest) {
                highest = value;
                delegate.accept(value);
            }
        }
    }

    /**
     * Returns the metadata of the maps: those of the reconstructed images, except the pixel shift,
     * since a map combines all the wavelengths.
     */
    private static Map<Class<?>, Object> resultMetadata(ImageWrapper32 reference) {
        var metadata = new HashMap<>(reference.metadata());
        metadata.remove(PixelShift.class);
        return metadata;
    }

    /**
     * Averages the images by blocks of pixels. The center of a block of the binned images is at
     * {@code factor * x + (factor - 1) / 2} in the original images.
     */
    private static Input bin(Input input, int factor) {
        var images = new ArrayList<ImageWrapper>(input.images().size());
        for (var image : input.images()) {
            var source = (ImageWrapper32) image.unwrapToMemory();
            var width = source.width() / factor;
            var height = source.height() / factor;
            var data = source.data();
            var binned = new float[height][width];
            var norm = 1f / (factor * factor);
            RowStrips.forEach(height, (yStart, yEnd) -> {
                for (var y = yStart; y < yEnd; y++) {
                    var row = binned[y];
                    for (var dy = 0; dy < factor; dy++) {
                        var line = data[y * factor + dy];
                        for (var x = 0; x < width; x++) {
                            var sum = 0f;
                            for (var dx = 0; dx < factor; dx++) {
                                sum += line[x * factor + dx];
                            }
                            row[x] += sum;
                        }
                    }
                    for (var x = 0; x < width; x++) {
                        row[x] *= norm;
                    }
                }
            });
            images.add(new ImageWrapper32(width, height, binned, new HashMap<>(source.metadata())));
        }
        var disk = Disk.of(input.disk());
        var offset = (factor - 1) / 2d;
        var binnedDisk = circle((disk.cx() - offset) / factor, (disk.cy() - offset) / factor, disk.radius() / factor);
        return new Input(images, input.pixelShifts(), binnedDisk, input.lambda0(), input.dispersion());
    }

    static Ellipse circle(double cx, double cy, double radius) {
        var r2 = radius * radius;
        return Ellipse.ofCartesian(new DoubleSextuplet(1 / r2, 0, 1 / r2, -2 * cx / r2, -2 * cy / r2, (cx * cx + cy * cy) / r2 - 1));
    }

    private static CloudInversionResult invertImages(Input input, Options options, DoubleConsumer progress, int binning) {
        var data = input.images().stream().map(i -> ((ImageWrapper32) i.unwrapToMemory()).data()).toList();
        var reference = (ImageWrapper32) input.images().getFirst().unwrapToMemory();
        var width = reference.width();
        var height = reference.height();
        var disk = Disk.of(input.disk());
        var sampleCount = input.pixelShifts().length;
        var lambda0 = input.lambda0();

        var rawRings = computeRawRings(data, width, height, disk, options.minMu(), sampleCount);
        var calibration = InstrumentProfileEstimator.calibrate(input.pixelShifts(), rawRings[RING_COUNT - 1], input.dispersion(), lambda0);
        if (calibration.strayLight() > MAX_STRAY_LIGHT || calibration.rmsResidual() > MAX_CALIBRATION_RESIDUAL) {
            throw new IllegalStateException(String.format(Locale.US, message("cloud.model.calibration.implausible"),
                    100 * calibration.strayLight(), 100 * calibration.rmsResidual()));
        }
        var continuum = calibration.continuum();
        var offsets = new double[sampleCount];
        for (var i = 0; i < sampleCount; i++) {
            offsets[i] = calibration.offsetOf(input.pixelShifts()[i], input.dispersion());
        }
        var fitIndices = fitIndices(offsets, options.fitHalfWidth());
        if (fitIndices.length < MIN_FITTED_SAMPLES) {
            throw new IllegalStateException(String.format(Locale.US, message("cloud.model.not.enough.samples"), fitIndices.length, MIN_FITTED_SAMPLES));
        }
        var fitOffsets = new double[fitIndices.length];
        for (var i = 0; i < fitIndices.length; i++) {
            fitOffsets[i] = offsets[fitIndices[i]];
        }
        var grid = SpectralGrid.of(fitOffsets, calibration.instrumentSigma(), offset -> calibration.transmissionAt(lambda0, offset));
        var template = new double[grid.fineCount()];
        for (var j = 0; j < template.length; j++) {
            template[j] = InstrumentProfileEstimator.atlasAt(lambda0 + grid.fineOffsets()[j]);
        }
        var noise = estimateNoise(data, width, height, disk, fitIndices, continuum);
        var strayLight = calibration.strayLight();
        var rings = buildRings(rawRings, fitIndices, continuum, strayLight, noise, grid, template, options.minMu());
        var unshifted = new CloudInversionResult.FitContext(lambda0, options.nonThermalSpeed(), grid, fitIndices, input.images(), input.disk(), rings, ShiftField.NONE,
                continuum, strayLight, options.tripleThreshold(), binning);
        var draft = unshifted.withShiftField(estimateShiftField(unshifted, data, width, height, disk, options.minMu()));
        var noiseScale = calibrateNoise(draft, data, width, height, disk, options.minMu());
        var context = draft.withRings(rings.withNoiseScale(noiseScale));

        var maps = new EnumMap<CloudMap, float[][]>(CloudMap.class);
        for (var map : CloudMap.values()) {
            maps.put(map, new float[height][width]);
        }
        var gpu = GpuCloudFitter.create(grid, lambda0, options.tripleThreshold());
        var inverted = false;
        if (gpu.isPresent()) {
            try {
                invertOnGpu(gpu.get(), context, data, disk, options.minMu(), maps, progress);
                inverted = true;
            } catch (CancellationException e) {
                throw e;
            } catch (RuntimeException e) {
                LOGGER.warn("Cloud model inversion on the GPU failed, using the CPU instead", e);
                for (var map : maps.values()) {
                    for (var row : map) {
                        Arrays.fill(row, 0);
                    }
                }
            }
        }
        if (!inverted) {
            invertOnCpu(context, data, width, height, disk, options.minMu(), maps, progress);
        }
        var statistics = finish(maps, width, height, noise * noiseScale);
        var calibrationProfile = calibrationProfile(rawRings[RING_COUNT - 1], offsets, calibration, lambda0);
        return new CloudInversionResult(width, height, resultMetadata(reference), maps, calibration, statistics, calibrationProfile, context);
    }

    /**
     * Fits the profile at one position of the grid of the inversion again.
     *
     * @param context the context of the inversion
     * @param x the column, in the grid of the inversion
     * @param y the row, in the grid of the inversion
     * @return the fit, or empty if the position is outside the disk
     */
    static Optional<CloudInversionResult.PixelFit> refit(CloudInversionResult.FitContext context, int x, int y) {
        var mu = Disk.of(context.disk()).mu(x, y);
        if (mu < 0) {
            return Optional.empty();
        }
        return new Worker(context).details(blockAverage(context.sources(), x, y, context.binning()), x, y, mu);
    }

    /**
     * Returns, for each image, an array holding the average of the block of pixels which
     * corresponds to one position of the binned grid, at this position. The other positions
     * of the arrays are not allocated.
     */
    private static List<float[][]> blockAverage(List<ImageWrapper> sources, int x, int y, int factor) {
        return sources.stream().map(image -> {
            var data = ((ImageWrapper32) image.unwrapToMemory()).data();
            if (factor == 1) {
                return data;
            }
            var sum = 0f;
            for (var dy = 0; dy < factor; dy++) {
                for (var dx = 0; dx < factor; dx++) {
                    sum += data[y * factor + dy][x * factor + dx];
                }
            }
            var rows = new float[y + 1][];
            rows[y] = new float[x + 1];
            rows[y][x] = sum / (factor * factor);
            return rows;
        }).toList();
    }

    private static void invertOnCpu(CloudInversionResult.FitContext context, List<float[][]> data, int width, int height, Disk disk, double minMu,
                                    EnumMap<CloudMap, float[][]> maps, DoubleConsumer progress) {
        var rowsDone = new AtomicInteger();
        var cancelFlag = CancellationSupport.currentFlag();
        RowStrips.forEach(height, (yStart, yEnd) -> CancellationSupport.runWith(cancelFlag, () -> {
            var worker = new Worker(context);
            for (var y = yStart; y < yEnd; y++) {
                CancellationSupport.checkCancelled();
                for (var x = 0; x < width; x++) {
                    var mu = disk.mu(x, y);
                    if (mu < minMu) {
                        continue;
                    }
                    var solution = worker.solve(data, x, y, mu);
                    if (solution.isPresent()) {
                        store(maps, x, y, solution.get());
                    } else {
                        maps.get(CloudMap.STATUS)[y][x] = CloudMap.STATUS_INVALID_PROFILE;
                    }
                }
                progress.accept((double) rowsDone.incrementAndGet() / height);
            }
        }));
    }

    /**
     * Fits the profiles on the GPU, by batches. The profiles are prepared on the CPU, and the
     * size of the batches adapts so that each kernel runs for a fraction of a second, which keeps
     * the display responsive and avoids the timeouts of the drivers.
     */
    private static void invertOnGpu(GpuCloudFitter gpu, CloudInversionResult.FitContext context, List<float[][]> data, Disk disk, double minMu,
                                    EnumMap<CloudMap, float[][]> maps, DoubleConsumer progress) {
        var status = maps.get(CloudMap.STATUS);
        var width = status[0].length;
        var height = status.length;
        var xs = new int[width * height];
        var ys = new int[width * height];
        var total = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (disk.mu(x, y) >= minMu) {
                    xs[total] = x;
                    ys[total] = y;
                    total++;
                }
            }
        }
        var maxBatch = (int) Math.max(MIN_GPU_BATCH, Math.min(MAX_GPU_BATCH, MAX_GPU_BUFFER_BYTES / ((long) gpu.fineCount() * Float.BYTES)));
        var batchSize = Math.min(INITIAL_GPU_BATCH, maxBatch);
        var start = 0;
        while (start < total) {
            CancellationSupport.checkCancelled();
            var count = Math.min(batchSize, total - start);
            var batch = GpuCloudFitter.Batch.allocate(count, gpu.samples(), gpu.fineCount());
            var valid = new boolean[count];
            var offset = start;
            RowStrips.forEach(count, (from, to) -> {
                var worker = new Worker(context);
                for (var k = from; k < to; k++) {
                    var x = xs[offset + k];
                    var y = ys[offset + k];
                    valid[k] = worker.export(data, x, y, disk.mu(x, y), batch, k);
                }
            });
            var begin = System.nanoTime();
            var fits = gpu.fit(batch);
            var seconds = (System.nanoTime() - begin) / 1e9;
            RowStrips.forEach(count, (from, to) -> {
                var worker = new Worker(context);
                for (var k = from; k < to; k++) {
                    var x = xs[offset + k];
                    var y = ys[offset + k];
                    var solution = valid[k] ? worker.solution(data, x, y, disk.mu(x, y), fits[k]) : Optional.<Solution>empty();
                    if (solution.isPresent()) {
                        store(maps, x, y, solution.get());
                    } else {
                        status[y][x] = CloudMap.STATUS_INVALID_PROFILE;
                    }
                }
            });
            start += count;
            progress.accept((double) start / total);
            batchSize = (int) Math.clamp(batchSize * GPU_BATCH_SECONDS / Math.max(seconds, 1e-3), Math.min(MIN_GPU_BATCH, maxBatch), maxBatch);
        }
    }

    /**
     * Measures the shift of the whole profile on a subset of the disk and fits a smooth surface
     * to it, rejecting outliers. The background of each position is then shifted by this surface,
     * so that the velocities of the clouds are measured relative to the surrounding atmosphere.
     */
    private static ShiftField estimateShiftField(CloudInversionResult.FitContext context, List<float[][]> data, int width, int height, Disk disk, double minMu) {
        var worker = new Worker(context);
        var xsBuilder = DoubleStream.builder();
        var ysBuilder = DoubleStream.builder();
        var shiftsBuilder = DoubleStream.builder();
        forEachSampledPosition(width, height, disk, minMu, SHIFT_PIXELS, (x, y, mu) -> {
            var shift = worker.measureShift(data, x, y, mu);
            if (Double.isFinite(shift) && Math.abs(shift) < MAX_SHIFT_ANGSTROMS) {
                xsBuilder.add((x - disk.cx()) / disk.radius());
                ysBuilder.add((y - disk.cy()) / disk.radius());
                shiftsBuilder.add(shift);
            }
        });
        var xs = xsBuilder.build().toArray();
        var ys = ysBuilder.build().toArray();
        var shifts = shiftsBuilder.build().toArray();
        var termCount = Polynomial2D.termCount(SHIFT_DEGREE);
        if (shifts.length < 10 * termCount) {
            return ShiftField.NONE;
        }
        var terms = new double[termCount];
        var keep = new boolean[shifts.length];
        Arrays.fill(keep, true);
        var residuals = new double[shifts.length];
        double[] coefficients = null;
        for (var iteration = 0; iteration < SHIFT_ITERATIONS; iteration++) {
            var fit = new LeastSquares(termCount);
            for (var i = 0; i < shifts.length; i++) {
                if (keep[i]) {
                    Polynomial2D.terms(xs[i], ys[i], SHIFT_DEGREE, terms);
                    fit.add(terms, shifts[i]);
                }
            }
            var solved = fit.solve();
            if (solved == null) {
                break;
            }
            coefficients = solved;
            for (var i = 0; i < shifts.length; i++) {
                residuals[i] = Math.abs(shifts[i] - Polynomial2D.evaluate(coefficients, xs[i], ys[i], SHIFT_DEGREE, terms));
            }
            var limit = SHIFT_REJECTION * 1.4826 * median(residuals);
            for (var i = 0; i < shifts.length; i++) {
                keep[i] = residuals[i] <= limit;
            }
        }
        if (coefficients == null) {
            return ShiftField.NONE;
        }
        return new ShiftField(coefficients, disk.cx(), disk.cy(), disk.radius());
    }

    /**
     * Fits a subset of the disk to compare the residuals with the estimated noise, and returns
     * the factor to apply to the noise so that the median reduced chi2 is 1. The noise estimated
     * from neighbouring pixels is too low because the reconstruction correlates them.
     */
    private static double calibrateNoise(CloudInversionResult.FitContext context, List<float[][]> data, int width, int height, Disk disk, double minMu) {
        var worker = new Worker(context);
        var values = DoubleStream.builder();
        forEachSampledPosition(width, height, disk, minMu, CALIBRATION_PIXELS, (x, y, mu) ->
                worker.solveSingle(data, x, y, mu).ifPresent(fit -> values.add(fit.reducedChi2())));
        var median = median(values.build().toArray());
        return median > 0 ? Math.sqrt(median) : 1;
    }

    @FunctionalInterface
    private interface PositionConsumer {
        void accept(int x, int y, double mu);
    }

    /**
     * Visits a regular subset of about the given number of positions of the disk.
     */
    private static void forEachSampledPosition(int width, int height, Disk disk, double minMu, int count, PositionConsumer consumer) {
        var area = Math.PI * disk.radius() * disk.radius();
        var stride = Math.max(1, (int) Math.sqrt(area / count));
        for (var y = stride / 2; y < height; y += stride) {
            for (var x = stride / 2; x < width; x += stride) {
                var mu = disk.mu(x, y);
                if (mu >= minMu) {
                    consumer.accept(x, y, mu);
                }
            }
        }
    }

    private static final class Worker {
        private final CloudInversionResult.FitContext context;
        private final CloudModelFitter fitter;
        private final double[] observed;
        private final double[] reference;
        private final double[] sigma;
        private final double[] profile;
        private final double[] shape;
        private final double[] derivative;
        private final double[] sampledDerivative;
        private final double[] shiftTerms;

        private Worker(CloudInversionResult.FitContext context) {
            this.context = context;
            var model = new CloudModel(context.grid(), context.lambda0());
            this.fitter = new CloudModelFitter(model);
            var n = context.fitIndices().length;
            this.observed = new double[n];
            this.reference = new double[n];
            this.sigma = new double[n];
            this.profile = new double[context.grid().fineCount()];
            this.shape = new double[context.grid().fineCount()];
            this.derivative = new double[context.grid().fineCount()];
            this.sampledDerivative = new double[n];
            this.shiftTerms = new double[Polynomial2D.termCount(SHIFT_DEGREE)];
        }

        private Optional<CloudModelFitter.Observation> observe(List<float[][]> data, int x, int y, double mu) {
            var indices = context.fitIndices();
            for (var i = 0; i < indices.length; i++) {
                var value = data.get(indices[i])[y][x] / context.continuum() - context.strayLight();
                if (!(value > 0)) {
                    return Optional.empty();
                }
                observed[i] = value;
            }
            context.rings().blend(mu, reference, sigma, profile, shape, derivative);
            if (wing(observed) < MIN_WING_RATIO * wing(reference)) {
                return Optional.empty();
            }
            var shift = context.shiftField().at(x, y, shiftTerms);
            if (shift != 0) {
                for (var j = 0; j < profile.length; j++) {
                    profile[j] -= shift * derivative[j];
                }
                context.grid().sample(derivative, sampledDerivative);
                for (var i = 0; i < reference.length; i++) {
                    reference[i] -= shift * sampledDerivative[i];
                }
            }
            return Optional.of(new CloudModelFitter.Observation(observed, sigma, reference, new CloudModel.Background(profile, shape, derivative)));
        }

        private double measureShift(List<float[][]> data, int x, int y, double mu) {
            return observe(data, x, y, mu).map(fitter::estimateShift).orElse(Double.NaN);
        }

        private Optional<CloudModelFitter.Fit> solveSingle(List<float[][]> data, int x, int y, double mu) {
            return observe(data, x, y, mu).map(observation -> fitter.fitBest(observation, hint(observation), Double.POSITIVE_INFINITY));
        }

        private Optional<Solution> solve(List<float[][]> data, int x, int y, double mu) {
            return observe(data, x, y, mu).map(observation -> solution(observation, fitter.fitBest(observation, hint(observation), context.tripleThreshold())));
        }

        private double hint(CloudModelFitter.Observation observation) {
            return CloudModelFitter.estimateVelocity(observation, context.grid().sampleOffsets(), context.lambda0(), VELOCITY_HINT_HALF_WIDTH);
        }

        private Solution solution(CloudModelFitter.Observation observation, CloudModelFitter.Fit fit) {
            var parameters = fit.decode();
            if (fit.components() == 0) {
                return new Solution(observation, fit, parameters, null, false);
            }
            var brighter = fitter.cloudContribution(fit, observation) > 0;
            var quantities = brighter ? null : CloudPhysics.compute(parameters, context.lambda0(), context.nonThermalSpeed());
            return new Solution(observation, fit, parameters, quantities, brighter);
        }

        private Optional<Solution> solution(List<float[][]> data, int x, int y, double mu, CloudModelFitter.Fit fit) {
            return observe(data, x, y, mu).map(observation -> solution(observation, fit));
        }

        /**
         * Copies the observation at a position into a batch for the GPU.
         *
         * @return false if the profile at this position cannot be fitted, in which case
         * a placeholder which converges immediately is copied instead
         */
        private boolean export(List<float[][]> data, int x, int y, double mu, GpuCloudFitter.Batch batch, int index) {
            var samples = context.grid().sampleCount();
            var fineCount = context.grid().fineCount();
            var observation = observe(data, x, y, mu);
            if (observation.isEmpty()) {
                Arrays.fill(batch.observed(), index * samples, (index + 1) * samples, 1);
                Arrays.fill(batch.sigma(), index * samples, (index + 1) * samples, 1);
                Arrays.fill(batch.reference(), index * samples, (index + 1) * samples, 1);
                Arrays.fill(batch.profile(), index * fineCount, (index + 1) * fineCount, 1);
                return false;
            }
            var value = observation.get();
            for (var i = 0; i < samples; i++) {
                batch.observed()[index * samples + i] = (float) value.observed()[i];
                batch.sigma()[index * samples + i] = (float) value.sigma()[i];
                batch.reference()[index * samples + i] = (float) value.reference()[i];
            }
            var background = value.background();
            for (var j = 0; j < fineCount; j++) {
                batch.profile()[index * fineCount + j] = (float) background.profile()[j];
                batch.shape()[index * fineCount + j] = (float) background.widthShape()[j];
            }
            batch.hints()[index] = (float) hint(value);
            return true;
        }

        private Optional<CloudInversionResult.PixelFit> details(List<float[][]> data, int x, int y, double mu) {
            return solve(data, x, y, mu).map(solution -> new CloudInversionResult.PixelFit(
                    context.grid().sampleOffsets(),
                    observed.clone(),
                    fitter.evaluate(solution.fit(), solution.observation()),
                    fitter.evaluateBackground(solution.parameters(), solution.observation()),
                    reference.clone(),
                    solution.parameters(),
                    solution.quantities(),
                    solution.fit().reducedChi2(),
                    solution.fit().detection(),
                    solution.brighter(),
                    false));
        }
    }

    /**
     * The solution at one position.
     *
     * @param quantities the physical quantities, or null if no significant absorption was found
     * @param brighter true if the cloud makes the line brighter than its background, in which case the
     * cloud model, which describes absorbing material, does not apply
     */
    private record Solution(CloudModelFitter.Observation observation, CloudModelFitter.Fit fit, CloudParameters parameters, CloudPhysics.Quantities quantities, boolean brighter) {
    }

    private static double wing(double[] profile) {
        var sum = 0d;
        var n = profile.length;
        for (var i = 0; i < WING_SAMPLES; i++) {
            sum += profile[i] + profile[n - 1 - i];
        }
        return sum / (2 * WING_SAMPLES);
    }

    private static void store(EnumMap<CloudMap, float[][]> maps, int x, int y, Solution solution) {
        var p = solution.parameters();
        maps.get(CloudMap.BACKGROUND_SCALE)[y][x] = (float) p.backgroundScale();
        maps.get(CloudMap.BACKGROUND_WIDTH)[y][x] = (float) p.backgroundWidth();
        maps.get(CloudMap.COMPONENTS)[y][x] = p.components();
        maps.get(CloudMap.REDUCED_CHI2)[y][x] = (float) solution.fit().reducedChi2();
        maps.get(CloudMap.DETECTION)[y][x] = (float) solution.fit().detection();
        var q = solution.quantities();
        if (q == null) {
            maps.get(CloudMap.STATUS)[y][x] = solution.brighter() ? CloudMap.STATUS_BRIGHTER : CloudMap.STATUS_NO_CLOUD;
            return;
        }
        maps.get(CloudMap.MASS_FLUX_VELOCITY)[y][x] = (float) q.massFluxVelocity();
        maps.get(CloudMap.FASTEST_VELOCITY)[y][x] = (float) fastestVelocity(p);
        maps.get(CloudMap.KINETIC_ENERGY_VELOCITY)[y][x] = (float) q.kineticEnergyVelocity();
        maps.get(CloudMap.KINETIC_ENERGY_FLUX_VELOCITY)[y][x] = (float) q.kineticEnergyFluxVelocity();
        maps.get(CloudMap.TEMPERATURE)[y][x] = (float) q.temperature();
        maps.get(CloudMap.COLUMN_MASS)[y][x] = (float) q.columnMass();
        maps.get(CloudMap.MASS_FLUX)[y][x] = (float) q.massFlux();
        maps.get(CloudMap.KINETIC_ENERGY)[y][x] = (float) q.kineticEnergy();
        maps.get(CloudMap.KINETIC_ENERGY_FLUX)[y][x] = (float) q.kineticEnergyFlux();
        maps.get(CloudMap.INTEGRATED_OPACITY)[y][x] = (float) q.integratedOpacity();
        maps.get(CloudMap.SOURCE_FUNCTION)[y][x] = (float) p.sourceFunction();
        maps.get(CloudMap.DOPPLER_WIDTH)[y][x] = (float) p.dopplerWidth();
        maps.get(CloudMap.OPACITY_BLUE)[y][x] = (float) p.opacities()[CloudParameters.BLUE];
        maps.get(CloudMap.OPACITY_CENTER)[y][x] = (float) p.opacities()[CloudParameters.CENTER];
        maps.get(CloudMap.OPACITY_RED)[y][x] = (float) p.opacities()[CloudParameters.RED];
        maps.get(CloudMap.VELOCITY_BLUE)[y][x] = (float) p.velocities()[CloudParameters.BLUE];
        maps.get(CloudMap.VELOCITY_CENTER)[y][x] = (float) p.velocities()[CloudParameters.CENTER];
        maps.get(CloudMap.VELOCITY_RED)[y][x] = (float) p.velocities()[CloudParameters.RED];
        maps.get(CloudMap.STATUS)[y][x] = CloudMap.STATUS_FITTED;
    }

    /**
     * Returns the velocity of the most shifted component which absorbs a noticeable amount of light,
     * or of the most opaque component if none does.
     */
    static double fastestVelocity(CloudParameters parameters) {
        var opacities = parameters.opacities();
        var velocities = parameters.velocities();
        var fastest = Double.NaN;
        var strongest = 0;
        for (var k = 0; k < opacities.length; k++) {
            if (opacities[k] > opacities[strongest]) {
                strongest = k;
            }
            if (opacities[k] >= FAST_FLOW_MIN_OPACITY && (Double.isNaN(fastest) || Math.abs(velocities[k]) > Math.abs(fastest))) {
                fastest = velocities[k];
            }
        }
        return Double.isNaN(fastest) ? velocities[strongest] : fastest;
    }

    private static CloudInversionResult.Statistics finish(EnumMap<CloudMap, float[][]> maps, int width, int height, double noise) {
        var status = maps.get(CloudMap.STATUS);
        var chi2 = maps.get(CloudMap.REDUCED_CHI2);
        var values = DoubleStream.builder();
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (CloudMap.isAnalyzed(status[y][x])) {
                    values.add(chi2[y][x]);
                }
            }
        }
        var median = median(values.build().toArray());
        var threshold = POOR_FIT_FACTOR * median;
        var disk = 0;
        var fitted = 0;
        var detected = 0;
        var noCloud = 0;
        var brighter = 0;
        var triple = 0;
        var invalid = 0;
        var poor = 0;
        var measured = 0;
        var sumTemperature = 0d;
        var sumMass = 0d;
        var sumEnergy = 0d;
        var sumMassFluxVelocity = 0d;
        var sumEnergyVelocity = 0d;
        var sumEnergyFluxVelocity = 0d;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var s = status[y][x];
                if (s == CloudMap.STATUS_NOT_FITTED) {
                    continue;
                }
                disk++;
                if (s == CloudMap.STATUS_INVALID_PROFILE) {
                    invalid++;
                    continue;
                }
                if (median > 0 && chi2[y][x] > threshold) {
                    poor++;
                    status[y][x] = CloudMap.STATUS_POOR_FIT;
                    for (var map : CloudMap.values()) {
                        if (map != CloudMap.STATUS && map != CloudMap.REDUCED_CHI2) {
                            maps.get(map)[y][x] = 0;
                        }
                    }
                    continue;
                }
                if (s == CloudMap.STATUS_NO_CLOUD) {
                    noCloud++;
                    continue;
                }
                if (s == CloudMap.STATUS_BRIGHTER) {
                    brighter++;
                    continue;
                }
                fitted++;
                if (maps.get(CloudMap.COMPONENTS)[y][x] == 3) {
                    triple++;
                }
                if (!CloudInversionResult.isDetected(maps.get(CloudMap.DETECTION)[y][x])) {
                    continue;
                }
                detected++;
                if (CloudInversionResult.isTemperatureMeasured(maps.get(CloudMap.DOPPLER_WIDTH)[y][x], maps.get(CloudMap.TEMPERATURE)[y][x])) {
                    sumTemperature += maps.get(CloudMap.TEMPERATURE)[y][x];
                    sumMass += maps.get(CloudMap.COLUMN_MASS)[y][x];
                    sumEnergy += maps.get(CloudMap.KINETIC_ENERGY)[y][x];
                    measured++;
                }
                sumMassFluxVelocity += Math.abs(maps.get(CloudMap.MASS_FLUX_VELOCITY)[y][x]);
                sumEnergyVelocity += maps.get(CloudMap.KINETIC_ENERGY_VELOCITY)[y][x];
                sumEnergyFluxVelocity += Math.abs(maps.get(CloudMap.KINETIC_ENERGY_FLUX_VELOCITY)[y][x]);
            }
        }
        var velocityCount = Math.max(1, detected);
        var measuredCount = Math.max(1, measured);
        var surfaceCount = Math.max(1, measured + noCloud);
        return new CloudInversionResult.Statistics(disk, fitted, detected, noCloud, brighter, triple, invalid, poor, median, noise,
                sumTemperature / measuredCount, sumMass / measuredCount, sumMassFluxVelocity / velocityCount, sumEnergyVelocity / velocityCount,
                sumEnergyFluxVelocity / velocityCount, sumEnergy / surfaceCount);
    }

    private static double median(double[] values) {
        return values.length == 0 ? 0 : MathUtils.median(values);
    }

    private static int[] fitIndices(double[] offsets, double halfWidth) {
        var indices = new ArrayList<Integer>();
        for (var i = 0; i < offsets.length; i++) {
            if (Math.abs(offsets[i]) <= halfWidth) {
                indices.add(i);
            }
        }
        indices.sort(Comparator.comparingDouble(i -> offsets[i]));
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int ringOf(double mu, double minMu) {
        return Math.clamp((int) ((mu - minMu) / (1 - minMu) * RING_COUNT), 0, RING_COUNT - 1);
    }

    private static double[][] computeRawRings(List<float[][]> data, int width, int height, Disk disk, double minMu, int sampleCount) {
        var wings = new DoubleStream.Builder[RING_COUNT];
        for (var r = 0; r < RING_COUNT; r++) {
            wings[r] = DoubleStream.builder();
        }
        var first = data.getFirst();
        var last = data.getLast();
        for (var y = 0; y < height; y += REFERENCE_STRIDE) {
            for (var x = 0; x < width; x += REFERENCE_STRIDE) {
                var mu = disk.mu(x, y);
                if (mu >= minMu) {
                    wings[ringOf(mu, minMu)].add((first[y][x] + last[y][x]) / 2);
                }
            }
        }
        var medians = new double[RING_COUNT];
        for (var r = 0; r < RING_COUNT; r++) {
            medians[r] = median(wings[r].build().toArray());
        }
        var sums = new double[RING_COUNT][sampleCount];
        var counts = new int[RING_COUNT];
        for (var y = 0; y < height; y += REFERENCE_STRIDE) {
            for (var x = 0; x < width; x += REFERENCE_STRIDE) {
                var mu = disk.mu(x, y);
                if (mu < minMu) {
                    continue;
                }
                var ring = ringOf(mu, minMu);
                if ((first[y][x] + last[y][x]) / 2 < REFERENCE_WING_RATIO * medians[ring]) {
                    continue;
                }
                var sum = sums[ring];
                for (var i = 0; i < sampleCount; i++) {
                    sum[i] += data.get(i)[y][x];
                }
                counts[ring]++;
            }
        }
        for (var r = 0; r < RING_COUNT; r++) {
            for (var i = 0; i < sampleCount && counts[r] > 0; i++) {
                sums[r][i] /= counts[r];
            }
        }
        for (var r = 0; r < RING_COUNT; r++) {
            if (counts[r] == 0) {
                sums[r] = sums[nearestFilledRing(counts, r)];
            }
        }
        return sums;
    }

    private static int nearestFilledRing(int[] counts, int ring) {
        for (var distance = 1; distance < RING_COUNT; distance++) {
            for (var candidate : new int[]{ring + distance, ring - distance}) {
                if (candidate >= 0 && candidate < RING_COUNT && counts[candidate] > 0) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException(message("cloud.model.no.reference"));
    }

    private static double estimateNoise(List<float[][]> data, int width, int height, Disk disk, int[] fitIndices, double continuum) {
        var index = fitIndices[0];
        var image = data.get(index);
        var builder = DoubleStream.builder();
        var levels = 0d;
        for (var y = 1; y < height - 1; y += NOISE_STRIDE) {
            for (var x = 1; x < width - 1; x += NOISE_STRIDE) {
                if (disk.mu(x, y) < NOISE_MIN_MU) {
                    continue;
                }
                var value = image[y][x];
                var neighbours = (image[y][x - 1] + image[y][x + 1] + image[y - 1][x] + image[y + 1][x]) / 4;
                builder.add(Math.abs(value - neighbours));
                levels += value;
            }
        }
        var differences = builder.build().toArray();
        if (differences.length == 0) {
            return 0.01;
        }
        var sigma = 1.4826 * median(differences) / Math.sqrt(1.25) / continuum;
        var level = levels / differences.length / continuum;
        return Math.max(1e-4, sigma / Math.sqrt(level));
    }

    private static Rings buildRings(double[][] rawRings, int[] fitIndices, double continuum, double strayLight, double noise, SpectralGrid grid, double[] template, double minMu) {
        var references = new double[RING_COUNT][fitIndices.length];
        var sigmas = new double[RING_COUNT][fitIndices.length];
        var profiles = new double[RING_COUNT][];
        var shapes = new double[RING_COUNT][];
        var derivatives = new double[RING_COUNT][];
        for (var r = 0; r < RING_COUNT; r++) {
            for (var i = 0; i < fitIndices.length; i++) {
                var value = rawRings[r][fitIndices[i]] / continuum;
                references[r][i] = value - strayLight;
                sigmas[r][i] = Math.max(1e-4, noise * Math.sqrt(Math.max(value, 0)));
            }
            var background = CloudModel.Background.of(grid.correctTemplate(references[r], template), grid.fineOffsets());
            profiles[r] = background.profile();
            shapes[r] = background.widthShape();
            derivatives[r] = background.derivative();
        }
        return new Rings(minMu, references, sigmas, profiles, shapes, derivatives);
    }

    private static CloudInversionResult.CalibrationProfile calibrationProfile(double[] center, double[] offsets, InstrumentProfileEstimator.Calibration calibration, double lambda0) {
        var observed = new double[center.length];
        for (var i = 0; i < center.length; i++) {
            observed[i] = center[i] / calibration.continuum();
        }
        var atlas = InstrumentProfileEstimator.convolvedAtlas(offsets, calibration.instrumentSigma(), calibration.telluricExponent(), lambda0);
        for (var i = 0; i < atlas.length; i++) {
            atlas[i] += calibration.strayLight();
        }
        return new CloudInversionResult.CalibrationProfile(offsets, observed, atlas);
    }
}
