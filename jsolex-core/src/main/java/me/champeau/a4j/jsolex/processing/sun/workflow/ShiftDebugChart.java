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
import java.util.List;

/**
 * Renders debug charts of per-line shift measurements, used by the limb based
 * corrections. Each panel plots samples (one per scan line, ignored when their
 * weight is 0) and an optional curve (NaN entries create gaps). All panels
 * share the same horizontal and vertical scales.
 */
final class ShiftDebugChart {
    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 800;
    private static final Color SAMPLES_COLOR = new Color(96, 125, 139);
    private static final Color CURVE_COLOR = new Color(211, 47, 47);

    private ShiftDebugChart() {
    }

    record Panel(String title, double[] values, double[] weights, double[] curve) {
    }

    static void emit(ImageEmitter imageEmitter, String chartTitle, String title, String name, List<Panel> panels) {
        var first = -1;
        var last = -1;
        for (var panel : panels) {
            for (var i = 0; i < panel.weights().length; i++) {
                if (panel.weights()[i] > 0) {
                    if (first < 0 || i < first) {
                        first = i;
                    }
                    last = Math.max(last, i);
                }
            }
        }
        if (first < 0 || last == first) {
            return;
        }
        var maxAbs = 0d;
        for (var panel : panels) {
            for (var i = first; i <= last; i++) {
                if (panel.weights()[i] > 0) {
                    maxAbs = Math.max(maxAbs, Math.abs(panel.values()[i]));
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
        g.drawString(chartTitle, 70, 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        var count = panels.size();
        var panelHeight = (CHART_HEIGHT - 110 - 60 * (count - 1)) / count;
        for (var p = 0; p < count; p++) {
            var area = new Rectangle(70, 70 + p * (panelHeight + 60), CHART_WIDTH - 100, panelHeight);
            drawPanel(g, area, panels.get(p), first, last, maxAbs);
        }
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
        var yStep = niceStep(2 * maxAbs, 6);
        for (var v = 0d; v <= maxAbs; v += yStep) {
            for (var sign = v == 0 ? 1 : -1; sign <= 1; sign += 2) {
                var py = (int) Math.round(centerY - sign * v * yScale);
                g.setColor(Color.LIGHT_GRAY);
                g.drawLine(area.x, py, area.x + area.width, py);
                g.setColor(Color.BLACK);
                g.drawString(String.format("%.2f", sign * v), area.x - 50, py + 4);
            }
        }
        var xStep = Math.max(1, (int) niceStep((double) last - first, 10));
        for (var frame = (first / xStep) * xStep + xStep; frame < last; frame += xStep) {
            var px = (int) Math.round(area.x + (frame - first) * xScale);
            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(px, area.y + area.height, px, area.y + area.height + 4);
            g.setColor(Color.BLACK);
            var label = String.valueOf(frame);
            g.drawString(label, px - g.getFontMetrics().stringWidth(label) / 2, area.y + area.height + 18);
        }
        g.setColor(SAMPLES_COLOR);
        for (var i = first; i <= last; i++) {
            if (panel.weights()[i] > 0) {
                var px = (int) Math.round(area.x + (i - first) * xScale);
                var py = (int) Math.round(Math.clamp(centerY - panel.values()[i] * yScale, area.y, area.y + area.height));
                g.fillOval(px - 1, py - 1, 3, 3);
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
