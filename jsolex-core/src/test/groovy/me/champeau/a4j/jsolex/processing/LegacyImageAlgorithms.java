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
package me.champeau.a4j.jsolex.processing;

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.stretching.GammaStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.GeometryTransform;
import me.champeau.a4j.jsolex.processing.util.Histogram;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.regression.Ellipse;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;
import static org.apache.commons.math3.util.FastMath.asinh;

/**
 * Reference implementations working on whole-image arrays, used to check that
 * the row-based implementations produce the same values.
 */
public final class LegacyImageAlgorithms {
    private static final float MAX_VALUE = 65535.0f;
    private static final double KERNEL_RADIUS = 2;

    private LegacyImageAlgorithms() {
    }

    public static float[][] copy(float[][] data) {
        var result = new float[data.length][];
        for (int y = 0; y < data.length; y++) {
            result[y] = data[y].clone();
        }
        return result;
    }

    public static double estimateBlackPoint(float[][] buffer, Ellipse ellipse) {
        var height = buffer.length;
        var width = height == 0 ? 0 : buffer[0].length;
        double blackEstimate = Double.MAX_VALUE;
        int cpt = 0;
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!ellipse.isWithin(x, y)) {
                    var v = buffer[y][x];
                    if (v > 0 && Float.isFinite(v)) {
                        var offcenter = 2 * Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (width + height);
                        blackEstimate = blackEstimate + (offcenter * v - blackEstimate) / (++cpt);
                    }
                }
            }
        }
        return cpt == 0 ? 0 : blackEstimate;
    }

    public static double estimateBackground(float[][] buffer, Ellipse ellipse) {
        var height = buffer.length;
        var width = height == 0 ? 0 : buffer[0].length;
        double avg = Double.MAX_VALUE;
        int cpt = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!ellipse.isWithin(x, y)) {
                    var v = buffer[y][x];
                    if (v > 0 && Float.isFinite(v)) {
                        avg = avg + (v - avg) / (++cpt);
                    }
                }
            }
        }
        return cpt == 0 ? 0 : avg;
    }

    public static BufferedImage monoImage(int width, int height, float[][] data) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        short[] converted = ((DataBufferUShort) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                converted[y * width + x] = (short) Math.round(data[y][x]);
            }
        }
        return image;
    }

    public static BufferedImage rgbImage(int width, int height, float[][] r, float[][] g, float[][] b) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = Math.round(r[y][x]);
                int gv = Math.round(g[y][x]);
                int bv = Math.round(b[y][x]);
                rv = (rv >> 8) & 0xFF;
                gv = (gv >> 8) & 0xFF;
                bv = (bv >> 8) & 0xFF;
                rgb[y * width + x] = (rv << 16) | (gv << 8) | bv;
            }
        }
        image.setRGB(0, 0, width, height, rgb, 0, width);
        return image;
    }

    public static float[][][] convertToRGB(ColorCurve curve, float[][] mono) {
        var height = mono.length;
        var width = height == 0 ? 0 : mono[0].length;
        float[][] r = new float[height][width];
        float[][] g = new float[height][width];
        float[][] b = new float[height][width];
        var rPoly = curve.rPoly();
        var gPoly = curve.gPoly();
        var bPoly = curve.bPoly();
        for (int y = 0; y < height; y++) {
            float[] line = mono[y];
            for (int x = 0; x < line.length; x++) {
                double v = line[x];
                r[y][x] = clampToRange(rPoly.applyAsDouble(v));
                g[y][x] = clampToRange(gPoly.applyAsDouble(v));
                b[y][x] = clampToRange(bPoly.applyAsDouble(v));
            }
        }
        return new float[][][]{r, g, b};
    }

    private static float clampToRange(double v) {
        if (v < 0) {
            return 0f;
        }
        if (v > ColorCurve.MAX) {
            return ColorCurve.MAX;
        }
        return (float) Math.round(v);
    }

    public static float[][][] fromRGBtoHSL(float[][][] rgb) {
        float[][] rChannel = rgb[0];
        float[][] gChannel = rgb[1];
        float[][] bChannel = rgb[2];
        int height = rChannel.length;
        int width = rChannel[0].length;
        var output = new float[3][height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = rChannel[y][x] / MAX_VALUE;
                float g = gChannel[y][x] / MAX_VALUE;
                float b = bChannel[y][x] / MAX_VALUE;
                float max = Math.max(r, Math.max(g, b));
                float min = Math.min(r, Math.min(g, b));
                float delta = max - min;
                float hue = 0.0f;
                if (delta == 0) {
                    hue = 0.0f;
                } else if (max == r) {
                    hue = ((g - b) / delta) % 6;
                } else if (max == g) {
                    hue = (b - r) / delta + 2;
                } else if (max == b) {
                    hue = (r - g) / delta + 4;
                }
                hue *= 60.0f;
                if (hue < 0) {
                    hue += 360.0f;
                }
                float lightness = (max + min) / 2;
                float saturation;
                if (delta == 0) {
                    saturation = 0;
                } else {
                    saturation = delta / (1 - Math.abs(2 * lightness - 1));
                }
                if (lightness <= 0.0001f) {
                    saturation = 0;
                }
                output[0][y][x] = Math.max(0, Math.min(360, hue));
                output[1][y][x] = Math.max(0, Math.min(saturation, 1.0f));
                output[2][y][x] = Math.max(0, Math.min(lightness, 1.0f));
            }
        }
        return output;
    }

    public static float[][][] fromHSLtoRGB(float[][][] hsl, float[][][] output) {
        float[][] hChannel = hsl[0];
        float[][] sChannel = hsl[1];
        float[][] lChannel = hsl[2];
        int height = hChannel.length;
        int width = hChannel[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float h = hChannel[y][x];
                float s = sChannel[y][x];
                float l = lChannel[y][x];
                float chroma = (1 - Math.abs(2 * l - 1)) * s;
                float hueSegment = h / 60.0f;
                float k = chroma * (1 - Math.abs((hueSegment % 2) - 1));
                float m = l - chroma / 2;
                float r, g, b;
                if (hueSegment < 1) {
                    r = chroma;
                    g = k;
                    b = 0;
                } else if (hueSegment < 2) {
                    r = k;
                    g = chroma;
                    b = 0;
                } else if (hueSegment < 3) {
                    r = 0;
                    g = chroma;
                    b = k;
                } else if (hueSegment < 4) {
                    r = 0;
                    g = k;
                    b = chroma;
                } else if (hueSegment < 5) {
                    r = k;
                    g = 0;
                    b = chroma;
                } else {
                    r = chroma;
                    g = 0;
                    b = k;
                }
                output[0][y][x] = (r + m) * MAX_VALUE;
                output[1][y][x] = (g + m) * MAX_VALUE;
                output[2][y][x] = (b + m) * MAX_VALUE;
            }
        }
        return output;
    }

    public static float[][][] stretchRgb(StretchingStrategy strategy, float[][] r, float[][] g, float[][] b, Map<Class<?>, Object> metadata) {
        int height = r.length;
        int width = r[0].length;
        var rgb = new float[][][]{copy(r), copy(g), copy(b)};
        var hsl = fromRGBtoHSL(rgb);
        var lightness = hsl[2];
        for (int y = 0; y < height; y++) {
            var row = lightness[y];
            for (int x = 0; x < width; x++) {
                row[x] *= 65535f;
            }
        }
        var lightnessImage = new ImageWrapper32(width, height, lightness, metadata);
        strategy.stretch(lightnessImage);
        for (int y = 0; y < height; y++) {
            var row = lightness[y];
            lightnessImage.pixels().getRow(y, row);
            for (int x = 0; x < width; x++) {
                row[x] /= 65535f;
            }
        }
        fromHSLtoRGB(hsl, rgb);
        var result = new float[3][][];
        for (int c = 0; c < 3; c++) {
            var channel = new ImageWrapper32(width, height, rgb[c], metadata);
            strategy.stretch(channel);
            result[c] = channel.pixels().toArray();
        }
        return result;
    }

    public static float[][][] filterLightness(float[][] r, float[][] g, float[][] b, UnaryOperator<Image> filter) {
        int height = r.length;
        int width = r[0].length;
        var hsl = fromRGBtoHSL(new float[][][]{copy(r), copy(g), copy(b)});
        hsl[2] = filter.apply(new Image(width, height, hsl[2])).data();
        return fromHSLtoRGB(hsl, new float[3][height][width]);
    }

    public static float[][][] shadowsHighlightsRgb(float[][] red, float[][] green, float[][] blue, BiConsumer<float[][], float[][]> adjust) {
        int height = red.length;
        int width = red[0].length;
        var r = copy(red);
        var g = copy(green);
        var b = copy(blue);
        var luminance = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                luminance[y][x] = (float) (0.2126 * r[y][x] + 0.7152 * g[y][x] + 0.0722 * b[y][x]);
            }
        }
        var adjusted = new float[height][width];
        adjust.accept(luminance, adjusted);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var lum = luminance[y][x];
                if (lum <= 0) {
                    continue;
                }
                var max = Math.max(r[y][x], Math.max(g[y][x], b[y][x]));
                var ratio = Math.min(adjusted[y][x] / lum, MAX_PIXEL_VALUE / max);
                r[y][x] *= ratio;
                g[y][x] *= ratio;
                b[y][x] *= ratio;
            }
        }
        return new float[][][]{r, g, b};
    }

    public static float[][][] colorize(int width, int height, float[][] mono, int[] rgb) {
        var stretched = new ImageWrapper32(width, height, mono, Map.of());
        new GammaStrategy(1.2).stretch(stretched);
        var copy = stretched.pixels().toArray();
        var r = new float[height][width];
        var g = new float[height][width];
        var b = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var gray = copy[y][x];
                r[y][x] = gray * rgb[0] / 255f;
                g[y][x] = gray * rgb[1] / 255f;
                b[y][x] = gray * rgb[2] / 255f;
            }
        }
        return stretchRgb(new LinearStrechingStrategy(0, 0.9f * Constants.MAX_PIXEL_VALUE), r, g, b, Map.of());
    }

    public static float[][] arcsinh(float[][] source, float blackPoint, float stretch) {
        var data = copy(source);
        var asinhStretch = asinh(stretch);
        var normalizedBlackPoint = blackPoint / MAX_PIXEL_VALUE;
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                var v = line[x];
                float sv;
                if (v == 0) {
                    sv = 0;
                } else {
                    double original = v / MAX_PIXEL_VALUE;
                    var pixel = Math.max(0, original - normalizedBlackPoint);
                    double stretched = (pixel * asinh(original * stretch)) / (original * asinhStretch);
                    sv = (float) (stretched * MAX_PIXEL_VALUE);
                }
                line[x] = sv;
                if (Float.valueOf(line[x]).isNaN()) {
                    line[x] = 0;
                }
                line[x] = Math.max(0, line[x]);
                line[x] = Math.min(MAX_PIXEL_VALUE, line[x]);
            }
        }
        return data;
    }

    public static float[][][] arcsinhRgb(float[][] r, float[][] g, float[][] b, float blackPoint, float stretch) {
        var rgb = new float[][][]{copy(r), copy(g), copy(b)};
        var asinhStretch = asinh(stretch);
        double max = MAX_PIXEL_VALUE;
        var bp = blackPoint / max;
        int height = r.length;
        int width = r[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double mean = (0.2126 * rgb[0][y][x] + 0.7152 * rgb[1][y][x] + 0.0722 * rgb[2][y][x]);
                for (int j = 0; j < rgb.length; j++) {
                    double original = rgb[j][y][x] / max;
                    var pixel = Math.max(0, original - bp);
                    double stretched = (pixel * asinh(original * stretch)) / (mean * asinhStretch);
                    rgb[j][y][x] = (float) (stretched * max);
                    if (Float.valueOf(rgb[j][y][x]).isNaN()) {
                        rgb[j][y][x] = 0;
                    }
                    rgb[j][y][x] = Math.max(0, rgb[j][y][x]);
                    rgb[j][y][x] = Math.min(MAX_PIXEL_VALUE, rgb[j][y][x]);
                }
            }
        }
        return rgb;
    }

    public static float[][] curve(float[][] source, double in, double out, double protectLo, double protectHi) {
        var data = copy(source);
        var curve = ColorCurve.cachedPolynomial((int) in, (int) out);
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                var v = line[x];
                if (v > protectLo && v < protectHi) {
                    line[x] = (float) Math.clamp(curve.applyAsDouble(v), 0, Constants.MAX_PIXEL_VALUE);
                }
            }
        }
        return data;
    }

    public static float[][] sigmoid(float[][] source, double midpoint, double steepness) {
        var data = copy(source);
        double sigmoidAtZero = sigmoid(0, midpoint, steepness);
        double sigmoidAtOne = sigmoid(1, midpoint, steepness);
        double sigmoidRange = sigmoidAtOne - sigmoidAtZero;
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                double normalized = line[x] / MAX_PIXEL_VALUE;
                double transformed = (sigmoid(normalized, midpoint, steepness) - sigmoidAtZero) / sigmoidRange;
                line[x] = (float) Math.clamp(transformed * MAX_PIXEL_VALUE, 0, MAX_PIXEL_VALUE);
            }
        }
        return data;
    }

    private static double sigmoid(double x, double midpoint, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * (x - midpoint)));
    }

    public static float[][] contrast(float[][] source, float min, float max) {
        var data = copy(source);
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                float v = line[x];
                if (v < min) {
                    line[x] = 0;
                } else if (v > max) {
                    line[x] = max;
                }
            }
        }
        return data;
    }

    public static float[][] mtf(float[][] source, double shadows8bit, double midtones, double highlights8bit) {
        var data = copy(source);
        var shadows = shadows8bit * 256;
        var highlights = highlights8bit * 256;
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                double value = line[x];
                double transformed;
                if (value <= shadows) {
                    transformed = 0;
                } else if (value >= highlights) {
                    transformed = MAX_PIXEL_VALUE;
                } else {
                    var xp = (value - shadows) / (highlights - shadows);
                    transformed = ((midtones - 1.0) * xp) / (((2.0 * midtones - 1.0) * xp) - midtones) * MAX_PIXEL_VALUE;
                }
                line[x] = (float) Math.clamp(transformed, 0, MAX_PIXEL_VALUE);
            }
        }
        return data;
    }

    public static float max(float[][] data, float initial) {
        var max = initial;
        for (var row : data) {
            for (var v : row) {
                max = Math.max(max, v);
            }
        }
        return max;
    }

    public static float[][] gamma(float[][] source, double gamma) {
        var data = copy(source);
        var max = max(data, 1e-7f);
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                var v = line[x];
                float normalized = v / max;
                float corrected = (float) Math.pow(normalized, gamma);
                line[x] = corrected * Constants.MAX_PIXEL_VALUE;
            }
        }
        return data;
    }

    public static float[][] negative(float[][] source) {
        var data = copy(source);
        var max = max(data, 0);
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                line[x] = max - line[x];
            }
        }
        return data;
    }

    public static float[] collect(float[][] data, BiPredicate<Integer, Integer> pixelMask) {
        var height = data.length;
        var width = height == 0 ? 0 : data[0].length;
        var pixels = new ArrayList<Float>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (pixelMask == null || pixelMask.test(x, y)) {
                    pixels.add(data[y][x]);
                }
            }
        }
        var result = new float[pixels.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = pixels.get(i);
        }
        return result;
    }

    public static float[][] percentile(float[][] source, double lowPercentile, double highPercentile, BiPredicate<Integer, Integer> pixelMask, boolean noClip, boolean extend) {
        var data = copy(source);
        var pixels = collect(data, pixelMask);
        if (pixels.length == 0) {
            return data;
        }
        Arrays.sort(pixels);
        float lowValue = pixels[(int) Math.round(lowPercentile / 100.0 * (pixels.length - 1))];
        float highPercentileValue = pixels[(int) Math.round(highPercentile / 100.0 * (pixels.length - 1))];
        float highValue = extend ? max(data, highPercentileValue) : highPercentileValue;
        if (highValue <= lowValue) {
            return data;
        }
        float range = highValue - lowValue;
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                float v = line[x];
                if (noClip) {
                    line[x] = ((v - lowValue) / range) * MAX_PIXEL_VALUE;
                } else if (v <= lowValue) {
                    line[x] = 0;
                } else if (v >= highValue) {
                    line[x] = MAX_PIXEL_VALUE;
                } else {
                    line[x] = ((v - lowValue) / range) * MAX_PIXEL_VALUE;
                }
            }
        }
        return data;
    }

    public static float[][] dynamic(float[][] source, float sourceLevel, double targetPosition) {
        var data = copy(source);
        var src = Math.min((int) sourceLevel >> 8, 255);
        var target = targetPosition * MAX_PIXEL_VALUE;
        double a = 0.8 * findLinearGrowthLimit(data, src, target);
        double linearSlope = target / a;
        double compressedSlope = (MAX_PIXEL_VALUE - target) / (MAX_PIXEL_VALUE - a);
        var observations = new ArrayList<WeightedObservedPoint>(65536);
        for (int i = 0; i < 65536; i++) {
            observations.add(new WeightedObservedPoint(1.0, i, i < a ? linearSlope * i : target + compressedSlope * (i - a)));
        }
        var fit = PolynomialCurveFitter.create(4).fit(observations);
        var poly = new PolynomialFunction(fit);
        for (var line : data) {
            for (int x = 0; x < line.length; x++) {
                var v = line[x];
                line[x] = (float) Math.clamp(poly.value(v), 0, MAX_PIXEL_VALUE);
            }
        }
        return data;
    }

    private static int findLinearGrowthLimit(float[][] data, int source, double target) {
        var values = Histogram.of(data, 256).values();
        var observations = new ArrayList<WeightedObservedPoint>();
        for (int i = Math.max(0, source - 16); i < Math.min(255, source + 16); i++) {
            observations.add(new WeightedObservedPoint(1.0, i, values[i]));
        }
        try {
            return gaussianEstimate(observations, source);
        } catch (Exception e) {
            observations = new ArrayList<>();
            for (int i = 1; i < 256; i++) {
                observations.add(new WeightedObservedPoint(1.0, i, values[i]));
            }
            try {
                return gaussianEstimate(observations, source);
            } catch (Exception e2) {
                return (int) (Math.min(255, target / 256) * 256);
            }
        }
    }

    private static int gaussianEstimate(ArrayList<WeightedObservedPoint> observations, int source) {
        var fit = GaussianCurveFitter.create().withMaxIterations(1024).fit(observations);
        int zeroCrossingIndex = (int) (fit[1] + 3 * fit[2]);
        return Math.min(255, Math.max(source, zeroCrossingIndex)) * 256;
    }

    public static float[][] geometryCorrection(float[][] buffer, GeometryTransform transform, float blackPoint) {
        var width = transform.width();
        var height = transform.height();
        var shear = transform.shear();
        var shift = transform.shift();
        var sx = transform.sx();
        var sy = transform.sy();
        var offsetX = transform.offsetX();
        var offsetY = transform.offsetY();
        var extendedWidth = transform.extendedWidth();
        var newWidth = transform.outputWidth();
        var newHeight = transform.outputHeight();
        var newBuffer = new float[newHeight][newWidth];
        var xSupport = sx < 1 ? 1 / sx : 1;
        var ySupport = sy < 1 ? 1 / sy : 1;
        for (int y = 0; y < newHeight; y++) {
            var v = (y - offsetY) / sy;
            var targetRow = newBuffer[y];
            if (v < 0 || v > height - 1) {
                Arrays.fill(targetRow, blackPoint);
                continue;
            }
            var sheared = shift - v * shear;
            for (int x = 0; x < newWidth; x++) {
                var u = (x - offsetX) / sx;
                targetRow[x] = u < 0 || u > extendedWidth - 1
                        ? blackPoint
                        : sampleCatmullRom(buffer, u + sheared, v, width, height, xSupport, ySupport);
            }
        }
        return newBuffer;
    }

    private static float sampleCatmullRom(float[][] data, double x, double y, int width, int height, double xSupport, double ySupport) {
        double value;
        if (ySupport == 1) {
            var j = (int) Math.floor(y);
            var t = y - j;
            if (t == 0) {
                value = sampleRow(data[clamp(j, height)], x, width, xSupport);
            } else {
                value = applyWeights(t,
                        sampleRow(data[clamp(j - 1, height)], x, width, xSupport),
                        sampleRow(data[clamp(j, height)], x, width, xSupport),
                        sampleRow(data[clamp(j + 1, height)], x, width, xSupport),
                        sampleRow(data[clamp(j + 2, height)], x, width, xSupport)
                );
            }
        } else {
            double sum = 0;
            double weightSum = 0;
            var from = (int) Math.ceil(y - KERNEL_RADIUS * ySupport);
            var to = (int) Math.floor(y + KERNEL_RADIUS * ySupport);
            for (int j = from; j <= to; j++) {
                var w = catmullRom((j - y) / ySupport);
                if (w != 0) {
                    sum += w * sampleRow(data[clamp(j, height)], x, width, xSupport);
                    weightSum += w;
                }
            }
            value = weightSum == 0 ? 0 : sum / weightSum;
        }
        if (value < 0) {
            return 0;
        }
        if (value > Constants.MAX_PIXEL_VALUE) {
            return Constants.MAX_PIXEL_VALUE;
        }
        return (float) value;
    }

    private static double sampleRow(float[] row, double x, int width, double support) {
        if (support == 1) {
            var i = (int) Math.floor(x);
            return applyWeights(x - i,
                    row[clamp(i - 1, width)],
                    row[clamp(i, width)],
                    row[clamp(i + 1, width)],
                    row[clamp(i + 2, width)]
            );
        }
        double sum = 0;
        double weightSum = 0;
        var from = (int) Math.ceil(x - KERNEL_RADIUS * support);
        var to = (int) Math.floor(x + KERNEL_RADIUS * support);
        for (int i = from; i <= to; i++) {
            var w = catmullRom((i - x) / support);
            if (w != 0) {
                sum += w * row[clamp(i, width)];
                weightSum += w;
            }
        }
        return weightSum == 0 ? 0 : sum / weightSum;
    }

    private static double applyWeights(double t, double vm1, double v0, double v1, double v2) {
        return catmullRom(t + 1) * vm1 + catmullRom(t) * v0 + catmullRom(1 - t) * v1 + catmullRom(2 - t) * v2;
    }

    private static double catmullRom(double t) {
        var abs = Math.abs(t);
        if (abs < 1) {
            return (1.5 * abs - 2.5) * abs * abs + 1;
        }
        if (abs < KERNEL_RADIUS) {
            return ((-0.5 * abs + 2.5) * abs - 4) * abs + 2;
        }
        return 0;
    }

    private static int clamp(int index, int length) {
        if (index < 0) {
            return 0;
        }
        if (index >= length) {
            return length - 1;
        }
        return index;
    }

    public static RGBImage rgb(float[][] r, float[][] g, float[][] b) {
        return new RGBImage(r[0].length, r.length, r, g, b, Map.of());
    }
}
