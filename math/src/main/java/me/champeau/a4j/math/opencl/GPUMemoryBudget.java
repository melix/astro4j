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
 */
package me.champeau.a4j.math.opencl;

/**
 * Calculates GPU memory budget for image caching.
 * <p>
 * This class determines how much GPU memory is available for caching images,
 * accounting for memory needed by correlation kernels and driver overhead.
 */
public final class GPUMemoryBudget {
    private static final double KERNEL_WORKSPACE_FRACTION = 0.3;
    private static final double IMAGE_CACHE_FRACTION = 0.5;

    private final long totalGpuMemory;
    private final long reservedForKernels;
    private final long availableForImages;

    /**
     * Creates a memory budget based on GPU capabilities and tile processing requirements.
     *
     * @param context         the OpenCL context
     * @param tileSize        the tile size used for correlation (32, 64, or 128)
     * @param maxTilesPerBatch maximum number of tiles processed in a single batch
     */
    public GPUMemoryBudget(OpenCLContext context, int tileSize, int maxTilesPerBatch) {
        this.totalGpuMemory = context.getCapabilities().globalMemSize();

        // Reserve memory for correlation workspace:
        // - 2 complex buffers (ref + target): 2 × tiles × tileSize² × 8 bytes
        // - 1 temp buffer: tiles × tileSize² × 8 bytes
        // - 1 real output: tiles × tileSize² × 4 bytes
        // - Results buffer: tiles × 12 bytes
        // Total: ~36 bytes per tile element
        long bytesPerTile = (long) tileSize * tileSize * 36;
        long estimatedKernelWorkspace = maxTilesPerBatch * bytesPerTile;

        // Use the larger of estimated workspace or 30% of total memory
        this.reservedForKernels = Math.max(
                estimatedKernelWorkspace,
                (long) (totalGpuMemory * KERNEL_WORKSPACE_FRACTION)
        );

        // Use 50% of remaining memory for image cache (leave room for fragmentation)
        this.availableForImages = (long) ((totalGpuMemory - reservedForKernels) * IMAGE_CACHE_FRACTION);
    }

    /**
     * Creates a memory budget with default tile processing parameters.
     *
     * @param context the OpenCL context
     */
    public GPUMemoryBudget(OpenCLContext context) {
        this(context, 32, 10000);
    }

    /**
     * Returns the maximum number of images that can be kept resident on GPU.
     *
     * @param width  image width in pixels
     * @param height image height in pixels
     * @return maximum number of resident images (at least 0)
     */
    public int maxResidentImages(int width, int height) {
        long bytesPerImage = (long) width * height * Float.BYTES;
        if (bytesPerImage == 0) {
            return 0;
        }
        return (int) Math.max(0, availableForImages / bytesPerImage);
    }

    /**
     * Returns the total GPU memory in bytes.
     */
    public long getTotalGpuMemory() {
        return totalGpuMemory;
    }

    /**
     * Returns the memory reserved for kernel operations in bytes.
     */
    public long getReservedForKernels() {
        return reservedForKernels;
    }

    /**
     * Returns the memory available for image caching in bytes.
     */
    public long getAvailableForImages() {
        return availableForImages;
    }

    @Override
    public String toString() {
        return String.format("GPUMemoryBudget[total=%d MB, reserved=%d MB, available=%d MB]",
                totalGpuMemory / (1024 * 1024),
                reservedForKernels / (1024 * 1024),
                availableForImages / (1024 * 1024));
    }
}
