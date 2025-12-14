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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.EllipseFittingRequestEvent;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeometryDetectedEvent;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ReconstructionDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.SpectralLineDetectedEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.event.TrimmingParametersDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;

import java.util.List;

/**
 * Processing event listener that delegates all events to a list of listeners.
 */
public class DelegatingProcessingEventListener implements ProcessingEventListener, Broadcaster {

    private final List<ProcessingEventListener> listeners;

    /**
     * Creates a delegating processing event listener.
     * @param listeners the list of listeners to delegate to
     */
    public DelegatingProcessingEventListener(List<ProcessingEventListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void onVideoMetadataAvailable(VideoMetadataEvent event) {
        for (ProcessingEventListener listener : listeners) {
            listener.onVideoMetadataAvailable(event);
        }
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        for (ProcessingEventListener listener : listeners) {
            listener.onImageGenerated(event);
        }
    }

    @Override
    public void onFileGenerated(FileGeneratedEvent event) {
        for (ProcessingEventListener listener : listeners) {
            listener.onFileGenerated(event);
        }
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        for (ProcessingEventListener listener : listeners) {
            listener.onPartialReconstruction(event);
        }
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        for (ProcessingEventListener listener : listeners) {
            listener.onOutputImageDimensionsDetermined(event);
        }
    }

    @Override
    public void onNotification(NotificationEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onNotification(e);
        }
    }

    @Override
    public void onSuggestion(SuggestionEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onSuggestion(e);
        }
    }

    @Override
    public void onProcessingStart(ProcessingStartEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onProcessingStart(e);
        }
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onProcessingDone(e);
        }
    }

    @Override
    public void onProgress(ProgressEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onProgress(e);
        }
    }

    @Override
    public void onGenericMessage(GenericMessage<?> e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onGenericMessage(e);
        }
    }

    @Override
    public void onScriptExecutionResult(ScriptExecutionResultEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onScriptExecutionResult(e);
        }
    }

    @Override
    public void onAverageImageComputed(AverageImageComputedEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onAverageImageComputed(e);
        }
    }

    @Override
    public void onReconstructionDone(ReconstructionDoneEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onReconstructionDone(e);
        }
    }

    @Override
    public void onTrimmingParametersDetermined(TrimmingParametersDeterminedEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onTrimmingParametersDetermined(e);
        }
    }

    @Override
    public void broadcast(ProcessingEvent<?> event) {
        for (ProcessingEventListener listener : listeners) {
            if (listener instanceof Broadcaster) {
                ((Broadcaster) listener).broadcast(event);
            }
        }
    }

    @Override
    public void onEllipseFittingRequest(EllipseFittingRequestEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onEllipseFittingRequest(e);
        }
    }

    @Override
    public void onSpectralLineDetected(SpectralLineDetectedEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onSpectralLineDetected(e);
        }
    }

    @Override
    public void onGeometryDetected(GeometryDetectedEvent e) {
        for (ProcessingEventListener listener : listeners) {
            listener.onGeometryDetected(e);
        }
    }
}
