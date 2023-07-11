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

import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NamingStrategyAwareImageEmitter implements ImageEmitter {
    private final ImageEmitter delegate;
    private final FileNamingStrategy strategy;
    private final int sequenceNumber;
    private final String imageKind;
    private final String serFileBaseName;

    public NamingStrategyAwareImageEmitter(ImageEmitter delegate,
                                           FileNamingStrategy strategy,
                                           int sequenceNumber,
                                           String imageKind,
                                           String serFileBaseName) {
        this.delegate = delegate;
        this.strategy = strategy;
        this.sequenceNumber = sequenceNumber;
        this.imageKind = imageKind;
        this.serFileBaseName = serFileBaseName;
    }

    private String rename(String name) {
        return strategy.render(sequenceNumber, imageKind, name, serFileBaseName);
    }

    @Override
    public Supplier<Void> newMonoImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image, Consumer<? super float[]> bufferConsumer) {
        return delegate.newMonoImage(kind, title, rename(name), image, bufferConsumer);
    }

    @Override
    public Supplier<Void> newMonoImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image) {
        return delegate.newMonoImage(kind, title, rename(name), image);
    }

    @Override
    public Supplier<Void> newColorImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image, Function<float[], float[][]> rgbSupplier) {
        return delegate.newColorImage(kind, title, rename(name), image, rgbSupplier);
    }

    @Override
    public Supplier<Void> newColorImage(GeneratedImageKind kind, String title, String name, int width, int height, Supplier<float[][]> rgbSupplier) {
        return delegate.newColorImage(kind, title, rename(name), width, height, rgbSupplier);
    }

    @Override
    public Supplier<Void> newGenericFile(GeneratedImageKind kind, String title, String name, Path file) {
        return delegate.newGenericFile(kind, title, rename(name), file);
    }
}
