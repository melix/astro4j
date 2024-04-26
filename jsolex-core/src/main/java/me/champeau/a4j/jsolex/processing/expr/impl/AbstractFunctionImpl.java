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

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

class AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFunctionImpl.class);

    protected final Map<Class<?>, Object> context;
    protected final Broadcaster broadcaster;

    protected AbstractFunctionImpl(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        this.context = context;
        this.broadcaster = broadcaster;
    }

    protected Optional<Ellipse> getEllipse(List<Object> arguments, int index) {
        if (index >= 0) {
            return getArgument(Ellipse.class, arguments, index)
                .or(() -> findEllipseInArguments(arguments))
                .or(() -> getFromContext(Ellipse.class));
        }
        return findEllipseInArguments(arguments)
            .or(() -> getFromContext(Ellipse.class));
    }

    private static Optional<Ellipse> findEllipseInArguments(List<Object> arguments) {
        if (arguments.isEmpty()) {
            return Optional.empty();
        }
        var first = arguments.get(0);
        if (first instanceof ImageWrapper img) {
            return img.findMetadata(Ellipse.class);
        }
        return Optional.empty();
    }

    protected <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    protected void assertExpectedArgCount(List<Object> arguments, String help, int min, int max) {
        var size = arguments.size();
        if (size < min || size > max) {
            throw new IllegalArgumentException(help);
        }
    }

    protected <T> Optional<T> getArgument(Class<T> clazz, List<Object> args, int position) {
        if (position < args.size()) {
            return Optional.of((T) args.get(position));
        }
        return Optional.empty();
    }

    protected double doubleArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).doubleValue();
    }

    protected float floatArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).floatValue();
    }

    protected int intArg(List<Object> arguments, int position) {
        return getAsNumber(arguments, position).intValue();
    }

    protected String stringArg(List<Object> arguments, int position) {
        return getArgument(String.class, arguments, position).orElseThrow();
    }

    protected Number getAsNumber(List<Object> arguments, int position) {
        if (position < arguments.size()) {
            var obj = arguments.get(position);
            if (obj instanceof Number num) {
                return num;
            }
            throw new IllegalStateException("Expected to find a number argument at position " + position + " but it as a " + obj.getClass());
        }
        throw new IllegalStateException("Not enough arguments to select a number at position " + position);
    }

    @SuppressWarnings("unchecked")
    public List<Object> expandToImageList(String currentFunction, List<Object> arguments, Function<List<Object>, Object> function) {
        record IndexedObject(Object image, int idx) {
        }
        var listOfImages = (List) arguments.get(0);
        var params = arguments.subList(1, arguments.size());
        var array = listOfImages.toArray(new Object[0]);
        var itemsToProcess = IntStream.range(0, array.length)
            .mapToObj(i -> new IndexedObject(array[i], i))
            .toList();
        var progress = new AtomicInteger(0);
        var processed = itemsToProcess.stream()
            .parallel()
            .map(o -> {
                var idx = o.idx;
                var image = o.image;
                var allArgs = new ArrayList<>();
                allArgs.add(image);
                allArgs.addAll(params);
                var p = progress.incrementAndGet();
                broadcaster.broadcast(ProgressEvent.of(p / (double) array.length, "ImageMath: " + currentFunction));
                var result = function.apply(allArgs);
                if (result instanceof ImageWrapper img && !(result instanceof FileBackedImage)) {
                    // save memory!
                    result = FileBackedImage.wrap(img);
                }
                return new IndexedObject(result, idx);
            }).toList();
        // iterate on keys to preserve order
        broadcaster.broadcast(ProgressEvent.of(1, currentFunction));
        return processed.stream().sorted(Comparator.comparingInt(IndexedObject::idx)).map(IndexedObject::image).toList();
    }

    public Object monoToMonoImageTransformer(String name, int maxArgCount, List<Object> arguments, ImageConsumer consumer) {
        if (arguments.size() > maxArgCount) {
            throw new IllegalArgumentException("Invalid number of arguments on '" + name + "' call");
        }
        var arg = arguments.get(0);
        if (arg instanceof FileBackedImage fileBackedImage) {
            arg = fileBackedImage.unwrapToMemory();
        }
        if (arg instanceof ImageWrapper32 image) {
            var copy = image.copy();
            consumer.accept(copy);
            return copy;
        } else if (arg instanceof List<?>) {
            return expandToImageList(name, arguments, e -> monoToMonoImageTransformer(name, maxArgCount, e, consumer));
        }
        throw new IllegalArgumentException(name + "first argument must be a mono image or a list of images");

    }

    @FunctionalInterface
    public interface ImageConsumer {
        void accept(ImageWrapper32 image);
    }
}
