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
package me.champeau.a4j.jsolex.app;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.server.JSolexServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * This class is used to hold the JSolEx server state.
 */
public class JSolExServerHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSolExServerHolder.class);

    private final AtomicBoolean command = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ApplicationContext context;
    private final List<Consumer<? super Boolean>> statusChangeListeners = new CopyOnWriteArrayList<>();

    public JSolExServerHolder() {
        Thread.startVirtualThread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                boolean lastStatus = started.get();
                var newStatus = context != null && context.isRunning();
                started.set(newStatus);
                if (lastStatus != newStatus) {
                    runStatusListeners();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void runStatusListeners() {
        for (var runnable : statusChangeListeners) {
            try {
                var status = this.started.get();
                runnable.accept(status);
            } catch (Exception e) {
                LOGGER.error(JSolEx.message("error.running.listener"), e);
            }
        }
    }

    public void addStatusChangeListener(Consumer<? super Boolean> runnable) {
        statusChangeListeners.add(runnable);
    }

    public void removeStatusChangeListener(Consumer<? super Boolean> runnable) {
        statusChangeListeners.remove(runnable);
    }

    public boolean isStarted() {
        return started.get();
    }

    public int getPort() {
        return context.getBean(EmbeddedServer.class).getPort();
    }

    public void start(int port) {
        if (command.compareAndSet(false, true)) {
            Thread.startVirtualThread(() -> context = JSolexServer.start(port, ProcessParamsIO.loadDefaults()));
        }
    }

    public void stop() {
        if (command.compareAndSet(true, false)) {
            try {
                context.close();
                context = null;
            } finally {
                command.set(false);
            }
        }
    }

    public List<String> getServerUrls() {
        if (context == null) {
            return List.of();
        }
        return JSolexServer.getServerUrls(context.getBean(EmbeddedServer.class));
    }

    public Optional<URI> getContextUri() {
        if (context == null) {
            return Optional.empty();
        }
        return Optional.of(context.getBean(EmbeddedServer.class).getContextURI());
    }

    public List<ProcessingEventListener> getListeners() {
        if (context == null) {
            return List.of();
        }
        return context.getBeansOfType(ProcessingEventListener.class).stream().toList();
    }
}
