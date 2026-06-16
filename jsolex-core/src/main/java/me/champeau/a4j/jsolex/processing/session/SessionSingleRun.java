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
package me.champeau.a4j.jsolex.processing.session;

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.math.regression.Ellipse;

/**
 * The minimal state needed to re-run a single-file script after import: the path
 * to the source SER file plus the processing parameters, fitted ellipse and the
 * valid pixel-shift range. With these, any pixel shift requested by {@code img()}
 * can be reconstructed on demand by reopening the SER file, so the per-shift images
 * themselves don't need to be serialized.
 *
 * @param serFilePath     the absolute path to the source SER file (may be {@code null} if unknown)
 * @param params          the processing parameters used for the run
 * @param ellipse         the fitted solar disk ellipse (may be {@code null})
 * @param pixelShiftRange the valid pixel-shift range for on-demand reconstruction (may be {@code null})
 */
public record SessionSingleRun(String serFilePath, ProcessParams params, Ellipse ellipse, PixelShiftRange pixelShiftRange) {
}
