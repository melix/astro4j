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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.application.Platform;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class BatchOperations {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Condition CONDITION = LOCK.newCondition();

    private static final List<Runnable> ACTIONS = new ArrayList<>();
    private static final Map<Object, Runnable> ONE_OF_A_KIND = new HashMap<>();

    static {
        var backgroundThread = new FXOperationsThread();
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private BatchOperations() {

    }

    public static void submitOneOfAKind(Object kind, Runnable action) {
        LOCK.lock();
        try {
            ONE_OF_A_KIND.put(kind, action);
            CONDITION.signalAll();
        } finally {
            LOCK.unlock();
        }
    }

    public static void submit(Runnable action) {
        LOCK.lock();
        try {
            ACTIONS.add(action);
            CONDITION.signalAll();
        } finally {
            LOCK.unlock();
        }
    }

    public static <T> T blockingUntilResultAvailable(Supplier<T> supplier) {
        var ref = new AtomicReference<T>();
        var latch = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(supplier.get());
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get();
    }

    private static class FXOperationsThread extends Thread {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                LOCK.lock();
                List<Runnable> actions ;
                try {
                    CONDITION.await();
                    actions = new ArrayList<>(ACTIONS);
                    ACTIONS.clear();
                    actions.addAll(ONE_OF_A_KIND.values());
                    ONE_OF_A_KIND.clear();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    LOCK.unlock();
                }
                Platform.runLater(() -> {
                    for (Runnable action : actions) {
                        action.run();
                    }
                });
            }
            BackgroundOperations.close();
        }
    }
}
