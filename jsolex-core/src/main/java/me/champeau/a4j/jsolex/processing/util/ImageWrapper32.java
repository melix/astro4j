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

import me.champeau.a4j.math.image.Image;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A monochrome image with 32-bit floating point pixels.
 */
public final class ImageWrapper32 implements ImageWrapper {
    private final ImagePlane pixels;
    private final Map<Class<?>, Object> metadata;

    public ImageWrapper32(ImagePlane pixels, Map<Class<?>, Object> metadata) {
        this.pixels = pixels;
        this.metadata = metadata;
    }

    /**
     * Creates an image holding a copy of the given pixels.
     *
     * @param width the width
     * @param height the height
     * @param data the pixels, indexed by row then column
     * @param metadata the metadata
     */
    public ImageWrapper32(int width, int height, float[][] data, Map<Class<?>, Object> metadata) {
        this(ImagePlane.of(width, height, data), metadata);
    }

    /**
     * Returns a new image whose dimensions are 0x0 with mutable metadata.
     * This can typically be used in image math scripts, when no real image is available.
     * @return a new empty image
     */
    public static ImageWrapper32 createEmpty() {
        return new ImageWrapper32(ImagePlane.allocate(0, 0), MutableMap.of());
    }

    @Override
    public int width() {
        return pixels.width();
    }

    @Override
    public int height() {
        return pixels.height();
    }

    public ImagePlane pixels() {
        return pixels;
    }

    @Override
    public Map<Class<?>, Object> metadata() {
        return metadata;
    }

    /**
     * Returns a copy of this image for use by the math library.
     *
     * @return a new image
     */
    public Image asImage() {
        return new Image(width(), height(), pixels.toArray());
    }

    public static ImageWrapper32 fromImage(Image image) {
        return fromImage(image, MutableMap.of());
    }

    public static ImageWrapper32 fromImage(Image image, Map<Class<?>, Object> metadata) {
        return new ImageWrapper32(image.width(), image.height(), image.data(), metadata);
    }

    @Override
    public ImageWrapper32 copy() {
        return new ImageWrapper32(pixels.copy(), new LinkedHashMap<>(metadata));
    }
}
