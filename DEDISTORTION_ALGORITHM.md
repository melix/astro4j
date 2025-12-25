# Dedistortion Algorithm Documentation

This document describes the dedistortion algorithm used in JSol'Ex for correcting local geometric distortions in astronomical images. The algorithm is particularly useful for stacking multiple frames where each may have slightly different distortions due to atmospheric turbulence or optical imperfections.

## Table of Contents

1. [High-Level Overview](#high-level-overview)
2. [Phase Correlation Fundamentals](#phase-correlation-fundamentals)
3. [Tile-Based Distortion Mapping](#tile-based-distortion-mapping)
4. [Signal Evaluation and Tile Selection](#signal-evaluation-and-tile-selection)
5. [Distortion Grid Construction](#distortion-grid-construction)
6. [Grid Interpolation and Smoothing](#grid-interpolation-and-smoothing)
7. [Iterative Refinement](#iterative-refinement)
8. [Consensus Reference Mode](#consensus-reference-mode)
9. [GPU Acceleration](#gpu-acceleration)
10. [Configuration Parameters](#configuration-parameters)
11. [File Locations](#file-locations)

---

## High-Level Overview

The dedistortion algorithm corrects local geometric distortions by comparing small regions (tiles) between a reference image and a target image. The core idea is:

1. **Divide** the image into overlapping tiles
2. **Measure** the local displacement of each tile using phase correlation
3. **Build** a sparse displacement grid from these measurements
4. **Interpolate** the grid to get per-pixel displacements
5. **Warp** the target image to align with the reference

```
┌─────────────────────────────────────────┐
│           Reference Image               │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐    │
│  │tile│ │tile│ │tile│ │tile│ │tile│    │
│  └────┘ └────┘ └────┘ └────┘ └────┘    │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐    │
│  │tile│ │tile│ │tile│ │tile│ │tile│    │
│  └────┘ └────┘ └────┘ └────┘ └────┘    │
│         ... more tiles ...              │
└─────────────────────────────────────────┘
                    │
                    ▼ Phase correlation per tile
┌─────────────────────────────────────────┐
│         Displacement Grid               │
│  (dx,dy) (dx,dy) (dx,dy) (dx,dy)       │
│  (dx,dy) (dx,dy) (dx,dy) (dx,dy)       │
│         ... sparse grid ...             │
└─────────────────────────────────────────┘
                    │
                    ▼ Bicubic interpolation
┌─────────────────────────────────────────┐
│         Corrected Image                 │
│  (per-pixel displacement applied)       │
└─────────────────────────────────────────┘
```

---

## Phase Correlation Fundamentals

Phase correlation is a frequency-domain technique for measuring the translational shift between two images. It is robust to noise and intensity variations because it normalizes by magnitude, retaining only phase information.

### Algorithm

Given two image patches `f` (reference) and `g` (target):

1. **Apply Hann Window**: Reduces spectral leakage at tile boundaries
   ```
   w(x, y) = w₁(x) × w₁(y)
   where w₁(i) = 0.5 × (1 - cos(2π × i / (N-1)))
   ```

2. **Compute FFTs**: Transform both patches to frequency domain
   ```
   F = FFT(f × w)
   G = FFT(g × w)
   ```

3. **Cross-Power Spectrum**: Normalize the product to extract phase
   ```
   R = (F* × G) / |F* × G|
   ```
   where F* is the complex conjugate of F.

4. **Inverse FFT**: Transform back to get the correlation surface
   ```
   r = IFFT(R)
   ```

5. **Find Peak**: The peak location indicates the shift
   ```
   (Δx, Δy) = argmax(r) - (N/2, N/2)
   ```

6. **Sub-pixel Refinement**: Fit a 2D Gaussian to the peak neighborhood
   ```
   Using log values of the 3×3 neighborhood around the peak:
   Δy_sub = (log(N) - log(S)) / (2 × (log(N) + log(S) - 2×log(C)))
   Δx_sub = (log(W) - log(E)) / (2 × (log(W) + log(E) - 2×log(C)))
   ```

### Why Phase Correlation?

- **Noise Robustness**: Magnitude normalization reduces sensitivity to noise
- **Intensity Invariance**: Works even with brightness differences between images
- **Sub-pixel Accuracy**: Gaussian fitting achieves sub-pixel precision
- **Computational Efficiency**: FFT-based, O(N² log N) complexity

---

## Tile-Based Distortion Mapping

The image is divided into a grid of tiles. Each tile position is determined by:

```
increment = max(MIN_STEP, tileSize × sampling)
```

For example, with `tileSize = 32` and `sampling = 0.5`:
- `increment = max(8, 32 × 0.5) = 16 pixels`

This means tiles are extracted every 16 pixels, creating overlapping coverage.

### Grid Positions

```
positions_x = [0, increment, 2×increment, ..., width - tileSize]
positions_y = [0, increment, 2×increment, ..., height - tileSize]
```

For a 2048×1536 image with increment=16 and tileSize=32:
- X positions: 0, 16, 32, ..., 2016 (127 positions)
- Y positions: 0, 16, 32, ..., 1504 (95 positions)
- Total grid points: ~12,000

### Tile Size Selection

| Tile Size | Characteristics |
|-----------|-----------------|
| 32×32     | Default, good balance of speed and accuracy |
| 64×64     | Captures larger distortions, less noise |
| 128×128   | For severe distortions, slower |

Smaller tiles capture finer distortions but are more sensitive to noise. Larger tiles are more robust but may miss local variations.

---

## Signal Evaluation and Tile Selection

Not all tiles are useful for correlation. Low-signal regions (sky background, uniform areas) produce unreliable displacement estimates. The algorithm uses **integral images** for efficient signal evaluation.

### Integral Image

An integral image allows O(1) computation of any rectangular region's sum:

```
I(x, y) = Σᵢ₌₀ˣ Σⱼ₌₀ʸ image(i, j)

Area sum of rectangle (x1,y1) to (x2,y2):
sum = I(x2,y2) - I(x2,y1-1) - I(x1-1,y2) + I(x1-1,y1-1)
```

### Signal Threshold

A tile is included only if its average intensity exceeds a threshold:

```
avgSignal = areaSum(x, y, x+tileSize, y+tileSize) / tileSize²
include = avgSignal > signalThreshold
```

This filtering is particularly important for solar images where the disk edge and background should be excluded.

---

## Distortion Grid Construction

The distortion grid stores displacement vectors (dx, dy) at each sampled position. The grid is sparse because:

1. Not all positions have sufficient signal
2. Some correlations may fail or produce outliers

### Data Structure

```java
public class DistorsionMap {
    private final float[][][] dxy;      // [gridHeight][gridWidth][2] for (dx, dy)
    private final boolean[][] sampled;  // [gridHeight][gridWidth] - true if measured
    private final int step;             // Grid spacing in pixels
    private final int width, height;    // Image dimensions
}
```

### Recording Displacements

For each valid tile at grid position (gx, gy):

```java
dxy[gy][gx][0] = dx;  // X displacement
dxy[gy][gx][1] = dy;  // Y displacement
sampled[gy][gx] = true;
```

---

## Grid Interpolation and Smoothing

The sparse displacement grid must be:
1. **Interpolated** to fill gaps
2. **Filtered** to remove outliers
3. **Smoothed** to ensure continuity

### Outlier Detection (MAD)

Uses Median Absolute Deviation for robust outlier detection:

```
median = median(all_displacements)
MAD = median(|displacement - median|)
threshold = MAD_THRESHOLD × MAD  // MAD_THRESHOLD = 3.0

outlier = |displacement - median| > threshold
```

Both global and local (neighborhood) medians are considered.

### Gap Filling

Unsampled points are filled using inverse-distance-weighted interpolation:

```
For each unsampled point (x, y):
  weighted_sum = 0
  weight_total = 0

  For each sampled neighbor (nx, ny) within radius 3:
    dist_sq = (nx-x)² + (ny-y)²
    weight = 1 / dist_sq
    weighted_sum += displacement[ny][nx] × weight
    weight_total += weight

  displacement[y][x] = weighted_sum / weight_total
```

### Gaussian Smoothing

A separable 2D Gaussian filter is applied:

```
σ = baseσ × (step / 64)  // Scale with grid spacing
kernel_size = 2 × ceil(3σ) + 1

Two-pass convolution:
1. Horizontal pass: row by row
2. Vertical pass: column by column
```

### Bicubic Interpolation for Pixel Queries

When warping the image, per-pixel displacements are computed using bicubic interpolation over a 4×4 neighborhood:

```java
double displacement(double px, double py) {
    double ax = px / step;  // Normalized grid coordinates
    double ay = py / step;

    int gx = (int) ax;
    int gy = (int) ay;
    double dx = ax - gx;
    double dy = ay - gy;

    double result = 0;
    for (int i = -1; i <= 2; i++) {
        for (int j = -1; j <= 2; j++) {
            result += dxy[gy+i][gx+j] × cubicWeight(dx-j) × cubicWeight(dy-i);
        }
    }
    return result;
}
```

The cubic weight function (Catmull-Rom spline with a = -0.5):

```
         ┌ (a+2)|t|³ - (a+3)|t|² + 1     if |t| ≤ 1
w(t) =   │ a|t|³ - 5a|t|² + 8a|t| - 4a   if 1 < |t| < 2
         └ 0                              otherwise
```

---

## Iterative Refinement

The algorithm can perform multiple iterations to capture progressively finer distortions.

### Multi-Level Refinement

When refinement is enabled, the algorithm uses progressively smaller tile sizes:

```
Level 1: tileSize = 128  →  captures large-scale distortions
Level 2: tileSize = 64   →  medium-scale corrections
Level 3: tileSize = 32   →  fine-scale adjustments
(stops at MIN_TILE_SIZE = 32)
```

Each level:
1. Computes a distortion map at that tile size
2. Applies correction to the image
3. Uses the corrected image for the next level

### Convergence Detection

The algorithm monitors the total distortion magnitude:

```java
currentDistortion = sum of |displacement| over all grid points
relativeImprovement = (previousDistortion - currentDistortion) / previousDistortion

if (relativeImprovement < CONVERGENCE_THRESHOLD) {  // 0.01 = 1%
    stop iteration;
}
```

Also stops if distortion increases (over-correction).

### Combining Multiple Levels

Distortion maps from different levels are synthesized:

```
For each pixel (x, y):
  totalDisplacement = Σ levels displacement[level](x, y)
```

---

## Consensus Reference Mode

When stacking multiple images without a designated reference, the algorithm can compute a "consensus" reference by averaging pairwise distortions.

### Algorithm

For N images:

1. **Compute pairwise distortions**: For each pair (i, j) where i < j:
   ```
   map[i→j] = computeDistortion(image[i], image[j])
   map[j→i] = -map[i→j]  // Inverse is negation
   ```

2. **Average distortions per image**:
   ```
   For image i:
     averageMap[i] = mean(map[i→j] for all j ≠ i)
   ```

3. **Compute correction**: The correction is the negated average
   ```
   correction[i] = -averageMap[i]
   ```

### Optimization for Large N

When N > MAX_CONSENSUS_COMPARISONS (30), the algorithm subsamples pairs:
- Uses √30 ≈ 5.5× standard deviation reduction (Central Limit Theorem)
- Maintains O(N) complexity instead of O(N²)

---

## GPU Acceleration

The algorithm includes OpenCL GPU implementations for significant speedup on large images.

### GPU Pipeline Overview

```
┌─────────────────────────────────────────────────────────┐
│                    GPU Memory                           │
│  ┌─────────┐    ┌──────────┐    ┌─────────────────┐    │
│  │Reference│    │  Target  │    │ Integral Images │    │
│  │  Image  │    │  Image   │    │                 │    │
│  └────┬────┘    └────┬─────┘    └────────┬────────┘    │
│       │              │                    │             │
│       ▼              ▼                    ▼             │
│  ┌────────────────────────────────────────────────┐    │
│  │            Tile Extraction Kernel              │    │
│  │  (filter by signal, extract valid tiles)       │    │
│  └────────────────────┬───────────────────────────┘    │
│                       │                                 │
│                       ▼                                 │
│  ┌────────────────────────────────────────────────┐    │
│  │         Phase Correlation Kernel               │    │
│  │  (Hann window → FFT → cross-power → IFFT)      │    │
│  └────────────────────┬───────────────────────────┘    │
│                       │                                 │
│                       ▼                                 │
│  ┌────────────────────────────────────────────────┐    │
│  │         Peak Finding Kernel                    │    │
│  │  (parallel reduction to find maximum)          │    │
│  └────────────────────┬───────────────────────────┘    │
│                       │                                 │
│                       ▼                                 │
│  ┌────────────────────────────────────────────────┐    │
│  │         Dedistort Kernel                       │    │
│  │  (bicubic grid + bilinear/Lanczos sampling)    │    │
│  └────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Key GPU Classes

| Class | Responsibility |
|-------|---------------|
| `GPUTileExtractor` | Computes integral images on GPU, filters positions by signal, extracts tiles |
| `PhaseCorrelation` | Batched phase correlation with multi-stage FFT |
| `GPUDedistort` | GPU-to-GPU image warping with Lanczos or bilinear interpolation |
| `GPUDistortionGrid` | GPU-resident displacement grid buffers |

### Batched Processing

GPU memory is finite. The algorithm processes tiles in batches:

```
batchSize = (0.5 × gpuMemory) / (36 × tileSize²)
```

The factor 36 accounts for:
- Input tiles (reference + target): 2 × 4 bytes
- Complex FFT buffers: 2 × 8 bytes
- Intermediate results: ~20 bytes overhead

### GPU-Resident Refinement

For multi-level refinement, intermediate results stay on GPU:

```
1. Upload reference and target images once
2. For each refinement level:
   a. Extract tiles (GPU kernel)
   b. Phase correlate (GPU kernel)
   c. Warp target image (GPU kernel) → stays on GPU
3. Download final distortion maps to CPU
```

This minimizes CPU-GPU transfers during iterative processing.

### OpenCL Kernels

| Kernel File | Purpose |
|------------|---------|
| `phase_correlation.cl` | Hann window, FFT, cross-power spectrum, peak finding |
| `tile_extraction.cl` | Integral images, position filtering, tile extraction |
| `dedistort.cl` | Image warping with bicubic grid + Lanczos sampling |

### Tile Size Constraints

GPU kernels are optimized for specific tile sizes:

| Tile Size | Implementation |
|-----------|----------------|
| 32×32 | Direct phase correlation (single kernel) |
| 64×64 | Multi-stage FFT (row FFT → transpose → column FFT) |
| 128×128 | Multi-stage FFT with larger intermediate buffers |

---

## Configuration Parameters

### Core Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `tileSize` | 32 | Tile size in pixels (32, 64, or 128 for GPU) |
| `sampling` | 0.5 | Grid density factor; step = tileSize × sampling |
| `threshold` | varies | Minimum signal for tile inclusion |
| `iterations` | 3 | Maximum iteration count |
| `refine` | true | Enable multi-level refinement |

### Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `MIN_TILE_SIZE` | 32 | Minimum tile size (GPU limit) |
| `MIN_STEP` | 8 | Minimum grid step in pixels |
| `CONVERGENCE_THRESHOLD` | 0.01 | Stop if improvement < 1% |
| `MAD_THRESHOLD` | 3.0 | Outlier detection (3σ) |
| `MAX_CONSENSUS_COMPARISONS` | 30 | Subsample limit for consensus mode |

### Sampling Factor Impact

| Sampling | Grid Density | Speed | Quality |
|----------|--------------|-------|---------|
| 0.25 | Very dense | Slow | Highest |
| 0.5 | Moderate | Balanced | Good |
| 1.0 | Sparse | Fast | Lower |

---

## File Locations

| Component | Path |
|-----------|------|
| Main algorithm | `jsolex-core/src/main/java/.../expr/impl/Dedistort.java` |
| Distortion map | `jsolex-core/src/main/java/.../expr/stacking/DistorsionMap.java` |
| Phase correlation | `math/src/main/java/.../correlation/PhaseCorrelation.java` |
| Signal evaluator | `jsolex-core/src/main/java/.../expr/stacking/SignalEvaluator.java` |
| GPU dedistort | `jsolex-core/src/main/java/.../expr/stacking/GPUDedistort.java` |
| GPU tile extractor | `jsolex-core/src/main/java/.../expr/stacking/GPUTileExtractor.java` |
| GPU distortion grid | `jsolex-core/src/main/java/.../expr/stacking/GPUDistortionGrid.java` |
| Phase correlation kernel | `math/src/main/resources/.../opencl/kernels/phase_correlation.cl` |
| Tile extraction kernel | `math/src/main/resources/.../opencl/kernels/tile_extraction.cl` |
| Dedistort kernel | `math/src/main/resources/.../opencl/kernels/dedistort.cl` |
| Function definition | `jsolex-core/src/main/functions/dedistort.yml` |

---

## Performance Characteristics

### Time Complexity

| Operation | Complexity |
|-----------|-----------|
| Phase correlation (per tile) | O(T² log T) where T = tile size |
| Signal evaluation (per tile) | O(1) using integral images |
| Grid processing | O(G) where G = grid points |
| Neighborhood filtering | O(G × R²) where R = search radius |

### Space Complexity

| Structure | Memory |
|-----------|--------|
| Distortion map | O(G × 2) floats |
| Integral images | O(W × H) per image |
| GPU tile batch | O(B × T²) where B = batch size |

### GPU Memory Usage

Per tile element: ~36 bytes (inputs, FFT buffers, outputs)

For 10,000 tiles at 32×32:
- Tile data: 10,000 × 32 × 32 × 36 ≈ 370 MB
- Typically batched to fit available memory