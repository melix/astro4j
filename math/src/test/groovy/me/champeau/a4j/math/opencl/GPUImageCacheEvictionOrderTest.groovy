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

import java.lang.reflect.Method
import java.util.function.IntFunction

/**
 * Regression test for the eviction-ordering bug in {@link GPUImageCache}.
 *
 * Background: {@link OpenCLContext#releaseBuffer} returns the buffer's
 * bytes to {@link GpuMemoryBudget} immediately, but {@code clReleaseMemObject}
 * only defers actual VRAM destruction until queued commands referencing
 * the buffer have completed (OpenCL spec). On NVIDIA, {@code clCreateBuffer}
 * is also lazy — physical VRAM is committed on first kernel touch.
 *
 * <p>Combined: if {@link GPUImageCache#removeEldestEntry} releases an
 * evicted buffer while a kernel that uses it is still in flight on the
 * shared command queue, the host's budget accounting reports the bytes
 * as free even though the device still holds them. A concurrent op that
 * acquires those "free" bytes and tries to materialise its own buffer
 * can fail with {@code CL_MEM_OBJECT_ALLOCATION_FAILURE} at the next
 * {@code clFinish}.
 *
 * <p>The fix: drain the queue ({@code context.finish()}) before any
 * cache-driven {@code releaseBuffer}, so by the time the budget bytes
 * are returned, the device has actually released them.
 *
 * <p>This test pins the invariant via an observable counter: every
 * cache eviction must produce at least one {@code clFinish} call on
 * the underlying context. The test uses a private context (not the
 * shared one) so concurrent test classes can't perturb the counter.
 */
@Requires({ OpenCLSupport.isAvailable() })
class GPUImageCacheEvictionOrderTest extends Specification {

    private OpenCLContext ctx

    def setup() {
        ctx = createPrivateContext()
    }

    def cleanup() {
        if (ctx != null) {
            ctx.close()
        }
    }

    private static OpenCLContext createPrivateContext() {
        Method create = OpenCLContext.class.getDeclaredMethod("create")
        create.setAccessible(true)
        return (OpenCLContext) create.invoke(null)
    }

    def "eviction drains the command queue before releasing the evicted buffer"() {
        given: "a cache with capacity 2 and three distinct images to upload"
        int width = 64
        int height = 64
        IntFunction<float[]> supplier = { int idx ->
            var data = new float[width * height]
            for (int i = 0; i < data.length; i++) {
                data[i] = idx * 1_000_000f + i
            }
            return data
        } as IntFunction<float[]>
        var cache = new GPUImageCache(ctx, 2, width, height, supplier)

        and: "the first two uploads fill the cache without evicting"
        cache.getImage(0)
        cache.getImage(1)
        int finishesBeforeEviction = ctx.getFinishCount()

        when: "the third upload exceeds capacity and must evict the LRU entry"
        cache.getImage(2)

        then: "the eviction path invoked context.finish() to drain in-flight work referencing the evicted buffer before releasing it"
        ctx.getFinishCount() > finishesBeforeEviction

        cleanup:
        cache.close()
    }

    def "replaceBuffer drains the command queue before releasing the previous buffer"() {
        given:
        int width = 64
        int height = 64
        IntFunction<float[]> supplier = { int idx ->
            return new float[width * height]
        } as IntFunction<float[]>
        var cache = new GPUImageCache(ctx, 4, width, height, supplier)
        cache.getImage(0)

        and: "a freshly allocated replacement buffer"
        long replacement = ctx.allocateBuffer(width * height * Float.BYTES,
                org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY)
        int finishesBeforeReplace = ctx.getFinishCount()

        when:
        cache.replaceBuffer(0, replacement)

        then: "the previous buffer's release was preceded by a finish()"
        ctx.getFinishCount() > finishesBeforeReplace

        cleanup:
        cache.close()
    }
}
