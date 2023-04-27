/*
 * Copyright 2023-2034 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;

public class JFXProcessingEventListener implements ProcessingEventListener {
    private final ProcessingEventListener delegate;

    public static ProcessingEventListener of(ProcessingEventListener delegate) {
        return new JFXProcessingEventListener(delegate);
    }

    private JFXProcessingEventListener(ProcessingEventListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        Platform.runLater(() -> delegate.onImageGenerated(event));
    }

    @Override
    public void onPartialReconstruction(PartialReconstructionEvent event) {
        Platform.runLater(() -> delegate.onPartialReconstruction(event));
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        Platform.runLater(() -> delegate.onOutputImageDimensionsDetermined(event));
    }

    @Override
    public void onNotification(NotificationEvent e) {
        Platform.runLater(() -> delegate.onNotification(e));
    }
}
