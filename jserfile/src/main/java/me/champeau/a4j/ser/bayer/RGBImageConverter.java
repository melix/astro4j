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

import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.ImageGeometry;

import java.nio.ByteBuffer;

/**
 * An image converter which converts a raw SER frame into
 * a byte[] for which each pixel is represented by an
 * (R,G,B) tuple of 3 consecutive bytes.
 */
public class RGBImageConverter implements ImageConverter<byte[]>, BayerMatrixSupport {

    @Override
    public byte[] createBuffer(ImageGeometry geometry) {
        int size = geometry.height() * geometry.width();
        return new byte[3 * size];
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, byte[] outputData) {
        int width = geometry.width();
        int height = geometry.height();
        int bytesPerPixel = geometry.getBytesPerPixel();
        int k = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte value = readByteColorValue(frameData, geometry, bytesPerPixel);
                if (geometry.colorMode() == ColorMode.MONO || geometry.colorMode().isBayer()) {
                    outputData[k + RED] = value;
                    outputData[k + GREEN] = value;
                    outputData[k + BLUE] = value;
                } else if (geometry.colorMode() == ColorMode.BGR) {
                    byte g = readByteColorValue(frameData, geometry, bytesPerPixel);
                    byte r = readByteColorValue(frameData, geometry, bytesPerPixel);
                    outputData[k + RED] = r;
                    outputData[k + GREEN] = g;
                    outputData[k + BLUE] = value;
                } else if (geometry.colorMode() == ColorMode.RGB) {
                    byte g = readByteColorValue(frameData, geometry, bytesPerPixel);
                    byte b = readByteColorValue(frameData, geometry, bytesPerPixel);
                    outputData[k + RED] = value;
                    outputData[k + GREEN] = g;
                    outputData[k + BLUE] = b;
                }
                k += 3;
            }
        }
    }

    private static byte readByteColorValue(ByteBuffer frameData, ImageGeometry geometry, int bytesPerPixel) {
        byte next;
        int bitsToDiscard = geometry.pixelDepthPerPlane() - 8;
        if (bytesPerPixel == 1) {
            // Data of between 1 and 8 bits should be stored aligned with the most significant bit
            next = (byte) (frameData.get() >> bitsToDiscard);
        } else {
            // Data between 9 and 16 bits should be stored aligned with the least significant bit
            short sixteenBit = frameData.getShort();
            next = (byte) sixteenBit;
        }
        return next;
    }
}
