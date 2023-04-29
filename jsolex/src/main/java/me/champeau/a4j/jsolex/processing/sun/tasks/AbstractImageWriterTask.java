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

import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.stretching.StretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.io.File;

public abstract class AbstractImageWriterTask extends AbstractTask<Void> {
    private final StretchingStrategy stretchingStrategy;
    private final File outputDirectory;
    private final String title;
    private final String name;

    protected AbstractImageWriterTask(Broadcaster broadcaster,
                                      ImageWrapper32 image,
                                      StretchingStrategy stretchingStrategy,
                                      File outputDirectory,
                                      String title,
                                      String name) {
        super(broadcaster, image);
        this.stretchingStrategy = stretchingStrategy;
        this.outputDirectory = outputDirectory;
        this.title = title;
        this.name = name;
    }

    public void transform() {

    }

    @Override
    public final Void call() {
        transform();
        File outputFile = new File(outputDirectory, name + ".png");
        var image = new GeneratedImage(title, outputFile.toPath(), createImageWrapper(), stretchingStrategy);
        broadcaster.broadcast(new ImageGeneratedEvent(image));
        return null;
    }

    public abstract ImageWrapper createImageWrapper();
}
