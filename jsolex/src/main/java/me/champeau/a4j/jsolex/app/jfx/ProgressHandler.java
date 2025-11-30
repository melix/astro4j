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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

public final class ProgressHandler {
    private static final long UPDATE_INTERVAL_MS = 50;

    private final ReentrantLock lock = new ReentrantLock();
    private final BiConsumer<Double, String> uiUpdater;
    private final Thread updateThread;
    private final AtomicBoolean pendingUpdate = new AtomicBoolean(false);

    private volatile double progress;
    private volatile String message;
    private volatile boolean dirty;
    private volatile boolean running = true;

    public ProgressHandler(BiConsumer<Double, String> uiUpdater) {
        this.uiUpdater = uiUpdater;
        this.updateThread = new Thread(this::updateLoop, "ProgressHandler");
        this.updateThread.setDaemon(true);
        this.updateThread.start();
    }

    public void update(double progress, String message) {
        lock.lock();
        try {
            this.progress = progress;
            this.message = message;
            this.dirty = true;
        } finally {
            lock.unlock();
        }
    }

    public void hide() {
        update(0, "");
    }

    public void close() {
        running = false;
        updateThread.interrupt();
    }

    private void updateLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(UPDATE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (dirty && pendingUpdate.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    lock.lock();
                    try {
                        uiUpdater.accept(progress, message);
                        dirty = false;
                    } finally {
                        lock.unlock();
                    }
                    pendingUpdate.set(false);
                });
            }
        }
    }
}