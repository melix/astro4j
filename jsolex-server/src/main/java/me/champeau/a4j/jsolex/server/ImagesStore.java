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
package me.champeau.a4j.jsolex.server;

import jakarta.inject.Singleton;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.DisplayCategory;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageSaver;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Singleton
public class ImagesStore implements ProcessingEventListener {
    private final AtomicLong currentId = new AtomicLong(0);

    private final Map<DisplayCategory, Map<GeneratedImageKind, List<ImageModel>>> images = new ConcurrentHashMap<>();
    private final Map<Long, ImageModel> imagesById = new ConcurrentHashMap<>();

    private final ImageSaver saver;
    private final List<StoreListener> listeners;

    public ImagesStore(List<StoreListener> listeners) {
        this.listeners = listeners;
        var params = ProcessParams.loadDefaults();
        this.saver = new ImageSaver(
            RangeExpansionStrategy.DEFAULT,
            params,
            Set.of(ImageFormat.JPG)
        );
    }

    @Override
    public void onImageGenerated(ImageGeneratedEvent event) {
        var payload = event.getPayload();
        var tempDir = TemporaryFolder.tempDir().resolve("server-images");
        var id = currentId.incrementAndGet();
        var name = id + "_" + payload.path().toFile().getName() + ".jpg";
        var saved = tempDir.resolve(name).toFile();
        saved.deleteOnExit();
        saver.save(payload.image(), saved);
        var imageKind = payload.kind();
        var displayCategory = imageKind.displayCategory();
        var caption = payload.title();
        var source = payload.image().findMetadata(SourceInfo.class);
        if (source.isPresent()) {
            var zonedDateTime = source.get().dateTime().withZoneSameInstant(ZoneId.systemDefault());
            caption += " (" + zonedDateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME) + ")";
        }
        var model = new ImageModel(
            id,
            displayCategory.name(),
            payload.title(),
            saved.toString(),
            caption
        );
        images.computeIfAbsent(displayCategory, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(imageKind, k -> new CopyOnWriteArrayList<>()).add(model);
        imagesById.put(id, model);
        for (var listener : listeners) {
            listener.imageAdded(this);
        }
    }

    public void clear() {
        images.clear();
        imagesById.clear();
        for (var listener : listeners) {
            listener.cleared(this);
        }
    }

    public Optional<ImageModel> findImage(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(imagesById.get(id));
    }

    public List<DisplayCategory> getDisplayCategories() {
        return images.keySet().stream()
            .sorted(Comparator.comparingInt(DisplayCategory::ordinal))
            .toList();
    }

    public Map<Long, ImageModel> getImagesById() {
        return Collections.unmodifiableMap(imagesById);
    }

    public Map<DisplayCategory, Map<GeneratedImageKind, List<ImageModel>>> getImages() {
        return getDisplayCategories().stream()
            .collect(Collectors.toMap(
                dc -> dc,
                images::get, (e1, e2) -> e1, LinkedHashMap::new
            ));
    }
}
