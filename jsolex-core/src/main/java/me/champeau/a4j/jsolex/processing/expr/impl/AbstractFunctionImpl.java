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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.IntStream;

class AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFunctionImpl.class);

    protected final Map<Class<?>, Object> context;
    protected final Broadcaster broadcaster;
    private final ProgressOperation operation;

    protected AbstractFunctionImpl(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        this.context = context;
        this.broadcaster = broadcaster;
        this.operation = newProgress("");
    }

    private ProgressOperation newProgress(String name) {
        var parent = (ProgressOperation) context.get(ProgressOperation.class);
        if (parent == null) {
            return ProgressOperation.root(name, e -> {
            });
        }
        return parent.createChild(name);
    }

    protected ProgressOperation newOperation() {
        return operation.createChild(operation.task());
    }

    protected Optional<Ellipse> getEllipse(Map<String, Object> arguments, String key) {
        return getArgument(Ellipse.class, arguments, key)
                .or(() -> findEllipseInArguments(arguments))
                .or(() -> getFromContext(Ellipse.class));
    }

    private static Optional<Ellipse> findEllipseInArguments(Map<String, Object> arguments) {
        var first = arguments.get("img");
        if (first instanceof ImageWrapper img) {
            return img.findMetadata(Ellipse.class);
        }
        return Optional.empty();
    }

    protected <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    protected <T> Optional<T> getArgument(Class<T> clazz, Map<String, Object> args, String key) {
        return Optional.ofNullable((T) args.get(key));
    }

    protected double doubleArg(Map<String, Object> arguments, String key, double defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getAsNumber(arguments, key).doubleValue();
    }

    protected float floatArg(Map<String, Object> arguments, String key, float defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getAsNumber(arguments, key).floatValue();
    }

    protected int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getAsNumber(arguments, key).intValue();
    }

    protected String stringArg(Map<String, Object> arguments, String key, String defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getArgument(String.class, arguments, key).orElseThrow();
    }

    protected Number getAsNumber(Map<String, Object> arguments, String key) {
        var obj = arguments.get(key);
        if (obj instanceof Number num) {
            return num;
        } else if (obj instanceof CharSequence str) {
            return Double.parseDouble(str.toString());
        }
        throw new IllegalStateException("Expected to find a number argument for argument " + key + " but it as a " + obj.getClass());
    }

    @SuppressWarnings("unchecked")
    public List<Object> expandToImageList(String currentFunction,
                                          String key,
                                          Map<String, Object> arguments,
                                          Function<Map<String, Object>, Object> function) {
        record IndexedObject(Object image, int idx) {
        }
        var listOfImages = (List) arguments.get(key);
        var array = listOfImages.toArray(new Object[0]);
        var itemsToProcess = IntStream.range(0, array.length)
                .mapToObj(i -> new IndexedObject(array[i], i))
                .toList();
        var progress = new AtomicInteger(0);
        var parallelOperation = operation.createChild("ImageMath: " + currentFunction);
        var processed = itemsToProcess.stream()
                .parallel()
                .map(o -> {
                    var idx = o.idx;
                    var image = o.image;
                    var allArgs = new HashMap<>(arguments);
                    allArgs.put(key, image);
                    var p = progress.incrementAndGet();
                    broadcaster.broadcast(parallelOperation.update(p / (double) array.length));
                    var result = function.apply(allArgs);
                    if (result instanceof ImageWrapper img) {
                        // save memory!
                        result = FileBackedImage.wrap(img);
                    }
                    return new IndexedObject(result, idx);
                }).toList();
        // iterate on keys to preserve order
        broadcaster.broadcast(parallelOperation.complete());
        return processed.stream().sorted(Comparator.comparingInt(IndexedObject::idx)).map(IndexedObject::image).toList();
    }

    private static void applyFunction(int width, int height, float[][] data, DoubleUnaryOperator function) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) function.applyAsDouble(data[y][x]);
            }
        }
    }

    public Object monoToMonoImageTransformer(String name, String key, Map<String, Object> arguments, ImageConsumer consumer) {
        var arg = arguments.get(key);
        if (arg instanceof List<?>) {
            return expandToImageList(name, key, arguments, e -> monoToMonoImageTransformer(name, key, e, consumer));
        } else if (arg instanceof ImageWrapper image) {
            var copy = image.unwrapToMemory().copy();
            consumer.accept(copy);
            return copy;
        }
        throw new IllegalArgumentException(name + " first argument must be a mono image or a list of images");
    }

    protected Object applyUnary(Map<String, Object> arguments, String name, String key, DoubleUnaryOperator function) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException(name + " takes 1 argument (image(s))");
        }
        var arg = arguments.get(key);
        if (arg instanceof List<?>) {
            return expandToImageList(name, key, arguments, list -> applyUnary(list, name, key, function));
        }
        var img = arguments.get(key);
        return applyUnary(img, function);
    }

    protected Object applyUnary(Map<String, Object> arguments, String name, String key, MonoImageTransformer transformer) {
        var arg = arguments.get(key);
        if (arg instanceof List<?>) {
            return expandToImageList(name, key, arguments, list -> applyUnary(list, name, key, transformer));
        }
        var img = arguments.get(key);
        return applyUnary(img, transformer);
    }

    protected Object applyBinary(Map<String, Object> arguments, String left, String right, String name, DoubleBinaryOperator function) {
        var arg = arguments.get(left);
        if (arg instanceof List<?>) {
            return expandToImageList(name, left, arguments, list -> applyBinary(list, left, right, name, function));
        }
        var img = arguments.get(left);
        var argument = doubleArg(arguments, right, 0);
        var unary = (DoubleUnaryOperator) v -> function.applyAsDouble(v, argument);
        return applyUnary(img, unary);
    }

    private Object applyUnary(Object img, DoubleUnaryOperator unary) {
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            var copy = mono.copy();
            applyFunction(mono.width(), mono.height(), copy.data(), unary);
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var copy = rgb.copy();
            applyFunction(rgb.width(), rgb.height(), copy.r(), unary);
            applyFunction(rgb.width(), rgb.height(), copy.g(), unary);
            applyFunction(rgb.width(), rgb.height(), copy.b(), unary);
            return copy;
        } else if (img instanceof Number number) {
            return unary.applyAsDouble(number.doubleValue());
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    private Object applyUnary(Object img, MonoImageTransformer transformer) {
        if (img instanceof FileBackedImage fileBackedImage) {
            img = fileBackedImage.unwrapToMemory();
        }
        if (img instanceof ImageWrapper32 mono) {
            var copy = mono.copy();
            transformer.transform(mono.width(), mono.height(), copy.data());
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var copy = rgb.copy();
            transformer.transform(rgb.width(), rgb.height(), copy.r());
            transformer.transform(rgb.width(), rgb.height(), copy.g());
            transformer.transform(rgb.width(), rgb.height(), copy.b());
            return copy;
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    @FunctionalInterface
    public interface ImageConsumer {
        void accept(ImageWrapper image);
    }

    @FunctionalInterface
    public interface MonoImageTransformer {
        void transform(int width, int height, float[][] data);
    }
}
