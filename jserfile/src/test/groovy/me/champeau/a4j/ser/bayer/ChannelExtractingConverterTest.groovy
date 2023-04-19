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

import me.champeau.a4j.ser.ColorMode
import me.champeau.a4j.ser.ImageGeometry
import spock.lang.Specification
import spock.lang.Subject

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChannelExtractingConverterTest extends Specification implements BayerMatrixSupport {
    @Subject
    private ChannelExtractingConverter converter

    private ImageGeometry geometry
    private byte[] buffer

    def "extracts channel #channel"() {
        given:
        newConverter(channel)

        when:
        converter.convert(0, ByteBuffer.wrap(new byte[0]), geometry, buffer)

        then:
        buffer.length == geometry.width() * geometry.height()
        buffer.each { it == channel }

        where:
        channel << [RED, GREEN, BLUE]
    }

    private void newConverter(int channel) {
        converter = new ChannelExtractingConverter(new DummyConverter(), channel)
        geometry = new ImageGeometry(ColorMode.RGB, 2, 2, 1, ByteOrder.LITTLE_ENDIAN)
        buffer = converter.createBuffer(geometry)
    }

    private static class DummyConverter implements ImageConverter<byte[]>, BayerMatrixSupport {

        @Override
        byte[] createBuffer(ImageGeometry geometry) {
            return new byte[PIXEL * geometry.width() * geometry.height()]
        }

        @Override
        void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, byte[] outputData) {
            for (int i = 0; i < outputData.length; i++) {
                int channel = i % PIXEL
                outputData[i] = (byte) channel
            }
        }
    }
}
