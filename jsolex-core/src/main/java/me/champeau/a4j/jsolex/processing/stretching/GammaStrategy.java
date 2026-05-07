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

import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.opencl.OpenCLContext;
import me.champeau.a4j.math.opencl.OpenCLSupport;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;

public final class GammaStrategy implements StretchingStrategy {
    private static final int GPU_THRESHOLD = 65536;
    private static final int WORK_GROUP_SIZE = 256;

    private final double gamma;

    public GammaStrategy(double gamma) {
        this.gamma = gamma;
    }

    /**
     * Stretches an image using the gamma correction
     *
     * @param image grayscale image, where each pixel must be in the 0-65535 range.
     */
    @Override
    public void stretch(ImageWrapper32 image) {
        var context = OpenCLSupport.getContext();
        int n = image.width() * image.height();
        if (context != null && OpenCLSupport.isEnabled() && n >= GPU_THRESHOLD
                && context.getCapabilities().maxWorkGroupSize() >= WORK_GROUP_SIZE) {
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
        float max = 1e-7f;
        int width = image.width();
        var height = image.height();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x];
                max = Math.max(v, max);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var v = data[y][x];
                float normalized = v / max;
                float corrected = (float) Math.pow(normalized, gamma);
                data[y][x] = corrected * Constants.MAX_PIXEL_VALUE;
            }
        }
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

        int numGroups = (n + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE;
        var partialMax = new float[numGroups];

        context.runOp(op -> {
            var dataBuffer = op.allocateBuffer(n * Float.BYTES, CL_MEM_READ_WRITE);
            op.write(dataBuffer, flatData);

            var partialMaxBuffer = op.allocateBuffer(numGroups * Float.BYTES, CL_MEM_READ_WRITE);

            op.kernel("stretching", "find_max")
                    .arg(dataBuffer)
                    .arg(partialMaxBuffer)
                    .arg(n)
                    .argLocalMemory((long) WORK_GROUP_SIZE * Float.BYTES)
                    .global((long) numGroups * WORK_GROUP_SIZE)
                    .local(WORK_GROUP_SIZE)
                    .run();

            op.read(partialMaxBuffer, partialMax);

            float max = 1e-7f;
            for (float v : partialMax) {
                if (v > max) {
                    max = v;
                }
            }

            op.kernel("stretching", "gamma_stretch")
                    .arg(dataBuffer)
                    .arg(max)
                    .arg((float) gamma)
                    .arg(Constants.MAX_PIXEL_VALUE)
                    .arg(n)
                    .run(n);

            op.read(dataBuffer, flatData);
        });

        for (int y = 0; y < height; y++) {
            System.arraycopy(flatData, y * width, data[y], 0, width);
        }
    }

}
