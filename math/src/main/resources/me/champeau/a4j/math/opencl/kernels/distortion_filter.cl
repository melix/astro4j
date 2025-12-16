/*
 * Copyright 2025-2025 the original author or authors.
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
 * GPU kernels for distortion map filtering and smoothing.
 * Implements MAD-based outlier removal and Gaussian smoothing on GPU,
 * replacing expensive CPU operations for large distortion grids.
 */

/**
 * Simple insertion sort for small arrays in private memory.
 * More efficient than bitonic sort for very small n.
 */
inline void insertion_sort_private(__private float* data, int n) {
    for (int i = 1; i < n; i++) {
        float key = data[i];
        int j = i - 1;
        while (j >= 0 && data[j] > key) {
            data[j + 1] = data[j];
            j--;
        }
        data[j + 1] = key;
    }
}

// Maximum window size supported: 11x11 (halfWindow=5), yielding up to 120 neighbors
// Uses ~1.9KB private memory per work-item (4 arrays × 120 × 4 bytes)
#define MAX_NEIGHBORS 120

// Number of histogram bins for approximate median computation
#define NUM_BINS 512

/**
 * MAD-based outlier removal for distortion grid.
 * Uses local median and MAD (Median Absolute Deviation) to identify
 * and replace outlier displacement values.
 *
 * Supports window sizes up to 23x23 (halfWindow <= 11).
 *
 * Global size: (gridWidth, gridHeight)
 * Local size: should be (1, 1) for this implementation due to private memory usage
 */
__kernel void mad_filter_grid(
    __global const float* inputDx,
    __global const float* inputDy,
    __global float* outputDx,
    __global float* outputDy,
    const int gridWidth,
    const int gridHeight,
    const int halfWindow,
    const float madThreshold
) {
    int gx = get_global_id(0);
    int gy = get_global_id(1);
    if (gx >= gridWidth || gy >= gridHeight) return;

    int idx = gy * gridWidth + gx;
    float valueDx = inputDx[idx];
    float valueDy = inputDy[idx];

    // Collect neighbors into private arrays (one per work-item)
    // Supports windows up to 23x23 (halfWindow=11)
    float neighborsDx[MAX_NEIGHBORS];
    float neighborsDy[MAX_NEIGHBORS];
    float absDiffDx[MAX_NEIGHBORS];
    float absDiffDy[MAX_NEIGHBORS];

    int count = 0;
    for (int ny = gy - halfWindow; ny <= gy + halfWindow; ny++) {
        if (ny < 0 || ny >= gridHeight) continue;
        for (int nx = gx - halfWindow; nx <= gx + halfWindow; nx++) {
            if (nx < 0 || nx >= gridWidth) continue;
            if (nx == gx && ny == gy) continue;  // Skip self
            if (count >= MAX_NEIGHBORS) break;  // Safety limit
            int nidx = ny * gridWidth + nx;
            neighborsDx[count] = inputDx[nidx];
            neighborsDy[count] = inputDy[nidx];
            count++;
        }
        if (count >= MAX_NEIGHBORS) break;  // Safety limit
    }

    if (count == 0) {
        outputDx[idx] = valueDx;
        outputDy[idx] = valueDy;
        return;
    }

    // Sort to find medians
    insertion_sort_private(neighborsDx, count);
    insertion_sort_private(neighborsDy, count);

    float medianDx = neighborsDx[count / 2];
    float medianDy = neighborsDy[count / 2];

    // Compute absolute deviations
    for (int i = 0; i < count; i++) {
        absDiffDx[i] = fabs(neighborsDx[i] - medianDx);
        absDiffDy[i] = fabs(neighborsDy[i] - medianDy);
    }

    // Sort absolute deviations to get MAD
    insertion_sort_private(absDiffDx, count);
    insertion_sort_private(absDiffDy, count);

    // MAD scaled for normal distribution (1.4826 factor)
    float madDx = absDiffDx[count / 2] * 1.4826f;
    float madDy = absDiffDy[count / 2] * 1.4826f;

    // Ensure minimum MAD to avoid division issues
    madDx = fmax(madDx, 0.1f);
    madDy = fmax(madDy, 0.1f);

    // Check if current value is an outlier
    float threshDx = madThreshold * madDx;
    float threshDy = madThreshold * madDy;

    bool isOutlierDx = fabs(valueDx - medianDx) > threshDx;
    bool isOutlierDy = fabs(valueDy - medianDy) > threshDy;

    outputDx[idx] = isOutlierDx ? medianDx : valueDx;
    outputDy[idx] = isOutlierDy ? medianDy : valueDy;
}

/**
 * Interpolates unsampled points using inverse-distance-squared weighting.
 * Points with sampled[i] == 0 are interpolated from nearby sampled points.
 *
 * Global size: (gridWidth, gridHeight)
 */
__kernel void interpolate_unsampled(
    __global float* gridDx,
    __global float* gridDy,
    __global const int* sampled,
    const int gridWidth,
    const int gridHeight,
    const int searchRadius
) {
    int gx = get_global_id(0);
    int gy = get_global_id(1);
    if (gx >= gridWidth || gy >= gridHeight) return;

    int idx = gy * gridWidth + gx;

    // Skip if already sampled
    if (sampled[idx]) return;

    float sumDx = 0.0f;
    float sumDy = 0.0f;
    float weightSum = 0.0f;

    for (int dy = -searchRadius; dy <= searchRadius; dy++) {
        int ny = gy + dy;
        if (ny < 0 || ny >= gridHeight) continue;
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            if (dx == 0 && dy == 0) continue;
            int nx = gx + dx;
            if (nx < 0 || nx >= gridWidth) continue;

            int nidx = ny * gridWidth + nx;
            if (sampled[nidx]) {
                float distSq = (float)(dx * dx + dy * dy);
                float weight = 1.0f / distSq;
                sumDx += gridDx[nidx] * weight;
                sumDy += gridDy[nidx] * weight;
                weightSum += weight;
            }
        }
    }

    if (weightSum > 0.0f) {
        gridDx[idx] = sumDx / weightSum;
        gridDy[idx] = sumDy / weightSum;
    }
}

/**
 * Separable Gaussian smoothing - horizontal pass.
 * Applies 1D Gaussian filter along rows.
 *
 * Global size: (gridWidth, gridHeight)
 */
__kernel void gaussian_smooth_horizontal(
    __global const float* input,
    __global float* output,
    __constant const float* weights,
    const int gridWidth,
    const int gridHeight,
    const int kernelRadius
) {
    int gx = get_global_id(0);
    int gy = get_global_id(1);
    if (gx >= gridWidth || gy >= gridHeight) return;

    float sum = 0.0f;
    float weightSum = 0.0f;

    for (int k = -kernelRadius; k <= kernelRadius; k++) {
        int nx = gx + k;
        if (nx >= 0 && nx < gridWidth) {
            float w = weights[k + kernelRadius];
            sum += input[gy * gridWidth + nx] * w;
            weightSum += w;
        }
    }

    output[gy * gridWidth + gx] = sum / weightSum;
}

/**
 * Separable Gaussian smoothing - vertical pass.
 * Applies 1D Gaussian filter along columns.
 *
 * Global size: (gridWidth, gridHeight)
 */
__kernel void gaussian_smooth_vertical(
    __global const float* input,
    __global float* output,
    __constant const float* weights,
    const int gridWidth,
    const int gridHeight,
    const int kernelRadius
) {
    int gx = get_global_id(0);
    int gy = get_global_id(1);
    if (gx >= gridWidth || gy >= gridHeight) return;

    float sum = 0.0f;
    float weightSum = 0.0f;

    for (int k = -kernelRadius; k <= kernelRadius; k++) {
        int ny = gy + k;
        if (ny >= 0 && ny < gridHeight) {
            float w = weights[k + kernelRadius];
            sum += input[ny * gridWidth + gx] * w;
            weightSum += w;
        }
    }

    output[gy * gridWidth + gx] = sum / weightSum;
}

/**
 * Finds approximate median using histogram-based approach.
 * O(n) instead of O(n²) for insertion sort.
 */
inline float histogram_median(__private float* data, int n, float minVal, float maxVal) {
    if (n == 0) return 0.0f;
    if (n == 1) return data[0];

    float range = maxVal - minVal;
    if (range < 1e-10f) return minVal;

    int histogram[NUM_BINS];
    for (int i = 0; i < NUM_BINS; i++) {
        histogram[i] = 0;
    }

    float binWidth = range / NUM_BINS;
    float invBinWidth = 1.0f / binWidth;

    for (int i = 0; i < n; i++) {
        int bin = (int)((data[i] - minVal) * invBinWidth);
        bin = clamp(bin, 0, NUM_BINS - 1);
        histogram[bin]++;
    }

    int target = n / 2;
    int cumsum = 0;
    for (int i = 0; i < NUM_BINS; i++) {
        cumsum += histogram[i];
        if (cumsum > target) {
            return minVal + (i + 0.5f) * binWidth;
        }
    }
    return (minVal + maxVal) * 0.5f;
}

/**
 * MAD-based outlier removal using histogram-based median.
 * Uses O(n) histogram approach instead of O(n²) insertion sort.
 * Can use larger work groups (8x8) due to reduced private memory.
 *
 * Global size: (gridWidth, gridHeight)
 * Local size: can be (8, 8) or similar
 */
__kernel void mad_filter_grid_histogram(
    __global const float* inputDx,
    __global const float* inputDy,
    __global float* outputDx,
    __global float* outputDy,
    const int gridWidth,
    const int gridHeight,
    const int halfWindow,
    const float madThreshold
) {
    int gx = get_global_id(0);
    int gy = get_global_id(1);
    if (gx >= gridWidth || gy >= gridHeight) return;

    int idx = gy * gridWidth + gx;
    float valueDx = inputDx[idx];
    float valueDy = inputDy[idx];

    float neighborsDx[MAX_NEIGHBORS];
    float neighborsDy[MAX_NEIGHBORS];

    float minDx = FLT_MAX, maxDx = -FLT_MAX;
    float minDy = FLT_MAX, maxDy = -FLT_MAX;

    int count = 0;
    for (int ny = gy - halfWindow; ny <= gy + halfWindow; ny++) {
        if (ny < 0 || ny >= gridHeight) continue;
        for (int nx = gx - halfWindow; nx <= gx + halfWindow; nx++) {
            if (nx < 0 || nx >= gridWidth) continue;
            if (nx == gx && ny == gy) continue;
            if (count >= MAX_NEIGHBORS) break;

            int nidx = ny * gridWidth + nx;
            float dx = inputDx[nidx];
            float dy = inputDy[nidx];
            neighborsDx[count] = dx;
            neighborsDy[count] = dy;

            minDx = fmin(minDx, dx);
            maxDx = fmax(maxDx, dx);
            minDy = fmin(minDy, dy);
            maxDy = fmax(maxDy, dy);
            count++;
        }
        if (count >= MAX_NEIGHBORS) break;
    }

    if (count == 0) {
        outputDx[idx] = valueDx;
        outputDy[idx] = valueDy;
        return;
    }

    float medianDx = histogram_median(neighborsDx, count, minDx, maxDx);
    float medianDy = histogram_median(neighborsDy, count, minDy, maxDy);

    float absDiffDx[MAX_NEIGHBORS];
    float absDiffDy[MAX_NEIGHBORS];
    float minAbsDx = FLT_MAX, maxAbsDx = -FLT_MAX;
    float minAbsDy = FLT_MAX, maxAbsDy = -FLT_MAX;

    for (int i = 0; i < count; i++) {
        float adx = fabs(neighborsDx[i] - medianDx);
        float ady = fabs(neighborsDy[i] - medianDy);
        absDiffDx[i] = adx;
        absDiffDy[i] = ady;
        minAbsDx = fmin(minAbsDx, adx);
        maxAbsDx = fmax(maxAbsDx, adx);
        minAbsDy = fmin(minAbsDy, ady);
        maxAbsDy = fmax(maxAbsDy, ady);
    }

    float madDx = histogram_median(absDiffDx, count, minAbsDx, maxAbsDx) * 1.4826f;
    float madDy = histogram_median(absDiffDy, count, minAbsDy, maxAbsDy) * 1.4826f;

    madDx = fmax(madDx, 0.1f);
    madDy = fmax(madDy, 0.1f);

    bool isOutlierDx = fabs(valueDx - medianDx) > madThreshold * madDx;
    bool isOutlierDy = fabs(valueDy - medianDy) > madThreshold * madDy;

    outputDx[idx] = isOutlierDx ? medianDx : valueDx;
    outputDy[idx] = isOutlierDy ? medianDy : valueDy;
}

