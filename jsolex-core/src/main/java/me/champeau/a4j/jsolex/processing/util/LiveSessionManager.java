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

import me.champeau.a4j.jsolex.processing.util.spectrosolhub.SpectroSolHubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LiveSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveSessionManager.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    private final SpectroSolHubClient client;
    private final AtomicBoolean active = new AtomicBoolean(false);

    private volatile String sessionId;
    private volatile String viewUrl;
    private ScheduledExecutorService heartbeatScheduler;

    public LiveSessionManager(SpectroSolHubClient client) {
        this.client = client;
    }

    public String start(String title) throws SpectroSolHubException {
        var response = client.startLiveSession(title);
        sessionId = response.sessionId();
        viewUrl = response.viewUrl();
        active.set(true);
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "live-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOGGER.info("Live session started: {}", viewUrl);
        return viewUrl;
    }

    public void stop() {
        if (active.compareAndSet(true, false)) {
            if (heartbeatScheduler != null) {
                heartbeatScheduler.shutdownNow();
                heartbeatScheduler = null;
            }
            if (sessionId != null) {
                try {
                    client.stopLiveSession(sessionId);
                    LOGGER.info("Live session stopped: {}", sessionId);
                } catch (SpectroSolHubException e) {
                    LOGGER.warn("Failed to stop live session: {}", e.getMessage());
                }
                sessionId = null;
                viewUrl = null;
            }
        }
    }

    public void pushImage(String category, String title, String imageKind, byte[] jpgBytes, String observationDate) {
        if (!active.get()) {
            return;
        }
        var currentSessionId = sessionId;
        if (currentSessionId == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                client.pushLiveImage(currentSessionId, category, title, imageKind, jpgBytes, observationDate);
            } catch (SpectroSolHubException e) {
                if (e.statusCode() == 404) {
                    LOGGER.warn("Live session {} no longer exists on server, stopping", currentSessionId);
                    stop();
                } else {
                    LOGGER.warn("Failed to push live image '{}': {}", title, e.getMessage());
                }
            }
        });
    }

    public boolean isActive() {
        return active.get();
    }

    public String getViewUrl() {
        return viewUrl;
    }

    private void sendHeartbeat() {
        var currentSessionId = sessionId;
        if (currentSessionId == null || !active.get()) {
            return;
        }
        try {
            client.sendHeartbeat(currentSessionId);
        } catch (SpectroSolHubException e) {
            if (e.statusCode() == 404) {
                LOGGER.warn("Live session {} expired on server, stopping", currentSessionId);
                stop();
            } else {
                LOGGER.warn("Failed to send heartbeat: {}", e.getMessage());
            }
        }
    }
}
