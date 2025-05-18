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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.awt.Graphics2D;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NamingStrategyAwareImageEmitter implements ImageEmitter {
    private final ImageEmitter delegate;
    private final FileNamingStrategy strategy;
    private final int sequenceNumber;
    private final String serFileBaseName;

    public NamingStrategyAwareImageEmitter(ImageEmitter delegate,
                                           FileNamingStrategy strategy,
                                           int sequenceNumber,
                                           String serFileBaseName) {
        this.delegate = delegate;
        this.strategy = strategy;
        this.sequenceNumber = sequenceNumber;
        this.serFileBaseName = serFileBaseName;
    }

    private String rename(GeneratedImageKind kind, String name, String category, ImageWrapper image) {
        return strategy.render(sequenceNumber, category, kind.directoryKind().name().toLowerCase(Locale.US), name, serFileBaseName, image);
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image, Consumer<? super float[][]> bufferConsumer) {
        delegate.newMonoImage(kind, null, title, rename(kind, name, category, image), description, image, bufferConsumer);
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image) {
        delegate.newMonoImage(kind, null, title, rename(kind, name, category, image), description, image);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image, Function<ImageWrapper32, float[][][]> rgbSupplier) {
        delegate.newColorImage(kind, null, title, rename(kind, name, category, image), description, image, rgbSupplier);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, String description, int width, int height, Map<Class<?>, Object> metadata, Supplier<float[][][]> rgbSupplier) {
        delegate.newColorImage(kind, null, title, rename(kind, name, category, null), description, width, height, metadata, rgbSupplier);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String category, String title, String name, String description, ImageWrapper32 image, Function<ImageWrapper32, float[][][]> rgbSupplier, BiConsumer<Graphics2D, ? super ImageWrapper> painter) {
        delegate.newColorImage(kind, null, title, rename(kind, name, category, image), description, image, rgbSupplier, painter);
    }

    @Override
    public void newGenericFile(GeneratedImageKind kind, String category, String title, String name, String description, Path file) {
        delegate.newGenericFile(kind, null, title, rename(kind, name, category, null), description, file);
    }
}
