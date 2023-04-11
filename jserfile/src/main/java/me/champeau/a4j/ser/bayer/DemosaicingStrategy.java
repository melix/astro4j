/*
 * Copyright 2023 the original author or authors.
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

/**
 * Interface for demosaicing algorithms, working on an RGB
 * byte array.
 */
public interface DemosaicingStrategy {
    /**
     * @return the RGB byte array
     */
    byte[] getRgb();

    /**
     * @return the color mode of the image to debayer
     */
    ColorMode getColorMode();

    /**
     * @return the image geometry
     */
    ImageGeometry getGeometry();

    /**
     * Performs demosaicing. For performance, it is expected that the
     * original byte[] is mutated, so if a particular algorithm cannot
     * work directly on the original array, it must create an internal
     * copy first, then mutate the original array before returning.
     */
    void demosaic();
}
