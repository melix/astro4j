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

import me.champeau.a4j.jsolex.processing.sun.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ParallelExecutor implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelExecutor.class);

    private final ExecutorService executorService = Executors.newWorkStealingPool();
    private final Semaphore semaphore = new Semaphore(8 * Runtime.getRuntime().availableProcessors());
//    private final Semaphore semaphore = new Semaphore(1 );

    private Consumer<? super Exception> exceptionHandler = (Consumer<Exception>) e -> LOGGER.error("An error happened during processing", e);

    private ParallelExecutor() {
    }

    public static ParallelExecutor newExecutor() {
        return new ParallelExecutor();
    }

    public void setExceptionHandler(Consumer<? super Exception> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void submit(Runnable task) {
        try {
            semaphore.acquire();
            executorService.submit(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    exceptionHandler.accept(ex);
                } finally {
                    semaphore.release();
                }
            });
        } catch (InterruptedException e) {
            throw new ProcessingException(e);
        }
    }

    public <T> CompletableFuture<T> submit(AbstractTask<T> task) {
        try {
            semaphore.acquire();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return task.get();
                } catch (Exception ex) {
                    exceptionHandler.accept(ex);
                    return null;
                } finally {
                    semaphore.release();
                }
            }, executorService);
        } catch (InterruptedException e) {
            throw new ProcessingException(e);
        }
    }

    @Override
    public void close() throws Exception {
        executorService.shutdown();
        if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
            throw new ProcessingException("Processing timed out after 1 hour");
        }
    }
}
