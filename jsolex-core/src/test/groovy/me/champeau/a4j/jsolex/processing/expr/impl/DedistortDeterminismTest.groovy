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
package me.champeau.a4j.jsolex.processing.expr.impl

import me.champeau.a4j.jsolex.processing.expr.stacking.ConsensusReference
import me.champeau.a4j.jsolex.processing.expr.stacking.SparseDistortionField
import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.MutableMap
import me.champeau.a4j.math.correlation.CorrelationTools
import me.champeau.a4j.math.opencl.OpenCLSupport
import spock.lang.Requires
import spock.lang.Specification

import java.util.concurrent.Executors

class DedistortDeterminismTest extends Specification {

    private static Broadcaster NO_OP_BROADCASTER = { event -> } as Broadcaster

    private static Dedistort createDedistort() {
        new Dedistort(Map.of(), NO_OP_BROADCASTER)
    }

    private static ImageWrapper32 createSyntheticImage(int width, int height, long seed) {
        var data = new float[height][width]
        var random = new Random(seed)
        var cx = width / 2.0
        var cy = height / 2.0
        var radius = Math.min(width, height) / 3.0

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var dx = x - cx
                var dy = y - cy
                var dist = Math.sqrt(dx * dx + dy * dy)
                var value = dist < radius ? (float) (1000 * (1 - dist / radius)) : 0f
                value += random.nextFloat() * 50
                data[y][x] = value
            }
        }
        new ImageWrapper32(width, height, data, MutableMap.of())
    }

    private static ImageWrapper32 createShiftedImage(ImageWrapper32 source, float shiftX, float shiftY) {
        var width = source.width()
        var height = source.height()
        var srcData = source.data()
        var data = new float[height][width]

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var sx = (int) Math.round(x - shiftX)
                var sy = (int) Math.round(y - shiftY)
                if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
                    data[y][x] = srcData[sy][sx]
                }
            }
        }
        new ImageWrapper32(width, height, data, MutableMap.of())
    }

    private static double imageChecksum(ImageWrapper32 img) {
        double sum = 0
        for (var row : img.data()) {
            for (var v : row) {
                sum += v
            }
        }
        sum
    }

    private static float[][] addNoise(float[][] data, int width, int height, long seed) {
        var random = new Random(seed)
        var result = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = data[y][x] + random.nextFloat() * 30
            }
        }
        result
    }

    private static List<ImageWrapper32> createConsensusImages(int width, int height) {
        var reference = createSyntheticImage(width, height, 42)
        (0..<10).collect { i ->
            var shifted = createShiftedImage(reference, (float) (i * 1.7 - 5), (float) (i * 0.9 - 3))
            var noisyData = addNoise(shifted.data(), width, height, 100 + i)
            var metadata = MutableMap.<Class<?>, Object> of()
            metadata.put(MetadataTable.class, new MetadataTable(Map.of(MetadataTable.FILE_NAME, String.format("image_%02d.ser", i))))
            new ImageWrapper32(width, height, noisyData, metadata)
        }
    }

    private static ImageWrapper32 createConsensusRef(int width, int height) {
        var reference = createSyntheticImage(width, height, 42)
        var refMetadata = MutableMap.<Class<?>, Object> of()
        refMetadata.put(ConsensusReference.class, ConsensusReference.INSTANCE)
        new ImageWrapper32(width, height, reference.data(), refMetadata)
    }

    private static List<List<Double>> runConsensusDedistort(int runs, List<ImageWrapper32> images, ImageWrapper32 consensusRef) {
        (1..runs).collect { run ->
            var dedistort = createDedistort()
            var args = new HashMap<String, Object>()
            args.put("ref", consensusRef)
            args.put("img", new ArrayList<>(images))
            args.put("ts", 64)
            args.put("sparse", true)
            args.put("iterations", 5)
            args.put("multiscale", true)
            var result = (List<ImageWrapper32>) dedistort.dedistort(args)
            result.collect { imageChecksum(it) }
        }
    }

    private static void assertDeterministic(List<List<Double>> checksums, int imageCount) {
        for (int i = 0; i < imageCount; i++) {
            var perImage = checksums.collect { it[i] }
            assert perImage.every { it == perImage[0] }: "Non-determinism: image $i checksums differ across ${checksums.size()} runs: $perImage"
        }
    }

    private static SparseDistortionField buildField(int width, int height, List<Map> samples, List<Integer> order) {
        var builder = SparseDistortionField.builder(width, height)
                .neighborsK(12)
                .baseTileSize(64)
                .useTileWeighting(false)
                .interpolationMethod(SparseDistortionField.InterpolationMethod.RBF_THIN_PLATE)
        for (int idx : order) {
            var s = samples[idx]
            builder.addSample(s.x as float, s.y as float, s.dx as float, s.dy as float, 64)
        }
        builder.build()
    }

    // ==================== CPU-only tests ====================

    def "SparseDistortionField query is order-independent"() {
        given: "a set of displacement samples"
        var random = new Random(42)
        int width = 256
        int height = 256
        int numSamples = 50

        var samples = (0..<numSamples).collect {
            [x: random.nextFloat() * width, y: random.nextFloat() * height,
             dx: (random.nextFloat() - 0.5f) * 10, dy: (random.nextFloat() - 0.5f) * 10]
        }

        when: "building sparse fields with samples in different orders"
        var indices = (0..<numSamples).toList()
        var shuffled = new ArrayList<>(indices)
        Collections.shuffle(shuffled, new Random(999))

        var map1 = buildField(width, height, samples, indices).toRegularGrid(32)
        var map2 = buildField(width, height, samples, shuffled).toRegularGrid(32)

        then: "both produce the same total distortion"
        map1.totalDistorsion() == map2.totalDistorsion()
    }

    def "CPU single image dedistort is deterministic"() {
        given:
        System.setProperty("opencl.enabled", "false")
        var reference = createSyntheticImage(256, 256, 42)
        var target = createShiftedImage(reference, 2.5f, -1.3f)

        when:
        var results = (1..3).collect {
            var dedistort = createDedistort()
            imageChecksum(dedistort.dedistort(reference, target, 64, 0.5, null, 3, true, true))
        }

        then:
        results.every { it == results[0] }

        cleanup:
        System.clearProperty("opencl.enabled")
    }

    def "CPU consensus dedistort is deterministic"() {
        given:
        System.setProperty("opencl.enabled", "false")
        int width = 512
        int height = 512
        var images = createConsensusImages(width, height)
        var consensusRef = createConsensusRef(width, height)

        when:
        var checksums = runConsensusDedistort(3, images, consensusRef)

        then:
        assertDeterministic(checksums, images.size())

        cleanup:
        System.clearProperty("opencl.enabled")
    }

    def "CPU concurrent dedistort is deterministic"() {
        given:
        System.setProperty("opencl.enabled", "false")
        var reference = createSyntheticImage(256, 256, 42)
        var targets = (1..4).collect { i ->
            createShiftedImage(reference, (float) (i * 1.5), (float) (-i * 0.8))
        }

        when:
        var allRunChecksums = (1..3).collect {
            var executor = Executors.newFixedThreadPool(4)
            try {
                var futures = targets.collect { target ->
                    executor.submit({
                        var dedistort = createDedistort()
                        imageChecksum(dedistort.dedistort(reference, target, 64, 0.5, null, 2, true, false))
                    } as java.util.concurrent.Callable<Double>)
                }
                futures.collect { it.get() }
            } finally {
                executor.shutdown()
            }
        }

        then:
        for (int i = 0; i < targets.size(); i++) {
            def checksums = allRunChecksums.collect { it[i] }
            assert checksums.every { it == checksums[0] }: "Image $i checksums differ: $checksums"
        }

        cleanup:
        System.clearProperty("opencl.enabled")
    }

    // ==================== GPU tests ====================

    @Requires({ OpenCLSupport.available })
    def "GPU and CPU NCC correlation produce close results"() {
        given: "identical tile data"
        var random = new Random(42)
        int tileSize = 32
        int numTiles = 200
        var refTiles = new float[numTiles][tileSize][tileSize]
        var targetTiles = new float[numTiles][tileSize][tileSize]
        for (int t = 0; t < numTiles; t++) {
            for (int y = 0; y < tileSize; y++) {
                for (int x = 0; x < tileSize; x++) {
                    refTiles[t][y][x] = random.nextFloat() * 1000
                    targetTiles[t][y][x] = refTiles[t][y][x] + (random.nextFloat() - 0.5f) * 100
                }
            }
        }

        when: "running NCC on CPU then GPU"
        System.setProperty("opencl.enabled", "false")
        var cpuResults = CorrelationTools.getInstance().batchedNCC(refTiles, targetTiles)

        System.setProperty("opencl.enabled", "true")
        var gpuResults = CorrelationTools.getInstance().batchedNCC(refTiles, targetTiles)

        then: "displacements and confidence agree within tolerance"
        int dxMismatches = 0
        int dyMismatches = 0
        int confMismatches = 0
        double maxDxDiff = 0
        double maxDyDiff = 0
        double maxConfDiff = 0
        for (int i = 0; i < numTiles; i++) {
            var dxDiff = Math.abs(cpuResults[i][0] - gpuResults[i][0])
            var dyDiff = Math.abs(cpuResults[i][1] - gpuResults[i][1])
            var confDiff = Math.abs(cpuResults[i][2] - gpuResults[i][2])
            maxDxDiff = Math.max(maxDxDiff, dxDiff)
            maxDyDiff = Math.max(maxDyDiff, dyDiff)
            maxConfDiff = Math.max(maxConfDiff, confDiff)
            if (dxDiff > 0.5f) { dxMismatches++ }
            if (dyDiff > 0.5f) { dyMismatches++ }
            if (confDiff > 0.05f) { confMismatches++ }
        }
        // Displacements: at least 95% within 0.5px
        assert dxMismatches < numTiles * 0.05: "$dxMismatches of $numTiles tiles have dx > 0.5px (max=$maxDxDiff)"
        assert dyMismatches < numTiles * 0.05: "$dyMismatches of $numTiles tiles have dy > 0.5px (max=$maxDyDiff)"
        // Confidence: at least 90% within 0.05
        assert confMismatches < numTiles * 0.10: "$confMismatches of $numTiles tiles have confidence diff > 0.05 (max=$maxConfDiff)"

        cleanup:
        System.clearProperty("opencl.enabled")
    }

    @Requires({ OpenCLSupport.available })
    def "GPU NCC correlation is deterministic"() {
        given:
        System.setProperty("opencl.enabled", "true")
        var random = new Random(42)
        int tileSize = 32
        int numTiles = 200
        var refTiles = new float[numTiles][tileSize][tileSize]
        var targetTiles = new float[numTiles][tileSize][tileSize]
        for (int t = 0; t < numTiles; t++) {
            for (int y = 0; y < tileSize; y++) {
                for (int x = 0; x < tileSize; x++) {
                    refTiles[t][y][x] = random.nextFloat() * 1000
                    targetTiles[t][y][x] = refTiles[t][y][x] + (random.nextFloat() - 0.5f) * 100
                }
            }
        }

        when:
        var tools = CorrelationTools.getInstance()
        var results = (1..5).collect {
            var displacements = tools.batchedNCC(refTiles, targetTiles)
            double checksum = 0
            for (var d : displacements) {
                checksum += d[0] + d[1] * 31 + d[2] * 997
            }
            checksum
        }

        then:
        results.every { it == results[0] }

        cleanup:
        System.clearProperty("opencl.enabled")
    }

    @Requires({ OpenCLSupport.available })
    def "GPU single image dedistort is deterministic"() {
        given:
        System.setProperty("opencl.enabled", "true")
        var reference = createSyntheticImage(256, 256, 42)
        var target = createShiftedImage(reference, 2.5f, -1.3f)

        when:
        var results = (1..3).collect {
            var dedistort = createDedistort()
            imageChecksum(dedistort.dedistort(reference, target, 64, 0.5, null, 3, true, true))
        }

        then:
        results.every { it == results[0] }

        cleanup:
        System.clearProperty("opencl.enabled")
    }

    @Requires({ OpenCLSupport.available })
    def "GPU consensus dedistort is deterministic"() {
        given:
        System.setProperty("opencl.enabled", "true")
        int width = 512
        int height = 512
        var images = createConsensusImages(width, height)
        var consensusRef = createConsensusRef(width, height)

        when:
        var checksums = runConsensusDedistort(3, images, consensusRef)

        then:
        assertDeterministic(checksums, images.size())

        cleanup:
        System.clearProperty("opencl.enabled")
    }
}
