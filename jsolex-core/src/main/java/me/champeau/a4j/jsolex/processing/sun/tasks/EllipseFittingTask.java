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
import me.champeau.a4j.jsolex.processing.sun.MagnitudeDetectorSupport;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);
    private final double sensitivity;

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster, ImageWrapper32 image, double sensitivity) {
        super(broadcaster, image);
        this.sensitivity = sensitivity;
    }

    @Override
    public EllipseFittingTask.Result call() throws Exception {
        broadcaster.broadcast(ProgressEvent.of(0, "Analyzing disk geometry"));
        List<Point2D> samples = new ArrayList<>();
        // We can have bad samples because the sun disk can be truncated
        // so we remove those which are within a small distance of x coordinate of the previous sample
        int threshold = 3;
        int lastMin = -1;
        int lastMax = -1;
        for (int y = 0; y < height; y++) {
            broadcaster.broadcast(ProgressEvent.of((y+1d/height)/height, "Analyzing disk geometry"));
            var line = new float[width];
            System.arraycopy(buffer, y * width, line, 0, width);
            var edges = MagnitudeDetectorSupport.findEdges(line, sensitivity);
            int min = edges.a();
            int max = edges.b();
            if (min >= 0 && Math.abs(min - lastMin) > threshold) {
                samples.add(new Point2D(min, y));
                lastMin = min;
            }
            if (max >= 0 && Math.abs(max - lastMax) > threshold) {
                samples.add(new Point2D(max, y));
                lastMax = max;
            }
        }
        broadcaster.broadcast(ProgressEvent.of(0, "Fitting ellipse"));
        samples = samples.stream()
                .map(p -> new Object() {
                    final Point2D point = p;
                    final float value = valueAt(p);
                })
                .sorted((a, b) -> Float.compare(b.value, a.value))
                .limit((int) (9d * samples.size() / 10))
                .map(p -> p.point)
                .toList();
        var ellipse = new EllipseRegression(samples).solve();
        // We're now going to filter samples by keeping only those which are within the predicted
        // eclipse, to reduce influence of outliers
        var filteredSamples = samples;
        for (int i = 0;i<2;i++) {
            filteredSamples = samples.stream().filter(ellipse::isWithin)
                    .toList();
            ellipse = new EllipseRegression(filteredSamples).solve();
        }
        LOGGER.info("{}", ellipse);
        broadcaster.broadcast(ProgressEvent.of(1, "Fitting ellipse"));
        return new Result(ellipse, filteredSamples);
    }

    private float valueAt(Point2D point) {
        return buffer[(int) (point.y() * width + point.x())];
    }

    public record Result(
            Ellipse ellipse,
            List<Point2D> samples
    ) {

    }
}
