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
package me.champeau.a4j.jsolex.processing.util;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Helpers to opt streams into parallel execution only when the heap
 * has enough headroom. Use these on streams whose per-element body
 * allocates large intermediate buffers (e.g. full-image float[][]).
 * <p>
 * Under heap pressure (free heap below 20% of total committed heap)
 * the returned stream is sequential, avoiding the multiplicative
 * memory cost of parallel allocation when it can't be afforded.
 */
public final class MemoryAwareStreams {

    private static final double PRESSURE_THRESHOLD = 0.2;

    private MemoryAwareStreams() {
    }

    public static IntStream maybeParallel(IntStream stream) {
        return underPressure() ? stream : stream.parallel();
    }

    public static <T> Stream<T> maybeParallel(Stream<T> stream) {
        return underPressure() ? stream : stream.parallel();
    }

    private static boolean underPressure() {
        var runtime = Runtime.getRuntime();
        return runtime.freeMemory() < PRESSURE_THRESHOLD * runtime.totalMemory();
    }
}
