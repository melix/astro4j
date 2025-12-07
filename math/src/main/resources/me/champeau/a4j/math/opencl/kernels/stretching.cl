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
 *
 * OpenCL kernels for image stretching operations.
 * Only includes kernels that are beneficial to run on GPU (heavy math operations).
 * Simple operations like linear stretch, range expansion, contrast adjustment, and
 * curve transform are not worth running on GPU due to memory transfer overhead.
 */

/**
 * Find max value only using parallel reduction.
 */
__kernel void find_max(
    __global const float* data,
    __global float* partialMax,
    const int n,
    __local float* localMax
) {
    int gid = get_global_id(0);
    int lid = get_local_id(0);
    int groupSize = get_local_size(0);

    localMax[lid] = (gid < n) ? data[gid] : -FLT_MAX;
    barrier(CLK_LOCAL_MEM_FENCE);

    for (int stride = groupSize / 2; stride > 0; stride >>= 1) {
        if (lid < stride) {
            localMax[lid] = fmax(localMax[lid], localMax[lid + stride]);
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (lid == 0) {
        int groupId = get_group_id(0);
        partialMax[groupId] = localMax[0];
    }
}

/**
 * Gamma correction: normalize, apply power, rescale.
 * result[i] = pow(data[i] / maxVal, gamma) * maxPixel
 */
__kernel void gamma_stretch(
    __global float* data,
    const float maxVal,
    const float gamma,
    const float maxPixel,
    const int n
) {
    int idx = get_global_id(0);
    if (idx < n) {
        float v = data[idx];
        float normalized = v / maxVal;
        float corrected = pow(normalized, gamma);
        data[idx] = corrected * maxPixel;
    }
}

/**
 * Arcsinh stretch transformation.
 * Based on SIRIL's asinh implementation.
 */
__kernel void asinh_stretch(
    __global float* data,
    const float normalizedBp,
    const float stretch,
    const float asinhVal,
    const float maxPixel,
    const int n
) {
    int idx = get_global_id(0);
    if (idx < n) {
        float v = data[idx];
        if (v == 0.0f) {
            return;
        }
        float original = v / maxPixel;
        float pixel = fmax(0.0f, original - normalizedBp);
        float stretched = (pixel * asinh(original * stretch)) / (original * asinhVal);
        float result = stretched * maxPixel;
        if (isnan(result)) {
            result = 0.0f;
        }
        data[idx] = clamp(result, 0.0f, maxPixel);
    }
}
