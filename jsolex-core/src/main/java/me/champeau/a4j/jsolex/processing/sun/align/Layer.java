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
package me.champeau.a4j.jsolex.processing.sun.align;

import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Layer {
    private final ImageMath imageMath;
    private final Image integralImage;
    private final Map<Integer, Quadrant[][]> quadrants = new ConcurrentHashMap<>();
    private final int size;

    public Layer(ImageMath imageMath, Image integralImage, int size) {
        this.imageMath = imageMath;
        this.integralImage = integralImage;
        this.size = size;
    }

    public static Layer of(Image image) {
        if (!isPowerOf2(image.width()) || image.width() != image.height()) {
            throw new IllegalArgumentException("Image must be a square with size being a power of 2");
        }
        var imageMath = ImageMath.newInstance();
        var integralImage = imageMath.integralImage(image);
        return new Layer(imageMath, integralImage, image.width());
    }

    public Quadrant quadrant(int depth, int x, int y) {
        var qSize = this.size / (1 << depth);
        var areaSum = imageMath.areaSum(integralImage, x * qSize, y * qSize, qSize, qSize);
        var areaAverage = areaSum / (qSize * qSize);
        return new Quadrant(depth, x, y, areaSum, areaAverage);
    }

    public Quadrant[][] quadrants(int depth) {
        return this.quadrants.computeIfAbsent(depth, unused -> {
            var n = 1 << depth;
            var result = new Quadrant[n][n];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    result[y][x] = quadrant(depth, x, y);
                }
            }
            return result;
        });
    }

    public double distanceTo(Layer layer, int depth) {
        var quadrants = quadrants(depth);
        var otherQuadrants = layer.quadrants(depth);
        double distance = 0;
        var n = 1 << depth;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                var q = quadrants[y][x];
                var otherQ = otherQuadrants[y][x];
                var diff = q.areaAverage() - otherQ.areaAverage();
                distance += diff * diff;
            }
        }
        return Math.sqrt(distance) / (n * n);
    }

    private static boolean isPowerOf2(int number) {
        return (number & -number) == number;
    }

    public record Quadrant(int depth, int x, int y, float areaSum, float areaAverage) {
    }
}
