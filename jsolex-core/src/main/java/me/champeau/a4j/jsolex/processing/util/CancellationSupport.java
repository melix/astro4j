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
package me.champeau.a4j.jsolex.processing.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Per-run cancellation support for processing operations. Each processing run
 * owns a cancellation flag which is bound to the threads working for that run
 * via a {@link ScopedValue}. Worker pools are shared between runs, so a global
 * flag cannot be used: a flag raised while tearing down one run must not leak
 * into the next run scheduled on the same pool threads, and resetting a global
 * flag for a new run would resurrect the tasks of the run being torn down.
 * <p>
 * Long-running loops poll the bound flag via {@link #checkCancelled()}, which
 * aborts with a {@link CancellationException} once the flag is raised. When no
 * flag is bound (work not initiated by a cancellable run), checks are no-ops.
 * <p>
 * Bindings do not cross thread boundaries on their own. Fan-out going through
 * {@link ProcessingLogContext} propagates the binding automatically; fan-out
 * which cannot (parallel streams in particular) must capture the flag with
 * {@link #currentFlag()} on the submitting thread and re-establish it with
 * {@link #runWith} or {@link #callWith} on the worker thread.
 */
public final class CancellationSupport {
    private static final ScopedValue<AtomicBoolean> CANCELLED = ScopedValue.newInstance();

    private CancellationSupport() {
    }

    /**
     * Runs the given body with the cancellation flag bound to {@code flag}.
     * A null flag simply runs the body without binding.
     *
     * @param flag the cancellation flag of the run, may be null
     * @param body the code to execute
     */
    public static void runWith(AtomicBoolean flag, Runnable body) {
        if (flag == null) {
            body.run();
        } else {
            ScopedValue.where(CANCELLED, flag).run(body);
        }
    }

    /**
     * Calls the given body with the cancellation flag bound to {@code flag}
     * and returns its result. A null flag simply calls the body without binding.
     *
     * @param flag the cancellation flag of the run, may be null
     * @param body the code to execute
     * @return the result of the body
     */
    public static <T> T callWith(AtomicBoolean flag, Supplier<T> body) {
        if (flag == null) {
            return body.get();
        }
        return ScopedValue.where(CANCELLED, flag).call(body::get);
    }

    /**
     * Returns the cancellation flag bound to the current thread, or null when
     * the current work is not attached to a cancellable run. Use this to carry
     * the flag across thread boundaries which {@link ProcessingLogContext}
     * does not cover, such as parallel streams.
     *
     * @return the current cancellation flag, or null
     */
    public static AtomicBoolean currentFlag() {
        return CANCELLED.isBound() ? CANCELLED.get() : null;
    }

    /**
     * Returns a runnable which executes the given task with the cancellation
     * flag currently bound to the calling thread, if any.
     *
     * @param task the task to wrap
     * @return the wrapped task
     */
    public static Runnable propagate(Runnable task) {
        var flag = currentFlag();
        if (flag == null) {
            return task;
        }
        return () -> runWith(flag, task);
    }

    /**
     * Returns whether the run which initiated the current work was cancelled.
     * Always false when no cancellation flag is bound to the current thread.
     *
     * @return true if processing should stop
     */
    public static boolean isCancelled() {
        return CANCELLED.isBound() && CANCELLED.get().get();
    }

    /**
     * Throws a {@link CancellationException} if the run which initiated the
     * current work was cancelled.
     */
    public static void checkCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Processing was interrupted");
        }
    }
}
