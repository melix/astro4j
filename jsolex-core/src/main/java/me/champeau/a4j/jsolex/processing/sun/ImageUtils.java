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
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.EightBitConversionSupport;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.ChannelExtractingConverter;
import me.champeau.a4j.ser.bayer.DemosaicingRGBImageConverter;
import me.champeau.a4j.ser.bayer.FloatPrecisionImageConverter;
import me.champeau.a4j.ser.bayer.ImageConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class ImageUtils {
    private static final List<String> EXTENSIONS = List.of("png", "jpg", "tif");

    private ImageUtils() {

    }

    public static void writeMonoImage(
            int width,
            int height,
            float[] data,
            File outputFile,
            Set<ImageFormat> imageFormats) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] converted = EightBitConversionSupport.to8BitImage(data);
        int[] rgb = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int value = converted[y * width + x] & 0xFF;
                rgb[x + y*width] = value << 16 | value << 8 | value;
            }
        }
        image.setRGB(0, 0, width, height, rgb, 0, width);
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
        LinearStrechingStrategy.DEFAULT.stretch(mono);
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
}
