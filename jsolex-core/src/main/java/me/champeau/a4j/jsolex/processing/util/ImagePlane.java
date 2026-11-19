/*
 * Copyright 2026 the original author or authors.
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

import me.champeau.a4j.math.RowStrips;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * The pixels of one channel of an image, stored row by row outside of the
 * Java heap. A plane owns its pixels: arrays given to it or obtained from it
 * are always copies.
 */
public final class ImagePlane {
    private final int width;
    private final int height;
    private final MemorySegment segment;

    private ImagePlane(int width, int height, MemorySegment segment) {
        this.width = width;
        this.height = height;
        this.segment = segment;
    }

    /**
     * Creates a plane of the given dimensions, filled with zeros.
     *
     * @param width the width
     * @param height the height
     * @return a new plane
     */
    public static ImagePlane allocate(int width, int height) {
        return new ImagePlane(width, height, ImageMemory.allocate((long) width * height * Float.BYTES));
    }

    /**
     * Creates a plane of the given dimensions holding a copy of the given pixels.
     *
     * @param width the width
     * @param height the height
     * @param pixels the pixels, indexed by row then column
     * @return a new plane
     */
    public static ImagePlane of(int width, int height, float[][] pixels) {
        var plane = allocate(width, height);
        plane.copyFrom(pixels);
        return plane;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float get(int x, int y) {
        return segment.getAtIndex(JAVA_FLOAT, index(x, y));
    }

    public void set(int x, int y, float value) {
        segment.setAtIndex(JAVA_FLOAT, index(x, y), value);
    }

    private long index(int x, int y) {
        return (long) y * width + Objects.checkIndex(x, width);
    }

    /**
     * Copies a row of this plane into the given array.
     *
     * @param y the row
     * @param target the array which receives the pixels of the row
     */
    public void getRow(int y, float[] target) {
        getRow(y, 0, target, 0, width);
    }

    /**
     * Copies a part of a row of this plane into the given array.
     *
     * @param y the row
     * @param x the column of the first pixel to copy
     * @param target the array which receives the pixels
     * @param offset the index of the first pixel in the target array
     * @param length the number of pixels to copy
     */
    public void getRow(int y, int x, float[] target, int offset, int length) {
        MemorySegment.copy(segment, JAVA_FLOAT, byteOffset(x, y, length), target, offset, length);
    }

    /**
     * Replaces a row of this plane with the given pixels.
     *
     * @param y the row
     * @param source the new pixels of the row
     */
    public void setRow(int y, float[] source) {
        setRow(y, 0, source, 0, width);
    }

    /**
     * Replaces a part of a row of this plane with the given pixels.
     *
     * @param y the row
     * @param x the column of the first pixel to replace
     * @param source the array which holds the new pixels
     * @param offset the index of the first pixel in the source array
     * @param length the number of pixels to replace
     */
    public void setRow(int y, int x, float[] source, int offset, int length) {
        MemorySegment.copy(source, offset, segment, JAVA_FLOAT, byteOffset(x, y, length), length);
    }

    private long byteOffset(int x, int y, int length) {
        Objects.checkFromIndexSize(x, length, width);
        return ((long) y * width + x) * Float.BYTES;
    }

    /**
     * An operation on a row of pixels.
     */
    @FunctionalInterface
    public interface RowOperator {
        void apply(int y, float[] row);
    }

    /**
     * Calls the operator with a copy of each row of this plane, then replaces
     * the row with what the operator left in the array. Rows are processed in
     * parallel, so the operator must be thread safe.
     *
     * @param operator the operator, which modifies the row it is given
     */
    public void updateRows(RowOperator operator) {
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var row = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                getRow(y, row);
                operator.apply(y, row);
                setRow(y, row);
            }
        });
    }

    /**
     * Creates a plane of the same dimensions, whose rows are what the operator
     * leaves in the array after being called with a copy of the corresponding
     * row of this plane. Rows are processed in parallel, so the operator must
     * be thread safe.
     *
     * @param operator the operator, which modifies the row it is given
     * @return a new plane
     */
    public ImagePlane mapRows(RowOperator operator) {
        var result = allocate(width, height);
        RowStrips.forEach(height, (yStart, yEnd) -> {
            var row = new float[width];
            for (int y = yStart; y < yEnd; y++) {
                getRow(y, row);
                operator.apply(y, row);
                result.setRow(y, row);
            }
        });
        return result;
    }

    /**
     * Returns the lowest pixel value of this plane.
     *
     * @return the minimum, or {@link Double#MAX_VALUE} if the plane is empty
     */
    public double min() {
        return RowStrips.min(height, (yStart, yEnd) -> {
            var row = new float[width];
            double min = Double.MAX_VALUE;
            for (int y = yStart; y < yEnd; y++) {
                getRow(y, row);
                for (float v : row) {
                    if (v < min) {
                        min = v;
                    }
                }
            }
            return min;
        });
    }

    /**
     * Returns the highest pixel value of this plane.
     *
     * @return the maximum, or {@code -Double.MAX_VALUE} if the plane is empty
     */
    public double max() {
        return RowStrips.max(height, (yStart, yEnd) -> {
            var row = new float[width];
            double max = -Double.MAX_VALUE;
            for (int y = yStart; y < yEnd; y++) {
                getRow(y, row);
                for (float v : row) {
                    if (v > max) {
                        max = v;
                    }
                }
            }
            return max;
        });
    }

    /**
     * Returns a copy of the pixels of this plane, indexed by row then column.
     *
     * @return a new array
     */
    public float[][] toArray() {
        var copy = new float[height][width];
        for (int y = 0; y < height; y++) {
            getRow(y, copy[y]);
        }
        return copy;
    }

    /**
     * Replaces the pixels of this plane with a copy of the given ones.
     *
     * @param pixels the new pixels, indexed by row then column
     */
    public void copyFrom(float[][] pixels) {
        if (pixels.length != height || (height > 0 && pixels[0].length != width)) {
            throw new IllegalArgumentException("Expected " + width + "x" + height + " pixels");
        }
        for (int y = 0; y < height; y++) {
            setRow(y, pixels[y]);
        }
    }

    /**
     * Replaces the pixels of this plane with a copy of those of another plane
     * of the same dimensions.
     *
     * @param other the plane to copy
     */
    public void copyFrom(ImagePlane other) {
        if (other.width != width || other.height != height) {
            throw new IllegalArgumentException("Expected " + width + "x" + height + " pixels");
        }
        segment.copyFrom(other.segment);
    }

    /**
     * Creates an independent copy of this plane.
     *
     * @return a new plane
     */
    public ImagePlane copy() {
        var copy = allocate(width, height);
        copy.copyFrom(this);
        return copy;
    }
}
