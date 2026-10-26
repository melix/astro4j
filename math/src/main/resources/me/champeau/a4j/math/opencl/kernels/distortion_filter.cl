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

// Number of histogram bins for approximate median computation
#define NUM_BINS 512

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

// Tiled MAD filter: a work-group of at most MAD_LOCAL x MAD_LOCAL work-items
// shares the grid values of its window plus a halo of MAD_MAX_HALF_WINDOW.
#define MAD_LOCAL 8
#define MAD_MAX_HALF_WINDOW 5
#define MAD_TILE (MAD_LOCAL + 2 * MAD_MAX_HALF_WINDOW)

// log2(NUM_BINS) bits of a bin index, selected 3 bits per round
#define BIN_BITS 9

inline int histogram_bin(float value, float minVal, float invBinWidth) {
    int bin = (int)((value - minVal) * invBinWidth);
    return clamp(bin, 0, NUM_BINS - 1);
}

typedef struct {
    __local const float* tileDx;
    __local const float* tileDy;
    int gx, gy;
    int lx, ly;
    int halfWindow;
    int gridWidth, gridHeight;
} MadWindow;

// Iterates over the neighbours of the window centre, excluding the centre
// itself and positions outside the grid. The body receives the neighbour
// values in vdx and vdy.
#define FOR_EACH_NEIGHBOUR(win, vdx, vdy, body) \
    for (int ny = (win).gy - (win).halfWindow; ny <= (win).gy + (win).halfWindow; ny++) { \
        if (ny < 0 || ny >= (win).gridHeight) continue; \
        int rowOffset = ((win).ly + ny - (win).gy + MAD_MAX_HALF_WINDOW) * MAD_TILE; \
        for (int nx = (win).gx - (win).halfWindow; nx <= (win).gx + (win).halfWindow; nx++) { \
            if (nx < 0 || nx >= (win).gridWidth || (nx == (win).gx && ny == (win).gy)) continue; \
            int tileIdx = rowOffset + (win).lx + nx - (win).gx + MAD_MAX_HALF_WINDOW; \
            float vdx = (win).tileDx[tileIdx]; \
            float vdy = (win).tileDy[tileIdx]; \
            body \
        } \
    }

#define COUNT_BUCKET(key, prefix, shift, counts) { \
    int match = ((key) >> ((shift) + 3)) == (prefix); \
    int digit = ((key) >> (shift)) & 7; \
    counts.s0 += match & (digit == 0); \
    counts.s1 += match & (digit == 1); \
    counts.s2 += match & (digit == 2); \
    counts.s3 += match & (digit == 3); \
    counts.s4 += match & (digit == 4); \
    counts.s5 += match & (digit == 5); \
    counts.s6 += match & (digit == 6); \
    counts.s7 += match & (digit == 7); \
}

// Picks the bucket holding the element of rank k among the counted keys,
// and updates the rank and the prefix accordingly.
inline int select_bucket(int8 counts, int* k, int* prefix) {
    int c[8] = {counts.s0, counts.s1, counts.s2, counts.s3, counts.s4, counts.s5, counts.s6, counts.s7};
    int acc = 0;
    int chosen = 7;
    for (int b = 0; b < 8; b++) {
        int next = acc + c[b];
        if (*k < next) {
            chosen = b;
            break;
        }
        acc = next;
    }
    *k -= acc;
    *prefix = (*prefix << 3) | chosen;
    return chosen;
}

/**
 * Same value as the CPU histogram median over the neighbour values (or their
 * absolute deviation from a centre when absolute is set): the centre of the bin
 * holding the element of rank n / 2, found by a radix select on the bin indices
 * instead of a 512-entry histogram.
 */
inline float2 window_medians(const MadWindow* win, int n,
                             float2 center, bool absolute,
                             float2 minVal, float2 maxVal) {
    float2 range = maxVal - minVal;
    float2 binWidth = range / NUM_BINS;
    float2 invBinWidth = 1.0f / binWidth;
    int kx = n / 2;
    int ky = n / 2;
    int prefixX = 0;
    int prefixY = 0;
    for (int shift = BIN_BITS - 3; shift >= 0; shift -= 3) {
        int8 countsX = (int8)(0);
        int8 countsY = (int8)(0);
        FOR_EACH_NEIGHBOUR(*win, vdx, vdy, {
            float ax = absolute ? fabs(vdx - center.x) : vdx;
            float ay = absolute ? fabs(vdy - center.y) : vdy;
            int keyX = histogram_bin(ax, minVal.x, invBinWidth.x);
            int keyY = histogram_bin(ay, minVal.y, invBinWidth.y);
            COUNT_BUCKET(keyX, prefixX, shift, countsX);
            COUNT_BUCKET(keyY, prefixY, shift, countsY);
        })
        select_bucket(countsX, &kx, &prefixX);
        select_bucket(countsY, &ky, &prefixY);
    }
    float2 median;
    median.x = minVal.x + (prefixX + 0.5f) * binWidth.x;
    median.y = minVal.y + (prefixY + 0.5f) * binWidth.y;
    median.x = (n == 1 || range.x < 1e-10f) ? minVal.x : median.x;
    median.y = (n == 1 || range.y < 1e-10f) ? minVal.y : median.y;
    return median;
}

/**
 * MAD-based outlier removal with the approximate (histogram bin centre) median
 * of the CPU implementation. The window is read from a shared tile and the
 * medians are found by radix select on the bin indices, so no work-item needs
 * a private histogram. halfWindow must not exceed MAD_MAX_HALF_WINDOW.
 *
 * Global size: (gridWidth, gridHeight) rounded up to the local size
 * Local size: at most (MAD_LOCAL, MAD_LOCAL)
 */
__kernel void mad_filter_grid_tiled(
    __global const float* inputDx,
    __global const float* inputDy,
    __global float* outputDx,
    __global float* outputDy,
    const int gridWidth,
    const int gridHeight,
    const int halfWindow,
    const float madThreshold
) {
    __local float tileDx[MAD_TILE * MAD_TILE];
    __local float tileDy[MAD_TILE * MAD_TILE];

    int lx = get_local_id(0);
    int ly = get_local_id(1);
    int localWidth = get_local_size(0);
    int localHeight = get_local_size(1);
    int originX = get_group_id(0) * localWidth;
    int originY = get_group_id(1) * localHeight;

    for (int i = ly * localWidth + lx; i < MAD_TILE * MAD_TILE; i += localWidth * localHeight) {
        int tx = originX - MAD_MAX_HALF_WINDOW + i % MAD_TILE;
        int ty = originY - MAD_MAX_HALF_WINDOW + i / MAD_TILE;
        bool inside = tx >= 0 && tx < gridWidth && ty >= 0 && ty < gridHeight;
        int idx = inside ? ty * gridWidth + tx : 0;
        tileDx[i] = inside ? inputDx[idx] : 0.0f;
        tileDy[i] = inside ? inputDy[idx] : 0.0f;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    int gx = originX + lx;
    int gy = originY + ly;
    if (gx >= gridWidth || gy >= gridHeight) return;

    MadWindow win;
    win.tileDx = tileDx;
    win.tileDy = tileDy;
    win.gx = gx;
    win.gy = gy;
    win.lx = lx;
    win.ly = ly;
    win.halfWindow = halfWindow;
    win.gridWidth = gridWidth;
    win.gridHeight = gridHeight;

    int idx = gy * gridWidth + gx;
    float2 value = (float2)(inputDx[idx], inputDy[idx]);

    float2 minVal = (float2)(FLT_MAX, FLT_MAX);
    float2 maxVal = (float2)(-FLT_MAX, -FLT_MAX);
    int count = 0;
    FOR_EACH_NEIGHBOUR(win, vdx, vdy, {
        minVal = fmin(minVal, (float2)(vdx, vdy));
        maxVal = fmax(maxVal, (float2)(vdx, vdy));
        count++;
    })

    if (count == 0) {
        outputDx[idx] = value.x;
        outputDy[idx] = value.y;
        return;
    }

    float2 median = window_medians(&win, count, (float2)(0.0f, 0.0f), false, minVal, maxVal);

    float2 minAbs = (float2)(FLT_MAX, FLT_MAX);
    float2 maxAbs = (float2)(-FLT_MAX, -FLT_MAX);
    FOR_EACH_NEIGHBOUR(win, vdx, vdy, {
        float2 a = fabs((float2)(vdx, vdy) - median);
        minAbs = fmin(minAbs, a);
        maxAbs = fmax(maxAbs, a);
    })

    float2 mad = window_medians(&win, count, median, true, minAbs, maxAbs) * 1.4826f;
    mad = fmax(mad, (float2)(0.1f, 0.1f));

    float2 deviation = fabs(value - median);
    outputDx[idx] = deviation.x > madThreshold * mad.x ? median.x : value.x;
    outputDy[idx] = deviation.y > madThreshold * mad.y ? median.y : value.y;
}
