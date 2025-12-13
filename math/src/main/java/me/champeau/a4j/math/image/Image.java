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
package me.champeau.a4j.math.image;

import java.util.Arrays;

/**
 * Represents a 2D image with floating-point pixel values.
 *
 * @param width the width of the image
 * @param height the height of the image
 * @param data the pixel data organized as [y][x]
 */
public record Image(int width, int height, float[][] data) {

    /**
     * Creates a new image with different pixel data but same dimensions.
     *
     * @param newData the new pixel data
     * @return a new image with the updated data
     */
    public Image withData(float[][] newData) {
        var copyData = deepCopy(newData);
        return new Image(width, height, copyData);
    }

    /**
     * Creates a deep copy of this image.
     *
     * @return a new image with copied data
     */
    public Image copy() {
        var copyData = deepCopy(data);
        return new Image(width, height, copyData);
    }

    private float[][] deepCopy(float[][] original) {
        if (original.length==0) {
            return new float[0][];
        }
        var copy = new float[original.length][];
        for (int i = 0; i < original.length; i++) {
            var length = original[i].length;
            copy[i] = new float[length];
            System.arraycopy(original[i], 0, copy[i], 0, length);
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Image image = (Image) o;

        if (width != image.width) {
            return false;
        }
        if (height != image.height) {
            return false;
        }
        for (int i = 0; i < data.length; i++) {
            float[] line = data[i];
            float[] other = image.data[i];
            if (!Arrays.equals(line, other)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + height;
        for (float[] line : data) {
            result = 31 * result + Arrays.hashCode(line);
        }
        return result;
    }

    @Override
    public String toString() {
        return "{ width = " + width + ", height = " + height + " }";
    }

}
