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

import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.PIXEL;

/**
 * A converter which will extract a single channel from an RGB image.
 */
public class ChannelExtractingConverter implements ImageConverter<short[]> {
    private final ImageConverter<short[]> delegate;
    private final int channel;

    public ChannelExtractingConverter(ImageConverter<short[]> delegate, int channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public short[] createBuffer(ImageGeometry geometry) {
        int height = geometry.height();
        int width = geometry.width();
        return new short[height * width];
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, short[] outputData) {
        var intermediateBuffer = delegate.createBuffer(geometry);
        delegate.convert(frameId, frameData, geometry, intermediateBuffer);
        int height = geometry.height();
        int width = geometry.width();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                short r = intermediateBuffer[(y * width + x) * PIXEL + channel];
                outputData[y * width + x] = r;
            }
        }
    }
}
