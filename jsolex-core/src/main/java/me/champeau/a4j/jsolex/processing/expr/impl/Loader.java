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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.RawImageIO;
import me.champeau.a4j.jsolex.processing.util.SDO;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class Loader extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Loader.class);
    private static final String DEFAULT_SDO_CHANNEL = "0094";
    private static final String SDO_CHANNEL = "sdoChannel";
    private static final String SDO_DATE = "sdoDate";
    private static final Set<String> RECOGNIZED_IMAGE_FORMATS = Set.of(
            "png",
            "jpg",
            "jpeg",
            "tif",
            "tiff",
            "fits",
            "fit",
            RawImageIO.EXTENSION
    );

    public Loader(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    private Path workingDirectory = new File(".").getAbsoluteFile().toPath();

    public Object load(Map<String, Object> arguments) {
        BuiltinFunction.LOAD.validateArgs(arguments);
        var arg = arguments.get("file");
        if (arg instanceof List<?>) {
            return expandToImageList("load", "file", arguments, this::load);
        }
        if (arg instanceof String path) {
            var file = workingDirectory.resolve(path).toFile();
            return loadImage(file);
        }
        throw new IllegalArgumentException("Unsupported argument '" + arg + "' to load()");
    }

    /**
     * Downloads the SDO browse image which is the closest in time to the requested date.
     * The solar disk is computed from the plate scale of the instrument and the apparent
     * size of the Sun at that date, and attached to the image, so that all the functions
     * which need a solar disk can be used on the result.
     *
     * @param arguments function arguments containing:
     *                  - resolution: the width of the frame, in pixels
     *                  - channel: the channel code, defaults to 0094
     *                  - date: the requested date, defaults to the date of the observation
     * @return the SDO image
     */
    public Object loadSdo(Map<String, Object> arguments) {
        BuiltinFunction.LOAD_SDO.validateArgs(arguments);
        var resolution = intArg(arguments, "resolution", 0);
        if (resolution <= 0) {
            throw new IllegalArgumentException("load_sdo requires a resolution in pixels (512, 1024, 2048 or 4096)");
        }
        var channel = stringArg(arguments, "channel", DEFAULT_SDO_CHANNEL);
        var date = sdoDate(arguments);
        var candidate = SDO.findClosest(date, channel, resolution)
                .orElseThrow(() -> new ProcessingException("No SDO image found for channel '" + channel + "' at resolution " + resolution + " around " + date));
        var url = SDO.fetchCandidateImage(candidate)
                .orElseThrow(() -> new ProcessingException("Unable to download SDO image " + candidate.fileName()));
        BufferedImage image;
        try (var stream = url.openStream()) {
            image = ImageIO.read(stream);
        } catch (IOException e) {
            throw new ProcessingException("Unable to read SDO image " + candidate.fileName(), e);
        }
        if (image == null) {
            throw new ProcessingException("Unable to decode SDO image " + candidate.fileName());
        }
        var properties = MutableMap.<String, String>of();
        properties.put(MetadataTable.FILE_NAME, candidate.fileName());
        properties.put(SDO_CHANNEL, candidate.channel());
        properties.put(SDO_DATE, candidate.timestamp().format(DateTimeFormatter.ISO_INSTANT));
        var metadata = MutableMap.<Class<?>, Object>of();
        metadata.put(MetadataTable.class, new MetadataTable(properties));
        metadata.put(Ellipse.class, solarDisk(image.getWidth(), image.getHeight(), candidate.solarDiskRadius()));
        LOGGER.info("Using SDO {} image taken at {}", candidate.channel(), candidate.timestamp());
        return toImageWrapper(image, metadata);
    }

    private ZonedDateTime sdoDate(Map<String, Object> arguments) {
        var date = arguments.get("date");
        if (date instanceof ImageWrapper image) {
            return observationDate(image.findMetadata(ProcessParams.class).orElse(null));
        }
        if (date instanceof String text && !text.isBlank()) {
            try {
                return ZonedDateTime.parse(text);
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(text).atZone(ZoneId.of("UTC"));
            }
        }
        return observationDate(null);
    }

    private ZonedDateTime observationDate(ProcessParams params) {
        var resolved = params != null ? params : getFromContext(ProcessParams.class).orElse(null);
        if (resolved == null) {
            throw new ProcessingException("load_sdo requires a date, which could not be determined from the observation");
        }
        return resolved.observationDetails().date();
    }

    /**
     * Builds the conic of a circle of the given radius, centered in an image of the
     * given dimensions.
     */
    private static Ellipse solarDisk(int width, int height, double radius) {
        var cx = (width - 1) / 2d;
        var cy = (height - 1) / 2d;
        return Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -2 * cx, -2 * cy, cx * cx + cy * cy - radius * radius));
    }

    /**
     * Saves an image and its metadata as raw {@code float[][]} data, without any rescaling
     * to a display range, so that the exact values can be reloaded later with {@code load}.
     * The image is returned unchanged, so the call can be inserted in a processing chain.
     *
     * @param arguments function arguments containing:
     *                  - img: the image to save
     *                  - file: the destination file, relative to the working directory; the
     *                  raw extension is appended if missing
     * @return the image
     */
    public Object saveRaw(Map<String, Object> arguments) {
        BuiltinFunction.SAVE_RAW.validateArgs(arguments);
        var arg = arguments.get("img");
        if (!(arguments.get("file") instanceof String path)) {
            throw new IllegalArgumentException("save_raw requires an image and a file name");
        }
        if (arg instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof ImageWrapper image)) {
                    throw new IllegalArgumentException("save_raw requires an image and a file name");
                }
                writeRaw(image, indexedPath(path, i));
            }
            return arg;
        }
        if (arg instanceof ImageWrapper image) {
            writeRaw(image, path);
            return image;
        }
        throw new IllegalArgumentException("save_raw requires an image and a file name");
    }

    private void writeRaw(ImageWrapper image, String path) {
        var name = path.toLowerCase(Locale.US).endsWith("." + RawImageIO.EXTENSION) ? path : path + "." + RawImageIO.EXTENSION;
        var target = workingDirectory.resolve(name);
        RawImageIO.write(image, target);
        LOGGER.info("Saved raw image to {}", target);
    }

    private static String indexedPath(String path, int index) {
        var suffix = "." + RawImageIO.EXTENSION;
        if (path.toLowerCase(Locale.US).endsWith(suffix)) {
            return path.substring(0, path.length() - suffix.length()) + "_" + index + suffix;
        }
        return path + "_" + index;
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
        var extension = file.getName().toLowerCase(Locale.US);
        if (extension.endsWith(".fits")) {
            return FitsUtils.readFitsFile(file);
        }
        if (extension.endsWith("." + RawImageIO.EXTENSION)) {
            return RawImageIO.read(file.toPath());
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

    public static ImageWrapper toImageWrapper(BufferedImage image, Map<Class<?>, Object> metadata) {
        var width = image.getWidth();
        var height = image.getHeight();
        var colorModel = image.getColorModel();
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

    public List<ImageWrapper> loadMany(Map<String, Object> arguments) {
        BuiltinFunction.LOAD_MANY.validateArgs(arguments);
        var directory = (String) arguments.get("dir");
        var pattern = Pattern.compile(stringArg(arguments, "pattern", ".*"));
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

    public Object chooseFile(Map<String, Object> arguments) {
        BuiltinFunction.CHOOSE_FILES.validateArgs(arguments);
        var id = stringArg(arguments, "id", null);
        var message = stringArg(arguments, "message", null);
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

    public Object chooseFiles(Map<String, Object> arguments) {
        BuiltinFunction.CHOOSE_FILES.validateArgs(arguments);
        var id = stringArg(arguments, "id", null);
        var message = stringArg(arguments, "message", null);
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
