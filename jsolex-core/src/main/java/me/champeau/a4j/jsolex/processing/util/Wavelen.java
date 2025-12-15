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

import java.util.Objects;

/**
 * Represents a wavelength with unit of length.
 */
public class Wavelen {
    private final double angstroms;

    private Wavelen(double angstroms) {
        this.angstroms = angstroms;
    }

    public static Wavelen ofAngstroms(double angstroms) {
        return new Wavelen(angstroms);
    }

    public static Wavelen ofNanos(double nanometers) {
        return new Wavelen(nanometers * 10);
    }

    public double nanos() {
        return angstroms / 10;
    }

    public double angstroms() {
        return angstroms;
    }

    public Wavelen plusAngstroms(double addAngstroms) {
        return new Wavelen(angstroms + addAngstroms);
    }

    public Wavelen plusNanos(double addNanos) {
        return new Wavelen(angstroms + addNanos * 10);
    }

    public Wavelen plus(double pixels, Dispersion dispersion) {
        return plusAngstroms(pixels * dispersion.angstromsPerPixel());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Wavelen wavelen)) {
            return false;
        }
        return Double.compare(angstroms, wavelen.angstroms) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(angstroms);
    }
}
