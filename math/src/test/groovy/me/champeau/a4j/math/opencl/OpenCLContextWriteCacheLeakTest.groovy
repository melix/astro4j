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
package me.champeau.a4j.math.opencl

import spock.lang.Requires
import spock.lang.Specification

import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.stream.IntStream

import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE

/**
 * Regression test for the native-memory leak that used to live in
 * {@link OpenCLContext#writeBuffer(long, float[])}.
 *
 * The old implementation cached a per-thread {@code FloatBuffer} in a
 * {@code ThreadLocal} and registered every allocation in a context-global
 * {@code Set<FloatBuffer>} that was only drained on context close. Because
 * the codebase fires GPU writes from virtual-thread-per-task executors
 * (BatchProcessingHelper, SolexVideoProcessor), every fresh wave of virtual
 * threads on a new batch produced a fresh wave of leaked 256 KB+ native
 * buffers — invisible to the 5.2.1 batch-memory fix, which only touches
 * Java-heap caches.
 *
 * The current implementation uses a small bounded pool (capacity
 * {@code WRITE_BUFFER_POOL_SIZE}) so the native footprint is independent of
 * the number of threads that ever called {@code writeBuffer}. This test
 * pins the two contracts: no {@code ThreadLocal} field, and the pool stays
 * bounded under arbitrary thread fan-out.
 */
@Requires({ OpenCLSupport.isAvailable() })
class OpenCLContextWriteCacheLeakTest extends Specification {

    private OpenCLContext context

    def setup() {
        context = OpenCLSupport.getContext()
    }

    private static int poolSize(OpenCLContext context) {
        var field = OpenCLContext.class.getDeclaredField("writeBufferPool")
        field.setAccessible(true)
        return ((java.util.Queue<FloatBuffer>) field.get(context)).size()
    }

    private static int poolCapacity() {
        var field = OpenCLContext.class.getDeclaredField("WRITE_BUFFER_POOL_SIZE")
        field.setAccessible(true)
        return (int) field.get(null)
    }

    private static int maxPoolEntryCapacity() {
        var field = OpenCLContext.class.getDeclaredField("WRITE_BUFFER_MAX_CAPACITY")
        field.setAccessible(true)
        return (int) field.get(null)
    }

    private static int largestPooledBufferCapacity(OpenCLContext context) {
        var field = OpenCLContext.class.getDeclaredField("writeBufferPool")
        field.setAccessible(true)
        var pool = (java.util.Queue<FloatBuffer>) field.get(context)
        return pool.stream().mapToInt(FloatBuffer::capacity).max().orElse(0)
    }

    def "OpenCLContext does not retain any ThreadLocal-keyed write cache"() {
        when:
        var threadLocalFields = OpenCLContext.class.getDeclaredFields().findAll {
            ThreadLocal.class.isAssignableFrom(it.type)
        }

        then: "no ThreadLocal field exists — the per-thread leak vector is gone"
        threadLocalFields.isEmpty()
    }

    def "writeBuffer scratch pool stays bounded under heavy virtual-thread fan-out"() {
        given: "a buffer big enough for the payload"
        int floats = 1024
        long buffer = context.allocateBuffer(floats * Float.BYTES, CL_MEM_READ_WRITE)
        var payload = new float[floats]
        for (int i = 0; i < floats; i++) {
            payload[i] = i
        }
        int cap = poolCapacity()

        when: "many more virtual threads call writeBuffer than the pool's capacity"
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, cap * 8).forEach { idx ->
                executor.submit({ context.writeBuffer(buffer, payload) })
            }
        }

        then: "the pool retains at most WRITE_BUFFER_POOL_SIZE scratch buffers — everything else was freed back to the native allocator"
        poolSize(context) <= cap

        cleanup:
        context.releaseBuffer(buffer)
    }

    def "a one-off giant upload does not poison the pool with a permanently retained large buffer"() {
        given: "a payload bigger than the pool's per-entry cap"
        int giantFloats = maxPoolEntryCapacity() * 4
        long buffer = context.allocateBuffer(giantFloats * Float.BYTES, CL_MEM_READ_WRITE)
        var giantPayload = new float[giantFloats]
        int cap = maxPoolEntryCapacity()

        when: "we issue the giant upload"
        context.writeBuffer(buffer, giantPayload)

        then: "the giant scratch buffer was freed immediately, not retained in the pool"
        largestPooledBufferCapacity(context) <= cap

        cleanup:
        context.releaseBuffer(buffer)
    }

    def "two back-to-back virtual-thread batches do not accumulate scratch buffers across the boundary"() {
        given:
        int floats = 1024
        long buffer = context.allocateBuffer(floats * Float.BYTES, CL_MEM_READ_WRITE)
        var payload = new float[floats]
        int cap = poolCapacity()

        when: "batch 1 then batch 2 — each spawning its own pool of virtual threads"
        Closure batch = {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                IntStream.range(0, 64).forEach { idx ->
                    executor.submit({ context.writeBuffer(buffer, payload) })
                }
            }
        }
        batch()
        int afterBatch1 = poolSize(context)
        batch()
        int afterBatch2 = poolSize(context)

        then: "neither batch left more than the pool's capacity behind, and batch 2 did not grow the pool further"
        afterBatch1 <= cap
        afterBatch2 <= cap

        cleanup:
        context.releaseBuffer(buffer)
    }

    def "writeBuffer preserves data integrity when a pooled scratch buffer is reused for a smaller payload"() {
        given: "two buffers of different sizes and matching payloads with distinct fingerprints"
        int big = 4096
        int small = 256
        long bigDev = context.allocateBuffer(big * Float.BYTES, CL_MEM_READ_WRITE)
        long smallDev = context.allocateBuffer(small * Float.BYTES, CL_MEM_READ_WRITE)
        var bigPayload = new float[big]
        for (int i = 0; i < big; i++) {
            bigPayload[i] = 1000f + i
        }
        var smallPayload = new float[small]
        for (int i = 0; i < small; i++) {
            smallPayload[i] = -1f - i
        }

        when: "we upload the big payload, then the small payload (which will reuse the same pooled scratch buffer)"
        context.writeBuffer(bigDev, bigPayload)
        context.writeBuffer(smallDev, smallPayload)

        and: "we read both buffers back from the device"
        var bigReadback = new float[big]
        var smallReadback = new float[small]
        context.readBuffer(bigDev, bigReadback)
        context.readBuffer(smallDev, smallReadback)

        then: "the small upload was not contaminated by stale data from the pooled scratch buffer"
        bigReadback == bigPayload
        smallReadback == smallPayload

        cleanup:
        context.releaseBuffer(bigDev)
        context.releaseBuffer(smallDev)
    }

    def "writeBuffer preserves data across many concurrent virtual-thread uploads with distinct payloads"() {
        given: "N device buffers, each filled with a distinct, recognisable payload"
        int n = 64
        int floats = 1024
        long[] devBuffers = new long[n]
        float[][] payloads = new float[n][floats]
        for (int t = 0; t < n; t++) {
            devBuffers[t] = context.allocateBuffer(floats * Float.BYTES, CL_MEM_READ_WRITE)
            for (int i = 0; i < floats; i++) {
                payloads[t][i] = t * 10000f + i
            }
        }

        when: "all uploads run concurrently from virtual threads, racing on the pooled scratch buffers"
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, n).forEach { idx ->
                executor.submit({ context.writeBuffer(devBuffers[idx], payloads[idx]) })
            }
        }

        then: "every buffer reads back exactly the payload it was given — no cross-contamination"
        for (int t = 0; t < n; t++) {
            var readback = new float[floats]
            context.readBuffer(devBuffers[t], readback)
            assert readback == payloads[t]
        }

        cleanup:
        for (int t = 0; t < n; t++) {
            context.releaseBuffer(devBuffers[t])
        }
    }
}
