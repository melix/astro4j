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

// 2D convolution kernel with edge clamping

__kernel void convolve2d(
    __global const float* image,
    __global const float* convKernel,
    __global float* output,
    int imgWidth,
    int imgHeight,
    int kWidth,
    int kHeight,
    float factor
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= imgWidth || y >= imgHeight) {
        return;
    }

    int kcx = kWidth / 2;
    int kcy = kHeight / 2;

    float sum = 0.0f;
    for (int ky = 0; ky < kHeight; ky++) {
        int sy = y + ky - kcy;
        // Clamp to image bounds
        sy = clamp(sy, 0, imgHeight - 1);

        for (int kx = 0; kx < kWidth; kx++) {
            int sx = x + kx - kcx;
            // Clamp to image bounds
            sx = clamp(sx, 0, imgWidth - 1);

            sum += convKernel[ky * kWidth + kx] * image[sy * imgWidth + sx];
        }
    }

    float val = sum * factor;
    // Clamp result to valid range
    output[y * imgWidth + x] = clamp(val, 0.0f, 65535.0f);
}
