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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.WorkflowState;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.detection.Flares;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.GeometryTransform;
import me.champeau.a4j.jsolex.processing.util.GeometryUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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
    private final Header header;

    public GeometryCorrector(Broadcaster broadcaster,
                             ProgressOperation operation,
                             Supplier<ImageWrapper32> image,
                             Ellipse ellipse,
                             Double forcedTilt,
                             Double frameRate,
                             Double xyRatio,
                             float blackPoint,
                             ProcessParams processParams,
                             ImageEmitter imageEmitter,
                             WorkflowState state,
                             Header header) {
        super(broadcaster, operation, image);
        this.ellipse = ellipse;
        this.forcedTilt = forcedTilt;
        this.frameRate = frameRate;
        this.xyRatio = xyRatio;
        this.blackPoint = blackPoint;
        this.processParams = processParams;
        this.imageEmitter = imageEmitter;
        this.state = state;
        this.header = header;
    }

    @Override
    public Result doCall() throws Exception {
        broadcaster.broadcast(operation.update(0, message("correcting.geometry")));
        
        var transform = GeometryTransform.of(ellipse, forcedTilt, xyRatio, height, processParams.geometryParams().isDisallowDownsampling());
        var semiAxis = ellipse.semiAxis();
        LOGGER.debug("a = {}, b={}, theta={}", semiAxis.a(), semiAxis.b(), transform.theta());
        if (xyRatio == null) {
            LOGGER.info(message("detected.xy.ratio"), String.format("%.2f", transform.detectedRatio()));
        }

        var corrected = GeometryUtils.applyGeometryCorrection(workImage, transform, blackPoint);

        var circle = GeometryUtils.computeCorrectedCircle(ellipse, transform);
        var metadata = new HashMap<>(getMetadata());
        metadata.put(Ellipse.class, circle);

        Redshifts redshifts = (Redshifts) metadata.get(Redshifts.class);
        if (redshifts != null) {
            redshifts = new Redshifts(redshifts.redshifts().stream()
                    .map(r -> new RedshiftArea(r.id(), r.pixelShift(), r.relPixelShift(), r.kmPerSec(), r.kmPerSecError(),
                            (int) transform.transformX(r.x1(), r.y1()), (int) transform.transformY(r.y1()),
                            (int) transform.transformX(r.x2(), r.y2()), (int) transform.transformY(r.y2()),
                            (int) transform.transformX(r.maxX(), r.maxY()), (int) transform.transformY(r.maxY())))
                    .toList());
            metadata.put(Redshifts.class, redshifts);
        }
        var activeRegions = (ActiveRegions) metadata.get(ActiveRegions.class);
        if (activeRegions != null) {
            activeRegions = activeRegions.transform(p -> new Point2D(transform.transformX(p.x(), p.y()), transform.transformY(p.y())));
            metadata.put(ActiveRegions.class, activeRegions);
        }
        var flares = (Flares) metadata.get(Flares.class);
        if (flares != null) {
            flares = flares.transform(p -> new Point2D(transform.transformX(p.x(), p.y()), transform.transformY(p.y())));
            metadata.put(Flares.class, flares);
        }
        corrected = new ImageWrapper32(corrected.width(), corrected.height(), corrected.data(), metadata);

        corrected.transformMetadata(ReferenceCoords.class, coords ->
            coords.addShearShiftCombined(transform.shear(), transform.shift())
                  .addScaleX(transform.sx())
                  .addScaleY(transform.sy())
        );

        TransformationHistory.recordTransform(corrected, message("geometry.correction"));
        if (processParams.observationDetails().altAzMode()) {
            var coordinates = processParams.observationDetails().coordinates();
            if (coordinates != null) {
                double angle = SolarParametersUtils.computeParallacticAngleRad(
                        processParams.observationDetails().date(),
                        coordinates.a(),
                        coordinates.b()
                );
                LOGGER.info(String.format(message("parallactic.angle"), Math.toDegrees(angle)));
                var rotated = new Rotate(MutableMap.of(Ellipse.class, ellipse), broadcaster).rotateRadians(
                        Map.of("img", corrected, "angle", angle, "bp", 0, "resize", 1)
                );
                corrected = (ImageWrapper32) rotated;
            }
        }
        var autocropMode = processParams.geometryParams().autocropMode();
        if (autocropMode != null) {
            var cropping = new Crop(MutableMap.of(Ellipse.class, ellipse), broadcaster);
            corrected = switch (autocropMode) {
                case RADIUS_1_1 -> (ImageWrapper32) cropping.autocrop2(Map.of("img", corrected, "factor", 1.1));
                case RADIUS_1_2 -> (ImageWrapper32) cropping.autocrop2(Map.of("img", corrected, "factor", 1.2));
                case RADIUS_1_5 -> (ImageWrapper32) cropping.autocrop2(Map.of("img", corrected, "factor", 1.5));
                case SOURCE_WIDTH -> {
                    var targetWidth = header == null ? width : header.geometry().width();
                    var center = circle.center();
                    var halfWidth = targetWidth / 2d;
                    var cx = center.a();
                    var cy = center.b();
                    if (cx - halfWidth >= 0 && (cy - halfWidth >= 0) && (cx + halfWidth <= targetWidth) && (cy + halfWidth <= targetWidth)) {
                        yield (ImageWrapper32) cropping.cropToRect(Map.of("img", corrected, "width", targetWidth, "height", targetWidth));
                    } else {
                        LOGGER.warn(message("destructive.cannot.crop"));
                        yield corrected;
                    }
                }
                case FIXED_WIDTH -> {
                    var fixedWidth = processParams.geometryParams().fixedWidth().orElse(1024);
                    yield (ImageWrapper32) cropping.cropToRect(Map.of("img", corrected, "width", fixedWidth, "height", fixedWidth));
                }
                default -> corrected;
            };
            corrected.transformMetadata(ReferenceCoords.class, ReferenceCoords::geoCorrectionMarker);
            TransformationHistory.recordTransform(corrected, message("autocrop"));
        }
        broadcaster.broadcast(operation.complete());
        var wrapped = FileBackedImage.wrap(corrected);
        return new Result(wrapped, wrapped, ellipse, corrected.findMetadata(Ellipse.class).orElse(circle), blackPoint);
    }

    public record Result(
            ImageWrapper corrected,
            ImageWrapper enhanced,
            Ellipse originalEllipse,
            Ellipse correctedCircle,
            float blackpoint
    ) {
        public Result withEnhanced(ImageWrapper32 enhanced) {
            return new Result(FileBackedImage.wrap(corrected), FileBackedImage.wrap(enhanced), originalEllipse, correctedCircle, blackpoint);
        }
    }
}
