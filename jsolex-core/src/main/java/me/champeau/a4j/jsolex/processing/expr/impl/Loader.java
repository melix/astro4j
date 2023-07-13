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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.expandToImageList;

public class Loader extends AbstractFunctionImpl {

    public Loader(ForkJoinContext forkJoinContext, Map<Class<?>, Object> context) {
        super(forkJoinContext, context);
    }

    private Path workingDirectory = new File(".").getAbsoluteFile().toPath();

    public Object load(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("load takes 1 arguments (image file(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList(forkJoinContext, arguments, this::load);
        }
        if (arg instanceof String path) {
            BufferedImage image;
            try {
                image = ImageIO.read(workingDirectory.resolve(path).toFile());
            } catch (IOException e) {
                throw new ProcessingException(e);
            }
            var width = image.getWidth();
            var height = image.getHeight();
            var colorModel = image.getColorModel();
            var size = width * height;
            if (colorModel.getNumComponents() == 3) {
                var r = new float[size];
                var g = new float[size];
                var b = new float[size];
                var color = image.getRGB(0, 0, width, height, null, 0, width);
                for (int i = 0; i < size; i++) {
                    var pixel = color[i];
                    r[i] = (pixel >> 16) & 0xFF;
                    g[i] = (pixel >> 8) & 0xFF;
                    b[i] = pixel & 0xFF;
                }
                return new RGBImage(width, height, r, g, b);
            } else {
                var data = new float[size];
                var dataBuffer = image.getRaster().getDataBuffer();
                if (dataBuffer instanceof DataBufferUShort shortBuffer) {
                    // 16-bit image
                    for (int i = 0; i < size; i++) {
                        data[i] = shortBuffer.getElemFloat(i);
                    }
                } else {
                    var rgb = image.getRGB(0, 0, width, height, null, 0, width);
                    for (int i = 0; i < data.length; i++) {
                        data[i] = rgb[i] & 0xFF;
                    }
                }
                return new ImageWrapper32(width, height, data);
            }
        }
        throw new IllegalArgumentException("Unsupported argument '" + arg + "' to load()");
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
}
