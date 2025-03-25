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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.io.File;
import java.util.function.Supplier;

public class WriteRGBImageTask extends AbstractImageWriterTask {
    private final Supplier<float[][][]> supplier;

    public WriteRGBImageTask(Broadcaster broadcaster,
                             ProgressOperation operation,
                             Supplier<ImageWrapper32> ref,
                             File outputDirectory,
                             String title,
                             String name,
                             String description,
                             GeneratedImageKind kind,
                             Supplier<float[][][]> supplier) {
        super(broadcaster, operation, ref, outputDirectory, title, name, description, kind);
        this.supplier = supplier;
    }

    @Override
    public ImageWrapper createImageWrapper() {
        var rgb = supplier.get();
        return new RGBImage(width, height, rgb[0], rgb[1], rgb[2], getMetadata());
    }
}
