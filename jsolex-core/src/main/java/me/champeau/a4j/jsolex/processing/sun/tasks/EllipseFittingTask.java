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
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.image.Kernel33;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.DebugImageHelper.plot;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);
    private static final int MINIMUM_SAMPLES = 64;

    private final Image image;
    private final ProcessParams processParams;
    private final ImageEmitter debugImagesEmitter;

    private boolean prefilter = false;

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster,
                              ImageWrapper32 image,
                              ProcessParams processParams,
                              ImageEmitter debugImagesEmitter) {
        super(broadcaster, image);
        this.image = image.asImage();
        this.processParams = processParams;
        this.debugImagesEmitter = debugImagesEmitter;
    }

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster,
                              ImageWrapper32 image) {
        this(broadcaster, image, null, null);
    }

    public EllipseFittingTask withPrefilter() {
        prefilter = true;
        return this;
    }

    @Override
    public EllipseFittingTask.Result call() throws Exception {
        var analyzingDiskGeometryMsg = message("analyzing.disk.geometry");
        broadcaster.broadcast(ProgressEvent.of(0, analyzingDiskGeometryMsg));
        var imageMath = ImageMath.newInstance();
        var workingImage = imageMath.convolve(image, Kernel33.GAUSSIAN_BLUR);
        if (prefilter) {
            var avg = imageMath.areaAverage(imageMath.integralImage(workingImage), 0, 0, width, height);
            // some images (in particular in calcium) can have large bright areas which interfere with detection
            // so we're keeping pixels which are "around" the average
            new CutoffStretchingStrategy(0.8f * avg, 1.2f * avg, 0, 0).stretch(width, height, workingImage.data());
        }
        var magnitude = imageMath.gradient(workingImage).magnitude();
        var magnitudes = magnitude.data();
        var samples = findSamplesUsingDynamicSensitivity(analyzingDiskGeometryMsg, magnitudes);
        var fittingEllipseMessage = message("fitting.ellipse");
        broadcaster.broadcast(ProgressEvent.of(0, fittingEllipseMessage));
        if (notEnoughSamples(fittingEllipseMessage, samples)) {
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
            produceEdgeDetectionImage(result, ImageWrapper32.fromImage(workingImage));
        }
        return result;
    }

    private List<Point2D> findSamplesUsingDynamicSensitivity(String analyzingDiskGeometryMsg, float[] magnitudes) {
        List<Point2D> samples = new ArrayList<>();
        double sensitivity = 0.00d;
        var maxMagnitude = maxOf(magnitudes);
        while (samples.size() < MINIMUM_SAMPLES && sensitivity < 1) {
            samples.clear();
            var minLimit = sensitivity * maxMagnitude;
            sensitivity += 0.05d;
            // We can have bad samples because the sun disk can be truncated
            // so we remove those which are within a small distance of x coordinate of the previous sample
            int threshold = 3;
            int lastMin = -1;
            int lastMax = -1;
            double sum = 0;
            double minVal = 0;
            double maxVal = 0;
            for (int y = 0; y < height; y++) {
                broadcaster.broadcast(ProgressEvent.of((y + 1d / height) / height, analyzingDiskGeometryMsg));
                int min = -1;
                int max = -1;
                for (int x = 0; x < width; x++) {
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
                if (min >= 0 && Math.abs(min - lastMin) > threshold) {
                    var candidate = new Point2D(min, y);
                    samples.add(candidate);
                    lastMin = min;
                    sum += minVal;
                }
                if (max >= 0 && Math.abs(max - lastMax) > threshold) {
                    var candidate = new Point2D(max, y);
                    samples.add(candidate);
                    lastMax = max;
                    sum += maxVal;
                }
            }
            if (samples.size() >= MINIMUM_SAMPLES) {
                var samplesAvg = sum / samples.size();
                var closestNeighborDistances = samples
                        .stream()
                        .distinct()
                        .collect(Collectors.toMap(p -> p, p -> {
                            double d = Double.MAX_VALUE;
                            for (Point2D sample : samples) {
                                if (sample != p) {
                                    d = Math.min(d, p.distanceTo(sample));
                                }
                            }
                            return d;
                        }));
                var avgMinDistance = closestNeighborDistances.values().stream().mapToDouble(Double::doubleValue).average().orElse(Double.MAX_VALUE);
                samples.removeIf(p ->
                        (magnitudes[(int) (p.x() + p.y() * width)] < .5f * samplesAvg)
                        || (closestNeighborDistances.get(p) > 8 * avgMinDistance)
                );
            }
        }
        return samples;
    }

    private boolean notEnoughSamples(String fittingEllipseMessage, List<Point2D> filteredSamples) {
        if (filteredSamples.size() < MINIMUM_SAMPLES) {
            broadcaster.broadcast(ProgressEvent.of(1, fittingEllipseMessage));
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

    public record Result(
            Ellipse ellipse,
            List<Point2D> samples
    ) {

    }
}
