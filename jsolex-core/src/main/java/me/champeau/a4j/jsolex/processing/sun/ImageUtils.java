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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class ImageUtils {
    private static final List<String> EXTENSIONS = List.of("png", "jpg", "tif");
    public static final float MAX_VALUE = 65535.0f;

    private ImageUtils() {

    }

    public static void writeMonoImage(
            int width,
            int height,
            float[] data,
            File outputFile,
            Set<ImageFormat> imageFormats) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        short[] converted = ((DataBufferUShort) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < converted.length; i++) {
            converted[i] = (short) Math.round(data[i]);
        }
        writeAllFormats(outputFile, imageFormats, image);
    }

    private static void writeAllFormats(File outputFile, Set<ImageFormat> imageFormats, BufferedImage image) {
        try {
            createDirectoryFor(outputFile);
            var baseName = baseNameOf(outputFile);
            for (ImageFormat format : imageFormats) {
                if (format.equals(ImageFormat.FITS)) {
                    continue;
                }
                ImageIO.write(image, format.name().toLowerCase(), new File(outputFile.getParentFile(), baseName + format.extension()));
            }
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
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
        Files.createDirectories(path);
    }

    public static void writeRgbImage(
            int width,
            int height,
            float[] r,
            float[] g,
            float[] b,
            File outputFile,
            Set<ImageFormat> imageFormats
    ) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = Math.round(r[y * width + x]);
                int gv = Math.round(g[y * width + x]);
                int bv = Math.round(b[y * width + x]);
                rv = (rv >> 8) & 0xFF;
                gv = (gv >> 8) & 0xFF;
                bv = (bv >> 8) & 0xFF;
                image.setRGB(x, y, rv << 16 | gv << 8 | bv);
            }
        }
        writeAllFormats(outputFile, imageFormats, image);
    }

    public static ImageConverter<float[]> createImageConverter(ColorMode colorMode) {
        return new FloatPrecisionImageConverter(
                new ChannelExtractingConverter(
                        new DemosaicingRGBImageConverter(
                                new BilinearDemosaicingStrategy(),
                                colorMode
                        ),
                        GREEN
                )
        );
    }

    public static float[][] convertToRGB(ColorCurve curve, float[] mono) {
        float[] r = new float[mono.length];
        float[] g = new float[mono.length];
        float[] b = new float[mono.length];
        for (int i = 0; i < mono.length; i++) {
            var rgb = curve.toRGB(mono[i]);
            r[i] = (float) rgb.a();
            g[i] = (float) rgb.b();
            b[i] = (float) rgb.c();
        }
        return new float[][]{r, g, b};
    }

    public static void bilinearSmoothing(Ellipse e, int width, int height, float[] data) {
        double radius = (e.semiAxis().a() + e.semiAxis().b()) / 2;
        double alpha = 1 / radius;
        for (double angle = 0; angle < 2 * Math.PI; angle += alpha) {
            var p = e.toCartesian(angle);
            int x = (int) Math.round(p.x());
            int y = (int) Math.round(p.y());
            if (x > 0 && x < width - 1 && y > 0 && y < height - 1) {
                data[x + y * width] = (data[(x - 1) + y * width] + data[x + 1 + y * width]
                                       + data[x + (y - 1) * width] + data[x + (y + 1) * width]) / 4;
            }
        }
    }

    public static float[][] fromRGBtoHSL(float[][] rgb) {
        return fromRGBtoHSL(rgb, new float[3][rgb[0].length]);
    }

    public static float[][] fromRGBtoHSL(float[][] rgb, float[][] output) {
        float[] rChannel = rgb[0];
        float[] gChannel = rgb[1];
        float[] bChannel = rgb[2];
        int size = rChannel.length;

        for (int i = 0; i < size; i++) {
            float r = rChannel[i] / MAX_VALUE;
            float g = gChannel[i] / MAX_VALUE;
            float b = bChannel[i] / MAX_VALUE;

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
            output[0][i] = Math.max(0, Math.min(360, hue));
            output[1][i] = Math.max(0, Math.min(saturation, 1.0f));
            output[2][i] = Math.max(0, Math.min(lightness, 1.0f));
        }

        return output;
    }

    public static float[][] fromHSLtoRGB(float[][] hsl) {
        return fromHSLtoRGB(hsl, new float[3][hsl[0].length]);
    }

    public static float[][] fromHSLtoRGB(float[][] hsl, float[][] output) {
        float[] hChannel = hsl[0];
        float[] sChannel = hsl[1];
        float[] lChannel = hsl[2];
        int size = hChannel.length;

        for (int i = 0; i < size; i++) {
            float h = hChannel[i];
            float s = sChannel[i];
            float l = lChannel[i];

            // Calculate chroma
            float chroma = (1 - Math.abs(2 * l - 1)) * s;

            // Calculate hue segment and offset within segment
            float hueSegment = h / 60.0f;
            float hueOffset = hueSegment - (float) Math.floor(hueSegment);

            // Calculate intermediate values
            float x = chroma * (1 - Math.abs(hueOffset - 1));
            float m = l - chroma / 2;

            float r, g, b;
            if (hueSegment < 1) {
                r = chroma;
                g = x;
                b = 0;
            } else if (hueSegment < 2) {
                r = x;
                g = chroma;
                b = 0;
            } else if (hueSegment < 3) {
                r = 0;
                g = chroma;
                b = x;
            } else if (hueSegment < 4) {
                r = 0;
                g = x;
                b = chroma;
            } else if (hueSegment < 5) {
                r = x;
                g = 0;
                b = chroma;
            } else {
                r = chroma;
                g = 0;
                b = x;
            }

            // Adjust values by adding the lightness offset
            output[0][i] = (r + m) * MAX_VALUE;
            output[1][i] = (g + m) * MAX_VALUE;
            output[2][i] = (b + m) * MAX_VALUE;
        }

        return output;
    }

}
