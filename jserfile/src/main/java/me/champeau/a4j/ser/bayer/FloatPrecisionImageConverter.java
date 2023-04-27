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
package me.champeau.a4j.ser.bayer;

import me.champeau.a4j.ser.ImageGeometry;

import java.nio.ByteBuffer;

/**
 * Each pixel in the image is represented by one byte per
 * channel (e.g in an RGB image, 3 bytes, or in a mono image, 1 byte).
 * This class is responsible for converting each byte into a float
 * value ranging from 0 to 65535.
 */
public class FloatPrecisionImageConverter implements ImageConverter<float[]> {
    private final ImageConverter<short[]> delegate;

    public FloatPrecisionImageConverter(ImageConverter<short[]> delegate) {
        this.delegate = delegate;
    }

    @Override
    public float[] createBuffer(ImageGeometry geometry) {
        return new float[delegate.createBuffer(geometry).length];
    }


    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, float[] outputData) {
        var intermediateBuffer = delegate.createBuffer(geometry);
        delegate.convert(frameId, frameData, geometry, intermediateBuffer);
        for (int i = 0; i < intermediateBuffer.length; i++) {
            outputData[i] = intermediateBuffer[i] & 0xFFFF;
        }
    }
}
