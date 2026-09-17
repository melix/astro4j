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

import me.champeau.a4j.jsolex.processing.util.MutableMap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders debug charts of per-line measurements, used by the limb based
 * corrections and the banding correction. Each panel plots one or more sample
 * series (one value per line, ignored when their weight is 0) and an optional
 * curve (NaN entries create gaps). All panels share the same horizontal and
 * vertical scales. Series beyond the number of available colors share a single
 * color and legend entry.
 */
final class ShiftDebugChart {
    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 800;
    private static final int LEFT_MARGIN = 100;
    private static final int RIGHT_MARGIN = 30;
    private static final int TOP_MARGIN = 70;
    private static final int BOTTOM_MARGIN = 70;
    private static final int PANEL_GAP = 60;
    private static final Color[] SAMPLES_COLORS = {
            new Color(96, 125, 139),
            new Color(25, 118, 210),
            new Color(56, 142, 60),
            new Color(245, 124, 0),
            new Color(123, 31, 162),
            new Color(0, 151, 167)
    };
    private static final Color OTHER_SAMPLES_COLOR = new Color(189, 189, 189);
    private static final Color CURVE_COLOR = new Color(211, 47, 47);

    private ShiftDebugChart() {
    }

    /**
     * A series of samples.
     *
     * @param label the label shown in the legend, or null
     * @param values the value of each line
     * @param weights the weight of each line, 0 to ignore it
     */
    record Series(String label, double[] values, double[] weights) {
    }

    /**
     * A panel of the chart.
     *
     * @param title the title of the panel
     * @param yAxisTitle the title of the vertical axis, including its unit
     * @param samples the sample series
     * @param curve an optional curve, or null
     * @param curveLabel the label of the curve in the legend, or null
     */
    record Panel(String title, String yAxisTitle, List<Series> samples, double[] curve, String curveLabel) {
        Panel(String title, String yAxisTitle, double[] values, double[] weights, double[] curve) {
            this(title, yAxisTitle, List.of(new Series(null, values, weights)), curve, null);
        }
    }

    static void emit(ImageEmitter imageEmitter, String chartTitle, String title, String name, String xAxisTitle, List<Panel> panels) {
        var first = -1;
        var last = -1;
        for (var panel : panels) {
            for (var series : panel.samples()) {
                for (var i = 0; i < series.weights().length; i++) {
                    if (series.weights()[i] > 0) {
                        if (first < 0 || i < first) {
                            first = i;
                        }
                        last = Math.max(last, i);
                    }
                }
            }
        }
        if (first < 0 || last == first) {
            return;
        }
        var maxAbs = 0d;
        for (var panel : panels) {
            for (var i = first; i <= last; i++) {
                for (var series : panel.samples()) {
                    if (series.weights()[i] > 0) {
                        maxAbs = Math.max(maxAbs, Math.abs(series.values()[i]));
                    }
                }
                if (panel.curve() != null && Double.isFinite(panel.curve()[i])) {
                    maxAbs = Math.max(maxAbs, Math.abs(panel.curve()[i]));
                }
            }
        }
        if (maxAbs == 0) {
            maxAbs = 1;
        }
        var chart = new BufferedImage(CHART_WIDTH, CHART_HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = chart.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, CHART_WIDTH, CHART_HEIGHT);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString(chartTitle, LEFT_MARGIN, 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        var count = panels.size();
        var panelHeight = (CHART_HEIGHT - TOP_MARGIN - BOTTOM_MARGIN - PANEL_GAP * (count - 1)) / count;
        Rectangle area = null;
        for (var p = 0; p < count; p++) {
            area = new Rectangle(LEFT_MARGIN, TOP_MARGIN + p * (panelHeight + PANEL_GAP), CHART_WIDTH - LEFT_MARGIN - RIGHT_MARGIN, panelHeight);
            drawPanel(g, area, panels.get(p), first, last, maxAbs);
        }
        g.setColor(Color.BLACK);
        g.drawString(xAxisTitle, area.x + (area.width - g.getFontMetrics().stringWidth(xAxisTitle)) / 2, area.y + area.height + 42);
        g.dispose();
        var buffer = ((DataBufferInt) chart.getRaster().getDataBuffer()).getData();
        var r = new float[CHART_HEIGHT][CHART_WIDTH];
        var gr = new float[CHART_HEIGHT][CHART_WIDTH];
        var b = new float[CHART_HEIGHT][CHART_WIDTH];
        for (var y = 0; y < CHART_HEIGHT; y++) {
            var offset = y * CHART_WIDTH;
            for (var x = 0; x < CHART_WIDTH; x++) {
                var pixel = buffer[offset + x];
                r[y][x] = ((pixel >> 16) & 0xFF) << 8;
                gr[y][x] = ((pixel >> 8) & 0xFF) << 8;
                b[y][x] = (pixel & 0xFF) << 8;
            }
        }
        imageEmitter.newColorImage(
                GeneratedImageKind.DEBUG,
                null,
                title,
                name,
                title,
                CHART_WIDTH,
                CHART_HEIGHT,
                MutableMap.of(),
                () -> new float[][][]{r, gr, b}
        );
    }

    private static void drawPanel(Graphics2D g, Rectangle area, Panel panel, int first, int last, double maxAbs) {
        var centerY = area.y + area.height / 2.0;
        var xScale = (double) area.width / (last - first);
        var yScale = (area.height / 2.0) / maxAbs;
        g.setColor(Color.BLACK);
        g.drawString(panel.title(), area.x, area.y - 8);
        drawYAxisTitle(g, area, panel.yAxisTitle());
        drawLegend(g, area, panel);
        var yStep = niceStep(2 * maxAbs, 6);
        for (var v = 0d; v <= maxAbs; v += yStep) {
            for (var sign = v == 0 ? 1 : -1; sign <= 1; sign += 2) {
                var py = (int) Math.round(centerY - sign * v * yScale);
                g.setColor(v == 0 ? Color.GRAY : Color.LIGHT_GRAY);
                g.drawLine(area.x, py, area.x + area.width, py);
                g.setColor(Color.BLACK);
                g.drawString(String.format("%.2f", sign * v), area.x - 50, py + 4);
            }
        }
        var xStep = Math.max(1, (int) niceStep((double) last - first, 10));
        for (var index = (first / xStep) * xStep + xStep; index < last; index += xStep) {
            var px = (int) Math.round(area.x + (index - first) * xScale);
            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(px, area.y + area.height, px, area.y + area.height + 4);
            g.setColor(Color.BLACK);
            var label = String.valueOf(index);
            g.drawString(label, px - g.getFontMetrics().stringWidth(label) / 2, area.y + area.height + 18);
        }
        for (var s = panel.samples().size() - 1; s >= 0; s--) {
            var series = panel.samples().get(s);
            g.setColor(samplesColor(s));
            for (var i = first; i <= last; i++) {
                if (series.weights()[i] > 0) {
                    var px = (int) Math.round(area.x + (i - first) * xScale);
                    var py = (int) Math.round(Math.clamp(centerY - series.values()[i] * yScale, area.y, area.y + area.height));
                    g.fillOval(px - 1, py - 1, 3, 3);
                }
            }
        }
        if (panel.curve() != null) {
            g.setColor(CURVE_COLOR);
            g.setStroke(new BasicStroke(2));
            var previousX = -1;
            var previousY = -1;
            for (var i = first; i <= last; i++) {
                if (!Double.isFinite(panel.curve()[i])) {
                    previousX = -1;
                    continue;
                }
                var px = (int) Math.round(area.x + (i - first) * xScale);
                var py = (int) Math.round(Math.clamp(centerY - panel.curve()[i] * yScale, area.y, area.y + area.height));
                if (previousX >= 0) {
                    g.drawLine(previousX, previousY, px, py);
                }
                previousX = px;
                previousY = py;
            }
            g.setStroke(new BasicStroke(1));
        }
        g.setColor(Color.BLACK);
        g.drawRect(area.x, area.y, area.width, area.height);
    }

    private static void drawYAxisTitle(Graphics2D g, Rectangle area, String yAxisTitle) {
        var transform = g.getTransform();
        var titleWidth = g.getFontMetrics().stringWidth(yAxisTitle);
        g.rotate(-Math.PI / 2);
        g.drawString(yAxisTitle, -(area.y + (area.height + titleWidth) / 2), area.x - 60);
        g.setTransform(transform);
    }

    private static void drawLegend(Graphics2D g, Rectangle area, Panel panel) {
        var entries = new ArrayList<LegendEntry>();
        var samples = panel.samples();
        for (var s = 0; s < Math.min(samples.size(), SAMPLES_COLORS.length); s++) {
            var label = samples.get(s).label();
            if (label != null) {
                entries.add(new LegendEntry(label, samplesColor(s), false));
            }
        }
        if (samples.size() > SAMPLES_COLORS.length && samples.getLast().label() != null) {
            entries.add(new LegendEntry("Others", OTHER_SAMPLES_COLOR, false));
        }
        if (panel.curve() != null && panel.curveLabel() != null) {
            entries.add(new LegendEntry(panel.curveLabel(), CURVE_COLOR, true));
        }
        var metrics = g.getFontMetrics();
        var x = area.x + area.width;
        var baseline = area.y - 8;
        for (var entry : entries.reversed()) {
            x -= metrics.stringWidth(entry.label());
            g.setColor(Color.BLACK);
            g.drawString(entry.label(), x, baseline);
            x -= 18;
            g.setColor(entry.color());
            if (entry.line()) {
                g.setStroke(new BasicStroke(2));
                g.drawLine(x, baseline - 4, x + 12, baseline - 4);
                g.setStroke(new BasicStroke(1));
            } else {
                g.fillOval(x + 3, baseline - 7, 6, 6);
            }
            x -= 16;
        }
    }

    private static Color samplesColor(int index) {
        return index < SAMPLES_COLORS.length ? SAMPLES_COLORS[index] : OTHER_SAMPLES_COLOR;
    }

    private record LegendEntry(String label, Color color, boolean line) {
    }

    private static double niceStep(double range, int targetTicks) {
        var raw = range / targetTicks;
        var magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        var residual = raw / magnitude;
        if (residual <= 1) {
            return magnitude;
        }
        if (residual <= 2) {
            return 2 * magnitude;
        }
        if (residual <= 5) {
            return 5 * magnitude;
        }
        return 10 * magnitude;
    }
}
