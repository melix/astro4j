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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

import java.time.ZoneOffset
import java.time.ZonedDateTime

class PeriodicErrorCorrectionTest extends Specification {
    private static final int SIZE = 512
    private static final double RADIUS = 160
    private static final double CENTER = SIZE / 2.0d
    private static final int SCANS = 40
    private static final double CADENCE = 38
    private static final double DURATION = 35
    private static final double PERIOD = 431
    private static final double FRAME_RATE = (SIZE - 1) / DURATION
    private static final double OMEGA = 2 * Math.PI / PERIOD
    private static final ZonedDateTime FIRST_SCAN = ZonedDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC)

    private static PeriodicErrorCorrection correction() {
        def context = new HashMap<Class<?>, Object>()
        new PeriodicErrorCorrection(context, Broadcaster.NO_OP, new EllipseFit(context, Broadcaster.NO_OP))
    }

    def "the distortion of a series of scans is removed, inside the disk as well as at the limb, with a #description period"() {
        given: "scans whose content drifts along the scanning direction with a periodic tracking error"
        def scans = (0..<SCANS).collect { distortedScan(it) }
        def truth = trueImage()

        when:
        def corrected = (List<ImageWrapper>) correction().correctPeriodicError(arguments(scans, period))

        then: "the corrected scans are much closer to the undistorted disk"
        def before = scans.collect { rmsInsideDisk(it, truth) }
        def after = corrected.collect { rmsInsideDisk((ImageWrapper32) it.unwrapToMemory(), truth) }
        after.sum() < before.sum() / 4

        and: "the images keep their metadata"
        corrected.every { it.findMetadata(SourceInfo).present }

        where:
        description | period
        "given"     | PERIOD
        "searched"  | 0
    }

    def "images without scan timing are left unchanged"() {
        given:
        def scans = (0..<SCANS).collect { new ImageWrapper32(SIZE, SIZE, trueImage(), [(Ellipse): circle()] as Map<Class<?>, Object>) }

        when:
        def result = correction().correctPeriodicError([img: scans])

        then:
        result.is(scans)
    }

    def "too few scans leave the images unchanged"() {
        given:
        def scans = (0..<3).collect { distortedScan(it) }

        when:
        def result = correction().correctPeriodicError([img: scans])

        then:
        result.is(scans)
    }

    private static Map<String, Object> arguments(List<ImageWrapper32> scans, double period) {
        period > 0 ? [img: scans, period: period] : [img: scans]
    }

    /**
     * The tracking error of the test series: the content of the scan is displaced by
     * this many frames at a given time, so the displacement over a scan is curved.
     */
    private static double drift(double time) {
        def amplitudes = [0.02, 0.03, 0.03]
        def phases = [0.3, 1.1, 2.0]
        double value = 0
        for (int j = 1; j <= amplitudes.size(); j++) {
            value += FRAME_RATE * amplitudes[j - 1] * Math.sin(j * OMEGA * time + phases[j - 1]) / (j * OMEGA)
        }
        value
    }

    /**
     * The drift of a scan without its affine part over the limb, which the geometry
     * correction of the processing removes.
     */
    private static Closure<Double> residualDrift(int index) {
        def start = index * CADENCE
        def frames = (0..<90).collect { CENTER + RADIUS * Math.sin(2 * Math.PI * it / 90) }
        def values = frames.collect { drift(start + it / FRAME_RATE) }
        def meanFrame = frames.sum() / frames.size()
        def meanValue = values.sum() / values.size()
        double covariance = 0, variance = 0
        frames.indices.each {
            covariance += (frames[it] - meanFrame) * (values[it] - meanValue)
            variance += (frames[it] - meanFrame) * (frames[it] - meanFrame)
        }
        def slope = covariance / variance
        def offset = meanValue - slope * meanFrame
        return { double frame -> drift(start + frame / FRAME_RATE) - offset - slope * frame }
    }

    private static ImageWrapper32 distortedScan(int index) {
        def shift = residualDrift(index)
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            def sourceY = y - shift(y)
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) value(x, sourceY)
            }
        }
        def metadata = [
                (SourceInfo)     : new SourceInfo("scan${index}.ser", "day", FIRST_SCAN.plusNanos((long) (index * CADENCE * 1e9)), SIZE, SIZE, DURATION),
                (ReferenceCoords): new ReferenceCoords([]),
                (Ellipse)        : circle()
        ] as Map<Class<?>, Object>
        new ImageWrapper32(SIZE, SIZE, data, metadata)
    }

    private static float[][] trueImage() {
        def data = new float[SIZE][SIZE]
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                data[y][x] = (float) value(x, y)
            }
        }
        data
    }

    private static double value(double x, double y) {
        def r = Math.hypot(x - CENTER, y - CENTER)
        def inner = Math.min(r, RADIUS) / RADIUS
        def mu = Math.sqrt(1 - inner * inner)
        def texture = 1 + 0.15 * mu * mu * Math.cos(2 * Math.PI * y / 23) * Math.cos(2 * Math.PI * x / 31)
        def edge = 1 / (1 + Math.exp((r - RADIUS) / 0.8))
        0.02 + (0.4 + 0.6 * mu) * texture * edge
    }

    private static Ellipse circle() {
        Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -2 * CENTER, -2 * CENTER, CENTER * CENTER * 2 - RADIUS * RADIUS))
    }

    private static double rmsInsideDisk(ImageWrapper32 image, float[][] truth) {
        double sum = 0
        int count = 0
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (Math.hypot(x - CENTER, y - CENTER) < RADIUS + 4) {
                    def diff = image.data()[y][x] - truth[y][x]
                    sum += diff * diff
                    count++
                }
            }
        }
        Math.sqrt(sum / count)
    }
}
