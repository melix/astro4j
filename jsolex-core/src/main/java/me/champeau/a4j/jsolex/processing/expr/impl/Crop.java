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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.crop.Cropper;
import me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombs;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


public class Crop extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Crop.class);
    private static final int DEFAULT_AR_SIZE = 32;

    public Crop(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object crop(Map<String, Object> arguments) {
        BuiltinFunction.CROP.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("crop", "img", arguments, this::crop);
        }
        var img = arguments.get("img");
        var left = intArg(arguments, "left", 0);
        var top = intArg(arguments, "top", 0);
        var width = intArg(arguments, "width", 0);
        var height = intArg(arguments, "height", 0);
        if (left < 0 || top < 0) {
            throw new IllegalArgumentException("top and left values must be >=0");
        }
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            return cropMonoImage(left, top, width, height, mono);
        } else if (img instanceof RGBImage rgb) {
            var ri = cropMonoImage(left, top, width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.r(), rgb.metadata()));
            var gi = cropMonoImage(left, top, width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.g(), rgb.metadata()));
            var bi = cropMonoImage(left, top, width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.b(), rgb.metadata()));
            return new RGBImage(width, height,
                    ri.data(),
                    gi.data(),
                    bi.data(),
                    ri.metadata()
            );
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    public Object cropToRect(Map<String, Object> arguments) {
        BuiltinFunction.CROP_RECT.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("crop_rect", "img", arguments, this::cropToRect);
        }
        var img = arguments.get("img");
        var width = intArg(arguments, "width", 0);
        var height = intArg(arguments, "height", 0);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height values must be >=0");
        }
        var ellipse = getEllipse(arguments, "ellipse");
        if (ellipse.isPresent()) {
            var sunDisk = ellipse.get();
            float blackPoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
            if (img instanceof FileBackedImage fileBackedImage) {
                img = fileBackedImage.unwrapToMemory();
            }
            if (img instanceof ImageWrapper32 mono) {
                return cropToRectMonoImage(width, height, mono, sunDisk, blackPoint);
            } else if (img instanceof RGBImage rgb) {
                var ri = cropToRectMonoImage(width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.r(), rgb.metadata()), sunDisk, blackPoint);
                var gi = cropToRectMonoImage(width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.g(), rgb.metadata()), sunDisk, blackPoint);
                var bi = cropToRectMonoImage(width, height, new ImageWrapper32(rgb.width(), rgb.height(), rgb.b(), rgb.metadata()), sunDisk, blackPoint);
                return new RGBImage(width, height,
                        ri.data(),
                        gi.data(),
                        bi.data(),
                        ri.metadata()
                );
            }
            throw new IllegalStateException("Unexpected image type " + img);
        } else {
            throw new IllegalStateException("Ellipse not found, cannot perform cropping");
        }
    }

    private static ImageWrapper32 cropMonoImage(int left, int top, int width, int height, ImageWrapper32 mono) {
        if (left + width > mono.width()) {
            throw new IllegalArgumentException("Crop width too large");
        }
        if (top + height > mono.height()) {
            throw new IllegalArgumentException("Crop height too large");
        }
        float[][] cropped = new float[height][width];
        var sourceData = mono.data();
        for (int y = 0; y < height; y++) {
            var line = sourceData[y + top];
            System.arraycopy(line, left, cropped[y], 0, width);
        }
        var metadata = fixMetadata(mono, new Cropper.CropResult(new Image(width, height, cropped), new DoublePair(left, top)));
        return new ImageWrapper32(width, height, cropped, metadata);
    }

    private static ImageWrapper32 cropToRectMonoImage(int width, int height, ImageWrapper32 mono, Ellipse sunDisk, float blackPoint) {
        var cropResult = Cropper.cropToRectangle(mono.asImage(), sunDisk, blackPoint, width, height);
        var metadata = fixMetadata(mono, cropResult);
        return ImageWrapper32.fromImage(cropResult.cropped(), metadata);
    }

    public Object autocrop(Map<String, Object> arguments) {
        BuiltinFunction.AUTOCROP.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("autocrop", "img", arguments, this::autocrop);
        }
        var ellipse = getEllipse(arguments, "ellipse");
        return doAutocrop(arg, ellipse, null, null);
    }

    public Object autocrop2(Map<String, Object> arguments) {
        BuiltinFunction.AUTOCROP2.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?> list) {
            if (arguments.size() == 1) {
                // collect all image dimensions and compute the rounding factor
                var images = list.stream()
                        .filter(ImageWrapper.class::isInstance)
                        .map(ImageWrapper.class::cast)
                        .toList();
                var maxDimension = images.stream()
                        .mapToInt(img -> Math.max(img.width(), img.height()))
                        .max()
                        .orElse(0);
                maxDimension = (int) Math.ceil(maxDimension / 16d) * 16;
                return expandToImageList("autocrop2", "img", Map.of("img", images, "width", maxDimension, "height", maxDimension), this::cropToRect);
            }
            return expandToImageList("autocrop2", "img", arguments, this::autocrop2);
        }
        var factor = doubleArg(arguments, "factor", 1.1);
        int rounding = intArg(arguments, "rounding", 16);
        if (rounding % 2 == 1) {
            throw new IllegalArgumentException("Rounding must be a factor of 2");
        }
        var ellipse = getEllipse(arguments, "ellipse");
        return doAutocrop(arg, ellipse, factor, rounding);
    }

    private Object doAutocrop(Object arg, Optional<Ellipse> ellipse, Double diameterFactor, Integer rounding) {
        Object finalArg = arg;
        var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElseGet(() -> {
            if (finalArg instanceof ImageWrapper wrapper && wrapper.unwrapToMemory() instanceof ImageWrapper32 mono) {
                if (ellipse.isPresent()) {
                    return (float) AnalysisUtils.estimateBlackPoint(mono, ellipse.get()) * 1.2f;
                }
                var histo = Histogram.builder(65536);
                for (float[] line : mono.data()) {
                    for (float v : line) {
                        histo.record(v);
                    }
                }
                return (float) histo.build().cumulative().percentile(0.01);
            }
            return 0f;
        });
        if (ellipse.isPresent()) {
            var circle = ellipse.get();
            if (arg instanceof FileBackedImage fileBackedImage) {
                arg = fileBackedImage.unwrapToMemory();
            }
            if (arg instanceof ImageWrapper32 mono) {
                var image = mono.asImage();
                var cropResult = Cropper.cropToSquare(image, circle, blackpoint, diameterFactor, rounding);
                var metadata = fixMetadata(mono, cropResult);
                return ImageWrapper32.fromImage(cropResult.cropped(), metadata);
            } else if (arg instanceof RGBImage rgb) {
                var cropResult = Cropper.cropToSquare(new Image(rgb.width(), rgb.height(), rgb.r()), circle, blackpoint, diameterFactor, rounding);
                var metadata = fixMetadata(rgb, cropResult);
                var r = cropResult.cropped();
                var g = Cropper.cropToSquare(new Image(rgb.width(), rgb.height(), rgb.g()), circle, blackpoint, diameterFactor, rounding).cropped();
                var b = Cropper.cropToSquare(new Image(rgb.width(), rgb.height(), rgb.b()), circle, blackpoint, diameterFactor, rounding).cropped();
                return new RGBImage(r.width(), r.height(), r.data(), g.data(), b.data(), metadata);
            }
            throw new IllegalStateException("Unsupported image type");
        } else {
            return arg;
        }
    }

    private static HashMap<Class<?>, Object> fixMetadata(ImageWrapper img, Cropper.CropResult cropResult) {
        var metadata = new HashMap<>(img.metadata());
        var left = cropResult.centerShift().a();
        var top = cropResult.centerShift().b();
        img.findMetadata(Ellipse.class).ifPresent(circle -> metadata.put(Ellipse.class, circle.translate(-left, -top)));
        img.findMetadata(Redshifts.class).ifPresent(redshifts -> {
            metadata.put(Redshifts.class, new Redshifts(
                    redshifts.redshifts().stream()
                            .map(rs -> new RedshiftArea(
                                    rs.id(),
                                    rs.pixelShift(),
                                    rs.relPixelShift(),
                                    rs.kmPerSec(),
                                    (int) (rs.x1() - left),
                                    (int) (rs.y1() - top),
                                    (int) (rs.x2() - left),
                                    (int) (rs.y2() - top),
                                    (int) (rs.maxX() - left),
                                    (int) (rs.maxY() - top)
                            ))
                            .toList()
            ));
        });
        img.findMetadata(ActiveRegions.class).ifPresent(activeRegions -> metadata.put(ActiveRegions.class, activeRegions.translate(-left, -top)));
        img.findMetadata(EllermanBombs.class).ifPresent(bombs -> metadata.put(EllermanBombs.class, bombs.translate(-left, -top)));
        img.findMetadata(ReferenceCoords.class).ifPresent(coords -> metadata.put(ReferenceCoords.class, coords.addOffsetX(left).addOffsetY(top)));
        return metadata;
    }

    /**
     * Given an image and a list of active regions, returns a list of cropped images, each containing one region.
     *
     * @param arguments an image or list of images
     * @return the cropped images
     */
    public Object cropActiveRegions(Map<String, Object> arguments) {
        BuiltinFunction.CROP_AR.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("crop_ar", "img", arguments, this::cropActiveRegions);
        }
        if (arg instanceof ImageWrapper wrapper) {
            int minSize = intArg(arguments, "ms", DEFAULT_AR_SIZE);
            double margin = doubleArg(arguments, "margin", 10) / 100d;
            var img = wrapper.unwrapToMemory();
            var activeRegions = img.findMetadata(ActiveRegions.class).orElse(null);
            if (activeRegions == null) {
                return List.of();
            }
            var list = activeRegions.regionList().stream()
                    .map(activeRegion -> {
                        var left = (int) activeRegion.topLeft().x();
                        var top = (int) activeRegion.topLeft().y();
                        var width = (int) activeRegion.width();
                        var height = (int) activeRegion.height();
                        if (width >= minSize && height >= minSize) {
                            // expand the crop area by 10%
                            var cropWidth = (int) (width * (1 + margin));
                            var cropHeight = (int) (height * (1 + margin));
                            var cropLeft = left - (cropWidth - width) / 2;
                            var cropTop = top - (cropHeight - height) / 2;
                            return crop(Map.of("img", img, "left", cropLeft, "top", cropTop, "width", cropWidth, "height", cropHeight));
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            if (list.isEmpty()) {
                LOGGER.info("No active region larger than {}x{} found", minSize, minSize);
            }
            return list;
        }
        return List.of();
    }
}
