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

import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;

/**
 * GPU-resident distortion grid (dx, dy arrays).
 * Used during refinement loops to avoid uploading grid data repeatedly.
 * <p>
 * This class is intended for use within a single GPU-locked context.
 * All methods must be called within {@link OpenCLContext#executeWithLock}.
 */
public class GPUDistortionGrid implements AutoCloseable {
    private final int gridWidth;
    private final int gridHeight;
    private final int gridStep;
    private long dxBuffer;
    private long dyBuffer;
    private final OpenCLContext context;
    private boolean closed = false;

    private GPUDistortionGrid(int gridWidth, int gridHeight, int gridStep,
                              long dxBuffer, long dyBuffer, OpenCLContext context) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.gridStep = gridStep;
        this.dxBuffer = dxBuffer;
        this.dyBuffer = dyBuffer;
        this.context = context;
    }

    /**
     * Uploads distortion grid data from CPU to GPU.
     * Must be called within executeWithLock.
     *
     * @param gridDx   displacement X values [gridHeight][gridWidth]
     * @param gridDy   displacement Y values [gridHeight][gridWidth]
     * @param gridStep grid step size
     * @param context  OpenCL context
     * @return GPU-resident distortion grid
     */
    public static GPUDistortionGrid upload(float[][] gridDx, float[][] gridDy,
                                           int gridStep, OpenCLContext context) {
        int gridHeight = gridDx.length;
        int gridWidth = gridDx[0].length;
        int size = gridWidth * gridHeight;
        int sizeInBytes = size * Float.BYTES;

        var flatDx = flatten(gridDx, gridWidth, gridHeight);
        var flatDy = flatten(gridDy, gridWidth, gridHeight);

        long dxBuffer = context.allocateBuffer(sizeInBytes, CL_MEM_READ_ONLY);
        long dyBuffer = context.allocateBuffer(sizeInBytes, CL_MEM_READ_ONLY);

        context.writeBuffer(dxBuffer, flatDx);
        context.writeBuffer(dyBuffer, flatDy);

        return new GPUDistortionGrid(gridWidth, gridHeight, gridStep, dxBuffer, dyBuffer, context);
    }

    /**
     * Creates a GPU distortion grid from a CPU DistorsionMap.
     * Must be called within executeWithLock.
     *
     * @param map     source distortion map
     * @param context OpenCL context
     * @return GPU-resident distortion grid
     */
    public static GPUDistortionGrid fromDistorsionMap(DistorsionMap map, OpenCLContext context) {
        return upload(map.getGridDx(), map.getGridDy(), map.getStep(), context);
    }

    /**
     * Returns the GPU buffer handle for dx displacements.
     */
    public long getDxBuffer() {
        checkNotClosed();
        return dxBuffer;
    }

    /**
     * Returns the GPU buffer handle for dy displacements.
     */
    public long getDyBuffer() {
        checkNotClosed();
        return dyBuffer;
    }

    /**
     * Returns the grid width (number of columns).
     */
    public int getGridWidth() {
        return gridWidth;
    }

    /**
     * Returns the grid height (number of rows).
     */
    public int getGridHeight() {
        return gridHeight;
    }

    /**
     * Returns the grid step size.
     */
    public int getGridStep() {
        return gridStep;
    }

    /**
     * Returns the OpenCL context this grid belongs to.
     */
    public OpenCLContext getContext() {
        return context;
    }

    /**
     * Releases GPU memory.
     * Must be called within executeWithLock.
     */
    @Override
    public void close() {
        if (!closed) {
            if (dxBuffer != 0) {
                context.releaseBuffer(dxBuffer);
                dxBuffer = 0;
            }
            if (dyBuffer != 0) {
                context.releaseBuffer(dyBuffer);
                dyBuffer = 0;
            }
            closed = true;
        }
    }

    /**
     * Checks if this GPU grid has been closed.
     */
    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("GPUDistortionGrid has been closed");
        }
    }

    private static float[] flatten(float[][] data, int width, int height) {
        var result = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, result, y * width, width);
        }
        return result;
    }
}
