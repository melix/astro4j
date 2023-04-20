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
package me.champeau.a4j.ser.bayer

import me.champeau.a4j.ser.ImageGeometry
import spock.lang.Specification
import spock.lang.Subject

import java.nio.ByteBuffer

class FloatPrecisionImageConverterTest extends Specification {
    private static final double EPSILON = 0.000001d;

    @Subject
    private FloatPrecisionImageConverter converter = new FloatPrecisionImageConverter(
            new DummyConverter()
    )

    def "converts to double array"() {
        when:
        def buffer = converter.createBuffer(null)
        converter.convert(0, null, null, buffer)

        then:
        assertEquals(buffer[0], 0.0d)
        assertEquals(buffer[1], 32d)
        assertEquals(buffer[2], 255d)
    }

    static void assertEquals(double a, double b) {
        assert Math.abs(a - b) < EPSILON
    }

    private static class DummyConverter implements ImageConverter<byte[]> {

        @Override
        byte[] createBuffer(ImageGeometry geometry) {
            return new byte[3];
        }

        @Override
        void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, byte[] outputData) {
            outputData[0] = (byte) 0;
            outputData[1] = (byte) 32;
            outputData[2] = (byte) 255;
        }
    }

}
