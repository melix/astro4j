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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.applySigmaClippedAverage;
import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.applySigmaClippedMedian;
import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.median;

public class ImageStatistics extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageStatistics.class);

    /**
     * Ratio between the median absolute deviation and the standard deviation of a normal distribution.
     */
    private static final double MAD_TO_SIGMA = 1.4826;

    /**
     * Square root of the sum of the squared Laplacian kernel coefficients: white noise of
     * standard deviation sigma produces a response of standard deviation 6 sigma.
     */
    private static final double LAPLACIAN_NORM = 6;

    public ImageStatistics(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object imgAvg(Map<String, Object> arguments) {
        BuiltinFunction.IMG_AVG.validateArgs(arguments);
        return applyImageStatistic("IMG_AVG", arguments, DoubleStream::average);
    }

    public Object imgAvg2(Map<String, Object> arguments) {
        BuiltinFunction.IMG_AVG2.validateArgs(arguments);
        var sigma = ((Number) arguments.get("sigma")).doubleValue();
        return applyImageStatistic("IMG_AVG2", arguments, stream -> applySigmaClippedAverage(stream, sigma));
    }

    public Object imgMedian(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MEDIAN.validateArgs(arguments);
        return applyImageStatistic("IMG_MEDIAN", arguments, stream -> median(stream));
    }

    public Object imgMedian2(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MEDIAN2.validateArgs(arguments);
        var sigma = ((Number) arguments.get("sigma")).doubleValue();
        return applyImageStatistic("IMG_MEDIAN2", arguments, stream -> applySigmaClippedMedian(stream, sigma));
    }

    public Object imgMin(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MIN.validateArgs(arguments);
        return applyImageStatistic("IMG_MIN", arguments, DoubleStream::min);
    }

    public Object imgMax(Map<String, Object> arguments) {
        BuiltinFunction.IMG_MAX.validateArgs(arguments);
        return applyImageStatistic("IMG_MAX", arguments, DoubleStream::max);
    }

    public Object noiseSigma(Map<String, Object> arguments) {
        BuiltinFunction.NOISE_SIGMA.validateArgs(arguments);
        var list = flattenToImageList(arguments.get("img"));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("noise_sigma requires at least one image argument");
        }
        if (list.size() == 1) {
            return computeNoiseSigma(list.getFirst(), arguments);
        }
        return list.stream()
                .map(img -> computeNoiseSigma(img, arguments))
                .toList();
    }

    /**
     * Estimates the standard deviation of the noise of an image, by measuring the response
     * of a Laplacian high pass filter. The filter cancels any locally linear component, so
     * smooth structures do not contribute to the estimate, and the median of the absolute
     * responses keeps it robust against isolated outliers.
     */
    private double computeNoiseSigma(ImageWrapper32 image, Map<String, Object> arguments) {
        var mask = statsMask(arguments, image);
        var data = image.data();
        var width = image.width();
        var height = image.height();
        if (width < 3 || height < 3) {
            return 0;
        }
        var responses = new double[(width - 2) * (height - 2)];
        var count = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (mask != null && !mask.test(x, y)) {
                    continue;
                }
                var response = 4 * data[y][x]
                        - 2 * (data[y - 1][x] + data[y + 1][x] + data[y][x - 1] + data[y][x + 1])
                        + data[y - 1][x - 1] + data[y - 1][x + 1] + data[y + 1][x - 1] + data[y + 1][x + 1];
                responses[count++] = Math.abs(response);
            }
        }
        if (count == 0) {
            return 0;
        }
        Arrays.sort(responses, 0, count);
        var median = responses[count / 2];
        return MAD_TO_SIGMA * median / LAPLACIAN_NORM;
    }

    public Object logStats(Map<String, Object> arguments) {
        BuiltinFunction.LOG_STATS.validateArgs(arguments);
        var label = stringArg(arguments, "label", "");
        var images = flattenToImageList(arguments.get("img"));
        var index = new AtomicInteger();
        for (var image : images) {
            logStatsOf(image, arguments, label, index.getAndIncrement());
        }
        return arguments.get("img");
    }

    /**
     * Logs the statistics which describe the level, the spread and the noise of an image, so
     * that their evolution over a set of images can be compared on the same scale.
     */
    private void logStatsOf(ImageWrapper32 image, Map<String, Object> arguments, String label, int index) {
        var mask = statsMask(arguments, image);
        var data = image.data();
        var collected = new float[image.width() * image.height()];
        var count = 0;
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if (mask == null || mask.test(x, y)) {
                    collected[count++] = data[y][x];
                }
            }
        }
        if (count == 0) {
            LOGGER.info("log_stats {}[{}]: no pixel selected", label, index);
            return;
        }
        var sorted = Arrays.copyOf(collected, count);
        Arrays.sort(sorted);
        double sum = 0;
        for (var v : sorted) {
            sum += v;
        }
        var mean = sum / sorted.length;
        double variance = 0;
        for (var v : sorted) {
            variance += (v - mean) * (v - mean);
        }
        var stddev = Math.sqrt(variance / sorted.length);
        var lo = sorted[(int) Math.round(0.001 * (sorted.length - 1))];
        var hi = sorted[(int) Math.round(0.999 * (sorted.length - 1))];
        LOGGER.info(String.format(Locale.US,
                "log_stats %s[%d]: mean=%.1f median=%.1f stddev=%.1f span(0.1-99.9)=%.1f noise=%.2f min=%.1f max=%.1f pixels=%d",
                label, index, mean, sorted[sorted.length / 2], stddev, hi - lo,
                computeNoiseSigma(image, arguments), sorted[0], sorted[sorted.length - 1], sorted.length));
    }

    private Object applyImageStatistic(String name, Map<String, Object> arguments,
                                       Function<DoubleStream, OptionalDouble> operator) {
        var list = flattenToImageList(arguments.get("list"));
        if (list.isEmpty()) {
            throw new IllegalArgumentException(name + " requires at least one image argument");
        }
        if (list.size() == 1) {
            return computeImageStatistic(list.getFirst(), arguments, operator);
        }
        return list.stream()
                .map(img -> computeImageStatistic(img, arguments, operator))
                .toList();
    }

    private List<ImageWrapper32> flattenToImageList(Object input) {
        var result = new ArrayList<ImageWrapper32>();
        flattenToImageListRecursive(input, result);
        return result;
    }

    private void flattenToImageListRecursive(Object input, List<ImageWrapper32> result) {
        if (input instanceof List<?> list) {
            for (var item : list) {
                flattenToImageListRecursive(item, result);
            }
        } else if (input instanceof ImageWrapper32 img) {
            result.add(img);
        } else if (input instanceof ImageWrapper wrapper) {
            var unwrapped = wrapper.unwrapToMemory();
            if (unwrapped instanceof ImageWrapper32 img) {
                result.add(img);
            }
        }
    }

    private double computeImageStatistic(ImageWrapper32 image,
                                         Map<String, Object> arguments,
                                         Function<DoubleStream, OptionalDouble> operator) {
        var data = image.data();
        var height = data.length;
        var width = height > 0 ? data[0].length : 0;
        var mask = statsMask(arguments, image);
        var stream = IntStream.range(0, height)
                .boxed()
                .flatMapToDouble(y -> IntStream.range(0, width)
                        .filter(x -> mask == null || mask.test(x, y))
                        .mapToDouble(x -> data[y][x]));
        return operator.apply(stream).orElse(0);
    }
}
