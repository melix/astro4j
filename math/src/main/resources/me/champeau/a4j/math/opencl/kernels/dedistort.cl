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

// Dedistortion kernel with displacement map lookup and interpolation

#define LANCZOS_A 3
#define PI_F 3.14159265358979323846f

// Compute sinc(x) = sin(pi*x) / (pi*x), handling x=0
inline float sinc(float x) {
    if (fabs(x) < 1e-7f) {
        return 1.0f;
    }
    float pix = PI_F * x;
    return sin(pix) / pix;
}

// Lanczos kernel: sinc(x) * sinc(x/a) for |x| < a, 0 otherwise
inline float lanczos_weight(float x) {
    float absX = fabs(x);
    if (absX >= LANCZOS_A) {
        return 0.0f;
    }
    return sinc(x) * sinc(x / LANCZOS_A);
}

// Bilinear interpolation (fast, lower quality)
inline float bilinear_sample(
    __global const float* image,
    float xx, float yy,
    int width, int height
) {
    int x0 = (int)floor(xx);
    int y0 = (int)floor(yy);
    int x1 = x0 + 1;
    int y1 = y0 + 1;

    // Clamp to image bounds
    x0 = max(0, min(x0, width - 1));
    x1 = max(0, min(x1, width - 1));
    y0 = max(0, min(y0, height - 1));
    y1 = max(0, min(y1, height - 1));

    float fx = xx - floor(xx);
    float fy = yy - floor(yy);

    float v00 = image[y0 * width + x0];
    float v10 = image[y0 * width + x1];
    float v01 = image[y1 * width + x0];
    float v11 = image[y1 * width + x1];

    float i0 = v00 + fx * (v10 - v00);
    float i1 = v01 + fx * (v11 - v01);

    return i0 + fy * (i1 - i0);
}

// Lanczos-3 interpolation (high quality, slower)
// The 2D kernel is separable: weights are computed once per row/column
// instead of once per tap, which divides the transcendental call count by 6.
inline float lanczos_sample(
    __global const float* image,
    float xx, float yy,
    int width, int height
) {
    int x0 = (int)floor(xx);
    int y0 = (int)floor(yy);

    float wx[2 * LANCZOS_A];
    float wy[2 * LANCZOS_A];
    for (int t = 0; t < 2 * LANCZOS_A; t++) {
        wx[t] = lanczos_weight(xx - (x0 - LANCZOS_A + 1 + t));
        wy[t] = lanczos_weight(yy - (y0 - LANCZOS_A + 1 + t));
    }

    float sum = 0.0f;
    float weightSum = 0.0f;

    for (int tj = 0; tj < 2 * LANCZOS_A; tj++) {
        int j = y0 - LANCZOS_A + 1 + tj;
        if (j < 0 || j >= height) {
            continue;
        }
        int rowOffset = j * width;
        for (int ti = 0; ti < 2 * LANCZOS_A; ti++) {
            int i = x0 - LANCZOS_A + 1 + ti;
            if (i >= 0 && i < width) {
                float w = wx[ti] * wy[tj];
                sum += image[rowOffset + i] * w;
                weightSum += w;
            }
        }
    }

    return weightSum > 0.0f ? sum / weightSum : 0.0f;
}

// Bicubic weight for distortion grid interpolation
inline float cubic_weight(float t) {
    float a = -0.5f;
    float absT = fabs(t);
    if (absT <= 1.0f) {
        return (a + 2.0f) * absT * absT * absT - (a + 3.0f) * absT * absT + 1.0f;
    } else if (absT < 2.0f) {
        return a * absT * absT * absT - 5.0f * a * absT * absT + 8.0f * a * absT - 4.0f * a;
    } else {
        return 0.0f;
    }
}

// Bicubic interpolation of distortion grid
inline float bicubic_grid_sample(
    __global const float* grid,
    float ax, float ay,
    int gridWidth, int gridHeight
) {
    int x0 = (int)floor(ax);
    int y0 = (int)floor(ay);

    float dx = ax - x0;
    float dy = ay - y0;

    float result = 0.0f;
    for (int i = 0; i < 4; i++) {
        float wy = cubic_weight(dy - (i - 1));
        int yi = min(max(y0 - 1 + i, 0), gridHeight - 1);
        for (int j = 0; j < 4; j++) {
            int xi = min(max(x0 - 1 + j, 0), gridWidth - 1);
            float wx = cubic_weight(dx - (j - 1));
            result += grid[yi * gridWidth + xi] * wx * wy;
        }
    }
    return result;
}

/**
 * Dedistort kernel using a sparse distortion grid (bicubic interpolation on GPU).
 * More memory efficient but requires grid interpolation per pixel.
 * <p>
 * When {@code scale} is greater than 1, the output is allocated at
 * {@code outWidth}x{@code outHeight} (~scale*input) and each output pixel is sampled
 * from the source at the corresponding sub-pixel coordinate (inverse-warp drizzle).
 */
__kernel void dedistort_sparse_lanczos(
    __global const float* input,
    __global const float* gridDx,
    __global const float* gridDy,
    __global float* output,
    int width,
    int height,
    int gridWidth,
    int gridHeight,
    int gridStep,
    int outWidth,
    int outHeight,
    float scale
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= outWidth || y >= outHeight) {
        return;
    }

    // Source-grid coordinates for this output pixel
    float sx = (float)x / scale;
    float sy = (float)y / scale;

    // Convert source coords to grid coords
    float ax = sx / gridStep;
    float ay = sy / gridStep;

    // Bicubic interpolation of displacement from grid
    float dx = 0.0f;
    float dy = 0.0f;

    if (ax >= 0 && ax < gridWidth - 1 && ay >= 0 && ay < gridHeight - 1) {
        dx = bicubic_grid_sample(gridDx, ax, ay, gridWidth, gridHeight);
        dy = bicubic_grid_sample(gridDy, ax, ay, gridWidth, gridHeight);
    }

    float srcX = sx + dx;
    float srcY = sy + dy;

    int idx = y * outWidth + x;
    if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
        output[idx] = lanczos_sample(input, srcX, srcY, width, height);
    } else {
        output[idx] = 0.0f;
    }
}

/**
 * Sparse grid dedistort with bilinear interpolation (fastest).
 * Supports super-resolution output via the {@code scale} parameter (see lanczos variant).
 */
__kernel void dedistort_sparse_bilinear(
    __global const float* input,
    __global const float* gridDx,
    __global const float* gridDy,
    __global float* output,
    int width,
    int height,
    int gridWidth,
    int gridHeight,
    int gridStep,
    int outWidth,
    int outHeight,
    float scale
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= outWidth || y >= outHeight) {
        return;
    }

    // Source-grid coordinates for this output pixel
    float sx = (float)x / scale;
    float sy = (float)y / scale;

    // Convert source coords to grid coords
    float ax = sx / gridStep;
    float ay = sy / gridStep;

    // Bicubic interpolation of displacement from grid
    float dx = 0.0f;
    float dy = 0.0f;

    if (ax >= 0 && ax < gridWidth - 1 && ay >= 0 && ay < gridHeight - 1) {
        dx = bicubic_grid_sample(gridDx, ax, ay, gridWidth, gridHeight);
        dy = bicubic_grid_sample(gridDy, ax, ay, gridWidth, gridHeight);
    }

    float srcX = sx + dx;
    float srcY = sy + dy;

    int idx = y * outWidth + x;
    if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
        output[idx] = bilinear_sample(input, srcX, srcY, width, height);
    } else {
        output[idx] = 0.0f;
    }
}
