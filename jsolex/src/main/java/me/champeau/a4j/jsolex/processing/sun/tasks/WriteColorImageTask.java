/*
 * Copyright 2003-2021 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;

import java.io.File;

public abstract class WriteColorImageTask extends AbstractImageWriterTask {
    /**
     * Creates an abstract task
     *
     * @param buffer the image buffer. A copy will be created in the
     * constructor, so that this task works with its own buffer
     */
    public WriteColorImageTask(Broadcaster broadcaster, float[] buffer, int width, int height, File outputDirectory, String title, String name) {
        super(broadcaster, buffer, width, height, outputDirectory, title, name);
    }

    public abstract float[][] getRGB();


    @Override
    public final void writeImage(File outputFile) {
        float[][] rgb = getRGB();
        float[] r = rgb[0];
        float[] g = rgb[1];
        float[] b = rgb[2];
        ImageUtils.writeRgbImage(width, height, r, g, b, outputFile);
    }

}
