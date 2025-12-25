/*
 * Copyright 2025-2025 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.expr.stacking;

import me.champeau.a4j.math.MathUtils;
import me.champeau.a4j.math.fft.FFTSupport;
import me.champeau.a4j.math.opencl.OpenCLSupport;
import me.champeau.a4j.math.regression.DistortionGridFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a distorsion map, which is a map of deltas in the x and y axis.
 * The map is created using a grid of samples, and then interpolated using local bicubic interpolation.
 */
public class DistorsionMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistorsionMap.class);


    static {
//        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DistorsionMap.class)).setLevel(Level.DEBUG);
    }

    private static final int FORMAT_VERSION = 2;
    private static final int LUT_SIZE = 256;
    private static final double[] CUBIC_WEIGHT_LUT = precomputeCubicWeights();

    /**
     * Threshold for MAD-based outlier rejection (number of scaled MADs from median).
     */
    private static final double MAD_THRESHOLD = 1.5;

    /**
     * Reference step size used to scale filtering parameters.
     * Parameters are tuned for this step size and scaled proportionally for other sizes.
     */
    private static final double REFERENCE_STEP_FOR_SCALING = 64.0;

    /**
     * Base window size for outlier detection (before scaling by step ratio).
     */
    private static final int BASE_OUTLIER_WINDOW_SIZE = 5;

    /**
     * Minimum window size for outlier detection after scaling.
     */
    private static final int MIN_OUTLIER_WINDOW_SIZE = 3;

    /**
     * Maximum window size for outlier detection after scaling.
     */
    private static final int MAX_OUTLIER_WINDOW_SIZE = 11;

    /**
     * Minimum Gaussian sigma for smoothing (prevents over-sharpening at large step sizes).
     */
    private static final double MIN_SMOOTHING_SIGMA = 0.5;

    /**
     * Minimum grid size (width × height) to trigger GPU-accelerated filtering.
     */
    private static final int GPU_GRID_SIZE_THRESHOLD = 1000;

    /**
     * Counter for generating unique debug image filenames.
     */
    private static final AtomicInteger DEBUG_IMAGE_COUNTER = new AtomicInteger(0);

    /**
     * Search radius for inverse distance weighting during interpolation.
     * The LUT covers offsets from -SEARCH_RADIUS to +SEARCH_RADIUS.
     */
    private static final int SEARCH_RADIUS = 3;

    /**
     * Precomputed inverse-squared distance weights for the 7×7 search pattern.
     * Index: [dy + SEARCH_RADIUS][dx + SEARCH_RADIUS]
     * Value: 1/(dx² + dy²), or 0 if dx=dy=0
     */
    private static final double[][] INVERSE_DIST_SQ_LUT = precomputeInverseDistanceSquared();

    private final int step;
    private final int tileSize;
    private final double[][][] dxy;
    private final boolean[][] sampled;
    private final int gridXSize;
    private final int gridYSize;
    private double totalDistorsion = -1;
    private final Lock cacheLock = new ReentrantLock();
    private double[][] cachedTileErrors;

    private static double[] precomputeCubicWeights() {
        var lut = new double[LUT_SIZE * 4];
        for (var i = 0; i < LUT_SIZE; i++) {
            var t = i / (double) (LUT_SIZE - 1);
            for (var offset = 0; offset < 4; offset++) {
                var distance = Math.abs(t - (offset - 1));
                lut[i * 4 + offset] = cubicWeightDirect(distance);
            }
        }
        return lut;
    }

    /**
     * Precomputes 1/(dx² + dy²) for the fixed 7×7 search pattern.
     * Used for inverse-distance-squared weighted interpolation of unsampled points.
     * This eliminates sqrt() and division operations during interpolation.
     */
    private static double[][] precomputeInverseDistanceSquared() {
        int size = SEARCH_RADIUS * 2 + 1;
        var lut = new double[size][size];
        for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                if (dx == 0 && dy == 0) {
                    lut[dy + SEARCH_RADIUS][dx + SEARCH_RADIUS] = 0;
                } else {
                    double distSq = dx * dx + dy * dy;
                    lut[dy + SEARCH_RADIUS][dx + SEARCH_RADIUS] = 1.0 / distSq;
                }
            }
        }
        return lut;
    }

    /**
     * Returns the precomputed inverse-squared distance weight for the given offset.
     * Used for inverse-distance-squared weighted interpolation.
     *
     * @param dx horizontal offset from -SEARCH_RADIUS to +SEARCH_RADIUS
     * @param dy vertical offset from -SEARCH_RADIUS to +SEARCH_RADIUS
     * @return 1/(dx² + dy²), or 0 if dx=dy=0
     * @throws ArrayIndexOutOfBoundsException if offsets exceed SEARCH_RADIUS
     */
    public static double getInverseDistanceSquaredWeight(int dx, int dy) {
        return INVERSE_DIST_SQ_LUT[dy + SEARCH_RADIUS][dx + SEARCH_RADIUS];
    }

    private static double cubicWeightDirect(double t) {
        var a = -0.5;
        var absT = Math.abs(t);
        if (absT <= 1.0) {
            return (a + 2.0) * absT * absT * absT - (a + 3.0) * absT * absT + 1.0;
        } else if (absT < 2.0) {
            return a * absT * absT * absT - 5.0 * a * absT * absT + 8.0 * a * absT - 4.0 * a;
        } else {
            return 0.0;
        }
    }

    public DistorsionMap(int width, int height, int tileSize, int step) {
        this.step = step;
        this.tileSize = tileSize;
        var xSamples = ((width + tileSize) / step) + 1;
        var ySamples = ((height + tileSize) / step) + 1;
        this.dxy = new double[ySamples][xSamples][2];
        this.sampled = new boolean[ySamples][xSamples];
        this.gridYSize = dxy.length;
        this.gridXSize = dxy[0].length;
    }

    private DistorsionMap(int step, int tileSize, double[][][] dxy, boolean[][] sampled) {
        this.step = step;
        this.tileSize = tileSize;
        this.dxy = dxy;
        this.sampled = sampled;
        this.gridYSize = dxy.length;
        this.gridXSize = dxy[0].length;
    }

    private static DistorsionMap createWithAllSampled(int step, int tileSize, double[][][] dxy) {
        var rows = dxy.length;
        var cols = dxy[0].length;
        var sampled = new boolean[rows][cols];
        for (var y = 0; y < rows; y++) {
            for (var x = 0; x < cols; x++) {
                sampled[y][x] = true;
            }
        }
        return new DistorsionMap(step, tileSize, dxy, sampled);
    }

    public static DistorsionMap synthesize(List<DistorsionMap> maps, int width, int height) {
        if (maps.isEmpty()) {
            throw new IllegalArgumentException("Cannot synthesize empty list of maps");
        }
        if (maps.size() == 1) {
            return maps.getFirst();
        }

        int minStep = maps.stream().mapToInt(m -> m.step).min().orElseThrow();
        int minTileSize = maps.stream().mapToInt(m -> m.tileSize).min().orElseThrow();

        var result = new DistorsionMap(width, height, minTileSize, minStep);

        for (int gy = 0; gy < result.gridYSize; gy++) {
            for (int gx = 0; gx < result.gridXSize; gx++) {
                int px = gx * minStep;
                int py = gy * minStep;

                double totalDx = 0;
                double totalDy = 0;
                double currentX = px;
                double currentY = py;

                for (var map : maps) {
                    var delta = map.findDistorsion((int) currentX, (int) currentY);
                    totalDx += delta.dx();
                    totalDy += delta.dy();
                    currentX += delta.dx();
                    currentY += delta.dy();
                }

                result.dxy[gy][gx][0] = totalDx;
                result.dxy[gy][gx][1] = totalDy;
                result.sampled[gy][gx] = true;
            }
        }

        LOGGER.debug("Synthesized {} maps into one: step={}, tileSize={}, gridSize={}x{}",
            maps.size(), minStep, minTileSize, result.gridXSize, result.gridYSize);

        return result;
    }

    public static DistorsionMap average(List<DistorsionMap> maps) {
        if (maps.isEmpty()) {
            throw new IllegalArgumentException("Cannot average empty list of maps");
        }
        if (maps.size() == 1) {
            return maps.getFirst();
        }

        var first = maps.getFirst();
        var step = first.step;
        var tileSize = first.tileSize;
        var gridYSize = first.gridYSize;
        var gridXSize = first.gridXSize;

        // Validate all maps have the same dimensions
        for (var i = 1; i < maps.size(); i++) {
            var map = maps.get(i);
            if (map.gridYSize != gridYSize || map.gridXSize != gridXSize) {
                throw new IllegalArgumentException(
                    String.format("Map %d has dimensions %dx%d but expected %dx%d",
                        i, map.gridXSize, map.gridYSize, gridXSize, gridYSize));
            }
        }

        var dxy = new double[gridYSize][gridXSize][2];
        var sampled = new boolean[gridYSize][gridXSize];

        for (var gy = 0; gy < gridYSize; gy++) {
            for (var gx = 0; gx < gridXSize; gx++) {
                var sumDx = 0.0;
                var sumDy = 0.0;
                var count = 0;
                for (var map : maps) {
                    if (map.sampled[gy][gx]) {
                        sumDx += map.dxy[gy][gx][0];
                        sumDy += map.dxy[gy][gx][1];
                        count++;
                    }
                }
                if (count > 0) {
                    dxy[gy][gx][0] = sumDx / count;
                    dxy[gy][gx][1] = sumDy / count;
                    sampled[gy][gx] = true;
                }
            }
        }

        return new DistorsionMap(step, tileSize, dxy, sampled);
    }

    public DistorsionMap negate() {
        var negatedDxy = new double[gridYSize][gridXSize][2];
        var copiedSampled = new boolean[gridYSize][gridXSize];

        for (var gy = 0; gy < gridYSize; gy++) {
            for (var gx = 0; gx < gridXSize; gx++) {
                negatedDxy[gy][gx][0] = -dxy[gy][gx][0];
                negatedDxy[gy][gx][1] = -dxy[gy][gx][1];
                copiedSampled[gy][gx] = sampled[gy][gx];
            }
        }

        return new DistorsionMap(step, tileSize, negatedDxy, copiedSampled);
    }

    public void recordDistorsion(int x, int y, double dx, double dy) {
        var offset = tileSize / 2;
        var sampleX = (x - offset) / step;
        var sampleY = (y - offset) / step;
        dxy[sampleY][sampleX][0] = dx;
        dxy[sampleY][sampleX][1] = dy;
        sampled[sampleY][sampleX] = true;
    }

    public void recordUnreliable(int x, int y) {
        var offset = tileSize / 2;
        var sampleX = (x - offset) / step;
        var sampleY = (y - offset) / step;
        dxy[sampleY][sampleX][0] = 0;
        dxy[sampleY][sampleX][1] = 0;
        sampled[sampleY][sampleX] = false;
    }

    public DeltaXY findDistorsion(int x, int y) {
        var ax = ((double) x) / step;
        var ay = ((double) y) / step;

        if (ax < 0 || ax >= gridXSize - 1 || ay < 0 || ay >= gridYSize - 1) {
            return new DeltaXY(0, 0);
        }

        var dx = bicubicInterpolate(ax, ay, 0);
        var dy = bicubicInterpolate(ax, ay, 1);
        return new DeltaXY(dx, dy);
    }

    private double bicubicInterpolate(double x, double y, int component) {
        var x0 = (int) Math.floor(x);
        var y0 = (int) Math.floor(y);

        var dx = x - x0;
        var dy = y - y0;

        var dxIdx = (int) (dx * (LUT_SIZE - 1));
        var dyIdx = (int) (dy * (LUT_SIZE - 1));

        var dxBase = dxIdx * 4;
        var dyBase = dyIdx * 4;

        var result = 0.0;
        for (var i = 0; i < 4; i++) {
            var wy = CUBIC_WEIGHT_LUT[dyBase + i];
            var yi = Math.min(Math.max(y0 - 1 + i, 0), gridYSize - 1);
            for (var j = 0; j < 4; j++) {
                var xi = Math.min(Math.max(x0 - 1 + j, 0), gridXSize - 1);
                var wx = CUBIC_WEIGHT_LUT[dxBase + j];
                result += dxy[yi][xi][component] * wx * wy;
            }
        }

        return result;
    }

    public double error() {
        return totalDistorsion() / (dxy.length * dxy[0].length);
    }

    public double totalDistorsion() {
        if (totalDistorsion < 0) {
            totalDistorsion = computeTotalDistorsion();
        }
        return totalDistorsion;
    }

    private double computeTotalDistorsion() {
        double total = 0.0;
        for (var row : dxy) {
            for (var delta : row) {
                total += Math.hypot(delta[0], delta[1]);
            }
        }
        return total;
    }

    private void ensureTileErrorsComputed(int tileSize) {
        if (cachedTileErrors != null) {
            return;
        }

        cacheLock.lock();
        try {
            if (cachedTileErrors != null) {
                return;
            }

            var tilesX = (dxy[0].length * step + tileSize - 1) / tileSize;
            var tilesY = (dxy.length * step + tileSize - 1) / tileSize;
            cachedTileErrors = new double[tilesY][tilesX];

            for (var ty = 0; ty < tilesY; ty++) {
                for (var tx = 0; tx < tilesX; tx++) {
                    var startX = tx * tileSize;
                    var startY = ty * tileSize;
                    var endX = Math.min(startX + tileSize, dxy[0].length * step);
                    var endY = Math.min(startY + tileSize, dxy.length * step);

                    var sampleStartX = startX / step;
                    var sampleEndX = (endX + step - 1) / step;
                    var sampleStartY = startY / step;
                    var sampleEndY = (endY + step - 1) / step;

                    var sum = 0.0;
                    var count = 0;
                    for (var sy = sampleStartY; sy < sampleEndY && sy < dxy.length; sy++) {
                        for (var sx = sampleStartX; sx < sampleEndX && sx < dxy[0].length; sx++) {
                            sum += Math.hypot(dxy[sy][sx][0], dxy[sy][sx][1]);
                            count++;
                        }
                    }
                    cachedTileErrors[ty][tx] = count > 0 ? sum / count : 0;
                }
            }
        } finally {
            cacheLock.unlock();
        }
    }

    public static int estimateTurbulenceScale(List<DistorsionMap> distortionMaps) {
        var allMagnitudes = new ArrayList<double[][]>();
        var step = distortionMaps.getFirst().step;

        for (var map : distortionMaps) {
            var gridHeight = map.dxy.length;
            var gridWidth = map.dxy[0].length;
            var magnitudes = new double[gridHeight][gridWidth];
            for (var y = 0; y < gridHeight; y++) {
                for (var x = 0; x < gridWidth; x++) {
                    magnitudes[y][x] = Math.hypot(map.dxy[y][x][0], map.dxy[y][x][1]);
                }
            }
            allMagnitudes.add(magnitudes);
        }

        var gridHeight = allMagnitudes.getFirst().length;
        var gridWidth = allMagnitudes.getFirst()[0].length;
        var avgMagnitudes = new double[gridHeight][gridWidth];
        for (var magnitudes : allMagnitudes) {
            for (var y = 0; y < gridHeight; y++) {
                for (var x = 0; x < gridWidth; x++) {
                    avgMagnitudes[y][x] += magnitudes[y][x];
                }
            }
        }
        for (var y = 0; y < gridHeight; y++) {
            for (var x = 0; x < gridWidth; x++) {
                avgMagnitudes[y][x] /= allMagnitudes.size();
            }
        }

        var paddedMagnitudes = FFTSupport.pad(avgMagnitudes, gridWidth, gridHeight);
        var fft = FFTSupport.fft2(paddedMagnitudes);

        var powerSpectrum = new double[fft.real.length][fft.real[0].length];
        for (var y = 0; y < fft.real.length; y++) {
            for (var x = 0; x < fft.real[0].length; x++) {
                powerSpectrum[y][x] = fft.real[y][x] * fft.real[y][x] + fft.imaginary[y][x] * fft.imaginary[y][x];
            }
        }

        var centerY = powerSpectrum.length / 2;
        var centerX = powerSpectrum[0].length / 2;
        powerSpectrum[centerY][centerX] = 0;

        var maxPower = 0.0;
        var maxFreqY = 0;
        var maxFreqX = 0;

        var searchRadius = Math.min(centerY, centerX) / 2;
        for (var dy = -searchRadius; dy <= searchRadius; dy++) {
            for (var dx = -searchRadius; dx <= searchRadius; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                var y = centerY + dy;
                var x = centerX + dx;
                if (y >= 0 && y < powerSpectrum.length && x >= 0 && x < powerSpectrum[0].length) {
                    if (powerSpectrum[y][x] > maxPower) {
                        maxPower = powerSpectrum[y][x];
                        maxFreqY = dy;
                        maxFreqX = dx;
                    }
                }
            }
        }

        var dominantFrequency = Math.hypot(maxFreqX, maxFreqY);
        if (dominantFrequency > 0) {
            var wavelengthInSamples = powerSpectrum[0].length / dominantFrequency;
            var wavelengthInPixels = wavelengthInSamples * step;
            return (int) Math.max(32, Math.min(256, wavelengthInPixels));
        } else {
            return 64;
        }
    }

    public double interpolateTileError(int x, int y, int tileSize) {
        ensureTileErrorsComputed(tileSize);

        var tx = (double) x / tileSize;
        var ty = (double) y / tileSize;

        var tx0 = (int) Math.floor(tx);
        var ty0 = (int) Math.floor(ty);
        var tx1 = Math.min(tx0 + 1, cachedTileErrors[0].length - 1);
        var ty1 = Math.min(ty0 + 1, cachedTileErrors.length - 1);

        tx0 = Math.max(0, Math.min(tx0, cachedTileErrors[0].length - 1));
        ty0 = Math.max(0, Math.min(ty0, cachedTileErrors.length - 1));

        var dx = tx - tx0;
        var dy = ty - ty0;

        var e00 = cachedTileErrors[ty0][tx0];
        var e10 = cachedTileErrors[ty0][tx1];
        var e01 = cachedTileErrors[ty1][tx0];
        var e11 = cachedTileErrors[ty1][tx1];

        var e0 = e00 * (1 - dx) + e10 * dx;
        var e1 = e01 * (1 - dx) + e11 * dx;

        return e0 * (1 - dy) + e1 * dy;
    }

    public static void saveTo(OutputStream out, DistorsionMap map) throws IOException {
        try (var channel = Channels.newChannel(out)) {
            var rows = map.dxy.length;
            var cols = map.dxy[0].length;
            var sampledBytes = (rows * cols + 7) / 8;

            var buffer = ByteBuffer.allocate(
                Integer.BYTES * 5 + // header (version + step + tileSize + rows + cols)
                Double.BYTES * rows * cols * 2 + // dxy data
                sampledBytes // sampled bitmap
            );

            // Write metadata with version
            buffer.putInt(FORMAT_VERSION);
            buffer.putInt(map.step).putInt(map.tileSize).putInt(rows).putInt(cols);

            // Write dxy data
            for (double[][] row : map.dxy) {
                for (double[] xy : row) {
                    buffer.putDouble(xy[0]).putDouble(xy[1]);
                }
            }

            // Write sampled array as packed bits
            var bitIndex = 0;
            var currentByte = 0;
            for (var y = 0; y < rows; y++) {
                for (var x = 0; x < cols; x++) {
                    if (map.sampled[y][x]) {
                        currentByte |= (1 << (bitIndex % 8));
                    }
                    bitIndex++;
                    if (bitIndex % 8 == 0) {
                        buffer.put((byte) currentByte);
                        currentByte = 0;
                    }
                }
            }
            if (bitIndex % 8 != 0) {
                buffer.put((byte) currentByte);
            }

            buffer.flip();
            channel.write(buffer);
        }
    }

    public static DistorsionMap loadFrom(InputStream in) {
        try (var channel = Channels.newChannel(in)) {
            var headerBuffer = ByteBuffer.allocate(Integer.BYTES * 5);

            // Read first 5 integers to determine format
            channel.read(headerBuffer);
            headerBuffer.flip();
            var firstInt = headerBuffer.getInt();

            int version;
            int step;
            int tileSize;
            int rows;
            int cols;

            if (firstInt == FORMAT_VERSION) {
                // New format (version 2+)
                version = firstInt;
                step = headerBuffer.getInt();
                tileSize = headerBuffer.getInt();
                rows = headerBuffer.getInt();
                cols = headerBuffer.getInt();
            } else {
                // Old format (version 1, no version marker)
                // firstInt is actually 'step'
                version = 1;
                step = firstInt;
                tileSize = headerBuffer.getInt();
                rows = headerBuffer.getInt();
                cols = headerBuffer.getInt();
                // Note: we read one extra int that belongs to dxy data, need to handle this
            }

            // Read dxy data
            var dxy = new double[rows][cols][2];
            var dataBuffer = ByteBuffer.allocate(Double.BYTES * rows * cols * 2);

            if (version == 1) {
                // For old format, we already consumed 5 ints but only needed 4
                // The 5th int is the first 4 bytes of dxy data
                // We need to prepend it to the data buffer
                var fifthInt = headerBuffer.getInt();
                var adjustedBuffer = ByteBuffer.allocate(Double.BYTES * rows * cols * 2 - Integer.BYTES);
                channel.read(adjustedBuffer);
                adjustedBuffer.flip();

                // Reconstruct the first double from fifthInt + next 4 bytes
                var firstDoubleBuffer = ByteBuffer.allocate(Double.BYTES);
                firstDoubleBuffer.putInt(fifthInt);
                firstDoubleBuffer.putInt(adjustedBuffer.getInt());
                firstDoubleBuffer.flip();
                dxy[0][0][0] = firstDoubleBuffer.getDouble();

                // Read the rest
                var remaining = rows * cols * 2 - 1;
                for (var i = 0; i < remaining; i++) {
                    var row = (i + 1) / (cols * 2);
                    var colIdx = (i + 1) % (cols * 2);
                    var col = colIdx / 2;
                    var component = colIdx % 2;
                    dxy[row][col][component] = adjustedBuffer.getDouble();
                }
            } else {
                channel.read(dataBuffer);
                dataBuffer.flip();
                for (var i = 0; i < rows; i++) {
                    for (var j = 0; j < cols; j++) {
                        dxy[i][j][0] = dataBuffer.getDouble();
                        dxy[i][j][1] = dataBuffer.getDouble();
                    }
                }
            }

            // Read or create sampled array
            boolean[][] sampled;
            if (version >= 2) {
                // Read sampled bitmap
                var sampledBytes = (rows * cols + 7) / 8;
                var sampledBuffer = ByteBuffer.allocate(sampledBytes);
                channel.read(sampledBuffer);
                sampledBuffer.flip();

                sampled = new boolean[rows][cols];
                var bitIndex = 0;
                for (var y = 0; y < rows; y++) {
                    for (var x = 0; x < cols; x++) {
                        var byteIndex = bitIndex / 8;
                        var bitOffset = bitIndex % 8;
                        var b = sampledBuffer.get(byteIndex);
                        sampled[y][x] = ((b >> bitOffset) & 1) == 1;
                        bitIndex++;
                    }
                }
                return new DistorsionMap(step, tileSize, dxy, sampled);
            } else {
                // Old format: assume all points are sampled
                return createWithAllSampled(step, tileSize, dxy);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load distorsion map", e);
        }
    }

    public void saveTo(OutputStream outputStream) throws IOException {
        saveTo(outputStream, this);
    }

    @Override
    public String toString() {
        return "Distorsion map (" + dxy[0].length + "x" + dxy.length + ")";
    }

    /**
     * Applies outlier rejection and Gaussian smoothing to the displacement grid.
     * Should be called after all displacements are recorded, before queries.
     * Parameters are scaled based on step size to maintain consistent physical coverage.
     * Uses GPU acceleration when available for large grids.
     */
    public void filterAndSmooth() {
        var gridWidth = dxy[0].length;
        var gridHeight = dxy.length;

        // Log statistics before filtering
        double beforeTotalDist = computeTotalDistorsion();
        double beforeMaxDx = 0, beforeMaxDy = 0;
        for (var row : dxy) {
            for (var d : row) {
                beforeMaxDx = Math.max(beforeMaxDx, Math.abs(d[0]));
                beforeMaxDy = Math.max(beforeMaxDy, Math.abs(d[1]));
            }
        }
        LOGGER.debug("filterAndSmooth: gridSize={}x{}, tileSize={}, step={}, BEFORE: totalDist={}, maxDx={}, maxDy={}",
                gridWidth, gridHeight, tileSize, step,
                String.format("%.4f", beforeTotalDist),
                String.format("%.4f", beforeMaxDx),
                String.format("%.4f", beforeMaxDy));

        var scaleFactor = REFERENCE_STEP_FOR_SCALING / step;

        var scaledWindowSize = (int) Math.round(BASE_OUTLIER_WINDOW_SIZE * scaleFactor);
        if (scaledWindowSize % 2 == 0) {
            scaledWindowSize++;
        }
        scaledWindowSize = Math.max(MIN_OUTLIER_WINDOW_SIZE, Math.min(scaledWindowSize, MAX_OUTLIER_WINDOW_SIZE));
        var halfWindow = scaledWindowSize / 2;

        var scaledSigma = Math.max(MIN_SMOOTHING_SIGMA, scaleFactor);
        LOGGER.debug("Smoothing params: scaleFactor={}, windowSize={}, sigma={}", scaleFactor, scaledWindowSize, scaledSigma);

        // Try GPU-accelerated path for large grids
        var openclContext = OpenCLSupport.getContext();
        if (openclContext != null && OpenCLSupport.isEnabled() && gridWidth * gridHeight >= GPU_GRID_SIZE_THRESHOLD) {
            try {
                filterAndSmoothGPU(gridWidth, gridHeight, halfWindow, (float) scaledSigma);
                totalDistorsion = -1;

                // Log statistics after GPU filtering
                double afterMaxDx = 0, afterMaxDy = 0;
                for (var row : dxy) {
                    for (var d : row) {
                        afterMaxDx = Math.max(afterMaxDx, Math.abs(d[0]));
                        afterMaxDy = Math.max(afterMaxDy, Math.abs(d[1]));
                    }
                }
                LOGGER.debug("filterAndSmooth AFTER (GPU): totalDist={}, maxDx={}, maxDy={}",
                        String.format("%.4f", totalDistorsion()),
                        String.format("%.4f", afterMaxDx),
                        String.format("%.4f", afterMaxDy));
                return;
            } catch (Exception e) {
                System.err.println("[DistorsionMap] GPU filtering failed, falling back to CPU");
                e.printStackTrace();
                openclContext.recordError("DistorsionMap.filterAndSmooth", e);
            }
        }

        // CPU fallback
        interpolateUnsampledPoints();
        LOGGER.debug("After interpolation: sampledPoints={}", countSampledPoints());

        var rejected = removeOutliers(MAD_THRESHOLD, scaledWindowSize);
        var rejectedCount = countRejected(rejected);
        LOGGER.debug("After outlier removal: totalDistortion={}, rejectedSamples={}",
                computeTotalDistorsion(), rejectedCount);

        if (LOGGER.isDebugEnabled() && rejectedCount > 0) {
            saveDebugImage(rejected, rejectedCount);
        }

        applyGaussianSmoothing(scaledSigma);
        totalDistorsion = -1;

        // Log statistics after filtering
        double afterMaxDx = 0, afterMaxDy = 0;
        for (var row : dxy) {
            for (var d : row) {
                afterMaxDx = Math.max(afterMaxDx, Math.abs(d[0]));
                afterMaxDy = Math.max(afterMaxDy, Math.abs(d[1]));
            }
        }
        LOGGER.debug("filterAndSmooth AFTER: totalDist={}, maxDx={}, maxDy={}",
                String.format("%.4f", totalDistorsion()),
                String.format("%.4f", afterMaxDx),
                String.format("%.4f", afterMaxDy));
    }

    /**
     * GPU-accelerated filtering using DistortionGridFilter with fused pipeline.
     */
    private void filterAndSmoothGPU(int gridWidth, int gridHeight, int halfWindow, float sigma) {
        var gridFilter = DistortionGridFilter.getInstance();

        var gridDx = getGridDx();
        var gridDy = getGridDy();

        gridFilter.filterAndSmoothFused(gridDx, gridDy, sampled, gridWidth, gridHeight,
            SEARCH_RADIUS, halfWindow, (float) MAD_THRESHOLD, sigma);

        for (var y = 0; y < gridHeight; y++) {
            for (var x = 0; x < gridWidth; x++) {
                dxy[y][x][0] = gridDx[y][x];
                dxy[y][x][1] = gridDy[y][x];
            }
        }
    }

    private int countSampledPoints() {
        var count = 0;
        for (var row : sampled) {
            for (var s : row) {
                if (s) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countRejected(boolean[][] rejected) {
        var count = 0;
        for (var row : rejected) {
            for (var r : row) {
                if (r) {
                    count++;
                }
            }
        }
        return count;
    }

    private void saveDebugImage(boolean[][] rejected, int rejectedCount) {
        try {
            var debugData = createDebugImage(rejected);
            var imgHeight = debugData.length;
            var imgWidth = debugData[0].length;

            var image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
            for (var y = 0; y < imgHeight; y++) {
                for (var x = 0; x < imgWidth; x++) {
                    image.setRGB(x, y, debugData[y][x]);
                }
            }

            var counter = DEBUG_IMAGE_COUNTER.incrementAndGet();
            var debugFile = new File(System.getProperty("java.io.tmpdir"),
                    String.format("dedistort/distorsion_map_debug_%03d.png", counter));
            debugFile.getParentFile().mkdirs();
            ImageIO.write(image, "png", debugFile);
            LOGGER.debug("Saved distortion map debug image: {} ({}x{}, {} rejected samples)",
                    debugFile.getAbsolutePath(), imgWidth, imgHeight, rejectedCount);
        } catch (IOException e) {
            LOGGER.warn("Failed to save distortion map debug image", e);
        }
    }

    private void interpolateUnsampledPoints() {
        var rows = dxy.length;
        var cols = dxy[0].length;

        for (var sy = 0; sy < rows; sy++) {
            for (var sx = 0; sx < cols; sx++) {
                if (sampled[sy][sx]) {
                    continue;
                }
                var sumDx = 0.0;
                var sumDy = 0.0;
                var weightSum = 0.0;
                for (var dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                    var ny = sy + dy;
                    if (ny < 0 || ny >= rows) {
                        continue;
                    }
                    for (var dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        var nx = sx + dx;
                        if (nx >= 0 && nx < cols && sampled[ny][nx]) {
                            var weight = INVERSE_DIST_SQ_LUT[dy + SEARCH_RADIUS][dx + SEARCH_RADIUS];
                            sumDx += dxy[ny][nx][0] * weight;
                            sumDy += dxy[ny][nx][1] * weight;
                            weightSum += weight;
                        }
                    }
                }
                if (weightSum > 0) {
                    dxy[sy][sx][0] = sumDx / weightSum;
                    dxy[sy][sx][1] = sumDy / weightSum;
                }
            }
        }
    }

    public int getStep() {
        return step;
    }

    public int getTileSize() {
        return tileSize;
    }

    public int getGridWidth() {
        return gridXSize;
    }

    public int getGridHeight() {
        return gridYSize;
    }

    public float[][] getGridDx() {
        var result = new float[gridYSize][gridXSize];
        for (var y = 0; y < gridYSize; y++) {
            for (var x = 0; x < gridXSize; x++) {
                result[y][x] = (float) dxy[y][x][0];
            }
        }
        return result;
    }

    public float[][] getGridDy() {
        var result = new float[gridYSize][gridXSize];
        for (var y = 0; y < gridYSize; y++) {
            for (var x = 0; x < gridXSize; x++) {
                result[y][x] = (float) dxy[y][x][1];
            }
        }
        return result;
    }

    /**
     * Removes outliers and returns a map of rejected samples for debugging.
     *
     * @return boolean array where true indicates the sample was rejected as outlier
     */
    private boolean[][] removeOutliers(double madThreshold, int windowSize) {
        var rows = dxy.length;
        var cols = dxy[0].length;
        var halfWindow = windowSize / 2;
        var maxNeighbors = windowSize * windowSize - 1;
        var neighborBuffer = new double[maxNeighbors];
        var rejected = new boolean[rows][cols];

        for (var component = 0; component < 2; component++) {
            var values = new double[rows * cols];
            for (var sy = 0; sy < rows; sy++) {
                for (var sx = 0; sx < cols; sx++) {
                    values[sy * cols + sx] = dxy[sy][sx][component];
                }
            }

            var globalMedian = MathUtils.median(values);
            var globalMAD = computeMAD(values, globalMedian);
            var globalThreshold = madThreshold * 1.4826 * globalMAD;

            var filtered = new double[rows][cols];
            for (var sy = 0; sy < rows; sy++) {
                for (var sx = 0; sx < cols; sx++) {
                    var current = dxy[sy][sx][component];
                    var neighborCount = collectNeighborsInto(sx, sy, halfWindow, component, neighborBuffer);
                    if (neighborCount == 0) {
                        filtered[sy][sx] = current;
                        continue;
                    }
                    var neighbors = Arrays.copyOf(neighborBuffer, neighborCount);
                    var localMedian = MathUtils.median(neighbors);
                    var localMAD = computeMAD(neighbors, localMedian);
                    var localThreshold = madThreshold * 1.4826 * Math.max(localMAD, 0.1);

                    var isLocalOutlier = Math.abs(current - localMedian) > localThreshold;
                    var isGlobalOutlier = Math.abs(current - globalMedian) > globalThreshold;

                    if (isLocalOutlier || isGlobalOutlier) {
                        filtered[sy][sx] = localMedian;
                        rejected[sy][sx] = true;
                    } else {
                        filtered[sy][sx] = current;
                    }
                }
            }

            for (var sy = 0; sy < rows; sy++) {
                for (var sx = 0; sx < cols; sx++) {
                    dxy[sy][sx][component] = filtered[sy][sx];
                }
            }
        }

        return rejected;
    }

    /**
     * Creates a debug image showing the distortion map with rejected samples highlighted.
     * Uses colors: blue gradient for magnitude, red for rejected samples, cyan for arrows.
     *
     * @param rejected boolean array marking rejected samples
     * @return RGB image data as packed integers (height × width)
     */
    private int[][] createDebugImage(boolean[][] rejected) {
        var rows = dxy.length;
        var cols = dxy[0].length;

        // Scale up for visibility: each grid cell becomes a small block
        var scale = 8;
        var imgHeight = rows * scale;
        var imgWidth = cols * scale;
        var image = new int[imgHeight][imgWidth];

        // Find max magnitude for normalization
        var maxMag = 0.0;
        for (var row : dxy) {
            for (var d : row) {
                maxMag = Math.max(maxMag, Math.hypot(d[0], d[1]));
            }
        }
        if (maxMag < 1e-10) {
            maxMag = 1.0;
        }

        // Draw distortion magnitude with color gradient (dark blue to bright yellow)
        for (var gy = 0; gy < rows; gy++) {
            for (var gx = 0; gx < cols; gx++) {
                var mag = Math.hypot(dxy[gy][gx][0], dxy[gy][gx][1]);
                var t = mag / maxMag;

                // Color gradient: dark blue (low) -> cyan -> yellow (high)
                int r, g, b;
                if (t < 0.5) {
                    // Dark blue to cyan
                    var s = t * 2;
                    r = (int) (20 * s);
                    g = (int) (40 + 180 * s);
                    b = (int) (80 + 100 * s);
                } else {
                    // Cyan to yellow
                    var s = (t - 0.5) * 2;
                    r = (int) (20 + 235 * s);
                    g = (int) (220 + 35 * s);
                    b = (int) (180 - 150 * s);
                }
                var bgColor = (r << 16) | (g << 8) | b;

                // Fill the block with background color
                for (var dy = 0; dy < scale; dy++) {
                    for (var dx = 0; dx < scale; dx++) {
                        var py = gy * scale + dy;
                        var px = gx * scale + dx;
                        if (py < imgHeight && px < imgWidth) {
                            image[py][px] = bgColor;
                        }
                    }
                }

                // Draw grid lines (subtle dark)
                for (var d = 0; d < scale; d++) {
                    var py = gy * scale;
                    var px = gx * scale + d;
                    if (py < imgHeight && px < imgWidth) {
                        image[py][px] = darken(image[py][px], 0.7);
                    }
                    py = gy * scale + d;
                    px = gx * scale;
                    if (py < imgHeight && px < imgWidth) {
                        image[py][px] = darken(image[py][px], 0.7);
                    }
                }

                // Draw rejected samples with bright red cross
                if (rejected != null && rejected[gy][gx]) {
                    var cy = gy * scale + scale / 2;
                    var cx = gx * scale + scale / 2;
                    var red = 0xFF3030;
                    for (var d = -scale / 3; d <= scale / 3; d++) {
                        if (cy + d >= 0 && cy + d < imgHeight && cx >= 0 && cx < imgWidth) {
                            image[cy + d][cx] = red;
                        }
                        if (cy >= 0 && cy < imgHeight && cx + d >= 0 && cx + d < imgWidth) {
                            image[cy][cx + d] = red;
                        }
                    }
                }

                // Draw direction arrow for non-rejected samples with significant displacement
                if ((rejected == null || !rejected[gy][gx]) && mag > maxMag * 0.1) {
                    var ddx = dxy[gy][gx][0];
                    var ddy = dxy[gy][gx][1];
                    var arrowLen = scale / 2.0;
                    var nx = ddx / mag;
                    var ny = ddy / mag;

                    var cy = gy * scale + scale / 2;
                    var cx = gx * scale + scale / 2;

                    // Draw arrow line in white
                    var white = 0xFFFFFF;
                    for (var tt = 0.0; tt <= arrowLen; tt += 0.5) {
                        var px = (int) (cx + nx * tt);
                        var py = (int) (cy + ny * tt);
                        if (py >= 0 && py < imgHeight && px >= 0 && px < imgWidth) {
                            image[py][px] = white;
                        }
                    }
                }
            }
        }

        return image;
    }

    private static int darken(int rgb, double factor) {
        var r = (int) (((rgb >> 16) & 0xFF) * factor);
        var g = (int) (((rgb >> 8) & 0xFF) * factor);
        var b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Collects neighbor values in a single pass using a pre-sized buffer.
     * @return actual count of collected neighbors
     */
    private int collectNeighborsInto(int sx, int sy, int halfWindow, int component, double[] buffer) {
        var rows = dxy.length;
        var cols = dxy[0].length;
        var idx = 0;
        for (var ny = sy - halfWindow; ny <= sy + halfWindow; ny++) {
            if (ny < 0 || ny >= rows) {
                continue;
            }
            for (var nx = sx - halfWindow; nx <= sx + halfWindow; nx++) {
                if (nx >= 0 && nx < cols && !(ny == sy && nx == sx)) {
                    buffer[idx++] = dxy[ny][nx][component];
                }
            }
        }
        return idx;
    }

    private double computeMAD(double[] values, double median) {
        if (values.length == 0) {
            return 0;
        }
        var deviations = new double[values.length];
        for (var i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return MathUtils.median(deviations);
    }

    private void applyGaussianSmoothing(double sigma) {
        var kernelRadius = (int) Math.ceil(3 * sigma);
        var kernelSize = 2 * kernelRadius + 1;
        var kernel = create1DGaussianKernel(kernelSize, sigma);
        var rows = dxy.length;
        var cols = dxy[0].length;

        for (var component = 0; component < 2; component++) {
            var grid = new double[rows][cols];
            for (var sy = 0; sy < rows; sy++) {
                for (var sx = 0; sx < cols; sx++) {
                    grid[sy][sx] = dxy[sy][sx][component];
                }
            }

            var temp = new double[rows][cols];
            for (var sy = 0; sy < rows; sy++) {
                for (var sx = 0; sx < cols; sx++) {
                    var sum = 0.0;
                    var weightSum = 0.0;
                    for (var k = -kernelRadius; k <= kernelRadius; k++) {
                        var nx = sx + k;
                        if (nx >= 0 && nx < cols) {
                            var w = kernel[k + kernelRadius];
                            sum += grid[sy][nx] * w;
                            weightSum += w;
                        }
                    }
                    temp[sy][sx] = sum / weightSum;
                }
            }

            for (var sy = 0; sy < rows; sy++) {
                for (var sx = 0; sx < cols; sx++) {
                    var sum = 0.0;
                    var weightSum = 0.0;
                    for (var k = -kernelRadius; k <= kernelRadius; k++) {
                        var ny = sy + k;
                        if (ny >= 0 && ny < rows) {
                            var w = kernel[k + kernelRadius];
                            sum += temp[ny][sx] * w;
                            weightSum += w;
                        }
                    }
                    dxy[sy][sx][component] = sum / weightSum;
                }
            }
        }
    }

    private static double[] create1DGaussianKernel(int size, double sigma) {
        var kernel = new double[size];
        var center = size / 2;
        var sum = 0.0;
        for (var i = 0; i < size; i++) {
            var x = i - center;
            kernel[i] = Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }
        for (var i = 0; i < size; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }
}
