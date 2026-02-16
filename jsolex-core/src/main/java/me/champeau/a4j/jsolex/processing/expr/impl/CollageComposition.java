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
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.CollageParameters;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class CollageComposition extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollageComposition.class);

    public CollageComposition(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object collage(Map<String, Object> arguments) {
        BuiltinFunction.COLLAGE.validateArgs(arguments);
        var pattern = stringArg(arguments, "pattern", "");
        var imagesArg = arguments.get("images");

        if (!(imagesArg instanceof List<?> imageList)) {
            throw new IllegalArgumentException("Images must be a list");
        }

        var parsedPattern = parsePattern(pattern);
        var rows = parsedPattern.rows();
        var cols = parsedPattern.cols();
        var cellPositions = parsedPattern.cellPositions();

        if (imageList.size() > cellPositions.size()) {
            LOGGER.warn("More images ({}) than pattern positions ({}), extra images will be ignored",
                    imageList.size(), cellPositions.size());
        }
        if (imageList.size() < cellPositions.size()) {
            LOGGER.warn("Fewer images ({}) than pattern positions ({}), some positions will be empty",
                    imageList.size(), cellPositions.size());
        }

        var padding = intArg(arguments, "padding", 10);
        var bgColor = parseBackgroundColor(arguments);

        var imageSelections = new ArrayList<CollageParameters.ImageSelection>();
        var imageIndex = 0;
        for (var cellPos : cellPositions) {
            if (imageIndex >= imageList.size()) {
                break;
            }
            var imgObj = imageList.get(imageIndex);
            var img = unwrapToImage(imgObj);
            imageSelections.add(new CollageParameters.ImageSelection(
                    img,
                    "",
                    Optional.of(cellPos.row()),
                    Optional.of(cellPos.col())
            ));
            imageIndex++;
        }

        var parameters = new CollageParameters(
                imageSelections,
                rows,
                cols,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                true,
                padding,
                false,
                bgColor.r(),
                bgColor.r(),
                bgColor.g(),
                bgColor.b()
        );

        return createCollage(parameters);
    }

    private BackgroundColor parseBackgroundColor(Map<String, Object> arguments) {
        if (!arguments.containsKey("bg")) {
            return new BackgroundColor(0, 0, 0);
        }
        var bgArg = arguments.get("bg");
        if (bgArg instanceof Number bgNum) {
            var value = bgNum.floatValue();
            return new BackgroundColor(value, value, value);
        } else if (bgArg instanceof List<?> bgList && bgList.size() >= 3) {
            return new BackgroundColor(
                    floatArg(bgList.get(0), 0),
                    floatArg(bgList.get(1), 0),
                    floatArg(bgList.get(2), 0)
            );
        }
        return new BackgroundColor(0, 0, 0);
    }

    private static ImageWrapper unwrapToImage(Object imgObj) {
        if (imgObj instanceof FileBackedImage fbi) {
            return fbi.unwrapToMemory();
        } else if (imgObj instanceof ImageWrapper iw) {
            return iw;
        }
        throw new IllegalArgumentException("Expected an image but got " + imgObj.getClass().getSimpleName());
    }

    private ParsedPattern parsePattern(String pattern) {
        var normalizedPattern = pattern.replace("\n", "/");
        var rows = normalizedPattern.split("/", -1);
        List<String> nonEmptyRows = new ArrayList<>();
        for (var row : rows) {
            var stripped = row.replaceAll("\\s+", "");
            if (!stripped.isEmpty()) {
                nonEmptyRows.add(stripped);
            }
        }

        if (nonEmptyRows.isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be empty");
        }

        int maxCols = 0;
        for (var row : nonEmptyRows) {
            maxCols = Math.max(maxCols, row.length());
        }

        List<CellPosition> cellPositions = new ArrayList<>();
        int rowIndex = 0;
        for (var row : nonEmptyRows) {
            for (int colIndex = 0; colIndex < row.length(); colIndex++) {
                char c = row.charAt(colIndex);
                if (c == 'X' || c == 'x') {
                    cellPositions.add(new CellPosition(rowIndex, colIndex));
                }
            }
            rowIndex++;
        }

        return new ParsedPattern(nonEmptyRows.size(), maxCols, cellPositions);
    }

    public ImageWrapper createCollage(CollageParameters parameters) {
        var progressOperation = newOperation().createChild(message("creating.collage"));
        broadcaster.broadcast(progressOperation);

        try {
            var layout = calculateLayout(parameters);

            var hasColorImages = false;
            var hasColorBackground = parameters.backgroundColorR() != parameters.backgroundColorG() ||
                                   parameters.backgroundColorG() != parameters.backgroundColorB();

            var images = parameters.images().stream()
                .map(imageSelection -> {
                    var img = imageSelection.image();
                    if (img instanceof FileBackedImage fbi) {
                        return fbi.unwrapToMemory();
                    }
                    return img;
                })
                .toList();

            for (var img : images) {
                if (img instanceof RGBImage) {
                    hasColorImages = true;
                    break;
                }
            }

            var metadata = MetadataMerger.merge(images);

            if (hasColorImages || hasColorBackground) {
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

        // Calculate total dimensions including padding between cells
        var totalWidth = cols * maxCellWidth + (cols - 1) * padding;
        var totalHeight = rows * maxCellHeight + (rows - 1) * padding;


        int scaledPadding = padding;
        if (parameters.downscaleIfNeeded() &&
            (totalWidth > parameters.maxOutputWidth() || totalHeight > parameters.maxOutputHeight())) {

            var scaleX = (double) parameters.maxOutputWidth() / totalWidth;
            var scaleY = (double) parameters.maxOutputHeight() / totalHeight;
            var scale = Math.min(scaleX, scaleY);

            maxCellWidth = (int) (maxCellWidth * scale);
            maxCellHeight = (int) (maxCellHeight * scale);
            scaledPadding = (int) (padding * scale);
            totalWidth = (int) (totalWidth * scale);
            totalHeight = (int) (totalHeight * scale);

        }

        return new CollageLayout(maxCellWidth, maxCellHeight, totalWidth, totalHeight, scaledPadding);
    }

    private float[][] createMonoCollageData(CollageParameters parameters, CollageLayout layout) {
        var collageData = new float[layout.totalHeight()][layout.totalWidth()];
        var bgColor = parameters.backgroundColor();

        for (var y = 0; y < layout.totalHeight(); y++) {
            for (var x = 0; x < layout.totalWidth(); x++) {
                collageData[y][x] = bgColor;
            }
        }

        var imageIndex = 0;
        for (var row = 0; row < parameters.rows(); row++) {
            for (var col = 0; col < parameters.columns(); col++) {
                if (imageIndex >= parameters.images().size()) {
                    break;
                }

                var imageSelection = parameters.images().get(imageIndex);
                int targetRow = imageSelection.row().orElse(row);
                int targetCol = imageSelection.column().orElse(col);

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

        var bgR = parameters.backgroundColorR();
        var bgG = parameters.backgroundColorG();
        var bgB = parameters.backgroundColorB();


        for (var y = 0; y < layout.totalHeight(); y++) {
            for (var x = 0; x < layout.totalWidth(); x++) {
                collageR[y][x] = bgR;
                collageG[y][x] = bgG;
                collageB[y][x] = bgB;
            }
        }


        var imageIndex = 0;
        for (var row = 0; row < parameters.rows(); row++) {
            for (var col = 0; col < parameters.columns(); col++) {
                if (imageIndex >= parameters.images().size()) {
                    break;
                }

                var imageSelection = parameters.images().get(imageIndex);
                int targetRow = imageSelection.row().orElse(row);
                int targetCol = imageSelection.column().orElse(col);

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

    private void placeColorImageInCell(float[][] collageR,
                                       float[][] collageG,
                                       float[][] collageB,
                                       ImageWrapper image,
                                       int row,
                                       int col,
                                       CollageLayout layout,
                                       CollageParameters parameters) {
        var rgbData = extractRgbData(image);
        if (rgbData == null) {
            return;
        }
        var placement = computePlacement(image, row, col, layout, parameters);
        copyPixels(placement, rgbData.r(), collageR);
        copyPixels(placement, rgbData.g(), collageG);
        copyPixels(placement, rgbData.b(), collageB);
    }

    private void placeImageInCell(float[][] collageData, ImageWrapper image,
                                  int row, int col, CollageLayout layout, CollageParameters parameters) {
        var imageData = extractMonoData(image);
        if (imageData == null) {
            return;
        }
        var placement = computePlacement(image, row, col, layout, parameters);
        copyPixels(placement, imageData, collageData);
    }

    private RgbData extractRgbData(ImageWrapper image) {
        if (image instanceof RGBImage rgb) {
            return new RgbData(rgb.r(), rgb.g(), rgb.b());
        } else if (image instanceof ImageWrapper32 mono) {
            var monoData = mono.data();
            return new RgbData(monoData, monoData, monoData);
        }
        var unwrapped = image.unwrapToMemory();
        if (unwrapped instanceof RGBImage rgb) {
            return new RgbData(rgb.r(), rgb.g(), rgb.b());
        } else if (unwrapped instanceof ImageWrapper32 mono) {
            var monoData = mono.data();
            return new RgbData(monoData, monoData, monoData);
        }
        logUnsupportedImageType(unwrapped);
        return null;
    }

    private void logUnsupportedImageType(ImageWrapper image) {
        LOGGER.warn("Unsupported image type for collage: {}", image.getClass());
    }

    private float[][] extractMonoData(ImageWrapper image) {
        if (image instanceof ImageWrapper32 mono) {
            return mono.data();
        }
        var unwrapped = image.unwrapToMemory();
        if (unwrapped instanceof ImageWrapper32 mono) {
            return mono.data();
        }
        logUnsupportedImageType(unwrapped);
        return null;
    }

    private PlacementInfo computePlacement(ImageWrapper image, int row, int col,
                                           CollageLayout layout, CollageParameters parameters) {
        var imgWidth = image.width();
        var imgHeight = image.height();
        var cellX = col * (layout.cellWidth() + layout.padding());
        var cellY = row * (layout.cellHeight() + layout.padding());

        var scaleX = 1.0;
        var scaleY = 1.0;
        var marginSize = Math.max(2, layout.padding() / 4);
        var effectiveCellWidth = Math.max(1, layout.cellWidth() - marginSize);
        var effectiveCellHeight = Math.max(1, layout.cellHeight() - marginSize);

        if (imgWidth > effectiveCellWidth || imgHeight > effectiveCellHeight) {
            scaleX = (double) effectiveCellWidth / imgWidth;
            scaleY = (double) effectiveCellHeight / imgHeight;
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

        return new PlacementInfo(imgWidth, imgHeight, cellX + offsetX, cellY + offsetY,
                scaledWidth, scaledHeight, scaleX, scaleY, layout.totalWidth(), layout.totalHeight());
    }

    private static void copyPixels(PlacementInfo p, float[][] src, float[][] dest) {
        for (var y = 0; y < p.scaledHeight(); y++) {
            for (var x = 0; x < p.scaledWidth(); x++) {
                var srcX = (int) (x / p.scaleX());
                var srcY = (int) (y / p.scaleY());
                if (srcX < p.imgWidth() && srcY < p.imgHeight()) {
                    var targetX = p.cellX() + x;
                    var targetY = p.cellY() + y;
                    if (targetX < p.totalWidth() && targetY < p.totalHeight()) {
                        dest[targetY][targetX] = src[srcY][srcX];
                    }
                }
            }
        }
    }

    private record CellPosition(int row, int col) {}

    private record ParsedPattern(int rows, int cols, List<CellPosition> cellPositions) {}

    private record BackgroundColor(float r, float g, float b) {}

    private record RgbData(float[][] r, float[][] g, float[][] b) {}

    private record PlacementInfo(
            int imgWidth,
            int imgHeight,
            int cellX,
            int cellY,
            int scaledWidth,
            int scaledHeight,
            double scaleX,
            double scaleY,
            int totalWidth,
            int totalHeight
    ) {}

    private record CollageLayout(
            int cellWidth,
            int cellHeight,
            int totalWidth,
            int totalHeight,
            int padding
    ) {}
}