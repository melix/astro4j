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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.plot;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);
    private static final int MINIMUM_SAMPLES = 32;
    private static final Kernel BLUR_8 = BlurKernel.of(8);
    private static final Kernel BLUR_4 = BlurKernel.of(4);
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
        var workingImage = image.copy();
        workingImage = imageMath.convolve(workingImage, BLUR_8);
        if (LOGGER.isDebugEnabled()) {
            debugImagesEmitter.newMonoImage(GeneratedImageKind.DEBUG, "Prefiltered", "prefilter", ImageWrapper32.fromImage(workingImage));
        }
        var magnitude = imageMath.convolve(workingImage, Kernel33.EDGE_DETECTION);
        magnitude = imageMath.convolve(magnitude, Kernel33.GAUSSIAN_BLUR);
        var magnitudes = magnitude.data();
        var samples = findSamplesUsingDynamicSensitivity(analyzingDiskGeometryMsg, magnitudes);
        var fittingEllipseMessage = message("fitting.ellipse");
        broadcaster.broadcast(ProgressEvent.of(0, fittingEllipseMessage));
        if (notEnoughSamples(samples)) {
            var template = message("ellipse.not.enough.samples").replace("{}", "%s");
            var message = String.format(template, samples.size(), MINIMUM_SAMPLES);
            broadcaster.broadcast(new NotificationEvent(new Notification(Notification.AlertType.ERROR, message("not.enough.samples.title"), "", message)));
            return null;
        }
        var initialEllipse = new EllipseRegression(samples).solve();
        samples.removeIf(p -> {
            var maybeY = initialEllipse.findY(p.x());
            if (maybeY.isEmpty()) {
                return true;
            }
            var doublePair = maybeY.get();
            var dmin = Math.min(Math.abs(p.y()-doublePair.a()), Math.abs(p.y()-doublePair.b()));
            return dmin > 20;
        });
        var ellipse = new EllipseRegression(samples).solve();
        LOGGER.debug("{}", ellipse);
        broadcaster.broadcast(ProgressEvent.of(1, fittingEllipseMessage));
        var result = new Result(ellipse, samples);
        if (processParams != null && processParams.extraParams().generateDebugImages()) {
            produceEdgeDetectionImage(result, ImageWrapper32.fromImage(magnitude));
        }
        return result;
    }

    private List<Point2D> findSamplesUsingDynamicSensitivity(String analyzingDiskGeometryMsg, float[] magnitudes) {
        Set<Point2D> samples = new LinkedHashSet<>();
        double sensitivity = 0.00d;
        var maxMagnitude = maxOf(magnitudes);
        double maxMag = Double.MIN_VALUE;
        while (samples.size() < MINIMUM_SAMPLES && sensitivity < 1) {
            sensitivity += 0.05d;
            LOGGER.debug("Sensitivity {}", sensitivity);
            var minLimit = sensitivity * maxMagnitude;
            double minVal = 0;
            double maxVal = 0;
            for (int y = 0; y < height; y++) {
                broadcaster.broadcast(ProgressEvent.of((y + 1d / height) / height, analyzingDiskGeometryMsg));
                int min = -1;
                int max = -1;
                for (int x = 0; x < width / 2; x++) {
                    var mag = magnitudes[x + y * width];
                    if (min == -1 && mag > minLimit) {
                        min = x;
                        minVal = mag;
                    }
                    mag = magnitudes[(width - x - 1) + y * width];
                    if (max == -1 && mag > minLimit) {
                        max = (width - x - 1);
                        maxVal = mag;
                    }
                    if (min != -1 && max != -1) {
                        break;
                    }
                }
                if (min >= 0) {
                    var candidate = new Point2D(min, y);
                    if (samples.add(candidate)) {
                        maxMag = Math.max(minVal, maxMag);
                    }
                }
                if (max >= 0) {
                    var candidate = new Point2D(max, y);
                    if (samples.add(candidate)) {
                        maxMag = Math.max(maxVal, maxMag);
                    }
                }
                if (min >= 0 || max >= 0) {
                    y += 4;
                }
            }
            filterOutliers(magnitudes, samples, maxMag);
        }
        return new ArrayList<>(samples.stream().toList());
    }

    private void filterOutliers(float[] magnitudes, Set<Point2D> samples, double maxMag) {
        var closestNeighborDistances = samples
                .stream()
                .distinct()
                .collect(Collectors.toMap(p -> p, p -> {
                    double d = Double.MAX_VALUE;
                    for (Point2D sample : samples) {
                        var dist = p.distanceTo(sample);
                        if (!sample.equals(p) && dist < d) {
                            d = dist;
                        }
                    }
                    return d;
                }));
        var avgMinDistance = closestNeighborDistances.values().stream().mapToDouble(Double::doubleValue).average().orElse(Double.MAX_VALUE);
        samples.removeIf(p ->
                (magnitudes[(int) (p.x() + p.y() * width)] < .5f * maxMag)
                || (closestNeighborDistances.get(p) > 1.5 * avgMinDistance)
        );
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
