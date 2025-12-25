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

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

import java.util.ArrayList;

/**
 * Sampling strategy that selects positions on a regular grid.
 * <p>
 * This is the traditional approach where samples are taken at fixed intervals
 * across the entire image. The grid density is controlled by the sampling parameter.
 * </p>
 */
public final class GridSamplingStrategy implements SamplingStrategy {

    private static final int MIN_STEP = 8;
    private static final ImageMath IMAGE_MATH = ImageMath.newInstance();

    private final double sampling;

    /**
     * Creates a grid sampling strategy.
     *
     * @param sampling sampling density factor (e.g., 0.5 means step = tileSize * 0.5)
     */
    public GridSamplingStrategy(double sampling) {
        this.sampling = sampling;
    }

    @Override
    public SamplePositions selectPositions(float[][] referenceData,
                                            int width,
                                            int height,
                                            int tileSize,
                                            float signalThreshold) {
        int increment = (int) Math.max(MIN_STEP, tileSize * sampling);
        int maxX = width - tileSize;
        int maxY = height - tileSize;
        int tileOffset = tileSize / 2;

        var integralImage = IMAGE_MATH.integralImage(new Image(width, height, referenceData));

        var xList = new ArrayList<Integer>();
        var yList = new ArrayList<Integer>();

        for (int y = 0; y <= maxY; y += increment) {
            for (int x = 0; x <= maxX; x += increment) {
                float avgSignal = IMAGE_MATH.areaAverage(integralImage, x, y, tileSize, tileSize);
                if (avgSignal > signalThreshold) {
                    xList.add(x + tileOffset);
                    yList.add(y + tileOffset);
                }
            }
        }

        int count = xList.size();
        int[] xArr = new int[count];
        int[] yArr = new int[count];
        for (int i = 0; i < count; i++) {
            xArr[i] = xList.get(i);
            yArr[i] = yList.get(i);
        }

        return SamplePositions.uniform(xArr, yArr, tileSize, count);
    }

    @Override
    public int getOutputGridStep(int tileSize, double sampling) {
        return (int) Math.max(MIN_STEP, tileSize * sampling);
    }

    @Override
    public String getName() {
        return "Grid (sampling=" + sampling + ")";
    }
}
