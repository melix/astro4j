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
 * Batched phase correlation kernel for GPU-accelerated image alignment.
 * Each work-group processes one tile pair, computing the phase correlation
 * displacement using FFT-based cross-power spectrum analysis.
 */

// Complex multiplication: (a + bi) * (c + di) = (ac - bd) + (ad + bc)i
inline float2 cmul(float2 a, float2 b) {
    return (float2)(a.x * b.x - a.y * b.y, a.x * b.y + a.y * b.x);
}

// Bit-reverse a 5-bit number (for N=32)
inline int bitrev5(int n) {
    int r = 0;
    r |= ((n >> 0) & 1) << 4;
    r |= ((n >> 1) & 1) << 3;
    r |= ((n >> 2) & 1) << 2;
    r |= ((n >> 3) & 1) << 1;
    r |= ((n >> 4) & 1) << 0;
    return r;
}


// In-place 32-point Cooley-Tukey FFT
// All 32 threads must call this together
// direction: -1 for forward FFT, +1 for inverse FFT
void fft32_inplace(__local float2* data, int tid, int direction) {
    float sign = (float)direction;

    // Step 1: Bit-reversal permutation
    // Each thread swaps its element with its bit-reversed partner (if needed)
    barrier(CLK_LOCAL_MEM_FENCE);
    int rev = bitrev5(tid);
    if (tid < rev) {
        float2 temp = data[tid];
        data[tid] = data[rev];
        data[rev] = temp;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 2: Butterfly stages
    // For N=32, we have 5 stages (log2(32) = 5)

    // Stage 1: butterflies of size 2 (stride=1)
    {
        int pair = tid >> 1;      // which pair (0-15)
        int pos = tid & 1;        // position in pair (0 or 1)
        int i = pair * 2 + pos;
        int j = pair * 2 + (1 - pos);

        if (pos == 0) {
            float2 a = data[i];
            float2 b = data[i + 1];
            data[i] = a + b;
            data[i + 1] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 2: butterflies of size 4 (stride=2)
    {
        int group = tid >> 2;     // which group of 4 (0-7)
        int pos = tid & 3;        // position in group (0-3)
        int base = group * 4;

        if (pos < 2) {
            int i = base + pos;
            int j = base + pos + 2;
            float angle = sign * M_PI_F * pos / 2.0f;
            float2 w = (float2)(cos(angle), sin(angle));

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 3: butterflies of size 8 (stride=4)
    {
        int group = tid >> 3;     // which group of 8 (0-3)
        int pos = tid & 7;        // position in group (0-7)
        int base = group * 8;

        if (pos < 4) {
            int i = base + pos;
            int j = base + pos + 4;
            float angle = sign * M_PI_F * pos / 4.0f;
            float2 w = (float2)(cos(angle), sin(angle));

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 4: butterflies of size 16 (stride=8)
    {
        int group = tid >> 4;     // which group of 16 (0-1)
        int pos = tid & 15;       // position in group (0-15)
        int base = group * 16;

        if (pos < 8) {
            int i = base + pos;
            int j = base + pos + 8;
            float angle = sign * M_PI_F * pos / 8.0f;
            float2 w = (float2)(cos(angle), sin(angle));

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 5: butterflies of size 32 (stride=16)
    {
        if (tid < 16) {
            int i = tid;
            int j = tid + 16;
            float angle = sign * M_PI_F * tid / 16.0f;
            float2 w = (float2)(cos(angle), sin(angle));

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);
}

// 2D FFT for 32x32 tile using row-column decomposition
// Work-group is 32x32 threads (1024 threads)
void fft2d_32x32(__local float2 data[32][32], int localX, int localY, int direction) {
    // FFT all rows - thread (localX, localY) works on row localY
    fft32_inplace(&data[localY][0], localX, direction);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Transpose the matrix
    float2 temp = data[localX][localY];
    barrier(CLK_LOCAL_MEM_FENCE);
    data[localY][localX] = temp;
    barrier(CLK_LOCAL_MEM_FENCE);

    // FFT all columns (now rows after transpose)
    fft32_inplace(&data[localY][0], localX, direction);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Transpose back
    temp = data[localX][localY];
    barrier(CLK_LOCAL_MEM_FENCE);
    data[localY][localX] = temp;
    barrier(CLK_LOCAL_MEM_FENCE);
}

// Parallel reduction to find maximum value and its position
// Tie-breaking: when values are equal, prefer peak closer to center (matches CPU behavior)
void find_peak_32x32(__local float data[32][32],
                     __local float* rowMax, __local int* rowMaxX, __local int* rowMaxDistSq,
                     __local int* peakY, __local int* peakX, __local float* peakVal,
                     __local float rowData[32][32], __local int rowIdx[32][32], __local int rowDistSq[32][32],
                     __local float* colData, __local int* colIdxY, __local int* colIdxX, __local int* colDistSq,
                     int localX, int localY) {
    int centerX = 16;
    int centerY = 16;

    // Each thread starts with its own value and distance to center
    int dx = localX - centerX;
    int dy = localY - centerY;
    int distSq = dx * dx + dy * dy;

    rowData[localY][localX] = data[localY][localX];
    rowIdx[localY][localX] = localX;
    rowDistSq[localY][localX] = distSq;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Parallel reduction within row with tie-breaking
    for (int stride = 16; stride > 0; stride >>= 1) {
        if (localX < stride) {
            float valA = rowData[localY][localX];
            float valB = rowData[localY][localX + stride];
            int distA = rowDistSq[localY][localX];
            int distB = rowDistSq[localY][localX + stride];

            // Tie-breaking: prefer peak closer to center
            if (valB > valA || (valB == valA && distB < distA)) {
                rowData[localY][localX] = valB;
                rowIdx[localY][localX] = rowIdx[localY][localX + stride];
                rowDistSq[localY][localX] = distB;
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Store row maximum with distance
    if (localX == 0) {
        rowMax[localY] = rowData[localY][0];
        rowMaxX[localY] = rowIdx[localY][0];
        rowMaxDistSq[localY] = rowDistSq[localY][0];
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Find max across all rows (use row 0 threads) with tie-breaking
    if (localY == 0) {
        colData[localX] = rowMax[localX];
        colIdxY[localX] = localX;
        colIdxX[localX] = rowMaxX[localX];
        colDistSq[localX] = rowMaxDistSq[localX];
        barrier(CLK_LOCAL_MEM_FENCE);

        for (int stride = 16; stride > 0; stride >>= 1) {
            if (localX < stride) {
                float valA = colData[localX];
                float valB = colData[localX + stride];
                int distA = colDistSq[localX];
                int distB = colDistSq[localX + stride];

                // Tie-breaking: prefer peak closer to center
                if (valB > valA || (valB == valA && distB < distA)) {
                    colData[localX] = valB;
                    colIdxY[localX] = colIdxY[localX + stride];
                    colIdxX[localX] = colIdxX[localX + stride];
                    colDistSq[localX] = distB;
                }
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }

        if (localX == 0) {
            *peakVal = colData[0];
            *peakY = colIdxY[0];
            *peakX = colIdxX[0];
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);
}

// Subpixel refinement using Gaussian fit
float2 subpixel_fit_32(__local float data[32][32], int centerY, int centerX) {
    if (centerX < 1 || centerX >= 31 || centerY < 1 || centerY >= 31) {
        return (float2)(0.0f, 0.0f);
    }

    float c = fmax(data[centerY][centerX], 1e-10f);
    float l = fmax(data[centerY][centerX - 1], 1e-10f);
    float r = fmax(data[centerY][centerX + 1], 1e-10f);
    float t = fmax(data[centerY - 1][centerX], 1e-10f);
    float b = fmax(data[centerY + 1][centerX], 1e-10f);

    float logC = log(c);
    float logL = log(l);
    float logR = log(r);
    float logT = log(t);
    float logB = log(b);

    float denomX = 2.0f * logC - logL - logR;
    float denomY = 2.0f * logC - logT - logB;

    float offsetX = 0.0f;
    float offsetY = 0.0f;

    if (fabs(denomX) > 1e-10f) {
        offsetX = 0.5f * (logL - logR) / denomX;
        offsetX = clamp(offsetX, -1.0f, 1.0f);
    }
    if (fabs(denomY) > 1e-10f) {
        offsetY = 0.5f * (logT - logB) / denomY;
        offsetY = clamp(offsetY, -1.0f, 1.0f);
    }

    return (float2)(offsetX, offsetY);
}

// Main kernel for 32x32 tiles
// Work-group size must be 32x32 (1024 threads)
__kernel void batched_phase_correlation_32(
    __global const float* refTiles,      // [numTiles * 32 * 32]
    __global const float* targetTiles,   // [numTiles * 32 * 32]
    __global float* results,             // [numTiles * 2] (dx, dy per tile)
    const int numTiles
) {
    int tileIdx = get_group_id(0);
    if (tileIdx >= numTiles) return;

    int localX = get_local_id(0);
    int localY = get_local_id(1);
    int linearIdx = localY * 32 + localX;

    // Local memory for tile processing
    __local float2 refData[32][32];
    __local float2 tgtData[32][32];
    __local float realResult[32][32];

    // For peak finding
    __local float rowMax[32];
    __local int rowMaxX[32];
    __local int rowMaxDistSq[32];  // Distance to center for tie-breaking
    __local int peakY, peakX;
    __local float peakVal;

    // Additional arrays for peak finding reduction
    __local float rowData[32][32];
    __local int rowIdx[32][32];
    __local int rowDistSq[32][32];  // Distance to center for tie-breaking
    __local float colData[32];
    __local int colIdxY[32];
    __local int colIdxX[32];
    __local int colDistSq[32];  // Distance to center for tie-breaking

    // Load tiles with Hann window
    int offset = tileIdx * 32 * 32 + linearIdx;

    // Compute Hann window value for this position
    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * localX / 31.0f));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * localY / 31.0f));
    float window = hannX * hannY;

    refData[localY][localX] = (float2)(refTiles[offset] * window, 0.0f);
    tgtData[localY][localX] = (float2)(targetTiles[offset] * window, 0.0f);
    barrier(CLK_LOCAL_MEM_FENCE);

    // 2D FFT of both tiles (direction = -1 for forward FFT)
    fft2d_32x32(refData, localX, localY, -1);
    fft2d_32x32(tgtData, localX, localY, -1);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Cross-power spectrum: match Java implementation exactly
    float2 ref = refData[localY][localX];
    float2 tgt = tgtData[localY][localX];

    // Java code uses:
    // crossR = refR * defR + refI * defI
    // crossI = refI * defR - refR * defI  (note: this is negated from standard conj(ref)*tgt)
    float2 cross;
    cross.x = ref.x * tgt.x + ref.y * tgt.y;
    cross.y = ref.y * tgt.x - ref.x * tgt.y;

    // Normalize by magnitude
    float mag = sqrt(cross.x * cross.x + cross.y * cross.y);
    if (mag > 1e-10f) {
        refData[localY][localX] = cross / mag;
    } else {
        refData[localY][localX] = (float2)(0.0f, 0.0f);
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Inverse 2D FFT (direction = +1)
    fft2d_32x32(refData, localX, localY, +1);

    // Scale by 1/N^2 for inverse FFT
    refData[localY][localX] = refData[localY][localX] / (32.0f * 32.0f);
    barrier(CLK_LOCAL_MEM_FENCE);

    // FFT shift: move zero frequency to center
    // Copy to realResult first, then do the shift
    int halfSize = 16;
    float myVal = refData[localY][localX].x;  // Take real part
    barrier(CLK_LOCAL_MEM_FENCE);

    // Compute destination coordinates (same as CPU fftShift)
    int dstY = (localY < halfSize) ? localY + halfSize : localY - halfSize;
    int dstX = (localX < halfSize) ? localX + halfSize : localX - halfSize;
    realResult[dstY][dstX] = myVal;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Find peak with tie-breaking (prefer peak closer to center)
    find_peak_32x32(realResult, rowMax, rowMaxX, rowMaxDistSq, &peakY, &peakX, &peakVal,
                    rowData, rowIdx, rowDistSq, colData, colIdxY, colIdxX, colDistSq, localX, localY);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Subpixel refinement and write result (only thread 0,0)
    if (localX == 0 && localY == 0) {
        float2 subpixel = subpixel_fit_32(realResult, peakY, peakX);

        float center = 16.0f;
        float dy = (float)peakY - center + subpixel.y;
        float dx = (float)peakX - center + subpixel.x;

        results[tileIdx * 2] = -dx;
        results[tileIdx * 2 + 1] = -dy;
    }
}

// ============================================================================
// Global memory FFT kernels for larger tile sizes (64x64, 128x128)
// These use multiple kernel invocations but avoid shared memory limits
// ============================================================================

// Generic bit-reverse for power-of-2 sizes
inline int bitrev(int n, int bits) {
    int r = 0;
    for (int i = 0; i < bits; i++) {
        r = (r << 1) | ((n >> i) & 1);
    }
    return r;
}

// 1D FFT row kernel using local memory for a single row
// Global size: (tileSize, numTiles * tileSize) - one thread per element
// Local size: (tileSize, 1) - one work-group per row
__kernel void fft_rows(
    __global float2* data,           // [numTiles * tileSize * tileSize]
    const int tileSize,
    const int logTileSize,
    const int direction              // -1 forward, +1 inverse
) {
    int col = get_local_id(0);
    int rowGlobal = get_global_id(1);  // Which row across all tiles

    __local float2 row[128];  // Max supported tile size

    // Load row into local memory
    int baseIdx = rowGlobal * tileSize;
    row[col] = data[baseIdx + col];
    barrier(CLK_LOCAL_MEM_FENCE);

    // Bit-reversal permutation
    int rev = bitrev(col, logTileSize);
    if (col < rev) {
        float2 temp = row[col];
        row[col] = row[rev];
        row[rev] = temp;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Butterfly stages
    float sign = (float)direction;
    for (int stage = 1; stage <= logTileSize; stage++) {
        int m = 1 << stage;        // Butterfly group size
        int mHalf = m >> 1;        // Butterfly pair distance

        int group = col / m;       // Which group
        int pos = col % m;         // Position within group

        if (pos < mHalf) {
            int i = group * m + pos;
            int j = i + mHalf;

            float angle = sign * M_PI_F * pos / (float)mHalf;
            float2 w = (float2)(cos(angle), sin(angle));

            float2 a = row[i];
            float2 b = cmul(w, row[j]);
            row[i] = a + b;
            row[j] = a - b;
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Write back to global memory
    data[baseIdx + col] = row[col];
}

// Transpose kernel for 2D FFT
// Each work-item copies one element
__kernel void transpose(
    __global const float2* src,
    __global float2* dst,
    const int tileSize,
    const int numTiles
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (x >= tileSize || y >= tileSize || tileIdx >= numTiles) return;

    int tileOffset = tileIdx * tileSize * tileSize;
    int srcIdx = tileOffset + y * tileSize + x;
    int dstIdx = tileOffset + x * tileSize + y;

    dst[dstIdx] = src[srcIdx];
}

// Apply Hann window and convert to complex
__kernel void apply_hann_window(
    __global const float* input,     // Real input tiles
    __global float2* output,         // Complex output
    const int tileSize,
    const int numTiles
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (x >= tileSize || y >= tileSize || tileIdx >= numTiles) return;

    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * x / (float)(tileSize - 1)));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * y / (float)(tileSize - 1)));
    float window = hannX * hannY;

    int idx = tileIdx * tileSize * tileSize + y * tileSize + x;
    output[idx] = (float2)(input[idx] * window, 0.0f);
}

// Cross-power spectrum kernel
__kernel void cross_power_spectrum(
    __global float2* ref,            // Reference FFT (will be overwritten with result)
    __global const float2* tgt,      // Target FFT
    const int tileSize,
    const int numTiles
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (x >= tileSize || y >= tileSize || tileIdx >= numTiles) return;

    int idx = tileIdx * tileSize * tileSize + y * tileSize + x;

    float2 r = ref[idx];
    float2 t = tgt[idx];

    // Match Java implementation
    float2 cross;
    cross.x = r.x * t.x + r.y * t.y;
    cross.y = r.y * t.x - r.x * t.y;

    float mag = sqrt(cross.x * cross.x + cross.y * cross.y);
    if (mag > 1e-10f) {
        ref[idx] = cross / mag;
    } else {
        ref[idx] = (float2)(0.0f, 0.0f);
    }
}

// Scale by 1/N^2 for inverse FFT
__kernel void scale_ifft(
    __global float2* data,
    const int tileSize,
    const int numTiles
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (x >= tileSize || y >= tileSize || tileIdx >= numTiles) return;

    int idx = tileIdx * tileSize * tileSize + y * tileSize + x;
    float scale = 1.0f / (float)(tileSize * tileSize);
    data[idx] = data[idx] * scale;
}

// FFT shift and extract real part
__kernel void fft_shift_real(
    __global const float2* input,
    __global float* output,
    const int tileSize,
    const int numTiles
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (x >= tileSize || y >= tileSize || tileIdx >= numTiles) return;

    int halfSize = tileSize / 2;
    int srcY = (y < halfSize) ? y + halfSize : y - halfSize;
    int srcX = (x < halfSize) ? x + halfSize : x - halfSize;

    int tileOffset = tileIdx * tileSize * tileSize;
    int srcIdx = tileOffset + srcY * tileSize + srcX;
    int dstIdx = tileOffset + y * tileSize + x;

    output[dstIdx] = input[srcIdx].x;
}

// Find peak in each tile using parallel reduction
// Work-group processes one tile
// Tie-breaking: when values are equal, prefer peak closer to center (matches CPU behavior)
__kernel void find_peaks(
    __global const float* data,      // [numTiles * tileSize * tileSize]
    __global float* results,         // [numTiles * 2] (dx, dy)
    const int tileSize,
    const int numTiles
) {
    int tileIdx = get_group_id(0);
    if (tileIdx >= numTiles) return;

    int tid = get_local_id(0);
    int localSize = get_local_size(0);

    __local float maxVals[256];
    __local int maxIdxs[256];
    __local int maxDistSqs[256];  // Distance squared to center for tie-breaking

    int tileOffset = tileIdx * tileSize * tileSize;
    int tileElements = tileSize * tileSize;
    int centerX = tileSize / 2;
    int centerY = tileSize / 2;

    // Each thread finds max among its assigned elements
    float localMax = -1e30f;
    int localIdx = 0;
    int localDistSq = 2147483647;  // INT_MAX

    for (int i = tid; i < tileElements; i += localSize) {
        float val = data[tileOffset + i];
        int y = i / tileSize;
        int x = i % tileSize;
        int dy = y - centerY;
        int dx = x - centerX;
        int distSq = dy * dy + dx * dx;

        // Tie-breaking: prefer peak closer to center
        if (val > localMax || (val == localMax && distSq < localDistSq)) {
            localMax = val;
            localIdx = i;
            localDistSq = distSq;
        }
    }

    maxVals[tid] = localMax;
    maxIdxs[tid] = localIdx;
    maxDistSqs[tid] = localDistSq;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Parallel reduction with tie-breaking
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (tid < stride) {
            float valA = maxVals[tid];
            float valB = maxVals[tid + stride];
            int distA = maxDistSqs[tid];
            int distB = maxDistSqs[tid + stride];

            // Tie-breaking: prefer peak closer to center
            if (valB > valA || (valB == valA && distB < distA)) {
                maxVals[tid] = valB;
                maxIdxs[tid] = maxIdxs[tid + stride];
                maxDistSqs[tid] = distB;
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Thread 0 computes final result with subpixel refinement
    if (tid == 0) {
        int peakIdx = maxIdxs[0];
        int peakY = peakIdx / tileSize;
        int peakX = peakIdx % tileSize;

        // Subpixel Gaussian fit
        float offsetX = 0.0f;
        float offsetY = 0.0f;

        if (peakX > 0 && peakX < tileSize - 1 && peakY > 0 && peakY < tileSize - 1) {
            float c = fmax(data[tileOffset + peakY * tileSize + peakX], 1e-10f);
            float l = fmax(data[tileOffset + peakY * tileSize + peakX - 1], 1e-10f);
            float r = fmax(data[tileOffset + peakY * tileSize + peakX + 1], 1e-10f);
            float t = fmax(data[tileOffset + (peakY - 1) * tileSize + peakX], 1e-10f);
            float b = fmax(data[tileOffset + (peakY + 1) * tileSize + peakX], 1e-10f);

            float logC = log(c);
            float logL = log(l);
            float logR = log(r);
            float logT = log(t);
            float logB = log(b);

            float denomX = 2.0f * logC - logL - logR;
            float denomY = 2.0f * logC - logT - logB;

            if (fabs(denomX) > 1e-10f) {
                offsetX = 0.5f * (logL - logR) / denomX;
                offsetX = clamp(offsetX, -1.0f, 1.0f);
            }
            if (fabs(denomY) > 1e-10f) {
                offsetY = 0.5f * (logT - logB) / denomY;
                offsetY = clamp(offsetY, -1.0f, 1.0f);
            }
        }

        float center = (float)tileSize / 2.0f;
        float dy = (float)peakY - center + offsetY;
        float dx = (float)peakX - center + offsetX;

        results[tileIdx * 2] = -dx;
        results[tileIdx * 2 + 1] = -dy;
    }
}
