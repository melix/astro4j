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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.BlurKernel;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.plot;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);
    private static final int MINIMUM_SAMPLES = 32;
    private static final Kernel BLUR_8 = BlurKernel.of(8);
    private final ProcessParams processParams;
    private final ImageEmitter debugImagesEmitter;
    private Image image;

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster,
                              Supplier<ImageWrapper32> image,
                              ProcessParams processParams,
                              ImageEmitter debugImagesEmitter) {
        super(broadcaster, image);
        this.processParams = processParams;
        this.debugImagesEmitter = debugImagesEmitter;
    }

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster,
                              Supplier<ImageWrapper32> image) {
        this(broadcaster, image, null, null);
    }

    @Override
    protected void prepareImage() {
        super.prepareImage();
        this.image = workImage.asImage();
    }

    @Override
    public EllipseFittingTask.Result doCall() throws Exception {
        var analyzingDiskGeometryMsg = message("analyzing.disk.geometry");
        broadcaster.broadcast(ProgressEvent.of(0, analyzingDiskGeometryMsg));
        var imageMath = ImageMath.newInstance();
        var tmp = truncate(image.copy());
        workImage = ImageWrapper32.fromImage(tmp);
        var data = tmp.data();
        tmp = imageMath.convolve(tmp, Kernel33.GAUSSIAN_BLUR);
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x] / Constants.MAX_PIXEL_VALUE;
                data[y][x] = v * v * Constants.MAX_PIXEL_VALUE;
            }
        }
        tmp = imageMath.convolve(tmp, BLUR_8);
        workImage = ImageWrapper32.fromImage(tmp);
        // Perform background neutralization to reduce impact of reflections,
        // particularly visible close to UV. We perform multiple iterations
        // because some gradients are particularly difficult to remove
        for (int i = 0; i < 8; i++) {
            workImage = BackgroundRemoval.neutralizeBackground(workImage);
        }
        LinearStrechingStrategy.DEFAULT.stretch(workImage);
        var magnitudes = workImage.data();
        var samples = findSamplesUsingDynamicSensitivity(magnitudes);
        var fittingEllipseMessage = message("fitting.ellipse");
        broadcaster.broadcast(ProgressEvent.of(0, fittingEllipseMessage));
        if (notEnoughSamples(samples)) {
            var template = message("ellipse.not.enough.samples").replace("{}", "%s");
            var message = String.format(template, samples.size(), MINIMUM_SAMPLES);
            broadcaster.broadcast(new NotificationEvent(new Notification(Notification.AlertType.ERROR, message("not.enough.samples.title"), "", message)));
            return null;
        }
        var ellipse = new EllipseRegression(samples).solve();
        LOGGER.debug("{}", ellipse);
        broadcaster.broadcast(ProgressEvent.of(1, fittingEllipseMessage));
        var result = new Result(ellipse, samples);
        if (processParams != null && processParams.extraParams().generateDebugImages()) {
            produceEdgeDetectionImage(result, workImage);
        }
        return result;
    }

    /**
     * Performs filtering of samples by computing their distance to the
     * detected ellipse.
     *
     * @param samples the samples to be filtered
     */
    private static void filterOutliersByDistanceToEllipse(Collection<Point2D> samples, double factor) {
        Ellipse initialEllipse;
        try {
            initialEllipse = new EllipseRegression(samples.stream().toList()).solve();
        } catch (Exception e) {
            // ignore, this will be caught later
            return;
        }
        var avgDistanceToEllipse = samples.stream()
            .filter(p -> initialEllipse.findY(p.x()).isPresent() || initialEllipse.findX(p.y()).isPresent())
            .mapToDouble(p -> {
                var maybeY = initialEllipse.findY(p.x());
                if (maybeY.isEmpty()) {
                    var doublePair = initialEllipse.findX(p.y()).get();
                    return Math.min(Math.abs(p.x() - doublePair.a()), Math.abs(p.x() - doublePair.b()));
                }
                var doublePair = maybeY.get();
                return Math.min(Math.abs(p.y() - doublePair.a()), Math.abs(p.y() - doublePair.b()));
            })
            .average()
            .orElse(10);
        var stddev = samples.stream()
            .filter(p -> initialEllipse.findY(p.x()).isPresent() || initialEllipse.findX(p.y()).isPresent())
            .mapToDouble(p -> {
                var maybeY = initialEllipse.findY(p.x());
                if (maybeY.isEmpty()) {
                    var doublePair = initialEllipse.findX(p.y()).get();
                    return Math.min(Math.abs(p.x() - doublePair.a()), Math.abs(p.x() - doublePair.b()));
                }
                var doublePair = maybeY.get();
                return Math.min(Math.abs(p.y() - doublePair.a()), Math.abs(p.y() - doublePair.b()));
            })
            .map(d -> (d - avgDistanceToEllipse) * (d - avgDistanceToEllipse))
            .sum();
        stddev = (float) Math.sqrt(stddev / samples.size());
        var threshold = 1.5 * (factor * stddev + avgDistanceToEllipse);
        samples.removeIf(p -> {
            var maybeY = initialEllipse.findY(p.x());
            if (maybeY.isEmpty()) {
                var maybeX = initialEllipse.findX(p.y());
                if (maybeX.isEmpty()) {
                    return true;
                }
                var doublePair = maybeX.get();
                var dmin = Math.min(Math.abs(p.x() - doublePair.a()), Math.abs(p.x() - doublePair.b()));
                return dmin > threshold;
            }
            var doublePair = maybeY.get();
            var dmin = Math.min(Math.abs(p.y() - doublePair.a()), Math.abs(p.y() - doublePair.b()));
            return dmin > threshold;
        });
    }

    private List<Point2D> findSamplesUsingDynamicSensitivity(float[][] magnitudes) {
        Set<Point2D> samples = new LinkedHashSet<>();
        var stats = statsOf(magnitudes);
        var maxMagnitude = stats.max();
        double sensitivity = 0.5 * (stats.min() + stats.stddev()) / Constants.MAX_PIXEL_VALUE;
        var minX = 0;
        var minY = 0;
        var maxX = width;
        var maxY = height;
        LOGGER.debug("Sensitivity {}", sensitivity);
        var minLimit = sensitivity * maxMagnitude;
        scan(samples, minLimit, width, height, magnitudes, true, minX, maxX, minY, maxY);
        scan(samples, minLimit, width, height, magnitudes, false, minX, maxX, minY, maxY);
        filterOutliersByDetectingLines(samples);
        return new ArrayList<>(samples.stream().toList());
    }

    private void filterOutliersByDetectingLines(Set<Point2D> samples) {
        var restore = new ArrayList<>(samples);
        var byX = samples.stream()
            .collect(Collectors.groupingBy(p -> (int) p.x()));
        var byY = samples.stream()
            .collect(Collectors.groupingBy(p -> (int) p.y()));
        var sX = 8 + Math.sqrt(byX.size() / 2d);
        var sY = 8 + Math.sqrt(byY.size() / 2d);
        // remove samples when they are too many of them in a single column or row, because it usually means we have
        // detected a line instead of a disk
        var removedX = new HashSet<Integer>();
        var removedY = new HashSet<Integer>();
        byX.forEach((x, points) -> {
            if (points.size() > sX) {
                for (int i = x - 2; i < x + 2; i++) {
                    removedX.add(i);
                }
            }
        });
        byY.forEach((y, points) -> {
            if (points.size() > sY) {
                for (int i = y - 2; i < y + 2; i++) {
                    removedY.add(i);
                }
            }
        });
        samples.removeIf(p -> removedX.contains((int) p.x()) || removedY.contains((int) p.y()));
        if (samples.size() < MINIMUM_SAMPLES && restore.size() > 2 * MINIMUM_SAMPLES) {
            // special case for very "flat" disks
            var size = restore.size();
            if (size > 512) {
                int step = size / 512;
                IntStream.range(0, size)
                    .filter(i -> (i % step) == 0)
                    .forEach(i -> samples.add(restore.get(i)));
            } else {
                samples.addAll(restore);
            }
        }
    }

    private static void filterOutliersByMinDistanceBetweenSamples(Collection<Point2D> samples) {
        var minDistanceToSamples = new HashMap<Point2D, Double>();
        samples.forEach(p -> {
            var x = p.x();
            var y = p.y();
            var avg = samples.stream().mapToDouble(p2 -> {
                var x2 = p2.x();
                var y2 = p2.y();
                return Math.sqrt((x - x2) * (x - x2) + (y - y2) * (y - y2));
            }).filter(d -> d > 0).min().orElse(0);
            minDistanceToSamples.put(p, avg);
        });
        var avg = minDistanceToSamples.values().stream().mapToDouble(d -> d).average().orElse(0);
        var threshold = 2 * avg;
        samples.removeIf(p -> minDistanceToSamples.get(p) > threshold);
    }

    private void scan(Collection<Point2D> samples, double minLimit, int width, int height, float[][] magnitudes, boolean scanInYDirection, int minX, int maxX, int minY, int maxY) {
        for (int i = scanInYDirection ? minY : minX; i < (scanInYDirection ? maxY : maxX); i++) {
            int min = -1;
            int max = -1;
            for (int j = scanInYDirection ? minX : minY; j < (scanInYDirection ? maxX : maxY); j++) {
                int x = scanInYDirection ? j : i;
                int y = scanInYDirection ? i : j;
                var mag = magnitudes[y][x];
                if (min == -1 && mag > minLimit) {
                    min = scanInYDirection ? x : y;
                }
                mag = scanInYDirection ? magnitudes[y][(width - x - 1)] : magnitudes[(height - y - 1)][x];
                if (max == -1 && mag > minLimit) {
                    max = scanInYDirection ? (width - x - 1) : (height - y - 1);
                }
                if (min != -1 && max != -1) {
                    break;
                }
            }
            if (min >= 0) {
                var candidate = scanInYDirection ? new Point2D(min, i) : new Point2D(i, min);
                samples.add(candidate);
            }
            if (max >= 0) {
                var candidate = scanInYDirection ? new Point2D(max, i) : new Point2D(i, max);
                samples.add(candidate);
            }
        }
    }

    private boolean notEnoughSamples(List<Point2D> filteredSamples) {
        if (filteredSamples.size() < MINIMUM_SAMPLES) {
            return true;
        }
        return false;
    }

    private void produceEdgeDetectionImage(EllipseFittingTask.Result result, ImageWrapper32 image) {
        if (debugImagesEmitter != null) {
            debugImagesEmitter.newColorImage(GeneratedImageKind.DEBUG, null, message("edge.detection"), "edge-detection", message("edge.detection.description"), image, debugImage -> {
                float[][][] rgb = new float[3][][];
                var mono = debugImage.data();
                float[][] overlay = ImageWrapper.copyData(mono);
                rgb[0] = overlay;
                rgb[1] = mono;
                rgb[2] = mono;
                var samples = result.samples();
                for (Point2D sample : samples) {
                    var x = sample.x();
                    var y = sample.y();
                    plot((int) x, (int) y, image.width(), image.height(), overlay);
                }
                var boundingBox = result.ellipse.boundingBox();
                var x1 = boundingBox.a();
                var y1 = boundingBox.c();
                var x2 = boundingBox.b();
                var y2 = boundingBox.d();
                for (int x = (int) x1; x < x2; x++) {
                    plot(x, (int) y1, image.width(), image.height(), overlay);
                    plot(x, (int) y2, image.width(), image.height(), overlay);
                }
                for (int y = (int) y1; y < y2; y++) {
                    plot((int) x1, y, image.width(), image.height(), overlay);
                    plot((int) x2, y, image.width(), image.height(), overlay);
                }
                return rgb;
            });
        }
    }

    private static Image truncate(Image image) {
        var data = image.data();
        var stats = statsOf(data);
        var minNonZero = stats.minNonZero();
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = data[y][x];
                if (v == 0) {
                    data[y][x] = minNonZero;
                }
            }
        }
        return image;
    }

    private static Stats statsOf(float[][] array) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float minNonZero = Float.MAX_VALUE;
        float sum = 0.0f;

        int n = 0;
        for (float[] line : array) {
            for (float v : line) {
                n++;
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
                if (v > 0) {
                    minNonZero = Math.min(minNonZero, v);
                }
            }
        }
        float average = sum / n;
        float stddev = 0;
        for (float[] line : array) {
            for (float v : line) {
                stddev += (v - average) * (v - average);
            }
        }
        stddev = (float) Math.sqrt(stddev / (n - 1));
        return new Stats(average, stddev, min, max, minNonZero);
    }

    private record Stats(float avg, float stddev, float min, float max, float minNonZero) {

    }

    public record Result(
        Ellipse ellipse,
        List<Point2D> samples
    ) {

    }
}
