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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public final class BackgroundOperations {

    private static final ExecutorService ASYNC = Executors.newCachedThreadPool();
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

    public static void async(Runnable action) {
        TASKS.add(ASYNC.submit(action));
    }

    public static void asyncIo(Runnable action) {
        TASKS.add(IO_ASYNC.submit(action));
    }

    public static void exclusiveIO(Runnable action) {
        try {
            EXCLUSIVE_IO_LOCK.lock();
            Future<?> future = EXCLUSIVE_IO.submit(action);
            TASKS.add(future);
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProcessingException(e);
            } catch (ExecutionException e) {
                throw new ProcessingException(e);
            } finally {
                TASKS.remove(future);
            }
        } finally {
            EXCLUSIVE_IO_LOCK.unlock();
        }
    }

    public static void close() {
        ASYNC.shutdownNow();
        IO_ASYNC.shutdownNow();
        EXCLUSIVE_IO.shutdownNow();
    }

    public static void interrupt() {
        TASKS.forEach(f -> f.cancel(true));
    }

    private static void cleanupTasks() {
        TASKS.removeIf(f -> f.isCancelled() || f.isDone());
    }
}
