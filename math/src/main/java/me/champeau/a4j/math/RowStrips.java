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
package me.champeau.a4j.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * Executes per-row image loops in parallel, splitting the row range into
 * contiguous strips (one per common pool worker) so that scheduling overhead
 * is amortized over large units of work.
 */
public final class RowStrips {

    private RowStrips() {
    }

    @FunctionalInterface
    public interface StripConsumer {
        void accept(int yStart, int yEnd);
    }

    @FunctionalInterface
    public interface StripToDouble {
        double apply(int yStart, int yEnd);
    }

    @FunctionalInterface
    public interface StripFunction<T> {
        T apply(int yStart, int yEnd);
    }

    public static void forEach(int height, StripConsumer consumer) {
        map(height, (yStart, yEnd) -> {
            consumer.accept(yStart, yEnd);
            return null;
        });
    }

    /**
     * Sums per-strip partial results. Partials are combined sequentially in
     * strip order, so the result is deterministic for a given parallelism.
     */
    public static double sum(int height, StripToDouble function) {
        var sum = 0d;
        for (var partial : map(height, function::apply)) {
            sum += partial;
        }
        return sum;
    }

    public static double max(int height, StripToDouble function) {
        var max = -Double.MAX_VALUE;
        for (var partial : map(height, function::apply)) {
            if (partial > max) {
                max = partial;
            }
        }
        return max;
    }

    /**
     * Maps each strip to a value and returns the results in strip order.
     */
    public static <T> List<T> map(int height, StripFunction<T> function) {
        var stripCount = stripCount(height);
        if (stripCount == 1) {
            return Collections.singletonList(function.apply(0, height));
        }
        var stripSize = (height + stripCount - 1) / stripCount;
        var effectiveCount = (height + stripSize - 1) / stripSize;
        var partials = new ArrayList<T>(Collections.nCopies(effectiveCount, null));
        IntStream.range(0, effectiveCount).parallel().forEach(strip -> {
            var yStart = strip * stripSize;
            var yEnd = Math.min(yStart + stripSize, height);
            partials.set(strip, function.apply(yStart, yEnd));
        });
        return partials;
    }

    private static int stripCount(int height) {
        return Math.clamp(ForkJoinPool.commonPool().getParallelism(), 1, Math.max(1, height));
    }
}
