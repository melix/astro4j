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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
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

    public Object rotateLeft(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_left takes 1 arguments (image(s))", 1, 1);
        return doRotate("rotate_left", arguments, -Math.PI / 2);
    }

    public Object rotateRight(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_right takes 1 arguments (image(s))", 1, 1);
        return doRotate("rotate_right", arguments, Math.PI / 2);
    }

    public Object rotateDegrees(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_deg takes 2 to 4 arguments (image(s), angle, [blackPoint], [resize])", 1, 4);
        var angle = Math.toRadians(doubleArg(arguments, 1));
        return doRotate("rotate_deg", arguments, angle);
    }

    public Object rotateRadians(List<Object> arguments) {
        assertExpectedArgCount(arguments, "rotate_rad takes 2 to 4 arguments (image(s), angle, [blackPoint], [resize])", 1, 4);
        var angle = doubleArg(arguments, 1);
        return doRotate("rotate_rad", arguments, angle);
    }

    public Object hflip(List<Object> arguments) {
        assertExpectedArgCount(arguments, "hflip takes 1 arguments (image(s))", 1, 1);
        return applyUnary(arguments, "hflip", (width, height, data) -> {
            var result = new float[height][width];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    result[y][x] = data[y][width - x - 1];
                }
            }
            System.arraycopy(result, 0, data, 0, result.length);
        });
    }

    public Object vflip(List<Object> arguments) {
        assertExpectedArgCount(arguments, "vflip takes 1 arguments (image(s))", 1, 1);
        return applyUnary(arguments, "vflip", (width, height, data) -> {
            var result = new float[width * height];
            for (var y = 0; y < height; y++) {
                System.arraycopy(data, (height - y - 1) * width, result, y * width, width);
            }
            System.arraycopy(result, 0, data, 0, result.length);
        });
    }

    private Image arbitraryRotation(List<Object> arguments, Image image, double angle) {
        var blackpoint = getArgument(Number.class, arguments, 2)
            .map(Number::floatValue)
            .or(() -> getFromContext(ImageStats.class).map(ImageStats::blackpoint))
            .orElse(0f);
        var resize = getArgument(Number.class, arguments, 3)
            .map(Number::intValue)
            .map(i -> i == 1)
            .orElse(false);
        return imageMath.rotate(image, angle, blackpoint, resize);
    }

    private Object doRotate(String functionName, List<Object> arguments, double angle) {
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(functionName, arguments, this::rotateRadians);
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
            var sx = origWidth / 2;
            var sy = origHeight / 2;
            var rotated = ellipse.rotate(-angle, new Point2D(sx, sy));
            sx = (newWidth - origWidth) / 2;
            sy = (newHeight - origHeight) / 2;
            if (sx != 0 || sy != 0) {
                rotated = rotated.translate(sx, sy);
            }
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
        wrapper.findMetadata(ReferenceCoords.class).ifPresent(coords -> coords.addRotation(angle));
        return metadata;
    }
}
