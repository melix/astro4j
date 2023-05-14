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

import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ImageEmitter {
    Future<Void> newMonoImage(WorkflowStep step, String title, String name, ImageWrapper32 image, StretchingStrategy stretchingStrategy, Consumer<? super float[]> bufferConsumer);

    Future<Void> newMonoImage(WorkflowStep step, String title, String name, ImageWrapper32 image, StretchingStrategy stretchingStrategy);

    Future<Void> newColorImage(WorkflowStep step, String title, String name, ImageWrapper32 image, StretchingStrategy stretchingStrategy, Function<float[], float[][]> rgbSupplier);

    Future<Void> newColorImage(WorkflowStep step, String title, String name, StretchingStrategy stretchingStrategy, int width, int height, Supplier<float[][]> rgbSupplier);
}