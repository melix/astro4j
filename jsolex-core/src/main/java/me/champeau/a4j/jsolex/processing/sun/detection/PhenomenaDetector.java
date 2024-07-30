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
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.IntPair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PhenomenaDetector {
    private static final double SPEED_OF_LIGHT = 299792.458d;
    private final Map<Integer, List<Redshift>> redshiftsPerFrame = new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();
    private final double dispersion;
    private final double lambda0;
    private final int reconstructedWidth;
    private Consumer<DoublePair> detectionListener;

    public PhenomenaDetector(double dispersion, double lambda0, int reconstructedWidth) {
        this.dispersion = dispersion;
        this.lambda0 = lambda0;
        this.reconstructedWidth = reconstructedWidth;
    }

    public void setDetectionListener(Consumer<DoublePair> detectionListener) {
        this.detectionListener = detectionListener;
    }

    public double speedOf(int shift) {
        return speedOf(shift, dispersion, lambda0);
    }

    public static double speedOf(double shift, double dispersion, double lambda0) {
        return SPEED_OF_LIGHT * shift * dispersion / lambda0;
    }

    public Map<Integer, List<Redshift>> getRedshifts() {
        return redshiftsPerFrame;
    }

    public void performDetection(int frameId, int width, int height, float[] original, DoubleUnaryOperator polynomial) {
        // Ellerman bomb detection consists in looking for a sharp increase in intensity
        // around the h-alpha line. The center of the h-alpha line is untouched, but around
        // the wings, a sharp increase in intensity is expected, but it will not cover the
        // whole height.
        // Detection is done column by column.
        var bordersAnalysis = new SpectrumFrameAnalyzer(width, height, 20000d).analyze(original);
        // 10 is to convert nm to angstrom
        int wingShiftInPixels = (int) Math.floor(0.5d / (10d * dispersion));
        var leftBorder = bordersAnalysis.leftBorder();
        var rightBorder = bordersAnalysis.rightBorder();
        var avgOfColumnAverages = 0d;
        if (leftBorder.isPresent() && rightBorder.isPresent()) {
            int leftLimit = leftBorder.get();
            int rightLimit = rightBorder.get();
            var range = rightLimit - leftLimit;
            leftLimit += (int) (range / 40d);
            rightLimit -= (int) (range / 40d);
            var avgCenterLine = 0d;
            var avgCenterCount = 0;
            var avgWings = 0d;
            var avgWingsCount = 0;
            var columnsAverages = new double[width];
            for (int x = leftLimit; x < rightLimit; x++) {
                var columnAvg = columnAverage(x, width, height, original);
                columnsAverages[x] = columnAvg;
                avgOfColumnAverages += columnAvg;
                var y = (int) Math.round(polynomial.applyAsDouble(x));
                double v = original[x + y * width];
                avgCenterLine += v;
                avgCenterCount++;
                if (y - wingShiftInPixels >= 0) {
                    v = original[x + (y - wingShiftInPixels) * width];
                    avgWings += v;
                    avgWingsCount++;
                }
                if (y + wingShiftInPixels < height) {
                    v = original[x + (y + wingShiftInPixels) * width];
                    avgWings += v;
                    avgWingsCount++;
                }
            }
            avgOfColumnAverages /= range;
            avgCenterLine /= avgCenterCount;
            // perform per column analysis
            var collector = new ArrayList<Redshift>();
            double finalAvgCenterLine = avgCenterLine;
            double finalAvgOfColumnAverages = avgOfColumnAverages;
            IntStream.range(leftLimit, rightLimit).parallel().forEach(x -> analyzeColumn(frameId, x, width, height, original, polynomial, wingShiftInPixels, collector, finalAvgCenterLine, finalAvgOfColumnAverages, columnsAverages));
            if (!collector.isEmpty()) {
                redshiftsPerFrame.put(frameId, Collections.unmodifiableList(collector));
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

    private void analyzeColumn(int frameId,
                               int x,
                               int width,
                               int height,
                               float[] original,
                               DoubleUnaryOperator polynomial,
                               int wingShiftInPixels,
                               List<Redshift> collector,
                               double avgLineValue,
                               double avgOfcolumnAverages,
                               double[] columnsAverages) {
        // The polynomial is used to find the original pixel position which is in the middle of the h-alpha line
        double yd = polynomial.applyAsDouble(x);
        int yi = (int) yd;
        if (yi - wingShiftInPixels < 0 || yi + wingShiftInPixels >= height) {
            return;
        }
        var colStdDev = stddev(original, width, height, x, x + 1);
        var columnAverage = columnsAverages[x];
        if (columnAverage < 0.9 * avgOfcolumnAverages) {
            return;
        }
        var middleValue = original[x + width * yi];
        if (middleValue > 1.5 * avgLineValue) {
            // reduce risks of detecting flares
            return;
        }
        var colMax = 0d;
        for (int y = 0; y < height; y++) {
            var v = original[x + width * y];
            if (v > colMax) {
                colMax = v;
            }
        }

        // now we're going to compute the maximum redshift
        int maxShift = 0;
        int relMaxShift = 0;
        int y = yi + wingShiftInPixels + 1;
        double prev = -1;
        int minY = 0;
        int maxY = 0;
        while (y < height) {
            var v = original[x + width * y];
            var shift = y - yi;
            var threshold = avgLineValue + 2 * colStdDev;
            if (v >= threshold || (prev > 0 && v > 1.2 * prev)) {
                break;
            }
            if (shift > maxShift) {
                maxShift = shift;
                relMaxShift = maxShift;
            }
            prev = v;
            y++;
            maxY = y;
        }
        y = yi - wingShiftInPixels - 1;
        prev = -1;
        int maxShiftDown = 0;
        int relMaxShiftDown = 0;
        while (y >= 0) {
            var v = original[x + width * y];
            var shift = yi - y;
            var threshold = avgLineValue + 2 * colStdDev;
            if (v >= threshold || (prev > 0 && v > 1.2 * prev)) {
                break;
            }
            if (shift > maxShiftDown) {
                maxShiftDown = shift;
                relMaxShiftDown = -shift;
            }
            prev = v;
            y--;
            minY = y;
        }
        if (maxShiftDown > maxShift) {
            maxShift = maxShiftDown;
            relMaxShift = relMaxShiftDown;
        }
        if (maxY == height) {
            return;
        }
        if (minY == 0) {
            return;
        }
        if (maxShift >= 2 * wingShiftInPixels) {
            var redshift = new Redshift(maxShift, relMaxShift, speedOf(maxShift), new IntPair(frameId, reconstructedWidth - x - 1));
            lock.lock();
            try {
                // the coordinates in the final image are reversed (x <-> y) because of a 90° rotation
                // and flipped vertically
                if (detectionListener != null) {
                    detectionListener.accept(new DoublePair(x, relMaxShift));
                }
                collector.add(redshift);
            } finally {
                lock.unlock();
            }
        }
    }

    public List<RedshiftArea> getMaxRedshiftAreas(int limit) {
        var anonynous = computeAreas()
            .stream()
            .sorted(
                Comparator.comparingInt(RedshiftArea::pixelShift).reversed().thenComparing(
                    Comparator.comparingInt(RedshiftArea::area)
                )
            ).limit(limit)
            .toList();
        var withId = new ArrayList<RedshiftArea>(limit);
        // provide an id to areas, starting from A to Z. If there's more than 26 areas, we'll use a numbered suffix, e.g A2
        char letter = 'A';
        int i = 0;
        int j = 0;
        for (var area : anonynous) {
            if (i == 26) {
                letter = 'A';
                j++;
                i = 0;
            }
            var id = j == 0 ? String.valueOf(letter) : letter + String.valueOf(j);
            var areaWithId = new RedshiftArea(id, area.pixelShift(), area.relPixelShift(), area.kmPerSec(), area.x1(), area.y1(), area.x2(), area.y2(), area.maxX(), area.maxY());
            withId.add(areaWithId);
            i++;
            letter++;
        }
        return withId;
    }

    /**
     * Checks if 2 areas are less than distance apart
     *
     * @param area1 the first area
     * @param area2 the second area
     * @param distance the distance
     * @return true if the areas are less than distance apart
     */
    private static boolean withinRange(Cluster area1, Cluster area2, int distance) {
        int dx = Math.max(0, Math.max(area1.x1() - area2.x2(), area2.x1() - area1.x2()));
        int dy = Math.max(0, Math.max(area1.y1() - area2.y2(), area2.y1() - area1.y2()));
        double actualDistance = Math.sqrt(dx * dx + dy * dy);
        return actualDistance < distance;
    }

    private Cluster mergeAreas(Cluster a1, Cluster a2) {
        int newX1 = Math.min(a1.x1(), a2.x1());
        int newY1 = Math.min(a1.y1(), a2.y1());
        int newX2 = Math.max(a1.x2(), a2.x2());
        int newY2 = Math.max(a1.y2(), a2.y2());
        return new Cluster(newX1, newY1, newX2, newY2, a1.pixelShift(), Stream.of(a1.areas, a2.areas).flatMap(List::stream).toList());
    }

    private List<RedshiftArea> computeAreas() {
        var allRedshifts = redshiftsPerFrame.values()
            .stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparingInt(Redshift::pixelShift).reversed())
            .limit(4096)
            .map(Redshift::toArea)
            .toList();
        var deleted = new BitSet(allRedshifts.size());
        var clusters = new ArrayList<Cluster>();
        for (int i = 0; i < allRedshifts.size(); i++) {
            var current = new Cluster(allRedshifts.get(i));
            if (deleted.get(i)) {
                continue;
            }
            for (int j = i + 1; j < allRedshifts.size(); j++) {
                if (deleted.get(j)) {
                    continue;
                }
                var next = new Cluster(allRedshifts.get(j));
                if (withinRange(next, current, 32)) {
                    deleted.set(j);
                    if (next.pixelShift() == current.pixelShift()) {
                        current = mergeAreas(current, next);
                    } else if (next.pixelShift() > current.pixelShift()) {
                        current = next;
                    }
                }
            }
            clusters.add(current);
        }
        // compute result by reducing clusters, the maxX/maxY positions are going to be the average of all areas centers
        var result = new ArrayList<RedshiftArea>(clusters.size());
        for (var cluster : clusters) {
            var areas = cluster.areas();
            var x1 = cluster.x1();
            var y1 = cluster.y1();
            var x2 = cluster.x2();
            var y2 = cluster.y2();
            var maxX = 0;
            var maxY = 0;
            for (var area : areas) {
                x1 = Math.min(x1, area.x1());
                y1 = Math.min(y1, area.y1());
                x2 = Math.max(x2, area.x2());
                y2 = Math.max(y2, area.y2());
                maxX += area.maxX();
                maxY += area.maxY();
            }
            maxX /= areas.size();
            maxY /= areas.size();
            var first = cluster.areas().getFirst();
            var redshiftArea = new RedshiftArea(null, cluster.pixelShift(), first.relPixelShift(), first.kmPerSec(), x1, y1, x2, y2, maxX, maxY);
            result.add(redshiftArea);
        }
        return result;
    }


    public boolean isEmpty() {
        return redshiftsPerFrame.isEmpty();
    }

    private record Cluster(int x1, int y1, int x2, int y2, int pixelShift, List<RedshiftArea> areas) {

        public Cluster(RedshiftArea redshiftArea) {
            this(redshiftArea.x1(), redshiftArea.y1(), redshiftArea.x2(), redshiftArea.y2(), redshiftArea.pixelShift(), List.of(redshiftArea));
        }
    }
}
