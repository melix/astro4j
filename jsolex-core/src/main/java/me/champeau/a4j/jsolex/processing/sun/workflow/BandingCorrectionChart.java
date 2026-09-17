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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.params.BandingCorrectionMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Renders a debug chart of the correction applied to each line by the transversallium correction.
 */
public final class BandingCorrectionChart {
    private BandingCorrectionChart() {
    }

    /**
     * Emits the chart of the corrections.
     *
     * @param imageEmitter the emitter of the chart
     * @param method the correction method which computed the corrections
     * @param chartTitle the title of the chart
     * @param passCorrections the correction of each line by each pass, as reported by the
     * correction, where lines are indexed in the image rotated left
     */
    public static void emit(ImageEmitter imageEmitter, BandingCorrectionMethod method, String chartTitle, List<double[]> passCorrections) {
        if (passCorrections.isEmpty()) {
            return;
        }
        var multiplicative = method == BandingCorrectionMethod.BANDING_CORRECTION;
        var lineCount = passCorrections.getFirst().length;
        var samples = new ArrayList<ShiftDebugChart.Series>();
        var total = new double[lineCount];
        Arrays.fill(total, multiplicative ? 1 : 0);
        for (var pass = 0; pass < passCorrections.size(); pass++) {
            var lineCorrections = passCorrections.get(pass);
            var values = new double[lineCount];
            var weights = new double[lineCount];
            for (var line = 0; line < lineCount; line++) {
                var position = lineCount - 1 - line;
                var correction = lineCorrections[line];
                if (multiplicative) {
                    values[position] = toPercent(correction);
                    total[position] *= correction;
                } else {
                    values[position] = correction;
                    total[position] += correction;
                }
                weights[position] = Double.isFinite(correction) ? 1 : 0;
            }
            samples.add(new ShiftDebugChart.Series("Pass " + (pass + 1), values, weights));
        }
        if (multiplicative) {
            for (var position = 0; position < lineCount; position++) {
                total[position] = toPercent(total[position]);
            }
        }
        var yAxisTitle = multiplicative ? "Correction (%)" : "Correction (pixel value)";
        ShiftDebugChart.emit(imageEmitter, chartTitle, message("banding.correction"), "transversallium-correction", "Position along the slit (px)", List.of(
                new ShiftDebugChart.Panel("Correction applied to each line", yAxisTitle, samples, total, "Total")
        ));
    }

    private static double toPercent(double factor) {
        return 100 * (factor - 1);
    }
}
