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
package me.champeau.a4j.jsolex.processing.sun.workflow;

/**
 * Stores information about the maximum range of pixel shifts
 * which can be used given the detected polynomial and frame
 * dimensions. The shift is expressed in pixels relative to
 * the middle of the detected spectral line.
 *
 * @param minPixelShift the min pixel shift
 * @param maxPixelShift the max pixel shift
 * @param step the step to use when sampling in the range
 */
public record PixelShiftRange(
    double minPixelShift,
    double maxPixelShift,
    double step
) {
    public boolean includes(double shift) {
        return shift >= minPixelShift && shift <= maxPixelShift;
    }
}
