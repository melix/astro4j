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
import me.champeau.a4j.jsolex.processing.sun.crop.Cropper;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

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

    public GeometryCorrector(Broadcaster broadcaster,
                             ImageWrapper32 image,
                             Ellipse ellipse,
                             Double forcedTilt,
                             Double frameRate,
                             Double xyRatio,
                             float blackPoint,
                             ProcessParams processParams,
                             ImageEmitter imageEmitter) {
        super(broadcaster, image);
        this.ellipse = ellipse;
        this.forcedTilt = forcedTilt;
        this.frameRate = frameRate;
        this.xyRatio = xyRatio;
        this.blackPoint = blackPoint;
        this.processParams = processParams;
        this.imageEmitter = imageEmitter;
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
        float[] newBuffer;
        int extendedWidth = width;
        if (performTransform) {
            extendedWidth = width + (int) Math.ceil(Math.abs(maxDx));
            newBuffer = new float[height * extendedWidth];
            Arrays.fill(newBuffer, blackPoint);
            var shift = maxDx < 0 ? maxDx : 0;
            for (int y = 0; y < height; y++) {
                var dx = y * shear;
                for (int x = 0; x < width; x++) {
                    int nx = (int) (x - shift + dx);
                    newBuffer[nx + y * extendedWidth] = buffer[x + y * width];
                }
            }
        } else {
            LOGGER.info(message("good.tilt"));
            newBuffer = new float[buffer.length];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        }
        double sx = 1.0d;
        double sy = Math.abs((a * b * Math.sqrt((a * a * m * m + b * b) / (a * a * sin * sin + b * b * cos * cos)) / (b * b * cos - a * a * m * sin)));
        LOGGER.info(message("detected.xy.ratio"), String.format("%.2f", sy));
        if (xyRatio != null) {
            sy = xyRatio;
        }
        if (sy < 1) {
            // because we don't want to do downsampling, we're inverting ratios
            sx = 1 / sy;
            sy = 1.0d;
        }
        var rotated = ImageMath.newInstance().rotateAndScale(new Image(extendedWidth, height, newBuffer), 0, blackPoint, sx, sy);
        broadcaster.broadcast(ProgressEvent.of(1, "Correcting geometry"));
        var full = new ImageWrapper32(rotated.width(), rotated.height(), rotated.data());
        return new Result(crop(rotated, full), ellipse);
    }

    private Ellipse performFitting(ImageWrapper32 image) {
        EllipseFittingTask.Result fitting;
        try {
            fitting = new EllipseFittingTask(broadcaster, image, .25d).call();
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        return fitting.ellipse();
    }

    private ImageWrapper32 crop(Image rotated, ImageWrapper32 full) {
        var diskEllipse = performFitting(full);
        return ImageWrapper32.fromImage(Cropper.cropToSquare(rotated, diskEllipse, blackPoint));
    }

    public record Result(
            ImageWrapper32 corrected,
            Ellipse disk
    ) {

    }
}
