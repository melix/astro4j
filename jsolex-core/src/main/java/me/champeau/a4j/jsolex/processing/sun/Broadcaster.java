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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;

/**
 * Interface for broadcasting processing events to listeners.
 * Used to communicate progress and status during image processing operations.
 */
public interface Broadcaster {
    /**
     * A no-op broadcaster that ignores all events.
     */
    Broadcaster NO_OP = e -> {};

    /**
     * Broadcasts a progress operation by wrapping it in a ProgressEvent.
     *
     * @param operation the progress operation to broadcast
     */
    default void broadcast(ProgressOperation operation) {
        broadcast(ProgressEvent.of(operation));
    }

    /**
     * Broadcasts a processing event to all listeners.
     *
     * @param event the event to broadcast
     */
    void broadcast(ProcessingEvent<?> event);
}
