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
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.plot;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);
    private static final int MINIMUM_SAMPLES = 32;
    public static final int EDGE_FILTER = 8;
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
        var tmp = image.copy();
        workImage = ImageWrapper32.fromImage(tmp);
        tmp = imageMath.convolve(tmp, BLUR_8);
        workImage = ImageWrapper32.fromImage(tmp);
        // Normalize to the 0-65535 range
        RangeExpansionStrategy.DEFAULT.stretch(workImage);
        filterIrrelevantPixels(workImage, statsOf(workImage.data()));
        var magnitude = imageMath.convolve(tmp, Kernel33.EDGE_DETECTION);
        magnitude = imageMath.convolve(magnitude, Kernel33.GAUSSIAN_BLUR);
        var magnitudes = magnitude.data();
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
            produceEdgeDetectionImage(result, ImageWrapper32.fromImage(magnitude));
        }
        return result;
    }

    private void filterIrrelevantPixels(ImageWrapper32 image, Stats stats) {
        var data = image.data();
        double dx = 0;
        for (float d : data) {
            if (d > stats.avg) {
                dx++;
            }
        }
        dx = dx / data.length;
        new CutoffStretchingStrategy((float) (stats.avg - dx * dx * stats.stddev), stats.avg, 0, stats.avg).stretch(image);
    }

    /**
     * Performs filtering of samples by computing their distance to the
     * detected ellipse.
     *
     * @param samples the samples to be filtered
     */
    private static void filterOutliersByDistanceToEllipse(Collection<Point2D> samples) {
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
        var threshold = 3 * avgDistanceToEllipse;
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

    private List<Point2D> findSamplesUsingDynamicSensitivity(float[] magnitudes) {
        Set<Point2D> samples = new LinkedHashSet<>();
        double sensitivity = 0.00d;
        var maxMagnitude = maxOf(magnitudes);
        var minX = 0;
        var minY = 0;
        var maxX = width;
        var maxY = height;
        boolean first = true;
        while (samples.size() < MINIMUM_SAMPLES && sensitivity < 1) {
            sensitivity += 0.05d;
            LOGGER.debug("Sensitivity {}", sensitivity);
            var minLimit = sensitivity * maxMagnitude;
            scan(samples, minLimit, width, height, magnitudes, true, minX, maxX, minY, maxY);
            scan(samples, minLimit, width, height, magnitudes, false, minX, maxX, minY, maxY);
            if (samples.size() > 2) {
                // remove the top-most, bottom-most, left-most and right-most samples, because they can be outliers in truncated disks
                var sortedByX = samples.stream().sorted(Comparator.comparing(Point2D::x)).toList();
                var finalMinX = (int) sortedByX.get(0).x();
                var finalMaxX = (int) sortedByX.get(samples.size() - 1).x();
                samples.removeIf(p -> Math.abs(p.x() - finalMinX) < EDGE_FILTER || Math.abs(p.x() - finalMaxX) < EDGE_FILTER);
                if (samples.size() > 2) {
                    var sortedByY = samples.stream().sorted(Comparator.comparing(Point2D::y)).toList();
                    var finalMinY = (int) sortedByY.get(0).y();
                    var finalMaxY = (int) sortedByY.get(samples.size() - 1).y();
                    samples.removeIf(p -> Math.abs(p.y() - finalMinY) < EDGE_FILTER || Math.abs(p.y() - finalMaxY) < EDGE_FILTER);
                    if (first) {
                        minY = finalMinY + EDGE_FILTER;
                        maxY = finalMaxY - EDGE_FILTER;
                    }
                }
                if (first) {
                    first = false;
                    minX = finalMinX + EDGE_FILTER;
                    maxX = finalMaxX - EDGE_FILTER;
                    sensitivity = 0; // start over
                }
            }
            filterOutliersByDistanceToEllipse(samples);
        }
        return new ArrayList<>(samples.stream().toList());
    }

    private void scan(Set<Point2D> samples, double minLimit, int width, int height, float[] magnitudes, boolean scanInYDirection, int minX, int maxX, int minY, int maxY) {
        for (int i = scanInYDirection ? minY : minX; i < (scanInYDirection ? maxY : maxX); i++) {
            int min = -1;
            int max = -1;
            for (int j = scanInYDirection ? minX : minY; j < (scanInYDirection ? maxX : maxY); j++) {
                int x = scanInYDirection ? j : i;
                int y = scanInYDirection ? i : j;
                var mag = magnitudes[x + y * width];
                if (min == -1 && mag > minLimit) {
                    min = scanInYDirection ? x : y;
                }
                mag = magnitudes[scanInYDirection ? (width - x - 1) + y * width : x + (height - y - 1) * width];
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
            if (min >= 0 || max >= 0) {
                i += EDGE_FILTER;
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
            debugImagesEmitter.newColorImage(GeneratedImageKind.DEBUG, message("edge.detection"), "edge-detection", image, debugImage -> {
                float[][] rgb = new float[3][];
                float[] overlay = new float[debugImage.length];
                System.arraycopy(debugImage, 0, overlay, 0, overlay.length);
                rgb[0] = overlay;
                rgb[1] = debugImage;
                rgb[2] = debugImage;
                var samples = result.samples();
                for (Point2D sample : samples) {
                    var x = sample.x();
                    var y = sample.y();
                    plot((int) x, (int) y, image.width(), image.height(), overlay);
                }
                return rgb;
            });
        }
    }

    private static float maxOf(float[] magnitudes) {
        float max = Float.MIN_VALUE;
        for (float magnitude : magnitudes) {
            if (magnitude > max) {
                max = magnitude;
            }
        }
        return max;
    }

    private static Stats statsOf(float[] array) {
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0.0f;
        var distinctValues = new HashSet<Float>();

        int n = array.length;
        for (float v : array) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            distinctValues.add(v);
        }
        float average = sum / n;
        float stddev = 0;
        for (float v : array) {
            stddev += (v - average) * (v - average);
        }
        stddev = (float) Math.sqrt(stddev / (n - 1));
        return new Stats(average, stddev, min, max, distinctValues.size());
    }

    private record Stats(float avg, float stddev, float min, float max, int distinctValues) {

    }

    public record Result(
        Ellipse ellipse,
        List<Point2D> samples
    ) {

    }
}
