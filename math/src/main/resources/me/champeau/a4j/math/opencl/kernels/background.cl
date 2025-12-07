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
 * OpenCL kernels for background model generation.
 */

/**
 * Generate background model using polynomial coefficients (degree 1).
 * Each work-item computes one pixel's background value.
 * Polynomial: c0 + c1*xNorm + c2*yNorm
 */
__kernel void generate_background_deg1(
    __global float* background,
    __global const float* coefficients,
    const int width,
    const int height,
    const float maxValue
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= width || y >= height) return;

    float xNorm = (float)x / (float)(width - 1);
    float yNorm = (float)y / (float)(height - 1);

    float bgValue = coefficients[0]
                  + coefficients[1] * xNorm
                  + coefficients[2] * yNorm;

    int idx = y * width + x;
    background[idx] = clamp(bgValue, 0.0f, maxValue);
}

/**
 * Generate background model using polynomial coefficients (degree 2).
 * Each work-item computes one pixel's background value.
 * Polynomial: c0 + c1*xNorm + c2*yNorm + c3*xNorm^2 + c4*xNorm*yNorm + c5*yNorm^2
 */
__kernel void generate_background_deg2(
    __global float* background,
    __global const float* coefficients,
    const int width,
    const int height,
    const float maxValue
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= width || y >= height) return;

    float xNorm = (float)x / (float)(width - 1);
    float yNorm = (float)y / (float)(height - 1);

    float bgValue = coefficients[0]
                  + coefficients[1] * xNorm
                  + coefficients[2] * yNorm
                  + coefficients[3] * xNorm * xNorm
                  + coefficients[4] * xNorm * yNorm
                  + coefficients[5] * yNorm * yNorm;

    int idx = y * width + x;
    background[idx] = clamp(bgValue, 0.0f, maxValue);
}

/**
 * Generate background model using polynomial coefficients (degree 3).
 * Each work-item computes one pixel's background value.
 * Terms: 1, x, y, x^2, xy, y^2, x^3, x^2y, xy^2, y^3
 */
__kernel void generate_background_deg3(
    __global float* background,
    __global const float* coefficients,
    const int width,
    const int height,
    const float maxValue
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= width || y >= height) return;

    float xNorm = (float)x / (float)(width - 1);
    float yNorm = (float)y / (float)(height - 1);
    float x2 = xNorm * xNorm;
    float y2 = yNorm * yNorm;
    float xy = xNorm * yNorm;

    float bgValue = coefficients[0]
                  + coefficients[1] * xNorm
                  + coefficients[2] * yNorm
                  + coefficients[3] * x2
                  + coefficients[4] * xy
                  + coefficients[5] * y2
                  + coefficients[6] * x2 * xNorm
                  + coefficients[7] * x2 * yNorm
                  + coefficients[8] * xNorm * y2
                  + coefficients[9] * y2 * yNorm;

    int idx = y * width + x;
    background[idx] = clamp(bgValue, 0.0f, maxValue);
}

/**
 * Generate background model using polynomial coefficients (generic degree).
 * Each work-item computes one pixel's background value.
 * This is slower than specialized kernels but handles any degree.
 */
__kernel void generate_background_generic(
    __global float* background,
    __global const float* coefficients,
    const int width,
    const int height,
    const int degree,
    const int numTerms,
    const float maxValue
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= width || y >= height) return;

    float xNorm = (float)x / (float)(width - 1);
    float yNorm = (float)y / (float)(height - 1);

    // Precompute powers of xNorm and yNorm
    float xPow[8];
    float yPow[8];
    xPow[0] = 1.0f;
    yPow[0] = 1.0f;
    for (int i = 1; i <= degree && i < 8; i++) {
        xPow[i] = xPow[i-1] * xNorm;
        yPow[i] = yPow[i-1] * yNorm;
    }

    float bgValue = 0.0f;
    int termIdx = 0;

    // Generate terms in same order as Java: for s=0..degree, for i=s..0, j=s-i
    for (int s = 0; s <= degree && termIdx < numTerms; s++) {
        for (int i = s; i >= 0 && termIdx < numTerms; i--) {
            int j = s - i;
            if (i < 8 && j < 8) {
                bgValue += coefficients[termIdx] * xPow[i] * yPow[j];
            }
            termIdx++;
        }
    }

    int idx = y * width + x;
    background[idx] = clamp(bgValue, 0.0f, maxValue);
}
