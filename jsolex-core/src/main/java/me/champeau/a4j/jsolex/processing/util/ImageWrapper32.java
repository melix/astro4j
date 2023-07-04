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

/**
 * A wrapper for images which are using 32-bit floats.
 * @param width the width of the image
 * @param height the height of the image
 * @param data the image data
 */
public record ImageWrapper32(
        int width,
        int height,
        float[] data
) implements ImageWrapper {
    public Image asImage() {
        return new Image(width, height, data);
    }

    public static ImageWrapper32 fromImage(Image image) {
        return new ImageWrapper32(image.width(), image.height(), image.data());
    }

    public ImageWrapper32 copy() {
        float[] copy = new float[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        return new ImageWrapper32(width, height, copy);
    }
}
