/*
 * Copyright 2023 the original author or authors.
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
package me.champeau.a4j.ser;

import java.nio.ByteOrder;

/**
 * Stores information about the image format
 * and geometry.
 * @param colorMode the color mode of images
 * @param width the width of the images
 * @param height the height of the images
 * @param pixelDepthPerPlane the number of pixels per plane
 * @param imageEndian the byte order of binary data of each frame
 */
public record ImageGeometry(
        ColorMode colorMode,
        int width,
        int height,
        int pixelDepthPerPlane,
        ByteOrder imageEndian
) {
    /**
     * Computes the number of bytes which are required to represent
     * a single pixel in the image.
     * @return the number of bytes per pixel
     */
    public int getBytesPerPixel() {
        return switch (pixelDepthPerPlane) {
            case 1, 2, 3, 4, 5, 6, 7, 8 -> colorMode.getNumberOfPlanes();
            case 9, 10, 11, 12, 13, 14, 15, 16 -> 2 * colorMode.getNumberOfPlanes();
            default -> throw new IllegalArgumentException("Invalid pixel depth: " + pixelDepthPerPlane);
        };
    }

    /**
     * Returns the total number of bytes per frames.
     * @return the number of bytes per frame
     */
    public int getBytesPerFrame() {
        return getBytesPerPixel() * width * height;
    }
}
