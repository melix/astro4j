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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageUtils {
    public static void writeMonoImage(
            int width,
            int height,
            double[] data,
            File outputFile
    ) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int value = (int) (data[y * width + x]);
                image.setRGB(x, y, value << 16 | value << 8 | value);
            }
        }
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeRgbImage(
            int width,
            int height,
            double[] r,
            double[] g,
            double[] b,
            File outputFile
    ) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = (int) (r[y * width + x]);
                int gv = (int) (g[y * width + x]);
                int bv = (int) (b[y * width + x]);
                image.setRGB(x, y, rv << 16 | gv << 8 | bv);
            }
        }
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
