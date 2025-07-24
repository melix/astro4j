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
package me.champeau.a4j.jsolex.processing.event;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import java.util.concurrent.CompletableFuture;

/**
 * Event requesting an ellipse fitting dialog to be shown
 */
public final class EllipseFittingRequestEvent extends ProcessingEvent<EllipseFittingRequestEvent.Payload> {
    
    public record Payload(
            ImageWrapper32 image,
            Ellipse initialEllipse,
            CompletableFuture<Ellipse> resultFuture
    ) {}
    
    public EllipseFittingRequestEvent(ImageWrapper32 image, Ellipse initialEllipse, CompletableFuture<Ellipse> resultFuture) {
        super(new Payload(image, initialEllipse, resultFuture));
    }
    
    public ImageWrapper32 image() {
        return getPayload().image();
    }
    
    public Ellipse initialEllipse() {
        return getPayload().initialEllipse();
    }
    
    public CompletableFuture<Ellipse> resultFuture() {
        return getPayload().resultFuture();
    }
}