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

import org.apache.commons.math3.analysis.interpolation.BicubicInterpolatingFunction;
import org.apache.commons.math3.analysis.interpolation.BicubicInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

/**
 * Represents a distorsion map, which is a map of deltas in the x and y axis.
 * The map is created using a grid of samples, and then interpolated using bicubic interpolation.
 */
public class DistorsionMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistorsionMap.class);

    private final int step;
    private final int tileSize;
    private final double[][][] dxy;
    private BicubicInterpolatingFunction dxModel, dyModel;

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
        if (dxModel != null || dyModel != null) {
            throw new IllegalStateException("Cannot record distorsion after interpolators have been computed");
        }
        var offset = tileSize / 2;
        var sampleX = (x - offset) / step;
        var sampleY = (y - offset) / step;
        dxy[sampleY][sampleX][0] = dx;
        dxy[sampleY][sampleX][1] = dy;
    }

    public void computeInterpolators() {
        dxModel = interpolate(0);
        dyModel = interpolate(1);
        if (LOGGER.isDebugEnabled()) {
            double sum = 0;
            double count = 0;
            for (int yy = 0; yy < dxy.length; yy++) {
                for (int xx = 0; xx < dxy[yy].length; xx++) {
                    var dx = dxy[yy][xx][1];
                    var dy = dxy[yy][xx][1];
                    var estimateX = dxModel.value(yy, xx);
                    var estimateY = dyModel.value(yy, xx);
                    if (Double.isFinite(estimateX) && Double.isFinite(estimateY)) {
                        sum += Math.pow(estimateX - dx, 2) + Math.pow(estimateY - dy, 2);
                        count++;
                    }
                }
            }
            LOGGER.debug("Error: {}", Math.sqrt(sum / count));
        }
    }

    private BicubicInterpolatingFunction interpolate(int idx) {
        var interpolator = new BicubicInterpolator();

        // Original grid dimensions
        int gridXSize = dxy[0].length;
        int gridYSize = dxy.length;

        // Extended grid to include all angles
        double[] xGrid = new double[gridXSize];
        double[] yGrid = new double[gridYSize];
        double[][] values = new double[gridYSize][gridXSize];

        // Populate xGrid and yGrid
        for (int i = 0; i < gridXSize; i++) {
            xGrid[i] = i;
        }
        for (int j = 0; j < gridYSize; j++) {
            yGrid[j] = j;
        }

        // Fill values grid, handling inner and boundary points
        for (int y = 0; y < gridYSize; y++) {
            for (int x = 0; x < gridXSize; x++) {
                values[y][x] = dxy[y][x][idx];
            }
        }

        // Build and return the spline interpolator
        return interpolator.interpolate(xGrid, yGrid, values);
    }

    public DeltaXY findDistorsion(int x, int y) {
        if (dxModel == null || dyModel == null) {
            computeInterpolators();
        }
        var ax = ((double) x) / step;
        var ay = ((double) y) / step;
        if (dxModel.isValidPoint(ay, ax) && dyModel.isValidPoint(ay, ax)) {
            return new DeltaXY(dxModel.value(ay, ax), dyModel.value(ay, ax));
        }
        return new DeltaXY(0, 0);
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
