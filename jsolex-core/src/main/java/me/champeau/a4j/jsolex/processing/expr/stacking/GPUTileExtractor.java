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

import me.champeau.a4j.math.opencl.GpuOp;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;

import java.util.ArrayList;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_MEM_WRITE_ONLY;

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
    ) {
    }

    /**
     * Extracts tiles from images using GPU acceleration.
     * Falls back to CPU if GPU is unavailable or grid is too small.
     *
     * @param referenceData   reference image data [height][width]
     * @param targetData      target image data [height][width]
     * @param width           image width
     * @param height          image height
     * @param tileSize        tile size (must be 32, 64, or 128)
     * @param increment       grid increment
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

        return context.runOp(op -> {
            // Image buffers
            var refImageBuf = op.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_ONLY);
            var targetImageBuf = op.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_ONLY);
            var refIntegralBuf = op.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_WRITE);
            var targetIntegralBuf = op.allocateBuffer(imageSize * Float.BYTES, CL_MEM_READ_WRITE);

            // Grid buffers
            var validMaskBuf = op.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            var gridXBuf = op.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            var gridYBuf = op.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            var outputIndicesBuf = op.allocateBuffer(totalPositions * Integer.BYTES, CL_MEM_READ_WRITE);
            var validCountBuf = op.allocateBuffer(Integer.BYTES, CL_MEM_READ_WRITE);

            op.write(refImageBuf, refFlat);
            op.write(targetImageBuf, targetFlat);

            // Compute integral images for both inputs
            computeIntegralImage(op, refImageBuf, refIntegralBuf, width, height);
            computeIntegralImage(op, targetImageBuf, targetIntegralBuf, width, height);

            // Filter positions by signal
            op.kernel("tile_extraction", "filter_positions_by_signal")
                    .arg(refIntegralBuf).arg(targetIntegralBuf).arg(validMaskBuf)
                    .arg(gridXBuf).arg(gridYBuf)
                    .arg(width).arg(height).arg(tileSize).arg(increment)
                    .arg(gridWidth).arg(gridHeight).arg(signalThreshold).arg(1) // hasTarget=true
                    .global(gridWidth, gridHeight)
                    .run();

            // Compute tile indices (prefix sum)
            op.kernel("tile_extraction", "compute_tile_indices")
                    .arg(validMaskBuf).arg(outputIndicesBuf).arg(validCountBuf).arg(totalPositions)
                    .global(1)
                    .run();

            // Read valid count (forces sync)
            int[] validCountArr = new int[1];
            op.readInts(validCountBuf, validCountArr);
            int validCount = validCountArr[0];

            if (validCount == 0) {
                return new ExtractionResult(
                        new float[0][], new float[0][],
                        new int[0], new int[0],
                        0, gridWidth, gridHeight);
            }

            // Tile output buffers — sized by validCount, capped at int range
            long tileBufferSize = (long) validCount * tileSizeSq * Float.BYTES;
            var refTilesBuf = op.allocateBuffer((int) Math.min(tileBufferSize, Integer.MAX_VALUE), CL_MEM_WRITE_ONLY);
            var targetTilesBuf = op.allocateBuffer((int) Math.min(tileBufferSize, Integer.MAX_VALUE), CL_MEM_WRITE_ONLY);

            op.kernel("tile_extraction", "extract_tiles")
                    .arg(refImageBuf).arg(targetImageBuf)
                    .arg(gridXBuf).arg(gridYBuf).arg(validMaskBuf).arg(outputIndicesBuf)
                    .arg(refTilesBuf).arg(targetTilesBuf)
                    .arg(width).arg(tileSize).arg(totalPositions)
                    .global(tileSize, tileSize, totalPositions)
                    .run();

            float[] refTilesFlat = new float[validCount * tileSizeSq];
            float[] targetTilesFlat = new float[validCount * tileSizeSq];
            int[] gridXArr = new int[totalPositions];
            int[] gridYArr = new int[totalPositions];
            int[] validMaskArr = new int[totalPositions];

            op.read(refTilesBuf, refTilesFlat);
            op.read(targetTilesBuf, targetTilesFlat);
            op.readInts(gridXBuf, gridXArr);
            op.readInts(gridYBuf, gridYArr);
            op.readInts(validMaskBuf, validMaskArr);

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
        });
    }

    private static void computeIntegralImage(GpuOp op, long inputBuf, long outputBuf, int width, int height) {
        // Horizontal pass: writes outputBuf
        op.kernel("tile_extraction", "integral_image_horizontal")
                .arg(inputBuf).arg(outputBuf).arg(width).arg(height)
                .global(1, height)
                .run();
        // Vertical pass (in-place on outputBuf)
        op.kernel("tile_extraction", "integral_image_vertical")
                .arg(outputBuf).arg(width).arg(height)
                .global(width, 1)
                .run();
    }

    private static ExtractionResult extractCPU(float[][] referenceData, float[][] targetData,
                                               int width, int height, int tileSize, int increment,
                                               float signalThreshold, int gridWidth, int gridHeight) {
        var signalEvaluator = new SignalEvaluator(referenceData, targetData, width, height);

        var refTilesList = new ArrayList<float[]>();
        var targetTilesList = new ArrayList<float[]>();
        var gridXList = new ArrayList<Integer>();
        var gridYList = new ArrayList<Integer>();

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

}
