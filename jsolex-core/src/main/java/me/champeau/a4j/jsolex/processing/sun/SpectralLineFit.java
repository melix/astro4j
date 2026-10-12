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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.math.RowStrips;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;

/**
 * Measures the amplitude of an emission line over a background spectrum, pixel by pixel.
 * <p>
 * The input is a spectral cube: one image per wavelength sample, all registered on the same
 * geometry. At every pixel, the samples form a short spectrum which is modelled as the sum of
 * <ul>
 *     <li>a template of the background spectrum, measured as the average spectrum of a region
 *     of the image where the emission line is negligible, with a free amplitude</li>
 *     <li>a Gaussian line of known position and width, whose amplitude is the result</li>
 *     <li>a constant and a linear slope, absorbing smooth variations of the background</li>
 * </ul>
 * The coefficients are obtained by least squares. Since the design only depends on the
 * wavelength samples, the fit reduces to a weighted sum of the samples, computed once.
 * <p>
 * Unlike the difference between a line sample and continuum samples, the fit is not biased by
 * the absorption lines of the background: a background carrying the template spectrum, at any
 * level, gives a null line amplitude.
 * <p>
 * A wavelength shift of the background spectrum, for example a Doppler shift, is not in the
 * span of the template, and on a slope of the template it is mistaken for line emission. It is
 * corrected by adding the derivative of the template to the model, which measures the shift.
 * Because the shift varies smoothly across the image, its estimate can be averaged over a
 * neighbourhood before its contribution is removed, which avoids most of the noise the extra
 * term would otherwise add.
 * <p>
 * The template must not contain the emission line itself, otherwise the line is subtracted from
 * every pixel in proportion to its background. When the template region still holds some
 * emission, only its faintest part can be used: the region is divided into tiles as wide as the
 * region itself, and the template is measured on the tiles where the line is the weakest, which
 * are found iteratively.
 * <p>
 * Samples equal to zero are treated as missing: they are produced where a wavelength falls out
 * of the capture window. A sample is also discarded when one of its neighbours is missing,
 * since interpolations mix it with the missing values. Each pixel is fitted on its usable
 * samples only.
 */
public final class SpectralLineFit {
    private static final double FWHM_TO_SIGMA = 2 * Math.sqrt(2 * Math.log(2));
    private static final int BASE_TERMS = 4;
    private static final int LINE = 1;
    private static final int SHIFT = 4;
    private static final double SINGULARITY_THRESHOLD = 1e-12;
    private static final int MAX_SAMPLES = 63;
    private static final int MAX_TEMPLATE_ITERATIONS = 20;
    private static final double OUTLIER_SIGMAS = 3;
    private static final double MAD_TO_SIGMA = 1.4826;

    private SpectralLineFit() {
    }

    /**
     * Computes the line amplitude at every pixel.
     *
     * @param width the width of the images
     * @param height the height of the images
     * @param cube the samples, one image per wavelength, as [sample][y][x]
     * @param positions the position of each sample along the spectrum, in pixels
     * @param linePosition the position of the centre of the emission line, in pixels
     * @param fwhm the full width at half maximum of the emission line, in pixels
     * @param template the pixels the background spectrum is measured on
     * @param region the pixels which are fitted, or null for all of them. The other pixels are
     * set to 0.
     * @param shiftSmoothing 0 to ignore wavelength shifts of the background, 1 to correct them
     * pixel by pixel, or the size, in pixels, of the neighbourhood over which the shift is
     * averaged before being corrected
     * @param templateFraction the fraction of the template region, where the line is the
     * weakest, on which the background spectrum is measured. 1 uses the whole region.
     * @return the line amplitude, in the unit of the samples
     */
    public static float[][] fit(int width,
                                int height,
                                float[][][] cube,
                                double[] positions,
                                double linePosition,
                                double fwhm,
                                BiPredicate<Integer, Integer> template,
                                BiPredicate<Integer, Integer> region,
                                int shiftSmoothing,
                                double templateFraction) {
        var n = cube.length;
        if (n != positions.length) {
            throw new IllegalArgumentException("Expected one position per sample");
        }
        if (n > MAX_SAMPLES) {
            throw new IllegalArgumentException("At most " + MAX_SAMPLES + " samples are supported, found " + n);
        }
        if (fwhm <= 0) {
            throw new IllegalArgumentException("The line width must be positive");
        }
        if (shiftSmoothing < 0) {
            throw new IllegalArgumentException("The shift smoothing must be positive or zero");
        }
        if (templateFraction <= 0 || templateFraction > 1) {
            throw new IllegalArgumentException("The template fraction must be greater than 0 and at most 1");
        }
        var unusable = unusableSamples(width, height, cube);
        var templateSpectrum = templateFraction < 1
                ? measureFaintestTemplate(width, height, cube, unusable, template, positions, linePosition, fwhm, templateFraction)
                : measureTemplate(width, height, cube, unusable, template);
        var design = new Design(columns(positions, templateSpectrum, linePosition, fwhm), shiftSmoothing > 0);
        var amplitude = new float[height][width];
        var shift = shiftSmoothing > 0 ? new float[height][width] : null;
        var leak = shiftSmoothing > 0 ? new float[height][width] : null;
        var fitted = new boolean[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    if (region != null && !region.test(x, y)) {
                        continue;
                    }
                    var weights = design.weightsFor(~unusable[y][x] & design.allSamples);
                    if (weights == null) {
                        continue;
                    }
                    amplitude[y][x] = (float) weightedSum(cube, weights.line, x, y);
                    if (shift != null) {
                        shift[y][x] = (float) weightedSum(cube, weights.shift, x, y);
                        leak[y][x] = (float) weights.leak;
                    }
                    fitted[y][x] = true;
                }
            }
        });
        if (shift != null) {
            var correction = shiftSmoothing > 1 ? boxAverage(width, height, shift, fitted, shiftSmoothing) : shift;
            RowStrips.forEach(height, (yStart, yEnd) -> {
                for (int y = yStart; y < yEnd; y++) {
                    for (int x = 0; x < width; x++) {
                        if (fitted[y][x]) {
                            amplitude[y][x] -= leak[y][x] * correction[y][x];
                        }
                    }
                }
            });
        }
        return amplitude;
    }

    private static double weightedSum(float[][][] cube, double[] weights, int x, int y) {
        var sum = 0d;
        for (int i = 0; i < weights.length; i++) {
            var w = weights[i];
            if (w != 0) {
                sum += w * cube[i][y][x];
            }
        }
        return sum;
    }

    /**
     * Returns, for every pixel, a bit set of the samples which cannot be used: missing samples,
     * and samples with a missing neighbour.
     */
    private static long[][] unusableSamples(int width, int height, float[][][] cube) {
        var missing = new long[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    var bits = 0L;
                    for (int i = 0; i < cube.length; i++) {
                        var value = cube[i][y][x];
                        if (value == 0 || !Float.isFinite(value)) {
                            bits |= 1L << i;
                        }
                    }
                    missing[y][x] = bits;
                }
            }
        });
        var unusable = new long[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    var bits = 0L;
                    for (int dy = Math.max(0, y - 1); dy <= Math.min(height - 1, y + 1); dy++) {
                        for (int dx = Math.max(0, x - 1); dx <= Math.min(width - 1, x + 1); dx++) {
                            bits |= missing[dy][dx];
                        }
                    }
                    unusable[y][x] = bits;
                }
            }
        });
        return unusable;
    }

    /**
     * Averages the spectrum of the template pixels. Only the pixels where every sample is usable
     * are taken, so that each sample of the template is measured on the same pixels.
     */
    private static double[] measureTemplate(int width, int height, float[][][] cube, long[][] unusable, BiPredicate<Integer, Integer> template) {
        var n = cube.length;
        var partials = RowStrips.map(height, (yStart, yEnd) -> {
            var sums = new double[n + 1];
            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    if (unusable[y][x] != 0 || !template.test(x, y)) {
                        continue;
                    }
                    for (int i = 0; i < n; i++) {
                        sums[i] += cube[i][y][x];
                    }
                    sums[n]++;
                }
            }
            return sums;
        });
        var total = new double[n + 1];
        for (var partial : partials) {
            for (int i = 0; i <= n; i++) {
                total[i] += partial[i];
            }
        }
        var count = total[n];
        if (count == 0) {
            throw noUsableTemplatePixel();
        }
        var spectrum = new double[n];
        for (int i = 0; i < n; i++) {
            spectrum[i] = total[i] / count;
        }
        return normalizeToPeak(spectrum);
    }

    private static IllegalArgumentException noUsableTemplatePixel() {
        return new IllegalArgumentException("The template region doesn't contain any pixel where all samples are usable");
    }

    /**
     * Scales a template spectrum so that its largest absolute value is 1.
     */
    private static double[] normalizeToPeak(double[] spectrum) {
        var max = 0d;
        for (var value : spectrum) {
            max = Math.max(max, Math.abs(value));
        }
        if (max == 0) {
            throw new IllegalArgumentException("The template spectrum is null");
        }
        for (int i = 0; i < spectrum.length; i++) {
            spectrum[i] /= max;
        }
        return spectrum;
    }

    /**
     * Measures the background spectrum on the part of the template region where the line is the
     * weakest. The region is divided into square tiles whose side is the thickness of the region,
     * so that the tiles follow its scale whatever the resolution. Each tile spectrum is fitted with
     * the current template, the tiles whose line to background ratio is an outlier are discarded,
     * and the template is measured again on the requested fraction of the remaining tiles with the
     * lowest ratio, until the selection no longer changes.
     */
    private static double[] measureFaintestTemplate(int width,
                                                    int height,
                                                    float[][][] cube,
                                                    long[][] unusable,
                                                    BiPredicate<Integer, Integer> template,
                                                    double[] positions,
                                                    double linePosition,
                                                    double fwhm,
                                                    double fraction) {
        var n = cube.length;
        var usable = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                usable[y][x] = unusable[y][x] == 0 && template.test(x, y);
            }
        }
        var size = Math.max(1, (int) Math.round(regionThickness(width, height, usable)));
        var tilesX = (width + size - 1) / size;
        var tileCount = tilesX * ((height + size - 1) / size);
        var sums = new double[tileCount][n];
        var counts = new int[tileCount];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!usable[y][x]) {
                    continue;
                }
                var tile = (y / size) * tilesX + x / size;
                for (int i = 0; i < n; i++) {
                    sums[tile][i] += cube[i][y][x];
                }
                counts[tile]++;
            }
        }
        var occupied = IntStream.range(0, tileCount).filter(t -> counts[t] > 0).toArray();
        if (occupied.length == 0) {
            throw noUsableTemplatePixel();
        }
        var medianCount = median(Arrays.stream(occupied).mapToDouble(t -> counts[t]).toArray());
        var tiles = Arrays.stream(occupied).filter(t -> counts[t] >= medianCount / 2).toArray();
        var selected = tiles;
        var spectrum = averageSpectrum(sums, counts, selected, n);
        for (int iteration = 0; iteration < MAX_TEMPLATE_ITERATIONS && tiles.length > 1; iteration++) {
            var design = new Design(columns(positions, spectrum, linePosition, fwhm), true);
            var lineWeights = design.pseudoInverseRow(design.allSamples, BASE_TERMS + 1, LINE);
            var levelWeights = design.pseudoInverseRow(design.allSamples, BASE_TERMS + 1, 0);
            if (lineWeights == null || levelWeights == null) {
                break;
            }
            var ratios = new double[tiles.length];
            for (int k = 0; k < tiles.length; k++) {
                var tile = sums[tiles[k]];
                var line = 0d;
                var level = 0d;
                for (int i = 0; i < n; i++) {
                    line += lineWeights[i] * tile[i];
                    level += levelWeights[i] * tile[i];
                }
                ratios[k] = line / level;
            }
            var next = faintestInliers(tiles, ratios, fraction);
            if (Arrays.equals(next, selected)) {
                break;
            }
            selected = next;
            spectrum = averageSpectrum(sums, counts, selected, n);
        }
        return spectrum;
    }

    /**
     * Returns the tiles with the lowest ratios, among those which are not outliers.
     */
    private static int[] faintestInliers(int[] tiles, double[] ratios, double fraction) {
        var center = median(ratios.clone());
        var deviations = new double[ratios.length];
        for (int k = 0; k < ratios.length; k++) {
            deviations[k] = Math.abs(ratios[k] - center);
        }
        var limit = OUTLIER_SIGMAS * MAD_TO_SIGMA * median(deviations);
        var inliers = IntStream.range(0, tiles.length)
                .filter(k -> Double.isFinite(ratios[k]) && (limit == 0 || Math.abs(ratios[k] - center) <= limit))
                .boxed()
                .sorted(Comparator.comparingDouble(k -> ratios[k]))
                .mapToInt(Integer::intValue)
                .toArray();
        if (inliers.length == 0) {
            return tiles;
        }
        var kept = Math.max(1, (int) Math.ceil(fraction * inliers.length));
        return Arrays.stream(inliers, 0, kept).map(k -> tiles[k]).sorted().toArray();
    }

    private static double[] averageSpectrum(double[][] sums, int[] counts, int[] tiles, int n) {
        var spectrum = new double[n];
        var total = 0d;
        for (var tile : tiles) {
            for (int i = 0; i < n; i++) {
                spectrum[i] += sums[tile][i];
            }
            total += counts[tile];
        }
        for (int i = 0; i < n; i++) {
            spectrum[i] /= total;
        }
        return normalizeToPeak(spectrum);
    }

    /**
     * Computes the thickness of a region, as twice the largest distance from one of its pixels to
     * its edge, using a two pass chamfer distance transform.
     */
    static double regionThickness(int width, int height, boolean[][] region) {
        var distance = new double[height][width];
        var diagonal = Math.sqrt(2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!region[y][x]) {
                    continue;
                }
                var d = Double.POSITIVE_INFINITY;
                d = Math.min(d, x > 0 ? distance[y][x - 1] + 1 : 1);
                d = Math.min(d, y > 0 ? distance[y - 1][x] + 1 : 1);
                d = Math.min(d, x > 0 && y > 0 ? distance[y - 1][x - 1] + diagonal : diagonal);
                d = Math.min(d, x < width - 1 && y > 0 ? distance[y - 1][x + 1] + diagonal : diagonal);
                distance[y][x] = d;
            }
        }
        var max = 0d;
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                if (!region[y][x]) {
                    continue;
                }
                var d = distance[y][x];
                d = Math.min(d, x < width - 1 ? distance[y][x + 1] + 1 : 1);
                d = Math.min(d, y < height - 1 ? distance[y + 1][x] + 1 : 1);
                d = Math.min(d, x < width - 1 && y < height - 1 ? distance[y + 1][x + 1] + diagonal : diagonal);
                d = Math.min(d, x > 0 && y < height - 1 ? distance[y + 1][x - 1] + diagonal : diagonal);
                distance[y][x] = d;
                max = Math.max(max, d);
            }
        }
        return 2 * max;
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        var n = values.length;
        return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2;
    }

    /**
     * Builds the columns of the design matrix: template, line, constant, slope and derivative of
     * the template.
     */
    private static double[][] columns(double[] positions, double[] template, double linePosition, double fwhm) {
        var n = positions.length;
        var sigma = fwhm / FWHM_TO_SIGMA;
        var min = Double.POSITIVE_INFINITY;
        var max = Double.NEGATIVE_INFINITY;
        for (var p : positions) {
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        var span = max > min ? max - min : 1;
        var columns = new double[BASE_TERMS + 1][n];
        columns[SHIFT] = splineDerivatives(positions, template);
        for (int i = 0; i < n; i++) {
            var offset = (positions[i] - linePosition) / sigma;
            columns[0][i] = template[i];
            columns[LINE][i] = Math.exp(-0.5 * offset * offset);
            columns[2][i] = 1;
            columns[3][i] = (positions[i] - min) / span;
        }
        return columns;
    }

    /**
     * Computes the derivative of the natural cubic spline interpolating the values at each
     * sample. A finite difference is not accurate enough when a line is only a few samples
     * wide, and an inaccurate derivative leaves part of the shift in the line amplitude.
     */
    static double[] splineDerivatives(double[] positions, double[] values) {
        var n = positions.length;
        var derivatives = new double[n];
        if (n < 2) {
            return derivatives;
        }
        var order = IntStream.range(0, n)
                .boxed()
                .sorted(Comparator.comparingDouble(i -> positions[i]))
                .mapToInt(Integer::intValue)
                .toArray();
        var x = new double[n];
        var y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = positions[order[i]];
            y[i] = values[order[i]];
        }
        var h = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
            if (h[i] <= 0) {
                throw new IllegalArgumentException("Samples must have distinct positions");
            }
        }
        var second = new double[n];
        if (n > 2) {
            var diagonal = new double[n - 2];
            var rhs = new double[n - 2];
            for (int i = 1; i < n - 1; i++) {
                diagonal[i - 1] = 2 * (h[i - 1] + h[i]);
                rhs[i - 1] = 6 * ((y[i + 1] - y[i]) / h[i] - (y[i] - y[i - 1]) / h[i - 1]);
            }
            for (int i = 1; i < n - 2; i++) {
                var factor = h[i] / diagonal[i - 1];
                diagonal[i] -= factor * h[i];
                rhs[i] -= factor * rhs[i - 1];
            }
            second[n - 2] = rhs[n - 3] / diagonal[n - 3];
            for (int i = n - 3; i >= 1; i--) {
                second[i] = (rhs[i - 1] - h[i] * second[i + 1]) / diagonal[i - 1];
            }
        }
        for (int i = 0; i < n - 1; i++) {
            derivatives[order[i]] = (y[i + 1] - y[i]) / h[i] - h[i] * (2 * second[i] + second[i + 1]) / 6;
        }
        var last = n - 2;
        derivatives[order[n - 1]] = (y[n - 1] - y[last]) / h[last] + h[last] * (second[last] + 2 * second[n - 1]) / 6;
        return derivatives;
    }

    /**
     * Averages the values of the fitted pixels over a square neighbourhood, using summed area
     * tables so the cost doesn't depend on the size of the neighbourhood.
     */
    private static float[][] boxAverage(int width, int height, float[][] values, boolean[][] valid, int size) {
        var sums = new double[height + 1][width + 1];
        var counts = new int[height + 1][width + 1];
        for (int y = 0; y < height; y++) {
            var rowSum = 0d;
            var rowCount = 0;
            for (int x = 0; x < width; x++) {
                if (valid[y][x]) {
                    rowSum += values[y][x];
                    rowCount++;
                }
                sums[y + 1][x + 1] = sums[y][x + 1] + rowSum;
                counts[y + 1][x + 1] = counts[y][x + 1] + rowCount;
            }
        }
        var half = size / 2;
        var result = new float[height][width];
        RowStrips.forEach(height, (yStart, yEnd) -> {
            for (int y = yStart; y < yEnd; y++) {
                var y0 = Math.max(0, y - half);
                var y1 = Math.min(height, y + half + 1);
                for (int x = 0; x < width; x++) {
                    if (!valid[y][x]) {
                        continue;
                    }
                    var x0 = Math.max(0, x - half);
                    var x1 = Math.min(width, x + half + 1);
                    var count = counts[y1][x1] - counts[y0][x1] - counts[y1][x0] + counts[y0][x0];
                    var sum = sums[y1][x1] - sums[y0][x1] - sums[y1][x0] + sums[y0][x0];
                    result[y][x] = (float) (sum / count);
                }
            }
        });
        return result;
    }

    /**
     * The least squares weights for a set of usable samples.
     *
     * @param line the weights giving the line amplitude, without shift term
     * @param shift the weights giving the shift coefficient, with the shift term
     * @param leak the line amplitude produced by a unit shift coefficient, without shift term
     */
    private record Weights(double[] line, double[] shift, double leak) {
    }

    private static final class Design {
        private static final Weights UNSOLVABLE = new Weights(null, null, 0);

        private final double[][] columns;
        private final boolean withShift;
        private final long allSamples;
        private final Map<Long, Weights> cache = new ConcurrentHashMap<>();

        private Design(double[][] columns, boolean withShift) {
            this.columns = columns;
            this.withShift = withShift;
            this.allSamples = (1L << columns[0].length) - 1;
        }

        Weights weightsFor(long samples) {
            var weights = cache.computeIfAbsent(samples, this::solve);
            return weights == UNSOLVABLE ? null : weights;
        }

        private Weights solve(long samples) {
            var base = pseudoInverseRow(samples, BASE_TERMS, LINE);
            if (base == null) {
                return UNSOLVABLE;
            }
            if (!withShift) {
                return new Weights(base, null, 0);
            }
            var shift = pseudoInverseRow(samples, BASE_TERMS + 1, SHIFT);
            if (shift == null) {
                return UNSOLVABLE;
            }
            var leak = 0d;
            for (int i = 0; i < base.length; i++) {
                leak += base[i] * columns[SHIFT][i];
            }
            return new Weights(base, shift, leak);
        }

        /**
         * Computes the row of the pseudo-inverse of the design matrix restricted to the given
         * samples and to the first terms, which gives one fitted coefficient as a weighted sum
         * of the samples. Weights of unused samples are 0.
         */
        private double[] pseudoInverseRow(long samples, int terms, int coefficient) {
            var n = columns[0].length;
            if (Long.bitCount(samples) <= terms) {
                return null;
            }
            var gram = new double[terms][terms];
            for (int a = 0; a < terms; a++) {
                for (int b = a; b < terms; b++) {
                    var sum = 0d;
                    for (int i = 0; i < n; i++) {
                        if ((samples & (1L << i)) != 0) {
                            sum += columns[a][i] * columns[b][i];
                        }
                    }
                    gram[a][b] = sum;
                    gram[b][a] = sum;
                }
            }
            var unit = new double[terms];
            unit[coefficient] = 1;
            var row = solveLinearSystem(gram, unit);
            if (row == null) {
                return null;
            }
            var weights = new double[n];
            for (int i = 0; i < n; i++) {
                if ((samples & (1L << i)) == 0) {
                    continue;
                }
                var sum = 0d;
                for (int a = 0; a < terms; a++) {
                    sum += row[a] * columns[a][i];
                }
                weights[i] = sum;
            }
            return weights;
        }
    }

    /**
     * Solves a small dense linear system by Gaussian elimination with partial pivoting.
     *
     * @return the solution, or null if the system is singular
     */
    static double[] solveLinearSystem(double[][] matrix, double[] rhs) {
        var n = rhs.length;
        var a = new double[n][n + 1];
        var scale = 0d;
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
            a[i][n] = rhs[i];
            scale = Math.max(scale, Math.abs(matrix[i][i]));
        }
        for (int col = 0; col < n; col++) {
            var pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(a[pivot][col]) <= SINGULARITY_THRESHOLD * scale) {
                return null;
            }
            var tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                var factor = a[r][col] / a[col][col];
                if (factor != 0) {
                    for (int c = col; c <= n; c++) {
                        a[r][c] -= factor * a[col][c];
                    }
                }
            }
        }
        var solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = a[i][n] / a[i][i];
        }
        return solution;
    }
}
