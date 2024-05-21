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
package me.champeau.a4j.jsolex.processing.sun.detection;

import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

public class PhenomenaDetector {
    private final Map<Integer, List<Redshift>> redshiftsPerFrame = new ConcurrentHashMap<>();

    private final double dispersion;
    private final double lambda0;
    private final int reconstructedHeight;
    private final int reconstructedWidth;

    public PhenomenaDetector(double dispersion, double lambda0, int reconstructedWidth, int reconstructedHeight) {
        this.dispersion = dispersion;
        this.lambda0 = lambda0;
        this.reconstructedWidth = reconstructedWidth;
        this.reconstructedHeight = reconstructedHeight;
    }

    public double speedOf(int shift) {
        return 299792.458d * shift * dispersion / lambda0;
    }

    public Map<Integer, List<Redshift>> getRedshifts() {
        return redshiftsPerFrame;
    }

    public void performDetection(int frameId, int width, int height, float[] original, DoubleUnaryOperator polynomial, double dispersion) {
        // Ellerman bomb detection consists in looking for a sharp increase in intensity
        // around the h-alpha line. The center of the h-alpha line is untouched, but around
        // the wings, a sharp increase in intensity is expected, but it will not cover the
        // whole height.
        // Detection is done column by column.
        var bordersAnalysis = new SpectrumFrameAnalyzer(width, height, 20000d);
        bordersAnalysis.analyze(original);
        // 10 is to convert nm to angstrom
        int wingShiftInPixels = (int) Math.floor(0.5d / (10d * dispersion));
        var leftBorder = bordersAnalysis.leftSunBorder();
        var rightBorder = bordersAnalysis.rightSunBorder();
        if (leftBorder.isPresent() && rightBorder.isPresent()) {
            int leftLimit = leftBorder.get();
            int rightLimit = rightBorder.get();
            var avgColumnValue = 0d;
            for (int x = leftLimit; x < rightLimit; x++) {
                avgColumnValue += columnAverage(x, width, height, original);
            }
            avgColumnValue /= (rightLimit - leftLimit);
            var stddev = stddev(original, width, height, leftLimit, rightLimit);
            // perform per column analysis
            var collector = new ArrayList<Redshift>();
            for (int x = leftLimit; x < rightLimit; x++) {
                analyzeColumn(frameId, x, width, height, original, polynomial, wingShiftInPixels, collector, avgColumnValue, stddev);
            }
            if (!collector.isEmpty()) {
                // We're only going to keep results for which there are at least 3 consecutive pixels
                // in order to reduce the noise. The one we'll keep is the one with the highest redshift.
                var consecutive = new ArrayList<Redshift>();
                var result = new ArrayList<Redshift>();
                var previous = collector.getFirst();
                consecutive.add(previous);
                collector.sort(Comparator.comparingInt(r -> r.position().b()));
                for (int i = 1; i < collector.size(); i++) {
                    var current = collector.get(i);
                    if (current.position().b() == previous.position().b() + 1) {
                        consecutive.add(current);
                    } else {
                        if (consecutive.size() >= 3) {
                            consecutive.sort(Comparator.comparingInt(Redshift::pixelShift).reversed());
                            var first = consecutive.getFirst();
                            result.add(new Redshift(first.pixelShift(), first.relMaxShift(), speedOf(first.pixelShift()), new IntPair(first.position().a() + 1, first.position().b())));
                        }
                        consecutive.clear();
                        consecutive.add(current);
                    }
                    previous = current;
                }

                // Final check for the last consecutive sequence
                if (consecutive.size() >= 3) {
                    consecutive.sort(Comparator.comparingInt(Redshift::pixelShift).reversed());
                    var first = consecutive.getFirst();
                    result.add(new Redshift(first.pixelShift(), first.relMaxShift(), speedOf(first.pixelShift()), new IntPair(first.position().a() + 1, first.position().b())));
                }

                if (!result.isEmpty()) {
                    redshiftsPerFrame.put(frameId, Collections.unmodifiableList(result));
                }
            }

        }
    }

    private static double stddev(float[] data, int width, int height, int startX, int endX) {
        double sum = 0;
        double range = endX - startX;
        int count = (int) (height * range);

        for (int x = startX; x < endX; x++) {
            for (int y = 0; y < height; y++) {
                sum += data[x + y * width];
            }
        }
        double avg = sum / count;

        double varianceSum = 0;
        for (int x = startX; x < endX; x++) {
            for (int y = 0; y < height; y++) {
                var v = data[x + y * width] - avg;
                varianceSum += v * v;
            }
        }
        double variance = varianceSum / (count - 1);

        return Math.sqrt(variance);
    }


    private static double columnAverage(int column, int width, int height, float[] data) {
        var tmp = 0d;
        for (int y = 0; y < height; y++) {
            tmp += data[column + y * width];
        }
        return tmp / height;
    }

    private void analyzeColumn(int frameId, int x, int width, int height, float[] original, DoubleUnaryOperator polynomial, int wingShiftInPixels, List<Redshift> collector, double avgColumnValue, double stddev) {
        // The polynomial is used to find the original pixel position which is in the middle of the h-alpha line
        double yd = polynomial.applyAsDouble(x);
        int yi = (int) yd;
        if (yi - wingShiftInPixels < 0 || yi + wingShiftInPixels >= height) {
            return;
        }
        var colStdDev = stddev(original, width, height, x, x + 1);
        if (colStdDev < 0.8 * stddev) {
            // reduce risks of detecting sunspots
            return;
        }

        int start = yi - wingShiftInPixels;
        int end = yi + wingShiftInPixels;

        var middle = original[x + width * yi];

        // now we're going to compute the maximum redshift
        int maxShift = 0;
        int relMaxShift = 0;
        int y = end;
        while (y < height && original[x + width * y] <= 1.2 * colStdDev + middle) {
            if (y - yi > maxShift) {
                maxShift = y - yi;
                relMaxShift = maxShift;
            }
            y++;
        }
        y = start;
        while (y >= 0 && original[x + width * y] <= 1.2 * colStdDev + middle) {
            if (yi - y > maxShift) {
                maxShift = yi - y;
                relMaxShift = -maxShift;
            }
            y--;
        }
        if (maxShift >= 2 * wingShiftInPixels) {
            // the coordinates in the final image are reversed (x <-> y) because of a 90° rotation
            // and flipped vertically
            var redshift = new Redshift(maxShift, relMaxShift, speedOf(maxShift), new IntPair(frameId, reconstructedWidth - x - 1));
            collector.add(redshift);
        }

    }

    public List<RedshiftArea> getMaxRedshiftAreas(int limit) {
        return computeAreas()
            .stream()
            .sorted(Comparator.comparingInt(RedshiftArea::pixelShift).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Checks if 2 areas are less than distance apart
     *
     * @param area1 the first area
     * @param area2 the second area
     * @param distance the distance
     * @return true if the areas are less than distance apart
     */
    private static boolean withinRange(RedshiftArea area1, RedshiftArea area2, int distance) {
        int dx = Math.max(0, Math.max(area1.x1() - area2.x2(), area2.x1() - area1.x2()));
        int dy = Math.max(0, Math.max(area1.y1() - area2.y2(), area2.y1() - area1.y2()));
        double actualDistance = Math.sqrt(dx * dx + dy * dy);
        return actualDistance < distance;
    }

    private RedshiftArea mergeAreas(RedshiftArea a1, RedshiftArea a2) {
        int newX1 = Math.min(a1.x1(), a2.x1());
        int newY1 = Math.min(a1.y1(), a2.y1());
        int newX2 = Math.max(a1.x2(), a2.x2());
        int newY2 = Math.max(a1.y2(), a2.y2());
        var max = Math.max(a1.pixelShift(), a2.pixelShift());
        var relMax = a1.pixelShift() > a2.pixelShift() ? a1.relPixelShift() : a2.relPixelShift();
        int maxX = a1.pixelShift() > a2.pixelShift() ? a1.maxX() : a2.maxX();
        int maxY = a1.pixelShift() > a2.pixelShift() ? a1.maxY() : a2.maxY();
        return new RedshiftArea(max, relMax, speedOf(max), newX1, newY1, newX2, newY2, maxX, maxY);
    }

    private List<RedshiftArea> computeAreas() {
        var allRedshifts = redshiftsPerFrame.values()
            .stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparingInt(Redshift::pixelShift).reversed())
            .map(Redshift::toArea)
            .toList();
        var deleted = new BitSet(allRedshifts.size());
        var result = new ArrayList<RedshiftArea>();
        for (int i = 0; i < allRedshifts.size(); i++) {
            var current = allRedshifts.get(i);
            if (deleted.get(i)) {
                continue;
            }
            for (int j = i + 1; j < allRedshifts.size(); j++) {
                if (deleted.get(j)) {
                    continue;
                }
                var next = allRedshifts.get(j);
                if (withinRange(next, current, 64)) {
                    deleted.set(j);
                    if (next.pixelShift() == current.pixelShift()) {
                        var merged = mergeAreas(current, next);
                        current = merged;
                    }
                }
            }
            result.add(current);
        }
        return result;
    }


}
