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
import me.champeau.a4j.math.MathUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Renders the charts of a cloud model inversion as images.
 */
public final class CloudModelCharts {
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 800;

    private static final int LEFT_MARGIN = 100;
    private static final int RIGHT_MARGIN = 40;
    private static final int TOP_MARGIN = 80;
    private static final int BOTTOM_MARGIN = 80;
    private static final int PANEL_GAP = 80;
    private static final double HISTOGRAM_LIMIT_KMS = 50;
    private static final double HISTOGRAM_BIN_KMS = 1;
    private static final Color AXIS_COLOR = Color.BLACK;
    private static final Color LEGEND_BACKGROUND = new Color(255, 255, 255, 220);
    private static final Color GRID_COLOR = new Color(225, 225, 225);
    private static final Color MASS_FLUX_COLOR = new Color(25, 118, 210);
    private static final Color ENERGY_COLOR = new Color(211, 47, 47);
    private static final Color ENERGY_FLUX_COLOR = new Color(56, 142, 60);
    private static final Color OBSERVED_COLOR = new Color(33, 33, 33);
    private static final Color MODEL_COLOR = new Color(245, 124, 0);

    private CloudModelCharts() {
    }

    private record Series(String label, Color color, double[] x, double[] y, boolean points) {
    }

    private record Axis(double min, double max, String title) {
        double step() {
            return MathUtils.niceStep(max - min, 8);
        }
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
        var image = newChart();
        var g = image.createGraphics();
        prepare(g);
        drawTitle(g, message("cloud.model.chart.velocities"));
        var area = new Rectangle(LEFT_MARGIN, TOP_MARGIN, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, HEIGHT - TOP_MARGIN - BOTTOM_MARGIN);
        drawPanel(g, area,
                new Axis(-HISTOGRAM_LIMIT_KMS, HISTOGRAM_LIMIT_KMS, message("cloud.model.chart.velocity.axis")),
                new Axis(0, max > 0 ? max * 1.1 : 1, message("cloud.model.chart.mass.fraction.axis")),
                List.of(
                        new Series(message("cloud.model.chart.velocity.legend.vm"), MASS_FLUX_COLOR, centers, vm, false),
                        new Series(message("cloud.model.chart.velocity.legend.ve"), ENERGY_COLOR, centers, ve, false),
                        new Series(message("cloud.model.chart.velocity.legend.vf"), ENERGY_FLUX_COLOR, centers, vf, false)
                ), 0);
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
        var image = newChart();
        var g = image.createGraphics();
        prepare(g);
        drawTitle(g, String.format(Locale.US, message("cloud.model.chart.calibration"),
                calibration.instrumentFwhm(), 100 * calibration.strayLight(), calibration.dispersionScale(), calibration.offset(), calibration.telluricExponent()));
        var panelHeight = (HEIGHT - TOP_MARGIN - BOTTOM_MARGIN - PANEL_GAP) / 3;
        var top = new Rectangle(LEFT_MARGIN, TOP_MARGIN, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, 2 * panelHeight);
        var bottom = new Rectangle(LEFT_MARGIN, TOP_MARGIN + 2 * panelHeight + PANEL_GAP, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, panelHeight);
        var xAxis = new Axis(Math.min(offsets[0], offsets[offsets.length - 1]), Math.max(offsets[0], offsets[offsets.length - 1]), message("cloud.model.chart.wavelength.axis"));
        drawPanel(g, top, xAxis,
                new Axis(Math.max(0, minY - 0.05), maxY + 0.05, message("cloud.model.chart.intensity.axis")),
                List.of(
                        new Series(message("cloud.model.chart.observed"), OBSERVED_COLOR, offsets, profile.observed(), true),
                        new Series(message("cloud.model.chart.atlas"), MODEL_COLOR, offsets, profile.atlas(), false)
                ), Double.NaN);
        var limit = Math.max(0.5, maxResidual * 1.2);
        drawPanel(g, bottom, xAxis,
                new Axis(-limit, limit, message("cloud.model.chart.residual.axis")),
                List.of(new Series(null, OBSERVED_COLOR, offsets, residuals, true)), Double.NaN);
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

    private static BufferedImage newChart() {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.dispose();
        return image;
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void drawTitle(Graphics2D g, String title) {
        g.setColor(AXIS_COLOR);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        var separator = title.indexOf(": ");
        if (g.getFontMetrics().stringWidth(title) <= WIDTH - LEFT_MARGIN - RIGHT_MARGIN || separator < 0) {
            g.drawString(title, LEFT_MARGIN, 40);
            return;
        }
        g.drawString(title.substring(0, separator).strip(), LEFT_MARGIN, 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(title.substring(separator + 2).strip(), LEFT_MARGIN, 56);
    }

    private static void drawPanel(Graphics2D g, Rectangle area, Axis xAxis, Axis yAxis, List<Series> series, double verticalLine) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        var metrics = g.getFontMetrics();
        var xStep = xAxis.step();
        for (var v = Math.ceil(xAxis.min() / xStep) * xStep; v <= xAxis.max() + 1e-9; v += xStep) {
            var px = toX(area, xAxis, v);
            g.setColor(GRID_COLOR);
            g.drawLine(px, area.y, px, area.y + area.height);
            g.setColor(AXIS_COLOR);
            var label = formatTick(v, xStep);
            g.drawString(label, px - metrics.stringWidth(label) / 2, area.y + area.height + 18);
        }
        var yStep = yAxis.step();
        for (var v = Math.ceil(yAxis.min() / yStep) * yStep; v <= yAxis.max() + 1e-9; v += yStep) {
            var py = toY(area, yAxis, v);
            g.setColor(GRID_COLOR);
            g.drawLine(area.x, py, area.x + area.width, py);
            g.setColor(AXIS_COLOR);
            var label = formatTick(v, yStep);
            g.drawString(label, area.x - 8 - metrics.stringWidth(label), py + 5);
        }
        if (yAxis.min() < 0 && yAxis.max() > 0) {
            g.setColor(Color.GRAY);
            var py = toY(area, yAxis, 0);
            g.drawLine(area.x, py, area.x + area.width, py);
        }
        if (Double.isFinite(verticalLine)) {
            g.setColor(Color.GRAY);
            var px = toX(area, xAxis, verticalLine);
            g.drawLine(px, area.y, px, area.y + area.height);
        }
        g.setColor(AXIS_COLOR);
        g.drawRect(area.x, area.y, area.width, area.height);
        g.drawString(xAxis.title(), area.x + (area.width - metrics.stringWidth(xAxis.title())) / 2, area.y + area.height + 40);
        var transform = g.getTransform();
        g.rotate(-Math.PI / 2);
        g.drawString(yAxis.title(), -(area.y + (area.height + metrics.stringWidth(yAxis.title())) / 2), area.x - 60);
        g.setTransform(transform);
        var clip = g.getClip();
        g.setClip(area);
        g.setStroke(new BasicStroke(2));
        for (var s : series) {
            g.setColor(s.color());
            if (s.points()) {
                for (var i = 0; i < s.x().length; i++) {
                    g.fillOval(toX(area, xAxis, s.x()[i]) - 3, toY(area, yAxis, s.y()[i]) - 3, 6, 6);
                }
            } else {
                var path = new Path2D.Double();
                for (var i = 0; i < s.x().length; i++) {
                    var px = toX(area, xAxis, s.x()[i]);
                    var py = toY(area, yAxis, s.y()[i]);
                    if (i == 0) {
                        path.moveTo(px, py);
                    } else {
                        path.lineTo(px, py);
                    }
                }
                g.draw(path);
            }
        }
        g.setClip(clip);
        g.setStroke(new BasicStroke(1));
        var legendY = area.y + 20;
        var labelWidth = 0;
        for (var s : series) {
            if (s.label() != null) {
                labelWidth = Math.max(labelWidth, g.getFontMetrics().stringWidth(s.label()));
            }
        }
        var legendX = area.x + area.width - labelWidth - 48;
        var legendCount = (int) series.stream().filter(s -> s.label() != null).count();
        if (legendCount > 0) {
            g.setColor(LEGEND_BACKGROUND);
            g.fillRect(legendX - 8, legendY - 16, labelWidth + 48, 20 * legendCount + 6);
        }
        for (var s : series) {
            if (s.label() == null) {
                continue;
            }
            g.setColor(s.color());
            g.fillRect(legendX, legendY - 10, 24, 10);
            g.setColor(AXIS_COLOR);
            g.drawString(s.label(), legendX + 32, legendY);
            legendY += 20;
        }
    }

    private static int toX(Rectangle area, Axis axis, double value) {
        return (int) Math.round(area.x + (value - axis.min()) / (axis.max() - axis.min()) * area.width);
    }

    private static int toY(Rectangle area, Axis axis, double value) {
        return (int) Math.round(area.y + area.height - (value - axis.min()) / (axis.max() - axis.min()) * area.height);
    }

    private static String formatTick(double value, double step) {
        if (step >= 1) {
            return String.format(Locale.US, "%.0f", value);
        }
        var decimals = (int) Math.ceil(-Math.log10(step));
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}
