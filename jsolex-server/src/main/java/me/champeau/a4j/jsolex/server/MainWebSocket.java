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

import io.micronaut.context.ApplicationContext;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

@ServerWebSocket("/ws")
public class MainWebSocket extends AbstractController implements StoreListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainWebSocket.class);

    private final WebSocketBroadcaster broadcaster;
    private final ApplicationContext context;

    public MainWebSocket(ViewsRenderer<Object, ?> viewsRenderer,
                         WebSocketBroadcaster broadcaster,
                         ApplicationContext context) {
        super(viewsRenderer);
        this.broadcaster = broadcaster;
        this.context = context;
    }

    @OnOpen
    public void onOpen() throws IOException {
        var store = context.getBean(ImagesStore.class);
        sendAllImages(store);
    }

    private void sendAllImages(ImagesStore imagesStore) throws IOException {
        broadcast("images-links", () -> {
            var model = imagesStore.getImages()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().ordinal()))
                .map(e -> {
                    var displayCategory = e.getKey();
                    var imagesByKind = e.getValue();
                    return Map.of(
                        "id", displayCategory.name(),
                        "label", message("displayCategory." + displayCategory.name()),
                        "images", imagesByKind.values()
                            .stream()
                            .flatMap(images -> images.stream()
                                .map(image -> Map.of(
                                    "id", image.id(),
                                    "label", image.title(),
                                    "url", "/image/" + image.id(),
                                    "caption", image.caption()
                                ))
                            ).collect(Collectors.groupingBy(image -> image.get("label")))
                            .values()
                            .stream()
                            .map(images -> {
                                var first = images.getFirst();
                                return Map.of(
                                    "id", first.get("id"),
                                    "label", first.get("label"),
                                    "url", images.stream().map(image -> image.get("url")).map(String.class::cast).collect(Collectors.joining(",")),
                                    "caption", images.stream().map(image -> image.get("caption")).map(String.class::cast).collect(Collectors.joining("||"))
                                );
                            })
                            .toList()
                    );
                }).toList();
            return Map.of("categories", model);
        });
    }

    @OnClose
    public void onClose() {

    }

    @OnMessage
    public void onMessage(String message) {
    }

    @Override
    public void imageAdded(ImagesStore store) {
        try {
            sendAllImages(store);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleared(ImagesStore store) {
        try {
            sendAllImages(store);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void broadcast(String view, Supplier<T> modelSupplier) {
        broadcaster.broadcastSync(render(view, modelSupplier));
    }
}
