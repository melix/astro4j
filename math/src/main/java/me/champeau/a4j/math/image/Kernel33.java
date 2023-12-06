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

public record Kernel33(
        float factor,
        float a, float b, float c,
        float d, float e, float f,
        float g, float h, float i
) implements Kernel {

    public static final Kernel33 IDENTITY = new Kernel33(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    public static final Kernel33 EDGE_DETECTION = new Kernel33(1, -1, -1, -1, -1, 8, -1, -1, -1, -1);
    public static final Kernel33 SHARPEN = new Kernel33(1, 0, -1, 0, -1, 5, -1, 0, -1, 0);
    public static final Kernel33 SHARPEN2 = new Kernel33(1f / 9f, -1, -1, -1, -1, 17, -1, -1, -1, -1);
    public static final Kernel33 GAUSSIAN_BLUR = new Kernel33(1f / 16f, 1, 2, 1, 2, 4, 2, 1, 2, 1);
    public static final Kernel33 SOBEL_LEFT = new Kernel33(1, 1, 0, -1, 2, 0, -2, 1, 0, -1);
    public static final Kernel33 SOBEL_RIGHT = new Kernel33(1, -1, 0, 1, -2, 0, 2, -1, 0, 1);
    public static final Kernel33 SOBEL_TOP = new Kernel33(1, 1, 2, 1, 0, 0, 0, -1, -2, -1);
    public static final Kernel33 SOBEL_BOTTOM = new Kernel33(1, -1, -2, -1, 0, 0, 0, 1, 2, 1);
    public static final Kernel33 LAPLACIAN = new Kernel33(1, 0, 1, 0, 1, -4, 1, 0, 1, 0);
    public static final Kernel33 LAPLACIAN_B = new Kernel33(1, 0.5f, 0, 0.5f, 0, -2, 0, 0.5f, 0, 0.5f);

    public int rows() {
        return 3;
    }

    public int cols() {
        return 3;
    }

    @Override
    public float[][] kernel() {
        return new float[][]{
                new float[]{a, b, c},
                new float[]{d, e, f},
                new float[]{g, h, i},
        };
    }
}
