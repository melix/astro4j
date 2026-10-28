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
package me.champeau.a4j.jsolex.app.listeners

import me.champeau.a4j.jsolex.processing.event.GeneratedImage
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener
import me.champeau.a4j.jsolex.processing.stretching.Clahe2Strategy
import me.champeau.a4j.jsolex.processing.stretching.StretchRecipe
import me.champeau.a4j.jsolex.processing.stretching.StretchStep
import me.champeau.a4j.jsolex.processing.stretching.StretchStepKind
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import java.nio.file.Path

class SharedStretchProcessingEventListenerTest extends Specification {

    def "all listeners receive the same stretched image"() {
        given:
        def first = new RecordingListener()
        def second = new RecordingListener()
        def image = noise()
        image.metadata().put(StretchRecipe, StretchRecipe.of(StretchStep.defaultsOf(StretchStepKind.CLAHE2)))
        def expected = image.copy()
        new Clahe2Strategy(Clahe2Strategy.DEFAULT_CLIP).stretch(expected)
        def listener = new SharedStretchProcessingEventListener([first], { second })

        when:
        listener.onImageGenerated(event(image))

        then:
        first.images.size() == 1
        second.images.size() == 1
        first.images.first().is(second.images.first())
        first.images.first().findMetadata(StretchRecipe).isEmpty()
        (first.images.first() as ImageWrapper32).data() == expected.data()

        and: "the original image keeps its recipe"
        image.findMetadata(StretchRecipe).isPresent()
    }

    def "images without a recipe are forwarded unchanged"() {
        given:
        def recipient = new RecordingListener()
        def image = noise()
        def listener = new SharedStretchProcessingEventListener([recipient], { null })

        when:
        listener.onImageGenerated(event(image))

        then:
        recipient.images == [image]
    }

    def "the on demand listener only receives images while it is active"() {
        given:
        def recipient = new RecordingListener()
        def active = false
        def listener = new SharedStretchProcessingEventListener([], { active ? recipient : null })

        when:
        listener.onImageGenerated(event(noise()))

        then:
        recipient.images.isEmpty()

        when:
        active = true
        listener.onImageGenerated(event(noise()))

        then:
        recipient.images.size() == 1
    }

    private static ImageGeneratedEvent event(ImageWrapper image) {
        new ImageGeneratedEvent(new GeneratedImage(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED, "Processed", Path.of("processed"), image, "description"))
    }

    private static ImageWrapper32 noise() {
        def random = new Random(1)
        def data = new float[64][64]
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                data[y][x] = (float) (1000 + 30000 * random.nextDouble())
            }
        }
        new ImageWrapper32(64, 64, data, [:] as Map<Class<?>, Object>)
    }

    private static class RecordingListener implements ProcessingEventListener {
        final List<ImageWrapper> images = []

        @Override
        void onImageGenerated(ImageGeneratedEvent event) {
            images << event.payload.image()
        }
    }
}
