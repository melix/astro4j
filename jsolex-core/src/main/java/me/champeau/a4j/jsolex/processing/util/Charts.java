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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.math.MathUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws charts made of panels sharing the same style: a white background, a grid, axis
 * scales and titles, curves, filled bands and a legend.
 */
public final class Charts {
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 800;
    public static final int LEFT_MARGIN = 100;
    public static final int RIGHT_MARGIN = 40;
    public static final int TOP_MARGIN = 80;
    public static final int BOTTOM_MARGIN = 80;
    public static final int PANEL_GAP = 80;

    private static final Color AXIS_COLOR = Color.BLACK;
    private static final Color LEGEND_BACKGROUND = new Color(255, 255, 255, 220);
    private static final Color GRID_COLOR = new Color(225, 225, 225);

    private Charts() {
    }

    /**
     * A curve, drawn as a line or as points. Its label is shown in the legend when it is not null.
     */
    public record Series(String label, Color color, double[] x, double[] y, boolean points) {
    }

    /**
     * A filled area between two curves. Its label is shown in the legend when it is not null.
     */
    public record Band(String label, Color color, double[] x, double[] low, double[] high) {
    }

    /**
     * The range and the title of an axis.
     */
    public record Axis(double min, double max, String title) {
        double step() {
            return MathUtils.niceStep(max - min, 8);
        }
    }

    /**
     * Where the legend of a panel is drawn.
     */
    public enum LegendPosition {
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    /**
     * Creates an empty chart, with a white background.
     *
     * @return the chart image
     */
    public static BufferedImage newChart() {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.dispose();
        return image;
    }

    /**
     * Enables antialiasing of the shapes and of the text.
     *
     * @param g the graphics of the chart
     */
    public static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /**
     * Draws the title of the chart. A title which is too long is split on 2 lines after
     * its first colon.
     *
     * @param g the graphics of the chart
     * @param title the title
     */
    public static void drawTitle(Graphics2D g, String title) {
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

    /**
     * Draws a panel with its grid, axes, bands, curves and legend.
     *
     * @param g the graphics of the chart
     * @param area the area of the panel, without its axis titles
     * @param xAxis the horizontal axis
     * @param yAxis the vertical axis
     * @param bands the filled areas, drawn below the curves
     * @param series the curves
     * @param verticalLine the abscissa of a vertical reference line, or NaN
     * @param legendPosition where the legend is drawn
     */
    public static void drawPanel(Graphics2D g,
                                 Rectangle area,
                                 Axis xAxis,
                                 Axis yAxis,
                                 List<Band> bands,
                                 List<Series> series,
                                 double verticalLine,
                                 LegendPosition legendPosition) {
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
        for (var band : bands) {
            var path = new Path2D.Double();
            for (var i = 0; i < band.x().length; i++) {
                var px = toX(area, xAxis, band.x()[i]);
                var py = toY(area, yAxis, band.high()[i]);
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            for (var i = band.x().length - 1; i >= 0; i--) {
                path.lineTo(toX(area, xAxis, band.x()[i]), toY(area, yAxis, band.low()[i]));
            }
            path.closePath();
            g.setColor(band.color());
            g.fill(path);
        }
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
        drawLegend(g, area, bands, series, legendPosition);
    }

    private record LegendEntry(String label, Color color) {
    }

    private static void drawLegend(Graphics2D g, Rectangle area, List<Band> bands, List<Series> series, LegendPosition position) {
        var entries = new ArrayList<LegendEntry>();
        for (var s : series) {
            if (s.label() != null) {
                entries.add(new LegendEntry(s.label(), s.color()));
            }
        }
        for (var band : bands) {
            if (band.label() != null) {
                entries.add(new LegendEntry(band.label(), band.color()));
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        var labelWidth = 0;
        for (var entry : entries) {
            labelWidth = Math.max(labelWidth, g.getFontMetrics().stringWidth(entry.label()));
        }
        var legendX = area.x + area.width - labelWidth - 48;
        var legendHeight = 20 * entries.size() + 6;
        var legendY = position == LegendPosition.TOP_RIGHT ? area.y + 20 : area.y + area.height - legendHeight + 4;
        g.setColor(LEGEND_BACKGROUND);
        g.fillRect(legendX - 8, legendY - 16, labelWidth + 48, legendHeight);
        for (var entry : entries) {
            g.setColor(entry.color());
            g.fillRect(legendX, legendY - 10, 24, 10);
            g.setColor(AXIS_COLOR);
            g.drawString(entry.label(), legendX + 32, legendY);
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
