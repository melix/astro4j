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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

public class WritableImageSupport {
    private WritableImageSupport() {
    }

    public static WritableImage asWritable(ImageWrapper source) {
        return switch (source) {
            case ImageWrapper32 mono -> {
                var image = new WritableImage(mono.width(), mono.height());
                var writer = image.getPixelWriter();
                var row = new float[mono.width()];
                byte[] pixels = new byte[3 * mono.width() * mono.height()];
                for (int y = 0; y < mono.height(); y++) {
                    mono.pixels().getRow(y, row);
                    for (int x = 0; x < mono.width(); x++) {
                        int value = (int) row[x];
                        var v = (byte) (value >> 8);
                        var offset = 3 * (y * mono.width() + x);
                        pixels[offset] = v;
                        pixels[offset + 1] = v;
                        pixels[offset + 2] = v;
                    }
                }
                writer.setPixels(0, 0, mono.width(), mono.height(), PixelFormat.getByteRgbInstance(), pixels, 0, 3 * mono.width());
                yield image;
            }
            case RGBImage rgb -> {
                var image = new WritableImage(rgb.width(), rgb.height());
                var writer = image.getPixelWriter();
                var r = new float[rgb.width()];
                var g = new float[rgb.width()];
                var b = new float[rgb.width()];
                byte[] pixels = new byte[3 * rgb.width() * rgb.height()];
                for (int y = 0; y < rgb.height(); y++) {
                    rgb.red().getRow(y, r);
                    rgb.green().getRow(y, g);
                    rgb.blue().getRow(y, b);
                    for (int x = 0; x < rgb.width(); x++) {
                        int vr = (int) r[x];
                        int vg = (int) g[x];
                        int vb = (int) b[x];
                        int offset = 3 * (y * rgb.width() + x);
                        pixels[offset] = (byte) (vr >> 8);
                        pixels[offset + 1] = (byte) (vg >> 8);
                        pixels[offset + 2] = (byte) (vb >> 8);
                    }
                }
                writer.setPixels(0, 0, rgb.width(), rgb.height(), PixelFormat.getByteRgbInstance(), pixels, 0, 3 * rgb.width());
                yield image;
            }
        };
    }
}
