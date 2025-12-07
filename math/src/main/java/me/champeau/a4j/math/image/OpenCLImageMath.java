/*
 * Copyright 2023-2023 the original author or authors.
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
package me.champeau.a4j.math.image;

import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLException;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opencl.CL10.*;

/**
 * GPU-accelerated implementation of {@link ImageMath} using OpenCL.
 * Falls back to CPU implementation for small images where GPU overhead exceeds benefits.
 * <p>
 * Thread-safety: All GPU operations are synchronized using the lock in {@link OpenCLContext}
 * to prevent kernel argument race conditions when multiple threads access the GPU concurrently.
 */
class OpenCLImageMath extends VectorApiImageMath {
    private static final int DEFAULT_MIN_PIXELS_FOR_GPU = 65536; // 256x256 minimum for GPU operations
    private static final int DEFAULT_MIN_PIXELS_FOR_CONVOLUTION = 16384; // 128x128 - convolution benefits at smaller sizes

    private final OpenCLContext context;
    private final int minPixelsForGPU;
    private final int minPixelsForConvolution;
    private final boolean allowFallback;

    OpenCLImageMath(OpenCLContext context) {
        this(context, DEFAULT_MIN_PIXELS_FOR_GPU, DEFAULT_MIN_PIXELS_FOR_CONVOLUTION, true);
    }

    /**
     * Constructor for testing that allows setting custom thresholds and disabling super.
     * Use minPixels = 0 to force GPU execution for all image sizes.
     * Set allowFallback = false to throw exceptions instead of falling back to CPU.
     */
    OpenCLImageMath(OpenCLContext context, int minPixelsForGPU, int minPixelsForConvolution, boolean allowFallback) {
        this.context = context;
        this.minPixelsForGPU = minPixelsForGPU;
        this.minPixelsForConvolution = minPixelsForConvolution;
        this.allowFallback = allowFallback;
    }

    private boolean shouldUseGPU(Image image) {
        return image.width() * image.height() >= minPixelsForGPU;
    }

    private boolean shouldUseGPUForConvolution(Image image) {
        return image.width() * image.height() >= minPixelsForConvolution;
    }

    @Override
    public Image add(Image source, float f) {
        if (!shouldUseGPU(source)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU add required but image too small");
            }
            return super.add(source, f);
        }
        try {
            return addGPU(source, f);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.add(source, f);
        }
    }

    private Image addGPU(Image source, float scalar) {
        var width = source.width();
        var height = source.height();
        var n = width * height;
        var flatData = flatten(source.data(), width, height);

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputBuffer, flatData);

                var kernel = context.getKernelManager().getKernel("arithmetic", "add_scalar");
                setKernelArgs(kernel, inputBuffer, scalar, outputBuffer, n);

                executeKernel(kernel, n);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return new Image(width, height, unflatten(result, width, height));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image multiply(Image source, float f) {
        if (!shouldUseGPU(source)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU multiply required but image too small");
            }
            return super.multiply(source, f);
        }
        try {
            return multiplyGPU(source, f);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.multiply(source, f);
        }
    }

    private Image multiplyGPU(Image source, float scalar) {
        var width = source.width();
        var height = source.height();
        var n = width * height;
        var flatData = flatten(source.data(), width, height);

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputBuffer, flatData);

                var kernel = context.getKernelManager().getKernel("arithmetic", "multiply_scalar");
                setKernelArgs(kernel, inputBuffer, scalar, outputBuffer, n);

                executeKernel(kernel, n);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return new Image(width, height, unflatten(result, width, height));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image divide(Image first, Image second) {
        if (!shouldUseGPU(first)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU divide required but image too small");
            }
            return super.divide(first, second);
        }
        try {
            return divideGPU(first, second);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.divide(first, second);
        }
    }

    private Image divideGPU(Image first, Image second) {
        var width = first.width();
        var height = first.height();
        var n = width * height;
        var flatFirst = flatten(first.data(), width, height);
        var flatSecond = flatten(second.data(), width, height);

        return context.executeWithLock(() -> {
            long bufferA = 0;
            long bufferB = 0;
            long outputBuffer = 0;
            try {
                bufferA = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                bufferB = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(bufferA, flatFirst);
                context.writeBuffer(bufferB, flatSecond);

                var kernel = context.getKernelManager().getKernel("arithmetic", "divide_images");
                setKernelArgsTwoInputs(kernel, bufferA, bufferB, outputBuffer, n);

                executeKernel(kernel, n);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return new Image(width, height, unflatten(result, width, height));
            } finally {
                if (bufferA != 0) {
                    context.releaseBuffer(bufferA);
                }
                if (bufferB != 0) {
                    context.releaseBuffer(bufferB);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image multiply(Image first, Image second) {
        if (!shouldUseGPU(first)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU multiply images required but image too small");
            }
            return super.multiply(first, second);
        }
        try {
            return multiplyImagesGPU(first, second);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.multiply(first, second);
        }
    }

    private Image multiplyImagesGPU(Image first, Image second) {
        var width = first.width();
        var height = first.height();
        var n = width * height;
        var flatFirst = flatten(first.data(), width, height);
        var flatSecond = flatten(second.data(), width, height);

        return context.executeWithLock(() -> {
            long bufferA = 0;
            long bufferB = 0;
            long outputBuffer = 0;
            try {
                bufferA = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                bufferB = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(bufferA, flatFirst);
                context.writeBuffer(bufferB, flatSecond);

                var kernel = context.getKernelManager().getKernel("arithmetic", "multiply_images");
                setKernelArgsTwoInputs(kernel, bufferA, bufferB, outputBuffer, n);

                executeKernel(kernel, n);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return new Image(width, height, unflatten(result, width, height));
            } finally {
                if (bufferA != 0) {
                    context.releaseBuffer(bufferA);
                }
                if (bufferB != 0) {
                    context.releaseBuffer(bufferB);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image convolve(Image image, Kernel kernel) {
        if (!shouldUseGPUForConvolution(image)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU convolution required but image too small: " + image.width() + "x" + image.height());
            }
            return super.convolve(image, kernel);
        }
        try {
            return convolveGPU(image, kernel);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.convolve(image, kernel);
        }
    }

    private Image convolveGPU(Image image, Kernel kernel) {
        var width = image.width();
        var height = image.height();
        var n = width * height;
        var flatImage = flatten(image.data(), width, height);

        var krows = kernel.kernel();
        var kHeight = krows.length;
        var kWidth = krows[0].length;
        var flatKernel = flattenKernel(krows, kWidth, kHeight);

        return context.executeWithLock(() -> {
            long imageBuffer = 0;
            long kernelBuffer = 0;
            long outputBuffer = 0;
            try {
                imageBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                kernelBuffer = context.allocateBuffer(flatKernel.length * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(imageBuffer, flatImage);
                context.writeBuffer(kernelBuffer, flatKernel);

                var clKernel = context.getKernelManager().getKernel("convolution", "convolve2d");
                setConvolutionKernelArgs(clKernel, imageBuffer, kernelBuffer, outputBuffer,
                        width, height, kWidth, kHeight, kernel.factor());

                executeKernel2D(clKernel, width, height);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return image.withData(unflatten(result, width, height));
            } finally {
                if (imageBuffer != 0) {
                    context.releaseBuffer(imageBuffer);
                }
                if (kernelBuffer != 0) {
                    context.releaseBuffer(kernelBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    private void setKernelArgs(long kernel, long inputBuffer, float scalar, long outputBuffer, int n) {
        clSetKernelArg1p(kernel, 0, inputBuffer);
        clSetKernelArg1f(kernel, 1, scalar);
        clSetKernelArg1p(kernel, 2, outputBuffer);
        clSetKernelArg1i(kernel, 3, n);
    }

    private void setKernelArgsTwoInputs(long kernel, long bufferA, long bufferB, long outputBuffer, int n) {
        clSetKernelArg1p(kernel, 0, bufferA);
        clSetKernelArg1p(kernel, 1, bufferB);
        clSetKernelArg1p(kernel, 2, outputBuffer);
        clSetKernelArg1i(kernel, 3, n);
    }

    private void setConvolutionKernelArgs(long kernel, long imageBuffer, long kernelBuffer,
                                          long outputBuffer, int width, int height,
                                          int kWidth, int kHeight, float factor) {
        checkKernelArg(clSetKernelArg1p(kernel, 0, imageBuffer), "imageBuffer");
        checkKernelArg(clSetKernelArg1p(kernel, 1, kernelBuffer), "kernelBuffer");
        checkKernelArg(clSetKernelArg1p(kernel, 2, outputBuffer), "outputBuffer");
        checkKernelArg(clSetKernelArg1i(kernel, 3, width), "width");
        checkKernelArg(clSetKernelArg1i(kernel, 4, height), "height");
        checkKernelArg(clSetKernelArg1i(kernel, 5, kWidth), "kWidth");
        checkKernelArg(clSetKernelArg1i(kernel, 6, kHeight), "kHeight");
        checkKernelArg(clSetKernelArg1f(kernel, 7, factor), "factor");
    }

    private static void checkKernelArg(int err, String argName) {
        if (err != CL_SUCCESS) {
            throw new OpenCLException("Failed to set kernel argument '" + argName + "': " + err);
        }
    }

    private void executeKernel(long kernel, int globalSize) {
        var globalWorkSize = BufferUtils.createPointerBuffer(1);
        globalWorkSize.put(0, globalSize);

        int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel, 1,
                null, globalWorkSize, null, null, null);
        if (err != CL_SUCCESS) {
            throw new OpenCLException("Failed to execute kernel: " + err);
        }
        context.finish();
    }

    private void executeKernel2D(long kernel, int width, int height) {
        var globalWorkSize = BufferUtils.createPointerBuffer(2);
        globalWorkSize.put(0, width);
        globalWorkSize.put(1, height);

        int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel, 2,
                null, globalWorkSize, null, null, null);
        if (err != CL_SUCCESS) {
            throw new OpenCLException("Failed to execute kernel: " + err);
        }
        context.finish();
    }

    private static float[] flatten(float[][] data, int width, int height) {
        var flat = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, flat, y * width, width);
        }
        return flat;
    }

    private static float[][] unflatten(float[] flat, int width, int height) {
        var data = new float[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(flat, y * width, data[y], 0, width);
        }
        return data;
    }

    private static float[] flattenKernel(float[][] kernel, int width, int height) {
        var flat = new float[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(kernel[y], 0, flat, y * width, width);
        }
        return flat;
    }

    @Override
    public Image rotateAndScale(Image image, double angle, float blackpoint, double scaleX, double scaleY) {
        if (!shouldUseGPU(image)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU rotateAndScale required but image too small");
            }
            return super.rotateAndScale(image, angle, blackpoint, scaleX, scaleY);
        }
        try {
            return rotateAndScaleGPU(image, angle, blackpoint, scaleX, scaleY);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.rotateAndScale(image, angle, blackpoint, scaleX, scaleY);
        }
    }

    private Image rotateAndScaleGPU(Image image, double angle, float blackpoint, double scaleX, double scaleY) {
        var srcWidth = image.width();
        var srcHeight = image.height();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int dstWidth = (int) ((Math.round(Math.abs(srcWidth * cos) + Math.abs(srcHeight * sin))) * scaleX);
        int dstHeight = (int) ((Math.round(Math.abs(srcHeight * cos) + Math.abs(srcWidth * sin))) * scaleY);

        var flatInput = flatten(image.data(), srcWidth, srcHeight);
        int srcN = srcWidth * srcHeight;
        int dstN = dstWidth * dstHeight;

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(srcN * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(dstN * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputBuffer, flatInput);

                var kernel = context.getKernelManager().getKernel("transform", "rotate_scale");
                clSetKernelArg1p(kernel, 0, inputBuffer);
                clSetKernelArg1p(kernel, 1, outputBuffer);
                clSetKernelArg1i(kernel, 2, srcWidth);
                clSetKernelArg1i(kernel, 3, srcHeight);
                clSetKernelArg1i(kernel, 4, dstWidth);
                clSetKernelArg1i(kernel, 5, dstHeight);
                clSetKernelArg1f(kernel, 6, (float) cos);
                clSetKernelArg1f(kernel, 7, (float) sin);
                clSetKernelArg1f(kernel, 8, (float) scaleX);
                clSetKernelArg1f(kernel, 9, (float) scaleY);
                clSetKernelArg1f(kernel, 10, blackpoint);

                executeKernel2D(kernel, dstWidth, dstHeight);

                var result = new float[dstN];
                context.readBuffer(outputBuffer, result);

                return new Image(dstWidth, dstHeight, unflatten(result, dstWidth, dstHeight));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image rotate(Image image, double angle, float blackpoint, boolean resize) {
        if (!shouldUseGPU(image)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU rotate required but image too small");
            }
            return super.rotate(image, angle, blackpoint, resize);
        }
        try {
            return rotateGPU(image, angle, blackpoint, resize);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.rotate(image, angle, blackpoint, resize);
        }
    }

    private Image rotateGPU(Image image, double angle, float blackpoint, boolean resize) {
        var srcWidth = image.width();
        var srcHeight = image.height();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int dstWidth = resize ? (int) (Math.round(Math.abs(srcWidth * cos) + Math.abs(srcHeight * sin))) : srcWidth;
        int dstHeight = resize ? (int) (Math.round(Math.abs(srcHeight * cos) + Math.abs(srcWidth * sin))) : srcHeight;

        var flatInput = flatten(image.data(), srcWidth, srcHeight);
        int srcN = srcWidth * srcHeight;
        int dstN = dstWidth * dstHeight;

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(srcN * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(dstN * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputBuffer, flatInput);

                var kernel = context.getKernelManager().getKernel("transform", "rotate");
                clSetKernelArg1p(kernel, 0, inputBuffer);
                clSetKernelArg1p(kernel, 1, outputBuffer);
                clSetKernelArg1i(kernel, 2, srcWidth);
                clSetKernelArg1i(kernel, 3, srcHeight);
                clSetKernelArg1i(kernel, 4, dstWidth);
                clSetKernelArg1i(kernel, 5, dstHeight);
                clSetKernelArg1f(kernel, 6, (float) cos);
                clSetKernelArg1f(kernel, 7, (float) sin);
                clSetKernelArg1f(kernel, 8, blackpoint);

                executeKernel2D(kernel, dstWidth, dstHeight);

                var result = new float[dstN];
                context.readBuffer(outputBuffer, result);

                return new Image(dstWidth, dstHeight, unflatten(result, dstWidth, dstHeight));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image rescale(Image image, int newWidth, int newHeight) {
        if (!shouldUseGPU(image)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU rescale required but image too small");
            }
            return super.rescale(image, newWidth, newHeight);
        }
        try {
            return rescaleGPU(image, newWidth, newHeight);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.rescale(image, newWidth, newHeight);
        }
    }

    private Image rescaleGPU(Image image, int dstWidth, int dstHeight) {
        var srcWidth = image.width();
        var srcHeight = image.height();

        var flatInput = flatten(image.data(), srcWidth, srcHeight);
        int srcN = srcWidth * srcHeight;
        int dstN = dstWidth * dstHeight;

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(srcN * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(dstN * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputBuffer, flatInput);

                var kernel = context.getKernelManager().getKernel("transform", "rescale");
                clSetKernelArg1p(kernel, 0, inputBuffer);
                clSetKernelArg1p(kernel, 1, outputBuffer);
                clSetKernelArg1i(kernel, 2, srcWidth);
                clSetKernelArg1i(kernel, 3, srcHeight);
                clSetKernelArg1i(kernel, 4, dstWidth);
                clSetKernelArg1i(kernel, 5, dstHeight);

                executeKernel2D(kernel, dstWidth, dstHeight);

                var result = new float[dstN];
                context.readBuffer(outputBuffer, result);

                return new Image(dstWidth, dstHeight, unflatten(result, dstWidth, dstHeight));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image mirror(Image source, boolean horizontalMirror, boolean verticalMirror) {
        if (!horizontalMirror && !verticalMirror) {
            return source;
        }
        if (!shouldUseGPU(source)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU mirror required but image too small");
            }
            return super.mirror(source, horizontalMirror, verticalMirror);
        }
        try {
            return mirrorGPU(source, horizontalMirror, verticalMirror);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.mirror(source, horizontalMirror, verticalMirror);
        }
    }

    private Image mirrorGPU(Image source, boolean horizontalMirror, boolean verticalMirror) {
        var width = source.width();
        var height = source.height();
        var n = width * height;
        var flatInput = flatten(source.data(), width, height);

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);

                context.writeBuffer(inputBuffer, flatInput);

                var kernel = context.getKernelManager().getKernel("transform", "mirror");
                clSetKernelArg1p(kernel, 0, inputBuffer);
                clSetKernelArg1p(kernel, 1, outputBuffer);
                clSetKernelArg1i(kernel, 2, width);
                clSetKernelArg1i(kernel, 3, height);
                clSetKernelArg1i(kernel, 4, horizontalMirror ? 1 : 0);
                clSetKernelArg1i(kernel, 5, verticalMirror ? 1 : 0);

                executeKernel2D(kernel, width, height);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return new Image(width, height, unflatten(result, width, height));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
            }
        });
    }

    @Override
    public Image dedistort(Image source, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos) {
        if (!shouldUseGPU(source)) {
            if (!allowFallback) {
                throw new OpenCLException("GPU dedistort required but image too small");
            }
            return super.dedistort(source, gridDx, gridDy, gridStep, useLanczos);
        }
        try {
            return dedistortGPU(source, gridDx, gridDy, gridStep, useLanczos);
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return super.dedistort(source, gridDx, gridDy, gridStep, useLanczos);
        }
    }

    private Image dedistortGPU(Image source, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos) {
        var width = source.width();
        var height = source.height();
        var n = width * height;
        var flatInput = flatten(source.data(), width, height);

        var gridHeight = gridDx.length;
        var gridWidth = gridDx[0].length;
        var gridN = gridWidth * gridHeight;
        var flatGridDx = flatten(gridDx, gridWidth, gridHeight);
        var flatGridDy = flatten(gridDy, gridWidth, gridHeight);

        return context.executeWithLock(() -> {
            long inputBuffer = 0;
            long outputBuffer = 0;
            long gridDxBuffer = 0;
            long gridDyBuffer = 0;
            try {
                inputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
                outputBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);
                gridDxBuffer = context.allocateBuffer(gridN * Float.BYTES, CL_MEM_READ_ONLY);
                gridDyBuffer = context.allocateBuffer(gridN * Float.BYTES, CL_MEM_READ_ONLY);

                context.writeBuffer(inputBuffer, flatInput);
                context.writeBuffer(gridDxBuffer, flatGridDx);
                context.writeBuffer(gridDyBuffer, flatGridDy);

                var kernelName = useLanczos ? "dedistort_sparse_lanczos" : "dedistort_sparse_bilinear";
                var kernel = context.getKernelManager().getKernel("dedistort", kernelName);
                clSetKernelArg1p(kernel, 0, inputBuffer);
                clSetKernelArg1p(kernel, 1, gridDxBuffer);
                clSetKernelArg1p(kernel, 2, gridDyBuffer);
                clSetKernelArg1p(kernel, 3, outputBuffer);
                clSetKernelArg1i(kernel, 4, width);
                clSetKernelArg1i(kernel, 5, height);
                clSetKernelArg1i(kernel, 6, gridWidth);
                clSetKernelArg1i(kernel, 7, gridHeight);
                clSetKernelArg1i(kernel, 8, gridStep);

                executeKernel2D(kernel, width, height);

                var result = new float[n];
                context.readBuffer(outputBuffer, result);

                return new Image(width, height, unflatten(result, width, height));
            } finally {
                if (inputBuffer != 0) {
                    context.releaseBuffer(inputBuffer);
                }
                if (outputBuffer != 0) {
                    context.releaseBuffer(outputBuffer);
                }
                if (gridDxBuffer != 0) {
                    context.releaseBuffer(gridDxBuffer);
                }
                if (gridDyBuffer != 0) {
                    context.releaseBuffer(gridDyBuffer);
                }
            }
        });
    }

}
