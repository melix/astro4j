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
package me.champeau.a4j.jsolex.processing.util;

/**
 * Represents spectral dispersion per pixel.
 */
public class Dispersion {
    private final double angstromsPerPixel;

    private Dispersion(double angstromsPerPixel) {
        this.angstromsPerPixel = angstromsPerPixel;
    }

    public static Dispersion ofAngstromsPerPixel(double angstromsPerPixel) {
        return new Dispersion(angstromsPerPixel);
    }

    public static Dispersion ofNanosPerPixel(double nanometersPerPixel) {
        return new Dispersion(nanometersPerPixel * 10);
    }

    public double nanosPerPixel() {
        return angstromsPerPixel / 10;
    }

    public double angstromsPerPixel() {
        return angstromsPerPixel;
    }
}
