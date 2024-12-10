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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class Loader extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Loader.class);
    private static final Set<String> RECOGNIZED_IMAGE_FORMATS = Set.of(
        "png",
        "jpg",
        "jpeg",
        "tif",
        "tiff",
        "fits",
        "fit"
    );

    public Loader(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    private Path workingDirectory = new File(".").getAbsoluteFile().toPath();

    public Object load(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("load takes 1 arguments (image file(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("load", arguments, this::load);
        }
        if (arg instanceof String path) {
            var file = workingDirectory.resolve(path).toFile();
            return loadImage(file);
        }
        throw new IllegalArgumentException("Unsupported argument '" + arg + "' to load()");
    }

    public static ImageWrapper loadImage(File file) {
        var imageWrapper = doLoad(file);
        imageWrapper.findMetadata(MetadataTable.class).ifPresentOrElse(metadata -> {
            metadata.properties().put(MetadataTable.FILE_NAME, file.getName());
        }, () -> {
            var metadata = MutableMap.<String, String>of();
            metadata.put(MetadataTable.FILE_NAME, file.getName());
            imageWrapper.metadata().put(MetadataTable.class, new MetadataTable(metadata));
        });
        return imageWrapper;
    }

    private static ImageWrapper doLoad(File file) {
        if (file.getName().toLowerCase(Locale.US).endsWith(".fits")) {
            return FitsUtils.readFitsFile(file);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
        var metadata = MutableMap.<Class<?>, Object>of();
        return toImageWrapper(image, metadata);
    }

    static ImageWrapper toImageWrapper(BufferedImage image, Map<Class<?>, Object> metadata) {
        var width = image.getWidth();
        var height = image.getHeight();
        var colorModel = image.getColorModel();
        var size = width * height;
        if (colorModel.getNumComponents() >= 3) {
            var r = new float[height][width];
            var g = new float[height][width];
            var b = new float[height][width];
            var color = image.getRGB(0, 0, width, height, null, 0, width);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var pixel = color[y * width + x];
                    r[y][x] = ((pixel >> 16) & 0xFF) << 8;
                    g[y][x] = ((pixel >> 8) & 0xFF) << 8;
                    b[y][x] = (pixel & 0xFF) << 8;
                }
            }
            return new RGBImage(width, height, r, g, b, metadata);
        } else {
            var data = new float[height][width];
            var dataBuffer = image.getRaster().getDataBuffer();
            if (dataBuffer instanceof DataBufferUShort shortBuffer) {
                // 16-bit image
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        data[y][x] = shortBuffer.getElemFloat(y * width + x);
                    }
                }
            } else {
                var raster = image.getRaster();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        // Get raw sample value and scale to 16-bit
                        data[y][x] = raster.getSample(x, y, 0) << 8;
                    }
                }
            }
            return new ImageWrapper32(width, height, data, metadata);
        }
    }

    public List<ImageWrapper> loadMany(List<Object> arguments) {
        if (arguments.size() > 2) {
            throw new IllegalArgumentException("loadMany takes 1 or 2 arguments (directory, [pattern])");
        }
        var directory = (String) arguments.get(0);
        var pattern = Pattern.compile(arguments.size() == 2 ? (String) arguments.get(1) : ".*");
        var lookupDir = workingDirectory.resolve(directory);
        if (Files.isDirectory(lookupDir)) {
            try (var stream = Files.list(lookupDir)) {
                return stream.map(Path::toFile)
                    .filter(p -> pattern.matcher(p.getName()).matches())
                    .filter(Loader::isImageFile)
                    .parallel()
                    .map(Loader::loadImage)
                    .toList();
            } catch (IOException e) {
                LOGGER.error("Unable to load files", e);
                return List.of();
            }
        }
        return List.of();
    }

    private static boolean isImageFile(File file) {
        var name = file.getName();
        if (!name.contains(".")) {
            return false;
        }
        var extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase(Locale.US);
        return RECOGNIZED_IMAGE_FORMATS.contains(extension);
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Object chooseFile(List<Object> arguments) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("choose_file takes 1 arguments (id, message)");
        }
        var id = stringArg(arguments, 0);
        var message = stringArg(arguments, 1);
        var chooser = (FileSelector) context.get(FileSelector.class);
        if (chooser == null) {
            chooser = new FileSelector() {
                @Override
                public Optional<File> chooseFile(String id, String title) {
                    return Optional.empty();
                }

                @Override
                public Optional<List<File>> chooseFiles(String id, String title) {
                    return Optional.empty();
                }
            };
        }
        var file = chooser.chooseFile(id, message);
        return file.map(Loader::loadImage).orElse(ImageWrapper32.createEmpty());
    }

    public Object chooseFiles(List<Object> arguments) {
        if (arguments.size() != 2) {
            throw new IllegalArgumentException("choose_files takes 1 arguments (id, message)");
        }
        var id = stringArg(arguments, 0);
        var message = stringArg(arguments, 1);
        var chooser = (FileSelector) context.get(FileSelector.class);
        if (chooser == null) {
            throw new IllegalStateException("No file selector registered");
        }
        var files = chooser.chooseFiles(id, message);
        return files.<Object>map(fileList -> fileList
            .stream()
            .map(Loader::loadImage)
            .toList()).orElse(List.of());
    }
}
