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
 * @param width the band width in pixels
 * @param passes the number of correction passes to apply
 * @param ellipseMode the region used by the correction (0 = whole line,
 *                    1 = inside disk, 2 = outside disk)
 * @param destripeParams optional pre-processing stripe-removal parameters
 */
public record BandingCorrectionParams(
        int width,
        int passes,
        Integer ellipseMode,
        DestripeParams destripeParams
) {
    /**
     * Creates the legacy two-parameter form.  Keeping this constructor means
     * callers and old script parameter overrides continue to compile, while
     * the new fields use the same inside-disk defaults as the old native path.
     */
    public BandingCorrectionParams(int width, int passes) {
        this(width, passes, 1, DestripeParams.disabled());
    }

    /**
     * Creates a banding configuration with an explicit ellipse mode.
     */
    public BandingCorrectionParams(int width, int passes, int ellipseMode) {
        this(width, passes, ellipseMode, DestripeParams.disabled());
    }

    /**
     * Normalizes fields which may be absent in JSON produced by older versions.
     * Gson normally invokes this compact constructor for records, but callers
     * which obtain a partially populated object can use the accessors below as
     * a second line of defence.
     */
    public BandingCorrectionParams {
        if (ellipseMode == null) {
            ellipseMode = 1;
        }
        if (destripeParams == null) {
            destripeParams = DestripeParams.disabled();
        }
    }

    /**
     * Returns a copy of this instance with the specified band width.
     *
     * @param width the new band width in pixels
     * @return a new BandingCorrectionParams with the updated width
     */
    public BandingCorrectionParams withWidth(int width) {
        return new BandingCorrectionParams(width, passes, ellipseMode(), destripeParams());
    }

    /**
     * Returns a copy of this instance with the specified number of passes.
     *
     * @param passes the new number of correction passes
     * @return a new BandingCorrectionParams with the updated passes
     */
    public BandingCorrectionParams withPasses(int passes) {
        return new BandingCorrectionParams(width, passes, ellipseMode(), destripeParams());
    }

    /**
     * Returns a copy with the selected ellipse mode.
     */
    public BandingCorrectionParams withEllipseMode(int ellipseMode) {
        return new BandingCorrectionParams(width, passes, ellipseMode, destripeParams());
    }

    /**
     * Returns a copy with the selected destripe parameters.
     */
    public BandingCorrectionParams withDestripeParams(DestripeParams destripeParams) {
        return new BandingCorrectionParams(width, passes, ellipseMode(), destripeParams);
    }

    /**
     * Returns a normalized instance, useful after reflection-based JSON
     * deserialization or script parameter overrides.
     */
    public BandingCorrectionParams normalized() {
        return new BandingCorrectionParams(width, passes, ellipseMode(), destripeParams());
    }
}
