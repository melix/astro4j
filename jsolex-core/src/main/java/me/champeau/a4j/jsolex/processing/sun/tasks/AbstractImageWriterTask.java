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
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.io.File;
import java.util.function.Supplier;

/** Abstract base class for tasks that write images to files. */
public abstract class AbstractImageWriterTask extends AbstractTask<Void> {
    private final File outputDirectory;
    private final String title;
    private final String name;
    private final GeneratedImageKind kind;
    private final String description;

    /**
     * Creates an image writer task.
     *
     * @param broadcaster the broadcaster
     * @param operation the progress operation
     * @param image the image supplier
     * @param outputDirectory the output directory
     * @param title the image title
     * @param name the output file name
     * @param description the image description
     * @param kind the image kind
     */
    protected AbstractImageWriterTask(Broadcaster broadcaster,
                                      ProgressOperation operation,
                                      Supplier<ImageWrapper32> image,
                                      File outputDirectory,
                                      String title,
                                      String name,
                                      String description,
                                      GeneratedImageKind kind) {
        super(broadcaster, operation, image);
        this.outputDirectory = outputDirectory;
        this.title = title;
        this.name = name;
        this.kind = kind;
        this.description = description;
    }

    /** Transforms the image before writing. Subclasses can override this method. */
    public void transform() {

    }

    @Override
    protected final Void doCall() {
        transform();
        File outputFile = new File(outputDirectory, name);
        var image = new GeneratedImage(kind, title, outputFile.toPath(), createImageWrapper(), description);
        broadcaster.broadcast(new ImageGeneratedEvent(image));
        return null;
    }

    /**
     * Creates the image wrapper to be saved.
     *
     * @return the image wrapper
     */
    public abstract ImageWrapper createImageWrapper();
}
