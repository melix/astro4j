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
 * @param ray the line being observed. When the detection mode is not
 * {@link LineDetectionMode#MANUAL} this is only a fallback until the line has been
 * identified from the scan, after which it holds the identified line.
 * @param detectionMode how the observed line is determined
 * @param pixelShift the pixel shift
 * @param dopplerShift the Doppler shift
 * @param continuumShift the continuum shift
 * @param switchRedBlueChannels whether to switch the red and blue channels of Doppler images
 */
public record SpectrumParams(
        SpectralRay ray,
        LineDetectionMode detectionMode,
        double pixelShift,
        double dopplerShift,
        double continuumShift,
        boolean switchRedBlueChannels
) {
    public SpectrumParams withRay(SpectralRay ray) {
        return new SpectrumParams(ray, detectionMode, pixelShift, dopplerShift, continuumShift, switchRedBlueChannels);
    }

    public SpectrumParams withDetectionMode(LineDetectionMode detectionMode) {
        return new SpectrumParams(ray, detectionMode, pixelShift, dopplerShift, continuumShift, switchRedBlueChannels);
    }

    public SpectrumParams withPixelShift(double pixelShift) {
        return new SpectrumParams(ray, detectionMode, pixelShift, dopplerShift, continuumShift, switchRedBlueChannels);
    }

    public SpectrumParams withDopplerShift(double dopplerShift) {
        return new SpectrumParams(ray, detectionMode, pixelShift, dopplerShift, continuumShift, switchRedBlueChannels);
    }

    public SpectrumParams withSwitchRedBlueChannels(boolean switchRedBlueChannels) {
        return new SpectrumParams(ray, detectionMode, pixelShift, dopplerShift, continuumShift, switchRedBlueChannels);
    }

    public SpectrumParams withContinuumShift(double continuumShift) {
        return new SpectrumParams(ray, detectionMode, pixelShift, dopplerShift, continuumShift, switchRedBlueChannels);
    }
}
