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
package me.champeau.a4j.ser.bayer;

import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;

/**
 * Base class for demosaicing algorithms.
 */
public abstract class AbstractDemosaicingStrategy implements DemosaicingStrategy, BayerMatrixSupport {

    protected final byte[] rgb;
    protected final ColorMode colorMode;
    protected final ImageGeometry geometry;
    protected final int width;
    protected final int height;
    protected final int lineFeed;

    protected AbstractDemosaicingStrategy(byte[] rgb, ColorMode colorMode, ImageGeometry geometry) {
        this.rgb = rgb;
        this.colorMode = colorMode;
        this.geometry = geometry;
        this.width = geometry.width();
        this.height = geometry.height();
        this.lineFeed = PIXEL * width;
    }

    protected static int unsigned(byte b) {
        return b & 0xFF;
    }

    protected static byte avg(byte a, byte b) {
        return (byte) ((unsigned(a) + unsigned(b)) >> 1);
    }

    protected static byte avg(byte a, byte b, byte c, byte d) {
        return (byte) ((unsigned(a) + unsigned(b) + unsigned(c) + unsigned(d)) >> 2);
    }

    @Override
    public byte[] getRgb() {
        return rgb;
    }

    @Override
    public ColorMode getColorMode() {
        return colorMode;
    }

    @Override
    public ImageGeometry getGeometry() {
        return geometry;
    }

    protected int indexOf(int x, int y) {
        return PIXEL * (x + y * width);
    }

    /**
     * Given a 2*2 bayer pixel, determines which color is represented at
     * the provided coordinates.
     *
     * @return which color is represented
     */
    protected int colorKindAt(int x, int y) {
        int col = x % 2;
        int row = y % 2;
        if (col == 0) {
            if (row == 0) {
                return switch (colorMode) {
                    case BAYER_BGGR -> BLUE;
                    case BAYER_GBRG -> GREEN;
                    case BAYER_GRBG -> GREEN;
                    case BAYER_RGGB -> RED;
                    default -> throw new IllegalStateException("Unsupported color mode");
                };
            } else {
                return switch (colorMode) {
                    case BAYER_BGGR -> GREEN;
                    case BAYER_GBRG -> RED;
                    case BAYER_GRBG -> BLUE;
                    case BAYER_RGGB -> GREEN;
                    default -> throw new IllegalStateException("Unsupported color mode");
                };
            }
        } else {
            if (row == 0) {
                return switch (colorMode) {
                    case BAYER_BGGR -> GREEN;
                    case BAYER_GBRG -> BLUE;
                    case BAYER_GRBG -> RED;
                    case BAYER_RGGB -> GREEN;
                    default -> throw new IllegalStateException("Unsupported color mode");
                };
            } else {
                return switch (colorMode) {
                    case BAYER_BGGR -> RED;
                    case BAYER_GBRG -> GREEN;
                    case BAYER_GRBG -> GREEN;
                    case BAYER_RGGB -> BLUE;
                    default -> throw new IllegalStateException("Unsupported color mode");
                };
            }
        }
    }
}
