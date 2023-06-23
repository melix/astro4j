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
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class GeometryCorrector extends AbstractTask<GeometryCorrector.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryCorrector.class);

    private final Ellipse ellipse;
    private final Double forcedTilt;
    private final Double xyRatio;
    private final Double frameRate;
    private final float blackPoint;
    private final ProcessParams processParams;
    private final ImageEmitter imageEmitter;
    private final WorkflowState state;

    public GeometryCorrector(Broadcaster broadcaster,
                             ImageWrapper32 image,
                             Ellipse ellipse,
                             Double forcedTilt,
                             Double frameRate,
                             Double xyRatio,
                             float blackPoint,
                             ProcessParams processParams,
                             ImageEmitter imageEmitter,
                             WorkflowState state) {
        super(broadcaster, image);
        this.ellipse = ellipse;
        this.forcedTilt = forcedTilt;
        this.frameRate = frameRate;
        this.xyRatio = xyRatio;
        this.blackPoint = blackPoint;
        this.processParams = processParams;
        this.imageEmitter = imageEmitter;
        this.state = state;
    }

    @Override
    public Result call() throws Exception {
        broadcaster.broadcast(ProgressEvent.of(0, "Correcting geometry"));
        var theta = forcedTilt == null ? ellipse.rotationAngle() : forcedTilt;
        var m = Math.tan(-theta);
        var semiAxis = ellipse.semiAxis();
        var a = semiAxis.a();
        var b = semiAxis.b();
        var cos = Math.cos(theta);
        var sin = Math.sin(theta);
        var shear = (m * cos * a * a + sin * b * b) / (b * b * cos - a * a * m * sin);
        LOGGER.debug("a = {}, b={}, theta={}", a, b, theta);
        boolean performTransform = Math.abs(180 * theta / Math.PI) > 1;
        var maxDx = height * shear;
        var shift = maxDx < 0 ? maxDx : 0;
        float[] newBuffer;
        int extendedWidth = width;
        if (performTransform) {
            extendedWidth = width + (int) Math.ceil(Math.abs(maxDx));
            newBuffer = new float[height * extendedWidth];
            Arrays.fill(newBuffer, blackPoint);
            for (int y = 0; y < height; y++) {
                var dx = y * shear;
                for (int x = 0; x < width; x++) {
                    int nx = (int) (x - shift + dx);
                    newBuffer[nx + y * extendedWidth] = buffer[x + y * width];
                    // reduce transform artifacts by filling with same border color
                    if (x == 0) {
                        for (int k = 0; k < nx; k++) {
                            newBuffer[k + y * extendedWidth] = buffer[x + y * width];
                        }
                    } else if (x == width - 1) {
                        for (int k = nx; k < extendedWidth; k++) {
                            newBuffer[k + y * extendedWidth] = buffer[x + y * width];
                        }
                    }
                }
            }
        } else {
            LOGGER.info(message("good.tilt"));
            newBuffer = new float[buffer.length];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        }
        double sx;
        double sy = Math.abs((a * b * Math.sqrt((a * a * m * m + b * b) / (a * a * sin * sin + b * b * cos * cos)) / (b * b * cos - a * a * m * sin)));
        LOGGER.info(message("detected.xy.ratio"), String.format("%.2f", sy));
        if (xyRatio != null) {
            sy = xyRatio;
        }
        if (sy < 1) {
            // because we don't want to do downsampling, we're inverting ratios
            sx = 1 / sy;
            sy = 1.0d;
        } else {
            sx = 1.0d;
        }
        var rescaled = ImageMath.newInstance().rotateAndScale(new Image(extendedWidth, height, newBuffer), 0, blackPoint, sx, sy);
        double finalSy = sy;
        var circle = computeCorrectedCircle(shear, shift, sx, finalSy);
        broadcaster.broadcast(ProgressEvent.of(1, "Correcting geometry"));
        return new Result(ImageWrapper32.fromImage(rescaled), ellipse, circle);
    }

    /**
     * Performs new ellipse regression, where sample points are taken from the
     * original ellipse, but corrected in the same way as we correct the image,
     * that is to say with a shear transform + x/y ratio correction.
     * @param shear the shear value
     * @param shift pixel shifting to avoid negative number overflow
     * @param sx the x correction ratio
     * @param sy the y correction ratio
     * @return a circle, if detected, or null.
     */
    private Ellipse computeCorrectedCircle(double shear, double shift, double sx, double sy) {
        var newSamples = IntStream.range(0, 32)
                .mapToDouble(i -> i * Math.PI / 16)
                .mapToObj(ellipse::toCartesian)
                .map(p -> {
                    var newX = (p.x() - shift + p.y() * shear);
                    return new Point2D(newX * sx, p.y() * sy);
                })
                .toList();
        Ellipse correctedEllipse = null;
        try {
            correctedEllipse = new EllipseRegression(newSamples).solve();
        } catch (Exception ex) {
            // ignore
        }
        return correctedEllipse;
    }

    public record Result(
            ImageWrapper32 corrected,
            Ellipse originalEllipse,
            Ellipse correctedCircle
    ) {
    }
}
