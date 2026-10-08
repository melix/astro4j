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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.Point2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Corrects the distortion caused by a periodic error of the mount tracking. In a scan,
 * the frame number is time: when the tracking speed varies as {@code v0 (1 + e(t))}, the
 * Sun drifts by {@code rate * integral(e)} frames with respect to the position the
 * reconstruction assumes. The geometry correction of each scan removes the affine part
 * of that drift over the disk, the rest bends the disk along the scanning direction,
 * differently from one scan to the next.
 * <p>
 * The error is modelled as a Fourier series of a fundamental period shared by all the
 * scans of a series. The limb of every scan is measured, and the coefficients of the
 * series are fitted by linear least squares to the limb deviations the drift predicts,
 * given the date of the scan and the frame of every limb point. Each image is then
 * resampled along the scanning direction to cancel the predicted drift.
 */
public class PeriodicErrorCorrection extends AbstractLimbFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicErrorCorrection.class);
    private static final double MIN_PERIOD = 200;
    private static final double MAX_PERIOD = 1000;
    private static final double SEARCH_OVERSAMPLING = 10;
    private static final double SAMPLE_REJECTION_SIGMA = 4;
    private static final int MIN_SAMPLES = ANGLES / 4;
    private static final int MIN_SCANS = 8;
    private static final int LINE_SAMPLES = 90;

    public PeriodicErrorCorrection(Map<Class<?>, Object> context, Broadcaster broadcaster, EllipseFit ellipseFit) {
        super(context, broadcaster, ellipseFit);
    }

    public Object correctPeriodicError(Map<String, Object> arguments) {
        BuiltinFunction.CORRECT_PERIODIC_ERROR.validateArgs(arguments);
        if (!(arguments.get("img") instanceof List<?> images)) {
            throw new IllegalArgumentException("correct_periodic_error requires a list of images");
        }
        var references = arguments.get("ref") instanceof List<?> refList ? refList : images;
        if (references.size() != images.size()) {
            throw new IllegalArgumentException("correct_periodic_error: the reference list must have the same size as the image list");
        }
        var harmonics = Math.max(1, intArg(arguments, "harmonics", 5));
        var period = doubleArg(arguments, "period", 0);
        if (period < 0) {
            throw new IllegalArgumentException("correct_periodic_error: the period must be positive");
        }
        var timings = new ArrayList<Timing>(images.size());
        for (int i = 0; i < images.size(); i++) {
            var timing = Timing.of((ImageWrapper) images.get(i));
            if (timing.isEmpty()) {
                LOGGER.warn("correct_periodic_error: image {} has no scan date, duration or geometry information, images left unchanged", i);
                return images;
            }
            timings.add(timing.get());
        }
        var origin = timings.stream().mapToDouble(Timing::start).min().orElse(0);
        var operation = newOperation("correct_periodic_error");
        var scans = new ArrayList<Scan>(images.size());
        var circles = new ArrayList<Optional<Circle>>(images.size());
        for (int i = 0; i < references.size(); i++) {
            var reference = (ImageWrapper) references.get(i);
            var scan = Timing.of(reference).flatMap(timing -> measure(toMono(reference.unwrapToMemory()), timing, origin));
            scan.ifPresent(scans::add);
            circles.add(scan.map(Scan::circle));
            broadcaster.broadcast(operation.update((i + 1) / (2.0 * images.size())));
        }
        if (scans.size() < MIN_SCANS) {
            LOGGER.warn("correct_periodic_error: the limb could be measured on {} scans only, at least {} are required, images left unchanged", scans.size(), MIN_SCANS);
            broadcaster.broadcast(operation.complete());
            return images;
        }
        var model = period > 0 ? fit(scans, 2 * Math.PI / period, harmonics) : search(scans, harmonics);
        var before = scans.stream().mapToDouble(Scan::sumOfSquares).sum();
        var samples = scans.stream().mapToInt(s -> s.data().length).sum();
        var fallback = medianCircle(scans);
        var result = new ArrayList<Object>(images.size());
        var maxDrift = 0d;
        for (int i = 0; i < images.size(); i++) {
            var image = ((ImageWrapper) images.get(i)).unwrapToMemory();
            var timing = timings.get(i);
            var lineFrames = lineFrames(circles.get(i).orElse(fallback), timing.geometry());
            var drift = Drift.of(model, timing.start() - origin, timing.frameRate(), lineFrames);
            maxDrift = Math.max(maxDrift, drift.maxAmplitude(lineFrames));
            result.add(FileBackedImage.wrap(correct(image, timing.geometry(), drift)));
            broadcaster.broadcast(operation.update((images.size() + i + 1) / (2.0 * images.size())));
        }
        LOGGER.info("correct_periodic_error: period {} s, {} harmonics, {} scans measured, limb deviation rms {} px before, {} px after, drift up to {} px",
                fmt(2 * Math.PI / model.omega()), harmonics, scans.size(),
                fmt(Math.sqrt(before / samples)), fmt(Math.sqrt(Math.max(0, before - model.explained()) / samples)), fmt(maxDrift));
        broadcaster.broadcast(operation.complete());
        return result;
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    /**
     * How the frames of a scan map onto the image. The transformations applied by the
     * processing are affine, so the frame of a pixel is an affine function of its
     * coordinates, and a drift of one frame moves the content of the image by a
     * constant vector.
     *
     * @param frame0 the frame of the pixel at the origin of the image
     * @param frameDx the change of frame for one pixel along x
     * @param frameDy the change of frame for one pixel along y
     * @param shiftX the displacement along x of the content of the image for a drift of one frame
     * @param shiftY the displacement along y of the content of the image for a drift of one frame
     */
    record Geometry(double frame0, double frameDx, double frameDy, double shiftX, double shiftY) {
        static Optional<Geometry> of(ReferenceCoords coords) {
            var origin = coords.determineOriginalCoordinates(new Point2D(0, 0), ReferenceCoords.NO_LIMIT);
            var alongX = coords.determineOriginalCoordinates(new Point2D(1, 0), ReferenceCoords.NO_LIMIT);
            var alongY = coords.determineOriginalCoordinates(new Point2D(0, 1), ReferenceCoords.NO_LIMIT);
            var k00 = alongX.x() - origin.x();
            var k01 = alongY.x() - origin.x();
            var k10 = alongX.y() - origin.y();
            var k11 = alongY.y() - origin.y();
            var determinant = k00 * k11 - k01 * k10;
            if (Math.abs(determinant) < 1e-12) {
                return Optional.empty();
            }
            return Optional.of(new Geometry(origin.y(), k10, k11, -k01 / determinant, k00 / determinant));
        }

        double frameAt(double x, double y) {
            return frame0 + frameDx * x + frameDy * y;
        }
    }

    /**
     * The timing of a scan.
     *
     * @param start the date of the first frame, in seconds since the epoch
     * @param frameRate the number of frames per second
     * @param geometry how the frames map onto the image
     */
    record Timing(double start, double frameRate, Geometry geometry) {
        static Optional<Timing> of(ImageWrapper image) {
            var sourceInfo = image.findMetadata(SourceInfo.class);
            var coords = image.findMetadata(ReferenceCoords.class);
            if (sourceInfo.isEmpty() || coords.isEmpty()) {
                return Optional.empty();
            }
            var info = sourceInfo.get();
            if (info.dateTime() == null || info.durationSeconds() <= 0 || info.height() < 2) {
                return Optional.empty();
            }
            var instant = info.dateTime().toInstant();
            var start = instant.getEpochSecond() + instant.getNano() / 1e9;
            return Geometry.of(coords.get()).map(geometry -> new Timing(start, (info.height() - 1) / info.durationSeconds(), geometry));
        }
    }

    /**
     * The limb deviations of a scan, ready for the fit. The deviations and the model are
     * both stripped of the components the circle fit and the geometry correction absorb.
     *
     * @param circle the circle fitted to the limb
     * @param start the date of the first frame, in seconds since the origin of the series
     * @param frameRate the number of frames per second
     * @param frames the frame of each limb sample
     * @param gains the radial displacement of each limb sample for a drift of one frame
     * @param data the limb deviations, stripped of the absorbed components
     * @param nuisance an orthonormal basis of the components absorbed by the circle fit and the geometry correction
     * @param lineFrames the frames of regularly spaced points of the limb, over which the affine part of the drift is removed
     */
    record Scan(Circle circle, double start, double frameRate, double[] frames, double[] gains, double[] data, double[][] nuisance, double[] lineFrames) {
        double sumOfSquares() {
            return Arrays.stream(data).map(d -> d * d).sum();
        }
    }

    /**
     * A fitted periodic error: the content of a scan is displaced along the scanning
     * direction by {@code rate * integral(e)} frames, with
     * {@code e(t) = sum(a_j cos(j omega t) + b_j sin(j omega t))}.
     *
     * @param omega the angular frequency of the fundamental
     * @param coefficients a_1, b_1, a_2, b_2...
     * @param explained the reduction of the sum of squared limb deviations
     */
    record Model(double omega, double[] coefficients, double explained) {
        int harmonics() {
            return coefficients.length / 2;
        }

        /**
         * The drift, in frames, at a given time of a scan, up to a constant.
         */
        double drift(double time, double frameRate) {
            var value = 0d;
            for (int j = 1; j <= harmonics(); j++) {
                var w = j * omega;
                value += frameRate * (coefficients[2 * j - 2] * Math.sin(w * time) - coefficients[2 * j - 1] * Math.cos(w * time)) / w;
            }
            return value;
        }
    }

    /**
     * The drift of one scan, without its affine part over the disk, which the geometry
     * correction of the scan has already removed.
     *
     * @param model the periodic error
     * @param start the date of the first frame, in seconds since the origin of the series
     * @param frameRate the number of frames per second
     * @param offset the constant part of the drift over the disk
     * @param slope the linear part of the drift over the disk, per frame
     */
    private record Drift(Model model, double start, double frameRate, double offset, double slope) {
        static Drift of(Model model, double start, double frameRate, double[] lineFrames) {
            var values = Arrays.stream(lineFrames).map(f -> model.drift(start + f / frameRate, frameRate)).toArray();
            var line = line(lineFrames, values);
            return new Drift(model, start, frameRate, line[0], line[1]);
        }

        double at(double frame) {
            return model.drift(start + frame / frameRate, frameRate) - offset - slope * frame;
        }

        double maxAmplitude(double[] frames) {
            return Arrays.stream(frames).map(f -> Math.abs(at(f))).max().orElse(0);
        }
    }

    private Optional<Scan> measure(ImageWrapper32 image, Timing timing, double origin) {
        var points = measureLimbPoints(image);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        var circle = points.get().circle();
        var radii = points.get().radii();
        var deviations = new ArrayList<double[]>();
        for (int k = 0; k < ANGLES; k++) {
            if (!Double.isNaN(radii[k])) {
                deviations.add(new double[]{2 * Math.PI * k / ANGLES, radii[k], radii[k] - circle.radius()});
            }
        }
        if (deviations.size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        var values = deviations.stream().mapToDouble(d -> d[2]).toArray();
        var median = median(values);
        var sigma = 1.4826 * median(Arrays.stream(values).map(v -> Math.abs(v - median)).toArray());
        var kept = deviations.stream()
                .filter(d -> sigma <= 0 || Math.abs(d[2] - median) <= SAMPLE_REJECTION_SIGMA * sigma)
                .toList();
        if (kept.size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        var geometry = timing.geometry();
        var count = kept.size();
        var frames = new double[count];
        var gains = new double[count];
        var data = new double[count];
        var absorbed = new double[5][count];
        for (int i = 0; i < count; i++) {
            var angle = kept.get(i)[0];
            var radius = kept.get(i)[1];
            var cos = Math.cos(angle);
            var sin = Math.sin(angle);
            frames[i] = geometry.frameAt(circle.cx() + radius * cos, circle.cy() + radius * sin);
            gains[i] = geometry.shiftX() * cos + geometry.shiftY() * sin;
            data[i] = kept.get(i)[2];
            absorbed[0][i] = 1;
            absorbed[1][i] = cos;
            absorbed[2][i] = sin;
            absorbed[3][i] = Math.cos(2 * angle);
            absorbed[4][i] = Math.sin(2 * angle);
        }
        var nuisance = orthonormalize(absorbed);
        project(data, nuisance);
        return Optional.of(new Scan(circle, timing.start() - origin, timing.frameRate(), frames, gains, data, nuisance, lineFrames(circle, geometry)));
    }

    private static double[] lineFrames(Circle circle, Geometry geometry) {
        var frames = new double[LINE_SAMPLES];
        for (int k = 0; k < LINE_SAMPLES; k++) {
            var angle = 2 * Math.PI * k / LINE_SAMPLES;
            frames[k] = geometry.frameAt(circle.cx() + circle.radius() * Math.cos(angle), circle.cy() + circle.radius() * Math.sin(angle));
        }
        return frames;
    }

    /**
     * Least-squares line through (frames, values), returned as {offset, slope}.
     */
    private static double[] line(double[] frames, double[] values) {
        var meanFrame = Arrays.stream(frames).average().orElse(0);
        var meanValue = Arrays.stream(values).average().orElse(0);
        double covariance = 0, variance = 0;
        for (int k = 0; k < frames.length; k++) {
            covariance += (frames[k] - meanFrame) * (values[k] - meanValue);
            variance += (frames[k] - meanFrame) * (frames[k] - meanFrame);
        }
        var slope = variance > 0 ? covariance / variance : 0;
        return new double[]{meanValue - slope * meanFrame, slope};
    }

    private static double[][] orthonormalize(double[][] vectors) {
        var basis = new ArrayList<double[]>();
        for (var vector : vectors) {
            var v = vector.clone();
            for (var b : basis) {
                var dot = dot(v, b);
                for (int i = 0; i < v.length; i++) {
                    v[i] -= dot * b[i];
                }
            }
            var norm = Math.sqrt(dot(v, v));
            if (norm > 1e-9) {
                for (int i = 0; i < v.length; i++) {
                    v[i] /= norm;
                }
                basis.add(v);
            }
        }
        return basis.toArray(new double[0][]);
    }

    private static void project(double[] vector, double[][] basis) {
        for (var b : basis) {
            var dot = dot(vector, b);
            for (int i = 0; i < vector.length; i++) {
                vector[i] -= dot * b[i];
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        var sum = 0d;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static Model search(List<Scan> scans, int harmonics) {
        var first = scans.stream().mapToDouble(Scan::start).min().orElse(0);
        var last = scans.stream().mapToDouble(Scan::start).max().orElse(0);
        var span = Math.max(last - first, MAX_PERIOD);
        var step = 1 / (span * SEARCH_OVERSAMPLING);
        var count = (int) Math.ceil((1 / MIN_PERIOD - 1 / MAX_PERIOD) / step) + 1;
        return IntStream.range(0, count)
                .parallel()
                .mapToObj(i -> fit(scans, 2 * Math.PI * (1 / MAX_PERIOD + i * step), harmonics))
                .max(Comparator.comparingDouble(Model::explained))
                .orElseThrow();
    }

    /**
     * Fits the coefficients of the periodic error for a given fundamental, by linear
     * least squares over the limb samples of all the scans.
     */
    private static Model fit(List<Scan> scans, double omega, int harmonics) {
        var terms = 2 * harmonics;
        var normal = new double[terms][terms];
        var rhs = new double[terms];
        for (var scan : scans) {
            var columns = new double[terms][];
            for (int t = 0; t < terms; t++) {
                var unit = new double[terms];
                unit[t] = 1;
                var drift = Drift.of(new Model(omega, unit, 0), scan.start(), scan.frameRate(), scan.lineFrames());
                var column = new double[scan.frames().length];
                for (int i = 0; i < column.length; i++) {
                    column[i] = scan.gains()[i] * drift.at(scan.frames()[i]);
                }
                project(column, scan.nuisance());
                columns[t] = column;
            }
            for (int a = 0; a < terms; a++) {
                rhs[a] += dot(columns[a], scan.data());
                for (int b = a; b < terms; b++) {
                    normal[a][b] += dot(columns[a], columns[b]);
                }
            }
        }
        for (int a = 0; a < terms; a++) {
            for (int b = 0; b < a; b++) {
                normal[a][b] = normal[b][a];
            }
        }
        var coefficients = solve(normal, rhs);
        return new Model(omega, coefficients, dot(coefficients, rhs));
    }

    private static Circle medianCircle(List<Scan> scans) {
        return new Circle(
                median(scans.stream().mapToDouble(s -> s.circle().cx()).toArray()),
                median(scans.stream().mapToDouble(s -> s.circle().cy()).toArray()),
                median(scans.stream().mapToDouble(s -> s.circle().radius()).toArray())
        );
    }

    private ImageWrapper correct(ImageWrapper image, Geometry geometry, Drift drift) {
        var width = image.width();
        var height = image.height();
        var gridWidth = width / GRID_STEP + 2;
        var gridHeight = height / GRID_STEP + 2;
        var gridDx = new float[gridHeight][gridWidth];
        var gridDy = new float[gridHeight][gridWidth];
        IntStream.range(0, gridHeight).parallel().forEach(j -> {
            var y = j * GRID_STEP;
            for (int i = 0; i < gridWidth; i++) {
                var x = i * GRID_STEP;
                var shift = drift.at(geometry.frameAt(x, y));
                gridDx[j][i] = (float) (shift * geometry.shiftX());
                gridDy[j][i] = (float) (shift * geometry.shiftY());
            }
        });
        if (image instanceof ImageWrapper32 mono) {
            return new ImageWrapper32(width, height, resample(mono.data(), width, height, gridDx, gridDy), image.metadata());
        }
        if (image instanceof RGBImage rgb) {
            return new RGBImage(width, height,
                    resample(rgb.r(), width, height, gridDx, gridDy),
                    resample(rgb.g(), width, height, gridDx, gridDy),
                    resample(rgb.b(), width, height, gridDx, gridDy),
                    image.metadata());
        }
        throw new IllegalArgumentException("correct_periodic_error only supports mono and RGB images");
    }

}
