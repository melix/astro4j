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
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SignedDiff extends AbstractFunctionImpl {
    public SignedDiff(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object signedDiff(Map<String, Object> arguments) {
        BuiltinFunction.SIGNED_DIFF.validateArgs(arguments);
        var a = unwrap(arguments.get("a"));
        var b = unwrap(arguments.get("b"));
        if (a instanceof ImageWrapper32 imgA && b instanceof ImageWrapper32 imgB) {
            return diffMono(imgA, imgB);
        }
        if (a instanceof RGBImage rgbA && b instanceof RGBImage rgbB) {
            return diffRGB(rgbA, rgbB);
        }
        if (a instanceof ImageWrapper32 imgA && b instanceof Number scalar) {
            return diffMonoScalar(imgA, scalar.floatValue());
        }
        if (a instanceof Number scalar && b instanceof ImageWrapper32 imgB) {
            return diffScalarMono(scalar.floatValue(), imgB);
        }
        if (a instanceof RGBImage rgbA && b instanceof Number scalar) {
            return diffRGBScalar(rgbA, scalar.floatValue());
        }
        if (a instanceof Number scalar && b instanceof RGBImage rgbB) {
            return diffScalarRGB(scalar.floatValue(), rgbB);
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() - nb.doubleValue();
        }
        if ((a instanceof ImageWrapper32 && b instanceof RGBImage) || (a instanceof RGBImage && b instanceof ImageWrapper32)) {
            throw new IllegalArgumentException("Cannot compute signed_diff between mono and RGB images");
        }
        throw new IllegalArgumentException("Unsupported operand types for signed_diff");
    }

    private static Object unwrap(Object o) {
        if (o instanceof FileBackedImage fbi) {
            return fbi.unwrapToMemory();
        }
        return o;
    }

    private static ImageWrapper32 diffMono(ImageWrapper32 a, ImageWrapper32 b) {
        var width = a.width();
        var height = a.height();
        if (width != b.width() || height != b.height()) {
            throw new IllegalArgumentException("Both images must have the same dimensions");
        }
        var da = a.data();
        var db = b.data();
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = da[y][x] - db[y][x];
            }
        }
        var metadata = MetadataMerger.merge(List.of(a, b));
        return new ImageWrapper32(width, height, result, metadata);
    }

    private static RGBImage diffRGB(RGBImage a, RGBImage b) {
        var width = a.width();
        var height = a.height();
        if (width != b.width() || height != b.height()) {
            throw new IllegalArgumentException("Both images must have the same dimensions");
        }
        var rr = diffChannel(a.r(), b.r(), width, height);
        var gg = diffChannel(a.g(), b.g(), width, height);
        var bb = diffChannel(a.b(), b.b(), width, height);
        var metadata = MetadataMerger.merge(List.of(a, b));
        return new RGBImage(width, height, rr, gg, bb, metadata);
    }

    private static float[][] diffChannel(float[][] ca, float[][] cb, int width, int height) {
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = ca[y][x] - cb[y][x];
            }
        }
        return result;
    }

    private static ImageWrapper32 diffMonoScalar(ImageWrapper32 img, float scalar) {
        var width = img.width();
        var height = img.height();
        var data = img.data();
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = data[y][x] - scalar;
            }
        }
        return new ImageWrapper32(width, height, result, new LinkedHashMap<>(img.metadata()));
    }

    private static ImageWrapper32 diffScalarMono(float scalar, ImageWrapper32 img) {
        var width = img.width();
        var height = img.height();
        var data = img.data();
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = scalar - data[y][x];
            }
        }
        return new ImageWrapper32(width, height, result, new LinkedHashMap<>(img.metadata()));
    }

    private static RGBImage diffRGBScalar(RGBImage img, float scalar) {
        var width = img.width();
        var height = img.height();
        var rr = subtractScalarFromChannel(img.r(), scalar, width, height);
        var gg = subtractScalarFromChannel(img.g(), scalar, width, height);
        var bb = subtractScalarFromChannel(img.b(), scalar, width, height);
        return new RGBImage(width, height, rr, gg, bb, new LinkedHashMap<>(img.metadata()));
    }

    private static RGBImage diffScalarRGB(float scalar, RGBImage img) {
        var width = img.width();
        var height = img.height();
        var rr = subtractChannelFromScalar(scalar, img.r(), width, height);
        var gg = subtractChannelFromScalar(scalar, img.g(), width, height);
        var bb = subtractChannelFromScalar(scalar, img.b(), width, height);
        return new RGBImage(width, height, rr, gg, bb, new LinkedHashMap<>(img.metadata()));
    }

    private static float[][] subtractScalarFromChannel(float[][] channel, float scalar, int width, int height) {
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = channel[y][x] - scalar;
            }
        }
        return result;
    }

    private static float[][] subtractChannelFromScalar(float scalar, float[][] channel, int width, int height) {
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = scalar - channel[y][x];
            }
        }
        return result;
    }
}
