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
import me.champeau.a4j.jsolex.processing.sun.ColumnBackground;
import me.champeau.a4j.jsolex.processing.sun.ScatteredLight;
import me.champeau.a4j.jsolex.processing.sun.workflow.AnalysisUtils;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Implementation of background removal functions for the image math language.
 * Provides functions to remove or neutralize background in solar images.
 */
public class BackgroundRemoval extends AbstractFunctionImpl {
    /**
     * Constructs a background removal function implementation.
     *
     * @param context the execution context
     * @param broadcaster the broadcaster for progress notifications
     */
    public BackgroundRemoval(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    /**
     * Removes background from an image using a quadratic model based on distance from the solar disk.
     * The function removes background outside the ellipse using a tolerance-based correction.
     *
     * @param arguments function arguments containing:
     *                  - img: the input image or list of images
     *                  - ellipse: the solar disk ellipse (optional if metadata available)
     *                  - tolerance: the correction factor (default: 0.9)
     * @return the processed image or list of images with background removed
     */
    public Object removeBackground(Map<String ,Object> arguments) {
        BuiltinFunction.REMOVE_BG.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("remove_bg", "img", arguments, this::removeBackground);
        }
        Optional<Ellipse> ellipse = getEllipse(arguments, "ellipse");
        if (ellipse.isEmpty()) {
            throw new IllegalArgumentException("Cannot perform background removal because ellipse isn't found");
        }
        double tolerance = doubleArg(arguments, "tolerance", .9);
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 ref) {
            return monoToMonoImageTransformer("remove_bg", "img", arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var e = ellipse.get();
                    var background = AnalysisUtils.estimateBackground(ref, e);
                    var width = image.width();
                    var height = image.height();
                    var data = image.data();
                    me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.removeBackground(width, height, data, tolerance, background, e);
                } else {
                    throw new IllegalArgumentException("remove_bg only supports mono images");
                }
            });
        }
        throw new IllegalArgumentException("remove_bg only supports mono images");
    }

    /**
     * Neutralizes background by estimating background level and removing it using polynomial regression.
     * The function iteratively models and removes background outside the solar disk.
     *
     * @param arguments function arguments containing:
     *                  - img: the input image or list of images
     *                  - iterations: number of neutralization iterations (default: 1)
     * @return the processed image or list of images with neutralized background
     */
    public Object neutralizeBackground(Map<String ,Object> arguments) {
        BuiltinFunction.NEUTRALIZE_BG.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("neutralize_bg", "img", arguments, this::neutralizeBackground);
        }
        var iterations = intArg(arguments, "iterations", 1);
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty()) {
                throw new IllegalArgumentException("Cannot perform background neutralization because ellipse isn't found");
            }
            return monoToMonoImageTransformer("neutralize_bg", "img", arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var model = me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.neutralizeBackground(image, iterations).data();
                    for (int y = 0; y < image.height(); y++) {
                        System.arraycopy(model[y], 0, image.data()[y], 0, image.width());
                    }
                } else {
                    throw new IllegalArgumentException("neutralize_bg only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("neutralize_bg only supports mono images");
    }

    /**
     * Computes a polynomial background model for an image by fitting a surface to background pixels.
     * The model is computed using pixels outside the solar disk and can use various polynomial degrees.
     *
     * @param arguments function arguments containing:
     *                  - img: the input image or list of images
     *                  - order: polynomial degree (default: 2)
     *                  - sigma: sigma threshold for outlier filtering (default: 2.5)
     * @return the computed background model image or list of images
     */
    public Object backgroundModel(Map<String ,Object> arguments) {
        BuiltinFunction.BG_MODEL.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("backgroundModel", "img", arguments, this::backgroundModel);
        }
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty() && !arguments.containsKey("mask")) {
                throw new IllegalArgumentException("Cannot perform background neutralization because ellipse isn't found");
            }
            int order = intArg(arguments, "order", 2);
            double sigma = doubleArg(arguments, "sigma", 2.5);
            return monoToMonoImageTransformer("backgroundModel", "img", arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var mask = statsMask(arguments, image);
                    var buffer = new float[image.height()][image.width()];
                    Optional<ImageWrapper32> optionalModel = me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.backgroundModel(image, order, sigma, buffer, mask);
                    optionalModel.ifPresent(model -> {
                        var data = model.data();
                        for (int y = 0; y < image.height(); y++) {
                            System.arraycopy(data[y], 0, image.data()[y], 0, image.width());
                        }
                    });
                } else {
                    throw new IllegalArgumentException("backgroundModel only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("backgroundModel only supports mono images");
    }

    /**
     * Computes the column illumination model of an image: a background level which changes from one
     * column to the next but stays constant along a column, typically produced by the light which
     * the instrument scatters while the slit crosses the solar disk. The level of each column is
     * the median of its usable pixels, and the model is that profile lightly smoothed, so the steep
     * fall right past the edges of the disk is followed instead of being rounded off.
     *
     * @param arguments function arguments containing:
     *                  - img: the input image or list of images
     *                  - mask: the pixels the model is computed from (optional, defaults to the
     *                  pixels outside the solar disk)
     *                  - smoothing: horizontal smoothing of the model, in pixels (default: 8)
     *                  - sigma: sigma threshold for discarding outlier columns (default: 2.5)
     *                  - normalize: when 1, scale the model so the columns beside the disk sit
     *                  at 1, so it can divide an image without changing its scale (default: 0)
     * @return the computed model image or list of images
     */
    public Object columnBackgroundModel(Map<String, Object> arguments) {
        BuiltinFunction.COLUMN_BG_MODEL.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("column_bg_model", "img", arguments, this::columnBackgroundModel);
        }
        if (arg instanceof ImageWrapper target) {
            Optional<Ellipse> ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty() && !arguments.containsKey("mask")) {
                throw new IllegalArgumentException("Cannot compute the column illumination model because ellipse isn't found");
            }
            var smoothing = doubleArg(arguments, "smoothing", 8);
            var sigma = doubleArg(arguments, "sigma", 2.5);
            var normalize = intArg(arguments, "normalize", 0) != 0;
            return monoToMonoImageTransformer("column_bg_model", "img", arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    var mask = statsMask(arguments, image);
                    var width = image.width();
                    var height = image.height();
                    var usable = mask != null ? mask : ellipse.<BiPredicate<Integer, Integer>>map(e -> (x, y) -> !e.isWithin(x, y)).orElse(null);
                    var levels = ColumnBackground.estimate(width, height, image.data(), usable, smoothing, sigma);
                    if (normalize) {
                        ColumnBackground.normalizeLevels(levels, ellipse.orElse(null));
                    }
                    var model = ColumnBackground.toImage(width, height, levels);
                    for (int y = 0; y < height; y++) {
                        System.arraycopy(model[y], 0, image.data()[y], 0, width);
                    }
                } else {
                    throw new IllegalArgumentException("column_bg_model only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("column_bg_model only supports mono images");
    }

    /**
     * Removes the background left by the light scattered inside the instrument while the slit
     * crosses the solar disk. The shape of that background along the scan axis is the chord of the
     * solar disk, so it is taken from the ellipse instead of being estimated from the background,
     * and only its amplitude is fitted. The disk itself is left untouched.
     *
     * @param arguments function arguments containing:
     *                  - img: the input image or list of images
     *                  - strength: the fraction of the estimated background removed (default: 1)
     *                  - iterations: the number of passes, 0 leaving the image untouched (default: 1)
     * @return the corrected image or list of images
     */
    public Object descatter(Map<String, Object> arguments) {
        BuiltinFunction.DESCATTER.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("descatter", "img", arguments, this::descatter);
        }
        if (arg instanceof ImageWrapper target) {
            var iterations = intArg(arguments, "iterations", 1);
            if (iterations <= 0) {
                return target;
            }
            var ellipse = target.findMetadata(Ellipse.class);
            if (ellipse.isEmpty()) {
                throw new IllegalArgumentException("Cannot remove scattered light because ellipse isn't found");
            }
            var strength = doubleArg(arguments, "strength", 1);
            return monoToMonoImageTransformer("descatter", "img", arguments, src -> {
                if (src instanceof ImageWrapper32 image) {
                    ScatteredLight.remove(image.width(), image.height(), image.data(), ellipse.get(), strength, iterations);
                } else {
                    throw new IllegalArgumentException("descatter only supports mono images");
                }
            });
        }

        throw new IllegalArgumentException("descatter only supports mono images");
    }
}
