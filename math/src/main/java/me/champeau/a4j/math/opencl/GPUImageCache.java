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
package me.champeau.a4j.math.opencl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;

/**
 * LRU cache for GPU-resident images.
 * <p>
 * This class manages a cache of images uploaded to GPU memory, automatically
 * evicting least recently used images when capacity is exceeded. It enables
 * efficient multi-image correlation by keeping frequently accessed images
 * on the GPU.
 * <p>
 * The cache itself is not thread-safe — confine each cache instance to a
 * single owner (typically the orchestrator running a sequence of GPU
 * operations against it).
 */
public final class GPUImageCache implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GPUImageCache.class);

    private final OpenCLContext context;
    private final int width;
    private final int height;
    private final int maxCachedImages;
    private final IntFunction<float[]> imageDataSupplier;
    private final LRUCache cache;

    private int cacheHits;
    private int cacheMisses;

    /**
     * Creates a new GPU image cache.
     *
     * @param context           the OpenCL context
     * @param maxCachedImages   maximum number of images to keep in GPU memory
     * @param width             image width in pixels
     * @param height            image height in pixels
     * @param imageDataSupplier function that provides flattened image data for a given index
     */
    public GPUImageCache(OpenCLContext context,
                         int maxCachedImages,
                         int width,
                         int height,
                         IntFunction<float[]> imageDataSupplier) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.maxCachedImages = Math.max(2, maxCachedImages); // Need at least 2 for pair correlation
        this.imageDataSupplier = imageDataSupplier;
        this.cache = new LRUCache(this.maxCachedImages);
    }

    /**
     * Gets the GPU buffer for an image, uploading it if necessary.
     * <p>
     * If the image is already cached, returns the existing buffer and marks
     * it as recently used. If not cached, uploads the image data to GPU,
     * potentially evicting the least recently used image if at capacity.
     *
     * @param imageIndex the index of the image
     * @return the GPU buffer handle
     */
    public long getImage(int imageIndex) {
        var entry = cache.get(imageIndex);
        if (entry != null) {
            cacheHits++;
            return entry.buffer;
        }

        cacheMisses++;
        return uploadImage(imageIndex);
    }

    /**
     * Checks if an image is currently cached.
     *
     * @param imageIndex the index of the image
     * @return true if the image is in the cache
     */
    public boolean isCached(int imageIndex) {
        return cache.containsKey(imageIndex);
    }

    /**
     * Returns the current number of cached images.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Returns the maximum cache capacity.
     */
    public int capacity() {
        return maxCachedImages;
    }

    /**
     * Returns cache hit count since creation.
     */
    public int getCacheHits() {
        return cacheHits;
    }

    /**
     * Returns cache miss count since creation.
     */
    public int getCacheMisses() {
        return cacheMisses;
    }

    /**
     * Returns the cache hit ratio (0.0 to 1.0).
     */
    public double getHitRatio() {
        int total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 0.0;
    }

    /**
     * Evicts all images from the cache, freeing GPU memory.
     */
    public void clear() {
        if (cache.isEmpty()) {
            return;
        }
        drainQueueBeforeRelease();
        for (var entry : cache.values()) {
            context.releaseBuffer(entry.buffer);
        }
        cache.clear();
    }

    /**
     * Replaces the GPU buffer for a cached image with a new buffer.
     * <p>
     * This is used during iterative refinement when images are warped on GPU.
     * The old buffer is released and replaced with the new buffer.
     * The new buffer ownership is transferred to the cache.
     *
     * @param imageIndex the index of the image to replace
     * @param newBuffer  the new GPU buffer handle (ownership transferred to cache)
     * @return true if the image was cached and replaced, false if not cached
     */
    public boolean replaceBuffer(int imageIndex, long newBuffer) {
        var entry = cache.get(imageIndex);
        if (entry == null) {
            return false;
        }

        drainQueueBeforeRelease();
        context.releaseBuffer(entry.buffer);

        cache.put(imageIndex, new CacheEntry(newBuffer, imageIndex));
        return true;
    }

    /**
     * Checks if a buffer can be replaced (i.e., the image is currently cached).
     *
     * @param imageIndex the index of the image
     * @return true if the image is cached and can be replaced
     */
    public boolean canReplaceBuffer(int imageIndex) {
        return cache.containsKey(imageIndex);
    }

    private long uploadImage(int imageIndex) {
        int bufferSize = width * height * Float.BYTES;
        long buffer = context.allocateBuffer(bufferSize, CL_MEM_READ_ONLY);

        try {
            var data = imageDataSupplier.apply(imageIndex);
            context.writeBuffer(buffer, data);
            cache.put(imageIndex, new CacheEntry(buffer, imageIndex));
            return buffer;
        } catch (Exception e) {
            // Clean up on failure
            context.releaseBuffer(buffer);
            throw e;
        }
    }

    @Override
    public void close() {
        if (cache.isEmpty()) {
            return;
        }
        try {
            drainQueueBeforeRelease();
        } catch (Exception e) {
            LOGGER.error("Failed to drain command queue before closing cache", e);
        }
        for (var entry : cache.values()) {
            try {
                context.releaseBuffer(entry.buffer);
            } catch (Exception e) {
                LOGGER.error("Failed to release buffer for image {}", entry.imageIndex, e);
            }
        }
        cache.clear();
    }

    /**
     * Waits for any in-flight GPU work referencing the cache's buffers to
     * complete before they are released. The OpenCL spec defers actual VRAM
     * destruction of a {@code clReleaseMemObject}'d buffer until queued
     * commands using it have finished; the host-side memory budget, however,
     * returns the bytes immediately. Without draining first, a concurrent
     * op can "acquire" those still-occupied bytes and trip
     * {@code CL_MEM_OBJECT_ALLOCATION_FAILURE} at the next {@code clFinish}.
     */
    private void drainQueueBeforeRelease() {
        context.finish();
    }

    @Override
    public String toString() {
        return String.format("GPUImageCache[size=%d/%d, hits=%d, misses=%d, hitRatio=%.2f]",
                cache.size(), maxCachedImages, cacheHits, cacheMisses, getHitRatio());
    }

    private record CacheEntry(long buffer, int imageIndex) {
    }

    /**
     * LRU cache using LinkedHashMap with access-order iteration.
     */
    private class LRUCache extends LinkedHashMap<Integer, CacheEntry> {
        private final int maxSize;

        LRUCache(int maxSize) {
            super(maxSize, 0.75f, true); // accessOrder=true for LRU
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CacheEntry> eldest) {
            if (size() > maxSize) {
                drainQueueBeforeRelease();
                context.releaseBuffer(eldest.getValue().buffer);
                return true;
            }
            return false;
        }
    }
}
