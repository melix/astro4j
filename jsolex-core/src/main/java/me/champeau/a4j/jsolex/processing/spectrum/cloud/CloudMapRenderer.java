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

import me.champeau.a4j.math.MathUtils;
import me.champeau.a4j.math.regression.Ellipse;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Renders the maps of a cloud model inversion with fixed color scales, so that
 * maps from different observations can be compared.
 */
public final class CloudMapRenderer {
    private static final float[] REJECTED = {128, 128, 128};
    private static final float[] UNDEFINED = {45, 45, 45};
    private static final float[] NOT_FITTED = {80, 80, 80};
    private static final double HATCHING_PERIODS_PER_RADIUS = 40;
    private static final float CHANNEL_SCALE = 256;
    private static final double MAX_VELOCITY_KMS = 100;
    private static final double VELOCITY_KNEE_KMS = 8;
    private static final double[] VELOCITY_TICKS = {-100, -30, -10, 0, 10, 30, 100};
    private static final double MAX_REDUCED_CHI2 = 5;
    private static final double[] QUALITY_TICKS = {0, 1, 2, 3, 4, 5};
    private static final double ROBUST_PERCENTILE = 99;

    private CloudMapRenderer() {
    }

    /**
     * The colors of a scale, as positions between 0 and 1 followed by red, green and blue.
     */
    public enum Palette {
        /**
         * From dark violet to pale yellow, for quantities which grow from zero. The scale does not start at black,
         * so that a zero value stands out from the sky and from the points which could not be measured.
         */
        SEQUENTIAL(new double[][]{
                {0, 40, 11, 84},
                {0.25, 87, 16, 110},
                {0.5, 188, 55, 84},
                {0.75, 249, 142, 9},
                {1, 252, 255, 164}
        }),
        /**
         * From blue to red through white, for signed quantities centered on zero.
         */
        DIVERGING(new double[][]{
                {0, 33, 102, 172},
                {0.25, 103, 169, 207},
                {0.5, 247, 247, 247},
                {0.75, 239, 138, 98},
                {1, 178, 24, 43}
        }),
        /**
         * From green to dark red through yellow, for a quality from good to poor.
         */
        QUALITY(new double[][]{
                {0, 26, 152, 80},
                {0.25, 145, 207, 96},
                {0.45, 254, 224, 139},
                {0.7, 244, 109, 67},
                {1, 165, 0, 38}
        });

        private final double[][] stops;

        Palette(double[][] stops) {
            this.stops = stops;
        }
    }

    /**
     * How the values are spread along a color scale.
     */
    public enum Mapping {
        LINEAR,
        LOGARITHMIC,
        /**
         * Inverse hyperbolic sine: linear around zero and logarithmic for large values, so that slow
         * and fast velocities can be told apart with a single fixed scale.
         */
        ASINH
    }

    /**
     * A color scale.
     *
     * @param min the value shown with the first color
     * @param max the value shown with the last color
     * @param mapping how the values are spread along the scale
     * @param palette the colors of the scale
     * @param ticks the values labeled on the legend, or null for evenly spaced labels
     */
    public record Scale(double min, double max, Mapping mapping, Palette palette, double[] ticks) {
        static Scale linear(double min, double max) {
            return new Scale(min, max, Mapping.LINEAR, Palette.SEQUENTIAL, null);
        }

        static Scale symmetric(double max) {
            return new Scale(-max, max, Mapping.LINEAR, Palette.DIVERGING, null);
        }

        static Scale log(double min, double max) {
            return new Scale(min, max, Mapping.LOGARITHMIC, Palette.SEQUENTIAL, null);
        }

        static Scale velocity() {
            return new Scale(-MAX_VELOCITY_KMS, MAX_VELOCITY_KMS, Mapping.ASINH, Palette.DIVERGING, VELOCITY_TICKS);
        }

        static Scale quality() {
            return new Scale(0, MAX_REDUCED_CHI2, Mapping.LINEAR, Palette.QUALITY, QUALITY_TICKS);
        }

        double position(double value) {
            return switch (mapping) {
                case LINEAR -> (value - min) / (max - min);
                case LOGARITHMIC -> (Math.log10(Math.max(value, min)) - Math.log10(min)) / (Math.log10(max) - Math.log10(min));
                case ASINH -> (asinh(value / VELOCITY_KNEE_KMS) - asinh(min / VELOCITY_KNEE_KMS))
                              / (asinh(max / VELOCITY_KNEE_KMS) - asinh(min / VELOCITY_KNEE_KMS));
            };
        }

        double valueAt(double position) {
            return switch (mapping) {
                case LINEAR -> min + position * (max - min);
                case LOGARITHMIC -> Math.pow(10, Math.log10(min) + position * (Math.log10(max) - Math.log10(min)));
                case ASINH -> {
                    var low = asinh(min / VELOCITY_KNEE_KMS);
                    yield VELOCITY_KNEE_KMS * Math.sinh(low + position * (asinh(max / VELOCITY_KNEE_KMS) - low));
                }
            };
        }

        double[] tickValues() {
            if (ticks != null) {
                return ticks;
            }
            var values = new double[5];
            for (var i = 0; i < values.length; i++) {
                values[i] = valueAt(i / 4d);
            }
            return values;
        }

        private static double asinh(double x) {
            return Math.log(x + Math.sqrt(x * x + 1));
        }
    }

    /**
     * Returns the scale used to display a map.
     *
     * @param map the map
     * @param result the inversion result, used for scales which depend on the data
     * @return the scale
     */
    public static Scale scaleOf(CloudMap map, CloudInversionResult result) {
        return switch (map) {
            case MASS_FLUX_VELOCITY, VELOCITY_CENTER, FASTEST_VELOCITY, KINETIC_ENERGY_FLUX_VELOCITY, VELOCITY_BLUE, VELOCITY_RED -> Scale.velocity();
            case KINETIC_ENERGY_VELOCITY -> Scale.linear(0, 30);
            case TEMPERATURE -> Scale.linear(4000, 15000);
            case COLUMN_MASS -> Scale.log(1e-5, 1e-3);
            case INTEGRATED_OPACITY -> Scale.linear(0, 1);
            case SOURCE_FUNCTION -> Scale.linear(0, 0.4);
            case DOPPLER_WIDTH -> Scale.linear(CloudModel.MIN_DOPPLER_WIDTH, CloudModel.MAX_DOPPLER_WIDTH);
            case BACKGROUND_SCALE -> Scale.linear(0.8, 1.2);
            case BACKGROUND_WIDTH -> Scale.symmetric(CloudModel.MAX_BACKGROUND_WIDTH);
            case OPACITY_BLUE, OPACITY_CENTER, OPACITY_RED -> Scale.linear(0, 2);
            case COMPONENTS -> Scale.linear(0, 3);
            case DETECTION -> Scale.linear(0, 4 * CloudInversionResult.MIN_DETECTION);
            case REDUCED_CHI2 -> Scale.quality();
            case STATUS -> Scale.linear(0, CloudMap.STATUS_BRIGHTER);
            case MASS_FLUX, KINETIC_ENERGY_FLUX -> Scale.symmetric(robustMaximum(result, map));
            case KINETIC_ENERGY -> Scale.linear(0, robustMaximum(result, map));
        };
    }

    private static double robustMaximum(CloudInversionResult result, CloudMap map) {
        var data = result.data(map);
        var values = new double[result.width() * result.height()];
        var count = 0;
        for (var y = 0; y < result.height(); y++) {
            for (var x = 0; x < result.width(); x++) {
                if (result.isDefined(map, x, y) && data[y][x] != 0) {
                    values[count++] = Math.abs(data[y][x]);
                }
            }
        }
        if (count == 0) {
            return 1;
        }
        return Math.max(MathUtils.percentile(Arrays.copyOf(values, count), ROBUST_PERCENTILE), Double.MIN_NORMAL);
    }

    /**
     * Colorizes a map. Positions outside of the disk are black, rejected positions are grey, and the positions of the disk
     * which were not fitted, close to the limb, are hatched.
     *
     * @param result the inversion result
     * @param map the map
     * @return the red, green and blue channels
     */
    public static float[][][] colorize(CloudInversionResult result, CloudMap map) {
        var scale = scaleOf(map, result);
        var data = result.data(map);
        var status = result.data(CloudMap.STATUS);
        var width = result.width();
        var height = result.height();
        var rgb = new float[3][height][width];
        var color = new float[3];
        var palette = scale.palette().stops;
        var disk = result.metadata().get(Ellipse.class) instanceof Ellipse ellipse ? ellipse : null;
        var stripeWidth = disk == null ? 1 : Math.max(1, (disk.semiAxis().a() + disk.semiAxis().b()) / (4 * HATCHING_PERIODS_PER_RADIUS));
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var s = status[y][x];
                if (s == CloudMap.STATUS_NOT_FITTED) {
                    if (disk != null && disk.isWithin(x, y) && Math.floorMod((long) Math.floor((x + y) / stripeWidth), 2) == 0) {
                        paint(rgb, x, y, NOT_FITTED);
                    }
                    continue;
                }
                if (!result.isDefined(map, x, y)) {
                    paint(rgb, x, y, CloudMap.isAnalyzed(s) ? UNDEFINED : REJECTED);
                    continue;
                }
                interpolate(palette, Math.clamp(scale.position(data[y][x]), 0, 1), color);
                paint(rgb, x, y, color);
            }
        }
        return rgb;
    }

    private static void paint(float[][][] rgb, int x, int y, float[] color) {
        for (var c = 0; c < 3; c++) {
            rgb[c][y][x] = color[c] * CHANNEL_SCALE;
        }
    }

    /**
     * Returns the title of a map, as shown on its legend.
     */
    public static String titleOf(CloudMap map) {
        return message("cloud.model.map." + map.id());
    }

    /**
     * Paints the title and the color bar of a map of an inversion.
     */
    public static void paintLegend(Graphics2D g, CloudInversionResult result, CloudMap map) {
        paintLegend(g, result.width(), result.height(), titleOf(map), map.unit(), scaleOf(map, result));
    }

    /**
     * Paints the title and the color bar of a map in the top left corner of the image,
     * which lies outside of the solar disk.
     *
     * @param g the graphics
     * @param width the width of the image
     * @param height the height of the image
     * @param title the title of the map
     * @param unit the unit of the values, or an empty string
     * @param scale the color scale
     */
    public static void paintLegend(Graphics2D g, int width, int height, String title, String unit, Scale scale) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var size = Math.max(12, Math.min(width, height) / 60);
        var font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        g.setFont(font);
        var margin = size;
        var header = unit.isEmpty() ? title : title + " (" + unit + ")";
        g.setColor(Color.WHITE);
        g.drawString(header, margin, margin + size);
        var barTop = margin + 2 * size;
        var barHeight = Math.max(4 * size, height / 6);
        var barWidth = size;
        var palette = scale.palette().stops;
        var color = new float[3];
        for (var i = 0; i < barHeight; i++) {
            interpolate(palette, 1 - (double) i / (barHeight - 1), color);
            g.setColor(new Color((int) color[0], (int) color[1], (int) color[2]));
            g.fillRect(margin, barTop + i, barWidth, 1);
        }
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1));
        g.drawRect(margin, barTop, barWidth, barHeight);
        for (var value : scale.tickValues()) {
            var position = Math.clamp(scale.position(value), 0, 1);
            var y = barTop + (int) Math.round((1 - position) * barHeight);
            g.drawLine(margin + barWidth, y, margin + barWidth + size / 3, y);
            g.drawString(format(value), margin + barWidth + size / 2, y + size / 3);
        }
    }

    /**
     * Returns the value of a map at one pixel with its unit, or empty if it is not defined there.
     */
    public static Optional<String> describe(CloudInversionResult result, CloudMap map, int x, int y) {
        if (x < 0 || y < 0 || x >= result.width() || y >= result.height() || !result.isDefined(map, x, y)) {
            return Optional.empty();
        }
        var value = result.valueAt(map, x, y);
        var formatted = isScientific(value) ? String.format(Locale.US, "%.2e", value) : String.format(Locale.US, "%.2f", value);
        return Optional.of(map.unit().isEmpty() ? formatted : formatted + " " + map.unit());
    }

    private static boolean isScientific(double value) {
        var abs = Math.abs(value);
        return abs != 0 && (abs < 1e-2 || abs >= 1e5);
    }

    private static String format(double value) {
        if (value == 0) {
            return "0";
        }
        var abs = Math.abs(value);
        if (isScientific(value)) {
            return String.format(Locale.US, "%.1e", value);
        }
        if (abs >= 100 || (abs >= 1 && value == Math.rint(value))) {
            return String.format(Locale.US, "%.0f", value);
        }
        if (abs >= 10) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static void interpolate(double[][] palette, double position, float[] color) {
        for (var i = 1; i < palette.length; i++) {
            if (position <= palette[i][0] || i == palette.length - 1) {
                var a = palette[i - 1];
                var b = palette[i];
                var t = Math.clamp((position - a[0]) / (b[0] - a[0]), 0, 1);
                for (var c = 0; c < 3; c++) {
                    color[c] = (float) (a[c + 1] + t * (b[c + 1] - a[c + 1]));
                }
                return;
            }
        }
    }
}
