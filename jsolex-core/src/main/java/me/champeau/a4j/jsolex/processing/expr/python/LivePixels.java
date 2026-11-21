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
package me.champeau.a4j.jsolex.processing.expr.python;

import me.champeau.a4j.math.image.ImagePlane;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * The pixels of a mono image as seen from Python: a list of rows, each row
 * being a list of pixel values. Reading or writing an element reads or writes
 * the pixel of the image itself.
 */
final class LivePixels implements ProxyArray {
    private final ImagePlane plane;

    LivePixels(ImagePlane plane) {
        this.plane = plane;
    }

    float[][] toArray() {
        return plane.toArray();
    }

    @Override
    public Object get(long index) {
        return new Row(plane, checkIndex(index, plane.height()));
    }

    @Override
    public void set(long index, Value value) {
        int y = checkIndex(index, plane.height());
        if (!value.hasArrayElements() || value.getArraySize() != plane.width()) {
            throw new IllegalArgumentException("A row of this image must have " + plane.width() + " pixels");
        }
        var row = new float[plane.width()];
        for (int x = 0; x < row.length; x++) {
            row[x] = (float) value.getArrayElement(x).asDouble();
        }
        plane.setRow(y, row);
    }

    @Override
    public long getSize() {
        return plane.height();
    }

    private static int checkIndex(long index, int size) {
        if (index < 0 || index >= size) {
            throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for length " + size);
        }
        return (int) index;
    }

    private record Row(ImagePlane plane, int y) implements ProxyArray {
        @Override
        public Object get(long index) {
            return plane.get(checkIndex(index, plane.width()), y);
        }

        @Override
        public void set(long index, Value value) {
            plane.set(checkIndex(index, plane.width()), y, (float) value.asDouble());
        }

        @Override
        public long getSize() {
            return plane.width();
        }
    }
}
