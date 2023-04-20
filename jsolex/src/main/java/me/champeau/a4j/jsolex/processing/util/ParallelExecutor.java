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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ParallelExecutor implements AutoCloseable {
    private final ExecutorService executorService = Executors.newWorkStealingPool();
    private final Semaphore semaphore = new Semaphore(8 * Runtime.getRuntime().availableProcessors());

    private ParallelExecutor() {
    }

    public static ParallelExecutor newExecutor() {
        return new ParallelExecutor();
    }

    public void submit(Runnable task) {
        try {
            semaphore.acquire();
            executorService.submit(() -> {
                try {
                    task.run();
                } finally {
                    semaphore.release();
                }
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        executorService.shutdown();
        if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
            throw new RuntimeException("Processing timed out after 1 hour");
        }
    }
}
