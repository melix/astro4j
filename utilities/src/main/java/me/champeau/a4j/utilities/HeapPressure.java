/*
 * Copyright 2023-2026 the original author or authors.
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
package me.champeau.a4j.utilities;

import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM-wide heap pressure ratio cache. Returns
 * {@code (totalMemory - freeMemory) / totalMemory}, refreshed at most once
 * every {@value #REFRESH_INTERVAL_NS} ns. Lock-free fast path: a single
 * atomic read.
 *
 * <p>Heap pressure is a process-global signal — sharing one cached snapshot
 * across all callers avoids redundant {@code Runtime} probes when several
 * subsystems sample within the same window.
 */
public final class HeapPressure {
    private static final long REFRESH_INTERVAL_NS = 100_000_000L; // 100 ms

    private record Snapshot(double ratio, long takenAtNs) {
    }

    private static final AtomicReference<Snapshot> REF = new AtomicReference<>();

    private HeapPressure() {
    }

    /**
     * Current heap pressure ratio: {@code (totalMemory - freeMemory) / totalMemory}.
     * {@code 0} means the currently-committed heap is empty; {@code 1} means the
     * currently-committed heap is full and the GC is about to either reclaim or
     * commit more memory from the OS. Using committed (not max) as the denominator
     * keeps the signal meaningful across a wide range of host RAM sizes — on a
     * 128 GB box where {@code -Xmx} is set very high, {@code used/max} stays low
     * even when the JVM has already grabbed tens of GB from the OS.
     */
    public static double current() {
        var snap = REF.get();
        long now = System.nanoTime();
        if (snap != null && now - snap.takenAtNs() < REFRESH_INTERVAL_NS) {
            return snap.ratio();
        }
        var rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        double ratio = total == 0 ? 0.0 : (double) (total - rt.freeMemory()) / total;
        REF.lazySet(new Snapshot(ratio, now));
        return ratio;
    }
}
