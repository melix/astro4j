/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.expr.impl.deghost;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import static me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid.AZIMUTH_SAMPLES;
import static me.champeau.a4j.jsolex.processing.expr.impl.deghost.PolarGrid.RADIAL_SAMPLES;

/**
 * Finds the shift of a reflected disk copy whose rim best matches an edge of the brightness excess. For each azimuth
 * up to {@code EDGE_RANKS} outermost radii where the excess falls below its local level are collected (a rim may be
 * hidden below unrelated brighter structure), and only sharp edges qualify; a reflection makes those loci follow the
 * rim of the shifted disk copy, so the shift amount is estimated from the median edge offset around each direction
 * and a candidate is scored by the quality-weighted count of azimuths with an edge on the predicted rim. The best
 * candidates are refined with a least-squares fit of the shifted-circle centre to the edge locus, then snapped to
 * the position maximising the brightness step observed across the rim. Glow around the disk cannot fake an edge
 * locus that follows the rim curve.
 */
public final class GhostDetector {

    public static final double MAX_SHIFT = 0.55;           // ghost shift search range (× R)
    public static final double EDGE_PEAK_FACTOR = 2;       // a signal only counts when clearly above the floor

    private static final double MIN_SHIFT = 0.10;
    private static final double DETECT_RADIAL_SIGMA_BINS = 8; // radial blur for edge detection: rims are large-scale, banding is not
    private static final double LATER_PASS_RELAX = 0.5;    // support threshold factor once a first reflection was found
    private static final double LIMB_EXCLUSION = 0.08;     // edges below this (× R) belong to the limb glow, not a rim
    private static final double EDGE_TOLERANCE = 0.04;     // an edge supports a rim when within this distance (× R)
    private static final double EDGE_LEVEL = 0.5;          // the edge sits where the excess falls to half its peak
    private static final double EDGE_SHARPNESS = 0.55;     // beyond a rim the excess must fall below this fraction of the inside value within one rim blur
    private static final int CENSOR_MARGIN_SAMPLES = 5;    // edges this close to the frame boundary are unreliable
    private static final int MIN_EDGE_SUPPORT = 40;        // ≥ 20° of the edge locus must lie on the rim
    private static final double RING_REJECTION = 1.25;     // the rim curve must beat a constant-radius circle by this factor
    private static final double RING_TEST_MIN_SHIFT = 0.2; // below this shift (× R) a rim is too close to a circle to test
    private static final int DIRECTION_STEP_BINS = 6;
    private static final int EDGE_RANKS = 3;               // outermost edges collected per azimuth
    private static final int REFINE_ROUNDS = 5;
    private static final int REFINE_SEEDS = 5;
    private static final int SNAP_RANGE = 40;              // ± pixels of local shift polish around the fitted rim
    private static final double SNAP_PROBE = 0.03;         // probe distance for the snap's rim step (× R)
    private static final double EXCLUSION_DIRECTION = Math.toRadians(15); // a found shift blocks re-detection within this angle ...
    private static final double EXCLUSION_SHIFT = 0.08;    // ... and within this shift distance (× R)

    private GhostDetector() {
    }

    public static GhostShift detect(double[][] excess, boolean[][] valid, double[][] smooth, List<GhostShift> exclusions, double threshold, double innerR, double outerR, double radius) {
        var detectField = new double[RADIAL_SAMPLES][AZIMUTH_SAMPLES];
        for (int ir = 0; ir < RADIAL_SAMPLES; ir++) {
            System.arraycopy(excess[ir], 0, detectField[ir], 0, AZIMUTH_SAMPLES);
        }
        PolarGrid.smoothRadial(detectField, DETECT_RADIAL_SIGMA_BINS);

        var edges = new double[EDGE_RANKS][AZIMUTH_SAMPLES];
        var quality = new double[EDGE_RANKS][AZIMUTH_SAMPLES];
        extractEdges(detectField, valid, threshold, innerR, outerR, radius, edges, quality);

        var candidates = new ArrayList<GhostShift>();
        var supports = new ArrayList<Double>();
        var minSupport = 0.6 * MIN_EDGE_SUPPORT * (exclusions.isEmpty() ? 1 : LATER_PASS_RELAX);
        var offsets = new double[AZIMUTH_SAMPLES];
        for (var edge : edges) {
            for (int direction = 0; direction < AZIMUTH_SAMPLES; direction += DIRECTION_STEP_BINS) {
                var count = 0;
                for (int db = -AZIMUTH_SAMPLES / 8; db <= AZIMUTH_SAMPLES / 8; db++) {
                    var it = Math.floorMod(direction + db, AZIMUTH_SAMPLES);
                    if (Double.isNaN(edge[it])) {
                        continue;
                    }
                    var u = edge[it] - innerR;
                    if (u < LIMB_EXCLUSION * radius) {
                        continue;
                    }
                    offsets[count++] = u / Math.cos(db * 2 * Math.PI / AZIMUTH_SAMPLES);
                }
                if (count < MIN_EDGE_SUPPORT / 2) {
                    continue;
                }
                Arrays.sort(offsets, 0, count);
                var amount = offsets[count / 2];
                if (amount < MIN_SHIFT * radius || amount > MAX_SHIFT * radius) {
                    continue;
                }
                var candidate = new GhostShift(amount, PolarGrid.azimuthAt(direction));
                if (isExcluded(candidate, exclusions, radius)) {
                    continue;
                }
                var candidateSupport = support(candidate, edges, quality, innerR, radius);
                if (candidateSupport < minSupport) {
                    continue;
                }
                if (candidate.amount() >= RING_TEST_MIN_SHIFT * radius && isRingLike(candidate, edge, (int) support(candidate, edges, null, innerR, radius), innerR, radius)) {
                    continue;
                }
                candidates.add(candidate);
                supports.add(candidateSupport);
            }
        }

        GhostShift best = null;
        var bestSupport = minSupport;
        var seeds = new ArrayList<GhostShift>();
        while (seeds.size() < REFINE_SEEDS && !candidates.isEmpty()) {
            var top = 0;
            for (int i = 1; i < supports.size(); i++) {
                if (supports.get(i) > supports.get(top)) {
                    top = i;
                }
            }
            var seed = candidates.remove(top);
            supports.remove(top);
            if (isExcluded(seed, seeds, radius)) {
                continue;
            }
            seeds.add(seed);
            var refined = refine(seed, edges, quality, innerR, radius);
            if (isExcluded(refined, exclusions, radius)) {
                continue;
            }
            var refinedSupport = support(refined, edges, quality, innerR, radius);
            if (refinedSupport > bestSupport) {
                bestSupport = refinedSupport;
                best = refined;
            }
        }
        if (best != null) {
            best = snap(smooth, edges, quality, best, exclusions, innerR, outerR, radius);
        }
        return best;
    }

    /**
     * Collects, for each azimuth, the outermost radii where the excess falls below its local level with a sharp
     * enough drop, together with a sharpness quality in [0, 1].
     */
    private static void extractEdges(double[][] excess, boolean[][] valid, double threshold, double innerR, double outerR, double radius, double[][] edges, double[][] quality) {
        for (var rank : edges) {
            Arrays.fill(rank, Double.NaN);
        }
        var floorIndex = Math.max(0, (int) Math.ceil(PolarGrid.indexAt(innerR + LIMB_EXCLUSION * radius, innerR, outerR)));
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            var lastValid = RADIAL_SAMPLES - 1;
            while (lastValid >= 0 && !valid[lastValid][it]) {
                lastValid--;
            }
            var topIndex = lastValid - CENSOR_MARGIN_SAMPLES;
            if (topIndex <= floorIndex) {
                continue;
            }
            var peak = 0.0;
            for (int ir = floorIndex; ir <= topIndex; ir++) {
                peak = Math.max(peak, excess[ir][it]);
            }
            if (peak < EDGE_PEAK_FACTOR * threshold) {
                continue;
            }
            var level = Math.max(threshold, EDGE_LEVEL * peak);
            var span = (int) Math.ceil(RimProfile.RIM_FEATHER * radius / PolarGrid.radialStep(innerR, outerR));
            var rank = 0;
            var ir = topIndex;
            if (excess[ir][it] < level) {
                while (rank < EDGE_RANKS && ir >= floorIndex) {
                    while (ir >= floorIndex && excess[ir][it] < level) {
                        ir--;
                    }
                    if (ir < floorIndex) {
                        break;
                    }
                    var outside = excess[Math.min(topIndex, ir + span)][it];
                    var inside = excess[Math.max(floorIndex, ir - span)][it];
                    if (inside > 0 && outside <= EDGE_SHARPNESS * inside) {
                        edges[rank][it] = PolarGrid.radiusAt(ir, innerR, outerR);
                        quality[rank][it] = Math.max(0, 1 - outside / inside);
                        rank++;
                    }
                    while (ir >= floorIndex && excess[ir][it] >= level) {
                        ir--;
                    }
                }
            }
            addSteepestEdges(excess, it, rank, floorIndex, topIndex, span, threshold, innerR, outerR, edges, quality);
        }
    }

    /**
     * Fills the remaining edge ranks of an azimuth with the steepest radial drops of the excess. A rim riding on top
     * of glow brighter than the crossing level produces no level crossing at all, but it remains the sharpest
     * brightness step of the profile.
     */
    private static void addSteepestEdges(double[][] excess, int it, int rank, int floorIndex, int topIndex, int span, double threshold, double innerR, double outerR, double[][] edges, double[][] quality) {
        var from = floorIndex + span;
        var to = topIndex - span;
        if (to <= from) {
            return;
        }
        var separation = span * PolarGrid.radialStep(innerR, outerR);
        var steps = new double[RADIAL_SAMPLES];
        for (int ir = from; ir <= to; ir++) {
            steps[ir] = excess[ir - span][it] - excess[ir + span][it];
        }
        while (rank < EDGE_RANKS) {
            var bestIr = -1;
            var bestStep = EDGE_PEAK_FACTOR * threshold;
            for (int ir = from; ir <= to; ir++) {
                if (steps[ir] <= bestStep || !isNeighborhoodPeak(steps, ir, from, to, span)) {
                    continue;
                }
                var r = PolarGrid.radiusAt(ir, innerR, outerR);
                var separated = true;
                for (int k = 0; k < rank; k++) {
                    if (!Double.isNaN(edges[k][it]) && Math.abs(edges[k][it] - r) <= separation) {
                        separated = false;
                        break;
                    }
                }
                if (separated) {
                    bestStep = steps[ir];
                    bestIr = ir;
                }
            }
            if (bestIr < 0) {
                break;
            }
            var inside = excess[bestIr - span][it];
            edges[rank][it] = PolarGrid.radiusAt(bestIr, innerR, outerR);
            quality[rank][it] = inside > 0 ? Math.max(0, 1 - excess[bestIr + span][it] / inside) : 0;
            rank++;
        }
    }

    /**
     * True when the drop at {@code ir} is the largest within one blur span around it, which excludes the tails of a
     * neighbouring stronger edge.
     */
    private static boolean isNeighborhoodPeak(double[] steps, int ir, int from, int to, int span) {
        for (int k = Math.max(from, ir - span); k <= Math.min(to, ir + span); k++) {
            if (steps[k] > steps[ir]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rank of the edge closest to the rim of the candidate at the given azimuth, or -1.
     */
    private static int nearestEdgeRank(double[][] edges, int azimuthBin, double rim) {
        var nearest = -1;
        var bestDistance = Double.POSITIVE_INFINITY;
        for (int rank = 0; rank < edges.length; rank++) {
            var value = edges[rank][azimuthBin];
            if (Double.isNaN(value)) {
                continue;
            }
            var distance = Math.abs(value - rim);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = rank;
            }
        }
        return nearest;
    }

    /**
     * Sum over the azimuths whose nearest edge lies on the candidate rim, weighted by edge quality, or a plain count
     * when no quality is given.
     */
    private static double support(GhostShift candidate, double[][] edges, double[][] quality, double innerR, double radius) {
        var support = 0.0;
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            var rim = candidate.rimAt(it, radius);
            if (rim <= innerR + LIMB_EXCLUSION * radius) {
                continue;
            }
            var rank = nearestEdgeRank(edges, it, rim);
            if (rank >= 0 && Math.abs(edges[rank][it] - rim) <= EDGE_TOLERANCE * radius) {
                support += quality == null ? 1 : quality[rank][it];
            }
        }
        return support;
    }

    /**
     * True when a constant-radius circle explains the edge locus of the crescent almost as well as the rim curve,
     * which is the signature of a band of background structure rather than a reflection.
     */
    private static boolean isRingLike(GhostShift candidate, double[] edge, int support, double innerR, double radius) {
        var crescent = new double[AZIMUTH_SAMPLES];
        var crescentCount = 0;
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            if (Double.isNaN(edge[it])) {
                continue;
            }
            if (candidate.rimAt(it, radius) > innerR + LIMB_EXCLUSION * radius) {
                crescent[crescentCount++] = edge[it];
            }
        }
        if (crescentCount == 0) {
            return true;
        }
        Arrays.sort(crescent, 0, crescentCount);
        var ringRadius = crescent[crescentCount / 2];
        var ringSupport = 0;
        for (int i = 0; i < crescentCount; i++) {
            if (Math.abs(crescent[i] - ringRadius) <= EDGE_TOLERANCE * radius) {
                ringSupport++;
            }
        }
        return support < RING_REJECTION * ringSupport;
    }

    /**
     * Refines a coarse candidate with a quality-weighted least-squares fit of the shifted-circle centre to the edge
     * locus, iterating with a shrinking outlier-rejection tolerance.
     */
    private static GhostShift refine(GhostShift candidate, double[][] edges, double[][] quality, double innerR, double radius) {
        var sx = candidate.amount() * Math.cos(candidate.directionRad());
        var sy = candidate.amount() * Math.sin(candidate.directionRad());
        var refined = candidate;
        for (int round = 0; round < REFINE_ROUNDS; round++) {
            var tolerance = (3.0 - round * 0.5) * EDGE_TOLERANCE * radius;
            var sxx = 0.0;
            var sxy = 0.0;
            var syy = 0.0;
            var bx = 0.0;
            var by = 0.0;
            var weightSum = 0.0;
            for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
                var rim = refined.rimAt(it, radius);
                if (rim <= innerR + LIMB_EXCLUSION * radius) {
                    continue;
                }
                var rank = nearestEdgeRank(edges, it, rim);
                if (rank < 0 || Math.abs(edges[rank][it] - rim) > tolerance) {
                    continue;
                }
                var weight = quality[rank][it];
                var th = PolarGrid.azimuthAt(it);
                var px = edges[rank][it] * Math.cos(th);
                var py = edges[rank][it] * Math.sin(th);
                var dx = px - sx;
                var dy = py - sy;
                var dist = Math.hypot(dx, dy);
                if (dist <= 0) {
                    continue;
                }
                var nx = dx / dist;
                var ny = dy / dist;
                var residual = dist - radius;
                sxx += weight * nx * nx;
                sxy += weight * nx * ny;
                syy += weight * ny * ny;
                bx += weight * nx * residual;
                by += weight * ny * residual;
                weightSum += weight;
            }
            if (weightSum < 0.6 * MIN_EDGE_SUPPORT) {
                break;
            }
            var det = sxx * syy - sxy * sxy;
            if (Math.abs(det) < 1e-9) {
                break;
            }
            sx += (syy * bx - sxy * by) / det;
            sy += (sxx * by - sxy * bx) / det;
            var amount = Math.hypot(sx, sy);
            if (amount < MIN_SHIFT * radius || amount > MAX_SHIFT * radius) {
                return refined;
            }
            refined = new GhostShift(amount, Math.atan2(sy, sx));
        }
        return refined;
    }

    /**
     * Local polish of the fitted shift: a small grid search around it maximising the brightness step actually
     * observed across the rim curve, which locks the rim onto the visible edge of the reflection.
     */
    private static GhostShift snap(double[][] smooth, double[][] edges, double[][] quality, GhostShift shift, List<GhostShift> exclusions, double innerR, double outerR, double radius) {
        var azimuthWeight = new double[AZIMUTH_SAMPLES];
        for (int it = 0; it < AZIMUTH_SAMPLES; it++) {
            for (int rank = 0; rank < edges.length; rank++) {
                if (!Double.isNaN(edges[rank][it])) {
                    azimuthWeight[it] = Math.max(azimuthWeight[it], quality[rank][it]);
                }
            }
        }
        var sx = shift.amount() * Math.cos(shift.directionRad());
        var sy = shift.amount() * Math.sin(shift.directionRad());
        var best = shift;
        var bestScore = Double.NEGATIVE_INFINITY;
        var probe = SNAP_PROBE * radius;
        for (int dy = -SNAP_RANGE; dy <= SNAP_RANGE; dy++) {
            for (int dx = -SNAP_RANGE; dx <= SNAP_RANGE; dx++) {
                var amount = Math.hypot(sx + dx, sy + dy);
                if (amount < MIN_SHIFT * radius || amount > MAX_SHIFT * radius) {
                    continue;
                }
                var candidate = new GhostShift(amount, Math.atan2(sy + dy, sx + dx));
                if (isExcluded(candidate, exclusions, radius)) {
                    continue;
                }
                var sum = 0.0;
                var weightSum = 0.0;
                for (int it = 0; it < AZIMUTH_SAMPLES; it += 2) {
                    if (azimuthWeight[it] <= 0) {
                        continue;
                    }
                    var rim = candidate.rimAt(it, radius);
                    if (rim <= innerR + LIMB_EXCLUSION * radius || rim + probe >= outerR) {
                        continue;
                    }
                    var inner = PolarGrid.samplePolar(smooth, PolarGrid.indexAt(rim - probe, innerR, outerR), it);
                    var outer = PolarGrid.samplePolar(smooth, PolarGrid.indexAt(rim + probe, innerR, outerR), it);
                    sum += azimuthWeight[it] * (inner - outer);
                    weightSum += azimuthWeight[it];
                }
                if (weightSum < 0.3 * MIN_EDGE_SUPPORT) {
                    continue;
                }
                var score = sum / weightSum;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static boolean isExcluded(GhostShift candidate, List<GhostShift> exclusions, double radius) {
        for (var excluded : exclusions) {
            var directionDistance = Math.abs(candidate.directionRad() - excluded.directionRad());
            directionDistance = Math.min(directionDistance, 2 * Math.PI - directionDistance);
            if (directionDistance < EXCLUSION_DIRECTION && Math.abs(candidate.amount() - excluded.amount()) < EXCLUSION_SHIFT * radius) {
                return true;
            }
        }
        return false;
    }
}
