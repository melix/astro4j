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
import me.champeau.a4j.jsolex.processing.sun.CollageParameters;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class CollageComposition extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollageComposition.class);

    public CollageComposition(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public ImageWrapper createCollage(CollageParameters parameters) {
        var progressOperation = newOperation().createChild(message("creating.collage"));
        broadcaster.broadcast(progressOperation);

        try {
            var layout = calculateLayout(parameters);
            var metadata = MutableMap.<Class<?>, Object>of();

            for (var imageSelection : parameters.images()) {
                metadata.putAll(imageSelection.image().metadata());
            }

            var hasColorImages = parameters.images().stream()
                    .anyMatch(img -> img.image() instanceof RGBImage);

            if (hasColorImages) {
                var collageData = createColorCollageData(parameters, layout);
                return new RGBImage(layout.totalWidth(), layout.totalHeight(), collageData[0], collageData[1], collageData[2], metadata);
            } else {
                var collageData = createMonoCollageData(parameters, layout);
                return new ImageWrapper32(layout.totalWidth(), layout.totalHeight(), collageData, metadata);
            }
        } finally {
            broadcaster.broadcast(progressOperation.complete());
        }
    }

    private CollageLayout calculateLayout(CollageParameters parameters) {
        var rows = parameters.rows();
        var cols = parameters.columns();
        var padding = parameters.padding();

        var maxCellWidth = 0;
        var maxCellHeight = 0;

        for (var imageSelection : parameters.images()) {
            var img = imageSelection.image();
            maxCellWidth = Math.max(maxCellWidth, img.width());
            maxCellHeight = Math.max(maxCellHeight, img.height());
        }

        var totalWidth = cols * maxCellWidth + (cols - 1) * padding;
        var totalHeight = rows * maxCellHeight + (rows - 1) * padding;

        if (parameters.downscaleIfNeeded() &&
            (totalWidth > parameters.maxOutputWidth() || totalHeight > parameters.maxOutputHeight())) {

            var scaleX = (double) parameters.maxOutputWidth() / totalWidth;
            var scaleY = (double) parameters.maxOutputHeight() / totalHeight;
            var scale = Math.min(scaleX, scaleY);

            maxCellWidth = (int) (maxCellWidth * scale);
            maxCellHeight = (int) (maxCellHeight * scale);
            totalWidth = (int) (totalWidth * scale);
            totalHeight = (int) (totalHeight * scale);
        }

        return new CollageLayout(maxCellWidth, maxCellHeight, totalWidth, totalHeight);
    }

    private float[][] createMonoCollageData(CollageParameters parameters, CollageLayout layout) {
        var collageData = new float[layout.totalHeight()][layout.totalWidth()];

        for (var y = 0; y < layout.totalHeight(); y++) {
            for (var x = 0; x < layout.totalWidth(); x++) {
                collageData[y][x] = parameters.backgroundColor();
            }
        }

        var imageIndex = 0;
        for (var row = 0; row < parameters.rows(); row++) {
            for (var col = 0; col < parameters.columns(); col++) {
                if (imageIndex >= parameters.images().size()) {
                    break;
                }

                var imageSelection = parameters.images().get(imageIndex);
                var targetRow = imageSelection.row().orElse(row);
                var targetCol = imageSelection.column().orElse(col);

                if (targetRow >= 0 && targetRow < parameters.rows() &&
                    targetCol >= 0 && targetCol < parameters.columns()) {

                    placeImageInCell(collageData, imageSelection.image(),
                                   targetRow, targetCol, layout, parameters);
                }

                imageIndex++;
            }
        }

        return collageData;
    }

    private float[][][] createColorCollageData(CollageParameters parameters, CollageLayout layout) {
        var collageR = new float[layout.totalHeight()][layout.totalWidth()];
        var collageG = new float[layout.totalHeight()][layout.totalWidth()];
        var collageB = new float[layout.totalHeight()][layout.totalWidth()];

        for (var y = 0; y < layout.totalHeight(); y++) {
            for (var x = 0; x < layout.totalWidth(); x++) {
                collageR[y][x] = parameters.backgroundColor();
                collageG[y][x] = parameters.backgroundColor();
                collageB[y][x] = parameters.backgroundColor();
            }
        }

        var imageIndex = 0;
        for (var row = 0; row < parameters.rows(); row++) {
            for (var col = 0; col < parameters.columns(); col++) {
                if (imageIndex >= parameters.images().size()) {
                    break;
                }

                var imageSelection = parameters.images().get(imageIndex);
                var targetRow = imageSelection.row().orElse(row);
                var targetCol = imageSelection.column().orElse(col);

                if (targetRow >= 0 && targetRow < parameters.rows() &&
                    targetCol >= 0 && targetCol < parameters.columns()) {

                    placeColorImageInCell(collageR, collageG, collageB, imageSelection.image(),
                                        targetRow, targetCol, layout, parameters);
                }

                imageIndex++;
            }
        }

        return new float[][][] { collageR, collageG, collageB };
    }

    private void placeColorImageInCell(float[][] collageR, float[][] collageG, float[][] collageB,
                                     ImageWrapper image, int row, int col,
                                     CollageLayout layout, CollageParameters parameters) {

        float[][] rData, gData, bData;
        if (image instanceof RGBImage rgb) {
            rData = rgb.r();
            gData = rgb.g();
            bData = rgb.b();
        } else if (image instanceof ImageWrapper32 mono) {
            var monoData = mono.data();
            rData = monoData;
            gData = monoData;
            bData = monoData;
        } else {
            var unwrapped = image.unwrapToMemory();
            if (unwrapped instanceof RGBImage rgb) {
                rData = rgb.r();
                gData = rgb.g();
                bData = rgb.b();
            } else if (unwrapped instanceof ImageWrapper32 mono) {
                var monoData = mono.data();
                rData = monoData;
                gData = monoData;
                bData = monoData;
            } else {
                LOGGER.warn("Unsupported image type for collage: {}", unwrapped.getClass());
                return;
            }
        }

        var imgWidth = image.width();
        var imgHeight = image.height();

        var cellX = col * (layout.cellWidth() + parameters.padding());
        var cellY = row * (layout.cellHeight() + parameters.padding());

        var scaleX = 1.0;
        var scaleY = 1.0;

        if (imgWidth > layout.cellWidth() || imgHeight > layout.cellHeight()) {
            scaleX = (double) layout.cellWidth() / imgWidth;
            scaleY = (double) layout.cellHeight() / imgHeight;

            if (parameters.maintainAspectRatio()) {
                var scale = Math.min(scaleX, scaleY);
                scaleX = scale;
                scaleY = scale;
            }
        }

        var scaledWidth = (int) (imgWidth * scaleX);
        var scaledHeight = (int) (imgHeight * scaleY);

        var offsetX = (layout.cellWidth() - scaledWidth) / 2;
        var offsetY = (layout.cellHeight() - scaledHeight) / 2;

        for (var y = 0; y < scaledHeight; y++) {
            for (var x = 0; x < scaledWidth; x++) {
                var srcX = (int) (x / scaleX);
                var srcY = (int) (y / scaleY);

                if (srcX < imgWidth && srcY < imgHeight) {
                    var targetX = cellX + offsetX + x;
                    var targetY = cellY + offsetY + y;

                    if (targetX < layout.totalWidth() && targetY < layout.totalHeight()) {
                        collageR[targetY][targetX] = rData[srcY][srcX];
                        collageG[targetY][targetX] = gData[srcY][srcX];
                        collageB[targetY][targetX] = bData[srcY][srcX];
                    }
                }
            }
        }
    }

    private void placeImageInCell(float[][] collageData, ImageWrapper image,
                                  int row, int col, CollageLayout layout, CollageParameters parameters) {

        float[][] imageData;
        if (image instanceof ImageWrapper32 mono) {
            imageData = mono.data();
        } else if (image instanceof RGBImage rgb) {
            imageData = rgb.toMono().data();
        } else {
            var unwrapped = image.unwrapToMemory();
            if (unwrapped instanceof ImageWrapper32 mono) {
                imageData = mono.data();
            } else if (unwrapped instanceof RGBImage rgb) {
                imageData = rgb.toMono().data();
            } else {
                LOGGER.warn("Unsupported image type for collage: {}", unwrapped.getClass());
                return;
            }
        }
        var imgWidth = image.width();
        var imgHeight = image.height();

        var cellX = col * (layout.cellWidth() + parameters.padding());
        var cellY = row * (layout.cellHeight() + parameters.padding());

        var scaleX = 1.0;
        var scaleY = 1.0;

        if (imgWidth > layout.cellWidth() || imgHeight > layout.cellHeight()) {
            scaleX = (double) layout.cellWidth() / imgWidth;
            scaleY = (double) layout.cellHeight() / imgHeight;

            if (parameters.maintainAspectRatio()) {
                var scale = Math.min(scaleX, scaleY);
                scaleX = scale;
                scaleY = scale;
            }
        }

        var scaledWidth = (int) (imgWidth * scaleX);
        var scaledHeight = (int) (imgHeight * scaleY);

        var offsetX = (layout.cellWidth() - scaledWidth) / 2;
        var offsetY = (layout.cellHeight() - scaledHeight) / 2;

        for (var y = 0; y < scaledHeight; y++) {
            for (var x = 0; x < scaledWidth; x++) {
                var srcX = (int) (x / scaleX);
                var srcY = (int) (y / scaleY);

                if (srcX < imgWidth && srcY < imgHeight) {
                    var targetX = cellX + offsetX + x;
                    var targetY = cellY + offsetY + y;

                    if (targetX < layout.totalWidth() && targetY < layout.totalHeight()) {
                        collageData[targetY][targetX] = imageData[srcY][srcX];
                    }
                }
            }
        }
    }

    private record CollageLayout(
        int cellWidth,
        int cellHeight,
        int totalWidth,
        int totalHeight
    ) {}
}