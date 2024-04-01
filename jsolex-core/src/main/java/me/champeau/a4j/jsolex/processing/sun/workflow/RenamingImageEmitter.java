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

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * An image emitter which renames the title and file names.
 */
public class RenamingImageEmitter implements ImageEmitter {
    private final ImageEmitter delegate;
    private final UnaryOperator<String> titleRenamer;
    private final UnaryOperator<String> fileRenamer;

    public RenamingImageEmitter(ImageEmitter delegate, UnaryOperator<String> titleRenamer, UnaryOperator<String> fileRenamer) {
        this.delegate = delegate;
        this.titleRenamer = titleRenamer;
        this.fileRenamer = fileRenamer;
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image, Consumer<? super float[]> bufferConsumer) {
        delegate.newMonoImage(kind, titleRenamer.apply(title), fileRenamer.apply(name), image, bufferConsumer);
    }

    @Override
    public void newMonoImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image) {
        delegate.newMonoImage(kind, titleRenamer.apply(title), fileRenamer.apply(name), image);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String title, String name, ImageWrapper32 image, Function<float[], float[][]> rgbSupplier) {
        delegate.newColorImage(kind, titleRenamer.apply(title), fileRenamer.apply(name), image, rgbSupplier);
    }

    @Override
    public void newColorImage(GeneratedImageKind kind, String title, String name, int width, int height, Supplier<float[][]> rgbSupplier) {
        delegate.newColorImage(kind, titleRenamer.apply(title), fileRenamer.apply(name), width, height, rgbSupplier);
    }

    @Override
    public void newGenericFile(GeneratedImageKind kind, String title, String name, Path file) {
        delegate.newGenericFile(kind, titleRenamer.apply(title), fileRenamer.apply(name), file);
    }
}
