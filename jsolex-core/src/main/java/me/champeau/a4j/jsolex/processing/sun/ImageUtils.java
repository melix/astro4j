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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.ChannelExtractingConverter;
import me.champeau.a4j.ser.bayer.DemosaicingRGBImageConverter;
import me.champeau.a4j.ser.bayer.FloatPrecisionImageConverter;
import me.champeau.a4j.ser.bayer.ImageConverter;
import me.champeau.a4j.ser.bayer.VerticalMirrorConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class ImageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);

    public static final float MAX_VALUE = 65535.0f;

    private ImageUtils() {

    }

    public static List<File> writeMonoImage(
        int width,
        int height,
        float[][] data,
        File outputFile,
        Set<ImageFormat> imageFormats) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        short[] converted = ((DataBufferUShort) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                converted[y * width + x] = (short) Math.round(data[y][x]);
            }
        }
        return writeAllFormats(outputFile, imageFormats, image);
    }

    private static List<File> writeAllFormats(File outputFile, Set<ImageFormat> imageFormats, BufferedImage image) {
        try {
            createDirectoryFor(outputFile);
            var baseName = baseNameOf(outputFile);
            return imageFormats.stream()
                .parallel()
                .map(format -> {
                    if (format.equals(ImageFormat.FITS)) {
                        return null;
                    }
                    var output = new File(outputFile.getParentFile(), baseName + format.extension());
                    var formatName = format.name().toLowerCase();
                    var img = image;
                    if (isJpegFormat(formatName) && is16BitGreyscale(img)) {
                        img = convertToRGB(img);
                    }
                    try {
                        ImageIO.write(img, formatName, output);
                        LOGGER.debug("Wrote {}", output);
                    } catch (IOException ex) {
                        throw new ProcessingException(ex);
                    }
                    return output;
                })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static boolean is16BitGreyscale(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_USHORT_GRAY;
    }

    private static boolean isJpegFormat(String formatName) {
        return "jpg".equals(formatName) || "jpeg".equals(formatName);
    }

    private static BufferedImage convertToRGB(BufferedImage source) {
        if (source.getType() != BufferedImage.TYPE_USHORT_GRAY) {
            throw new IllegalArgumentException("Source image must be of type TYPE_USHORT_GRAY.");
        }

        int width = source.getWidth();
        int height = source.getHeight();

        var rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        short[] sourceData = ((java.awt.image.DataBufferUShort) source.getRaster().getDataBuffer()).getData();
        int[] destData = ((java.awt.image.DataBufferInt) rgbImage.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                short grayValue = sourceData[y * width + x];
                int rgbValue = (grayValue >> 8) & 0xFF;
                int rgbColor = (rgbValue << 16) | (rgbValue << 8) | rgbValue;
                destData[y * width + x] = rgbColor;
            }
        }

        return rgbImage;
    }

    private static String baseNameOf(File outputFile) {
        String fileName = outputFile.getName();
        if (Arrays.stream(ImageFormat.values()).anyMatch(imageFormat -> outputFile.getName().endsWith(imageFormat.extension()))) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        }
        return fileName;
    }

    private static void createDirectoryFor(File outputFile) throws IOException {
        var path = outputFile.getParentFile().toPath();
        if (!Files.isDirectory(path)) {
            Files.createDirectories(path);
        }
    }

    public static List<File> writeRgbImage(
        int width,
        int height,
        float[][] r,
        float[][] g,
        float[][] b,
        File outputFile,
        Set<ImageFormat> imageFormats
    ) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] rgb = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = Math.round(r[y][x]);
                int gv = Math.round(g[y][x]);
                int bv = Math.round(b[y][x]);
                rv = (rv >> 8) & 0xFF;
                gv = (gv >> 8) & 0xFF;
                bv = (bv >> 8) & 0xFF;
                rgb[y * width + x] = (rv << 16) | (gv << 8) | bv;
            }
        }
        image.setRGB(0, 0, width, height, rgb, 0, width);
        return writeAllFormats(outputFile, imageFormats, image);
    }

    public static ImageConverter<float[][]> createImageConverter(ColorMode colorMode, boolean vflip) {
        var converter = new FloatPrecisionImageConverter(
            new ChannelExtractingConverter(
                new DemosaicingRGBImageConverter(
                    new BilinearDemosaicingStrategy(),
                    colorMode
                ),
                GREEN
            )
        );
        if (vflip) {
            return new VerticalMirrorConverter(converter);
        }
        return converter;
    }

    public static ImageConverter<float[][]> createImageConverter(ColorMode colorMode) {
        return createImageConverter(colorMode, false);
    }

    public static float[][][] convertToRGB(ColorCurve curve, float[][] mono) {
        var height = mono.length;
        var width = height==0 ? 0 : mono[0].length;
        float[][] r = new float[height][width];
        float[][] g = new float[height][width];
        float[][] b = new float[height][width];
        for (int y = 0; y < height; y++) {
            float[] line = mono[y];
            for (int x = 0; x < line.length; x++) {
                var rgb = curve.toRGB(line[x]);
                r[y][x] = (float) rgb.a();
                g[y][x] = (float) rgb.b();
                b[y][x] = (float) rgb.c();
            }
        }
        return new float[][][]{r, g, b};
    }

    public static void bilinearSmoothing(Ellipse e, int width, int height, float[][] data) {
        double radius = (e.semiAxis().a() + e.semiAxis().b()) / 2;
        double alpha = 1 / radius;
        for (double angle = 0; angle < 2 * Math.PI; angle += alpha) {
            var p = e.toCartesian(angle);
            int x = (int) Math.round(p.x());
            int y = (int) Math.round(p.y());
            if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                data[y][x] = (data[y][(x - 1)] + data[y][x + 1]
                                          + data[y - 1][x] + data[y + 1][x]) / 4;
            }
        }
    }

    public static float[][][] fromRGBtoHSL(float[][][] rgb) {
        return fromRGBtoHSL(rgb, new float[4][rgb[0].length][rgb[0][0].length]);
    }

    public static float[][][] fromRGBtoHSL(float[][][] rgb, float[][][] output) {
        float[][] rChannel = rgb[0];
        float[][] gChannel = rgb[1];
        float[][] bChannel = rgb[2];
        int height = rChannel.length;
        int width = rChannel[0].length;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = rChannel[y][x] / MAX_VALUE;
                float g = gChannel[y][x] / MAX_VALUE;
                float b = bChannel[y][x] / MAX_VALUE;

                float max = Math.max(r, Math.max(g, b));
                float min = Math.min(r, Math.min(g, b));
                float delta = max - min;

                // Calculate the hue
                float hue = 0.0f;
                if (delta == 0) {
                    hue = 0.0f;
                } else if (max == r) {
                    hue = ((g - b) / delta) % 6;
                } else if (max == g) {
                    hue = (b - r) / delta + 2;
                } else if (max == b) {
                    hue = (r - g) / delta + 4;
                }
                hue *= 60.0f;
                if (hue < 0) {
                    hue += 360.0f;
                }

                // Calculate the lightness
                float lightness = (max + min) / 2;

                // Calculate the saturation
                float saturation;
                if (delta == 0) {
                    saturation = 0;
                } else {
                    saturation = delta / (1 - Math.abs(2 * lightness - 1));
                }
                // Handle the case when lightness is close to or equal to zero
                if (lightness <= 0.0001f) {
                    saturation = 0;
                }
                // handle rounding errors
                output[0][y][x] = Math.max(0, Math.min(360, hue));
                output[1][y][x] = Math.max(0, Math.min(saturation, 1.0f));
                output[2][y][x] = Math.max(0, Math.min(lightness, 1.0f));
            }
        }

        return output;
    }

    public static float[][][] fromHSLtoRGB(float[][][] hsl) {
        return fromHSLtoRGB(hsl, new float[4][hsl[0].length][hsl[0][0].length]);
    }

    public static float[][][] fromHSLtoRGB(float[][][] hsl, float[][][] output) {
        float[][] hChannel = hsl[0];
        float[][] sChannel = hsl[1];
        float[][] lChannel = hsl[2];
        int height = hChannel.length;
        int width = hChannel[0].length;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float h = hChannel[y][x];
                float s = sChannel[y][x];
                float l = lChannel[y][x];

                // Calculate chroma
                float chroma = (1 - Math.abs(2 * l - 1)) * s;

                // Calculate hue segment and offset within segment
                float hueSegment = h / 60.0f;
                float hueOffset = hueSegment - (float) Math.floor(hueSegment);

                // Calculate intermediate values
                float k = chroma * (1 - Math.abs(hueOffset - 1));
                float m = l - chroma / 2;

                float r, g, b;
                if (hueSegment < 1) {
                    r = chroma;
                    g = k;
                    b = 0;
                } else if (hueSegment < 2) {
                    r = k;
                    g = chroma;
                    b = 0;
                } else if (hueSegment < 3) {
                    r = 0;
                    g = chroma;
                    b = k;
                } else if (hueSegment < 4) {
                    r = 0;
                    g = k;
                    b = chroma;
                } else if (hueSegment < 5) {
                    r = k;
                    g = 0;
                    b = chroma;
                } else {
                    r = chroma;
                    g = 0;
                    b = k;
                }

                // Adjust values by adding the lightness offset
                output[0][y][x] = (r + m) * MAX_VALUE;
                output[1][y][x] = (g + m) * MAX_VALUE;
                output[2][y][x] = (b + m) * MAX_VALUE;
            }
        }

        return output;
    }

}
