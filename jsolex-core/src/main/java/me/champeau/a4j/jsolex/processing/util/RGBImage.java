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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.image.ImagePlane;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A color image made of three independent channels.
 */
public final class RGBImage implements ImageWrapper {
    private final ImagePlane red;
    private final ImagePlane green;
    private final ImagePlane blue;
    private final Map<Class<?>, Object> metadata;

    public RGBImage(ImagePlane red, ImagePlane green, ImagePlane blue, Map<Class<?>, Object> metadata) {
        if (red.height() != green.height() || red.height() != blue.height()) {
            throw new IllegalArgumentException("Inconsistent image height");
        }
        if (red.width() != green.width() || red.width() != blue.width()) {
            throw new IllegalArgumentException("Inconsistent image width");
        }
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.metadata = metadata;
    }

    /**
     * Creates an image holding a copy of the given channels.
     *
     * @param width the width
     * @param height the height
     * @param r the red channel, indexed by row then column
     * @param g the green channel, indexed by row then column
     * @param b the blue channel, indexed by row then column
     * @param metadata the metadata
     */
    public RGBImage(int width, int height, float[][] r, float[][] g, float[][] b, Map<Class<?>, Object> metadata) {
        this(ImagePlane.of(width, height, r), ImagePlane.of(width, height, g), ImagePlane.of(width, height, b), metadata);
    }

    @Override
    public int width() {
        return red.width();
    }

    @Override
    public int height() {
        return red.height();
    }

    public ImagePlane red() {
        return red;
    }

    public ImagePlane green() {
        return green;
    }

    public ImagePlane blue() {
        return blue;
    }

    @Override
    public Map<Class<?>, Object> metadata() {
        return metadata;
    }

    @Override
    public RGBImage copy() {
        return new RGBImage(red.copy(), green.copy(), blue.copy(), new LinkedHashMap<>(metadata));
    }

    public static RGBImage toRGB(ImageWrapper32 mono) {
        var pixels = mono.pixels();
        return new RGBImage(pixels.copy(), pixels.copy(), pixels.copy(), new LinkedHashMap<>(mono.metadata()));
    }

    public static RGBImage fromMono(ImageWrapper32 mono, Function<ImageWrapper32, float[][][]> converter) {
        return fromMono(mono, converter, new LinkedHashMap<>(mono.metadata()));
    }

    public static RGBImage fromMono(ImageWrapper32 mono, Function<ImageWrapper32, float[][][]> converter, Map<Class<?>, Object> metadata) {
        var rgb = converter.apply(mono);
        return new RGBImage(mono.width(), mono.height(), rgb[0], rgb[1], rgb[2], metadata);
    }

    public ImageWrapper32 toMono() {
        int width = width();
        int height = height();
        var mono = ImagePlane.allocate(width, height);
        var r = new float[width];
        var g = new float[width];
        var b = new float[width];
        var luminance = new float[width];
        for (int y = 0; y < height; y++) {
            red.getRow(y, r);
            green.getRow(y, g);
            blue.getRow(y, b);
            for (int x = 0; x < width; x++) {
                luminance[x] = 0.299f * r[x] + 0.587f * g[x] + 0.114f * b[x];
            }
            mono.setRow(y, luminance);
        }
        return new ImageWrapper32(mono, new LinkedHashMap<>(metadata));
    }
}
