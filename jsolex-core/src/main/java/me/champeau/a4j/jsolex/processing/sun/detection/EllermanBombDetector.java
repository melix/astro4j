/*
 * Copyright 2026-2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.util.Dispersion;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.DoubleUnaryOperator;

/**
 * Detects Ellerman bombs and flares in the frames of a scan.
 * <p>
 * An Ellerman bomb is a compact brightening of both wings of the line. It is reported
 * as a flare when the line core is in emission too. Each column of a frame is summarized by the mean intensity
 * of 4 bands: the line core, the 2 wings and the far wings, the latter being moved
 * closer to the line, or measured on one side only, when the frame doesn't extend far
 * enough. A column is compared to the median of a ring of neighbours, which excludes
 * the columns next to it since a brightening spans a few of them.
 * <p>
 * The comparison with neighbouring pixels rather than with the mean profile of the
 * scan, the normalization by the outer wings and the use of the scatter of the
 * neighbours follow the approach described by Stephan Schmelzing in his study of
 * Ellerman bomb candidates observed with a spectroheliograph.
 * <p>
 * The excess of a wing is measured twice, on the intensities and on the intensities
 * divided by the far wings, and the smallest of both is kept: a column which is
 * brighter at all wavelengths only shows an excess of intensity, whereas a dark
 * feature such as a pore, where the line is shallower, only shows an excess once
 * normalized. The excess of a column is the one of its weakest wing. A column is a
 * candidate when this excess is significant compared to the scatter of the ring.
 * <p>
 * Candidates found in neighbouring columns and frames are then merged, and only
 * those which are seen more than once, remain compact along the scan and contain a
 * clearly significant column are kept. The confidence of a brightening grows with
 * the excess of its brightest column, following a logistic curve fitted on
 * brightenings classified by eye, on which the minimal confidence was chosen too.
 */
class EllermanBombDetector {
    private static final double CORE_LIMIT_ANGSTROMS = 0.35;
    private static final double WING_START_ANGSTROMS = 0.75;
    private static final double WING_END_ANGSTROMS = 2.25;
    private static final double REFERENCE_START_ANGSTROMS = 3.5;
    private static final double REFERENCE_END_ANGSTROMS = 5;
    private static final double MIN_REFERENCE_GAP_ANGSTROMS = 0.25;
    private static final double MIN_REFERENCE_WIDTH_ANGSTROMS = 1;
    private static final int MIN_WING_ROWS = 3;
    private static final int MIN_REFERENCE_ROWS = 4;
    private static final int GUARD_RANGE = 4;
    private static final int LOCAL_RANGE = 12;
    private static final int MIN_RING_SIZE = LOCAL_RANGE - GUARD_RANGE + 1;
    private static final int BORDER_MARGIN = 16;
    private static final int MIN_WIDTH_FOR_DETECTION = 8 * LOCAL_RANGE;
    private static final double MIN_CANDIDATE_SIGNIFICANCE = 2;
    private static final double MIN_PEAK_SIGNIFICANCE = 3;
    private static final double HALF_CONFIDENCE_EXCESS = 0.0427;
    private static final double CONFIDENCE_STEEPNESS = 3.81;
    private static final double MIN_CONFIDENCE = 0.7;
    private static final double FLARE_CORE_BRIGHTENING = 2;
    private static final int CLUSTER_COLUMN_RANGE = 2;
    private static final int MIN_CLUSTER_SIZE = 2;
    private static final int MAX_SIZE = 2 * LOCAL_RANGE;
    private static final int ELLERMAN_DEDUP_DISTANCE = 8;
    private static final int FLARE_DEDUP_DISTANCE = 16;
    private static final int MAX_COUNT = 5;

    private final Dispersion dispersion;
    private final Queue<Candidate> candidates = new ConcurrentLinkedQueue<>();

    EllermanBombDetector(Dispersion dispersion) {
        this.dispersion = dispersion;
    }

    void analyzeFrame(int frameId, int left, int right, int height, float[][] frame, DoubleUnaryOperator polynomial) {
        if (right - left < MIN_WIDTH_FOR_DETECTION) {
            return;
        }
        var bands = computeBands(frame, left, right, height, polynomial);
        var ring = new double[2 * MIN_RING_SIZE];
        for (int x = left + BORDER_MARGIN; x < right - BORDER_MARGIN; x++) {
            if (Double.isNaN(bands.core()[x])) {
                continue;
            }
            var lowWing = measureWingExcess(bands.lowWing(), bands.normalizedLowWing(), x, left, right, ring);
            if (lowWing == null) {
                continue;
            }
            var highWing = measureWingExcess(bands.highWing(), bands.normalizedHighWing(), x, left, right, ring);
            if (highWing == null) {
                continue;
            }
            var excess = Math.min(lowWing.relative(), highWing.relative());
            var significance = excess / Math.max(lowWing.scatter(), highWing.scatter());
            if (!(significance >= MIN_CANDIDATE_SIGNIFICANCE)) {
                continue;
            }
            var kind = bands.core()[x] > FLARE_CORE_BRIGHTENING * bands.averageCore() ? Flare.Kind.FLARE : Flare.Kind.ELLERMAN_BOMB;
            candidates.add(new Candidate(kind, frameId, x, excess, significance));
        }
    }

    /**
     * Returns the confirmed brightenings, best first. The score of a brightening is its
     * confidence, in percent.
     *
     * @param disk the solar disk, in (column, frame) coordinates, if it could be determined
     */
    List<Flare> findFlares(Optional<Ellipse> disk) {
        var framesPerPixel = disk.map(EllermanBombDetector::framesPerPixel).orElse(1d);
        var maxFrameSpan = Math.max(MAX_SIZE, MAX_SIZE * framesPerPixel);
        var confirmed = new ArrayList<Flare>();
        for (var cluster : clusterCandidates()) {
            if (cluster.members().size() < MIN_CLUSTER_SIZE || cluster.significance() < MIN_PEAK_SIGNIFICANCE || cluster.confidence() < MIN_CONFIDENCE) {
                continue;
            }
            if (cluster.kind() == Flare.Kind.ELLERMAN_BOMB && cluster.frameSpan() > maxFrameSpan) {
                continue;
            }
            confirmed.add(cluster.toFlare());
        }
        confirmed.sort(Comparator.comparingDouble(Flare::score).reversed());
        var flares = new ArrayList<Flare>();
        for (var flare : confirmed) {
            if (flares.size() == MAX_COUNT) {
                break;
            }
            if (flares.stream().noneMatch(better -> areDuplicates(better, flare))) {
                flares.add(flare);
            }
        }
        return List.copyOf(flares);
    }

    private static double framesPerPixel(Ellipse disk) {
        var box = disk.boundingBox();
        return (box.d() - box.c()) / (box.b() - box.a());
    }

    private static boolean areDuplicates(Flare better, Flare flare) {
        var distance = new Point2D(better.x(), better.y()).distanceTo(new Point2D(flare.x(), flare.y()));
        return distance < (better.kind() == Flare.Kind.FLARE ? FLARE_DEDUP_DISTANCE : ELLERMAN_DEDUP_DISTANCE);
    }

    private List<Cluster> clusterCandidates() {
        var all = List.copyOf(candidates);
        var indices = new HashMap<Long, Integer>();
        for (int i = 0; i < all.size(); i++) {
            indices.put(key(all.get(i).frameId(), all.get(i).x()), i);
        }
        var parents = new int[all.size()];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = i;
        }
        for (int i = 0; i < all.size(); i++) {
            var candidate = all.get(i);
            for (int dx = -CLUSTER_COLUMN_RANGE; dx <= CLUSTER_COLUMN_RANGE; dx++) {
                var previous = indices.get(key(candidate.frameId() - 1, candidate.x() + dx));
                if (previous != null) {
                    parents[root(parents, i)] = root(parents, previous);
                }
                var neighbour = dx < 0 ? indices.get(key(candidate.frameId(), candidate.x() + dx)) : null;
                if (neighbour != null) {
                    parents[root(parents, i)] = root(parents, neighbour);
                }
            }
        }
        var clusters = new HashMap<Integer, List<Candidate>>();
        for (int i = 0; i < all.size(); i++) {
            clusters.computeIfAbsent(root(parents, i), unused -> new ArrayList<>()).add(all.get(i));
        }
        return clusters.values().stream().map(Cluster::new).toList();
    }

    private static long key(int frameId, int x) {
        return ((long) frameId << 32) | (x & 0xFFFFFFFFL);
    }

    private static int root(int[] parents, int i) {
        while (parents[i] != i) {
            parents[i] = parents[parents[i]];
            i = parents[i];
        }
        return i;
    }

    private Bands computeBands(float[][] frame, int left, int right, int height, DoubleUnaryOperator polynomial) {
        int width = frame[0].length;
        var core = new double[width];
        var lowWing = new double[width];
        var highWing = new double[width];
        var reference = new double[width];
        double angstromsPerPixel = dispersion.angstromsPerPixel();
        double coreLimit = CORE_LIMIT_ANGSTROMS / angstromsPerPixel;
        double wingStart = WING_START_ANGSTROMS / angstromsPerPixel;
        double wingEnd = WING_END_ANGSTROMS / angstromsPerPixel;
        int margin = 5 * height / 100;
        boolean hasReference = false;
        for (int x = left; x < right; x++) {
            double center = polynomial.applyAsDouble(x);
            var lowReference = ReferenceBand.of(wingEnd, center - margin, angstromsPerPixel);
            var highReference = ReferenceBand.of(wingEnd, height - 1 - margin - center, angstromsPerPixel);
            double extent = Math.max(Math.max(wingEnd, lowReference.end()), highReference.end());
            int from = Math.max(margin, (int) Math.ceil(center - extent));
            int to = Math.min(height - margin - 1, (int) Math.floor(center + extent));
            double coreSum = 0;
            double lowSum = 0;
            double highSum = 0;
            double referenceSum = 0;
            int coreCount = 0;
            int lowCount = 0;
            int highCount = 0;
            int referenceCount = 0;
            for (int y = from; y <= to; y++) {
                double offset = y - center;
                double distance = Math.abs(offset);
                var v = frame[y][x];
                if (distance < coreLimit) {
                    coreSum += v;
                    coreCount++;
                } else if ((offset < 0 ? lowReference : highReference).contains(distance)) {
                    referenceSum += v;
                    referenceCount++;
                } else if (distance >= wingStart && distance <= wingEnd) {
                    if (offset < 0) {
                        lowSum += v;
                        lowCount++;
                    } else {
                        highSum += v;
                        highCount++;
                    }
                }
            }
            if (coreCount == 0 || lowCount < MIN_WING_ROWS || highCount < MIN_WING_ROWS) {
                core[x] = Double.NaN;
                lowWing[x] = Double.NaN;
                highWing[x] = Double.NaN;
                continue;
            }
            core[x] = coreSum / coreCount;
            lowWing[x] = lowSum / lowCount;
            highWing[x] = highSum / highCount;
            if (referenceCount >= MIN_REFERENCE_ROWS && referenceSum > 0) {
                reference[x] = referenceSum / referenceCount;
                hasReference = true;
            } else {
                reference[x] = Double.NaN;
            }
        }
        double coreTotal = 0;
        int validColumns = 0;
        for (int x = left; x < right; x++) {
            if (hasReference && Double.isNaN(reference[x])) {
                core[x] = Double.NaN;
                lowWing[x] = Double.NaN;
                highWing[x] = Double.NaN;
            }
            if (!Double.isNaN(core[x])) {
                coreTotal += core[x];
                validColumns++;
            }
        }
        var averageCore = validColumns == 0 ? 0 : coreTotal / validColumns;
        if (!hasReference) {
            return new Bands(core, lowWing, highWing, lowWing, highWing, averageCore);
        }
        return new Bands(core, lowWing, highWing, normalize(lowWing, reference), normalize(highWing, reference), averageCore);
    }

    private static double[] normalize(double[] values, double[] reference) {
        var normalized = new double[values.length];
        for (int x = 0; x < values.length; x++) {
            normalized[x] = values[x] / reference[x];
        }
        return normalized;
    }

    private static Excess measureWingExcess(double[] wing, double[] normalizedWing, int x, int left, int right, double[] ring) {
        var excess = measureExcess(normalizedWing, x, left, right, ring);
        if (excess == null || wing == normalizedWing) {
            return excess;
        }
        var intensityExcess = measureExcess(wing, x, left, right, ring);
        if (intensityExcess == null) {
            return null;
        }
        return new Excess(Math.min(excess.relative(), intensityExcess.relative()), excess.scatter());
    }

    private static Excess measureExcess(double[] values, int x, int left, int right, double[] ring) {
        int n = collectRing(values, x, left, right, ring);
        if (n < MIN_RING_SIZE) {
            return null;
        }
        var value = values[x];
        int below = 0;
        for (int i = 0; i < n; i++) {
            if (ring[i] < value) {
                below++;
            }
        }
        if (below <= n / 2) {
            return null;
        }
        Arrays.sort(ring, 0, n);
        var median = ring[n / 2];
        for (int i = 0; i < n; i++) {
            ring[i] = Math.abs(ring[i] - median);
        }
        Arrays.sort(ring, 0, n);
        return new Excess(value / median - 1, ring[n / 2] / median);
    }

    private static int collectRing(double[] values, int x, int left, int right, double[] ring) {
        int n = 0;
        for (int k = GUARD_RANGE; k <= LOCAL_RANGE; k++) {
            for (int neighbour = x - k; neighbour <= x + k; neighbour += 2 * k) {
                if (neighbour >= left && neighbour < right && !Double.isNaN(values[neighbour])) {
                    ring[n++] = values[neighbour];
                }
            }
        }
        return n;
    }

    private record ReferenceBand(double start, double end) {
        static ReferenceBand of(double wingEnd, double available, double angstromsPerPixel) {
            double end = Math.min(REFERENCE_END_ANGSTROMS / angstromsPerPixel, available);
            double start = Math.max(wingEnd + MIN_REFERENCE_GAP_ANGSTROMS / angstromsPerPixel,
                    Math.min(REFERENCE_START_ANGSTROMS / angstromsPerPixel, end - MIN_REFERENCE_WIDTH_ANGSTROMS / angstromsPerPixel));
            return new ReferenceBand(start, end);
        }

        boolean contains(double distance) {
            return distance >= start && distance <= end;
        }
    }

    private record Bands(double[] core,
                         double[] lowWing,
                         double[] highWing,
                         double[] normalizedLowWing,
                         double[] normalizedHighWing,
                         double averageCore) {
    }

    private record Excess(double relative, double scatter) {
    }

    private record Candidate(Flare.Kind kind, int frameId, int x, double excess, double significance) {
    }

    private record Cluster(List<Candidate> members) {
        Candidate brightest() {
            return members.stream().max(Comparator.comparingDouble(Candidate::excess)).orElseThrow();
        }

        double significance() {
            return members.stream().mapToDouble(Candidate::significance).max().orElse(0);
        }

        Flare.Kind kind() {
            return members.stream().anyMatch(member -> member.kind() == Flare.Kind.FLARE) ? Flare.Kind.FLARE : Flare.Kind.ELLERMAN_BOMB;
        }

        double confidence() {
            return 1 / (1 + Math.pow(HALF_CONFIDENCE_EXCESS / brightest().excess(), CONFIDENCE_STEEPNESS));
        }

        Flare toFlare() {
            var brightest = brightest();
            return new Flare(kind(), brightest.frameId(), brightest.x(), brightest.x(), brightest.frameId(), 100 * confidence());
        }

        int frameSpan() {
            int first = Integer.MAX_VALUE;
            int last = Integer.MIN_VALUE;
            for (var member : members) {
                first = Math.min(first, member.frameId());
                last = Math.max(last, member.frameId());
            }
            return last - first + 1;
        }
    }
}
