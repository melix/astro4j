/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.server

import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.ByteOrder

import static me.champeau.a4j.jsolex.server.FrameDecoder.PixelFormat

class FrameDecoderTest extends Specification {

    def "decodes mono frames into the 16-bit range (#format)"() {
        given:
        def body = samples(format, [0, 100, 200, 30, 40, 50])

        when:
        def image = FrameDecoder.decode(body, 3, 2, format)

        then:
        image.width() == 3
        image.height() == 2
        image.data()[0] as List == [0f, 100f * scale, 200f * scale]
        image.data()[1] as List == [30f * scale, 40f * scale, 50f * scale]

        where:
        format            | scale
        PixelFormat.MONO8 | 256
        PixelFormat.MONO16 | 1
        PixelFormat.MONO12 | 1
    }

    def "averages the channels of colour frames (#format)"() {
        given: "two pixels whose channels average to 100 and 180"
        def body = samples(format, [50, 100, 150, 100, 200, 240])

        when:
        def image = FrameDecoder.decode(body, 2, 1, format)

        then:
        image.data()[0] as List == [100f * scale, 180f * scale]

        where:
        format            | scale
        PixelFormat.RGB24 | 256
        PixelFormat.RGB48 | 1
    }

    def "skips the padding byte of RGB32 frames"() {
        given:
        def body = [50, 100, 150, 255, 100, 200, 240, 7].collect { (byte) it } as byte[]

        when:
        def image = FrameDecoder.decode(body, 2, 1, PixelFormat.RGB32)

        then:
        image.data()[0] as List == [100f * 256, 180f * 256]
    }

    def "brings every phase of a Bayer mosaic to the same mean"() {
        given: "a flat field seen through a mosaic whose red, green and blue pixels differ"
        def width = 4
        def height = 4
        def values = []
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                values << (y % 2 == 0 ? (x % 2 == 0 ? 400 : 800) : (x % 2 == 0 ? 800 : 200))
            }
        }
        def body = samples(PixelFormat.RAW16, values)

        when:
        def image = FrameDecoder.decode(body, width, height, PixelFormat.RAW16)

        then:
        image.data().every { row -> row.every { Math.abs(it - 550f) < 1e-3 } }
    }

    def "rejects a body whose length does not match the format"() {
        when:
        FrameDecoder.decode(new byte[5], 2, 2, PixelFormat.MONO16)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("8 bytes")
        e.message.contains("MONO16")
    }

    def "parses format names regardless of case"() {
        expect:
        PixelFormat.parse("mono16").get() == PixelFormat.MONO16
        PixelFormat.parse(" RGB24 ").get() == PixelFormat.RGB24
        PixelFormat.parse("YUY2").isEmpty()
        PixelFormat.parse(null).isEmpty()
    }

    private static byte[] samples(PixelFormat format, List<Integer> values) {
        def bytesPerSample = format == PixelFormat.MONO8 || format == PixelFormat.RAW8 || format == PixelFormat.RGB24 || format == PixelFormat.RGB32 ? 1 : 2
        def buffer = ByteBuffer.allocate(values.size() * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        values.each { v ->
            if (bytesPerSample == 1) {
                buffer.put((byte) v)
            } else {
                buffer.putShort((short) v)
            }
        }
        return buffer.array()
    }
}
