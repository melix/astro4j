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
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;

public class GeometryCorrector extends AbstractTask<ImageWrapper32> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeometryCorrector.class);

    private final Ellipse ellipse;
    private final double correctionAngle;
    private final float blackpoint;
    private final Double frameRate;
    private final OptionalDouble forcedRatio;

    public GeometryCorrector(Broadcaster broadcaster,
                             ImageWrapper32 image,
                             Ellipse ellipse,
                             double correctionAngle,
                             float blackpoint,
                             Double frameRate,
                             OptionalDouble forcedRatio) {
        super(broadcaster, image);
        this.ellipse = ellipse;
        this.correctionAngle = correctionAngle;
        this.blackpoint = blackpoint;
        this.frameRate = frameRate;
        this.forcedRatio = forcedRatio;
    }

    @Override
    public ImageWrapper32 call() throws Exception {
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
        var rotated = ImageMath.newInstance().rotateAndScale(buffer, width, height, correctionAngle, blackpoint, sx, sy);
        broadcaster.broadcast(ProgressEvent.of(1, "Correcting geometry"));
        return new ImageWrapper32(rotated.width(), rotated.height(), rotated.data());
    }
}
