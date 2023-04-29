/*
 * Copyright 20023-2023 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.WriteColorImageTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.WriteMonoImageTask;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;

import java.io.File;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An image emitter is a utility tool to generate
 * mono or color images with transformations.
 */
class ImageEmitter {
    private final Broadcaster broadcaster;
    private final ParallelExecutor executor;
    private final File outputDir;

    ImageEmitter(Broadcaster broadcaster,
                         ParallelExecutor executor,
                         File outputDir) {
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.outputDir = outputDir;
    }

    public Future<Void> newMonoImage(String title, String name, ImageWrapper32 image, StretchingStrategy stretchingStrategy, Consumer<? super float[]> bufferConsumer) {
        return executor.submit(new WriteMonoImageTask(broadcaster,
                image,
                stretchingStrategy,
                outputDir,
                title,
                name
        ) {
            @Override
            public void transform() {
                bufferConsumer.accept(getBuffer());
            }
        });
    }

    public Future<Void> newMonoImage(String title, String name, ImageWrapper32 image, StretchingStrategy stretchingStrategy) {
        return executor.submit(new WriteMonoImageTask(broadcaster,
                image,
                stretchingStrategy,
                outputDir,
                title,
                name
        ));
    }

    public Future<Void> newColorImage(String title, String name, ImageWrapper32 image, StretchingStrategy stretchingStrategy, Function<float[], float[][]> rgbSupplier) {
        return executor.submit(new WriteColorImageTask(broadcaster,
                image,
                stretchingStrategy,
                outputDir,
                title,
                name,
                rgbSupplier)
        );
    }
}
