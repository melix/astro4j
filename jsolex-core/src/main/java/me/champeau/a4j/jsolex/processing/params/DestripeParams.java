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
 * Parameters for the additive, multi-scale stripe removal stage.
 *
 * <p>The defaults intentionally match the ImageMath invocation which is useful
 * for reconstructed Sol'Ex images.  The stage is disabled by default so that
 * configurations written by older JSol'Ex versions retain their behaviour.</p>
 */
public record DestripeParams(
        boolean enabled,
        int bandSize,
        int passes,
        int strips,
        int ellipseMode
) {
    public static final int DEFAULT_BAND_SIZE = 192;
    public static final int DEFAULT_PASSES = -1;
    public static final int DEFAULT_STRIPS = 1;
    public static final int DEFAULT_ELLIPSE_MODE = 1;

    /**
     * Returns the disabled defaults used when loading a legacy configuration.
     *
     * @return disabled destripe parameters
     */
    public static DestripeParams disabled() {
        return new DestripeParams(false, DEFAULT_BAND_SIZE, DEFAULT_PASSES, DEFAULT_STRIPS, DEFAULT_ELLIPSE_MODE);
    }

    /**
     * Returns the recommended parameters for the ImageMath-equivalent chain.
     *
     * @return recommended destripe parameters
     */
    public static DestripeParams defaults() {
        return disabled();
    }
}
