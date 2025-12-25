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

import me.champeau.a4j.math.spatial.KDTree2D;

import java.util.Arrays;

/**
 * A sparse distortion field that stores displacement samples at arbitrary positions
 * and uses RBF (Radial Basis Function) interpolation for querying.
 * <p>
 * Unlike {@link DistorsionMap} which uses a regular grid, this class can store
 * samples at non-uniform positions, making it suitable for interest-point-based
 * distortion estimation where samples are concentrated in areas with high detail.
 * </p>
 * <p>
 * Interpolation is performed using a local RBF approach with k-nearest neighbors,
 * which provides smooth results while maintaining O(log n) query complexity.
 * </p>
 */
public class SparseDistortionField {

    /**
     * Interpolation methods for querying displacement at arbitrary positions.
     */
    public enum InterpolationMethod {
        /**
         * Inverse Distance Weighting: weight = 1 / distance^power.
         * Simple and fast, but can produce "bull's eye" artifacts.
         */
        IDW,

        /**
         * Gaussian RBF: φ(r) = exp(-(εr)²).
         * Smooth interpolation with controllable shape parameter.
         */
        RBF_GAUSSIAN,

        /**
         * Thin-plate spline RBF: φ(r) = r² log(r).
         * C¹ continuous, good for deformation fields.
         */
        RBF_THIN_PLATE
    }

    private static final int DEFAULT_NEIGHBORS_K = 8;
    private static final double DEFAULT_RBF_EPSILON = 0.01;
    private static final double DEFAULT_IDW_POWER = 2.0;

    private final float[] sampleX;
    private final float[] sampleY;
    private final float[] sampleDx;
    private final float[] sampleDy;
    private final int[] sampleTileSize;
    private final int sampleCount;

    private final KDTree2D spatialIndex;

    private final int width;
    private final int height;

    private final int neighborsK;
    private final double rbfEpsilon;
    private final double idwPower;
    private final InterpolationMethod method;
    private final int baseTileSize;
    private final boolean useTileWeighting;

    private SparseDistortionField(float[] sampleX,
                                  float[] sampleY,
                                  float[] sampleDx,
                                  float[] sampleDy,
                                  int[] sampleTileSize,
                                  int sampleCount,
                                  KDTree2D spatialIndex,
                                  int width,
                                  int height,
                                  int neighborsK,
                                  double rbfEpsilon,
                                  double idwPower,
                                  InterpolationMethod method,
                                  int baseTileSize,
                                  boolean useTileWeighting) {
        this.sampleX = sampleX;
        this.sampleY = sampleY;
        this.sampleDx = sampleDx;
        this.sampleDy = sampleDy;
        this.sampleTileSize = sampleTileSize;
        this.sampleCount = sampleCount;
        this.spatialIndex = spatialIndex;
        this.width = width;
        this.height = height;
        this.neighborsK = neighborsK;
        this.rbfEpsilon = rbfEpsilon;
        this.idwPower = idwPower;
        this.method = method;
        this.baseTileSize = baseTileSize;
        this.useTileWeighting = useTileWeighting;
    }

    /**
     * Creates a builder for constructing a SparseDistortionField.
     *
     * @param width  image width
     * @param height image height
     * @return a new builder
     */
    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    /**
     * Returns the number of samples in the field.
     *
     * @return sample count
     */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * Returns the image width.
     *
     * @return width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the image height.
     *
     * @return height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Queries the displacement at the given position using RBF interpolation.
     *
     * @param px x coordinate
     * @param py y coordinate
     * @return the interpolated displacement
     */
    public DeltaXY query(float px, float py) {
        if (sampleCount == 0) {
            return new DeltaXY(0, 0);
        }

        var neighbors = spatialIndex.nearestK(px, py, neighborsK);
        if (neighbors.length == 0) {
            return new DeltaXY(0, 0);
        }

        return switch (method) {
            case IDW -> interpolateIDW(px, py, neighbors);
            case RBF_GAUSSIAN -> interpolateGaussianRBF(px, py, neighbors);
            case RBF_THIN_PLATE -> interpolateThinPlateRBF(px, py, neighbors);
        };
    }

    private DeltaXY interpolateIDW(float px, float py, int[] neighbors) {
        double weightSum = 0;
        double interpDx = 0;
        double interpDy = 0;

        for (var idx : neighbors) {
            double dx = px - sampleX[idx];
            double dy = py - sampleY[idx];
            var distSq = dx * dx + dy * dy;

            if (distSq < 1e-10) {
                return new DeltaXY(sampleDx[idx], sampleDy[idx]);
            }

            var dist = Math.sqrt(distSq);
            var weight = 1.0 / Math.pow(dist, idwPower);

            weightSum += weight;
            interpDx += weight * sampleDx[idx];
            interpDy += weight * sampleDy[idx];
        }

        if (weightSum < 1e-10) {
            return new DeltaXY(0, 0);
        }

        return new DeltaXY(interpDx / weightSum, interpDy / weightSum);
    }

    private DeltaXY interpolateGaussianRBF(float px, float py, int[] neighbors) {
        double weightSum = 0;
        double interpDx = 0;
        double interpDy = 0;

        for (var idx : neighbors) {
            double dx = px - sampleX[idx];
            double dy = py - sampleY[idx];
            var distSq = dx * dx + dy * dy;

            var scaledEpsilon = rbfEpsilon;
            if (useTileWeighting) {
                // Scale epsilon based on tile size: larger tiles have wider influence
                // A tile twice as large should have influence over twice the distance
                var tileSize = sampleTileSize[idx];
                var tileSizeRatio = (double) tileSize / baseTileSize;
                scaledEpsilon = rbfEpsilon / tileSizeRatio;
            }

            var phi = Math.exp(-scaledEpsilon * scaledEpsilon * distSq);

            weightSum += phi;
            interpDx += phi * sampleDx[idx];
            interpDy += phi * sampleDy[idx];
        }

        if (weightSum < 1e-10) {
            return new DeltaXY(0, 0);
        }

        return new DeltaXY(interpDx / weightSum, interpDy / weightSum);
    }

    private DeltaXY interpolateThinPlateRBF(float px, float py, int[] neighbors) {
        double weightSum = 0;
        double interpDx = 0;
        double interpDy = 0;

        for (var idx : neighbors) {
            double dx = px - sampleX[idx];
            double dy = py - sampleY[idx];
            var distSq = dx * dx + dy * dy;

            double phi;
            if (distSq < 1e-10) {
                return new DeltaXY(sampleDx[idx], sampleDy[idx]);
            } else {
                phi = distSq * Math.log(Math.sqrt(distSq));
            }

            var weight = 1.0 / (1.0 + Math.abs(phi));

            // Apply tile size weighting if enabled: larger tiles have wider influence
            if (useTileWeighting) {
                var tileSize = sampleTileSize[idx];
                var tileSizeRatio = (double) tileSize / baseTileSize;
                weight *= tileSizeRatio * tileSizeRatio;
            }

            weightSum += weight;
            interpDx += weight * sampleDx[idx];
            interpDy += weight * sampleDy[idx];
        }

        if (weightSum < 1e-10) {
            return new DeltaXY(0, 0);
        }

        return new DeltaXY(interpDx / weightSum, interpDy / weightSum);
    }

    /**
     * Converts this sparse field to a regular DistorsionMap by sampling at grid positions.
     * This is useful for GPU-accelerated warping which expects regular grids.
     *
     * @param step grid step size in pixels
     * @return a regular distortion map
     */
    public DistorsionMap toRegularGrid(int step) {
        var map = new DistorsionMap(width, height, step, step);

        var gridWidth = map.getGridWidth();
        var gridHeight = map.getGridHeight();
        var halfStep = step / 2;

        for (var gy = 0; gy < gridHeight; gy++) {
            var py = gy * step;
            for (var gx = 0; gx < gridWidth; gx++) {
                var px = gx * step;
                var delta = query(px, py);
                map.recordDistorsion(px + halfStep, py + halfStep, delta.dx(), delta.dy());
            }
        }

        map.filterAndSmooth();
        return map;
    }

    /**
     * Computes the total distortion magnitude across all samples.
     *
     * @return sum of displacement magnitudes
     */
    public double totalDistorsion() {
        double total = 0;
        for (var i = 0; i < sampleCount; i++) {
            total += Math.hypot(sampleDx[i], sampleDy[i]);
        }
        return total;
    }

    /**
     * Builder for constructing a SparseDistortionField.
     */
    public static class Builder {
        private final int width;
        private final int height;

        private float[] sampleX;
        private float[] sampleY;
        private float[] sampleDx;
        private float[] sampleDy;
        private int[] sampleTileSize;
        private float[] sampleConfidence;
        private int sampleCount;
        private int capacity;
        private int baseTileSize = 64;

        private int neighborsK = DEFAULT_NEIGHBORS_K;
        private double rbfEpsilon = DEFAULT_RBF_EPSILON;
        private double idwPower = DEFAULT_IDW_POWER;
        private InterpolationMethod method = InterpolationMethod.RBF_GAUSSIAN;
        private boolean useTileWeighting = false;

        private Builder(int width, int height) {
            this.width = width;
            this.height = height;
            this.capacity = 1024;
            this.sampleX = new float[capacity];
            this.sampleY = new float[capacity];
            this.sampleDx = new float[capacity];
            this.sampleDy = new float[capacity];
            this.sampleTileSize = new int[capacity];
            this.sampleConfidence = new float[capacity];
            this.sampleCount = 0;
        }

        /**
         * Adds a displacement sample at the given position with tile size and confidence.
         *
         * @param x          x coordinate
         * @param y          y coordinate
         * @param dx         x displacement
         * @param dy         y displacement
         * @param tileSize   the tile size that produced this sample
         * @param confidence confidence in the displacement estimate (0-1)
         * @return this builder
         */
        public Builder addSample(float x, float y, float dx, float dy, int tileSize, float confidence) {
            ensureCapacity(sampleCount + 1);
            sampleX[sampleCount] = x;
            sampleY[sampleCount] = y;
            sampleDx[sampleCount] = dx;
            sampleDy[sampleCount] = dy;
            sampleTileSize[sampleCount] = tileSize;
            sampleConfidence[sampleCount] = confidence;
            sampleCount++;
            return this;
        }

        /**
         * Adds a displacement sample at the given position with tile size (default confidence 1.0).
         *
         * @param x        x coordinate
         * @param y        y coordinate
         * @param dx       x displacement
         * @param dy       y displacement
         * @param tileSize the tile size that produced this sample
         * @return this builder
         */
        public Builder addSample(float x, float y, float dx, float dy, int tileSize) {
            return addSample(x, y, dx, dy, tileSize, 1.0f);
        }

        /**
         * Sets the base tile size (used as reference for weighting).
         *
         * @param tileSize the base tile size
         * @return this builder
         */
        public Builder baseTileSize(int tileSize) {
            this.baseTileSize = tileSize;
            return this;
        }

        /**
         * Sets the number of neighbors to use for interpolation.
         *
         * @param k neighbor count
         * @return this builder
         */
        public Builder neighborsK(int k) {
            this.neighborsK = k;
            return this;
        }

        /**
         * Sets the RBF shape parameter (epsilon) for Gaussian RBF.
         *
         * @param epsilon shape parameter
         * @return this builder
         */
        public Builder rbfEpsilon(double epsilon) {
            this.rbfEpsilon = epsilon;
            return this;
        }

        /**
         * Sets the power parameter for IDW interpolation.
         *
         * @param power power parameter (typically 2)
         * @return this builder
         */
        public Builder idwPower(double power) {
            this.idwPower = power;
            return this;
        }

        /**
         * Sets the interpolation method.
         *
         * @param method the interpolation method
         * @return this builder
         */
        public Builder interpolationMethod(InterpolationMethod method) {
            this.method = method;
            return this;
        }

        /**
         * Enables or disables tile size weighting.
         * When enabled, larger tiles have more influence on the interpolated result.
         * The weight is proportional to (tileSize / baseTileSize)².
         *
         * @param enabled true to enable tile weighting
         * @return this builder
         */
        public Builder useTileWeighting(boolean enabled) {
            this.useTileWeighting = enabled;
            return this;
        }

        /**
         * Builds the SparseDistortionField.
         *
         * @return a new SparseDistortionField
         */
        public SparseDistortionField build() {
            var finalX = new float[sampleCount];
            var finalY = new float[sampleCount];
            var finalDx = new float[sampleCount];
            var finalDy = new float[sampleCount];
            var finalTileSize = new int[sampleCount];

            System.arraycopy(sampleX, 0, finalX, 0, sampleCount);
            System.arraycopy(sampleY, 0, finalY, 0, sampleCount);
            System.arraycopy(sampleDx, 0, finalDx, 0, sampleCount);
            System.arraycopy(sampleDy, 0, finalDy, 0, sampleCount);
            System.arraycopy(sampleTileSize, 0, finalTileSize, 0, sampleCount);

            var spatialIndex = KDTree2D.build(finalX, finalY, sampleCount);

            return new SparseDistortionField(
                    finalX, finalY, finalDx, finalDy, finalTileSize, sampleCount,
                    spatialIndex, width, height,
                    neighborsK, rbfEpsilon, idwPower, method, baseTileSize, useTileWeighting
            );
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity > capacity) {
                var newCapacity = capacity * 2;
                while (newCapacity < minCapacity) {
                    newCapacity *= 2;
                }
                sampleX = Arrays.copyOf(sampleX, newCapacity);
                sampleY = Arrays.copyOf(sampleY, newCapacity);
                sampleDx = Arrays.copyOf(sampleDx, newCapacity);
                sampleDy = Arrays.copyOf(sampleDy, newCapacity);
                sampleTileSize = Arrays.copyOf(sampleTileSize, newCapacity);
                sampleConfidence = Arrays.copyOf(sampleConfidence, newCapacity);
                capacity = newCapacity;
            }
        }
    }
}
