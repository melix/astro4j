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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

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

    public void computeInterpolators() {
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
