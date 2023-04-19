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

/**
 * Defines constants used in classes which need to be aware
 * of the bayer matrix.
 */
public interface BayerMatrixSupport {
    /**
     * Index of RED pixels in an RGB byte arrays
     */
    int RED = 0;
    /**
     * Index of GREEN pixels in RGB byte arrays
     */
    int GREEN = 1;
    /**
     * Index of BLUE pixels in RGB byte arrays
     */
    int BLUE = 2;

    /**
     * The number of bytes used to represent a single
     * pixel.
     */
    int PIXEL = 3;
}
