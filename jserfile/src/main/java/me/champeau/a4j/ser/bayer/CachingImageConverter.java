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

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A converter which is responsible for caching the result of the conversion,
 * at the cost of increased memory usage.
 * @param <ARRAY> the type of the array used to store the result of the conversion
 */
public class CachingImageConverter<ARRAY> implements ImageConverter<ARRAY> {
    private final Map<Integer, ARRAY> cache = new ConcurrentHashMap<>();
    private final ImageConverter<ARRAY> delegate;

    public CachingImageConverter(ImageConverter<ARRAY> delegate) {
        this.delegate = delegate;
    }

    @Override
    public ARRAY createBuffer(ImageGeometry geometry) {
        return delegate.createBuffer(geometry);
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, ARRAY outputData) {
        ARRAY result = cache.computeIfAbsent(frameId, id -> {
            ARRAY buffer = delegate.createBuffer(geometry);
            delegate.convert(id, frameData, geometry, buffer);
            return buffer;
        });
        System.arraycopy(result, 0, outputData, 0, Array.getLength(result));
    }
}
