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
package me.champeau.a4j.jsolex.processing.util;

public class ThumbnailGenerator {
    public static ImageWrapper generateThumbnail(ImageWrapper source, int maxWidth, int maxHeight) {
        if (source instanceof FileBackedImage fbi) {
            source = fbi.unwrapToMemory();
        }
        var sourceWidth = source.width();
        var sourceHeight = source.height();

        if (sourceWidth <= maxWidth && sourceHeight <= maxHeight) {
            return source;
        }

        var scaleX = (double) maxWidth / sourceWidth;
        var scaleY = (double) maxHeight / sourceHeight;
        var scale = Math.min(scaleX, scaleY);

        var newWidth = (int) (sourceWidth * scale);
        var newHeight = (int) (sourceHeight * scale);

        if (source instanceof RGBImage rgb) {
            return createRGBThumbnail(rgb, newWidth, newHeight);
        } else if (source instanceof ImageWrapper32 mono) {
            return createMonoThumbnail(mono, newWidth, newHeight);
        }

        return source;
    }

    private static ImageWrapper createMonoThumbnail(ImageWrapper32 source, int newWidth, int newHeight) {
        var sourceData = source.data();
        var thumbnailData = new float[newHeight][newWidth];

        var scaleX = (double) source.width() / newWidth;
        var scaleY = (double) source.height() / newHeight;

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                var sourceX = (int) (x * scaleX);
                var sourceY = (int) (y * scaleY);

                sourceX = Math.min(sourceX, source.width() - 1);
                sourceY = Math.min(sourceY, source.height() - 1);

                thumbnailData[y][x] = sourceData[sourceY][sourceX];
            }
        }

        return new ImageWrapper32(newWidth, newHeight, thumbnailData, MutableMap.of());
    }

    private static ImageWrapper createRGBThumbnail(RGBImage source, int newWidth, int newHeight) {
        var sourceR = source.r();
        var sourceG = source.g();
        var sourceB = source.b();

        var thumbnailR = new float[newHeight][newWidth];
        var thumbnailG = new float[newHeight][newWidth];
        var thumbnailB = new float[newHeight][newWidth];

        var scaleX = (double) source.width() / newWidth;
        var scaleY = (double) source.height() / newHeight;

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                var sourceX = (int) (x * scaleX);
                var sourceY = (int) (y * scaleY);

                sourceX = Math.min(sourceX, source.width() - 1);
                sourceY = Math.min(sourceY, source.height() - 1);

                thumbnailR[y][x] = sourceR[sourceY][sourceX];
                thumbnailG[y][x] = sourceG[sourceY][sourceX];
                thumbnailB[y][x] = sourceB[sourceY][sourceX];
            }
        }

        return new RGBImage(newWidth, newHeight, thumbnailR, thumbnailG, thumbnailB, MutableMap.of());
    }
}