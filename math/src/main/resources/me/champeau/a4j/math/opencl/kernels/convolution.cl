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

// 2D convolution with edge clamping. The coefficients are read from constant
// memory when the kernel fits the device constant buffer, from global memory
// otherwise; both variants compute exactly the same sums.
#define DEFINE_CONVOLVE2D(name, kernelSpace) \
__kernel void name( \
    __global const float* image, \
    kernelSpace const float* convKernel, \
    __global float* output, \
    int imgWidth, \
    int imgHeight, \
    int kWidth, \
    int kHeight, \
    float factor \
) { \
    int x = get_global_id(0); \
    int y = get_global_id(1); \
    if (x >= imgWidth || y >= imgHeight) { \
        return; \
    } \
    int kcx = kWidth / 2; \
    int kcy = kHeight / 2; \
    float sum = 0.0f; \
    for (int ky = 0; ky < kHeight; ky++) { \
        int sy = clamp(y + ky - kcy, 0, imgHeight - 1); \
        for (int kx = 0; kx < kWidth; kx++) { \
            int sx = clamp(x + kx - kcx, 0, imgWidth - 1); \
            sum += convKernel[ky * kWidth + kx] * image[sy * imgWidth + sx]; \
        } \
    } \
    output[y * imgWidth + x] = sum * factor; \
}

DEFINE_CONVOLVE2D(convolve2d, __global)
DEFINE_CONVOLVE2D(convolve2d_const, __constant)

// Horizontal pass of a separable (low-rank) convolution. No factor: the
// vertical pass applies it on the final value.
__kernel void convolve1d_horizontal(
    __global const float* image,
    __global const float* rowKernel,
    __global float* output,
    int imgWidth,
    int imgHeight,
    int kWidth
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= imgWidth || y >= imgHeight) {
        return;
    }

    int kcx = kWidth / 2;
    float sum = 0.0f;
    for (int kx = 0; kx < kWidth; kx++) {
        int sx = clamp(x + kx - kcx, 0, imgWidth - 1);
        sum += rowKernel[kx] * image[y * imgWidth + sx];
    }
    output[y * imgWidth + x] = sum;
}

// Vertical pass of a separable (low-rank) convolution. When accumulate is
// non-zero the term is added to the existing output (used for rank-2
// kernels); when finalize is non-zero the factor is applied, matching the
// convolve2d output exactly.
__kernel void convolve1d_vertical(
    __global const float* image,
    __global const float* colKernel,
    __global float* output,
    int imgWidth,
    int imgHeight,
    int kHeight,
    float factor,
    int accumulate,
    int finalize
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= imgWidth || y >= imgHeight) {
        return;
    }

    int kcy = kHeight / 2;
    float sum = 0.0f;
    for (int ky = 0; ky < kHeight; ky++) {
        int sy = clamp(y + ky - kcy, 0, imgHeight - 1);
        sum += colKernel[ky] * image[sy * imgWidth + x];
    }

    int idx = y * imgWidth + x;
    float val = (accumulate ? output[idx] : 0.0f) + sum;
    if (finalize) {
        val = val * factor;
    }
    output[idx] = val;
}
