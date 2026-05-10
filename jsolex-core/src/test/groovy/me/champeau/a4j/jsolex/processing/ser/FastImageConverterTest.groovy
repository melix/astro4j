/*
 * Copyright 2023-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.ser

import me.champeau.a4j.ser.ColorMode
import me.champeau.a4j.ser.ImageGeometry
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.ByteOrder

class FastImageConverterTest extends Specification {

    // SER files store 16-bit pixels little-endian; the ByteBuffer is left in its
    // default big-endian order, matching what FastImageConverter receives in production.
    private static ByteBuffer leShorts(int[] values) {
        def bb = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN)
        values.each { bb.putShort((short) it) }
        bb.rewind()
        bb.order(ByteOrder.BIG_ENDIAN)
        return bb
    }

    def "convertBPP2 with depth=16 and vflip=false maps each pixel one-to-one"() {
        given:
        def geometry = new ImageGeometry(ColorMode.MONO, 4, 2, 16, ByteOrder.LITTLE_ENDIAN)
        def buffer = leShorts([0x0001, 0x1234, 0xFFFF, 0x8000,
                               0xABCD, 0x0FF0, 0x7FFF, 0x4000] as int[])
        def out = new float[2][4]
        def converter = new FastImageConverter(false)

        when:
        converter.convert(0, buffer, geometry, out)

        then:
        out[0] == [1f, 4660f, 65535f, 32768f] as float[]
        out[1] == [43981f, 4080f, 32767f, 16384f] as float[]
    }

    def "convertBPP2 with depth=12 and vflip=false applies 4-bit left shift truncated to 16 bits"() {
        given:
        def geometry = new ImageGeometry(ColorMode.MONO, 4, 2, 12, ByteOrder.LITTLE_ENDIAN)
        def buffer = leShorts([0x0001, 0x1234, 0xFFFF, 0x8000,
                               0xABCD, 0x0FF0, 0x7FFF, 0x4000] as int[])
        def out = new float[2][4]
        def converter = new FastImageConverter(false)

        when:
        converter.convert(0, buffer, geometry, out)

        then:
        // (value << 4) & 0xFFFF
        out[0] == [0x0010 as float, 0x2340 as float, 0xFFF0 as float, 0x0000 as float] as float[]
        out[1] == [0xBCD0 as float, 0xFF00 as float, 0xFFF0 as float, 0x0000 as float] as float[]
    }

    def "convertBPP2 with vflip=true reverses rows"() {
        given:
        def geometry = new ImageGeometry(ColorMode.MONO, 2, 3, 16, ByteOrder.LITTLE_ENDIAN)
        // row 0: 100, 200; row 1: 300, 400; row 2: 500, 600
        def buffer = leShorts([100, 200,
                               300, 400,
                               500, 600] as int[])
        def out = new float[3][2]
        def converter = new FastImageConverter(true)

        when:
        converter.convert(0, buffer, geometry, out)

        then:
        out[0] == [500f, 600f] as float[]
        out[1] == [300f, 400f] as float[]
        out[2] == [100f, 200f] as float[]
    }

    def "convertBPP2 advances the caller's ByteBuffer position by 2 * width * height"() {
        given:
        def geometry = new ImageGeometry(ColorMode.MONO, 2, 1, 16, ByteOrder.LITTLE_ENDIAN)
        def buffer = leShorts([1, 2] as int[])
        def initialPosition = buffer.position()
        def out = new float[1][2]
        def converter = new FastImageConverter(false)

        when:
        converter.convert(0, buffer, geometry, out)

        then:
        buffer.position() == initialPosition + 2 * geometry.width() * geometry.height()
    }
}
