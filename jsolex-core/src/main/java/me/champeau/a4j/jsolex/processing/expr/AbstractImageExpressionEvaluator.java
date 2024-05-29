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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.expr.ExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast;
import me.champeau.a4j.jsolex.processing.expr.impl.Animate;
import me.champeau.a4j.jsolex.processing.expr.impl.BackgroundRemoval;
import me.champeau.a4j.jsolex.processing.expr.impl.Clahe;
import me.champeau.a4j.jsolex.processing.expr.impl.Colorize;
import me.champeau.a4j.jsolex.processing.expr.impl.Convolution;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.DiskFill;
import me.champeau.a4j.jsolex.processing.expr.impl.EllipseFit;
import me.champeau.a4j.jsolex.processing.expr.impl.FixBanding;
import me.champeau.a4j.jsolex.processing.expr.impl.GeometryCorrection;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Inverse;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.expr.impl.MathFunctions;
import me.champeau.a4j.jsolex.processing.expr.impl.MosaicComposition;
import me.champeau.a4j.jsolex.processing.expr.impl.RGBCombination;
import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.expr.impl.Saturation;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.expr.impl.SimpleFunctionCall;
import me.champeau.a4j.jsolex.processing.expr.impl.Stacking;
import me.champeau.a4j.jsolex.processing.expr.impl.Stretching;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public abstract class AbstractImageExpressionEvaluator extends ExpressionEvaluator {

    private final Map<Class<?>, Object> context = new HashMap<>();

    // Function implementations
    private final AdjustContrast adjustContrast;
    private final Animate animate;
    private final BackgroundRemoval bgRemoval;
    private final Clahe clahe;
    private final Colorize colorize;
    private final Convolution convolution;
    private final Crop crop;
    private final DiskFill diskFill;
    private final EllipseFit ellipseFit;
    private final FixBanding fixBanding;
    private final GeometryCorrection geometryCorrection;
    private final ImageDraw imageDraw;
    private final Inverse inverse;
    private final Loader loader;
    private final MathFunctions math;
    private final MosaicComposition mosaicComposition;
    private final Rotate rotate;
    private final Saturation saturation;
    private final Scaling scaling;
    private final SimpleFunctionCall simpleFunctionCall;
    private final Stretching stretching;
    private final Stacking stacking;

    protected AbstractImageExpressionEvaluator(Broadcaster broadcaster) {
        this.adjustContrast = new AdjustContrast(context, broadcaster);
        this.animate = new Animate(context, broadcaster);
        this.bgRemoval = new BackgroundRemoval(context, broadcaster);
        this.clahe = new Clahe(context, broadcaster);
        this.colorize = new Colorize(context, broadcaster);
        this.convolution = new Convolution(context, broadcaster);
        this.crop = new Crop(context, broadcaster);
        this.diskFill = new DiskFill(context, broadcaster);
        this.ellipseFit = new EllipseFit(context, broadcaster);
        this.fixBanding = new FixBanding(context, broadcaster);
        this.geometryCorrection = new GeometryCorrection(context, broadcaster, ellipseFit);
        this.imageDraw = new ImageDraw(context, broadcaster);
        this.inverse = new Inverse(context, broadcaster);
        this.loader = new Loader(context, broadcaster);
        this.math = new MathFunctions(context, broadcaster);
        this.rotate = new Rotate(context, broadcaster);
        this.saturation = new Saturation(context, broadcaster);
        this.scaling = new Scaling(context, broadcaster, crop);
        this.simpleFunctionCall = new SimpleFunctionCall(context, broadcaster);
        this.stretching = new Stretching(context, broadcaster);
        this.stacking = new Stacking(context, scaling, crop, broadcaster);
        this.mosaicComposition = new MosaicComposition(context, broadcaster, stacking, ellipseFit, scaling);
    }

    public <T> void putInContext(Class<T> key, T value) {
        context.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    @Override
    protected Object plus(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            return Stream.concat(leftList.stream(), rightList.stream()).toList();
        }
        if (left instanceof CharSequence leftString && right instanceof CharSequence rightString) {
            return leftString.toString() + rightString;
        }
        if (left instanceof CharSequence leftString) {
            return leftString.toString() + format(right);
        }
        if (right instanceof CharSequence rightString) {
            return String.valueOf(left) + format(rightString);
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, Double::sum);
    }

    private static String format(Object o) {
        if (o instanceof Double d) {
            return String.format(Locale.US, "%.2f", d);
        }
        if (o instanceof Float f) {
            return String.format(Locale.US, "%.2f", f);
        }
        return o.toString();
    }

    @Override
    protected Object minus(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            var copy = new ArrayList<>(leftList);
            copy.removeAll(rightList);
            return Collections.unmodifiableList(copy);
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a - b);
    }

    @Override
    protected Object mul(Object left, Object right) {
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a * b);
    }

    @Override
    protected Object div(Object left, Object right) {
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a / b);
    }

    @Override
    protected Object functionCall(BuiltinFunction function, List<Object> arguments) {
        return switch (function) {
            case ADJUST_CONTRAST -> adjustContrast.adjustContrast(arguments);
            case ADJUST_GAMMA -> adjustContrast.adjustGamma(arguments);
            case ANIM -> animate.createAnimation(arguments);
            case ASINH_STRETCH -> stretching.asinhStretch(arguments);
            case AUTO_CONTRAST -> adjustContrast.autoContrast(arguments);
            case AUTOCROP -> crop.autocrop(arguments);
            case AUTOCROP2 -> crop.autocrop2(arguments);
            case AVG -> simpleFunctionCall.applyFunction("avg", arguments, DoubleStream::average);
            case BLUR -> convolution.blur(arguments);
            case CLAHE -> clahe.clahe(arguments);
            case CHOOSE_FILE -> loader.chooseFile(arguments);
            case CHOOSE_FILES -> loader.chooseFiles(arguments);
            case COLORIZE -> colorize.colorize(arguments);
            case CROP -> crop.crop(arguments);
            case CROP_RECT -> crop.cropToRect(arguments);
            case DISK_FILL -> diskFill.fill(arguments);
            case DRAW_ARROW -> imageDraw.drawArrow(arguments);
            case DRAW_CIRCLE -> imageDraw.drawCircle(arguments);
            case DRAW_RECT -> imageDraw.drawRectangle(arguments);
            case DRAW_GLOBE -> imageDraw.drawGlobe(arguments);
            case DRAW_OBS_DETAILS -> imageDraw.drawObservationDetails(arguments);
            case DRAW_SOLAR_PARAMS -> imageDraw.drawSolarParameters(arguments);
            case DRAW_TEXT -> imageDraw.drawText(arguments);
            case ELLIPSE_FIT -> ellipseFit.fit(arguments);
            case EXP -> math.exp(arguments);
            case FIX_BANDING -> fixBanding.fixBanding(arguments);
            case FIX_GEOMETRY -> geometryCorrection.fixGeometry(arguments);
            case HFLIP -> rotate.hflip(arguments);
            case IMG -> image(arguments);
            case INVERT -> inverse.invert(arguments);
            case LINEAR_STRETCH -> stretching.linearStretch(arguments);
            case LIST -> arguments;
            case LOAD -> loader.load(arguments);
            case LOAD_MANY -> loader.loadMany(arguments);
            case LOG -> math.log(arguments);
            case MAX -> simpleFunctionCall.applyFunction("max", arguments, DoubleStream::max);
            case MEDIAN -> simpleFunctionCall.applyFunction("median", arguments, AbstractImageExpressionEvaluator::median);
            case MIN -> simpleFunctionCall.applyFunction("min", arguments, DoubleStream::min);
            case MOSAIC -> mosaicComposition.mosaic(arguments);
            case POW -> math.pow(arguments);
            case RADIUS_RESCALE -> scaling.radiusRescale(arguments);
            case RANGE -> createRange(arguments);
            case REMOVE_BG -> bgRemoval.removeBackground(arguments);
            case RESCALE_ABS -> scaling.absoluteRescale(arguments);
            case RESCALE_REL -> scaling.relativeRescale(arguments);
            case RL_DECON -> convolution.richardsonLucy(arguments);
            case ROTATE_LEFT -> rotate.rotateLeft(arguments);
            case ROTATE_RIGHT -> rotate.rotateRight(arguments);
            case ROTATE_DEG -> rotate.rotateDegrees(arguments);
            case ROTATE_RAD -> rotate.rotateRadians(arguments);
            case RGB -> RGBCombination.combine(arguments);
            case SATURATE -> saturation.saturate(arguments);
            case SHARPEN -> convolution.sharpen(arguments);
            case STACK -> stacking.stack(arguments);
            case VFLIP -> rotate.vflip(arguments);
            case WORKDIR -> setWorkDir(arguments);
        };
    }

    private static OptionalDouble median(DoubleStream doubleStream) {
        var array = doubleStream.toArray();
        if (array.length == 0) {
            return OptionalDouble.empty();
        }
        Arrays.sort(array);
        double median;
        int length = array.length;
        if (length % 2 == 0) {
            median = (array[length / 2 - 1] + array[length / 2]) / 2.0;
        } else {
            median = array[length / 2];
        }
        return OptionalDouble.of(median);
    }

    private Object setWorkDir(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalStateException("workdir accepts a single argument: path to directory");
        }
        var path = arguments.get(0).toString();
        loader.setWorkingDirectory(Paths.get(path));
        return path;
    }

    protected abstract ImageWrapper findImage(double shift);

    private ImageWrapper image(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("img() call must have a single argument (image shift)");
        }
        var arg = arguments.get(0);
        if (arg instanceof Number shift) {
            return findImage(shift.doubleValue());
        }
        throw new IllegalArgumentException("img() argument must be a number representing an image shift");

    }

    private static Object apply(ImageWrapper32 leftImage,
                                ImageWrapper32 rightImage,
                                Number leftScalar,
                                Number rightScalar,
                                DoubleBinaryOperator operator) {
        if (leftImage != null && rightImage != null) {
            var leftData = leftImage.data();
            var rightData = rightImage.data();
            var length = leftData.length;
            var width = leftImage.width();
            var height = leftImage.height();
            if (width != rightImage.width() || height != rightImage.height()) {
                throw new IllegalArgumentException("Both images must have the same dimensions");
            }
            float[] result = new float[length];
            float min = 0;
            for (int i = 0; i < length; i++) {
                var v = (float) operator.applyAsDouble(leftData[i], rightData[i]);
                if (v < min) {
                    min = v;
                }
                result[i] = v;
            }
            normalize(length, result, min);
            Map<Class<?>, Object> metadata = new LinkedHashMap<>();
            metadata.putAll(leftImage.metadata());
            metadata.putAll(rightImage.metadata());
            return new ImageWrapper32(width, height, result, metadata);
        }
        if (leftImage != null && rightScalar != null) {
            var scalar = rightScalar.doubleValue();
            var leftData = leftImage.data();
            var length = leftData.length;
            var width = leftImage.width();
            var height = leftImage.height();
            float[] result = new float[length];
            float min = 0;
            for (int i = 0; i < length; i++) {
                var v = (float) operator.applyAsDouble(leftData[i], scalar);
                if (v < min) {
                    min = v;
                }
                result[i] = v;
            }
            normalize(length, result, min);
            return new ImageWrapper32(width, height, result, leftImage.metadata());
        }
        if (rightImage != null && leftScalar != null) {
            var scalar = leftScalar.doubleValue();
            var rightData = rightImage.data();
            var length = rightData.length;
            var width = rightImage.width();
            var height = rightImage.height();
            float[] result = new float[length];
            float min = 0;
            for (int i = 0; i < length; i++) {
                var v = (float) operator.applyAsDouble(rightData[i], scalar);
                if (v < min) {
                    min = v;
                }
                result[i] = v;
            }
            normalize(length, result, min);
            return new ImageWrapper32(width, height, result, rightImage.metadata());
        }
        if (leftScalar != null && rightScalar != null) {
            return operator.applyAsDouble(leftScalar.doubleValue(), rightScalar.doubleValue());
        }
        throw new IllegalArgumentException("Unexpected operand types");
    }

    private static void normalize(int length, float[] result, float min) {
        if (min < 0) {
            // shift all values so that they are positive
            var abs = Math.abs(min);
            for (int i = 0; i < length; i++) {
                result[i] += abs;
            }
        }
    }

    private static ImageWrapper32 asImage(Object source) {
        if (source instanceof ImageWrapper32 image) {
            return image;
        }
        if (source instanceof FileBackedImage fileBackedImage) {
            return asImage(fileBackedImage.unwrapToMemory());
        }
        return null;
    }

    private static Number asScalar(Object source) {
        if (source instanceof Number number) {
            return number;
        }
        return null;
    }

    private List<ImageWrapper> createRange(List<Object> arguments) {
        if (arguments.size() < 2) {
            return List.of();
        }
        Number from = asScalar(arguments.get(0));
        Number to = asScalar(arguments.get(1));
        Number step = 1;
        if (arguments.size() == 3) {
            step = asScalar(arguments.get(2));
            if (step == null) {
                step = 1;
            }
        }
        List<ImageWrapper> images = new ArrayList<>();
        if (from != null && to != null) {
            double fromDouble = from.doubleValue();
            double toDouble = to.doubleValue();
            double stepDouble = step.doubleValue();
            if (fromDouble > toDouble) {
                double tmp = fromDouble;
                fromDouble = toDouble;
                toDouble = tmp;
            }
            for (double i = fromDouble; i <= toDouble; i += stepDouble) {
                images.add(findImage(i));
            }
        }
        return Collections.unmodifiableList(images);
    }

}
