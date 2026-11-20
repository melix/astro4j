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

import me.champeau.a4j.math.opencl.GpuOp;
import me.champeau.a4j.math.opencl.KernelCall;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLException;
import org.lwjgl.BufferUtils;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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
        return shouldUseGPU(image.width(), image.height());
    }

    private boolean shouldUseGPU(int width, int height) {
        return width * height >= minPixelsForGPU;
    }

    private boolean shouldUseGPUForConvolution(Image image) {
        return shouldUseGPUForConvolution(image.width(), image.height());
    }

    private boolean shouldUseGPUForConvolution(int width, int height) {
        return width * height >= minPixelsForConvolution;
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

        return context.runOp(op -> {
            var inputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var outputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.write(inputBuffer, flatData);
            op.kernel("arithmetic", "add_scalar")
                    .arg(inputBuffer)
                    .arg(scalar)
                    .arg(outputBuffer)
                    .arg(n)
                    .run(n);
            var result = new float[n];
            op.read(outputBuffer, result);
            return new Image(width, height, unflatten(result, width, height));
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

        return context.runOp(op -> {
            var inputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var outputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.write(inputBuffer, flatData);
            op.kernel("arithmetic", "multiply_scalar")
                    .arg(inputBuffer)
                    .arg(scalar)
                    .arg(outputBuffer)
                    .arg(n)
                    .run(n);
            var result = new float[n];
            op.read(outputBuffer, result);
            return new Image(width, height, unflatten(result, width, height));
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

        return context.runOp(op -> {
            var bufferA = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var bufferB = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var outputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.write(bufferA, flatFirst);
            op.write(bufferB, flatSecond);
            op.kernel("arithmetic", "divide_images")
                    .arg(bufferA)
                    .arg(bufferB)
                    .arg(outputBuffer)
                    .arg(n)
                    .run(n);
            var result = new float[n];
            op.read(outputBuffer, result);
            return new Image(width, height, unflatten(result, width, height));
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

        return context.runOp(op -> {
            var bufferA = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var bufferB = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var outputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);
            op.write(bufferA, flatFirst);
            op.write(bufferB, flatSecond);
            op.kernel("arithmetic", "multiply_images")
                    .arg(bufferA).arg(bufferB).arg(outputBuffer).arg(n)
                    .run(n);
            var result = new float[n];
            op.read(outputBuffer, result);
            return new Image(width, height, unflatten(result, width, height));
        });
    }

    @Override
    public Image convolve(Image image, Kernel kernel) {
        return runOnGPU(shouldUseGPUForConvolution(image), "convolution", image.width(), image.height(),
                () -> convolveGPU(imageInput(image), image.width(), image.height(), OpenCLImageMath::readImage, kernel),
                () -> super.convolve(image, kernel));
    }

    @Override
    public double estimateSharpness(ImagePlane plane) {
        return PlaneOperations.estimateSharpness(convolve(plane, Kernel33.LAPLACIAN), convolve(plane, Kernel33.LAPLACIAN_B));
    }

    @Override
    public ImagePlane convolve(ImagePlane plane, Kernel kernel) {
        return runOnGPU(shouldUseGPUForConvolution(plane.width(), plane.height()), "convolution", plane.width(), plane.height(),
                () -> convolveGPU(planeInput(plane), plane.width(), plane.height(), OpenCLImageMath::readPlane, kernel),
                () -> super.convolve(plane, kernel));
    }

    private <T> T convolveGPU(GpuInput input, int width, int height, GpuOutput<T> output, Kernel kernel) {
        var n = width * height;
        return context.runOp(op -> {
            var plan = prepareConvolutionPlan(op, kernel);
            var imageBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            input.write(op, imageBuffer);
            long tempBuffer = plan.separable() ? op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE) : 0;
            var outputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
            enqueueConvolution(op, plan, imageBuffer, tempBuffer, outputBuffer, width, height);
            return output.read(op, outputBuffer, width, height);
        });
    }

    private <T> T runOnGPU(boolean useGPU, String operation, int width, int height, Supplier<T> gpu, Supplier<T> cpu) {
        if (!useGPU) {
            if (!allowFallback) {
                throw new OpenCLException("GPU " + operation + " required but image too small: " + width + "x" + height);
            }
            return cpu.get();
        }
        try {
            return gpu.get();
        } catch (OpenCLException e) {
            if (!allowFallback) {
                throw e;
            }
            return cpu.get();
        }
    }

    @FunctionalInterface
    private interface GpuInput {
        void write(GpuOp op, long buffer);
    }

    @FunctionalInterface
    private interface GpuOutput<T> {
        T read(GpuOp op, long buffer, int width, int height);
    }

    private static GpuInput imageInput(Image image) {
        var flat = flatten(image.data(), image.width(), image.height());
        return (op, buffer) -> op.write(buffer, flat);
    }

    private static GpuInput planeInput(ImagePlane plane) {
        return (op, buffer) -> op.write(buffer, plane.segment());
    }

    private static Image readImage(GpuOp op, long buffer, int width, int height) {
        var result = new float[width * height];
        op.read(buffer, result);
        return new Image(width, height, unflatten(result, width, height));
    }

    private static ImagePlane readPlane(GpuOp op, long buffer, int width, int height) {
        var result = ImagePlane.allocate(width, height);
        op.read(buffer, result.segment());
        return result;
    }

    /**
     * GPU buffers and metadata for one convolution kernel within a
     * {@link GpuOp}. Either the separable row/col term buffers are set, or
     * the flat 2D kernel buffer.
     */
    private record GpuConvolutionPlan(float factor, int kernelWidth, int kernelHeight,
                                      long[] rowBuffers, long[] colBuffers, long kernel2d) {
        boolean separable() {
            return rowBuffers != null;
        }
    }

    private GpuConvolutionPlan prepareConvolutionPlan(GpuOp op, Kernel kernel) {
        var krows = kernel.kernel();
        var kHeight = krows.length;
        var kWidth = krows[0].length;
        var decomposition = KernelDecomposition.decompose(kernel)
                .filter(d -> d.cheaperThan2D(kWidth, kHeight));
        if (decomposition.isPresent()) {
            var terms = decomposition.get().terms();
            var rowBuffers = new long[terms.size()];
            var colBuffers = new long[terms.size()];
            for (int t = 0; t < terms.size(); t++) {
                var term = terms.get(t);
                rowBuffers[t] = op.allocateBuffer(term.row().length * Float.BYTES, CL_MEM_READ_ONLY);
                colBuffers[t] = op.allocateBuffer(term.col().length * Float.BYTES, CL_MEM_READ_ONLY);
                op.write(rowBuffers[t], term.row());
                op.write(colBuffers[t], term.col());
            }
            return new GpuConvolutionPlan(kernel.factor(), kWidth, kHeight, rowBuffers, colBuffers, 0);
        }
        var flatKernel = flattenKernel(krows, kWidth, kHeight);
        var kernelBuffer = op.allocateBuffer(flatKernel.length * Float.BYTES, CL_MEM_READ_ONLY);
        op.write(kernelBuffer, flatKernel);
        return new GpuConvolutionPlan(kernel.factor(), kWidth, kHeight, null, null, kernelBuffer);
    }

    private void enqueueConvolution(GpuOp op, GpuConvolutionPlan plan, long input, long temp, long output, int width, int height) {
        if (!plan.separable()) {
            long kernelBytes = (long) plan.kernelWidth() * plan.kernelHeight() * Float.BYTES;
            var kernelName = kernelBytes <= context.getCapabilities().maxConstantBufferSize() ? "convolve2d_const" : "convolve2d";
            op.kernel("convolution", kernelName)
                    .arg(input).arg(plan.kernel2d()).arg(output)
                    .arg(width).arg(height).arg(plan.kernelWidth()).arg(plan.kernelHeight()).arg(plan.factor())
                    .run(width, height);
            return;
        }
        int last = plan.rowBuffers().length - 1;
        for (int t = 0; t <= last; t++) {
            op.kernel("convolution", "convolve1d_horizontal")
                    .arg(input).arg(plan.rowBuffers()[t]).arg(temp)
                    .arg(width).arg(height).arg(plan.kernelWidth())
                    .run(width, height);
            op.kernel("convolution", "convolve1d_vertical")
                    .arg(temp).arg(plan.colBuffers()[t]).arg(output)
                    .arg(width).arg(height).arg(plan.kernelHeight()).arg(plan.factor())
                    .arg(t > 0 ? 1 : 0).arg(t == last ? 1 : 0)
                    .run(width, height);
        }
    }

    /**
     * Richardson-Lucy deconvolution with the estimate resident on the GPU
     * across iterations: the image and PSF are uploaded once and only the
     * final estimate is downloaded, instead of a full image round-trip per
     * convolution. Semantics match {@link Deconvolution#richardsonLucy}.
     */
    Image richardsonLucy(Image image, Image psf, int iterations) {
        var psfFlipped = super.mirror(psf, false, true);
        var psfKernel = ImageKernel.of(psf);
        var psfFlippedKernel = ImageKernel.of(psfFlipped);
        var width = image.width();
        var height = image.height();
        var n = width * height;
        var flatImage = flatten(image.data(), width, height);

        return context.runOp(op -> {
            var psfPlan = prepareConvolutionPlan(op, psfKernel);
            var psfFlippedPlan = prepareConvolutionPlan(op, psfFlippedKernel);
            var imageBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var estimateBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
            var convBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
            var ratioBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
            var tempBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
            op.write(imageBuffer, flatImage);
            op.write(estimateBuffer, flatImage);

            for (int i = 0; i < iterations; i++) {
                enqueueConvolution(op, psfPlan, estimateBuffer, tempBuffer, convBuffer, width, height);
                op.kernel("deconvolution", "rl_ratio")
                        .arg(imageBuffer).arg(convBuffer).arg(ratioBuffer)
                        .arg(Deconvolution.EPSILON).arg(n)
                        .run(n);
                enqueueConvolution(op, psfFlippedPlan, ratioBuffer, tempBuffer, convBuffer, width, height);
                op.kernel("deconvolution", "rl_update")
                        .arg(estimateBuffer).arg(convBuffer).arg(n)
                        .run(n);
            }

            var result = new float[n];
            op.read(estimateBuffer, result);
            return new Image(width, height, unflatten(result, width, height));
        });
    }

    boolean supportsResidentDeconvolution(Image image) {
        return shouldUseGPUForConvolution(image);
    }

    boolean allowsFallback() {
        return allowFallback;
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
        return runOnGPU(shouldUseGPU(image), "rotateAndScale", image.width(), image.height(),
                () -> rotateAndScaleGPU(imageInput(image), image.width(), image.height(), OpenCLImageMath::readImage, angle, blackpoint, scaleX, scaleY),
                () -> super.rotateAndScale(image, angle, blackpoint, scaleX, scaleY));
    }

    @Override
    public ImagePlane rotateAndScale(ImagePlane plane, double angle, float blackpoint, double scaleX, double scaleY) {
        return runOnGPU(shouldUseGPU(plane.width(), plane.height()), "rotateAndScale", plane.width(), plane.height(),
                () -> rotateAndScaleGPU(planeInput(plane), plane.width(), plane.height(), OpenCLImageMath::readPlane, angle, blackpoint, scaleX, scaleY),
                () -> super.rotateAndScale(plane, angle, blackpoint, scaleX, scaleY));
    }

    private <T> T rotateAndScaleGPU(GpuInput input, int srcWidth, int srcHeight, GpuOutput<T> output,
                                    double angle, float blackpoint, double scaleX, double scaleY) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int dstWidth = (int) ((Math.round(Math.abs(srcWidth * cos) + Math.abs(srcHeight * sin))) * scaleX);
        int dstHeight = (int) ((Math.round(Math.abs(srcHeight * cos) + Math.abs(srcWidth * sin))) * scaleY);
        return transformGPU(input, srcWidth, srcHeight, dstWidth, dstHeight, output, "rotate_scale", call -> call
                .arg(srcWidth).arg(srcHeight).arg(dstWidth).arg(dstHeight)
                .arg((float) cos).arg((float) sin)
                .arg((float) scaleX).arg((float) scaleY)
                .arg(blackpoint));
    }

    @Override
    public Image rotate(Image image, double angle, float blackpoint, boolean resize) {
        return runOnGPU(shouldUseGPU(image), "rotate", image.width(), image.height(),
                () -> rotateGPU(imageInput(image), image.width(), image.height(), OpenCLImageMath::readImage, angle, blackpoint, resize),
                () -> super.rotate(image, angle, blackpoint, resize));
    }

    @Override
    public ImagePlane rotate(ImagePlane plane, double angle, float blackpoint, boolean resize) {
        return runOnGPU(shouldUseGPU(plane.width(), plane.height()), "rotate", plane.width(), plane.height(),
                () -> rotateGPU(planeInput(plane), plane.width(), plane.height(), OpenCLImageMath::readPlane, angle, blackpoint, resize),
                () -> super.rotate(plane, angle, blackpoint, resize));
    }

    private <T> T rotateGPU(GpuInput input, int srcWidth, int srcHeight, GpuOutput<T> output,
                            double angle, float blackpoint, boolean resize) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int dstWidth = resize ? (int) (Math.round(Math.abs(srcWidth * cos) + Math.abs(srcHeight * sin))) : srcWidth;
        int dstHeight = resize ? (int) (Math.round(Math.abs(srcHeight * cos) + Math.abs(srcWidth * sin))) : srcHeight;
        return transformGPU(input, srcWidth, srcHeight, dstWidth, dstHeight, output, "rotate", call -> call
                .arg(srcWidth).arg(srcHeight).arg(dstWidth).arg(dstHeight)
                .arg((float) cos).arg((float) sin).arg(blackpoint));
    }

    @Override
    public Image rescale(Image image, int newWidth, int newHeight) {
        return runOnGPU(shouldUseGPU(image), "rescale", image.width(), image.height(),
                () -> rescaleGPU(imageInput(image), image.width(), image.height(), OpenCLImageMath::readImage, newWidth, newHeight),
                () -> super.rescale(image, newWidth, newHeight));
    }

    @Override
    public ImagePlane rescale(ImagePlane plane, int newWidth, int newHeight) {
        return runOnGPU(shouldUseGPU(plane.width(), plane.height()), "rescale", plane.width(), plane.height(),
                () -> rescaleGPU(planeInput(plane), plane.width(), plane.height(), OpenCLImageMath::readPlane, newWidth, newHeight),
                () -> super.rescale(plane, newWidth, newHeight));
    }

    private <T> T rescaleGPU(GpuInput input, int srcWidth, int srcHeight, GpuOutput<T> output, int dstWidth, int dstHeight) {
        return transformGPU(input, srcWidth, srcHeight, dstWidth, dstHeight, output, "rescale", call -> call
                .arg(srcWidth).arg(srcHeight).arg(dstWidth).arg(dstHeight));
    }

    @Override
    public Image mirror(Image source, boolean horizontalMirror, boolean verticalMirror) {
        if (!horizontalMirror && !verticalMirror) {
            return source;
        }
        return runOnGPU(shouldUseGPU(source), "mirror", source.width(), source.height(),
                () -> mirrorGPU(imageInput(source), source.width(), source.height(), OpenCLImageMath::readImage, horizontalMirror, verticalMirror),
                () -> super.mirror(source, horizontalMirror, verticalMirror));
    }

    @Override
    public ImagePlane mirror(ImagePlane plane, boolean horizontalMirror, boolean verticalMirror) {
        if (!horizontalMirror && !verticalMirror) {
            return super.mirror(plane, false, false);
        }
        return runOnGPU(shouldUseGPU(plane.width(), plane.height()), "mirror", plane.width(), plane.height(),
                () -> mirrorGPU(planeInput(plane), plane.width(), plane.height(), OpenCLImageMath::readPlane, horizontalMirror, verticalMirror),
                () -> super.mirror(plane, horizontalMirror, verticalMirror));
    }

    private <T> T mirrorGPU(GpuInput input, int width, int height, GpuOutput<T> output, boolean horizontalMirror, boolean verticalMirror) {
        return transformGPU(input, width, height, width, height, output, "mirror", call -> call
                .arg(width).arg(height)
                .arg(horizontalMirror ? 1 : 0).arg(verticalMirror ? 1 : 0));
    }

    private <T> T transformGPU(GpuInput input, int srcWidth, int srcHeight, int dstWidth, int dstHeight,
                               GpuOutput<T> output, String kernelName, UnaryOperator<KernelCall> arguments) {
        int srcN = srcWidth * srcHeight;
        int dstN = dstWidth * dstHeight;
        return context.runOp(op -> {
            var inputBuffer = op.allocateBuffer(srcN * Float.BYTES, CL_MEM_READ_ONLY);
            var outputBuffer = op.allocateBuffer(dstN * Float.BYTES, CL_MEM_WRITE_ONLY);
            input.write(op, inputBuffer);
            arguments.apply(op.kernel("transform", kernelName).arg(inputBuffer).arg(outputBuffer))
                    .run(dstWidth, dstHeight);
            return output.read(op, outputBuffer, dstWidth, dstHeight);
        });
    }

    @Override
    public Image dedistort(Image source, float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos) {
        return runOnGPU(shouldUseGPU(source), "dedistort", source.width(), source.height(),
                () -> dedistortGPU(imageInput(source), source.width(), source.height(), OpenCLImageMath::readImage, gridDx, gridDy, gridStep, useLanczos),
                () -> super.dedistort(source, gridDx, gridDy, gridStep, useLanczos));
    }

    private <T> T dedistortGPU(GpuInput input, int width, int height, GpuOutput<T> output,
                               float[][] gridDx, float[][] gridDy, int gridStep, boolean useLanczos) {
        var n = width * height;
        var gridHeight = gridDx.length;
        var gridWidth = gridDx[0].length;
        var gridN = gridWidth * gridHeight;
        var flatGridDx = flatten(gridDx, gridWidth, gridHeight);
        var flatGridDy = flatten(gridDy, gridWidth, gridHeight);

        var kernelName = useLanczos ? "dedistort_sparse_lanczos" : "dedistort_sparse_bilinear";
        return context.runOp(op -> {
            var inputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_ONLY);
            var outputBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_WRITE_ONLY);
            var gridDxBuffer = op.allocateBuffer(gridN * Float.BYTES, CL_MEM_READ_ONLY);
            var gridDyBuffer = op.allocateBuffer(gridN * Float.BYTES, CL_MEM_READ_ONLY);
            input.write(op, inputBuffer);
            op.write(gridDxBuffer, flatGridDx);
            op.write(gridDyBuffer, flatGridDy);
            op.kernel("dedistort", kernelName)
                    .arg(inputBuffer).arg(gridDxBuffer).arg(gridDyBuffer).arg(outputBuffer)
                    .arg(width).arg(height).arg(gridWidth).arg(gridHeight).arg(gridStep)
                    .arg(width).arg(height).arg(1.0f)
                    .run(width, height);
            return output.read(op, outputBuffer, width, height);
        });
    }

}
