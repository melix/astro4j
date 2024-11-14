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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.awt.Graphics2D;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Filters generated images by step.
 */
public class DiscardNonRequiredImages implements ImageEmitter {
    private final ImageEmitter delegate;
    private final Set<GeneratedImageKind> allowed;

    public DiscardNonRequiredImages(ImageEmitter delegate, Set<GeneratedImageKind> allowed) {
        this.delegate = delegate;
        this.allowed = allowed;
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String category, String title, String name, ImageWrapper32 image, Consumer<? super float[][]> bufferConsumer) {
        if (discard(kind)) {
            return;
        }
        delegate.newMonoImage(kind, category, title, name, image, bufferConsumer);
    }

    private boolean discard(GeneratedImageKind kind) {
        return !allowed.contains(kind);
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String category, String title, String name, ImageWrapper32 image) {
        if (discard(kind)) {
            return;
        }
        delegate.newMonoImage(kind, category, title, name, image);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, ImageWrapper32 image, Function<ImageWrapper32, float[][][]> rgbSupplier) {
        if (discard(kind)) {
            return;
        }
        delegate.newColorImage(kind, category, title, name, image, rgbSupplier);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, int width, int height, Map<Class<?>, Object> metadata, Supplier<float[][][]> rgbSupplier) {
        if (discard(kind)) {
            return;
        }
        delegate.newColorImage(kind, category, title, name, width, height, metadata, rgbSupplier);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, ImageWrapper32 image, Function<ImageWrapper32, float[][][]> rgbSupplier, BiConsumer<Graphics2D, ? super ImageWrapper> painter) {
        if (discard(kind)) {
            return;
        }
        delegate.newColorImage(kind, category, title, name, image, rgbSupplier, painter);
    }

    @Override
    public void newGenericFile(GeneratedImageKind kind, String category, String title, String name, Path file) {
        if (discard(kind)) {
            return;
        }
        delegate.newGenericFile(kind, category, title, name, file);
    }
}
