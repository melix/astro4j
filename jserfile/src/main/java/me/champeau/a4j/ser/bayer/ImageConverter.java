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
 * Interface for image converters responsible
 * for converting the raw byte[] from each
 * frame into RGB images (or any other relevant
 * format for image processing)
 */
public interface ImageConverter<ARRAY> {

    /**
     * Creates a byte[] which can be used to write
     * the converted image. This buffer can be reused
     * in order to reduce GC pressure.
     * @param geometry the image geometry
     * @return a buffer, to be passed to the {@link #convert}
     * method.
     */
    ARRAY createBuffer(ImageGeometry geometry);

    /**
     * Converts a single frame.
     *
     * @param frameId the frame id
     * @param frameData the raw frame image data
     * @param geometry the image geometry
     * @param outputData the output buffer
     */
    void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, ARRAY outputData);
}
