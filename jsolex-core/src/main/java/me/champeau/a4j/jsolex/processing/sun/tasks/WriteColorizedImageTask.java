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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.io.File;
import java.util.function.Function;

public class WriteColorizedImageTask extends AbstractImageWriterTask {
    private final ImageWrapper32 mono;
    private final Function<float[], float[][]> converter;

    public WriteColorizedImageTask(Broadcaster broadcaster,
                                   ImageWrapper32 image,
                                   StretchingStrategy stretchingStrategy,
                                   File outputDirectory,
                                   String title,
                                   String name,
                                   Function<float[], float[][]> converter) {
        super(broadcaster, image, stretchingStrategy, outputDirectory, title, name);
        this.mono = image;
        this.converter = converter;
    }

    @Override
    public ImageWrapper createImageWrapper() {
        return new ColorizedImageWrapper(mono, converter);
    }
}
