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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImageCombination extends AbstractFunctionImpl {

    public ImageCombination(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object sideBySide(Map<String, Object> arguments) {
        BuiltinFunction.SIDE_BY_SIDE.validateArgs(arguments);
        var leftArg = arguments.get("left");
        var rightArg = arguments.get("right");

        if (leftArg instanceof List<?> leftList && rightArg instanceof List<?> rightList) {
            if (leftList.size() != rightList.size()) {
                throw new IllegalArgumentException("Lists must have the same size");
            }
            var result = new ArrayList<>();
            for (int i = 0; i < leftList.size(); i++) {
                var left = asImage(leftList.get(i));
                var right = asImage(rightList.get(i));
                result.add(combineSideBySide(left, right));
            }
            return result;
        }

        var left = asImage(leftArg);
        var right = asImage(rightArg);
        return combineSideBySide(left, right);
    }

    public Object topBottom(Map<String, Object> arguments) {
        BuiltinFunction.TOP_BOTTOM.validateArgs(arguments);
        var topArg = arguments.get("top");
        var bottomArg = arguments.get("bottom");

        if (topArg instanceof List<?> topList && bottomArg instanceof List<?> bottomList) {
            if (topList.size() != bottomList.size()) {
                throw new IllegalArgumentException("Lists must have the same size");
            }
            var result = new ArrayList<>();
            for (int i = 0; i < topList.size(); i++) {
                var top = asImage(topList.get(i));
                var bottom = asImage(bottomList.get(i));
                result.add(combineTopBottom(top, bottom));
            }
            return result;
        }

        var top = asImage(topArg);
        var bottom = asImage(bottomArg);
        return combineTopBottom(top, bottom);
    }

    private static ImageWrapper asImage(Object img) {
        if (img instanceof ImageWrapper wrapper) {
            return wrapper;
        }
        throw new IllegalArgumentException("Expected an image but got " + img.getClass().getSimpleName());
    }

    private static ImageWrapper combineSideBySide(ImageWrapper left, ImageWrapper right) {
        if (left instanceof ImageWrapper32 leftMono && right instanceof ImageWrapper32 rightMono) {
            return combineSideBySideMono(leftMono, rightMono);
        }
        if (left instanceof RGBImage leftRgb && right instanceof RGBImage rightRgb) {
            return combineSideBySideRgb(leftRgb, rightRgb);
        }

        throw new IllegalArgumentException("Both images must be of the same type (mono or RGB)");
    }

    private static ImageWrapper32 combineSideBySideMono(ImageWrapper32 left, ImageWrapper32 right) {
        int height = Math.max(left.height(), right.height());
        int leftWidth = left.width();
        int rightWidth = right.width();
        int totalWidth = leftWidth + rightWidth;

        var result = new float[height][totalWidth];
        var leftPixels = left.pixels();
        var rightPixels = right.pixels();

        for (int y = 0; y < height; y++) {
            if (y < left.height()) {
                leftPixels.getRow(y, 0, result[y], 0, leftWidth);
            }
            if (y < right.height()) {
                rightPixels.getRow(y, 0, result[y], leftWidth, rightWidth);
            }
        }

        var metadata = MetadataMerger.merge(List.of(left, right));
        return new ImageWrapper32(totalWidth, height, result, metadata);
    }

    private static RGBImage combineSideBySideRgb(RGBImage left, RGBImage right) {
        int height = Math.max(left.height(), right.height());
        int leftWidth = left.width();
        int rightWidth = right.width();
        int totalWidth = leftWidth + rightWidth;

        var resultR = new float[height][totalWidth];
        var resultG = new float[height][totalWidth];
        var resultB = new float[height][totalWidth];

        for (int y = 0; y < height; y++) {
            if (y < left.height()) {
                left.red().getRow(y, 0, resultR[y], 0, leftWidth);
                left.green().getRow(y, 0, resultG[y], 0, leftWidth);
                left.blue().getRow(y, 0, resultB[y], 0, leftWidth);
            }
            if (y < right.height()) {
                right.red().getRow(y, 0, resultR[y], leftWidth, rightWidth);
                right.green().getRow(y, 0, resultG[y], leftWidth, rightWidth);
                right.blue().getRow(y, 0, resultB[y], leftWidth, rightWidth);
            }
        }

        var metadata = MetadataMerger.merge(List.of(left, right));
        return new RGBImage(totalWidth, height, resultR, resultG, resultB, metadata);
    }

    private static ImageWrapper combineTopBottom(ImageWrapper top, ImageWrapper bottom) {
        if (top instanceof ImageWrapper32 topMono && bottom instanceof ImageWrapper32 bottomMono) {
            return combineTopBottomMono(topMono, bottomMono);
        }
        if (top instanceof RGBImage topRgb && bottom instanceof RGBImage bottomRgb) {
            return combineTopBottomRgb(topRgb, bottomRgb);
        }

        throw new IllegalArgumentException("Both images must be of the same type (mono or RGB)");
    }

    private static ImageWrapper32 combineTopBottomMono(ImageWrapper32 top, ImageWrapper32 bottom) {
        int width = Math.max(top.width(), bottom.width());
        int topHeight = top.height();
        int bottomHeight = bottom.height();
        int totalHeight = topHeight + bottomHeight;

        var result = new float[totalHeight][width];
        var topPixels = top.pixels();
        var bottomPixels = bottom.pixels();

        for (int y = 0; y < topHeight; y++) {
            topPixels.getRow(y, 0, result[y], 0, top.width());
        }
        for (int y = 0; y < bottomHeight; y++) {
            bottomPixels.getRow(y, 0, result[topHeight + y], 0, bottom.width());
        }

        var metadata = MetadataMerger.merge(List.of(top, bottom));
        return new ImageWrapper32(width, totalHeight, result, metadata);
    }

    private static RGBImage combineTopBottomRgb(RGBImage top, RGBImage bottom) {
        int width = Math.max(top.width(), bottom.width());
        int topHeight = top.height();
        int bottomHeight = bottom.height();
        int totalHeight = topHeight + bottomHeight;

        var resultR = new float[totalHeight][width];
        var resultG = new float[totalHeight][width];
        var resultB = new float[totalHeight][width];

        for (int y = 0; y < topHeight; y++) {
            top.red().getRow(y, 0, resultR[y], 0, top.width());
            top.green().getRow(y, 0, resultG[y], 0, top.width());
            top.blue().getRow(y, 0, resultB[y], 0, top.width());
        }
        for (int y = 0; y < bottomHeight; y++) {
            bottom.red().getRow(y, 0, resultR[topHeight + y], 0, bottom.width());
            bottom.green().getRow(y, 0, resultG[topHeight + y], 0, bottom.width());
            bottom.blue().getRow(y, 0, resultB[topHeight + y], 0, bottom.width());
        }

        var metadata = MetadataMerger.merge(List.of(top, bottom));
        return new RGBImage(width, totalHeight, resultR, resultG, resultB, metadata);
    }
}
