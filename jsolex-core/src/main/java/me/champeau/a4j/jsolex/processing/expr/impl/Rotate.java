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
import me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombs;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Rotate extends AbstractFunctionImpl {
    private final ImageMath imageMath = ImageMath.newInstance();

    public Rotate(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object rotateLeft(Map<String, Object> arguments) {
        BuiltinFunction.ROTATE_LEFT.validateArgs(arguments);
        return doRotate("rotate_left", arguments, -Math.PI / 2);
    }

    public Object rotateRight(Map<String, Object> arguments) {
        BuiltinFunction.ROTATE_RIGHT.validateArgs(arguments);
        return doRotate("rotate_right", arguments, Math.PI / 2);
    }

    public Object rotateDegrees(Map<String, Object> arguments) {
        BuiltinFunction.ROTATE_DEG.validateArgs(arguments);
        var angle = Math.toRadians(doubleArg(arguments, "angle", 0));
        return doRotate("rotate_deg", arguments, angle);
    }

    public Object rotateRadians(Map<String, Object> arguments) {
        BuiltinFunction.ROTATE_RAD.validateArgs(arguments);
        var angle = doubleArg(arguments, "angle", 0);
        return doRotate("rotate_rad", arguments, angle);
    }

    public Object hflip(Map<String, Object> arguments) {
        BuiltinFunction.HFLIP.validateArgs(arguments);
        return applyUnary(arguments, "hflip", "img", new MonoImageTransformer() {
            @Override
            public void transform(int width, int height, float[][] data) {
                var result = new float[height][width];
                for (var y = 0; y < height; y++) {
                    for (var x = 0; x < width; x++) {
                        result[y][x] = data[y][width - x - 1];
                    }
                }
                System.arraycopy(result, 0, data, 0, result.length);
            }

            @Override
            public void postProcess(ImageWrapper image) {
                fixMetadataForFlipping(image, true);
            }
        });
    }

    public Object vflip(Map<String, Object> arguments) {
        BuiltinFunction.VFLIP.validateArgs(arguments);
        return applyUnary(arguments, "vflip", "img", new MonoImageTransformer() {
            @Override
            public void transform(int width, int height, float[][] data) {
                var result = new float[height][width];
                for (var y = 0; y < height; y++) {
                    System.arraycopy(data[y], 0, result[height - y - 1], 0, width);
                }
                System.arraycopy(result, 0, data, 0, result.length);
            }

            @Override
            public void postProcess(ImageWrapper image) {
                fixMetadataForFlipping(image, false);
            }
        });
    }

    private Image arbitraryRotation(Map<String, Object> arguments, Image image, double angle) {
        var blackpoint = getArgument(Number.class, arguments, "bp")
                .map(Number::floatValue)
                .or(() -> getFromContext(ImageStats.class).map(ImageStats::blackpoint))
                .orElse(0f);
        var resize = getArgument(Number.class, arguments, "resize")
                .map(Number::intValue)
                .map(i -> i == 1)
                .orElse(false);
        return imageMath.rotate(image, angle, blackpoint, resize);
    }

    private Object doRotate(String functionName, Map<String, Object> arguments, double angle) {
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList(functionName, "img", arguments, this::rotateRadians);
        }
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 mono) {
            var trn = arbitraryRotation(arguments, mono.asImage(), angle);
            return ImageWrapper32.fromImage(trn, fixMetadata(mono, angle, trn.width(), trn.height()));
        } else if (arg instanceof RGBImage rgb) {
            var height = rgb.height();
            var width = rgb.width();
            var r = arbitraryRotation(arguments, new Image(width, height, rgb.r()), angle);
            var g = arbitraryRotation(arguments, new Image(width, height, rgb.g()), angle);
            var b = arbitraryRotation(arguments, new Image(width, height, rgb.b()), angle);
            var md = fixMetadata(rgb, angle, r.width(), r.height());
            return new RGBImage(r.width(), r.height(), r.data(), g.data(), b.data(), md);
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    public static Map<Class<?>, Object> fixMetadata(ImageWrapper wrapper, double angle, int newWidth, int newHeight) {
        var metadata = new LinkedHashMap<>(wrapper.metadata());
        var origWidth = wrapper.width();
        var origHeight = wrapper.height();
        wrapper.findMetadata(Ellipse.class).ifPresent(ellipse -> {
            var rotated = ellipse.rotate(angle, origWidth, origHeight, newWidth, newHeight);
            metadata.put(Ellipse.class, rotated);
        });
        wrapper.findMetadata(Redshifts.class).ifPresent(redshifts -> {
            var rotated = redshifts.redshifts().stream()
                    .map(rs -> {
                        var x1 = rs.x1();
                        var y1 = rs.y1();
                        var x2 = rs.x2();
                        var y2 = rs.y2();
                        var maxX = rs.maxX();
                        var maxY = rs.maxY();
                        var cx = origWidth / 2;
                        var cy = origHeight / 2;
                        var sx = cx + (newWidth - origWidth) / 2;
                        var sy = cy + (newHeight - origHeight) / 2;
                        // apply rotation and scale to each coordinate
                        var cosAlpha = Math.cos(angle);
                        var sinAlpha = Math.sin(angle);
                        var x1p = (int) (sx + Math.round((x1 - cx) * cosAlpha - (y1 - cy) * sinAlpha));
                        var y1p = (int) (sy + Math.round((x1 - cx) * sinAlpha + (y1 - cy) * cosAlpha));
                        var x2p = (int) (sx + Math.round((x2 - cx) * cosAlpha - (y2 - cy) * sinAlpha));
                        var y2p = (int) (sy + Math.round((x2 - cx) * sinAlpha + (y2 - cy) * cosAlpha));
                        var maxXP = (int) (sx + Math.round((maxX - cx) * cosAlpha - (maxY - cy) * sinAlpha));
                        var maxYP = (int) (sy + Math.round((maxX - cx) * sinAlpha + (maxY - cy) * cosAlpha));
                        return new RedshiftArea(rs.id(), rs.pixelShift(), rs.relPixelShift(), rs.kmPerSec(), x1p, y1p, x2p, y2p, maxXP, maxYP);
                    })
                    .toList();
            metadata.put(Redshifts.class, new Redshifts(rotated));
        });
        wrapper.findMetadata(ActiveRegions.class).ifPresent(activeRegions -> {
            var rotated = activeRegions.transform(p -> {
                var cx = origWidth / 2;
                var cy = origHeight / 2;
                var sx = cx + (newWidth - origWidth) / 2;
                var sy = cy + (newHeight - origHeight) / 2;
                var cosAlpha = Math.cos(angle);
                var sinAlpha = Math.sin(angle);
                var x = p.x();
                var y = p.y();
                var xp = (int) (sx + Math.round((x - cx) * cosAlpha - (y - cy) * sinAlpha));
                var yp = (int) (sy + Math.round((x - cx) * sinAlpha + (y - cy) * cosAlpha));
                return new Point2D(xp, yp);
            });
            metadata.put(ActiveRegions.class, rotated);
        });
        wrapper.findMetadata(EllermanBombs.class).ifPresent(bombs -> {
            var rotated = bombs.transform(p -> {
                var cx = origWidth / 2;
                var cy = origHeight / 2;
                var sx = cx + (newWidth - origWidth) / 2;
                var sy = cy + (newHeight - origHeight) / 2;
                var cosAlpha = Math.cos(angle);
                var sinAlpha = Math.sin(angle);
                var x = p.x();
                var y = p.y();
                var xp = (int) (sx + Math.round((x - cx) * cosAlpha - (y - cy) * sinAlpha));
                var yp = (int) (sy + Math.round((x - cx) * sinAlpha + (y - cy) * cosAlpha));
                return new Point2D(xp, yp);
            });
            metadata.put(EllermanBombs.class, rotated);
        });
        wrapper.findMetadata(ReferenceCoords.class).ifPresent(coords -> coords.addRotation(angle));
        return metadata;
    }

    public static void fixMetadataForFlipping(ImageWrapper wrapper, boolean hflip) {
        var metadata = wrapper.metadata();
        var width = wrapper.width();
        var height = wrapper.height();
        wrapper.findMetadata(Ellipse.class).ifPresent(e -> {
            var flipped = hflip ? e.hflip(width) : e.vflip(height);
            metadata.put(Ellipse.class, flipped);
        });
        wrapper.findMetadata(Redshifts.class).ifPresent(rs -> {
            var areas = rs.redshifts().stream().map(a -> {
                if (hflip) {
                    return new RedshiftArea(
                            a.id(), a.pixelShift(), a.relPixelShift(), a.kmPerSec(),
                            width - a.x2(), a.y1(), width - a.x1(), a.y2(),
                            width - a.maxX(), a.maxY()
                    );
                } else {
                    return new RedshiftArea(
                            a.id(), a.pixelShift(), a.relPixelShift(), a.kmPerSec(),
                            a.x1(), height - a.y2(), a.x2(), height - a.y1(),
                            a.maxX(), height - a.maxY()
                    );
                }
            }).toList();
            metadata.put(Redshifts.class, new Redshifts(areas));
        });
        wrapper.findMetadata(ActiveRegions.class).ifPresent(ar -> {
            var flipped = ar.transform(p ->
                    new Point2D(
                            hflip ? width - p.x() : p.x(),
                            hflip ? p.y() : height - p.y()
                    )
            );
            metadata.put(ActiveRegions.class, flipped);
        });
        wrapper.findMetadata(EllermanBombs.class).ifPresent(ar -> {
            var flipped = ar.transform(p ->
                    new Point2D(
                            hflip ? width - p.x() : p.x(),
                            hflip ? p.y() : height - p.y()
                    )
            );
            metadata.put(EllermanBombs.class, flipped);
        });
        wrapper.findMetadata(ReferenceCoords.class).ifPresent(rc -> {
            if (hflip) {
                rc.addVFlip(wrapper.height());
            } else {
                rc.addHFlip(wrapper.width());
            }
            metadata.put(ReferenceCoords.class, rc);
        });
    }

}
