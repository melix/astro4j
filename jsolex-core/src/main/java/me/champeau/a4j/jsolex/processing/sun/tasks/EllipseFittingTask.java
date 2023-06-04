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

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);
    private static final int MINIMUM_SAMPLES = 10;

    private final Image image;
    private final double sensitivity;

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster, ImageWrapper32 image, double sensitivity) {
        super(broadcaster, image);
        this.sensitivity = sensitivity;
        this.image = image.asImage();
    }

    @Override
    public EllipseFittingTask.Result call() throws Exception {
        var analyzingDiskGeometryMsg = message("analyzing.disk.geometry");
        broadcaster.broadcast(ProgressEvent.of(0, analyzingDiskGeometryMsg));
        var magnitude = ImageMath.newInstance().gradient(image).magnitude();
        var magnitudes = magnitude.data();
        var minLimit = sensitivity * maxOf(magnitudes);
        List<Point2D> samples = new ArrayList<>();
        // We can have bad samples because the sun disk can be truncated
        // so we remove those which are within a small distance of x coordinate of the previous sample
        int threshold = 3;
        int lastMin = -1;
        int lastMax = -1;
        for (int y = 0; y < height; y++) {
            broadcaster.broadcast(ProgressEvent.of((y + 1d / height) / height, analyzingDiskGeometryMsg));
            int min = -1;
            int max = -1;
            for (int x = 0; x < width / 2; x++) {
                var mag = magnitudes[x + y * width];
                if (min == -1 && mag > minLimit) {
                    min = x;
                }
                mag = magnitudes[(width - x - 1) + y * width];
                if (max == -1 && mag > minLimit) {
                    max = (width - x - 1);
                }
                if (min != -1 && max != -1) {
                    break;
                }
            }
            if (min >= 0 && Math.abs(min - lastMin) > threshold) {
                samples.add(new Point2D(min, y));
                lastMin = min;
            }
            if (max >= 0 && Math.abs(max - lastMax) > threshold) {
                samples.add(new Point2D(max, y));
                lastMax = max;
            }
        }
        var fittingEllipseMessage = message("fitting.ellipse");
        broadcaster.broadcast(ProgressEvent.of(0, fittingEllipseMessage));

        if (notEnoughSamples(fittingEllipseMessage, samples)) {
            return null;
        }
        var ellipse = new EllipseRegression(samples).solve();
        // We're doing a 2d pass, by excluding outliers
        var center = ellipse.center();
        var cp = new Point2D(center.a(), center.b());
        var semiAxis = ellipse.semiAxis();
        var outlierDistance = (semiAxis.a() + semiAxis.b()) / 2;
        var filteredSamples = samples.stream()
                .filter(p -> cp.distanceTo(p) > outlierDistance)
                .toList();
        if (notEnoughSamples(fittingEllipseMessage, filteredSamples)) {
            return null;
        }
/*
        float[] array = new float[width * height];
        for (Point2D filteredSample : filteredSamples) {
            array[(int) (filteredSample.x() + filteredSample.y() * width)] = 65535;
        }
        ImageUtils.writeMonoImage(width, height, array, new File("/tmp/foo.png"));
*/
        ellipse = new EllipseRegression(filteredSamples).solve();
        LOGGER.debug("{}", ellipse);
        broadcaster.broadcast(ProgressEvent.of(1, fittingEllipseMessage));
        return new Result(ellipse, filteredSamples);
    }

    private boolean notEnoughSamples(String fittingEllipseMessage, List<Point2D> filteredSamples) {
        if (filteredSamples.size() < MINIMUM_SAMPLES) {
            broadcaster.broadcast(ProgressEvent.of(1, fittingEllipseMessage));
            LOGGER.error(message("ellipse.not.enough.samples"), filteredSamples.size(), MINIMUM_SAMPLES);
            return true;
        }
        return false;
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
