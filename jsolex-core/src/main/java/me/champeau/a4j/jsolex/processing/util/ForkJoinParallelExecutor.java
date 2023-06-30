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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ForkJoinParallelExecutor implements AutoCloseable, ForkJoinContext {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ExecutionContext executionContext;
    private final int maxParallel;

    private ForkJoinParallelExecutor() {
        this(Runtime.getRuntime().availableProcessors());
    }

    private ForkJoinParallelExecutor(int maxParallel) {
        this.maxParallel = maxParallel;
        executionContext = new ExecutionContext( null, null, null);
    }

    public static ForkJoinParallelExecutor newExecutor() {
        return new ForkJoinParallelExecutor();
    }

    public static ForkJoinParallelExecutor newExecutor(int maxParallel) {
        return new ForkJoinParallelExecutor(maxParallel);
    }

    public void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
        executionContext.setUncaughtExceptionHandler(handler);
    }

    @Override
    public void setOnTaskStart(Consumer<? super Thread> consumer) {
        executionContext.setOnTaskStart(consumer);
    }

    @Override
    public void setOnTaskFinished(Consumer<? super Thread> consumer) {
        executionContext.setOnTaskFinished(consumer);
    }

    @Override
    public void close() throws Exception {
        executionContext.waitFor();
        executor.shutdownNow();
    }

    @Override
    public void cancel() {
        try {
            executor.shutdownNow();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void async(Runnable r) {
        executionContext.async(r);
    }

    @Override
    public <T> Supplier<T> submit(Callable<T> callable) {
        return executionContext.submit(callable);
    }

    @Override
    public void isolate(Consumer<? super ForkJoinContext> context) {
        executionContext.isolate(context);
    }

    @Override
    public <T> Supplier<T> forkJoin(Function<? super ForkJoinContext, T> consumer) {
        return executionContext.forkJoin(consumer);
    }

    @Override
    public <T, U> Supplier<U> submitAndThen(Callable<T> callable, Function<? super T, U> consumer) {
        return executionContext.submitAndThen(callable, consumer);
    }

    @Override
    public void blocking(Runnable r) {
        executionContext.blocking(r);
    }

    private class ExecutionContext implements ForkJoinContext {
        private final Semaphore semaphore;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private final List<Object> tasks = new ArrayList<>();
        private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
        private Consumer<? super Thread> onTaskStart;
        private Consumer<? super Thread> onTaskEnd;

        public ExecutionContext(Consumer<? super Thread> onTaskStart, Consumer<? super Thread> onTaskEnd, Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
            this.semaphore = new Semaphore(maxParallel);
            this.onTaskStart = onTaskStart;
            this.onTaskEnd = onTaskEnd;
            this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        }

        @Override
        public synchronized void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
            uncaughtExceptionHandler = handler;
        }

        @Override
        public void setOnTaskStart(Consumer<? super Thread> consumer) {
            this.onTaskStart = consumer;
        }

        @Override
        public void setOnTaskFinished(Consumer<? super Thread> consumer) {
            this.onTaskEnd = consumer;
        }

        @Override
        public void isolate(Consumer<? super ForkJoinContext> context) {
            var ctx = new ExecutionContext(onTaskStart, onTaskEnd, uncaughtExceptionHandler);
            context.accept(ctx);
        }

        public void async(Runnable r) {
            submit(() -> {
                r.run();
                return null;
            });
        }

        @Override
        public <T> Supplier<T> submit(Callable<T> callable) {
            lock.lock();
            try {
                tasks.add(callable);
                return asSupplier(executor.submit(() -> {
                    try {
                        semaphore.acquireUninterruptibly();
                        if (uncaughtExceptionHandler != null) {
                            Thread.currentThread().setUncaughtExceptionHandler(uncaughtExceptionHandler);
                        }
                        if (onTaskStart != null) {
                            onTaskStart.accept(Thread.currentThread());
                        }
                        return callable.call();
                    } finally {
                        lock.lock();
                        try {
                            tasks.remove(callable);
                            if (onTaskEnd != null) {
                                onTaskEnd.accept(Thread.currentThread());
                            }
                        } finally {
                            semaphore.release();
                            condition.signalAll();
                            lock.unlock();
                        }
                    }
                }));
            } finally {
                lock.unlock();
            }
        }

        @Override
        public <T, U> Supplier<U> submitAndThen(Callable<T> callable, Function<? super T, U> consumer) {
            return submit(() -> consumer.apply(submit(callable).get()));
        }

        @Override
        public <T> Supplier<T> forkJoin(Function<? super ForkJoinContext, T> consumer) {
            var context = new ExecutionContext(onTaskStart, onTaskEnd, uncaughtExceptionHandler);
            try {
                return context.submit(() -> consumer.apply(context));
            } finally {
                context.waitFor();
            }
        }

        @Override
        public void cancel() {
            try {
                ForkJoinParallelExecutor.this.cancel();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void waitFor() {
            lock.lock();
            semaphore.release();
            try {
                while (!tasks.isEmpty()) {
                    condition.await();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                semaphore.acquireUninterruptibly();
                lock.unlock();
            }
        }

        private <T> Supplier<T> asSupplier(Future<T> future) {
            return () -> {
                try {
                    semaphore.release();
                    return future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ProcessingException(e);
                } catch (ExecutionException e) {
                    var ex = ProcessingException.wrap(e);
                    if (uncaughtExceptionHandler != null) {
                        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), ex);
                        return null;
                    }
                    throw ex;
                } finally {
                    semaphore.acquireUninterruptibly();
                }
            };
        }

    }
}
