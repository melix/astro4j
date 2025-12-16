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

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;

/**
 * Holds an image on GPU memory for multi-step processing.
 * Avoids repeated GPU↔CPU transfers during refinement loops.
 * <p>
 * This class is intended for use within a single GPU-locked context.
 * All methods must be called within {@link OpenCLContext#executeWithLock}.
 */
public class GPUImage implements AutoCloseable {
    private final int width;
    private final int height;
    private long buffer;
    private final OpenCLContext context;
    private boolean closed = false;

    private GPUImage(int width, int height, long buffer, OpenCLContext context) {
        this.width = width;
        this.height = height;
        this.buffer = buffer;
        this.context = context;
    }

    /**
     * Uploads image data from CPU to GPU.
     * Must be called within executeWithLock.
     *
     * @param data    2D image data [height][width]
     * @param width   image width
     * @param height  image height
     * @param context OpenCL context
     * @return GPU-resident image
     */
    public static GPUImage upload(float[][] data, int width, int height, OpenCLContext context) {
        var flatData = flatten(data, width, height);
        int sizeInBytes = width * height * Float.BYTES;
        long buffer = context.allocateBuffer(sizeInBytes, CL_MEM_READ_WRITE);
        context.writeBuffer(buffer, flatData);
        return new GPUImage(width, height, buffer, context);
    }

    /**
     * Creates a GPU image from an existing buffer handle.
     * The buffer ownership is transferred to this GPUImage.
     * Must be called within executeWithLock.
     *
     * @param buffer  existing GPU buffer handle
     * @param width   image width
     * @param height  image height
     * @param context OpenCL context
     * @return GPU-resident image wrapping the buffer
     */
    public static GPUImage fromBuffer(long buffer, int width, int height, OpenCLContext context) {
        return new GPUImage(width, height, buffer, context);
    }

    /**
     * Downloads image data from GPU to CPU.
     * Must be called within executeWithLock.
     *
     * @return 2D image data [height][width]
     */
    public float[][] download() {
        checkNotClosed();
        var flatData = new float[width * height];
        context.readBuffer(buffer, flatData);
        return unflatten(flatData, width, height);
    }

    /**
     * Returns the GPU buffer handle for use in kernel operations.
     * Must be called within executeWithLock.
     *
     * @return buffer handle
     */
    public long getBuffer() {
        checkNotClosed();
        return buffer;
    }

    /**
     * Returns the image width.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the image height.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the OpenCL context this image belongs to.
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
        if (!closed && buffer != 0) {
            context.releaseBuffer(buffer);
            buffer = 0;
            closed = true;
        }
    }

    /**
     * Checks if this GPU image has been closed.
     */
    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("GPUImage has been closed");
        }
    }

    private static float[] flatten(float[][] data, int width, int height) {
        var result = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, result, y * width, width);
        }
        return result;
    }

    private static float[][] unflatten(float[] data, int width, int height) {
        var result = new float[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data, y * width, result[y], 0, width);
        }
        return result;
    }
}
