/*
 * Copyright 2026 the original author or authors.
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
 * How the red and blue channels of Doppler images are assigned.
 */
public enum DopplerColors {
    /**
     * The channels are assigned from the direction in which the wavelength runs on the
     * sensor, so that receding material is red and approaching material is blue.
     */
    AUTO,
    /**
     * The image shifted by the positive Doppler shift goes to the red channel.
     */
    NORMAL,
    /**
     * The image shifted by the positive Doppler shift goes to the blue channel.
     */
    SWITCHED
}
