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
package me.champeau.a4j.jsolex.cli;

import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Event listener for logging processing events in script execution. */
public class ScriptLoggingListener implements ProcessingEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptLoggingListener.class);
    private final ProcessParams processParams;
    private final Set<ImageFormat> imageFormats;
    private final List<String> suggestions = new CopyOnWriteArrayList<>();
    private final Map<String, ImageWrapper> generatedImages = new LinkedHashMap<>();

    private long sd;

    /**
     * Creates a new script logging listener.
     * @param processParams the processing parameters
     * @param imageFormats the image formats to use when saving
     */
    public ScriptLoggingListener(ProcessParams processParams, Set<ImageFormat> imageFormats) {
        this.processParams = processParams;
        this.imageFormats = imageFormats;
    }


    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var image = payload.image();
        var target = payload.path().toFile();
        new ImageSaver(RangeExpansionStrategy.DEFAULT, processParams, imageFormats).save(image, target);
        LOGGER.info("Image {} generated at {}", payload.title(), payload.path());
    }

    @Override
    public void onOutputImageDimensionsDetermined(OutputImageDimensionsDeterminedEvent event) {
        LOGGER.debug("Output dimensions of {} determined: {}x{}", event.getLabel(), event.getWidth(), event.getHeight());
    }

    @Override
    public void onNotification(NotificationEvent e) {
        switch (e.type()) {
            case INFORMATION -> LOGGER.info("{} {} - {}", e.title(), e.header(), e.message());
            case ERROR -> LOGGER.error("{} {} - {}", e.title(), e.header(), e.message());
        }
    }

    @Override
    public void onSuggestion(SuggestionEvent e) {
        suggestions.add(e.getPayload());
    }

    @Override
    public void onProcessingStart(ProcessingStartEvent e) {
        sd = e.getPayload().timestamp();
    }

    @Override
    public void onProcessingDone(ProcessingDoneEvent e) {
        var duration = Duration.ofNanos(e.getPayload().timestamp() - sd);
        double seconds = duration.toMillis() / 1000d;
        LOGGER.info("Finished in {}s", seconds);
        if (!suggestions.isEmpty()) {
            var sb = new StringBuilder();
            sb.append("Suggestions :\n");
            for (String suggestion : suggestions) {
                sb.append("    - ").append(suggestion).append("\n");
            }
            LOGGER.info(sb.toString());
        }
    }

    @Override
    public void onScriptExecutionResult(ScriptExecutionResultEvent e) {
        generatedImages.putAll(e.getPayload().imagesByLabel());
    }

    /**
     * Gets the images generated during script execution.
     * @return map of image names to image wrappers
     */
    public Map<String, ImageWrapper> getGeneratedImages() {
        return generatedImages;
    }
}
