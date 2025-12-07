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

// Geometric transformations with bilinear interpolation

__kernel void rotate_scale(
    __global const float* input,
    __global float* output,
    int srcWidth,
    int srcHeight,
    int dstWidth,
    int dstHeight,
    float cosAngle,
    float sinAngle,
    float scaleX,
    float scaleY,
    float blackpoint
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= dstWidth || y >= dstHeight) {
        return;
    }

    // Use integer division for center calculation to match CPU implementation
    int centerX = srcWidth / 2;
    int centerY = srcHeight / 2;
    int newCenterX = dstWidth / 2;
    int newCenterY = dstHeight / 2;

    float rx = (x - newCenterX) / scaleX;
    float ry = (y - newCenterY) / scaleY;
    float sx = rx * cosAngle + ry * sinAngle + centerX;
    float sy = -rx * sinAngle + ry * cosAngle + centerY;

    int x0 = (int)sx;
    int y0 = (int)sy;
    int x1 = x0 + 1;
    int y1 = y0 + 1;
    float fx = sx - x0;
    float fy = sy - y0;

    if (x0 >= 0 && x1 < srcWidth && y0 >= 0 && y1 < srcHeight && fx >= 0.0f && fy >= 0.0f) {
        float v00 = input[y0 * srcWidth + x0];
        float v01 = input[y1 * srcWidth + x0];
        float v10 = input[y0 * srcWidth + x1];
        float v11 = input[y1 * srcWidth + x1];

        float result = (1.0f - fx) * (1.0f - fy) * v00 +
                       fx * (1.0f - fy) * v10 +
                       (1.0f - fx) * fy * v01 +
                       fx * fy * v11;

        output[y * dstWidth + x] = result;
    } else {
        output[y * dstWidth + x] = blackpoint;
    }
}

__kernel void rotate(
    __global const float* input,
    __global float* output,
    int srcWidth,
    int srcHeight,
    int dstWidth,
    int dstHeight,
    float cosAngle,
    float sinAngle,
    float blackpoint
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= dstWidth || y >= dstHeight) {
        return;
    }

    // Use integer division for center calculation to match CPU implementation
    int centerX = srcWidth / 2;
    int centerY = srcHeight / 2;
    int newCenterX = dstWidth / 2;
    int newCenterY = dstHeight / 2;

    float rx = x - newCenterX;
    float ry = y - newCenterY;
    float sx = rx * cosAngle + ry * sinAngle + centerX;
    float sy = -rx * sinAngle + ry * cosAngle + centerY;

    int x0 = (int)sx;
    int y0 = (int)sy;
    int x1 = x0 + 1;
    int y1 = y0 + 1;
    float fx = sx - x0;
    float fy = sy - y0;

    if (x0 >= 0 && x1 < srcWidth && y0 >= 0 && y1 < srcHeight && fx >= 0.0f && fy >= 0.0f) {
        float v00 = input[y0 * srcWidth + x0];
        float v01 = input[y1 * srcWidth + x0];
        float v10 = input[y0 * srcWidth + x1];
        float v11 = input[y1 * srcWidth + x1];

        float result = (1.0f - fx) * (1.0f - fy) * v00 +
                       fx * (1.0f - fy) * v10 +
                       (1.0f - fx) * fy * v01 +
                       fx * fy * v11;

        output[y * dstWidth + x] = result;
    } else {
        output[y * dstWidth + x] = blackpoint;
    }
}

__kernel void rescale(
    __global const float* input,
    __global float* output,
    int srcWidth,
    int srcHeight,
    int dstWidth,
    int dstHeight
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= dstWidth || y >= dstHeight) {
        return;
    }

    // Use integer division for center calculation to match CPU implementation
    int centerX = srcWidth / 2;
    int centerY = srcHeight / 2;
    int newCenterX = dstWidth / 2;
    int newCenterY = dstHeight / 2;
    float scaleX = (float)dstWidth / srcWidth;
    float scaleY = (float)dstHeight / srcHeight;

    float rx = (x - newCenterX) / scaleX;
    float ry = (y - newCenterY) / scaleY;
    float sx = rx + centerX;
    float sy = ry + centerY;

    int x0 = (int)floor(sx);
    int y0 = (int)floor(sy);
    int x1 = x0 + 1;
    int y1 = y0 + 1;
    float fx = sx - x0;
    float fy = sy - y0;

    if (x0 >= 0 && x1 < srcWidth && y0 >= 0 && y1 < srcHeight) {
        float v00 = input[y0 * srcWidth + x0];
        float v01 = input[y1 * srcWidth + x0];
        float v10 = input[y0 * srcWidth + x1];
        float v11 = input[y1 * srcWidth + x1];

        float result = (1.0f - fx) * (1.0f - fy) * v00 +
                       fx * (1.0f - fy) * v10 +
                       (1.0f - fx) * fy * v01 +
                       fx * fy * v11;

        output[y * dstWidth + x] = result;
    } else {
        output[y * dstWidth + x] = 0.0f;
    }
}

__kernel void mirror(
    __global const float* input,
    __global float* output,
    int width,
    int height,
    int horizontalMirror,
    int verticalMirror
) {
    int x = get_global_id(0);
    int y = get_global_id(1);

    if (x >= width || y >= height) {
        return;
    }

    int sx = horizontalMirror ? (width - x - 1) : x;
    int sy = verticalMirror ? (height - y - 1) : y;

    output[y * width + x] = input[sy * width + sx];
}
