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

// Element-wise arithmetic operations for image processing

__kernel void add_scalar(
    __global const float* data,
    float scalar,
    __global float* result,
    int n
) {
    int idx = get_global_id(0);
    if (idx < n) {
        result[idx] = data[idx] + scalar;
    }
}

__kernel void multiply_scalar(
    __global const float* data,
    float scalar,
    __global float* result,
    int n
) {
    int idx = get_global_id(0);
    if (idx < n) {
        result[idx] = data[idx] * scalar;
    }
}

__kernel void divide_images(
    __global const float* a,
    __global const float* b,
    __global float* result,
    int n
) {
    int idx = get_global_id(0);
    if (idx < n) {
        float divisor = b[idx];
        if (divisor == 0.0f) {
            result[idx] = 0.0f;
        } else {
            result[idx] = a[idx] / divisor;
        }
    }
}

__kernel void multiply_images(
    __global const float* a,
    __global const float* b,
    __global float* result,
    int n
) {
    int idx = get_global_id(0);
    if (idx < n) {
        result[idx] = a[idx] * b[idx];
    }
}
