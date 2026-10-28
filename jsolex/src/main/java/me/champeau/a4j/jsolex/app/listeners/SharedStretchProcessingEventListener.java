/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.stretching.StretchRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Delegates events to listeners which save the generated images as files. An image
 * which carries a stretch recipe is stretched once for all of them, instead of once
 * per listener, and only if at least one listener receives it.
 */
public class SharedStretchProcessingEventListener extends DelegatingProcessingEventListener {
    private final List<ProcessingEventListener> listeners;
    private final Supplier<ProcessingEventListener> onDemandListener;

    /**
     * Creates a listener.
     *
     * @param listeners the listeners which receive all events
     * @param onDemandListener supplies a listener which only receives the generated
     * images, or {@code null} when that listener is not active
     */
    public SharedStretchProcessingEventListener(List<ProcessingEventListener> listeners, Supplier<ProcessingEventListener> onDemandListener) {
        super(listeners);
        this.listeners = listeners;
        this.onDemandListener = onDemandListener;
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var recipients = new ArrayList<>(listeners);
        var onDemand = onDemandListener.get();
        if (onDemand != null) {
            recipients.add(onDemand);
        }
        if (recipients.isEmpty()) {
            return;
        }
        var stretchedEvent = withStretchedImage(event);
        for (var recipient : recipients) {
            recipient.onImageGenerated(stretchedEvent);
        }
    }

    private static ImageGeneratedEvent withStretchedImage(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var stretched = StretchRecipe.materialize(payload.image());
        if (stretched == payload.image()) {
            return event;
        }
        return new ImageGeneratedEvent(new GeneratedImage(payload.kind(), payload.title(), payload.path(), stretched, payload.description(), payload.displayTitle()));
    }
}
