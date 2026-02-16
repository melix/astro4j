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

/** Base class for function implementations. */
class AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFunctionImpl.class);

    /**
     * The evaluation context containing shared objects.
     */
    protected final Map<Class<?>, Object> context;

    /**
     * Broadcaster for publishing progress events.
     */
    protected final Broadcaster broadcaster;

    /**
     * Creates a new function implementation.
     *
     * @param context the evaluation context
     * @param broadcaster the broadcaster for progress events
     */
    protected AbstractFunctionImpl(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        this.context = context;
        this.broadcaster = broadcaster;
    }

    /**
     * Creates a new child progress operation with the given name.
     * The parent is looked up from context each time to ensure we use
     * the correctly-populated ProgressOperation from script execution.
     *
     * @param name the name for the operation
     * @return a new progress operation
     */
    protected ProgressOperation newOperation(String name) {
        var parent = (ProgressOperation) context.get(ProgressOperation.class);
        if (parent == null) {
            return ProgressOperation.root(name, e -> {
            });
        }
        return parent.createChild(name);
    }

    /**
     * Creates a new child progress operation with an empty name.
     *
     * @return a new progress operation
     */
    protected ProgressOperation newOperation() {
        return newOperation("");
    }

    /**
     * Gets an ellipse from arguments or context.
     *
     * @param arguments the function arguments
     * @param key the argument key
     * @return the ellipse, if found
     */
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

    /**
     * Gets a value from the evaluation context.
     *
     * @param type the type to retrieve
     * @param <T> the type parameter
     * @return the value, if found
     */
    protected <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    /**
     * Gets an argument from the argument map.
     *
     * @param clazz the expected type
     * @param args the arguments map
     * @param key the argument key
     * @param <T> the type parameter
     * @return the argument value, if found
     */
    protected <T> Optional<T> getArgument(Class<T> clazz, Map<String, Object> args, String key) {
        return Optional.ofNullable((T) args.get(key));
    }

    /**
     * Gets a double argument with default value.
     *
     * @param arguments the arguments map
     * @param key the argument key
     * @param defaultValue the default value
     * @return the argument value or default
     */
    protected double doubleArg(Map<String, Object> arguments, String key, double defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getAsNumber(arguments, key).doubleValue();
    }

    /**
     * Gets a float argument with default value.
     *
     * @param arguments the arguments map
     * @param key the argument key
     * @param defaultValue the default value
     * @return the argument value or default
     */
    protected float floatArg(Map<String, Object> arguments, String key, float defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getAsNumber(arguments, key).floatValue();
    }

    /**
     * Converts an object to a float value.
     *
     * @param obj the object to convert
     * @param defaultValue the default value if obj is null
     * @return the float value
     */
    protected float floatArg(Object obj, float defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Number num) {
            return num.floatValue();
        } else if (obj instanceof CharSequence str) {
            return Float.parseFloat(str.toString());
        }
        throw new IllegalStateException("Expected a number but got " + obj.getClass());
    }

    /**
     * Gets an int argument with default value.
     *
     * @param arguments the arguments map
     * @param key the argument key
     * @param defaultValue the default value
     * @return the argument value or default
     */
    protected int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getAsNumber(arguments, key).intValue();
    }

    /**
     * Gets a string argument with default value.
     *
     * @param arguments the arguments map
     * @param key the argument key
     * @param defaultValue the default value
     * @return the argument value or default
     */
    protected String stringArg(Map<String, Object> arguments, String key, String defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        return getArgument(String.class, arguments, key).orElseThrow();
    }

    /**
     * Gets a boolean argument with default value.
     * Supports both numeric values (0=false, non-zero=true) and string values ("true"/"false").
     *
     * @param arguments the arguments map
     * @param key the argument key
     * @param defaultValue the default value
     * @return the argument value or default
     */
    protected boolean booleanArg(Map<String, Object> arguments, String key, boolean defaultValue) {
        if (!arguments.containsKey(key)) {
            return defaultValue;
        }
        var obj = arguments.get(key);
        if (obj instanceof Boolean bool) {
            return bool;
        } else if (obj instanceof Number num) {
            return num.doubleValue() != 0;
        } else if (obj instanceof CharSequence str) {
            return Boolean.parseBoolean(str.toString());
        }
        throw new IllegalStateException("Expected to find a boolean argument for argument " + key + " but it was a " + obj.getClass());
    }

    /**
     * Gets an argument as a Number.
     *
     * @param arguments the arguments map
     * @param key the argument key
     * @return the argument value as a Number
     */
    protected Number getAsNumber(Map<String, Object> arguments, String key) {
        var obj = arguments.get(key);
        if (obj instanceof Number num) {
            return num;
        } else if (obj instanceof CharSequence str) {
            return Double.parseDouble(str.toString());
        }
        throw new IllegalStateException("Expected to find a number argument for argument " + key + " but it as a " + obj.getClass());
    }

    /**
     * Expands a list argument to process each element in parallel.
     *
     * @param currentFunction the function name
     * @param key the argument key
     * @param arguments the arguments map
     * @param function the function to apply to each element
     * @return the list of results
     */
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
        var parallelOperation = newOperation("ImageMath: " + currentFunction);
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

    /**
     * Applies a transformation to mono images.
     *
     * @param name the function name
     * @param key the argument key
     * @param arguments the arguments map
     * @param consumer the image consumer
     * @return the transformed image or list of images
     */
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

    /**
     * Applies a unary function to images.
     *
     * @param arguments the arguments map
     * @param name the function name
     * @param key the argument key
     * @param function the unary function
     * @return the transformed image or list of images
     */
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

    /**
     * Applies a unary transformation to images.
     *
     * @param arguments the arguments map
     * @param name the function name
     * @param key the argument key
     * @param transformer the mono image transformer
     * @return the transformed image or list of images
     */
    protected Object applyUnary(Map<String, Object> arguments, String name, String key, MonoImageTransformer transformer) {
        var arg = arguments.get(key);
        if (arg instanceof List<?>) {
            return expandToImageList(name, key, arguments, list -> applyUnary(list, name, key, transformer));
        }
        var img = arguments.get(key);
        return applyUnary(img, transformer);
    }

    /**
     * Applies a binary function to images.
     *
     * @param arguments the arguments map
     * @param left the left argument key
     * @param right the right argument key
     * @param name the function name
     * @param function the binary function
     * @return the transformed image or list of images
     */
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
            transformer.postProcess(copy);
            return copy;
        } else if (img instanceof RGBImage rgb) {
            var copy = rgb.copy();
            transformer.transform(rgb.width(), rgb.height(), copy.r());
            transformer.transform(rgb.width(), rgb.height(), copy.g());
            transformer.transform(rgb.width(), rgb.height(), copy.b());
            transformer.postProcess(copy);
            return copy;
        }
        throw new IllegalStateException("Unexpected image type " + img);
    }

    /** Functional interface for consuming images. */
    @FunctionalInterface
    public interface ImageConsumer {
        /**
         * Accepts an image.
         *
         * @param image the image to accept
         */
        void accept(ImageWrapper image);
    }

    /** Functional interface for transforming mono images. */
    @FunctionalInterface
    public interface MonoImageTransformer {
        /**
         * Transforms image data.
         *
         * @param width the image width
         * @param height the image height
         * @param data the image data
         */
        void transform(int width, int height, float[][] data);

        /**
         * Post-processes the image after transformation.
         *
         * @param image the image to post-process
         */
        default void postProcess(ImageWrapper image) {

        }
    }
}
