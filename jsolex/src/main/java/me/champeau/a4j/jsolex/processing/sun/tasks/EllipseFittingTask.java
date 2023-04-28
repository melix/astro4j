/*
 * Copyright 2003-2021 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.MagnitudeDetectorSupport;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static me.champeau.a4j.math.fft.FFTSupport.nextPowerOf2;

public class EllipseFittingTask extends AbstractTask<EllipseFittingTask.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EllipseFittingTask.class);

    /**
     * Creates ellipse fitting task
     *
     * @param image the image to work with
     */
    public EllipseFittingTask(Broadcaster broadcaster, ImageWrapper32 image) {
        super(broadcaster, image);
    }

    @Override
    public EllipseFittingTask.Result call() throws Exception {
        List<Point2D> samples = new ArrayList<>();
        // We can have bad samples because the sun disk can be truncated
        // so we remove those which are within a small distance of x coordinate of the previous sample
        int threshold = 3;
        int lastMin = -1;
        int lastMax = -1;
        int magnitudeDetectionWidth = nextPowerOf2(width);
        double offset = (magnitudeDetectionWidth - width) / 2d;
        for (int y = 0; y < height; y++) {
            var line = new float[width];
            System.arraycopy(buffer, y * width, line, 0, width);
            var magnitudes = MagnitudeDetectorSupport.computeMagnitudes(width, line);
            var edges = MagnitudeDetectorSupport.findEdges(magnitudes, 10);
            int min = edges.a();
            int max = edges.b();
            if (min >= 0 && Math.abs(min - lastMin) > threshold) {
                double x = min - offset;
                samples.add(new Point2D(x, y));
                lastMin = min;
            }
            if (max >= 0 && Math.abs(max - lastMax) > threshold) {
                double x = max - offset;
                samples.add(new Point2D(x, y));
                lastMax = max;
            }
        }

        var ellipse = new EllipseRegression(samples)
                .solve();
        // We're now going to filter samples by keeping only those which are within the predicted
        // eclipse, to reduce influence of outliers
        ellipse = new EllipseRegression(
                samples.stream()
                        .filter(ellipse::isWithin)
                        .toList()
                // rescale so that the limb is visible
        ).solve().scale(0.99d);
        LOGGER.info("{}", ellipse);
        return new Result(ellipse, Collections.unmodifiableList(samples));
    }

    public record Result(
            Ellipse ellipse,
            List<Point2D> samples
    ) {

    }
}
