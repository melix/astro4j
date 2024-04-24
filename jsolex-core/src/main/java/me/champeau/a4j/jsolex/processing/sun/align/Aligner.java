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
package me.champeau.a4j.jsolex.processing.sun.align;

import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.crop.Cropper;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Aligner {
    private static final Logger LOGGER = LoggerFactory.getLogger(Aligner.class);
    public static final int WORK_SIZE = 1024;
    public static final int INITIAL_DIVISION_COUNT = 7;
    public static final int MAX_ITERATIONS = 8;

    private final Broadcaster broadcaster;
    private final ImageMath imageMath;
    private final Image referenceImage;
    private final Layer referenceLayer;

    private Aligner(Broadcaster broadcaster, ImageMath imageMath, Image referenceImage, Layer referenceLayer) {
        this.broadcaster = broadcaster;
        this.imageMath = imageMath;
        this.referenceImage = referenceImage;
        this.referenceLayer = referenceLayer;
    }

    public static Aligner forReferenceImage(Broadcaster broadcaster, Image ref) {
        var imageMath = ImageMath.newInstance();
        var prepared = prepareForAlignment(ref, imageMath);
        broadcaster.broadcast(new ImageGeneratedEvent(new GeneratedImage(
                GeneratedImageKind.DEBUG, "Reference", Path.of("/tmp/cropped.png"), ImageWrapper32.fromImage(prepared.cropped)
        )));
        return new Aligner(broadcaster, imageMath, prepared.cropped, Layer.of(prepared.rescaled));
    }

    /**
     * Prepares an image for alignment by cropping it to a square,
     * scaling to 1024x1024 then applying lightness normalization.
     *
     * @param image the source image
     * @return the prepared image
     */
    static WorkImage prepareForAlignment(Image image, ImageMath imageMath) {
        var normalized = image.copy();
        RangeExpansionStrategy.DEFAULT.stretch(ImageWrapper32.fromImage(normalized));
        var cropped = fitAndCrop(normalized);
        var rescaled = imageMath.rescale(cropped, WORK_SIZE, WORK_SIZE);
        return new WorkImage(cropped, rescaled);
    }

    private static Image fitAndCrop(Image image) {
        Image prepared;
        var task = new EllipseFittingTask(Broadcaster.NO_OP, () -> ImageWrapper32.fromImage(image));
        EllipseFittingTask.Result fitting;
        try {
            fitting = task.call();
            prepared = Cropper.cropToSquare(image, fitting.ellipse(), 0, null, null).cropped();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        return prepared;
    }

    private static RotationResult futureGet(Future<RotationResult> fut) {
        try {
            return fut.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(e);
        } catch (ExecutionException e) {
            throw ProcessingException.wrap(e.getCause());
        }
    }

    public AlignedImage align(Image candidate) {
        var prepared = prepareForAlignment(candidate, imageMath).rescaled;
        DoublePair normal = estimateRotation(prepared);
        DoublePair flipped = estimateRotation(imageMath.mirror(prepared, true, false));
        boolean flip = normal.b() > flipped.b();
        if (flip) {
            LOGGER.info("Image is flipped");
            return alignedImage(candidate, true, flipped.a());
        }
        return alignedImage(candidate, false, normal.a());
    }

    private DoublePair estimateRotation(Image unrotated) {
        int steps = INITIAL_DIVISION_COUNT;
        var range = 2 * Math.PI;
        int count = 0;
        double curAngle = 0;
        double score = 0;
        try (var executor = ParallelExecutor.newExecutor(Runtime.getRuntime().availableProcessors())) {
            while (count < MAX_ITERATIONS) {
                List<Future<RotationResult>> angles = new ArrayList<>();
                for (int n = 0; n < steps; n++) {
                    var rotationAngle = curAngle + n * range / steps;
                    var nn = n;
                    angles.add(executor.submit(() -> {
                        var rotated = imageMath.rotate(unrotated, rotationAngle, 0f, false);
                        var otherLayer = Layer.of(rotated);
                        return new RotationResult(multiScaleDistance(otherLayer, steps), rotationAngle, nn);
                    }));
                }
                var bestResults = angles.stream()
                        .map(Aligner::futureGet)
                        .sorted(Comparator.comparing(RotationResult::distance))
                        .limit(3)
                        .toList();
                var best1 = bestResults.get(0);
                var best2 = bestResults.get(1);
                var best3 = bestResults.get(2);
                score += best1.distance;
                if (best1.distance == 0) {
                    curAngle = best1.angle;
                    break;
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Estimated rotation {}", formatAngle(curAngle));
                }
                // there are 2 possibilities: either the best angle is between the second and third one
                // in which case we have a good idea where the maximum is, or we are in a situation where
                // the best angle is further from the 3rd one, in which case we have to perform a broader
                // search
                count++;
                var sortedByAngle = bestResults.stream().sorted(Comparator.comparing(RotationResult::angle)).toList();
                if ((best1.angle > best2.angle && best1.angle < best3.angle) || (best1.angle > best3.angle && best1.angle < best2.angle)) {
                    // narrow the range
                    range = sortedByAngle.get(2).angle - sortedByAngle.get(0).angle;
                    curAngle = sortedByAngle.get(0).angle;
                } else {
                    range = range / 2;
                    curAngle = best1.angle - range / 2;
                }
            }
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
        return new DoublePair(curAngle, score);
    }

    private AlignedImage alignedImage(Image candidate, boolean flip, double curAngle) {
        var rotated = candidate;
        if (flip) {
            rotated = imageMath.mirror(rotated, true, false);
        }
        rotated = fitAndCrop(imageMath.rotate(rotated, curAngle, 0, false));
        rotated = imageMath.rescale(rotated, referenceImage.width(), referenceImage.height());
        LOGGER.info("Output dimensions {}x{} vs reference {}x{}", rotated.width(), rotated.height(), referenceImage.width(), referenceImage.height());
        return new AlignedImage(candidate, rotated, flip, curAngle);
    }

    private double multiScaleDistance(Layer otherLayer, int steps) {
        return referenceLayer.distanceTo(otherLayer, Math.max(3, steps - 1));
    }

    private static String formatAngle(double radians) {
        return String.format("%.2f°", 180 * radians / Math.PI);
    }

    private record RotationResult(double distance, double angle, int n) {

    }

    private record WorkImage(Image cropped, Image rescaled) {

    }

    public record AlignedImage(Image image, Image rotated, boolean flipped, double rotation) {
        @Override
        public String toString() {
            return "AlignedImage{" +
                   "flipped=" + flipped +
                   ", rotation=" + rotation +
                   '}';
        }
    }
}
