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
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
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
                var left = unwrapImage(leftList.get(i));
                var right = unwrapImage(rightList.get(i));
                result.add(combineSideBySide(left, right));
            }
            return result;
        }

        var left = unwrapImage(leftArg);
        var right = unwrapImage(rightArg);
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
                var top = unwrapImage(topList.get(i));
                var bottom = unwrapImage(bottomList.get(i));
                result.add(combineTopBottom(top, bottom));
            }
            return result;
        }

        var top = unwrapImage(topArg);
        var bottom = unwrapImage(bottomArg);
        return combineTopBottom(top, bottom);
    }

    private static ImageWrapper unwrapImage(Object img) {
        if (img instanceof FileBackedImage fileBackedImage) {
            return fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper wrapper) {
            return wrapper.unwrapToMemory();
        }
        throw new IllegalArgumentException("Expected an image but got " + img.getClass().getSimpleName());
    }

    private static ImageWrapper combineSideBySide(ImageWrapper left, ImageWrapper right) {
        if (left.height() != right.height()) {
            throw new IllegalArgumentException("Images must have the same height for side by side combination");
        }

        if (left instanceof ImageWrapper32 leftMono && right instanceof ImageWrapper32 rightMono) {
            return combineSideBySideMono(leftMono, rightMono);
        }
        if (left instanceof RGBImage leftRgb && right instanceof RGBImage rightRgb) {
            return combineSideBySideRgb(leftRgb, rightRgb);
        }

        throw new IllegalArgumentException("Both images must be of the same type (mono or RGB)");
    }

    private static ImageWrapper32 combineSideBySideMono(ImageWrapper32 left, ImageWrapper32 right) {
        int height = left.height();
        int leftWidth = left.width();
        int rightWidth = right.width();
        int totalWidth = leftWidth + rightWidth;

        var result = new float[height][totalWidth];
        var leftData = left.data();
        var rightData = right.data();

        for (int y = 0; y < height; y++) {
            System.arraycopy(leftData[y], 0, result[y], 0, leftWidth);
            System.arraycopy(rightData[y], 0, result[y], leftWidth, rightWidth);
        }

        var metadata = MetadataMerger.merge(List.of(left, right));
        return new ImageWrapper32(totalWidth, height, result, metadata);
    }

    private static RGBImage combineSideBySideRgb(RGBImage left, RGBImage right) {
        int height = left.height();
        int leftWidth = left.width();
        int rightWidth = right.width();
        int totalWidth = leftWidth + rightWidth;

        var resultR = new float[height][totalWidth];
        var resultG = new float[height][totalWidth];
        var resultB = new float[height][totalWidth];

        for (int y = 0; y < height; y++) {
            System.arraycopy(left.r()[y], 0, resultR[y], 0, leftWidth);
            System.arraycopy(right.r()[y], 0, resultR[y], leftWidth, rightWidth);
            System.arraycopy(left.g()[y], 0, resultG[y], 0, leftWidth);
            System.arraycopy(right.g()[y], 0, resultG[y], leftWidth, rightWidth);
            System.arraycopy(left.b()[y], 0, resultB[y], 0, leftWidth);
            System.arraycopy(right.b()[y], 0, resultB[y], leftWidth, rightWidth);
        }

        var metadata = MetadataMerger.merge(List.of(left, right));
        return new RGBImage(totalWidth, height, resultR, resultG, resultB, metadata);
    }

    private static ImageWrapper combineTopBottom(ImageWrapper top, ImageWrapper bottom) {
        if (top.width() != bottom.width()) {
            throw new IllegalArgumentException("Images must have the same width for top/bottom combination");
        }

        if (top instanceof ImageWrapper32 topMono && bottom instanceof ImageWrapper32 bottomMono) {
            return combineTopBottomMono(topMono, bottomMono);
        }
        if (top instanceof RGBImage topRgb && bottom instanceof RGBImage bottomRgb) {
            return combineTopBottomRgb(topRgb, bottomRgb);
        }

        throw new IllegalArgumentException("Both images must be of the same type (mono or RGB)");
    }

    private static ImageWrapper32 combineTopBottomMono(ImageWrapper32 top, ImageWrapper32 bottom) {
        int width = top.width();
        int topHeight = top.height();
        int bottomHeight = bottom.height();
        int totalHeight = topHeight + bottomHeight;

        var result = new float[totalHeight][width];
        var topData = top.data();
        var bottomData = bottom.data();

        for (int y = 0; y < topHeight; y++) {
            System.arraycopy(topData[y], 0, result[y], 0, width);
        }
        for (int y = 0; y < bottomHeight; y++) {
            System.arraycopy(bottomData[y], 0, result[topHeight + y], 0, width);
        }

        var metadata = MetadataMerger.merge(List.of(top, bottom));
        return new ImageWrapper32(width, totalHeight, result, metadata);
    }

    private static RGBImage combineTopBottomRgb(RGBImage top, RGBImage bottom) {
        int width = top.width();
        int topHeight = top.height();
        int bottomHeight = bottom.height();
        int totalHeight = topHeight + bottomHeight;

        var resultR = new float[totalHeight][width];
        var resultG = new float[totalHeight][width];
        var resultB = new float[totalHeight][width];

        for (int y = 0; y < topHeight; y++) {
            System.arraycopy(top.r()[y], 0, resultR[y], 0, width);
            System.arraycopy(top.g()[y], 0, resultG[y], 0, width);
            System.arraycopy(top.b()[y], 0, resultB[y], 0, width);
        }
        for (int y = 0; y < bottomHeight; y++) {
            System.arraycopy(bottom.r()[y], 0, resultR[topHeight + y], 0, width);
            System.arraycopy(bottom.g()[y], 0, resultG[topHeight + y], 0, width);
            System.arraycopy(bottom.b()[y], 0, resultB[topHeight + y], 0, width);
        }

        var metadata = MetadataMerger.merge(List.of(top, bottom));
        return new RGBImage(width, totalHeight, resultR, resultG, resultB, metadata);
    }
}
