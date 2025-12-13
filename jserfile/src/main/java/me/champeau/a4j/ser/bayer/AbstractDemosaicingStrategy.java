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
package me.champeau.a4j.ser.bayer;

import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;

/** Base class for demosaicing algorithms. */
public abstract class AbstractDemosaicingStrategy implements DemosaicingStrategy, BayerMatrixSupport {

    /** Constructs an abstract demosaicing strategy. */
    protected AbstractDemosaicingStrategy() {
    }

    /** The color mode. */
    protected ColorMode colorMode;
    /** The image geometry. */
    protected ImageGeometry geometry;
    /** The image width. */
    protected int width;
    /** The image height. */
    protected int height;
    /** The line feed size. */
    protected int lineFeed;

    /**
     * Sets up the demosaicing strategy with the given parameters.
     *
     * @param colorMode the color mode
     * @param geometry the image geometry
     */
    protected void setup(ColorMode colorMode, ImageGeometry geometry) {
        this.colorMode = colorMode;
        this.geometry = geometry;
        this.width = geometry.width();
        this.height = geometry.height();
        this.lineFeed = PIXEL * width;
    }

    /**
     * Converts a signed short to an unsigned value.
     *
     * @param b the signed short
     * @return the unsigned value
     */
    protected static int unsigned(short b) {
        return b & 0xFFFF;
    }

    /**
     * Computes the average of two values.
     *
     * @param a the first value
     * @param b the second value
     * @return the average
     */
    protected static short avg(short a, short b) {
        return (short) ((unsigned(a) + unsigned(b)) >> 1);
    }

    /**
     * Computes the average of four values.
     *
     * @param a the first value
     * @param b the second value
     * @param c the third value
     * @param d the fourth value
     * @return the average
     */
    protected static short avg(short a, short b, short c, short d) {
        return (short) ((unsigned(a) + unsigned(b) + unsigned(c) + unsigned(d)) >> 2);
    }

    /**
     * Computes the index in the image buffer for the given coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return the index in the buffer
     */
    protected int indexOf(int x, int y) {
        return PIXEL * (x + y * width);
    }

    /**
     * Given a 2*2 bayer pixel, determines which color is represented at
     * the provided coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
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
