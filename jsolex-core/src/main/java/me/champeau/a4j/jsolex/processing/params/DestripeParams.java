/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.params;

/**
 * Parameters of the destripe correction.
 *
 * @param bandSize the coarsest scale, in pixels, of the correction
 * @param passes the number of passes, or -1 to iterate until convergence
 */
public record DestripeParams(
        int bandSize,
        int passes
) {
    /**
     * Default band size in pixels.
     */
    public static final int DEFAULT_BAND_SIZE = 192;

    /**
     * Number of passes meaning that passes are repeated until convergence.
     */
    public static final int AUTOMATIC_PASSES = -1;

    /**
     * Default number of passes.
     */
    public static final int DEFAULT_PASSES = AUTOMATIC_PASSES;

    /**
     * Default number of passes when they are not automatic.
     */
    public static final int DEFAULT_MANUAL_PASSES = 2;

    /**
     * Returns the default destripe parameters.
     *
     * @return the default parameters
     */
    public static DestripeParams defaults() {
        return new DestripeParams(DEFAULT_BAND_SIZE, DEFAULT_PASSES);
    }
}
