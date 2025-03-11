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
import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.LinearRegression;
import me.champeau.a4j.math.tuples.DoublePair;
import me.champeau.a4j.math.tuples.IntPair;
import me.champeau.a4j.ser.Header;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PhenomenaDetector {
    private static final double AR_STDDEV_THRESHOLD = 0.85;
    private static final double AR_AVG_THRESHOLD = 0.95;
    private static final int MIN_AR_AREA = 8;

    private static final double SPEED_OF_LIGHT = 299792.458d;
    private final Map<Integer, List<Redshift>> redshiftsPerFrame = new ConcurrentHashMap<>();
    private final Map<Integer, BitSet> activeRegionsPerFrame = new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();
    private final Dispersion dispersion;
    private final Wavelen lambda0;
    private final int reconstructedWidth;

    private PhenomenaListener detectionListener;
    private boolean detectActiveRegions = true;
    private boolean detectRedshifts = true;

    public PhenomenaDetector(Dispersion dispersion, Wavelen lambda0, int reconstructedWidth) {
        this.dispersion = dispersion;
        this.lambda0 = lambda0;
        this.reconstructedWidth = reconstructedWidth;
    }

    public void setDetectActiveRegions(boolean detectActiveRegions) {
        this.detectActiveRegions = detectActiveRegions;
    }

    public void setDetectRedshifts(boolean detectRedshifts) {
        this.detectRedshifts = detectRedshifts;
    }

    public void setDetectionListener(PhenomenaListener detectionListener) {
        this.detectionListener = detectionListener;
    }

    public double speedOf(int shift) {
        return speedOf(shift, dispersion, lambda0);
    }

    public static double speedOf(double shift, Dispersion dispersion, Wavelen lambda0) {
        return SPEED_OF_LIGHT * shift * dispersion.angstromsPerPixel() / lambda0.angstroms();
    }

    public Map<Integer, List<Redshift>> getRedshifts() {
        return redshiftsPerFrame;
    }

    public void performDetection(int frameId, int width, int height, float[][] original, DoubleUnaryOperator polynomial, Header header) {
        if (!detectRedshifts && !detectActiveRegions) {
            return;
        }
        var bordersAnalysis = new SpectrumFrameAnalyzer(width, height, header.isJSolexTrimmedSer(), 20000d).analyze(original);
        // 10 is to convert nm to angstrom
        int wingShiftInPixels = (int) Math.floor(0.5d / dispersion.angstromsPerPixel());
        var leftBorder = bordersAnalysis.leftBorder();
        var rightBorder = bordersAnalysis.rightBorder();
        var avgOfColumnAverages = 0d;
        if (leftBorder.isPresent() && rightBorder.isPresent()) {
            var activeRegionsMask = new BitSet(width);
            int leftLimit = leftBorder.get();
            int rightLimit = rightBorder.get();
            var range = rightLimit - leftLimit;
            leftLimit += (int) (range / 40d);
            rightLimit -= (int) (range / 40d);
            var avgCenterLine = 0d;
            var avgCenterCount = 0;
            var columnsAverages = new double[width];
            var columnsStddevs = new double[width];
            for (int x = leftLimit; x < rightLimit; x++) {
                var columnAvg = columnAverage(x, height, original);
                columnsAverages[x] = columnAvg;
                avgOfColumnAverages += columnAvg;
                var y = (int) Math.round(polynomial.applyAsDouble(x));
                double v = original[y][x];
                avgCenterLine += v;
                avgCenterCount++;
            }
            var avgPoints = new ArrayList<Point2D>();
            var stddevPoints = new ArrayList<Point2D>();
            for (int x = leftLimit; x < rightLimit; x++) {
                var avg = columnsAverages[x];
                avgPoints.add(new Point2D(x, avg));
                double stddev = 0;
                for (int y = 0; y < height; y++) {
                    var delta = original[y][x] - avg;
                    stddev += delta * delta;
                }
                columnsStddevs[x] = Math.sqrt(stddev / height);
                stddevPoints.add(new Point2D(x, columnsStddevs[x]));
            }
            var avgModel = LinearRegression.thirdOrderRegression(avgPoints.toArray(Point2D[]::new)).asPolynomial();
            var stddevModel = LinearRegression.thirdOrderRegression(stddevPoints.toArray(Point2D[]::new)).asPolynomial();
            avgOfColumnAverages /= range;
            avgCenterLine /= avgCenterCount;
            // perform per column analysis
            var collector = new ArrayList<Redshift>();
            double finalAvgCenterLine = avgCenterLine;
            double finalAvgOfColumnAverages = avgOfColumnAverages;
            IntStream.range(leftLimit, rightLimit)
                .parallel()
                .forEach(x -> analyzeColumn(frameId, x, width, height, original, polynomial, wingShiftInPixels, collector, finalAvgCenterLine, finalAvgOfColumnAverages, columnsAverages, columnsStddevs, avgModel, stddevModel, activeRegionsMask));
            if (!collector.isEmpty()) {
                redshiftsPerFrame.put(frameId, Collections.unmodifiableList(collector));
            }
            if (activeRegionsMask.cardinality() > 0) {
                activeRegionsPerFrame.put(frameId, activeRegionsMask);
            }
        }
    }

    private static double stddev(float[][] data, int width, int height, int startX, int endX) {
        double sum = 0;
        double range = endX - startX;
        int count = (int) (height * range);

        for (int x = startX; x < endX; x++) {
            for (int y = 0; y < height; y++) {
                sum += data[y][x];
            }
        }
        double avg = sum / count;

        double varianceSum = 0;
        for (int x = startX; x < endX; x++) {
            for (int y = 0; y < height; y++) {
                var v = data[y][x] - avg;
                varianceSum += v * v;
            }
        }
        double variance = varianceSum / (count - 1);

        return Math.sqrt(variance);
    }


    private static double columnAverage(int column, int height, float[][] data) {
        var tmp = 0d;
        for (int y = 0; y < height; y++) {
            tmp += data[y][column];
        }
        return tmp / height;
    }

    private void analyzeColumn(int frameId,
                               int x,
                               int width,
                               int height,
                               float[][] original,
                               DoubleUnaryOperator polynomial,
                               int wingShiftInPixels,
                               List<Redshift> collector,
                               double avgLineValue,
                               double avgOfcolumnAverages,
                               double[] columnsAverages,
                               double[] columnsStddevs,
                               DoubleUnaryOperator avgModel,
                               DoubleUnaryOperator stddevModel,
                               BitSet activeRegionMask
    ) {
        if (detectActiveRegions) {
            detectActiveRegions(x, columnsAverages, columnsStddevs, avgModel, stddevModel, activeRegionMask);
        }
        if (!detectRedshifts) {
            return;
        }
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
        var middleValue = original[yi][x];
        if (middleValue > 1.5 * avgLineValue) {
            // reduce risks of detecting flares
            return;
        }
        var colMax = 0d;
        for (int y = 0; y < height; y++) {
            var v = original[y][x];
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
            var v = original[y][x];
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
            var v = original[y][x];
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
                    detectionListener.onRedshift(new DoublePair(x, relMaxShift));
                }
                collector.add(redshift);
            } finally {
                lock.unlock();
            }
        }
    }

    private void detectActiveRegions(int x, double[] columnsAverages, double[] columnsStddevs, DoubleUnaryOperator avgModel, DoubleUnaryOperator stddevModel, BitSet activeRegionMask) {
        if (isPotentialActiveRegion(x, columnsAverages, columnsStddevs, avgModel, stddevModel)) {
            // active region detected in this column
            activeRegionMask.set(x);
            if (detectionListener != null) {
                detectionListener.onActiveRegion(x);
            }
        }
    }

    private boolean isPotentialActiveRegion(int x, double[] columnsAverages, double[] columnsStddevs, DoubleUnaryOperator avgModel, DoubleUnaryOperator stddevModel) {
        var avg = columnsAverages[x];
        var stddev = columnsStddevs[x];
        var avgModelValue = avgModel.applyAsDouble(x);
        var stddevModelValue = stddevModel.applyAsDouble(x);
        return avg < AR_AVG_THRESHOLD * avgModelValue && stddev < AR_STDDEV_THRESHOLD * stddevModelValue;
    }

    public ActiveRegions getActiveRegions() {
        var points = new Stack<Point2D>();
        points.addAll(activeRegionsPerFrame.entrySet()
            .stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .flatMap(entry -> entry.getValue().stream().mapToObj(x -> new Point2D(x, entry.getKey())))
            .toList());
        Set<Point2D> pointsAsSet = new HashSet<>(points);
        Set<Point2D> visited = new HashSet<>();
        List<List<Point2D>> activeRegions = new ArrayList<>();
        while (!points.isEmpty()) {
            var current = points.pop();
            if (visited.contains(current)) {
                continue;
            }
            pointsAsSet.remove(current);
            var area = new ArrayList<Point2D>();
            dfs(current, pointsAsSet, visited, area);
            activeRegions.add(area);
        }
        var activeRegionList = activeRegions.stream()
            .sorted(Comparator.<List<Point2D>>comparingInt(List::size).reversed())
            .map(ActiveRegion::of)
            .filter(s -> s.width() >= MIN_AR_AREA && s.height() >= MIN_AR_AREA)
            .toList();
        return new ActiveRegions(clusterActiveRegions(activeRegionList));
    }

    private List<ActiveRegion> clusterActiveRegions(List<ActiveRegion> activeRegionList) {
        activeRegionList = new ArrayList<>(activeRegionList); // make mutable
        activeRegionList.sort(
            Comparator.<ActiveRegion>comparingDouble(s -> s.topLeft().x())
                .thenComparingDouble(s -> s.topLeft().y())
        );

        var deleted = new BitSet(activeRegionList.size());
        var clusters = new ArrayList<ActiveRegion>();

        for (int i = 0; i < activeRegionList.size(); i++) {
            if (deleted.get(i)) {
                continue;
            }

            var currentCluster = activeRegionList.get(i);

            for (int j = i + 1; j < activeRegionList.size(); j++) {
                if (deleted.get(j)) {
                    continue;
                }

                var next = activeRegionList.get(j);

                if (shouldMerge(currentCluster, next)) {
                    currentCluster = mergeActiveRegions(currentCluster, next);
                    deleted.set(j); // Mark as merged
                }
            }

            // Add the merged cluster to the result
            clusters.add(currentCluster);
        }

        return clusters.size() == activeRegionList.size() ? activeRegionList : clusterActiveRegions(clusters);
    }

    private boolean shouldMerge(ActiveRegion a, ActiveRegion b) {
        var aSize = sizeOf(a);
        var bSize = sizeOf(b);
        var centerA = new Point2D((a.topLeft().x() + a.bottomRight().x()) / 2, (a.topLeft().y() + a.bottomRight().y()) / 2);
        var centerB = new Point2D((b.topLeft().x() + b.bottomRight().x()) / 2, (b.topLeft().y() + b.bottomRight().y()) / 2);
        return centerA.distanceTo(centerB) < (aSize + bSize) / 2;
    }

    private ActiveRegion mergeActiveRegions(ActiveRegion a, ActiveRegion b) {
        return ActiveRegion.of(
            Stream.concat(a.points().stream(), b.points().stream())
                .toList()
        );
    }

    private static double sizeOf(ActiveRegion activeRegion) {
        return Math.sqrt(activeRegion.width() * activeRegion.width() + activeRegion.height() * activeRegion.height());
    }


    private static void dfs(Point2D start,
                            Set<Point2D> points,
                            Set<Point2D> visited,
                            List<Point2D> area) {
        var stack = new Stack<Point2D>();
        stack.push(start);

        while (!stack.isEmpty()) {
            var current = stack.pop();

            if (visited.add(current)) {
                var left = new Point2D(current.x() - 1, current.y());
                var right = new Point2D(current.x() + 1, current.y());
                var down = new Point2D(current.x(), current.y() + 1);
                var up = new Point2D(current.x(), current.y() - 1);

                area.add(current);

                if (points.contains(right) && !visited.contains(right)) {
                    stack.push(right);
                }
                if (points.contains(left) && !visited.contains(left)) {
                    stack.push(left);
                }
                if (points.contains(down) && !visited.contains(down)) {
                    stack.push(down);
                }
                if (points.contains(up) && !visited.contains(up)) {
                    stack.push(up);
                }
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


    public boolean hasRedshifts() {
        return !redshiftsPerFrame.isEmpty();
    }

    public boolean hasActiveRegions() {
        return !activeRegionsPerFrame.isEmpty();
    }

    public boolean isRedShiftDetectionEnabled() {
        return detectRedshifts;
    }

    public boolean isActiveRegionsDetectionEnabled() {
        return detectActiveRegions;
    }

    private record Cluster(int x1, int y1, int x2, int y2, int pixelShift, List<RedshiftArea> areas) {

        public Cluster(RedshiftArea redshiftArea) {
            this(redshiftArea.x1(), redshiftArea.y1(), redshiftArea.x2(), redshiftArea.y2(), redshiftArea.pixelShift(), List.of(redshiftArea));
        }
    }
}
