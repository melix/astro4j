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
 * (R,G,B) tuple of 3 consecutive words.
 */
public class RGBImageConverter implements ImageConverter<short[]>, BayerMatrixSupport {

    /** Constructs an RGB image converter. */
    public RGBImageConverter() {
    }

    @Override
    public short[] createBuffer(ImageGeometry geometry) {
        int size = geometry.height() * geometry.width();
        return new short[3 * size];
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, short[] outputData) {
        var isBayer = geometry.colorMode().isBayer();
        int width = geometry.width();
        int height = geometry.height();
        int bytesPerPixel = geometry.getBytesPerPixel();
        int bitsToDiscard = bytesPerPixel == 1 ? 8 - geometry.pixelDepthPerPlane() : 16 - geometry.pixelDepthPerPlane();
        if (geometry.colorMode() == ColorMode.MONO || isBayer) {
            convertMono(frameData, outputData, height, width, bytesPerPixel, bitsToDiscard);
        } else if (geometry.colorMode() == ColorMode.BGR) {
            convertBGR(frameData, outputData, height, width, bytesPerPixel, bitsToDiscard);
        } else if (geometry.colorMode() == ColorMode.RGB) {
            convertRGB(frameData, outputData, height, width, bytesPerPixel, bitsToDiscard);
        }
    }

    private static void convertBGR(ByteBuffer frameData, short[] outputData, int height, int width, int bytesPerPixel, int bitsToDiscard) {
        int k = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                short b = readColor(frameData, bytesPerPixel, bitsToDiscard);
                short g = readColor(frameData, bytesPerPixel, bitsToDiscard);
                short r = readColor(frameData, bytesPerPixel, bitsToDiscard);
                outputData[k + RED] = r;
                outputData[k + GREEN] = g;
                outputData[k + BLUE] = b;
                k += 3;
            }
        }
    }

    private static void convertRGB(ByteBuffer frameData, short[] outputData, int height, int width, int bytesPerPixel, int bitsToDiscard) {
        int k = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                short r = readColor(frameData, bytesPerPixel, bitsToDiscard);
                short g = readColor(frameData, bytesPerPixel, bitsToDiscard);
                short b = readColor(frameData, bytesPerPixel, bitsToDiscard);
                outputData[k + RED] = r;
                outputData[k + GREEN] = g;
                outputData[k + BLUE] = b;
                k += 3;
            }
        }
    }

    private static void convertMono(ByteBuffer frameData, short[] outputData, int height, int width, int bytesPerPixel, int bitsToDiscard) {
        int k = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                short value = readColor(frameData, bytesPerPixel, bitsToDiscard);
                outputData[k + RED] = value;
                outputData[k + GREEN] = value;
                outputData[k + BLUE] = value;
                k += 3;
            }
        }
    }

    private static short readColor(ByteBuffer frameData, int bytesPerPixel, int bitsToDiscard) {
        short next;
        if (bytesPerPixel == 1) {
            // Data of between 1 and 8 bits should be stored aligned with the most significant bit
            int v = frameData.get() >> bitsToDiscard;
            next = (short) ((v & 0xFF) << 8);
        } else {
            // Data between 9 and 16 bits should be stored aligned with the least significant bit
            var v = Short.reverseBytes(frameData.getShort()) & 0xFFFF;
            next = (short) (v << bitsToDiscard);
        }
        return next;
    }
}
