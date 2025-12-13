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

/** An image converter that performs no conversion. */
public class NoOpConverter implements ImageConverter<byte[]> {

    /** Constructs a no-op converter. */
    public NoOpConverter() {
    }

    @Override
    public byte[] createBuffer(ImageGeometry geometry) {
        return new byte[geometry.getBytesPerFrame()];
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, byte[] outputData) {
        System.arraycopy(frameData.array(), 0, outputData, 0, outputData.length);
    }
}
