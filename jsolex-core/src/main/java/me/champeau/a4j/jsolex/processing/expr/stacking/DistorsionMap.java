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
package me.champeau.a4j.jsolex.processing.expr.stacking;

import me.champeau.a4j.math.fft.FFTSupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a distorsion map, which is a map of deltas in the x and y axis.
 * The map is created using a grid of samples, and then interpolated using local bicubic interpolation.
 */
public class DistorsionMap {
    private static final int LUT_SIZE = 256;
    private static final double[] CUBIC_WEIGHT_LUT = precomputeCubicWeights();

    private final int step;
    private final int tileSize;
    private final double[][][] dxy;
    private double totalDistorsion = -1;
    private final Lock cacheLock = new ReentrantLock();
    private double[][] cachedTileErrors;
    private int estimatedTurbulenceScale = -1;

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
        this.dxy = new double[xSamples][ySamples][2];
    }

    private DistorsionMap(int step, int tileSize, double[][][] dxy) {
        this.step = step;
        this.tileSize = tileSize;
        this.dxy = dxy;
    }

    public void recordDistorsion(int x, int y, double dx, double dy) {
        var offset = tileSize / 2;
        var sampleX = (x - offset) / step;
        var sampleY = (y - offset) / step;
        dxy[sampleY][sampleX][0] = dx;
        dxy[sampleY][sampleX][1] = dy;
    }

    public DeltaXY findDistorsion(int x, int y) {
        var ax = ((double) x) / step;
        var ay = ((double) y) / step;

        var gridXSize = dxy[0].length;
        var gridYSize = dxy.length;

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

        var gridXSize = dxy[0].length;
        var gridYSize = dxy.length;

        var p = new double[4][4];
        for (var i = 0; i < 4; i++) {
            for (var j = 0; j < 4; j++) {
                var xi = Math.min(Math.max(x0 - 1 + j, 0), gridXSize - 1);
                var yi = Math.min(Math.max(y0 - 1 + i, 0), gridYSize - 1);
                p[i][j] = dxy[yi][xi][component];
            }
        }

        var dx = x - x0;
        var dy = y - y0;

        var dxIdx = (int) (dx * (LUT_SIZE - 1));
        var dyIdx = (int) (dy * (LUT_SIZE - 1));

        var result = 0.0;
        for (var i = 0; i < 4; i++) {
            var wy = CUBIC_WEIGHT_LUT[dyIdx * 4 + i];
            for (var j = 0; j < 4; j++) {
                var wx = CUBIC_WEIGHT_LUT[dxIdx * 4 + j];
                result += p[i][j] * wx * wy;
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

            var buffer = ByteBuffer.allocate(
                Integer.BYTES * 4 + // header
                Double.BYTES * rows * cols * 2 // data
            );

            // Write metadata
            buffer.putInt(map.step).putInt(map.tileSize).putInt(rows).putInt(cols);

            // Write data rows
            for (double[][] row : map.dxy) {
                for (double[] xy : row) {
                    buffer.putDouble(xy[0]).putDouble(xy[1]);
                }
            }
            buffer.flip();
            channel.write(buffer);
        }
    }

    public static DistorsionMap loadFrom(InputStream in) {
        try (var channel = Channels.newChannel(in)) {
            var buffer = ByteBuffer.allocate(Integer.BYTES * 4);

            // Read metadata
            channel.read(buffer);
            buffer.flip();
            var step = buffer.getInt();
            var tileSize = buffer.getInt();
            var rows = buffer.getInt();
            var cols = buffer.getInt();
            buffer.clear();

            // read data
            var dxy = new double[rows][cols][2];
            buffer = ByteBuffer.allocate(Double.BYTES * rows * cols * 2);

            channel.read(buffer);
            buffer.flip();
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    dxy[i][j][0] = buffer.getDouble();
                    dxy[i][j][1] = buffer.getDouble();
                }
            }
            return new DistorsionMap(step, tileSize, dxy);
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
}
