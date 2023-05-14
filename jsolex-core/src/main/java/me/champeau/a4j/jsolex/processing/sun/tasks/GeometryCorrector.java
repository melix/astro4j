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
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalDouble;

public class GeometryCorrector extends AbstractTask<GeometryCorrector.Result> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryCorrector.class);

    private final Ellipse ellipse;
    private final double correctionAngle;
    private final Double frameRate;
    private final OptionalDouble forcedRatio;
    // This is the sun disk as detected after the initial image correction
    // So that next images use the same geometry
    private final Optional<Ellipse> sunDisk;

    public GeometryCorrector(Broadcaster broadcaster,
                             ImageWrapper32 image,
                             Ellipse ellipse,
                             double correctionAngle,
                             Double frameRate,
                             OptionalDouble forcedRatio,
                             Optional<Ellipse> sunDisk) {
        super(broadcaster, image);
        this.ellipse = ellipse;
        this.correctionAngle = correctionAngle;
        this.frameRate = frameRate;
        this.forcedRatio = forcedRatio;
        this.sunDisk = sunDisk;
    }

    @Override
    public Result call() throws Exception {
        broadcaster.broadcast(ProgressEvent.of(0, "Correcting geometry"));
        var ratio = ellipse.xyRatio();
        if (forcedRatio.isPresent()) {
            ratio = forcedRatio.getAsDouble();
            LOGGER.info("Overriding X/Y ratio to {}", String.format("%.2f", ratio));
        }
        double sx, sy;
        if (ratio < 1) {
            sx = 1d;
            sy = 1d / ratio;
            if (ratio < 0.98 && frameRate != null && forcedRatio.isEmpty()) {
                double exposureInMillis = 1000d / frameRate;
                broadcaster.broadcast(new SuggestionEvent(
                        "Image is undersampled by a factor of " +
                        String.format("%.2f", ratio) +
                        ". Try to use " + String.format("%.2f ms exposure", exposureInMillis * ratio)
                        + " at acquisition instead of " + String.format("%.2f fps", exposureInMillis))
                );
            }
        } else {
            sx = ratio;
            sy = 1d;
        }
        var rotated = ImageMath.newInstance().rotateAndScale(buffer, width, height, correctionAngle, 0, sx, sy);
        broadcaster.broadcast(ProgressEvent.of(1, "Correcting geometry"));
        var full = new ImageWrapper32(rotated.width(), rotated.height(), rotated.data());
        return crop(rotated, full);
    }

    private Result crop(ImageMath.Image rotated, ImageWrapper32 full) {
        var diskEllipse = sunDisk.orElseGet(() -> {
            EllipseFittingTask.Result fitting;
            try {
                fitting = new EllipseFittingTask(broadcaster, full, 6d).call();
            } catch (Exception e) {
                throw new ProcessingException(e);
            }
            return fitting.ellipse();
        });
        // at this stage, the new fitting should give us a good estimate of the center and radius
        // because if geometry correction worked, the disk should be circle, so we can crop to a square
        var center = diskEllipse.center();
        var source = rotated.data();
        var cx = center.a();
        var cy = center.b();
        var diameter = (diskEllipse.semiAxis().a() + diskEllipse.semiAxis().b());
        var croppedSize = 1.2d * diameter;
        var croppedWidth = (int) Math.round(Math.min(rotated.width(), croppedSize));
        var croppedHeight = (int) Math.round(Math.min(rotated.height(), croppedSize));
        var cropped = new float[croppedWidth * croppedHeight];
        var dx = cx - croppedWidth / 2d;
        var dy = cy - croppedHeight / 2d;
        for (int y = 0; y < croppedHeight; y++) {
            for (int x = 0; x < croppedWidth; x++) {
                int idx = x + y * croppedWidth;
                int sourceX = (int) Math.round(dx + x);
                int sourceY = (int) Math.round(dy + y);
                if (sourceX >= 0 && sourceY >= 0 && sourceX < rotated.width() && sourceY < rotated.height()) {
                    cropped[idx] = source[sourceX + sourceY * rotated.width()];
                }
            }
        }
        return new Result(new ImageWrapper32(croppedWidth, croppedHeight, cropped), diskEllipse);
    }

    public record Result(
            ImageWrapper32 corrected,
            Ellipse disk
    ) {

    }
}