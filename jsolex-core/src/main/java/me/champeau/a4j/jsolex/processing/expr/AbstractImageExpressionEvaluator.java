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
import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.NegativeImageStrategy;
import me.champeau.a4j.jsolex.processing.sun.BandingReduction;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.sun.crop.Cropper;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public abstract class AbstractImageExpressionEvaluator extends ExpressionEvaluator {

    private final Map<Class<?>, Object> context = new HashMap<>();

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
            case IMG -> image(arguments);
            case AVG -> applyFunction(arguments, DoubleStream::average);
            case MIN -> applyFunction(arguments, DoubleStream::min);
            case MAX -> applyFunction(arguments, DoubleStream::max);
            case INVERT -> inverse(arguments);
            case RANGE -> createRange(arguments);
            case FIX_BANDING -> fixBanding(arguments);
            case ASINH_STRETCH -> asinhStretch(arguments);
            case COLORIZE -> colorize(arguments);
            case AUTOCROP -> autocrop(arguments);
        };
    }

    private Object autocrop(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("autocrop takes 1 arguments (image(s))");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?> list) {
            return list.stream().map(e -> colorize(List.of(e))).toList();
        }
        var ellipse = getFromContext(Ellipse.class);
        var blackpoint = getFromContext(ImageStats.class).map(ImageStats::blackpoint).orElse(0f);
        if (ellipse.isPresent()) {
            var circle = ellipse.get();
            if (arg instanceof ImageWrapper32 mono) {
                var image = mono.asImage();
                var cropped = Cropper.cropToSquare(image, circle, blackpoint);
                return ImageWrapper32.fromImage(cropped);
            } else if (arg instanceof ColorizedImageWrapper wrapper) {
                var mono = wrapper.mono();
                var cropped = Cropper.cropToSquare(mono.asImage(), circle, blackpoint);
                return new ColorizedImageWrapper(ImageWrapper32.fromImage(cropped), wrapper.converter());
            }
            throw new IllegalStateException("Unsupported image type");
        } else {
            throw new IllegalStateException("Sun disk not detected, cannot perform autocrop");
        }
    }

    private Object asinhStretch(List<Object> arguments) {
        if (arguments.size() < 3) {
            throw new IllegalArgumentException("asinh_stretch takes 3 arguments (image(s), blackpoint, stretch)");
        }
        float blackpoint = ((Number) arguments.get(1)).floatValue();
        float stretch = ((Number) arguments.get(2)).floatValue();
        return monoToMonoImageTransformer("asinh_stretch", 3, arguments, (width, height, data) -> new ArcsinhStretchingStrategy(blackpoint, stretch, stretch).stretch(data));
    }

    private Object colorize(List<Object> arguments) {
        if (arguments.size() != 7 && arguments.size() != 2) {
            throw new IllegalArgumentException("colorize takes 3 arguments (image, rIn, rOut, gIn, gOut, bIn, bOut) or 2 arguments (image, profile name)");
        }
        var arg = arguments.get(0);
        if (arg instanceof List<?> list) {
            return list.stream().map(e -> colorize(List.of(e))).toList();
        }
        if (arguments.size() == 7) {
            int rIn = ((Number) arguments.get(1)).intValue();
            int rOut = ((Number) arguments.get(2)).intValue();
            int gIn = ((Number) arguments.get(3)).intValue();
            int gOut = ((Number) arguments.get(4)).intValue();
            int bIn = ((Number) arguments.get(5)).intValue();
            int bOut = ((Number) arguments.get(6)).intValue();
            if (arg instanceof ImageWrapper32 mono) {
                return new ColorizedImageWrapper(mono, data -> {
                    var curve = new ColorCurve("adhoc", rIn, rOut, gIn, gOut, bIn, bOut);
                    return doColorize(data, curve);
                });
            }
        } else {
            String profile = arguments.get(1).toString();
            var rays = SpectralRayIO.loadDefaults();
            for (SpectralRay ray : rays) {
                if (ray.label().equalsIgnoreCase(profile) && (arg instanceof ImageWrapper32 mono)) {
                    var curve = ray.colorCurve();
                    if (curve != null) {
                        return new ColorizedImageWrapper(mono, data -> doColorize(data, curve));
                    }
                }
            }
            throw new IllegalArgumentException("Cannot find color profile '" + profile + "'");
        }
        throw new IllegalArgumentException("colorize first argument must be an image or a list of images");
    }

    private float[][] doColorize(float[] data, ColorCurve curve) {
        float[] copy = new float[data.length];
        System.arraycopy(data, 0, copy, 0, copy.length);
        return ImageUtils.convertToRGB(curve, copy);
    }

    private Object fixBanding(List<Object> arguments) {
        if (arguments.size() < 3) {
            throw new IllegalArgumentException("fix_banding takes 3 arguments (image, band size, passes)");
        }
        var ellipse = getFromContext(Ellipse.class);
        int bandSize = ((Number) arguments.get(1)).intValue();
        int passes = ((Number) arguments.get(2)).intValue();
        return monoToMonoImageTransformer("fix_banding", 3, arguments, (width, height, data) -> {
            for (int i = 0; i < passes; i++) {
                BandingReduction.reduceBanding(width, height, data, bandSize, ellipse.orElse(null));
            }
        });
    }

    private Object inverse(List<Object> arguments) {
        return monoToMonoImageTransformer("invert", 1, arguments, (w, h, data) -> NegativeImageStrategy.DEFAULT.stretch(data));
    }

    private Object monoToMonoImageTransformer(String name, int maxArgCount, List<Object> arguments, ImageConsumer consumer) {
        if (arguments.size() > maxArgCount) {
            throw new IllegalArgumentException("Invalid number of arguments on '" + name + "' call");
        }
        var arg = arguments.get(0);
        if (arg instanceof ImageWrapper32 image) {
            var source = image.data();
            var width = image.width();
            var height = image.height();
            var output = new float[source.length];
            System.arraycopy(source, 0, output, 0, source.length);
            consumer.accept(width, height, output);
            return new ImageWrapper32(image.width(), image.height(), output);
        } else if (arg instanceof List<?> list) {
            return list.stream().map(e -> monoToMonoImageTransformer(name, maxArgCount, List.of(e), consumer)).toList();
        }
        throw new IllegalArgumentException(name + "first argument must be an image or a list of images");
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

    private Object applyFunction(List<Object> arguments, Function<DoubleStream, OptionalDouble> operator) {
        if (arguments.size() == 1) {
            if (arguments.get(0) instanceof List<?> list) {
                // unwrap
                //noinspection unchecked
                return applyFunction((List<Object>) list, operator);
            }
        }
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("avg() must have at least one argument");
        }
        var types = arguments.stream().map(Object::getClass).distinct().toList();
        if (types.size() > 1) {
            throw new IllegalArgumentException("avg() only works on arguments of the same type");
        }
        var type = types.get(0);
        if (Number.class.isAssignableFrom(type)) {
            return operator.apply(arguments.stream()
                            .map(Number.class::cast)
                            .mapToDouble(Number::doubleValue))
                    .orElse(0);
        }
        if (ImageWrapper32.class.isAssignableFrom(type)) {
            var images = arguments
                    .stream()
                    .map(ImageWrapper32.class::cast)
                    .toList();
            var first = images.get(0);
            var width = first.width();
            var height = first.height();
            var length = first.data().length;
            if (images.stream().anyMatch(i -> i.data().length != length || i.width() != width || i.height() != height)) {
                throw new IllegalArgumentException("All images must have the same dimensions");
            }
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                var idx = i;
                result[i] = (float) operator.apply(images.stream().mapToDouble(img -> img.data()[idx])).orElse(0);
            }
            return new ImageWrapper32(width, height, result);
        }
        throw new IllegalArgumentException("Unexpected argument type '" + type + "'");
    }

    private Object apply(ImageWrapper32 leftImage,
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

    private ImageWrapper32 asImage(Object source) {
        if (source instanceof ImageWrapper32 image) {
            return image;
        }
        return null;
    }

    private Number asScalar(Object source) {
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

    @FunctionalInterface
    interface ImageConsumer {
        void accept(int width, int height, float[] data);
    }
}
