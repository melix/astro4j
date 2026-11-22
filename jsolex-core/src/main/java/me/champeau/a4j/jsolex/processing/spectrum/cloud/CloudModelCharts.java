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
package me.champeau.a4j.jsolex.processing.spectrum.cloud;

import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.Charts;
import me.champeau.a4j.jsolex.processing.util.Charts.Axis;
import me.champeau.a4j.jsolex.processing.util.Charts.LegendPosition;
import me.champeau.a4j.jsolex.processing.util.Charts.Series;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

import static me.champeau.a4j.jsolex.processing.util.Charts.BOTTOM_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.LEFT_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.PANEL_GAP;
import static me.champeau.a4j.jsolex.processing.util.Charts.RIGHT_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.TOP_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Renders the charts of a cloud model inversion as images.
 */
public final class CloudModelCharts {
    public static final int WIDTH = Charts.WIDTH;
    public static final int HEIGHT = Charts.HEIGHT;

    private static final double HISTOGRAM_LIMIT_KMS = 50;
    private static final double HISTOGRAM_BIN_KMS = 1;
    private static final Color MASS_FLUX_COLOR = new Color(25, 118, 210);
    private static final Color ENERGY_COLOR = new Color(211, 47, 47);
    private static final Color ENERGY_FLUX_COLOR = new Color(56, 142, 60);
    private static final Color OBSERVED_COLOR = new Color(33, 33, 33);
    private static final Color MODEL_COLOR = new Color(245, 124, 0);

    private CloudModelCharts() {
    }

    /**
     * Renders the distribution of the three Doppler velocities, weighted by the column mass.
     *
     * @param result the inversion result
     * @return the red, green and blue channels of the chart
     */
    public static float[][][] velocityDistribution(CloudInversionResult result) {
        var bins = (int) (2 * HISTOGRAM_LIMIT_KMS / HISTOGRAM_BIN_KMS);
        var centers = new double[bins];
        for (var i = 0; i < bins; i++) {
            centers[i] = -HISTOGRAM_LIMIT_KMS + (i + 0.5) * HISTOGRAM_BIN_KMS;
        }
        var vm = histogram(result, CloudMap.MASS_FLUX_VELOCITY, bins);
        var ve = histogram(result, CloudMap.KINETIC_ENERGY_VELOCITY, bins);
        var vf = histogram(result, CloudMap.KINETIC_ENERGY_FLUX_VELOCITY, bins);
        var max = 0d;
        for (var i = 0; i < bins; i++) {
            max = Math.max(max, Math.max(vm[i], Math.max(ve[i], vf[i])));
        }
        var image = Charts.newChart();
        var g = image.createGraphics();
        Charts.prepare(g);
        Charts.drawTitle(g, message("cloud.model.chart.velocities"));
        var area = new Rectangle(LEFT_MARGIN, TOP_MARGIN, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, HEIGHT - TOP_MARGIN - BOTTOM_MARGIN);
        Charts.drawPanel(g, area,
                new Axis(-HISTOGRAM_LIMIT_KMS, HISTOGRAM_LIMIT_KMS, message("cloud.model.chart.velocity.axis")),
                new Axis(0, max > 0 ? max * 1.1 : 1, message("cloud.model.chart.mass.fraction.axis")),
                List.of(),
                List.of(
                        new Series(message("cloud.model.chart.velocity.legend.vm"), MASS_FLUX_COLOR, centers, vm, false),
                        new Series(message("cloud.model.chart.velocity.legend.ve"), ENERGY_COLOR, centers, ve, false),
                        new Series(message("cloud.model.chart.velocity.legend.vf"), ENERGY_FLUX_COLOR, centers, vf, false)
                ), 0, LegendPosition.TOP_RIGHT);
        g.dispose();
        return ImageUtils.toRgb(image);
    }

    /**
     * Renders the mean profile at disk center compared with the calibrated atlas, and the residuals.
     *
     * @param result the inversion result
     * @return the red, green and blue channels of the chart
     */
    public static float[][][] calibration(CloudInversionResult result) {
        var profile = result.calibrationProfile();
        var calibration = result.calibration();
        var offsets = profile.offsets();
        var residuals = new double[offsets.length];
        var minY = Double.MAX_VALUE;
        var maxY = 0d;
        var maxResidual = 0d;
        for (var i = 0; i < offsets.length; i++) {
            residuals[i] = 100 * (profile.observed()[i] - profile.atlas()[i]);
            minY = Math.min(minY, Math.min(profile.observed()[i], profile.atlas()[i]));
            maxY = Math.max(maxY, Math.max(profile.observed()[i], profile.atlas()[i]));
            maxResidual = Math.max(maxResidual, Math.abs(residuals[i]));
        }
        var image = Charts.newChart();
        var g = image.createGraphics();
        Charts.prepare(g);
        Charts.drawTitle(g, String.format(Locale.US, message("cloud.model.chart.calibration"),
                calibration.instrumentFwhm(), 100 * calibration.strayLight(), calibration.dispersionScale(), calibration.offset(), calibration.telluricExponent()));
        var panelHeight = (HEIGHT - TOP_MARGIN - BOTTOM_MARGIN - PANEL_GAP) / 3;
        var top = new Rectangle(LEFT_MARGIN, TOP_MARGIN, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, 2 * panelHeight);
        var bottom = new Rectangle(LEFT_MARGIN, TOP_MARGIN + 2 * panelHeight + PANEL_GAP, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, panelHeight);
        var xAxis = new Axis(Math.min(offsets[0], offsets[offsets.length - 1]), Math.max(offsets[0], offsets[offsets.length - 1]), message("cloud.model.chart.wavelength.axis"));
        Charts.drawPanel(g, top, xAxis,
                new Axis(Math.max(0, minY - 0.05), maxY + 0.05, message("cloud.model.chart.intensity.axis")),
                List.of(),
                List.of(
                        new Series(message("cloud.model.chart.observed"), OBSERVED_COLOR, offsets, profile.observed(), true),
                        new Series(message("cloud.model.chart.atlas"), MODEL_COLOR, offsets, profile.atlas(), false)
                ), Double.NaN, LegendPosition.TOP_RIGHT);
        var limit = Math.max(0.5, maxResidual * 1.2);
        Charts.drawPanel(g, bottom, xAxis,
                new Axis(-limit, limit, message("cloud.model.chart.residual.axis")),
                List.of(),
                List.of(new Series(null, OBSERVED_COLOR, offsets, residuals, true)), Double.NaN, LegendPosition.TOP_RIGHT);
        g.dispose();
        return ImageUtils.toRgb(image);
    }

    private static double[] histogram(CloudInversionResult result, CloudMap map, int bins) {
        var values = result.data(map);
        var mass = result.data(CloudMap.COLUMN_MASS);
        var status = result.data(CloudMap.STATUS);
        var histogram = new double[bins];
        var total = 0d;
        for (var y = 0; y < result.height(); y++) {
            for (var x = 0; x < result.width(); x++) {
                if (!result.isDefined(map, x, y) || !result.isDefined(CloudMap.COLUMN_MASS, x, y)) {
                    continue;
                }
                var weight = mass[y][x];
                total += weight;
                var bin = (int) Math.floor((values[y][x] + HISTOGRAM_LIMIT_KMS) / HISTOGRAM_BIN_KMS);
                if (bin >= 0 && bin < bins) {
                    histogram[bin] += weight;
                }
            }
        }
        if (total > 0) {
            for (var i = 0; i < bins; i++) {
                histogram[i] = 100 * histogram[i] / total;
            }
        }
        return histogram;
    }
}
