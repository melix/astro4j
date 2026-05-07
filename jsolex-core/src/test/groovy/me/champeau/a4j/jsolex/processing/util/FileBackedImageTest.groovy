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
package me.champeau.a4j.jsolex.processing.util

import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

class FileBackedImageTest extends Specification {

    def cleanup() {
        FileBackedImage.setPressureSupplierForTesting(null)
    }

    def "round-trip preserves mono pixel data exactly through forced flush"() {
        given: "a real FITS image loaded from test resources"
        def src = new File(getClass().getResource("/average/Ha/14_38_01.ser-average.fits").file)
        def original = (ImageWrapper32) FitsUtils.readFitsFile(src)
        def originalCopy = copyMonoData(original.data())

        and: "an FBI in pressure mode so the constructor flushes immediately"
        FileBackedImage.setPressureSupplierForTesting({ true } as BooleanSupplier)
        def fbi = FileBackedImage.wrap(original)

        when: "the soft reference is cleared (data should already be on disk)"
        fbi.clearSoftReferenceForTesting()
        def recovered = (ImageWrapper32) fbi.unwrapToMemory()

        then: "every pixel matches"
        recovered.width() == original.width()
        recovered.height() == original.height()
        floatArraysEqual(recovered.data(), originalCopy)
    }

    def "round-trip preserves RGB pixel data exactly through forced flush"() {
        given: "a synthetic RGB image with deterministic per-channel values"
        def width = 320
        def height = 240
        def r = synthesise(width, height, 0)
        def g = synthesise(width, height, 1000)
        def b = synthesise(width, height, 2000)
        def metadata = [:] as Map<Class<?>, Object>
        def rgb = new RGBImage(width, height, r, g, b, metadata)
        def rCopy = copyMonoData(r)
        def gCopy = copyMonoData(g)
        def bCopy = copyMonoData(b)

        and: "an FBI under pressure"
        FileBackedImage.setPressureSupplierForTesting({ true } as BooleanSupplier)
        def fbi = FileBackedImage.wrap(rgb)

        when:
        fbi.clearSoftReferenceForTesting()
        def recovered = (RGBImage) fbi.unwrapToMemory()

        then: "all three channels match the originals"
        recovered.width() == width
        recovered.height() == height
        floatArraysEqual(recovered.r(), rCopy)
        floatArraysEqual(recovered.g(), gCopy)
        floatArraysEqual(recovered.b(), bCopy)
    }

    def "comfortable heap keeps data in memory: unwrap returns the in-memory copy without writing to disk"() {
        given: "an FBI built under comfortable heap (no constructor flush)"
        FileBackedImage.setPressureSupplierForTesting({ false } as BooleanSupplier)
        def width = 200
        def height = 150
        def data = synthesise(width, height, 42)
        def img = new ImageWrapper32(width, height, data, [:] as Map<Class<?>, Object>)
        def fbi = FileBackedImage.wrap(img)

        when: "data is unwrapped without forcing eviction"
        def recovered = (ImageWrapper32) fbi.unwrapToMemory()

        then: "data is recovered from memory"
        floatArraysEqual(recovered.data(), data)

        and: "no disk write happened — comfortable heap means we keep things in RAM"
        !statusSaved(fbi)
    }

    def "repeated unwrap calls under comfortable heap stay in memory"() {
        given: "an FBI under comfortable heap"
        FileBackedImage.setPressureSupplierForTesting({ false } as BooleanSupplier)
        def width = 100
        def height = 80
        def data = synthesise(width, height, 7)
        def img = new ImageWrapper32(width, height, data, [:] as Map<Class<?>, Object>)
        def fbi = FileBackedImage.wrap(img)

        when: "unwrapToMemory is invoked repeatedly"
        ImageWrapper32 recovered = null
        for (int i = 0; i < 10; i++) {
            recovered = (ImageWrapper32) fbi.unwrapToMemory()
        }

        then: "data is still intact"
        floatArraysEqual(recovered.data(), data)

        and: "no disk write ever happened"
        !statusSaved(fbi)
    }

    def "pressure flip from comfortable to tight evicts to disk on next soft-reference clear"() {
        given: "an FBI built comfortable (no flush in constructor)"
        FileBackedImage.setPressureSupplierForTesting({ false } as BooleanSupplier)
        def width = 150
        def height = 120
        def data = synthesise(width, height, 99)
        def img = new ImageWrapper32(width, height, data, [:] as Map<Class<?>, Object>)
        def fbi = FileBackedImage.wrap(img)
        assert !statusSaved(fbi)

        when: "pressure flips to tight and the soft ref is cleared"
        FileBackedImage.setPressureSupplierForTesting({ true } as BooleanSupplier)
        fbi.clearSoftReferenceForTesting()
        def recovered = (ImageWrapper32) fbi.unwrapToMemory()

        then: "the file was written and the recovery returns identical data"
        statusSaved(fbi)
        floatArraysEqual(recovered.data(), data)
    }

    def "concurrent unwrapToMemory after flush returns equivalent data"() {
        given: "an FBI flushed to disk"
        FileBackedImage.setPressureSupplierForTesting({ true } as BooleanSupplier)
        def width = 256
        def height = 256
        def data = synthesise(width, height, 17)
        def img = new ImageWrapper32(width, height, data, [:] as Map<Class<?>, Object>)
        def fbi = FileBackedImage.wrap(img)
        fbi.clearSoftReferenceForTesting()

        when: "many threads concurrently unwrap"
        def threads = 8
        def latch = new CountDownLatch(threads)
        def start = new CountDownLatch(1)
        def results = Collections.synchronizedList(new ArrayList<float[][]>())
        def errors = Collections.synchronizedList(new ArrayList<Throwable>())
        def pool = Executors.newFixedThreadPool(threads)
        threads.times {
            pool.submit({
                try {
                    start.await()
                    def r = (ImageWrapper32) fbi.unwrapToMemory()
                    results.add(r.data())
                } catch (Throwable t) {
                    errors.add(t)
                } finally {
                    latch.countDown()
                }
            })
        }
        start.countDown()
        latch.await(10, TimeUnit.SECONDS)
        pool.shutdownNow()

        then: "no errors, every result matches the original"
        errors.isEmpty()
        results.size() == threads
        results.every { floatArraysEqual(it, data) }
    }

    private static boolean statusSaved(FileBackedImage fbi) {
        // Reach into the package-private status indirectly: unwrapToMemory's
        // disk-read path waits on status.saved(). We use a side-effect:
        // if the file exists with non-zero size, a flush completed.
        def field = FileBackedImage.class.getDeclaredField("backingFile")
        field.setAccessible(true)
        def path = (java.nio.file.Path) field.get(fbi)
        return java.nio.file.Files.exists(path) && java.nio.file.Files.size(path) > 0
    }

    private static float[][] synthesise(int width, int height, int seed) {
        def random = new Random(seed)
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = 1000.0f + random.nextFloat() * 60000.0f
            }
        }
        return data
    }

    private static float[][] copyMonoData(float[][] src) {
        def copy = new float[src.length][src[0].length]
        for (int y = 0; y < src.length; y++) {
            System.arraycopy(src[y], 0, copy[y], 0, src[y].length)
        }
        return copy
    }

    private static boolean floatArraysEqual(float[][] a, float[][] b) {
        if (a.length != b.length) {
            return false
        }
        for (int y = 0; y < a.length; y++) {
            if (!Arrays.equals(a[y], b[y])) {
                return false
            }
        }
        return true
    }
}
