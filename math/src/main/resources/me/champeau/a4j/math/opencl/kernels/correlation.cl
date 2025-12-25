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

// Pre-computed twiddle factors for 32-point FFT
// twiddle32[k] = (cos(-π*k/16), sin(-π*k/16)) for forward FFT
// For inverse FFT, use (twiddle32[k].x, -twiddle32[k].y)
__constant float2 twiddle32[16] = {
    (float2)(1.0f, 0.0f),                          // k=0:  cos(0), sin(0)
    (float2)(0.98078528040323043f, -0.19509032201612825f),  // k=1:  cos(-π/16), sin(-π/16)
    (float2)(0.92387953251128674f, -0.38268343236508978f),  // k=2:  cos(-π/8), sin(-π/8)
    (float2)(0.83146961230254524f, -0.55557023301960218f),  // k=3:  cos(-3π/16), sin(-3π/16)
    (float2)(0.70710678118654752f, -0.70710678118654752f),  // k=4:  cos(-π/4), sin(-π/4)
    (float2)(0.55557023301960218f, -0.83146961230254524f),  // k=5:  cos(-5π/16), sin(-5π/16)
    (float2)(0.38268343236508978f, -0.92387953251128674f),  // k=6:  cos(-3π/8), sin(-3π/8)
    (float2)(0.19509032201612825f, -0.98078528040323043f),  // k=7:  cos(-7π/16), sin(-7π/16)
    (float2)(0.0f, -1.0f),                         // k=8:  cos(-π/2), sin(-π/2)
    (float2)(-0.19509032201612825f, -0.98078528040323043f), // k=9:  cos(-9π/16), sin(-9π/16)
    (float2)(-0.38268343236508978f, -0.92387953251128674f), // k=10: cos(-5π/8), sin(-5π/8)
    (float2)(-0.55557023301960218f, -0.83146961230254524f), // k=11: cos(-11π/16), sin(-11π/16)
    (float2)(-0.70710678118654752f, -0.70710678118654752f), // k=12: cos(-3π/4), sin(-3π/4)
    (float2)(-0.83146961230254524f, -0.55557023301960218f), // k=13: cos(-13π/16), sin(-13π/16)
    (float2)(-0.92387953251128674f, -0.38268343236508978f), // k=14: cos(-7π/8), sin(-7π/8)
    (float2)(-0.98078528040323043f, -0.19509032201612825f)  // k=15: cos(-15π/16), sin(-15π/16)
};

// Get twiddle factor with direction support
// For forward FFT (direction=-1): returns twiddle32[k]
// For inverse FFT (direction=+1): returns conjugate (cos, -sin) = (cos, sin) with negated sin
inline float2 get_twiddle(int k, int direction) {
    float2 w = twiddle32[k];
    // For inverse FFT, we need positive angles, which is the conjugate
    return (direction < 0) ? w : (float2)(w.x, -w.y);
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
    // Twiddle indices: pos * 8 (stride 8 in table)
    {
        int group = tid >> 2;     // which group of 4 (0-7)
        int pos = tid & 3;        // position in group (0-3)
        int base = group * 4;

        if (pos < 2) {
            int i = base + pos;
            int j = base + pos + 2;
            float2 w = get_twiddle(pos * 8, direction);

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 3: butterflies of size 8 (stride=4)
    // Twiddle indices: pos * 4 (stride 4 in table)
    {
        int group = tid >> 3;     // which group of 8 (0-3)
        int pos = tid & 7;        // position in group (0-7)
        int base = group * 8;

        if (pos < 4) {
            int i = base + pos;
            int j = base + pos + 4;
            float2 w = get_twiddle(pos * 4, direction);

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 4: butterflies of size 16 (stride=8)
    // Twiddle indices: pos * 2 (stride 2 in table)
    {
        int group = tid >> 4;     // which group of 16 (0-1)
        int pos = tid & 15;       // position in group (0-15)
        int base = group * 16;

        if (pos < 8) {
            int i = base + pos;
            int j = base + pos + 8;
            float2 w = get_twiddle(pos * 2, direction);

            float2 a = data[i];
            float2 b = cmul(w, data[j]);
            data[i] = a + b;
            data[j] = a - b;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Stage 5: butterflies of size 32 (stride=16)
    // Twiddle indices: tid * 1 (stride 1 in table)
    {
        if (tid < 16) {
            int i = tid;
            int j = tid + 16;
            float2 w = get_twiddle(tid, direction);

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

// Compute confidence for 32x32 kernel
// Must be called by thread (0,0) after peak finding
float compute_confidence_32(__local float data[32][32], int peakY, int peakX, float peakVal) {
    // Compute mean
    float sum = 0.0f;
    for (int y = 0; y < 32; y++) {
        for (int x = 0; x < 32; x++) {
            sum += data[y][x];
        }
    }
    float mean = sum / 1024.0f;

    // Find second max excluding 3x3 around peak
    float secondMax = -1e30f;
    for (int y = 0; y < 32; y++) {
        for (int x = 0; x < 32; x++) {
            if (abs(y - peakY) > 1 || abs(x - peakX) > 1) {
                secondMax = fmax(secondMax, data[y][x]);
            }
        }
    }

    // Compute PSR and confidence (matches CPU)
    float psr = (peakVal - mean) / (secondMax - mean + 1e-10f);
    float confidence = 1.0f - 1.0f / (1.0f + psr * 0.5f);
    return clamp(confidence, 0.0f, 1.0f);
}

// Main kernel for 32x32 tiles
// Work-group size must be 32x32 (1024 threads)
__kernel void batched_correlation_32(
    __global const float* refTiles,      // [numTiles * 32 * 32]
    __global const float* targetTiles,   // [numTiles * 32 * 32]
    __global float* results,             // [numTiles * 3] (dx, dy, confidence per tile)
    const int numTiles,
    const int normalize                  // 1 for phase correlation, 0 for cross-correlation
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

    // Optionally normalize by magnitude (phase correlation vs cross-correlation)
    if (normalize) {
        float mag = sqrt(cross.x * cross.x + cross.y * cross.y);
        if (mag > 1e-10f) {
            refData[localY][localX] = cross / mag;
        } else {
            refData[localY][localX] = (float2)(0.0f, 0.0f);
        }
    } else {
        refData[localY][localX] = cross;
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

    // Subpixel refinement, confidence, and write result (only thread 0,0)
    if (localX == 0 && localY == 0) {
        float2 subpixel = subpixel_fit_32(realResult, peakY, peakX);

        float center = 16.0f;
        float dy = (float)peakY - center + subpixel.y;
        float dx = (float)peakX - center + subpixel.x;

        // Compute confidence (PSR-based, matches CPU)
        float confidence = compute_confidence_32(realResult, peakY, peakX, peakVal);

        results[tileIdx * 3] = -dx;
        results[tileIdx * 3 + 1] = -dy;
        results[tileIdx * 3 + 2] = confidence;
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

// Transpose kernel for 2D FFT using local memory blocking
// Uses 16x16 work-groups for coalesced memory access
#define TRANSPOSE_BLOCK_SIZE 16

__kernel void transpose(
    __global const float2* src,
    __global float2* dst,
    const int tileSize,
    const int numTiles
) {
    // Local memory with +1 padding to avoid bank conflicts
    __local float2 block[TRANSPOSE_BLOCK_SIZE][TRANSPOSE_BLOCK_SIZE + 1];

    int localX = get_local_id(0);
    int localY = get_local_id(1);
    int blockX = get_group_id(0) * TRANSPOSE_BLOCK_SIZE;
    int blockY = get_group_id(1) * TRANSPOSE_BLOCK_SIZE;
    int tileIdx = get_group_id(2);

    if (tileIdx >= numTiles) return;

    int tileOffset = tileIdx * tileSize * tileSize;

    // Coalesced read: threads in a row read consecutive memory
    int srcX = blockX + localX;
    int srcY = blockY + localY;
    if (srcX < tileSize && srcY < tileSize) {
        block[localY][localX] = src[tileOffset + srcY * tileSize + srcX];
    }

    barrier(CLK_LOCAL_MEM_FENCE);

    // Coalesced write: threads in a row write consecutive memory (transposed block position)
    int dstX = blockY + localX;  // Note: blockY, not blockX
    int dstY = blockX + localY;  // Note: blockX, not blockY
    if (dstX < tileSize && dstY < tileSize) {
        dst[tileOffset + dstY * tileSize + dstX] = block[localX][localY];  // Swapped indices
    }
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
    const int numTiles,
    const int normalize              // 1 for phase correlation, 0 for cross-correlation
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

    // Optionally normalize by magnitude (phase correlation vs cross-correlation)
    if (normalize) {
        float mag = sqrt(cross.x * cross.x + cross.y * cross.y);
        if (mag > 1e-10f) {
            ref[idx] = cross / mag;
        } else {
            ref[idx] = (float2)(0.0f, 0.0f);
        }
    } else {
        ref[idx] = cross;
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
// Also computes confidence based on Peak-to-Sidelobe Ratio (PSR)
__kernel void find_peaks(
    __global const float* data,      // [numTiles * tileSize * tileSize]
    __global float* results,         // [numTiles * 3] (dx, dy, confidence)
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
    __local float sumVals[256];   // For computing mean
    __local float secondMaxVals[256];  // For computing second maximum

    int tileOffset = tileIdx * tileSize * tileSize;
    int tileElements = tileSize * tileSize;
    int centerX = tileSize / 2;
    int centerY = tileSize / 2;

    // Each thread finds max, sum, and second max among its assigned elements
    float localMax = -1e30f;
    int localIdx = 0;
    int localDistSq = 2147483647;  // INT_MAX
    float localSum = 0.0f;
    float localSecondMax = -1e30f;
    int localMaxX = 0, localMaxY = 0;

    // First pass: find local max and sum
    for (int i = tid; i < tileElements; i += localSize) {
        float val = data[tileOffset + i];
        int y = i / tileSize;
        int x = i % tileSize;
        int dy = y - centerY;
        int dx = x - centerX;
        int distSq = dy * dy + dx * dx;

        localSum += val;

        // Tie-breaking: prefer peak closer to center
        if (val > localMax || (val == localMax && distSq < localDistSq)) {
            // Before updating max, save old max as potential second max
            if (localMax > localSecondMax) {
                localSecondMax = localMax;
            }
            localMax = val;
            localIdx = i;
            localDistSq = distSq;
            localMaxX = x;
            localMaxY = y;
        } else if (val > localSecondMax) {
            localSecondMax = val;
        }
    }

    maxVals[tid] = localMax;
    maxIdxs[tid] = localIdx;
    maxDistSqs[tid] = localDistSq;
    sumVals[tid] = localSum;
    secondMaxVals[tid] = localSecondMax;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Parallel reduction for max (with tie-breaking) and sum
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (tid < stride) {
            float valA = maxVals[tid];
            float valB = maxVals[tid + stride];
            int distA = maxDistSqs[tid];
            int distB = maxDistSqs[tid + stride];

            // Sum reduction
            sumVals[tid] += sumVals[tid + stride];

            // Track second max during reduction
            float secA = secondMaxVals[tid];
            float secB = secondMaxVals[tid + stride];
            float newSecondMax = fmax(secA, secB);

            // Tie-breaking: prefer peak closer to center
            if (valB > valA || (valB == valA && distB < distA)) {
                // B becomes new max, A might be second max
                newSecondMax = fmax(newSecondMax, valA);
                maxVals[tid] = valB;
                maxIdxs[tid] = maxIdxs[tid + stride];
                maxDistSqs[tid] = distB;
            } else {
                // A stays max, B might be second max
                newSecondMax = fmax(newSecondMax, valB);
            }
            secondMaxVals[tid] = newSecondMax;
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Thread 0 computes final result with subpixel refinement and confidence
    if (tid == 0) {
        int peakIdx = maxIdxs[0];
        int peakY = peakIdx / tileSize;
        int peakX = peakIdx % tileSize;
        float peakVal = maxVals[0];
        float mean = sumVals[0] / (float)tileElements;

        // Find true second max excluding 3x3 around peak (like CPU does)
        float secondMax = -1e30f;
        for (int i = 0; i < tileElements; i++) {
            int y = i / tileSize;
            int x = i % tileSize;
            // Exclude 3x3 neighborhood around peak
            if (abs(y - peakY) > 1 || abs(x - peakX) > 1) {
                float val = data[tileOffset + i];
                secondMax = fmax(secondMax, val);
            }
        }

        // Compute confidence using Peak-to-Sidelobe Ratio (matches CPU)
        float psr = (peakVal - mean) / (secondMax - mean + 1e-10f);
        float confidence = 1.0f - 1.0f / (1.0f + psr * 0.5f);
        confidence = clamp(confidence, 0.0f, 1.0f);

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

        results[tileIdx * 3] = -dx;
        results[tileIdx * 3 + 1] = -dy;
        results[tileIdx * 3 + 2] = confidence;
    }
}

// ============================================================================
// NCC (Normalized Cross-Correlation) kernels
// NCC normalizes by local variance, producing values in [-1, 1]
// ============================================================================

// Compute tile statistics for NCC: mean and sum of squared deviations
// stats[tileIdx * 2] = sqrt(sumSqRef * sumSqTarget) (normalization factor)
// Uses parallel reduction with 256 threads per tile
__kernel void compute_tile_stats(
    __global const float* refTiles,      // [numTiles * tileSize * tileSize]
    __global const float* targetTiles,   // [numTiles * tileSize * tileSize]
    __global float* stats,               // [numTiles * 2] = {normFactor, unused}
    const int tileSize,
    const int numTiles
) {
    int tileIdx = get_group_id(0);
    if (tileIdx >= numTiles) return;

    int tid = get_local_id(0);
    int localSize = get_local_size(0);

    __local float sumRef[256];
    __local float sumTarget[256];
    __local float sumSqRef[256];
    __local float sumSqTarget[256];

    int tileOffset = tileIdx * tileSize * tileSize;
    int tileElements = tileSize * tileSize;

    // Each thread accumulates its subset of elements
    float localSumRef = 0.0f;
    float localSumTarget = 0.0f;
    for (int i = tid; i < tileElements; i += localSize) {
        localSumRef += refTiles[tileOffset + i];
        localSumTarget += targetTiles[tileOffset + i];
    }

    sumRef[tid] = localSumRef;
    sumTarget[tid] = localSumTarget;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduction for sums
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (tid < stride) {
            sumRef[tid] += sumRef[tid + stride];
            sumTarget[tid] += sumTarget[tid + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Compute means
    __local float meanRef;
    __local float meanTarget;
    if (tid == 0) {
        meanRef = sumRef[0] / (float)tileElements;
        meanTarget = sumTarget[0] / (float)tileElements;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Now compute sum of squared deviations
    float localSumSqRef = 0.0f;
    float localSumSqTarget = 0.0f;
    for (int i = tid; i < tileElements; i += localSize) {
        float r = refTiles[tileOffset + i] - meanRef;
        float t = targetTiles[tileOffset + i] - meanTarget;
        localSumSqRef += r * r;
        localSumSqTarget += t * t;
    }

    sumSqRef[tid] = localSumSqRef;
    sumSqTarget[tid] = localSumSqTarget;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduction for sum of squares
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (tid < stride) {
            sumSqRef[tid] += sumSqRef[tid + stride];
            sumSqTarget[tid] += sumSqTarget[tid + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Store normalization factor and both means
    if (tid == 0) {
        float normFactor = sqrt(sumSqRef[0] * sumSqTarget[0]);
        stats[tileIdx * 3] = (normFactor > 1e-10f) ? normFactor : 1e-10f;
        stats[tileIdx * 3 + 1] = meanRef;
        stats[tileIdx * 3 + 2] = meanTarget;
    }
}

// Apply zero-mean and Hann window for NCC
// Uses precomputed means from compute_tile_stats
// isTarget: 0 for ref tile, 1 for target tile
__kernel void zero_mean_and_hann(
    __global const float* input,
    __global float2* output,
    __global const float* stats,         // [numTiles * 3]: normFactor, meanRef, meanTarget
    const int tileSize,
    const int numTiles,
    const int isTarget                   // 0 for ref, 1 for target
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (x >= tileSize || y >= tileSize || tileIdx >= numTiles) return;

    int idx = tileIdx * tileSize * tileSize + y * tileSize + x;

    // Read precomputed mean from stats buffer
    float mean = isTarget ? stats[tileIdx * 3 + 2] : stats[tileIdx * 3 + 1];

    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * x / (float)(tileSize - 1)));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * y / (float)(tileSize - 1)));
    float window = hannX * hannY;

    float val = input[idx] - mean;
    output[idx] = (float2)(val * window, 0.0f);
}

// Find peaks with NCC normalization
// Uses the precomputed normalization factor from compute_tile_stats
__kernel void find_peaks_ncc(
    __global const float* data,          // [numTiles * tileSize * tileSize] cross-correlation result
    __global const float* stats,         // [numTiles * 3] containing normFactor, meanRef, meanTarget
    __global float* results,             // [numTiles * 3] (dx, dy, confidence)
    const int tileSize,
    const int numTiles
) {
    int tileIdx = get_group_id(0);
    if (tileIdx >= numTiles) return;

    int tid = get_local_id(0);
    int localSize = get_local_size(0);

    __local float maxVals[256];
    __local int maxIdxs[256];
    __local int maxDistSqs[256];

    int tileOffset = tileIdx * tileSize * tileSize;
    int tileElements = tileSize * tileSize;
    int centerX = tileSize / 2;
    int centerY = tileSize / 2;

    // Each thread finds max among its assigned elements
    float localMax = -1e30f;
    int localIdx = 0;
    int localDistSq = 2147483647;

    for (int i = tid; i < tileElements; i += localSize) {
        float val = data[tileOffset + i];
        int y = i / tileSize;
        int x = i % tileSize;
        int dy = y - centerY;
        int dx = x - centerX;
        int distSq = dy * dy + dx * dx;

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

    // Parallel reduction for max with tie-breaking
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (tid < stride) {
            float valA = maxVals[tid];
            float valB = maxVals[tid + stride];
            int distA = maxDistSqs[tid];
            int distB = maxDistSqs[tid + stride];

            if (valB > valA || (valB == valA && distB < distA)) {
                maxVals[tid] = valB;
                maxIdxs[tid] = maxIdxs[tid + stride];
                maxDistSqs[tid] = distB;
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    // Thread 0 computes final result
    if (tid == 0) {
        int peakIdx = maxIdxs[0];
        int peakY = peakIdx / tileSize;
        int peakX = peakIdx % tileSize;
        float peakVal = maxVals[0];

        // NCC confidence = peak value / normalization factor
        float normFactor = stats[tileIdx * 3];
        float nccVal = peakVal / normFactor;
        float confidence = clamp(nccVal, 0.0f, 1.0f);

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

        results[tileIdx * 3] = -dx;
        results[tileIdx * 3 + 1] = -dy;
        results[tileIdx * 3 + 2] = confidence;
    }
}

// NCC kernel for 32x32 tiles
// Work-group size must be 32x32 (1024 threads)
__kernel void batched_ncc_32(
    __global const float* refTiles,      // [numTiles * 32 * 32]
    __global const float* targetTiles,   // [numTiles * 32 * 32]
    __global float* results,             // [numTiles * 3] (dx, dy, confidence per tile)
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

    // For statistics computation
    __local float partialSumRef[32];
    __local float partialSumTgt[32];
    __local float meanRef;
    __local float meanTgt;
    __local float partialSumSqRef[32];
    __local float partialSumSqTgt[32];
    __local float normFactor;

    // For peak finding
    __local float rowMax[32];
    __local int rowMaxX[32];
    __local int rowMaxDistSq[32];
    __local int peakY, peakX;
    __local float peakVal;

    __local float rowData[32][32];
    __local int rowIdx[32][32];
    __local int rowDistSq[32][32];
    __local float colData[32];
    __local int colIdxY[32];
    __local int colIdxX[32];
    __local int colDistSq[32];

    // Load tiles
    int offset = tileIdx * 32 * 32 + linearIdx;
    float refVal = refTiles[offset];
    float tgtVal = targetTiles[offset];

    // Step 1: Compute means using parallel reduction
    // Each row computes partial sum, then reduce across rows
    __local float rowSumRef[32];
    __local float rowSumTgt[32];

    // Warp-level sum within row using shared memory
    rowData[localY][localX] = refVal;
    rowIdx[localY][localX] = (int)(tgtVal * 1000);  // Reuse as temp storage
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduce within row
    for (int stride = 16; stride > 0; stride >>= 1) {
        if (localX < stride) {
            rowData[localY][localX] += rowData[localY][localX + stride];
            rowIdx[localY][localX] += rowIdx[localY][localX + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (localX == 0) {
        rowSumRef[localY] = rowData[localY][0];
        rowSumTgt[localY] = (float)rowIdx[localY][0] / 1000.0f;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduce across rows (use thread column 0)
    if (localY == 0) {
        partialSumRef[localX] = rowSumRef[localX];
        partialSumTgt[localX] = rowSumTgt[localX];
        barrier(CLK_LOCAL_MEM_FENCE);

        for (int stride = 16; stride > 0; stride >>= 1) {
            if (localX < stride) {
                partialSumRef[localX] += partialSumRef[localX + stride];
                partialSumTgt[localX] += partialSumTgt[localX + stride];
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }

        if (localX == 0) {
            meanRef = partialSumRef[0] / 1024.0f;
            meanTgt = partialSumTgt[0] / 1024.0f;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reload original values (we corrupted rowData/rowIdx)
    refVal = refTiles[offset];
    tgtVal = targetTiles[offset];

    // Step 2: Compute sum of squared deviations
    float devRef = refVal - meanRef;
    float devTgt = tgtVal - meanTgt;
    float sqRef = devRef * devRef;
    float sqTgt = devTgt * devTgt;

    rowData[localY][localX] = sqRef;
    rowDistSq[localY][localX] = (int)(sqTgt * 1000);  // Reuse as temp
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduce sum of squares within row
    for (int stride = 16; stride > 0; stride >>= 1) {
        if (localX < stride) {
            rowData[localY][localX] += rowData[localY][localX + stride];
            rowDistSq[localY][localX] += rowDistSq[localY][localX + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (localX == 0) {
        partialSumSqRef[localY] = rowData[localY][0];
        partialSumSqTgt[localY] = (float)rowDistSq[localY][0] / 1000.0f;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduce across rows
    if (localY == 0) {
        colData[localX] = partialSumSqRef[localX];
        colDistSq[localX] = (int)(partialSumSqTgt[localX] * 1000);
        barrier(CLK_LOCAL_MEM_FENCE);

        for (int stride = 16; stride > 0; stride >>= 1) {
            if (localX < stride) {
                colData[localX] += colData[localX + stride];
                colDistSq[localX] += colDistSq[localX + stride];
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }

        if (localX == 0) {
            float sumSqRef = colData[0];
            float sumSqTgt = (float)colDistSq[0] / 1000.0f;
            normFactor = sqrt(sumSqRef * sumSqTgt);
            if (normFactor < 1e-10f) {
                normFactor = 1e-10f;
            }
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 3: Apply zero-mean and Hann window
    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * localX / 31.0f));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * localY / 31.0f));
    float window = hannX * hannY;

    refData[localY][localX] = (float2)(devRef * window, 0.0f);
    tgtData[localY][localX] = (float2)(devTgt * window, 0.0f);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 4: 2D FFT of both tiles
    fft2d_32x32(refData, localX, localY, -1);
    fft2d_32x32(tgtData, localX, localY, -1);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 5: Cross-power spectrum (no normalization for NCC)
    float2 ref = refData[localY][localX];
    float2 tgt = tgtData[localY][localX];

    float2 cross;
    cross.x = ref.x * tgt.x + ref.y * tgt.y;
    cross.y = ref.y * tgt.x - ref.x * tgt.y;
    refData[localY][localX] = cross;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 6: Inverse 2D FFT
    fft2d_32x32(refData, localX, localY, +1);

    // Scale by 1/N^2
    refData[localY][localX] = refData[localY][localX] / (32.0f * 32.0f);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 7: FFT shift
    int halfSize = 16;
    float myVal = refData[localY][localX].x;
    barrier(CLK_LOCAL_MEM_FENCE);

    int dstY = (localY < halfSize) ? localY + halfSize : localY - halfSize;
    int dstX = (localX < halfSize) ? localX + halfSize : localX - halfSize;
    realResult[dstY][dstX] = myVal;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 8: Find peak
    find_peak_32x32(realResult, rowMax, rowMaxX, rowMaxDistSq, &peakY, &peakX, &peakVal,
                    rowData, rowIdx, rowDistSq, colData, colIdxY, colIdxX, colDistSq, localX, localY);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 9: Subpixel refinement and write result
    if (localX == 0 && localY == 0) {
        float2 subpixel = subpixel_fit_32(realResult, peakY, peakX);

        float center = 16.0f;
        float dy = (float)peakY - center + subpixel.y;
        float dx = (float)peakX - center + subpixel.x;

        // NCC confidence = peak / normFactor
        float nccVal = peakVal / normFactor;
        float confidence = clamp(nccVal, 0.0f, 1.0f);

        results[tileIdx * 3] = -dx;
        results[tileIdx * 3 + 1] = -dy;
        results[tileIdx * 3 + 2] = confidence;
    }
}

// ============================================================================
// Tile Extraction from GPU-Resident Images
// These kernels extract tiles directly from full images stored on GPU,
// enabling efficient multi-image correlation without CPU-GPU transfers.
// ============================================================================

/**
 * Extracts tiles from GPU-resident images and applies Hann window.
 * Output format matches the input expected by apply_hann_window / fft_rows pipeline.
 *
 * Global work size: (tileSize, tileSize, numTiles)
 *
 * @param refImage      Full reference image [height * width]
 * @param targetImage   Full target image [height * width]
 * @param imageWidth    Width of images in pixels
 * @param imageHeight   Height of images in pixels
 * @param tilePositions Tile center positions [numTiles * 2] as (x, y) pairs
 * @param refTilesOut   Output reference tiles as complex [numTiles * tileSize * tileSize * 2]
 * @param targetTilesOut Output target tiles as complex [numTiles * tileSize * tileSize * 2]
 * @param tileSize      Tile size (64 or 128)
 * @param numTiles      Number of tiles to extract
 */
__kernel void extract_tiles_from_images(
    __global const float* refImage,
    __global const float* targetImage,
    const int imageWidth,
    const int imageHeight,
    __global const int* tilePositions,
    __global float2* refTilesOut,
    __global float2* targetTilesOut,
    const int tileSize,
    const int numTiles
) {
    int localX = get_global_id(0);
    int localY = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (localX >= tileSize || localY >= tileSize || tileIdx >= numTiles) {
        return;
    }

    // Get tile center position
    int centerX = tilePositions[tileIdx * 2];
    int centerY = tilePositions[tileIdx * 2 + 1];

    // Convert to tile corner (top-left)
    int halfTile = tileSize / 2;
    int tileX = centerX - halfTile;
    int tileY = centerY - halfTile;

    // Compute source pixel coordinates
    int srcX = tileX + localX;
    int srcY = tileY + localY;

    // Load pixel values with bounds checking
    float refVal = 0.0f;
    float tgtVal = 0.0f;
    if (srcX >= 0 && srcX < imageWidth && srcY >= 0 && srcY < imageHeight) {
        int srcIdx = srcY * imageWidth + srcX;
        refVal = refImage[srcIdx];
        tgtVal = targetImage[srcIdx];
    }

    // Apply Hann window
    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * localX / (tileSize - 1)));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * localY / (tileSize - 1)));
    float window = hannX * hannY;

    refVal *= window;
    tgtVal *= window;

    // Output index
    int outIdx = tileIdx * tileSize * tileSize + localY * tileSize + localX;

    // Write as complex (real, 0)
    refTilesOut[outIdx] = (float2)(refVal, 0.0f);
    targetTilesOut[outIdx] = (float2)(tgtVal, 0.0f);
}

/**
 * Extracts tiles from GPU-resident images with zero-mean normalization for NCC.
 * This version computes mean on the fly using a two-pass approach.
 *
 * Pass 1: Compute tile means (use compute_tile_stats kernel)
 * Pass 2: Extract with zero-mean and Hann window (this kernel)
 *
 * Global work size: (tileSize, tileSize, numTiles)
 */
__kernel void extract_tiles_zero_mean(
    __global const float* refImage,
    __global const float* targetImage,
    const int imageWidth,
    const int imageHeight,
    __global const int* tilePositions,
    __global const float* refMeans,      // [numTiles] precomputed means
    __global const float* targetMeans,   // [numTiles] precomputed means
    __global float2* refTilesOut,
    __global float2* targetTilesOut,
    const int tileSize,
    const int numTiles
) {
    int localX = get_global_id(0);
    int localY = get_global_id(1);
    int tileIdx = get_global_id(2);

    if (localX >= tileSize || localY >= tileSize || tileIdx >= numTiles) {
        return;
    }

    // Get tile center position
    int centerX = tilePositions[tileIdx * 2];
    int centerY = tilePositions[tileIdx * 2 + 1];

    // Convert to tile corner (top-left)
    int halfTile = tileSize / 2;
    int tileX = centerX - halfTile;
    int tileY = centerY - halfTile;

    // Compute source pixel coordinates
    int srcX = tileX + localX;
    int srcY = tileY + localY;

    // Load pixel values with bounds checking
    float refVal = 0.0f;
    float tgtVal = 0.0f;
    if (srcX >= 0 && srcX < imageWidth && srcY >= 0 && srcY < imageHeight) {
        int srcIdx = srcY * imageWidth + srcX;
        refVal = refImage[srcIdx];
        tgtVal = targetImage[srcIdx];
    }

    // Apply zero-mean
    float refMean = refMeans[tileIdx];
    float tgtMean = targetMeans[tileIdx];
    refVal -= refMean;
    tgtVal -= tgtMean;

    // Apply Hann window
    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * localX / (tileSize - 1)));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * localY / (tileSize - 1)));
    float window = hannX * hannY;

    refVal *= window;
    tgtVal *= window;

    // Output index
    int outIdx = tileIdx * tileSize * tileSize + localY * tileSize + localX;

    // Write as complex (real, 0)
    refTilesOut[outIdx] = (float2)(refVal, 0.0f);
    targetTilesOut[outIdx] = (float2)(tgtVal, 0.0f);
}

/**
 * Computes tile statistics (mean and sum of squared deviations) from GPU-resident images.
 * Used for NCC normalization.
 *
 * Global work size: (256, numTiles)
 * Local work size: (256, 1)
 */
__kernel void compute_tile_stats_from_images(
    __global const float* image,
    const int imageWidth,
    const int imageHeight,
    __global const int* tilePositions,
    __global float* means,         // [numTiles] output means
    __global float* sumSqDevs,     // [numTiles] output sum of squared deviations
    const int tileSize,
    const int numTiles
) {
    int localId = get_local_id(0);
    int tileIdx = get_group_id(1);

    if (tileIdx >= numTiles) return;

    __local float partialSum[256];
    __local float partialSumSq[256];
    __local float tileMean;

    // Get tile center position
    int centerX = tilePositions[tileIdx * 2];
    int centerY = tilePositions[tileIdx * 2 + 1];
    int halfTile = tileSize / 2;
    int tileX = centerX - halfTile;
    int tileY = centerY - halfTile;

    int pixelsPerTile = tileSize * tileSize;

    // Pass 1: Compute sum for mean
    float sum = 0.0f;
    for (int i = localId; i < pixelsPerTile; i += 256) {
        int px = i % tileSize;
        int py = i / tileSize;
        int srcX = tileX + px;
        int srcY = tileY + py;

        if (srcX >= 0 && srcX < imageWidth && srcY >= 0 && srcY < imageHeight) {
            sum += image[srcY * imageWidth + srcX];
        }
    }

    partialSum[localId] = sum;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduce to get total sum
    for (int stride = 128; stride > 0; stride >>= 1) {
        if (localId < stride) {
            partialSum[localId] += partialSum[localId + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (localId == 0) {
        tileMean = partialSum[0] / (float)pixelsPerTile;
        means[tileIdx] = tileMean;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Pass 2: Compute sum of squared deviations
    float sumSq = 0.0f;
    float mean = tileMean;
    for (int i = localId; i < pixelsPerTile; i += 256) {
        int px = i % tileSize;
        int py = i / tileSize;
        int srcX = tileX + px;
        int srcY = tileY + py;

        if (srcX >= 0 && srcX < imageWidth && srcY >= 0 && srcY < imageHeight) {
            float val = image[srcY * imageWidth + srcX] - mean;
            sumSq += val * val;
        }
    }

    partialSumSq[localId] = sumSq;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Reduce
    for (int stride = 128; stride > 0; stride >>= 1) {
        if (localId < stride) {
            partialSumSq[localId] += partialSumSq[localId + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (localId == 0) {
        sumSqDevs[tileIdx] = partialSumSq[0];
    }
}

/**
 * Computes NCC normalization factors from sum of squared deviations.
 * normFactor = sqrt(refSumSq * targetSumSq)
 *
 * Global work size: (numTiles)
 */
__kernel void compute_ncc_norm_factors(
    __global const float* refSumSqDevs,    // [numTiles]
    __global const float* targetSumSqDevs, // [numTiles]
    __global float* statsBuffer,           // [numTiles * 3] - only index 0 used per tile
    const int numTiles
) {
    int tileIdx = get_global_id(0);
    if (tileIdx >= numTiles) return;

    float refSumSq = refSumSqDevs[tileIdx];
    float targetSumSq = targetSumSqDevs[tileIdx];
    float normFactor = sqrt(refSumSq * targetSumSq);

    // Avoid division by zero in find_peaks_ncc
    if (normFactor < 1e-10f) {
        normFactor = 1e-10f;
    }

    statsBuffer[tileIdx * 3] = normFactor;
    statsBuffer[tileIdx * 3 + 1] = 0.0f; // unused
    statsBuffer[tileIdx * 3 + 2] = 0.0f; // unused
}

// ============================================================================
// GPU-Resident Image Correlation Kernels
// These kernels extract tiles directly from full GPU-resident images,
// eliminating the need to transfer tile data between CPU and GPU.
// ============================================================================

/**
 * NCC correlation kernel for 32x32 tiles extracted from GPU-resident images.
 * Each work-group processes one tile pair, extracting tiles directly from
 * the source images and performing NCC correlation.
 *
 * Work-group size must be 32x32 (1024 threads).
 *
 * @param refImage     Full reference image [height * width]
 * @param targetImage  Full target image [height * width]
 * @param imageWidth   Width of images in pixels
 * @param imageHeight  Height of images in pixels
 * @param tilePositions Tile center positions [numTiles * 2] as (x, y) pairs
 * @param results      Output displacements [numTiles * 3] as (dx, dy, confidence)
 * @param numTiles     Number of tiles to process
 */
__kernel void correlate_resident_ncc_32(
    __global const float* refImage,
    __global const float* targetImage,
    const int imageWidth,
    const int imageHeight,
    __global const int* tilePositions,   // [numTiles * 2] = (centerX, centerY) pairs
    __global float* results,             // [numTiles * 3] = (dx, dy, confidence)
    const int numTiles
) {
    int tileIdx = get_group_id(0);
    if (tileIdx >= numTiles) return;

    int localX = get_local_id(0);
    int localY = get_local_id(1);

    // Get tile center position
    int centerX = tilePositions[tileIdx * 2];
    int centerY = tilePositions[tileIdx * 2 + 1];

    // Convert to tile corner (top-left)
    int tileX = centerX - 16;
    int tileY = centerY - 16;

    // Compute source pixel coordinates
    int srcX = tileX + localX;
    int srcY = tileY + localY;

    // Load pixel values with bounds checking
    float refVal = 0.0f;
    float tgtVal = 0.0f;
    if (srcX >= 0 && srcX < imageWidth && srcY >= 0 && srcY < imageHeight) {
        int srcIdx = srcY * imageWidth + srcX;
        refVal = refImage[srcIdx];
        tgtVal = targetImage[srcIdx];
    }

    // === From here, same as batched_ncc_32 ===

    // Local memory for tile processing
    __local float2 refData[32][32];
    __local float2 tgtData[32][32];
    __local float realResult[32][32];

    // For statistics computation
    __local float partialSumRef[32];
    __local float partialSumTgt[32];
    __local float meanRef;
    __local float meanTgt;
    __local float partialSumSqRef[32];
    __local float partialSumSqTgt[32];
    __local float normFactor;

    // For peak finding
    __local float rowMax[32];
    __local int rowMaxX[32];
    __local int rowMaxDistSq[32];
    __local int peakY, peakX;
    __local float peakVal;

    __local float rowData[32][32];
    __local int rowIdx[32][32];
    __local int rowDistSq[32][32];
    __local float colData[32];
    __local int colIdxY[32];
    __local int colIdxX[32];
    __local int colDistSq[32];

    __local float rowSumRef[32];
    __local float rowSumTgt[32];

    // Step 1: Compute means using parallel reduction
    rowData[localY][localX] = refVal;
    rowIdx[localY][localX] = (int)(tgtVal * 1000);
    barrier(CLK_LOCAL_MEM_FENCE);

    for (int stride = 16; stride > 0; stride >>= 1) {
        if (localX < stride) {
            rowData[localY][localX] += rowData[localY][localX + stride];
            rowIdx[localY][localX] += rowIdx[localY][localX + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (localX == 0) {
        rowSumRef[localY] = rowData[localY][0];
        rowSumTgt[localY] = (float)rowIdx[localY][0] / 1000.0f;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    if (localY == 0) {
        partialSumRef[localX] = rowSumRef[localX];
        partialSumTgt[localX] = rowSumTgt[localX];
        barrier(CLK_LOCAL_MEM_FENCE);

        for (int stride = 16; stride > 0; stride >>= 1) {
            if (localX < stride) {
                partialSumRef[localX] += partialSumRef[localX + stride];
                partialSumTgt[localX] += partialSumTgt[localX + stride];
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }

        if (localX == 0) {
            meanRef = partialSumRef[0] / 1024.0f;
            meanTgt = partialSumTgt[0] / 1024.0f;
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 2: Compute sum of squared deviations
    float devRef = refVal - meanRef;
    float devTgt = tgtVal - meanTgt;
    float sqRef = devRef * devRef;
    float sqTgt = devTgt * devTgt;

    rowData[localY][localX] = sqRef;
    rowDistSq[localY][localX] = (int)(sqTgt * 1000);
    barrier(CLK_LOCAL_MEM_FENCE);

    for (int stride = 16; stride > 0; stride >>= 1) {
        if (localX < stride) {
            rowData[localY][localX] += rowData[localY][localX + stride];
            rowDistSq[localY][localX] += rowDistSq[localY][localX + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (localX == 0) {
        partialSumSqRef[localY] = rowData[localY][0];
        partialSumSqTgt[localY] = (float)rowDistSq[localY][0] / 1000.0f;
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    if (localY == 0) {
        colData[localX] = partialSumSqRef[localX];
        colDistSq[localX] = (int)(partialSumSqTgt[localX] * 1000);
        barrier(CLK_LOCAL_MEM_FENCE);

        for (int stride = 16; stride > 0; stride >>= 1) {
            if (localX < stride) {
                colData[localX] += colData[localX + stride];
                colDistSq[localX] += colDistSq[localX + stride];
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }

        if (localX == 0) {
            float sumSqRef = colData[0];
            float sumSqTgt = (float)colDistSq[0] / 1000.0f;
            normFactor = sqrt(sumSqRef * sumSqTgt);
            if (normFactor < 1e-10f) {
                normFactor = 1e-10f;
            }
        }
    }
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 3: Apply zero-mean and Hann window
    float hannX = 0.5f * (1.0f - cos(2.0f * M_PI_F * localX / 31.0f));
    float hannY = 0.5f * (1.0f - cos(2.0f * M_PI_F * localY / 31.0f));
    float window = hannX * hannY;

    refData[localY][localX] = (float2)(devRef * window, 0.0f);
    tgtData[localY][localX] = (float2)(devTgt * window, 0.0f);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 4: 2D FFT of both tiles
    fft2d_32x32(refData, localX, localY, -1);
    fft2d_32x32(tgtData, localX, localY, -1);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 5: Cross-power spectrum (no normalization for NCC)
    float2 ref = refData[localY][localX];
    float2 tgt = tgtData[localY][localX];

    float2 cross;
    cross.x = ref.x * tgt.x + ref.y * tgt.y;
    cross.y = ref.y * tgt.x - ref.x * tgt.y;
    refData[localY][localX] = cross;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 6: Inverse 2D FFT
    fft2d_32x32(refData, localX, localY, +1);

    // Scale by 1/N^2
    refData[localY][localX] = refData[localY][localX] / (32.0f * 32.0f);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 7: FFT shift
    int halfSize = 16;
    float myVal = refData[localY][localX].x;
    barrier(CLK_LOCAL_MEM_FENCE);

    int dstY = (localY < halfSize) ? localY + halfSize : localY - halfSize;
    int dstX = (localX < halfSize) ? localX + halfSize : localX - halfSize;
    realResult[dstY][dstX] = myVal;
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 8: Find peak
    find_peak_32x32(realResult, rowMax, rowMaxX, rowMaxDistSq, &peakY, &peakX, &peakVal,
                    rowData, rowIdx, rowDistSq, colData, colIdxY, colIdxX, colDistSq, localX, localY);
    barrier(CLK_LOCAL_MEM_FENCE);

    // Step 9: Subpixel refinement and write result
    if (localX == 0 && localY == 0) {
        float2 subpixel = subpixel_fit_32(realResult, peakY, peakX);

        float center = 16.0f;
        float dy = (float)peakY - center + subpixel.y;
        float dx = (float)peakX - center + subpixel.x;

        // NCC confidence = peak / normFactor
        float nccVal = peakVal / normFactor;
        float confidence = clamp(nccVal, 0.0f, 1.0f);

        results[tileIdx * 3] = -dx;
        results[tileIdx * 3 + 1] = -dy;
        results[tileIdx * 3 + 2] = confidence;
    }
}
