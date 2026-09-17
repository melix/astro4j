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
package me.champeau.a4j.jsolex.processing.params;

/**
 * Parameters for banding correction processing.
 *
 * @param width the band width in pixels of the banding correction
 * @param passes the number of passes of the banding correction
 * @param destripeParams the parameters of the destripe correction
 * @param method the correction algorithm to apply
 */
public record BandingCorrectionParams(
        int width,
        int passes,
        DestripeParams destripeParams,
        BandingCorrectionMethod method
) {
    /**
     * Creates banding correction parameters using the banding correction method.
     *
     * @param width the band width in pixels
     * @param passes the number of correction passes to apply
     */
    public BandingCorrectionParams(int width, int passes) {
        this(width, passes, DestripeParams.defaults(), BandingCorrectionMethod.BANDING_CORRECTION);
    }

    /**
     * Returns a copy of this instance with the specified band width.
     *
     * @param width the new band width in pixels
     * @return a new BandingCorrectionParams with the updated width
     */
    public BandingCorrectionParams withWidth(int width) {
        return new BandingCorrectionParams(width, passes, destripeParams, method);
    }

    /**
     * Returns a copy of this instance with the specified number of passes.
     *
     * @param passes the new number of correction passes
     * @return a new BandingCorrectionParams with the updated passes
     */
    public BandingCorrectionParams withPasses(int passes) {
        return new BandingCorrectionParams(width, passes, destripeParams, method);
    }

    /**
     * Returns a copy of this instance with the specified destripe parameters.
     *
     * @param destripeParams the new destripe parameters
     * @return a new BandingCorrectionParams with the updated destripe parameters
     */
    public BandingCorrectionParams withDestripeParams(DestripeParams destripeParams) {
        return new BandingCorrectionParams(width, passes, destripeParams, method);
    }

    /**
     * Returns a copy of this instance with the specified correction method.
     *
     * @param method the new correction method
     * @return a new BandingCorrectionParams with the updated method
     */
    public BandingCorrectionParams withMethod(BandingCorrectionMethod method) {
        return new BandingCorrectionParams(width, passes, destripeParams, method);
    }
}
