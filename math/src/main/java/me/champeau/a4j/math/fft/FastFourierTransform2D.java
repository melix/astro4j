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
package me.champeau.a4j.math.fft;

class FastFourierTransform2D implements FastFourierTransform {

    private final float[] real;
    private final float[] imaginary;
    private final int width;
    private final int height;

    FastFourierTransform2D(float[] real, int width, int height) {
        this.real = real;
        this.imaginary = new float[real.length];
        this.width = width;
        this.height = height;
    }

    @Override
    public void transform() {
        transformRows();
        transformColumns();
    }

    @Override
    public void inverseTransform() {
        inverseColumns();
        inverseRows();
    }

    @Override
    public float[] real() {
        return real;
    }

    @Override
    public float[] imaginary() {
        return imaginary;
    }

    void transformColumns() {
        var r = new float[height];
        var i = new float[height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int offset = y * width + x;
                r[y] = real[offset];
                i[y] = imaginary[offset];
            }
            FastFourierTransform.ofComplex(r, i).transform();
            for (int y = 0; y < height; y++) {
                int offset = y * width + x;
                real[offset] = r[y];
                imaginary[offset] = i[y];
            }
        }
    }

    void inverseColumns() {
        float[] r = new float[height];
        float[] i = new float[height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int offset = y * width + x;
                r[y] = real[offset];
                i[y] = -imaginary[offset];
            }
            FastFourierTransform.ofComplex(r, i).transform();
            for (int y = 0; y < height; y++) {
                int offset = y * width + x;
                real[offset] = r[y] / height;
                imaginary[offset] = -(i[y] / height);
            }
        }
    }

    void transformRows() {
        float[] r = new float[width];
        float[] i = new float[width];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            System.arraycopy(real, offset, r, 0, width);
            System.arraycopy(imaginary, offset, i, 0, width);
            FastFourierTransform.ofComplex(r, i).transform();
            System.arraycopy(r, 0, real, offset, width);
            System.arraycopy(i, 0, imaginary, offset, width);
        }
    }

    void inverseRows() {
        float[] r = new float[width];
        float[] i = new float[width];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            System.arraycopy(real, offset, r, 0, width);
            for (int x = 0; x < width; x++) {
                i[x] = -imaginary[x + offset];
            }
            FastFourierTransform.ofComplex(r, i).transform();
            for (int x = 0; x < width; x++) {
                real[x + offset] = r[x] / width;
                imaginary[x + offset] = -(i[x] / width);
            }
        }
    }
}
