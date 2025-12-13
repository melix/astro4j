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

import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

/**
 * Utility class for executing background operations using different execution strategies.
 * Provides CPU-bound async execution, IO-bound async execution with virtual threads,
 * and exclusive IO operations with locking. All operations are tracked and can be
 * interrupted or cleaned up.
 */
public final class BackgroundOperations {

    private static final ExecutorService ASYNC = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("async-task-" + threadNumber.getAndIncrement());
            return t;
        }
    });
    private static final ExecutorService IO_ASYNC = Executors.newVirtualThreadPerTaskExecutor();
    private static final ReentrantLock EXCLUSIVE_IO_LOCK = new ReentrantLock();
    private static final ExecutorService EXCLUSIVE_IO = Executors.newCachedThreadPool();
    private static final Deque<Future<?>> TASKS = new ConcurrentLinkedDeque<>();
    private static final Thread CLEANUP_THREAD;

    static {
        CLEANUP_THREAD = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                cleanupTasks();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            TASKS.clear();
        });
        CLEANUP_THREAD.setDaemon(true);
        CLEANUP_THREAD.start();
    }

    private BackgroundOperations() {

    }

    /**
     * Executes a CPU-bound task asynchronously on a fixed thread pool.
     * The task is tracked and can be interrupted via {@link #interrupt()}.
     * Exceptions thrown by the action are logged but do not propagate.
     *
     * @param action the task to execute
     */
    public static void async(Runnable action) {
        TASKS.add(ASYNC.submit(wrap(action)));
    }

    private static Runnable wrap(Runnable action) {
        return () -> {
            try {
                action.run();
            } catch (Throwable e) {
                logError(e);
            }
        };
    }

    /**
     * Executes an IO-bound task asynchronously using virtual threads.
     * The task is tracked and can be interrupted via {@link #interrupt()}.
     * Exceptions thrown by the action are logged but do not propagate.
     *
     * @param action the IO task to execute
     */
    public static void asyncIo(Runnable action) {
        TASKS.add(IO_ASYNC.submit(wrap(action)));
    }

    /**
     * Executes an IO operation with exclusive access via a lock.
     * Only one exclusive IO operation can run at a time.
     * This method blocks until the lock is acquired.
     *
     * @param action the exclusive IO operation to execute
     */
    public static void exclusiveIO(Runnable action) {
        try {
            EXCLUSIVE_IO_LOCK.lock();
            action.run();
        } finally {
            EXCLUSIVE_IO_LOCK.unlock();
        }
    }

    /**
     * Shuts down all executors immediately, attempting to stop all actively
     * executing tasks. This method does not wait for tasks to complete.
     */
    public static void close() {
        ASYNC.shutdownNow();
        IO_ASYNC.shutdownNow();
        EXCLUSIVE_IO.shutdownNow();
    }

    /**
     * Interrupts all currently tracked background tasks by cancelling them.
     * Cancelled tasks may throw {@link InterruptedException} if they are blocked.
     */
    public static void interrupt() {
        TASKS.forEach(f -> f.cancel(true));
    }

    private static void cleanupTasks() {
        TASKS.removeIf(f -> f.isCancelled() || f.isDone());
    }
}
