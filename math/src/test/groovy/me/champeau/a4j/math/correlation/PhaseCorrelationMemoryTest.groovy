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
package me.champeau.a4j.math.correlation

import me.champeau.a4j.math.opencl.OpenCLSupport
import me.champeau.a4j.math.regression.DistortionGridFilter
import spock.lang.Requires
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Test to reproduce GPU memory leak in PhaseCorrelation.
 * Run with: ./gradlew :math:test --tests "PhaseCorrelationMemoryTest" -POPENCL_ENABLED=true
 */
@Requires({ OpenCLSupport.available })
class PhaseCorrelationMemoryTest extends Specification {

    def "should handle GPU operations on virtual threads without error"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }
        println "OpenCL device: ${context.capabilities.deviceName()}"
        println "Global memory: ${context.capabilities.globalMemSize() / (1024 * 1024)} MB"

        and: "tile data for phase correlation"
        int numTiles = 4096
        int tileSize = 64
        def refTiles = createRandomTiles(numTiles, tileSize)
        def targetTiles = createRandomTiles(numTiles, tileSize)

        and: "grid data for distortion filter"
        int gridWidth = 250
        int gridHeight = 250
        def gridDx = createRandomGrid(gridWidth, gridHeight)
        def gridDy = createRandomGrid(gridWidth, gridHeight)
        def sampled = createSampledMask(gridWidth, gridHeight)

        when: "we run GPU operations on virtual threads (like the real app)"
        def errorRef = new AtomicReference<Throwable>()
        def phaseCorr = PhaseCorrelation.getInstance()
        def gridFilter = DistortionGridFilter.getInstance()

        // This mimics how the real app runs - on virtual threads
        int numIterations = 15  // Should be enough to trigger the issue

        for (int i = 0; i < numIterations && errorRef.get() == null; i++) {
            def latch = new CountDownLatch(1)
            def iteration = i

            // Run on a virtual thread, just like the real application
            Thread.startVirtualThread {
                try {
                    println "Virtual thread iteration ${iteration + 1}/${numIterations} - Thread: ${Thread.currentThread()}"

                    // Phase correlation batch
                    def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
                    println "  PhaseCorrelation completed: ${results.length} results"

                    // Distortion grid filtering (mimics what happens after phase correlation)
                    def dxCopy = copyGrid(gridDx)
                    def dyCopy = copyGrid(gridDy)
                    def sampledCopy = copySampled(sampled)

                    gridFilter.interpolateUnsampled(dxCopy, dyCopy, sampledCopy, gridWidth, gridHeight, 3)
                    gridFilter.madFilter(dxCopy, dyCopy, gridWidth, gridHeight, 2, 3.0f)
                    gridFilter.gaussianSmooth(dxCopy, dyCopy, gridWidth, gridHeight, 1.5f)
                    println "  Grid filtering completed"

                } catch (Throwable t) {
                    println "ERROR in virtual thread: ${t.message}"
                    t.printStackTrace()
                    errorRef.set(t)
                } finally {
                    latch.countDown()
                }
            }

            // Wait for this iteration to complete before starting the next
            latch.await()

            // Small delay to allow thread unmounting/remounting
            Thread.sleep(10)
        }

        then: "no exception is thrown"
        errorRef.get() == null
    }

    def "should handle dedistort-like pattern with 6 batches then filtering"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }
        println "OpenCL device: ${context.capabilities.deviceName()}"
        println "Global memory: ${context.capabilities.globalMemSize() / (1024 * 1024)} MB"

        and: "tile data for phase correlation - exactly matching real app"
        int numTilesPerBatch = 4096
        int lastBatchSize = 3547  // Real app has smaller last batch
        int tileSize = 64

        and: "grid data for distortion filter"
        int gridWidth = 250
        int gridHeight = 250

        when: "we simulate multiple dedistort passes on virtual threads"
        def errorRef = new AtomicReference<Throwable>()
        def phaseCorr = PhaseCorrelation.getInstance()
        def gridFilter = DistortionGridFilter.getInstance()

        // Simulate multiple dedistort passes (each pass = 6 phase correlation batches + filtering)
        int numPasses = 5

        for (int pass = 0; pass < numPasses && errorRef.get() == null; pass++) {
            def latch = new CountDownLatch(1)
            def passNum = pass

            Thread.startVirtualThread {
                try {
                    println "=== Pass ${passNum + 1}/${numPasses} on ${Thread.currentThread()} ==="

                    // Create fresh data for each pass
                    def refTiles = createRandomTiles(numTilesPerBatch, tileSize)
                    def targetTiles = createRandomTiles(numTilesPerBatch, tileSize)
                    def smallRefTiles = createRandomTiles(lastBatchSize, tileSize)
                    def smallTargetTiles = createRandomTiles(lastBatchSize, tileSize)

                    // 5 full batches + 1 smaller batch (exactly like real app)
                    for (int batch = 0; batch < 5; batch++) {
                        def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
                        println "  Batch ${batch + 1}/6: ${results.length} results"
                    }
                    def lastResults = phaseCorr.batchedCorrelation(smallRefTiles, smallTargetTiles)
                    println "  Batch 6/6: ${lastResults.length} results"

                    // Now do grid filtering (where the error occurs in real app)
                    def gridDx = createRandomGrid(gridWidth, gridHeight)
                    def gridDy = createRandomGrid(gridWidth, gridHeight)
                    def sampled = createSampledMask(gridWidth, gridHeight)

                    println "  Starting grid filtering..."
                    gridFilter.interpolateUnsampled(gridDx, gridDy, sampled, gridWidth, gridHeight, 3)
                    println "    interpolateUnsampled completed"
                    gridFilter.madFilter(gridDx, gridDy, gridWidth, gridHeight, 2, 3.0f)
                    println "    madFilter completed"
                    gridFilter.gaussianSmooth(gridDx, gridDy, gridWidth, gridHeight, 1.5f)
                    println "    gaussianSmooth completed"

                    println "=== Pass ${passNum + 1} completed ==="
                } catch (Throwable t) {
                    println "ERROR in pass ${passNum + 1}: ${t.message}"
                    t.printStackTrace()
                    errorRef.set(t)
                } finally {
                    latch.countDown()
                }
            }

            latch.await()
        }

        then: "no exception is thrown"
        errorRef.get() == null
    }

    def "should handle many allocations like real app (640+ allocations)"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }
        println "OpenCL device: ${context.capabilities.deviceName()}"
        println "GPU Memory: ${context.capabilities.globalMemSize() / (1024 * 1024)} MB"

        and: "tile data"
        int numTilesPerBatch = 4096
        int lastBatchSize = 3547
        int tileSize = 64
        int gridWidth = 250
        int gridHeight = 250

        when: "we run until we exceed 640 allocations (like real app before failure)"
        def errorRef = new AtomicReference<Throwable>()
        def phaseCorr = PhaseCorrelation.getInstance()
        def gridFilter = DistortionGridFilter.getInstance()

        // The real app fails around 640 allocations
        // Each dedistort pass does: 6 batches × 7 buffers/batch = 42 PhaseCorrelation allocs
        // Plus ~12 DistortionGridFilter allocs = ~54 total per pass
        // To reach 640, we need ~12 passes
        int numPasses = 15

        for (int pass = 0; pass < numPasses && errorRef.get() == null; pass++) {
            def latch = new CountDownLatch(1)
            def passNum = pass

            Thread.startVirtualThread {
                try {
                    println "=== Pass ${passNum + 1}/${numPasses} (targeting 640+ allocs) ==="

                    // Create fresh data for each pass
                    def refTiles = createRandomTiles(numTilesPerBatch, tileSize)
                    def targetTiles = createRandomTiles(numTilesPerBatch, tileSize)
                    def smallRefTiles = createRandomTiles(lastBatchSize, tileSize)
                    def smallTargetTiles = createRandomTiles(lastBatchSize, tileSize)

                    // 5 full batches + 1 smaller batch
                    for (int batch = 0; batch < 5; batch++) {
                        def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
                        if (batch == 0) {
                            println "  Batch ${batch + 1}/6 completed"
                        }
                    }
                    phaseCorr.batchedCorrelation(smallRefTiles, smallTargetTiles)
                    println "  All 6 batches completed"

                    // Grid filtering
                    def gridDx = createRandomGrid(gridWidth, gridHeight)
                    def gridDy = createRandomGrid(gridWidth, gridHeight)
                    def sampled = createSampledMask(gridWidth, gridHeight)

                    gridFilter.interpolateUnsampled(gridDx, gridDy, sampled, gridWidth, gridHeight, 3)
                    gridFilter.madFilter(gridDx, gridDy, gridWidth, gridHeight, 2, 3.0f)
                    gridFilter.gaussianSmooth(gridDx, gridDy, gridWidth, gridHeight, 1.5f)
                    println "  Grid filtering completed"

                    // Add some IO operation to potentially trigger virtual thread unmounting
                    Thread.sleep(1)

                    println "=== Pass ${passNum + 1} completed | ${context.getMemoryStats()} ==="
                } catch (Throwable t) {
                    println "ERROR in pass ${passNum + 1}: ${t.message}"
                    t.printStackTrace()
                    errorRef.set(t)
                } finally {
                    latch.countDown()
                }
            }

            latch.await()
        }

        println "Final state: ${context.getMemoryStats()}"

        then: "no exception is thrown"
        errorRef.get() == null
    }

    def "should handle GPU operations across multiple virtual threads concurrently"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }
        println "OpenCL device: ${context.capabilities.deviceName()}"

        and: "tile data"
        int numTiles = 2048
        int tileSize = 64
        def refTiles = createRandomTiles(numTiles, tileSize)
        def targetTiles = createRandomTiles(numTiles, tileSize)

        when: "we run GPU operations from multiple virtual threads"
        def errorRef = new AtomicReference<Throwable>()
        def phaseCorr = PhaseCorrelation.getInstance()

        // Run multiple batches of work, each in separate virtual threads
        int numBatches = 10
        int iterationsPerBatch = 5

        for (int batch = 0; batch < numBatches && errorRef.get() == null; batch++) {
            def latch = new CountDownLatch(iterationsPerBatch)
            def batchNum = batch

            // Launch multiple virtual threads concurrently
            for (int i = 0; i < iterationsPerBatch; i++) {
                def iterNum = i
                Thread.startVirtualThread {
                    try {
                        println "Batch ${batchNum}, iteration ${iterNum} - Thread: ${Thread.currentThread()}"
                        def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
                        println "  Completed: ${results.length} results"
                    } catch (Throwable t) {
                        println "ERROR: ${t.message}"
                        t.printStackTrace()
                        errorRef.compareAndSet(null, t)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            println "Batch ${batch + 1}/${numBatches} completed"
        }

        then: "no exception is thrown"
        errorRef.get() == null
    }

    // Note: The concurrent virtual threads test was removed because PhaseCorrelation.flatten3D
    // creates internal copies of data, and concurrent operations exhaust Java heap.
    // The GPU lock ensures serialization anyway, so concurrent tests don't provide value.

    def "should handle repeated batched correlation without memory leak"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }
        println "OpenCL device: ${context.capabilities.deviceName()}"
        println "Global memory: ${context.capabilities.globalMemSize() / (1024 * 1024)} MB"
        println "Max alloc size: ${context.capabilities.maxMemAllocSize() / (1024 * 1024)} MB"

        and: "synthetic tile data - 4096 tiles of 64x64"
        int numTiles = 4096
        int tileSize = 64
        def refTiles = createRandomTiles(numTiles, tileSize)
        def targetTiles = createRandomTiles(numTiles, tileSize)

        when: "we run batched correlation multiple times"
        def phaseCorr = PhaseCorrelation.getInstance()
        int numIterations = 20  // Should trigger the leak after ~6 iterations based on logs

        for (int i = 0; i < numIterations; i++) {
            println "Iteration ${i + 1}/${numIterations}..."
            def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
            println "  Completed, got ${results.length} results"

            // Force GC to rule out Java heap issues
            System.gc()
        }

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should handle repeated small batches without memory leak"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }

        and: "smaller batch - 1024 tiles of 64x64"
        int numTiles = 1024
        int tileSize = 64
        def refTiles = createRandomTiles(numTiles, tileSize)
        def targetTiles = createRandomTiles(numTiles, tileSize)

        when: "we run many iterations"
        def phaseCorr = PhaseCorrelation.getInstance()
        int numIterations = 50

        for (int i = 0; i < numIterations; i++) {
            if (i % 10 == 0) {
                println "Iteration ${i + 1}/${numIterations}..."
            }
            def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
        }

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should handle tile size 32 without memory leak"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }

        and: "tiles of size 32x32"
        int numTiles = 4096
        int tileSize = 32
        def refTiles = createRandomTiles(numTiles, tileSize)
        def targetTiles = createRandomTiles(numTiles, tileSize)

        when: "we run many iterations"
        def phaseCorr = PhaseCorrelation.getInstance()
        int numIterations = 30

        for (int i = 0; i < numIterations; i++) {
            if (i % 5 == 0) {
                println "Iteration ${i + 1}/${numIterations}..."
            }
            def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
        }

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should handle PhaseCorrelation + DistortionGridFilter together without memory leak"() {
        given: "OpenCL is available"
        def context = OpenCLSupport.getContext()
        if (context == null || !OpenCLSupport.isEnabled()) {
            println "OpenCL not available, skipping test"
            return
        }
        def gpuMemoryMB = context.capabilities.globalMemSize() / (1024 * 1024)
        println "OpenCL device: ${context.capabilities.deviceName()}"
        println "GPU memory: ${gpuMemoryMB} MB"

        and: "tile data sized to use significant GPU memory"
        int numTiles = 4096
        int tileSize = 64
        // Each batch uses ~576 MB (7 buffers × complex floats × tiles × tileSize²)
        long memoryPerBatchMB = 576
        // Calculate how many batches to exceed GPU memory multiple times
        int batchesToExceedMemory = (int) Math.ceil(gpuMemoryMB / memoryPerBatchMB)
        int totalBatches = batchesToExceedMemory * 5  // Run 5x what should fill memory
        println "Memory per batch: ${memoryPerBatchMB} MB"
        println "Batches to exceed GPU memory: ${batchesToExceedMemory}"
        println "Total batches to run: ${totalBatches}"

        def refTiles = createRandomTiles(numTiles, tileSize)
        def targetTiles = createRandomTiles(numTiles, tileSize)

        and: "grid data for DistortionGridFilter"
        int gridWidth = 250
        int gridHeight = 250
        def gridDx = createRandomGrid(gridWidth, gridHeight)
        def gridDy = createRandomGrid(gridWidth, gridHeight)
        def sampled = createSampledMask(gridWidth, gridHeight)

        when: "we simulate the dedistort pipeline"
        def phaseCorr = PhaseCorrelation.getInstance()
        def gridFilter = me.champeau.a4j.math.regression.DistortionGridFilter.getInstance()

        long totalMemoryUsed = 0
        for (int batch = 0; batch < totalBatches; batch++) {
            // PhaseCorrelation batch
            def results = phaseCorr.batchedCorrelation(refTiles, targetTiles)
            totalMemoryUsed += memoryPerBatchMB
            println "Batch ${batch + 1}/${totalBatches}: PhaseCorrelation completed (total memory cycled: ${totalMemoryUsed} MB)"

            // Every 6 batches, run the grid filter (simulating end of dedistort pass)
            if ((batch + 1) % 6 == 0) {
                def dxCopy = copyGrid(gridDx)
                def dyCopy = copyGrid(gridDy)

                gridFilter.interpolateUnsampled(dxCopy, dyCopy, sampled, gridWidth, gridHeight, 3)
                gridFilter.madFilter(dxCopy, dyCopy, gridWidth, gridHeight, 2, 3.0f)
                gridFilter.gaussianSmooth(dxCopy, dyCopy, gridWidth, gridHeight, 1.5f)
                println "  Grid filtering completed"
            }
        }

        then: "no exception is thrown"
        noExceptionThrown()
    }

    private static float[][][] createRandomTiles(int numTiles, int tileSize) {
        def random = new Random(42)  // Fixed seed for reproducibility
        def tiles = new float[numTiles][tileSize][tileSize]
        for (int t = 0; t < numTiles; t++) {
            for (int y = 0; y < tileSize; y++) {
                for (int x = 0; x < tileSize; x++) {
                    tiles[t][y][x] = random.nextFloat() * 65535.0f
                }
            }
        }
        return tiles
    }

    private static float[][] createRandomGrid(int width, int height) {
        def random = new Random(42)
        def grid = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = (random.nextFloat() - 0.5f) * 10.0f  // Small displacements
            }
        }
        return grid
    }

    private static boolean[][] createSampledMask(int width, int height) {
        def random = new Random(42)
        def sampled = new boolean[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sampled[y][x] = random.nextFloat() > 0.3f  // 70% sampled
            }
        }
        return sampled
    }

    private static float[][] copyGrid(float[][] grid) {
        int height = grid.length
        int width = grid[0].length
        def copy = new float[height][width]
        for (int y = 0; y < height; y++) {
            System.arraycopy(grid[y], 0, copy[y], 0, width)
        }
        return copy
    }

    private static boolean[][] copySampled(boolean[][] sampled) {
        int height = sampled.length
        int width = sampled[0].length
        def copy = new boolean[height][width]
        for (int y = 0; y < height; y++) {
            System.arraycopy(sampled[y], 0, copy[y], 0, width)
        }
        return copy
    }
}
