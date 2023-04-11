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
package me.champeau.a4j.ser;

import java.util.Optional;

/**
 * The different kinds of color modes supported
 * in a SER file.
 */
public enum ColorMode {
    MONO(0, 1),
    BAYER_RGGB(8, 1),
    BAYER_GRBG(9, 1),
    BAYER_GBRG(10, 1),
    BAYER_BGGR(11, 1),
    BAYER_CYYM(16, 1),
    BAYER_YCMY(17, 1),
    BAYER_YMCY(18, 1),
    RGB(100, 3),
    BGR(101, 3);

    private final int value;
    private final int numberOfPlanes;

    ColorMode(int value, int numberOfPlanes) {
        this.value = value;
        this.numberOfPlanes = numberOfPlanes;
    }

    public int getValue() {
        return value;
    }

    public boolean isBayer() {
        return switch (this) {
            case BAYER_RGGB, BAYER_BGGR, BAYER_CYYM, BAYER_GBRG, BAYER_GRBG, BAYER_YCMY, BAYER_YMCY -> true;
            default -> false;
        };
    }

    public int getNumberOfPlanes() {
        return numberOfPlanes;
    }

    public static Optional<ColorMode> of(int byteValue) {
        return Optional.ofNullable(switch (byteValue) {
            case 0 -> MONO;
            case 8 -> BAYER_RGGB;
            case 9 -> BAYER_GRBG;
            case 10 -> BAYER_GBRG;
            case 11 -> BAYER_BGGR;
            case 16 -> BAYER_CYYM;
            case 17 -> BAYER_YCMY;
            case 18 -> BAYER_YMCY;
            case 100 -> RGB;
            case 101 -> BGR;
            default -> null;
        });
    }
}
