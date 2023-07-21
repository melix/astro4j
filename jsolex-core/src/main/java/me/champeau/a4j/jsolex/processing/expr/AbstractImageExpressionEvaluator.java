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
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.expr.impl.RGBCombination;
import me.champeau.a4j.jsolex.processing.expr.impl.Rotate;
import me.champeau.a4j.jsolex.processing.expr.impl.Saturation;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport;
import me.champeau.a4j.jsolex.processing.expr.impl.Stretching;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.processing.expr.impl.ScriptSupport.applyFunction;

public abstract class AbstractImageExpressionEvaluator extends ExpressionEvaluator {

    private final ForkJoinContext forkJoinContext;
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
    private final Loader loader;
    private final Rotate rotate;
    private final Saturation saturation;
    private final Scaling scaling;
    private final Stretching stretching;

    protected AbstractImageExpressionEvaluator(ForkJoinContext forkJoinContext) {
        this.forkJoinContext = forkJoinContext;
        this.adjustContrast = new AdjustContrast(forkJoinContext, context);
        this.animate = new Animate(forkJoinContext, context);
        this.bgRemoval = new BackgroundRemoval(forkJoinContext, context);
        this.clahe = new Clahe(forkJoinContext, context);
        this.colorize = new Colorize(forkJoinContext, context);
        this.convolution = new Convolution(forkJoinContext, context);
        this.crop = new Crop(forkJoinContext, context);
        this.diskFill = new DiskFill(forkJoinContext, context);
        this.ellipseFit = new EllipseFit(forkJoinContext, context);
        this.fixBanding = new FixBanding(forkJoinContext, context);
        this.loader = new Loader(forkJoinContext, context);
        this.rotate = new Rotate(forkJoinContext, context);
        this.saturation = new Saturation(forkJoinContext, context);
        this.scaling = new Scaling(forkJoinContext, context);
        this.stretching = new Stretching(forkJoinContext, context);
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
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, Double::sum);
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
            case ANIM -> animate.createAnimation(arguments);
            case ASINH_STRETCH -> stretching.asinhStretch(arguments);
            case AUTOCROP -> crop.autocrop(arguments);
            case AUTOCROP2 -> crop.autocrop2(arguments);
            case AVG -> applyFunction("avg", arguments, DoubleStream::average);
            case BLUR -> convolution.blur(arguments);
            case CLAHE -> clahe.clahe(arguments);
            case COLORIZE -> colorize.colorize(arguments);
            case CROP -> crop.crop(arguments);
            case CROP_RECT -> crop.cropToRect(arguments);
            case DISK_FILL -> diskFill.fill(arguments);
            case ELLIPSE_FIT -> ellipseFit.fit(arguments);
            case FIX_BANDING -> fixBanding.fixBanding(arguments);
            case IMG -> image(arguments);
            case INVERT -> ScriptSupport.inverse(arguments);
            case LINEAR_STRETCH -> stretching.linearStretch(arguments);
            case LIST -> arguments;
            case LOAD -> loader.load(arguments);
            case LOAD_MANY -> loader.loadMany(arguments);
            case MAX -> applyFunction("max", arguments, DoubleStream::max);
            case MEDIAN -> applyFunction("median", arguments, AbstractImageExpressionEvaluator::median);
            case MIN -> applyFunction("min", arguments, DoubleStream::min);
            case RADIUS_RESCALE -> scaling.radiusRescale(arguments);
            case RANGE -> createRange(arguments);
            case REMOVE_BG -> bgRemoval.removeBackground(arguments);
            case RESCALE_ABS -> scaling.absoluteRescale(arguments);
            case RESCALE_REL -> scaling.relativeRescale(arguments);
            case ROTATE_LEFT -> rotate.rotateLeft(arguments);
            case ROTATE_RIGHT -> rotate.rotateRight(arguments);
            case ROTATE_DEG -> rotate.rotateDegrees(arguments);
            case ROTATE_RAD -> rotate.rotateRadians(arguments);
            case RGB -> RGBCombination.combine(arguments);
            case SATURATE -> saturation.saturate(arguments);
            case SHARPEN -> convolution.sharpen(arguments);
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

    protected abstract ImageWrapper findImage(int shift);

    private ImageWrapper image(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("img() call must have a single argument (image shift)");
        }
        var arg = arguments.get(0);
        if (arg instanceof Number shift) {
            return findImage(shift.intValue());
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
            return new ImageWrapper32(width, height, result);
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
            return new ImageWrapper32(width, height, result);
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
            return new ImageWrapper32(width, height, result);
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
            int fromInt = from.intValue();
            int toInt = to.intValue();
            int stepInt = step.intValue();
            if (fromInt > toInt) {
                int tmp = fromInt;
                fromInt = toInt;
                toInt = tmp;
            }
            for (int i = fromInt; i <= toInt; i += stepInt) {
                images.add(findImage(i));
            }
        }
        return Collections.unmodifiableList(images);
    }

}
