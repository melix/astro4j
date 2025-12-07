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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;
import static org.apache.commons.math3.util.FastMath.asinh;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_SUCCESS;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clSetKernelArg;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Implements arcsinh stretching, as described in SIRIL docs:
 * https://free-astro.org/siril_doc-en/co/AsinhTransformation.html
 */
public final class ArcsinhStretchingStrategy implements StretchingStrategy {
    private static final int GPU_THRESHOLD = 65536;

    private final double blackPoint;
    private final double stretch;
    private final double maxStretch;
    private final double asinh;
    private final double normalizedBlackPoint;

    public ArcsinhStretchingStrategy(float blackPoint, float stretch, double maxStretch) {
        this.blackPoint = blackPoint;
        this.stretch = stretch;
        this.maxStretch = maxStretch;
        this.asinh = asinh(stretch);
        this.normalizedBlackPoint = blackPoint / MAX_PIXEL_VALUE;
    }

    public double getBlackPoint() {
        return blackPoint;
    }

    public double getStretch() {
        return stretch;
    }

    public double getMaxStretch() {
        return maxStretch;
    }

    @Override
    public void stretch(ImageWrapper32 image) {
        var context = OpenCLSupport.getContext();
        int n = image.width() * image.height();
        if (context != null && OpenCLSupport.isEnabled() && n >= GPU_THRESHOLD) {
            try {
                stretchGPU(context, image);
                return;
            } catch (Exception e) {
                // Fall through to CPU
            }
        }
        stretchCPU(image);
    }

    void stretchCPU(ImageWrapper32 image) {
        var data = image.data();
        var height = image.height();
        var width = image.width();
        for (int y = 0; y < height; y++) {
            var line = data[y];
            for (int x = 0; x < width; x++) {
                var v = line[x];
                var sv = stretchPixel(v);
                line[x] = sv;
                if (Float.valueOf(line[x]).isNaN()) {
                    line[x] = 0;
                }
                line[x] = Math.max(0, line[x]);
                line[x] = Math.min(MAX_PIXEL_VALUE, line[x]);
            }
        }
        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

    void stretchGPU(OpenCLContext context, ImageWrapper32 image) {
        var data = image.data();
        int width = image.width();
        int height = image.height();
        int n = width * height;

        var flatData = new float[n];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, flatData, y * width, width);
        }

        context.executeWithLock(() -> {
            long dataBuffer = 0;
            try {
                dataBuffer = context.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
                context.writeBuffer(dataBuffer, flatData);

                try (var stack = stackPush()) {
                    var kernel = context.getKernelManager().getKernel("stretching", "asinh_stretch");
                    clSetKernelArg(kernel, 0, stack.pointers(dataBuffer));
                    clSetKernelArg(kernel, 1, stack.floats((float) normalizedBlackPoint));
                    clSetKernelArg(kernel, 2, stack.floats((float) stretch));
                    clSetKernelArg(kernel, 3, stack.floats((float) asinh));
                    clSetKernelArg(kernel, 4, stack.floats(MAX_PIXEL_VALUE));
                    clSetKernelArg(kernel, 5, stack.ints(n));

                    var globalWorkSize = stack.pointers(n);
                    int err = clEnqueueNDRangeKernel(context.getCommandQueue(), kernel, 1, null, globalWorkSize, null, null, null);
                    if (err != CL_SUCCESS) {
                        throw new RuntimeException("Failed to execute kernel: " + err);
                    }
                    context.finish();
                }

                context.readBuffer(dataBuffer, flatData);
            } finally {
                if (dataBuffer != 0) {
                    context.releaseBuffer(dataBuffer);
                }
            }
        });

        for (int y = 0; y < height; y++) {
            System.arraycopy(flatData, y * width, data[y], 0, width);
        }

        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

    public float stretchPixel(float v) {
        if (v == 0) {
            return 0;
        }
        double original = v / MAX_PIXEL_VALUE;
        var pixel = Math.max(0, original - normalizedBlackPoint);
        double stretched = (pixel * asinh(original * stretch)) / (original * asinh);
        return (float) (stretched * MAX_PIXEL_VALUE);
    }

    @Override
    public void stretch(RGBImage image) {
        var rgb = new float[][][]{
            image.r(),
            image.g(),
            image.b()
        };
        double max = MAX_PIXEL_VALUE;
        var bp = blackPoint / max;
        int height = image.height();
        int width = image.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double mean = (0.2126 * rgb[0][y][x] + 0.7152 * rgb[1][y][x] + 0.0722 * rgb[2][y][x]);
                for (int j = 0; j < rgb.length; j++) {
                    double original = rgb[j][y][x] / max;
                    var pixel = Math.max(0, original - bp);
                    double stretched = (pixel * asinh(original * stretch)) / (mean * asinh);
                    rgb[j][y][x] = (float) (stretched * max);
                    if (Float.valueOf(rgb[j][y][x]).isNaN()) {
                        rgb[j][y][x] = 0;
                    }
                    rgb[j][y][x] = Math.max(0, rgb[j][y][x]);
                    rgb[j][y][x] = Math.min(MAX_PIXEL_VALUE, rgb[j][y][x]);
                }
            }
        }
        LinearStrechingStrategy.DEFAULT.stretch(image);
    }

}
