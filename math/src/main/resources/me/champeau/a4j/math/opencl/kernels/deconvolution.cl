/*
 * Copyright 2026-2026 the original author or authors.
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
 * Element-wise update steps of Richardson-Lucy deconvolution. The estimate
 * stays resident on the GPU across iterations; only these small kernels and
 * the convolution passes run per iteration.
 */

// ratio = image / (convolved + epsilon), matching the CPU implementation
// which adds epsilon to the convolved estimate before dividing.
__kernel void rl_ratio(
    __global const float* image,
    __global const float* convolved,
    __global float* ratio,
    float epsilon,
    int n
) {
    int i = get_global_id(0);
    if (i >= n) {
        return;
    }
    ratio[i] = image[i] / (convolved[i] + epsilon);
}

// estimate = clamp(estimate * correction), matching the CPU multiply
// followed by clampData.
__kernel void rl_update(
    __global float* estimate,
    __global const float* correction,
    int n
) {
    int i = get_global_id(0);
    if (i >= n) {
        return;
    }
    estimate[i] = clamp(estimate[i] * correction[i], 0.0f, 65535.0f);
}
