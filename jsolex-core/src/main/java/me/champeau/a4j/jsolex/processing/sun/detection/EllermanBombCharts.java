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
package me.champeau.a4j.jsolex.processing.sun.detection;

import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.Charts;
import me.champeau.a4j.jsolex.processing.util.Charts.Axis;
import me.champeau.a4j.jsolex.processing.util.Charts.Band;
import me.champeau.a4j.jsolex.processing.util.Charts.LegendPosition;
import me.champeau.a4j.jsolex.processing.util.Charts.Series;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.WING_END_ANGSTROMS;
import static me.champeau.a4j.jsolex.processing.sun.detection.EllermanBombDetector.WING_START_ANGSTROMS;
import static me.champeau.a4j.jsolex.processing.util.Charts.BOTTOM_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.HEIGHT;
import static me.champeau.a4j.jsolex.processing.util.Charts.LEFT_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.PANEL_GAP;
import static me.champeau.a4j.jsolex.processing.util.Charts.RIGHT_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.TOP_MARGIN;
import static me.champeau.a4j.jsolex.processing.util.Charts.WIDTH;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Renders the line profile of a detected brightening, compared with its neighbours.
 */
public final class EllermanBombCharts {
    private static final Color DETECTION_COLOR = new Color(33, 33, 33);
    private static final Color NEIGHBOURS_COLOR = new Color(25, 118, 210);
    private static final Color SCATTER_COLOR = new Color(25, 118, 210, 60);
    private static final Color WINGS_COLOR = new Color(245, 124, 0, 45);
    private static final double MARGIN = 0.05;

    private EllermanBombCharts() {
    }

    /**
     * Renders the profile of a brightening in the upper panel, and its ratio to the median
     * profile of the neighbours in the lower panel.
     *
     * @param profile the profile
     * @param title the title of the chart
     * @return the red, green and blue channels of the chart
     */
    public static float[][][] profile(EllermanBombProfile profile, String title) {
        var offsets = profile.offsets();
        var samples = offsets.length;
        var low = new double[samples];
        var high = new double[samples];
        var ratio = new double[samples];
        var ratioLow = new double[samples];
        var ratioHigh = new double[samples];
        var one = new double[samples];
        var minY = Double.MAX_VALUE;
        var maxY = -Double.MAX_VALUE;
        var minRatio = 1d;
        var maxRatio = 1d;
        for (int k = 0; k < samples; k++) {
            var neighbours = profile.neighbours()[k];
            var scatter = profile.scatter()[k];
            low[k] = neighbours - scatter;
            high[k] = neighbours + scatter;
            ratio[k] = profile.detection()[k] / neighbours;
            ratioLow[k] = low[k] / neighbours;
            ratioHigh[k] = high[k] / neighbours;
            one[k] = 1;
            minY = Math.min(minY, Math.min(low[k], profile.detection()[k]));
            maxY = Math.max(maxY, Math.max(high[k], profile.detection()[k]));
            minRatio = Math.min(minRatio, Math.min(ratioLow[k], ratio[k]));
            maxRatio = Math.max(maxRatio, Math.max(ratioHigh[k], ratio[k]));
        }
        var image = Charts.newChart();
        var g = image.createGraphics();
        Charts.prepare(g);
        Charts.drawTitle(g, title);
        var panelHeight = (HEIGHT - TOP_MARGIN - BOTTOM_MARGIN - PANEL_GAP) / 3;
        var top = new Rectangle(LEFT_MARGIN, TOP_MARGIN, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, 2 * panelHeight);
        var bottom = new Rectangle(LEFT_MARGIN, TOP_MARGIN + 2 * panelHeight + PANEL_GAP, WIDTH - LEFT_MARGIN - RIGHT_MARGIN, panelHeight);
        var xAxis = new Axis(offsets[0], offsets[samples - 1], message("ellerman.bomb.profile.wavelength.axis"));
        var yAxis = new Axis(minY - MARGIN, maxY + MARGIN, message("ellerman.bomb.profile.intensity.axis"));
        var ratioMargin = MARGIN * (maxRatio - minRatio);
        var ratioAxis = new Axis(minRatio - ratioMargin, maxRatio + ratioMargin, message("ellerman.bomb.profile.ratio.axis"));
        var topBands = new ArrayList<>(wingBands(yAxis, message("ellerman.bomb.profile.wings")));
        topBands.add(new Band(message("ellerman.bomb.profile.scatter"), SCATTER_COLOR, offsets, low, high));
        Charts.drawPanel(g, top, xAxis, yAxis, topBands,
                List.of(
                        new Series(message("ellerman.bomb.profile.neighbours"), NEIGHBOURS_COLOR, offsets, profile.neighbours(), false),
                        new Series(message("ellerman.bomb.profile.detection"), DETECTION_COLOR, offsets, profile.detection(), false)
                ), Double.NaN, LegendPosition.BOTTOM_RIGHT);
        var bottomBands = new ArrayList<>(wingBands(ratioAxis, null));
        bottomBands.add(new Band(null, SCATTER_COLOR, offsets, ratioLow, ratioHigh));
        Charts.drawPanel(g, bottom, xAxis, ratioAxis, bottomBands,
                List.of(
                        new Series(null, NEIGHBOURS_COLOR, offsets, one, false),
                        new Series(null, DETECTION_COLOR, offsets, ratio, false)
                ), Double.NaN, LegendPosition.BOTTOM_RIGHT);
        g.dispose();
        return ImageUtils.toRgb(image);
    }

    private static List<Band> wingBands(Axis axis, String label) {
        var bottom = new double[]{axis.min(), axis.min()};
        var top = new double[]{axis.max(), axis.max()};
        return List.of(
                new Band(label, WINGS_COLOR, new double[]{-WING_END_ANGSTROMS, -WING_START_ANGSTROMS}, bottom, top),
                new Band(null, WINGS_COLOR, new double[]{WING_START_ANGSTROMS, WING_END_ANGSTROMS}, bottom, top)
        );
    }
}
