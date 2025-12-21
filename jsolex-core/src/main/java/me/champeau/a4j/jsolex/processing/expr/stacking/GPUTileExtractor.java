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
package me.champeau.a4j.jsolex.processing.expr.stacking;

import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opencl.CL10.*;

/**
 * GPU-accelerated tile extraction for distortion map computation.
 * <p>
 * This class performs the entire tile extraction pipeline on GPU:
 * <ol>
 *   <li>Upload full images to GPU memory</li>
 *   <li>Compute integral images on GPU</li>
 *   <li>Filter grid positions by signal threshold</li>
 *   <li>Extract valid tiles directly on GPU</li>
 * </ol>
 * <p>
 * This eliminates CPU loops over grid positions and reduces CPU-GPU transfers
 * from O(tiles) to O(1) - just upload 2 images, download extracted tiles.
 */
public class GPUTileExtractor {
    private static final int MIN_POSITIONS_FOR_GPU = 1000;

    /**
     * Result of GPU tile extraction containing extracted tiles and their positions.
     */
    public record ExtractionResult(
            float[][] refTiles,      // [validCount][tileSize²]
            float[][] targetTiles,   // [validCount][tileSize²]
            int[] gridX,             // [validCount] - x positions
            int[] gridY,             // [validCount] - y positions
            int validCount,
            int gridWidth,
            int gridHeight
    ) {}

    /**
     * Extracts tiles from images using GPU acceleration.
     * Falls back to CPU if GPU is unavailable or grid is too small.
     *
     * @param referenceData reference image data [height][width]
     * @param targetData    target image data [height][width]
     * @param width         image width
     * @param height        image height
     * @param tileSize      tile size (must be 32, 64, or 128)
     * @param increment     grid increment
     * @param signalThreshold minimum signal threshold
     * @return extraction result with tiles and positions
     */
    public static ExtractionResult extract(float[][] referenceData, float[][] targetData,
                                           int width, int height, int tileSize, int increment,
                                           float signalThreshold) {
        int maxY = height - tileSize;
        int maxX = width - tileSize;
        int gridWidth = (maxX / increment) + 1;
        int gridHeight = (maxY / increment) + 1;
        int totalPositions = gridWidth * gridHeight;

        var context = OpenCLSupport.getContext();
        if (context != null && OpenCLSupport.isEnabled() && totalPositions >= MIN_POSITIONS_FOR_GPU) {
            try {
                return extractGPU(context, referenceData, targetData, width, height,
                        tileSize, increment, signalThreshold, gridWidth, gridHeight, totalPositions);
            } catch (Exception e) {
                System.err.println("[GPUTileExtractor] GPU extraction failed, falling back to CPU");
                e.printStackTrace();
                context.recordError("GPUTileExtractor.extract", e);
            }
        }
        return extractCPU(referenceData, targetData, width, height, tileSize, increment,
                signalThreshold, gridWidth, gridHeight);
    }

    private static ExtractionResult extractGPU(OpenCLContext context,
                                                float[][] referenceData, float[][] targetData,
                                                int width, int height, int tileSize, int increment,
                                                float signalThreshold, int gridWidth, int gridHeight,
                                                int totalPositions) {
        var refFlat = flatten2D(referenceData, width, height);
        var targetFlat = flatten2D(targetData, width, height);
        int imageSize = width * height;
        int tileSizeSq = tileSize * tileSize;

        return context.executeWithLock(() -> {
            long refImageBuf = 0, targetImageBuf = 0;
            long refIntegralBuf = 0, targetIntegralBuf = 0;
            long validMaskBuf = 0, gridXBuf = 0, gridYBuf = 0;
            long outputIndicesBuf = 0, validCountBuf = 0;
            long refTilesBuf = 0, targetTilesBuf = 0;

            try {
                // Allocate image buffers
                refImageBuf = context.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_ONLY);
                targetImageBuf = context.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_ONLY);
                refIntegralBuf = context.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_WRITE);
                targetIntegralBuf = context.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_WRITE);

                // Allocate grid buffers
                validMaskBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
                gridXBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
                gridYBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
                outputIndicesBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
                validCountBuf = context.allocateBuffer(Integer.BYTES, CL_MEM_READ_WRITE);

                // Upload images
                context.writeBuffer(refImageBuf, refFlat);
                context.writeBuffer(targetImageBuf, targetFlat);

                // Compute integral images
                computeIntegralImage(context, refImageBuf, refIntegralBuf, width, height);
                computeIntegralImage(context, targetImageBuf, targetIntegralBuf, width, height);

                // Filter positions by signal
                var filterKernel = context.getKernelManager().getKernel("tile_extraction", "filter_positions_by_signal");
                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(filterKernel, 0, stack.pointers(refIntegralBuf));
                    clSetKernelArg(filterKernel, 1, stack.pointers(targetIntegralBuf));
                    clSetKernelArg(filterKernel, 2, stack.pointers(validMaskBuf));
                    clSetKernelArg(filterKernel, 3, stack.pointers(gridXBuf));
                    clSetKernelArg(filterKernel, 4, stack.pointers(gridYBuf));
                    clSetKernelArg(filterKernel, 5, stack.ints(width));
                    clSetKernelArg(filterKernel, 6, stack.ints(height));
                    clSetKernelArg(filterKernel, 7, stack.ints(tileSize));
                    clSetKernelArg(filterKernel, 8, stack.ints(increment));
                    clSetKernelArg(filterKernel, 9, stack.ints(gridWidth));
                    clSetKernelArg(filterKernel, 10, stack.ints(gridHeight));
                    clSetKernelArg(filterKernel, 11, stack.floats(signalThreshold));
                    clSetKernelArg(filterKernel, 12, stack.ints(1)); // hasTarget = true

                    var globalSize = stack.pointers(gridWidth, gridHeight);
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), filterKernel, 2, null, globalSize, null, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue filter_positions_by_signal kernel: " + err);
                    }
                }

                // Compute tile indices (prefix sum)
                var indexKernel = context.getKernelManager().getKernel("tile_extraction", "compute_tile_indices");
                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(indexKernel, 0, stack.pointers(validMaskBuf));
                    clSetKernelArg(indexKernel, 1, stack.pointers(outputIndicesBuf));
                    clSetKernelArg(indexKernel, 2, stack.pointers(validCountBuf));
                    clSetKernelArg(indexKernel, 3, stack.ints(totalPositions));

                    var globalSize = stack.pointers(1);
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), indexKernel, 1, null, globalSize, null, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue compute_tile_indices kernel: " + err);
                    }
                }

                context.finish();

                // Read valid count
                int[] validCountArr = new int[1];
                readBufferInt(context, validCountBuf, validCountArr);
                int validCount = validCountArr[0];

                if (validCount == 0) {
                    return new ExtractionResult(
                            new float[0][], new float[0][],
                            new int[0], new int[0],
                            0, gridWidth, gridHeight
                    );
                }

                // Allocate tile buffers - ensure we don't exceed max allocation size
                long tileBufferSize = (long) validCount * tileSizeSq * Float.BYTES;
                refTilesBuf = context.allocateBuffer((int) Math.min(tileBufferSize, Integer.MAX_VALUE), CL_MEM_WRITE_ONLY);
                targetTilesBuf = context.allocateBuffer((int) Math.min(tileBufferSize, Integer.MAX_VALUE), CL_MEM_WRITE_ONLY);

                // Extract tiles
                var extractKernel = context.getKernelManager().getKernel("tile_extraction", "extract_tiles");
                try (var stack = MemoryStack.stackPush()) {
                    clSetKernelArg(extractKernel, 0, stack.pointers(refImageBuf));
                    clSetKernelArg(extractKernel, 1, stack.pointers(targetImageBuf));
                    clSetKernelArg(extractKernel, 2, stack.pointers(gridXBuf));
                    clSetKernelArg(extractKernel, 3, stack.pointers(gridYBuf));
                    clSetKernelArg(extractKernel, 4, stack.pointers(validMaskBuf));
                    clSetKernelArg(extractKernel, 5, stack.pointers(outputIndicesBuf));
                    clSetKernelArg(extractKernel, 6, stack.pointers(refTilesBuf));
                    clSetKernelArg(extractKernel, 7, stack.pointers(targetTilesBuf));
                    clSetKernelArg(extractKernel, 8, stack.ints(width));
                    clSetKernelArg(extractKernel, 9, stack.ints(tileSize));
                    clSetKernelArg(extractKernel, 10, stack.ints(totalPositions));

                    var globalSize = stack.pointers(tileSize, tileSize, totalPositions);
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), extractKernel, 3, null, globalSize, null, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to enqueue extract_tiles kernel: " + err);
                    }
                }

                context.finish();

                // Read results - use NoSync variants since we just finished
                float[] refTilesFlat = new float[validCount * tileSizeSq];
                float[] targetTilesFlat = new float[validCount * tileSizeSq];
                int[] gridXArr = new int[totalPositions];
                int[] gridYArr = new int[totalPositions];
                int[] validMaskArr = new int[totalPositions];

                context.readBufferNoSync(refTilesBuf, refTilesFlat);
                context.readBufferNoSync(targetTilesBuf, targetTilesFlat);
                readBufferInt(context, gridXBuf, gridXArr);
                readBufferInt(context, gridYBuf, gridYArr);
                readBufferInt(context, validMaskBuf, validMaskArr);

                // Convert to 2D arrays and collect valid positions
                float[][] refTiles = new float[validCount][tileSizeSq];
                float[][] targetTiles = new float[validCount][tileSizeSq];
                int[] validGridX = new int[validCount];
                int[] validGridY = new int[validCount];

                for (int i = 0; i < validCount; i++) {
                    System.arraycopy(refTilesFlat, i * tileSizeSq, refTiles[i], 0, tileSizeSq);
                    System.arraycopy(targetTilesFlat, i * tileSizeSq, targetTiles[i], 0, tileSizeSq);
                }

                int validIdx = 0;
                for (int i = 0; i < totalPositions && validIdx < validCount; i++) {
                    if (validMaskArr[i] == 1) {
                        validGridX[validIdx] = gridXArr[i];
                        validGridY[validIdx] = gridYArr[i];
                        validIdx++;
                    }
                }

                return new ExtractionResult(refTiles, targetTiles, validGridX, validGridY,
                        validCount, gridWidth, gridHeight);

            } finally {
                safeRelease(context, refImageBuf);
                safeRelease(context, targetImageBuf);
                safeRelease(context, refIntegralBuf);
                safeRelease(context, targetIntegralBuf);
                safeRelease(context, validMaskBuf);
                safeRelease(context, gridXBuf);
                safeRelease(context, gridYBuf);
                safeRelease(context, outputIndicesBuf);
                safeRelease(context, validCountBuf);
                safeRelease(context, refTilesBuf);
                safeRelease(context, targetTilesBuf);
            }
        });
    }

    private static void computeIntegralImage(OpenCLContext context, long inputBuf, long outputBuf,
                                              int width, int height) {
        // Horizontal pass
        var hKernel = context.getKernelManager().getKernel("tile_extraction", "integral_image_horizontal");
        try (var stack = MemoryStack.stackPush()) {
            clSetKernelArg(hKernel, 0, stack.pointers(inputBuf));
            clSetKernelArg(hKernel, 1, stack.pointers(outputBuf));
            clSetKernelArg(hKernel, 2, stack.ints(width));
            clSetKernelArg(hKernel, 3, stack.ints(height));

            var globalSize = stack.pointers(1, height);
            int err = clEnqueueNDRangeKernel(context.getCommandQueue(), hKernel, 2, null, globalSize, null, null, null);
            if (err != CL_SUCCESS) {
                throw new RuntimeException("Failed to enqueue integral_image_horizontal kernel: " + err);
            }
        }

        // Vertical pass (in-place on output)
        var vKernel = context.getKernelManager().getKernel("tile_extraction", "integral_image_vertical");
        try (var stack = MemoryStack.stackPush()) {
            clSetKernelArg(vKernel, 0, stack.pointers(outputBuf));
            clSetKernelArg(vKernel, 1, stack.ints(width));
            clSetKernelArg(vKernel, 2, stack.ints(height));

            var globalSize = stack.pointers(width, 1);
            int err = clEnqueueNDRangeKernel(context.getCommandQueue(), vKernel, 2, null, globalSize, null, null, null);
            if (err != CL_SUCCESS) {
                throw new RuntimeException("Failed to enqueue integral_image_vertical kernel: " + err);
            }
        }
    }

    private static ExtractionResult extractCPU(float[][] referenceData, float[][] targetData,
                                                int width, int height, int tileSize, int increment,
                                                float signalThreshold, int gridWidth, int gridHeight) {
        var signalEvaluator = new SignalEvaluator(referenceData, targetData, width, height);

        var refTilesList = new java.util.ArrayList<float[]>();
        var targetTilesList = new java.util.ArrayList<float[]>();
        var gridXList = new java.util.ArrayList<Integer>();
        var gridYList = new java.util.ArrayList<Integer>();

        int maxY = height - tileSize;
        int maxX = width - tileSize;

        for (int y = 0; y <= maxY; y += increment) {
            for (int x = 0; x <= maxX; x += increment) {
                if (signalEvaluator.passesThreshold(x, y, tileSize, tileSize, signalThreshold)) {
                    float[] refTile = new float[tileSize * tileSize];
                    float[] targetTile = new float[tileSize * tileSize];

                    for (int ty = 0; ty < tileSize; ty++) {
                        System.arraycopy(referenceData[y + ty], x, refTile, ty * tileSize, tileSize);
                        System.arraycopy(targetData[y + ty], x, targetTile, ty * tileSize, tileSize);
                    }

                    refTilesList.add(refTile);
                    targetTilesList.add(targetTile);
                    gridXList.add(x);
                    gridYList.add(y);
                }
            }
        }

        int validCount = refTilesList.size();
        float[][] refTiles = refTilesList.toArray(new float[0][]);
        float[][] targetTiles = targetTilesList.toArray(new float[0][]);
        int[] gridX = gridXList.stream().mapToInt(Integer::intValue).toArray();
        int[] gridY = gridYList.stream().mapToInt(Integer::intValue).toArray();

        return new ExtractionResult(refTiles, targetTiles, gridX, gridY, validCount, gridWidth, gridHeight);
    }

    private static float[] flatten2D(float[][] data, int width, int height) {
        float[] flat = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, flat, y * width, width);
        }
        return flat;
    }

    /**
     * Extracts tiles from GPU-resident images.
     * Useful for refinement loops where the target image is already on GPU.
     * Must be called within OpenCLContext.executeWithLock.
     *
     * @param context          OpenCL context
     * @param refImage         GPU-resident reference image
     * @param targetImage      GPU-resident target image
     * @param width            image width
     * @param height           image height
     * @param tileSize         tile size (32, 64, or 128)
     * @param increment        grid increment
     * @param signalThreshold  minimum signal threshold
     * @return extraction result with tiles and positions
     */
    public static ExtractionResult extractFromGPU(OpenCLContext context,
                                                   GPUImage refImage, GPUImage targetImage,
                                                   int width, int height, int tileSize, int increment,
                                                   float signalThreshold) {
        int maxY = height - tileSize;
        int maxX = width - tileSize;
        int gridWidth = (maxX / increment) + 1;
        int gridHeight = (maxY / increment) + 1;
        int totalPositions = gridWidth * gridHeight;
        int imageSize = width * height;
        int tileSizeSq = tileSize * tileSize;

        long refIntegralBuf = 0, targetIntegralBuf = 0;
        long validMaskBuf = 0, gridXBuf = 0, gridYBuf = 0;
        long outputIndicesBuf = 0, validCountBuf = 0;
        long refTilesBuf = 0, targetTilesBuf = 0;

        try {
            // Allocate integral image buffers
            refIntegralBuf = context.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_WRITE);
            targetIntegralBuf = context.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_WRITE);

            // Allocate grid buffers
            validMaskBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            gridXBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            gridYBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            outputIndicesBuf = context.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            validCountBuf = context.allocateBuffer(Integer.BYTES, CL_MEM_READ_WRITE);

            // Compute integral images from GPU-resident images
            computeIntegralImage(context, refImage.getBuffer(), refIntegralBuf, width, height);
            computeIntegralImage(context, targetImage.getBuffer(), targetIntegralBuf, width, height);

            // Filter positions by signal
            var filterKernel = context.getKernelManager().getKernel("tile_extraction", "filter_positions_by_signal");
            try (var stack = MemoryStack.stackPush()) {
                clSetKernelArg(filterKernel, 0, stack.pointers(refIntegralBuf));
                clSetKernelArg(filterKernel, 1, stack.pointers(targetIntegralBuf));
                clSetKernelArg(filterKernel, 2, stack.pointers(validMaskBuf));
                clSetKernelArg(filterKernel, 3, stack.pointers(gridXBuf));
                clSetKernelArg(filterKernel, 4, stack.pointers(gridYBuf));
                clSetKernelArg(filterKernel, 5, stack.ints(width));
                clSetKernelArg(filterKernel, 6, stack.ints(height));
                clSetKernelArg(filterKernel, 7, stack.ints(tileSize));
                clSetKernelArg(filterKernel, 8, stack.ints(increment));
                clSetKernelArg(filterKernel, 9, stack.ints(gridWidth));
                clSetKernelArg(filterKernel, 10, stack.ints(gridHeight));
                clSetKernelArg(filterKernel, 11, stack.floats(signalThreshold));
                clSetKernelArg(filterKernel, 12, stack.ints(1)); // hasTarget = true

                var globalSize = stack.pointers(gridWidth, gridHeight);
                int err = clEnqueueNDRangeKernel(context.getCommandQueue(), filterKernel, 2, null, globalSize, null, null, null);
                if (err != CL_SUCCESS) {
                    throw new RuntimeException("Failed to enqueue filter_positions_by_signal kernel: " + err);
                }
            }

            // Compute tile indices (prefix sum)
            var indexKernel = context.getKernelManager().getKernel("tile_extraction", "compute_tile_indices");
            try (var stack = MemoryStack.stackPush()) {
                clSetKernelArg(indexKernel, 0, stack.pointers(validMaskBuf));
                clSetKernelArg(indexKernel, 1, stack.pointers(outputIndicesBuf));
                clSetKernelArg(indexKernel, 2, stack.pointers(validCountBuf));
                clSetKernelArg(indexKernel, 3, stack.ints(totalPositions));

                var globalSize = stack.pointers(1);
                int err = clEnqueueNDRangeKernel(context.getCommandQueue(), indexKernel, 1, null, globalSize, null, null, null);
                if (err != CL_SUCCESS) {
                    throw new RuntimeException("Failed to enqueue compute_tile_indices kernel: " + err);
                }
            }

            context.finish();

            // Read valid count
            int[] validCountArr = new int[1];
            readBufferInt(context, validCountBuf, validCountArr);
            int validCount = validCountArr[0];

            if (validCount == 0) {
                return new ExtractionResult(
                        new float[0][], new float[0][],
                        new int[0], new int[0],
                        0, gridWidth, gridHeight
                );
            }

            // Allocate tile buffers
            long tileBufferSize = (long) validCount * tileSizeSq * Float.BYTES;
            refTilesBuf = context.allocateBuffer((int) Math.min(tileBufferSize, Integer.MAX_VALUE), CL_MEM_WRITE_ONLY);
            targetTilesBuf = context.allocateBuffer((int) Math.min(tileBufferSize, Integer.MAX_VALUE), CL_MEM_WRITE_ONLY);

            // Extract tiles from GPU-resident images
            var extractKernel = context.getKernelManager().getKernel("tile_extraction", "extract_tiles");
            try (var stack = MemoryStack.stackPush()) {
                clSetKernelArg(extractKernel, 0, stack.pointers(refImage.getBuffer()));
                clSetKernelArg(extractKernel, 1, stack.pointers(targetImage.getBuffer()));
                clSetKernelArg(extractKernel, 2, stack.pointers(gridXBuf));
                clSetKernelArg(extractKernel, 3, stack.pointers(gridYBuf));
                clSetKernelArg(extractKernel, 4, stack.pointers(validMaskBuf));
                clSetKernelArg(extractKernel, 5, stack.pointers(outputIndicesBuf));
                clSetKernelArg(extractKernel, 6, stack.pointers(refTilesBuf));
                clSetKernelArg(extractKernel, 7, stack.pointers(targetTilesBuf));
                clSetKernelArg(extractKernel, 8, stack.ints(width));
                clSetKernelArg(extractKernel, 9, stack.ints(tileSize));
                clSetKernelArg(extractKernel, 10, stack.ints(totalPositions));

                var globalSize = stack.pointers(tileSize, tileSize, totalPositions);
                int err = clEnqueueNDRangeKernel(context.getCommandQueue(), extractKernel, 3, null, globalSize, null, null, null);
                if (err != CL_SUCCESS) {
                    throw new RuntimeException("Failed to enqueue extract_tiles kernel: " + err);
                }
            }

            context.finish();

            // Read results - use NoSync variants since we just finished
            float[] refTilesFlat = new float[validCount * tileSizeSq];
            float[] targetTilesFlat = new float[validCount * tileSizeSq];
            int[] gridXArr = new int[totalPositions];
            int[] gridYArr = new int[totalPositions];
            int[] validMaskArr = new int[totalPositions];

            context.readBufferNoSync(refTilesBuf, refTilesFlat);
            context.readBufferNoSync(targetTilesBuf, targetTilesFlat);
            readBufferInt(context, gridXBuf, gridXArr);
            readBufferInt(context, gridYBuf, gridYArr);
            readBufferInt(context, validMaskBuf, validMaskArr);

            // Convert to 2D arrays and collect valid positions
            float[][] refTiles = new float[validCount][tileSizeSq];
            float[][] targetTiles = new float[validCount][tileSizeSq];
            int[] validGridX = new int[validCount];
            int[] validGridY = new int[validCount];

            for (int i = 0; i < validCount; i++) {
                System.arraycopy(refTilesFlat, i * tileSizeSq, refTiles[i], 0, tileSizeSq);
                System.arraycopy(targetTilesFlat, i * tileSizeSq, targetTiles[i], 0, tileSizeSq);
            }

            int validIdx = 0;
            for (int i = 0; i < totalPositions && validIdx < validCount; i++) {
                if (validMaskArr[i] == 1) {
                    validGridX[validIdx] = gridXArr[i];
                    validGridY[validIdx] = gridYArr[i];
                    validIdx++;
                }
            }

            return new ExtractionResult(refTiles, targetTiles, validGridX, validGridY,
                    validCount, gridWidth, gridHeight);

        } finally {
            safeRelease(context, refIntegralBuf);
            safeRelease(context, targetIntegralBuf);
            safeRelease(context, validMaskBuf);
            safeRelease(context, gridXBuf);
            safeRelease(context, gridYBuf);
            safeRelease(context, outputIndicesBuf);
            safeRelease(context, validCountBuf);
            safeRelease(context, refTilesBuf);
            safeRelease(context, targetTilesBuf);
        }
    }

    private static void safeRelease(OpenCLContext context, long buffer) {
        if (buffer != 0) {
            try {
                context.releaseBuffer(buffer);
            } catch (Exception e) {
                // Ignore release errors
            }
        }
    }

    private static void readBufferInt(OpenCLContext context, long buffer, int[] data) {
        var intBuffer = MemoryUtil.memAllocInt(data.length);
        try {
            int err = clEnqueueReadBuffer(context.getCommandQueue(), buffer, true, 0, intBuffer, null, null);
            if (err != CL_SUCCESS) {
                throw new RuntimeException("Failed to read int buffer: " + err);
            }
            intBuffer.rewind();
            intBuffer.get(data);
        } finally {
            MemoryUtil.memFree(intBuffer);
        }
    }
}
