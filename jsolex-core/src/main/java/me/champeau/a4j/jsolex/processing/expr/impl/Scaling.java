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
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Scaling extends AbstractFunctionImpl {
    private final ImageMath imageMath = ImageMath.newInstance();
    private final EllipseFit ellipseFit;
    private final Crop crop;

    public Scaling(Map<Class<?>, Object> context, Broadcaster broadcaster, Crop crop) {
        super(context, broadcaster);
        this.crop = crop;
        ellipseFit = new EllipseFit(context, broadcaster);
    }

    public Object relativeRescale(Map<String, Object> arguments) {
        BuiltinFunction.RESCALE_REL.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("rescale_rel", "img", arguments, this::relativeRescale);
        }
        double scaleX = doubleArg(arguments, "sx", 1);
        double scaleY = doubleArg(arguments, "sy", 1);
        if (scaleX < 0 || scaleY < 0) {
            throw new IllegalArgumentException("scaleX and scaleY must be > 0");
        }
        if (arg instanceof ImageWrapper img) {
            var width = (int) Math.round(img.width() * scaleX);
            var height = (int) Math.round(img.height() * scaleY);
            var result = doRescale(img, width, height);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    public Object absoluteRescale(Map<String, Object> arguments) {
        BuiltinFunction.RESCALE_ABS.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("rescale_abs", "img", arguments, this::absoluteRescale);
        }
        int width = intArg(arguments, "width", 0);
        int height = intArg(arguments, "height", 0);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Width and height must be >= 0");
        }
        if (arg instanceof ImageWrapper img) {
            var result = doRescale(img, width, height);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    public Object radiusRescale(Map<String, Object> arguments) {
        BuiltinFunction.RADIUS_RESCALE.validateArgs(arguments);
        var arg = arguments.get("images");
        if (arg instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("radius_rescale requires a non-empty list of images");
            }
            var filtered = list.stream()
                    .filter(ImageWrapper.class::isInstance)
                    .map(ImageWrapper.class::cast)
                    .toList();
            return performRadiusRescale(filtered);
        } else {
            throw new IllegalArgumentException("radius_rescale requires a list of images");
        }
    }

    List<ImageWrapper> performRadiusRescale(List<? extends ImageWrapper> filtered) {
        var fittings = filtered.stream()
                .parallel()
                .map(img -> {
                    ImageWrapper withEllipse = img;
                    var ellipse = img.findMetadata(Ellipse.class).orElse(null);
                    if (ellipse == null) {
                        withEllipse = (ImageWrapper) ellipseFit.fit(Map.of("img", img));
                    }
                    return withEllipse;
                })
                .collect(Collectors.toMap(
                        img -> img,
                        img -> {
                            var fit = img.findMetadata(Ellipse.class).orElse(null);
                            return radiusOf(fit);
                        },
                        (a, b) -> a, // Merge function (not used but required for toMap)
                        LinkedHashMap::new  // Ensures insertion order is maintained
                ));
        var targetRadius = fittings.values().stream().mapToDouble(Double::doubleValue).max();
        if (targetRadius.isPresent()) {
            List<ImageWrapper> result = new ArrayList<>();
            var maxRadiusValue = targetRadius.getAsDouble();
            for (Map.Entry<ImageWrapper, Double> entry : fittings.entrySet()) {
                var img = entry.getKey();
                var radius = entry.getValue();
                if (radius.equals(maxRadiusValue)) {
                    result.add(img);
                } else {
                    var scale = maxRadiusValue / radius;
                    result.add(doRescale(img, (int) Math.round(img.width() * scale), (int) Math.round(img.height() * scale)));
                }
            }
            var minWidth = result.stream().mapToInt(ImageWrapper::width).min().orElse(0);
            var minHeight = result.stream().mapToInt(ImageWrapper::height).min().orElse(0);
            //noinspection unchecked
            result = (List<ImageWrapper>) crop.cropToRect(Map.of("img", result, "width", minWidth, "height", minHeight));
            return result;
        }
        throw new IllegalArgumentException("Unable to determine max radius of images");
    }

    // At this stage, we're assuming that the ellipse represents a circle
    private double radiusOf(Ellipse e) {
        var semiAxis = e.semiAxis();
        return (semiAxis.a() + semiAxis.b()) / 2d;
    }

    private ImageWrapper doRescale(ImageWrapper img, int width, int height) {
        var metadata = fixMetadata(img, width, height);
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            return ImageWrapper32.fromImage(
                    imageMath.rescale(mono.asImage(),
                            width,
                            height
                    ),
                    metadata);
        } else if (img instanceof RGBImage rgb) {
            var r = new Image(rgb.width(), rgb.height(), rgb.r());
            var g = new Image(rgb.width(), rgb.height(), rgb.g());
            var b = new Image(rgb.width(), rgb.height(), rgb.b());
            return new RGBImage(
                    width,
                    height,
                    imageMath.rescale(r, width, height).data(),
                    imageMath.rescale(g, width, height).data(),
                    imageMath.rescale(b, width, height).data(),
                    metadata
            );
        }
        return null;
    }

    private static Map<Class<?>, Object> fixMetadata(ImageWrapper image, double width, double height) {
        var metadata = new LinkedHashMap<>(image.metadata());
        image.findMetadata(Ellipse.class).ifPresent(ellipse -> {
            double sx = width / image.width();
            double sy = height/ image.height();
            var rescaled = ellipse.rescale(sx, sy);
            metadata.put(Ellipse.class, rescaled);
        });
        image.findMetadata(Redshifts.class).ifPresent(redshifts -> {
            double sx = image.width() / width;
            double sy = image.height() / height;
            metadata.put(Redshifts.class, new Redshifts(redshifts.redshifts().stream()
                    .map(rs -> new RedshiftArea(rs.id(), rs.pixelShift(), rs.relPixelShift(), rs.kmPerSec(), (int) (rs.x1() / sx), (int) (rs.y1() / sy), (int) (rs.x2() / sx), (int) (rs.y2() / sy), (int) (rs.maxX() / sx), (int) (rs.maxY() / sy)))
                    .toList()));
        });
        image.findMetadata(ActiveRegions.class).ifPresent(activeRegions -> {
            double sx = image.width() / width;
            double sy = image.height() / height;
            metadata.put(ActiveRegions.class, activeRegions.transform(p -> {
                var x = (p.x() / sx);
                var y = (p.y() / sy);
                return new Point2D(x, y);
            }));
        });
        return metadata;
    }
}
