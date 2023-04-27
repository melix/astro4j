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
 * An RGB image converter which adds demosaicing of the resulting
 * image.
 */
public class DemosaicingRGBImageConverter implements ImageConverter<short[]> {
    private final DemosaicingStrategy strategy;
    private final RGBImageConverter delegate;
    private final ColorMode forcedMode;

    public DemosaicingRGBImageConverter(DemosaicingStrategy strategy, ColorMode forcedMode) {
        this.strategy = strategy;
        this.delegate = new RGBImageConverter();
        this.forcedMode = forcedMode;
    }

    @Override
    public short[] createBuffer(ImageGeometry geometry) {
        return delegate.createBuffer(geometry);
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, short[] outputData) {
        delegate.convert(frameId, frameData, geometry, outputData);
        ColorMode mode = forcedMode == null ? geometry.colorMode() : forcedMode;
        if (mode.isBayer()) {
            strategy.demosaic(outputData, mode, geometry);
        }
    }
}
