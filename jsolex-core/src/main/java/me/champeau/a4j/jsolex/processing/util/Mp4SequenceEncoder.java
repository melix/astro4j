/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.util;

import org.jcodec.api.transcode.PixelStore.LoanerPicture;
import org.jcodec.api.transcode.SinkImpl;
import org.jcodec.api.transcode.VideoFrameWithPacket;
import org.jcodec.common.Codec;
import org.jcodec.common.Format;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Packet.FrameType;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;

import java.io.Closeable;
import java.io.IOException;

/**
 * Encodes a sequence of RGB frames into an H.264 MP4 file using jcodec.
 * The RGB to YUV conversion is performed here, with full range BT.601
 * coefficients, because the conversion bundled with jcodec darkens the luma.
 */
public final class Mp4SequenceEncoder implements Closeable {
    private final SinkImpl sink;
    private final Rational fps;
    private long timestamp;
    private long frameNo;

    /**
     * Creates an encoder writing to a channel.
     *
     * @param channel the output channel, closed when the encoder is closed
     * @param fps the number of frames per second
     * @throws IOException if the output cannot be initialized
     */
    public Mp4SequenceEncoder(SeekableByteChannel channel, int fps) throws IOException {
        this.fps = new Rational(fps, 1);
        sink = SinkImpl.createWithStream(channel, Format.MOV, Codec.H264, null);
        sink.init();
    }

    /**
     * Encodes a frame given as packed 0xRRGGBB pixels.
     *
     * @param rgb the pixels, row-major
     * @param width the frame width, which must be even and identical for every frame
     * @param height the frame height, which must be even and identical for every frame
     * @throws IOException if encoding fails
     */
    public void encodeFrame(int[] rgb, int width, int height) throws IOException {
        var picture = Picture.create(width, height, ColorSpace.YUV420J);
        toYuv420(rgb, width, height, picture);
        var packet = Packet.createPacket(null, timestamp, fps.getNum(), fps.getDen(), frameNo, FrameType.KEY, null);
        sink.outputVideoFrame(new VideoFrameWithPacket(packet, new LoanerPicture(picture, 0)));
        timestamp += fps.getDen();
        frameNo++;
    }

    private static void toYuv420(int[] rgb, int width, int height, Picture picture) {
        var luma = picture.getPlaneData(0);
        var cb = picture.getPlaneData(1);
        var cr = picture.getPlaneData(2);
        var chromaWidth = width / 2;
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                var u = 0;
                var v = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        var idx = (y + dy) * width + x + dx;
                        var pixel = rgb[idx];
                        var r = (pixel >> 16) & 0xFF;
                        var g = (pixel >> 8) & 0xFF;
                        var b = pixel & 0xFF;
                        luma[idx] = (byte) (((77 * r + 150 * g + 29 * b + 128) >> 8) - 128);
                        u += (-43 * r - 85 * g + 128 * b + 128) >> 8;
                        v += (128 * r - 107 * g - 21 * b + 128) >> 8;
                    }
                }
                var chromaIdx = (y / 2) * chromaWidth + x / 2;
                cb[chromaIdx] = (byte) Math.clamp((u + 2) >> 2, -128, 127);
                cr[chromaIdx] = (byte) Math.clamp((v + 2) >> 2, -128, 127);
            }
        }
    }

    /**
     * Finishes the file and closes the output channel.
     *
     * @throws IOException if writing fails
     */
    @Override
    public void close() throws IOException {
        sink.finish();
    }
}
