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
 * GPU kernels for efficient tile extraction with integral image signal filtering.
 * These kernels eliminate CPU-side tile extraction loops by performing all
 * operations directly on GPU memory.
 */

/**
 * Computes an integral image (summed-area table) from an input image.
 * The integral image allows O(1) computation of any rectangular region sum.
 *
 * Must be executed in two passes:
 * 1. Horizontal prefix sum (each row independently)
 * 2. Vertical prefix sum (each column, reading from pass 1 output)
 *
 * Global size: (width, height)
 */
__kernel void integral_image_horizontal(
    __global const float* input,
    __global float* output,
    const int width,
    const int height
) {
    int y = get_global_id(1);
    if (y >= height) return;

    // Each work-item processes one row
    int rowOffset = y * width;
    float sum = 0.0f;
    for (int x = 0; x < width; x++) {
        sum += input[rowOffset + x];
        output[rowOffset + x] = sum;
    }
}

__kernel void integral_image_vertical(
    __global float* data,
    const int width,
    const int height
) {
    int x = get_global_id(0);
    if (x >= width) return;

    // Each work-item processes one column
    float sum = 0.0f;
    for (int y = 0; y < height; y++) {
        sum += data[y * width + x];
        data[y * width + x] = sum;
    }
}

/**
 * Returns the sum of a rectangular region using an integral image.
 * Region is from (x, y) with dimensions (w, h).
 */
inline float integral_area_sum(
    __global const float* integral,
    int x, int y, int w, int h,
    int imageWidth
) {
    // Handle boundary: clamp to image dimensions
    int x1 = x - 1;
    int y1 = y - 1;
    int x2 = x + w - 1;
    int y2 = y + h - 1;

    float bottomRight = integral[y2 * imageWidth + x2];
    float bottomLeft = (x1 >= 0) ? integral[y2 * imageWidth + x1] : 0.0f;
    float topRight = (y1 >= 0) ? integral[y1 * imageWidth + x2] : 0.0f;
    float topLeft = (x1 >= 0 && y1 >= 0) ? integral[y1 * imageWidth + x1] : 0.0f;

    return bottomRight - bottomLeft - topRight + topLeft;
}

/**
 * Filters grid positions based on signal threshold using integral images.
 * For each grid position, checks if the average signal exceeds the threshold.
 *
 * Global size: (gridWidth, gridHeight)
 * Output validMask[i] = 1 if position i should be processed, 0 otherwise
 */
__kernel void filter_positions_by_signal(
    __global const float* refIntegral,
    __global const float* targetIntegral,  // Can be NULL (set numTargetPixels=0)
    __global int* validMask,
    __global int* gridX,
    __global int* gridY,
    const int imageWidth,
    const int imageHeight,
    const int tileSize,
    const int increment,
    const int gridWidth,
    const int gridHeight,
    const float signalThreshold,
    const int hasTarget
) {
    int gx = get_global_id(0);
    int gy = get_global_id(1);
    if (gx >= gridWidth || gy >= gridHeight) return;

    int posIdx = gy * gridWidth + gx;
    int x = gx * increment;
    int y = gy * increment;

    // Store grid coordinates for later use
    gridX[posIdx] = x;
    gridY[posIdx] = y;

    // Bounds check
    if (x + tileSize > imageWidth || y + tileSize > imageHeight) {
        validMask[posIdx] = 0;
        return;
    }

    // Compute reference signal using integral image
    float refSum = integral_area_sum(refIntegral, x, y, tileSize, tileSize, imageWidth);
    float refAvg = refSum / (float)(tileSize * tileSize);

    if (refAvg <= signalThreshold) {
        validMask[posIdx] = 0;
        return;
    }

    // Check target signal if provided
    if (hasTarget) {
        float targetSum = integral_area_sum(targetIntegral, x, y, tileSize, tileSize, imageWidth);
        float targetAvg = targetSum / (float)(tileSize * tileSize);
        if (targetAvg <= signalThreshold) {
            validMask[posIdx] = 0;
            return;
        }
    }

    validMask[posIdx] = 1;
}

/**
 * Computes prefix sum of validMask to get output indices for each valid position.
 * This allows us to compact valid tiles into contiguous memory.
 *
 * Note: This is a simple sequential scan. For very large grids, a parallel
 * prefix sum would be more efficient, but for typical grid sizes (~62K positions),
 * this is fast enough.
 *
 * Global size: (1)
 * Returns total count of valid positions in validCount[0]
 */
__kernel void compute_tile_indices(
    __global const int* validMask,
    __global int* outputIndices,
    __global int* validCount,
    const int totalPositions
) {
    int count = 0;
    for (int i = 0; i < totalPositions; i++) {
        if (validMask[i]) {
            outputIndices[i] = count;
            count++;
        } else {
            outputIndices[i] = -1;
        }
    }
    validCount[0] = count;
}

/**
 * Extracts tiles from full images directly on GPU.
 * Only extracts tiles for positions where validMask == 1.
 *
 * Global size: (tileSize, tileSize, totalPositions)
 */
__kernel void extract_tiles(
    __global const float* refImage,
    __global const float* targetImage,
    __global const int* gridX,
    __global const int* gridY,
    __global const int* validMask,
    __global const int* outputIndices,
    __global float* refTiles,
    __global float* targetTiles,
    const int imageWidth,
    const int tileSize,
    const int totalPositions
) {
    int tx = get_global_id(0);  // x within tile
    int ty = get_global_id(1);  // y within tile
    int posIdx = get_global_id(2);

    if (tx >= tileSize || ty >= tileSize || posIdx >= totalPositions) return;
    if (validMask[posIdx] == 0) return;

    int outIdx = outputIndices[posIdx];
    if (outIdx < 0) return;

    int x = gridX[posIdx];
    int y = gridY[posIdx];

    int srcIdx = (y + ty) * imageWidth + (x + tx);
    int dstIdx = outIdx * tileSize * tileSize + ty * tileSize + tx;

    refTiles[dstIdx] = refImage[srcIdx];
    targetTiles[dstIdx] = targetImage[srcIdx];
}

/**
 * Writes displacement results back to the distortion map.
 * Handles both valid positions (with computed displacements) and
 * invalid positions (zero displacement).
 *
 * Global size: (totalPositions)
 */
__kernel void write_displacements(
    __global const int* gridX,
    __global const int* gridY,
    __global const int* validMask,
    __global const int* outputIndices,
    __global const float* displacements,  // [validCount * 2]
    __global float* gridDx,
    __global float* gridDy,
    const int gridWidth,
    const int tileSize,
    const int totalPositions,
    const int increment
) {
    int posIdx = get_global_id(0);
    if (posIdx >= totalPositions) return;

    int x = gridX[posIdx];
    int y = gridY[posIdx];

    // Compute grid indices for output
    int gx = x / increment;
    int gy = y / increment;
    int outIdx = gy * gridWidth + gx;

    if (validMask[posIdx]) {
        int dispIdx = outputIndices[posIdx];
        gridDx[outIdx] = displacements[dispIdx * 2];
        gridDy[outIdx] = displacements[dispIdx * 2 + 1];
    } else {
        gridDx[outIdx] = 0.0f;
        gridDy[outIdx] = 0.0f;
    }
}
