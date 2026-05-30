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

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Carries the identity of the file currently being processed (its batch
 * sequence number) so that log statements can be attributed to the correct
 * per-file log, regardless of which thread emits them.
 * <p>
 * The identity is held in a {@link ScopedValue}: it is bound for the duration
 * of {@link #runWith} and is automatically unbound when that call returns, so a
 * shared worker thread that processes work for several files in turn never
 * leaks one file's identity into another's work.
 * <p>
 * {@code ScopedValue} bindings do not cross thread boundaries on their own.
 * When processing fans work out to another thread, use {@link #submit} or
 * {@link #runAsync} (which propagate the identity for you) rather than handing
 * the task straight to an executor. For fan-out that cannot go through those
 * methods (a parallel stream, a deferred UI action, ...), read the identity
 * with {@link #currentFileId()} on the submitting thread and re-establish it
 * with {@link #runWith} on the worker thread.
 */
public final class ProcessingLogContext {

    private static final ScopedValue<Integer> FILE_ID = ScopedValue.newInstance();

    private ProcessingLogContext() {

    }

    /**
     * Runs the given body with the file identity bound to {@code fileId}. Any
     * log statement emitted (directly or via fan-out propagated with
     * {@link #submit}/{@link #runAsync}) while the body runs is attributed to
     * this file.
     */
    public static void runWith(int fileId, Runnable body) {
        ScopedValue.where(FILE_ID, fileId).run(body);
    }

    /**
     * Returns the file identity bound to the current thread, or an empty
     * optional when no file is being processed on this thread.
     */
    public static OptionalInt currentFileId() {
        return FILE_ID.isBound() ? OptionalInt.of(FILE_ID.get()) : OptionalInt.empty();
    }

    /**
     * Submits a task to a {@link ParallelExecutor}, propagating the current
     * file identity so that log statements emitted by the task are attributed
     * to the file being processed on the submitting thread.
     */
    public static void submit(ParallelExecutor executor, Runnable task) {
        executor.submit(wrap(task));
    }

    /**
     * Submits a task to an {@link ExecutorService}, propagating the current
     * file identity so that log statements emitted by the task are attributed
     * to the file being processed on the submitting thread.
     */
    public static Future<?> submit(ExecutorService executor, Runnable task) {
        return executor.submit(wrap(task));
    }

    /**
     * Equivalent to {@link CompletableFuture#runAsync(Runnable, Executor)} but
     * propagates the current file identity so that log statements emitted by
     * the task are attributed to the file being processed on the submitting
     * thread.
     */
    public static CompletableFuture<Void> runAsync(Executor executor, Runnable task) {
        return CompletableFuture.runAsync(wrap(task), executor);
    }

    private static Runnable wrap(Runnable task) {
        var fileId = currentFileId();
        if (fileId.isEmpty()) {
            return task;
        }
        int id = fileId.getAsInt();
        return () -> runWith(id, task);
    }
}
