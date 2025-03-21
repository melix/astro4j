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
 * A wrapper for images which are using 32-bit floats.
 * @param width the width of the image
 * @param height the height of the image
 * @param data the image data
 */
public record ImageWrapper32(
        int width,
        int height,
        float[][] data,
        Map<Class<?>, Object> metadata
) implements ImageWrapper {
    public ImageWrapper32 {
        if (data.length != height) {
            throw new IllegalArgumentException("Inconsistent image height");
        }
        if (data.length>0 && data[0].length != width) {
            throw new IllegalArgumentException("Inconsistent image width");
        }
    }

    /**
     * Returns a new image whose dimensions are 0x0 with mutable metadata.
     * This can typically be used in image math scripts, when no real image is available.
     * @return a new empty image
     */
    public static ImageWrapper32 createEmpty() {
        return new ImageWrapper32(0, 0, new float[0][0], MutableMap.of());
    }

    public Image asImage() {
        return new Image(width, height, data);
    }

    public static ImageWrapper32 fromImage(Image image) {
        return new ImageWrapper32(image.width(), image.height(), image.data(), MutableMap.of());
    }

    public static ImageWrapper32 fromImage(Image image, Map<Class<?>, Object> metadata) {
        return new ImageWrapper32(image.width(), image.height(), image.data(), metadata);
    }

    public ImageWrapper32 copy() {
        float[][] copy = ImageWrapper.copyData(data);
        return new ImageWrapper32(width, height, copy, new LinkedHashMap<>(metadata));
    }
}
