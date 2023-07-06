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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Filters generated images by step.
 */
public class DiscardNonRequiredImages implements ImageEmitter {
    private static final Supplier<Void> VOID = () -> null;
    private final ImageEmitter delegate;
    private final Set<GeneratedImageKind> allowed;

    public DiscardNonRequiredImages(ImageEmitter delegate, Set<GeneratedImageKind> allowed) {
        this.delegate = delegate;
        this.allowed = allowed;
    }

    @Override
    public Supplier<Void> newMonoImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image, Consumer<? super float[]> bufferConsumer) {
        if (!allowed.contains(kind)) {
            return VOID;
        }
        return delegate.newMonoImage(kind, title, name, image, bufferConsumer);
    }

    @Override
    public Supplier<Void> newMonoImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image) {
        if (!allowed.contains(kind)) {
            return VOID;
        }
        return delegate.newMonoImage(kind, title, name, image);
    }

    @Override
    public Supplier<Void> newColorImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image, Function<float[], float[][]> rgbSupplier) {
        if (!allowed.contains(kind)) {
            return VOID;
        }
        return delegate.newColorImage(kind, title, name, image, rgbSupplier);
    }

    @Override
    public Supplier<Void> newColorImage(GeneratedImageKind kind, String title, String name, int width, int height, Supplier<float[][]> rgbSupplier) {
        if (!allowed.contains(kind)) {
            return VOID;
        }
        return delegate.newColorImage(kind, title, name, width, height, rgbSupplier);
    }
}
