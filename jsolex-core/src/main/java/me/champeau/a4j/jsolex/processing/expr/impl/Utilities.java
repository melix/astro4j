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
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MetadataMerger;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.sqrt;

public class Utilities extends AbstractFunctionImpl {
    private static final String DEFAULT_DATE_FORMAT = "yyyy/MM/dd HH:mm:ss [z]";

    public Utilities(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    // computes the distance to the circle center, relative to the radius. A negative value
    // means that the point is inside the circle, a positive value means that the point is outside
    // the circle. The distance is normalized so that it is 0 at the circle border, and 1 at the
    // circle center.
    public static double normalizedDistanceToCenter(double x, double y, double cx, double cy, double radius) {
        var dx = x - cx;
        var dy = y - cy;
        double distance = sqrt(dx * dx + dy * dy);
        return distance / radius;
    }

    /**
     * Blends two images together, using a cosine function to interpolate between the two images.
     * The blending is done in a circular region defined by the ellipse.
     * @param disk the disk image
     * @param protus the prominences image
     * @param ellipse the ellipse defining the blending region
     * @param start the start distance for blending
     * @param end the end distance for blending
     * @return the blended image
     */
    public static ImageWrapper32 blend(ImageWrapper32 disk, ImageWrapper32 protus, Ellipse ellipse, double start, double end) {
        int height = protus.height();
        int width = protus.width();
        var diskData = disk.data();
        var protusData = protus.data();
        var cx = ellipse.center().a();
        var cy = ellipse.center().b();
        var radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2;
        var result = disk.copy();
        var outputData = result.data();
        var range = end - start;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var d = diskData[y][x];
                var p = protusData[y][x];
                var dist = normalizedDistanceToCenter(x, y, cx, cy, radius);
                if (dist <= start) {
                    outputData[y][x] = d;
                } else if (dist <= end) {
                    var alpha = (float) (0.5 * (1 + Math.cos(Math.PI * (dist - start) / range)));
                    outputData[y][x] = alpha * d + (1 - alpha) * p;
                } else {
                    outputData[y][x] = p;
                }
            }
        }
        return result;
    }

    public Object videoDateTime(Map<String ,Object> arguments) {
        BuiltinFunction.VIDEO_DATETIME.validateArgs(arguments);
        if (arguments.get("img") instanceof List) {
            return expandToImageList("video_datetime", "img", arguments, this::videoDateTime);
        }
        if (arguments.get("img") instanceof ImageWrapper image) {
            var params = image.findMetadata(ProcessParams.class).orElse((ProcessParams) context.get(ProcessParams.class));
            if (params == null) {
                return "Unknown date";
            }
            var date = params.observationDetails().date();
            return date.format(DateTimeFormatter.ofPattern(stringArg(arguments, "format", DEFAULT_DATE_FORMAT)));
        }
        return "Unknown date";
    }

    public List<Object> sort(Map<String ,Object> arguments) {
        BuiltinFunction.SORT.validateArgs(arguments);
        if (!(arguments.get("images") instanceof List list)) {
            throw new IllegalArgumentException("sort() accepts two arguments (images, [sort order])");
        }
        var ordering = stringArg(arguments, "order", "shift");
        boolean reverse = ordering.endsWith(" desc");
        if (reverse) {
            ordering = ordering.substring(0, ordering.length() - 5);
        }
        Comparator<? super ImageWrapper> comparator = null;
        if ("date".equals(ordering)) {
            comparator = Comparator.comparing(image -> image.findMetadata(ProcessParams.class).orElse((ProcessParams) context.get(ProcessParams.class)).observationDetails().date());
        } else if ("shift".equals(ordering)) {
            comparator = Comparator.comparingDouble(image -> image.findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0.0));
        } else if ("file_name".equals(ordering)) {
            comparator = Comparator.comparing(image -> image.findMetadata(MetadataTable.class)
                .flatMap(t -> t.get(MetadataTable.FILE_NAME))
                .orElseGet(() -> image.findMetadata(SourceInfo.class).map(SourceInfo::serFileName).orElse(""))
            );
        }
        if (comparator == null) {
            return list;
        }
        if (reverse) {
            comparator = comparator.reversed();
        }
        return list.stream()
            .sorted(comparator)
            .toList();
    }

    public Object extractChannel(Map<String ,Object> arguments, int channel) {
        if (arguments.get("img") instanceof List) {
            return expandToImageList("extractChannel", "img", arguments, args -> extractChannel(args, channel));
        }
        var image = arguments.get("img");
        if (image instanceof ImageWrapper wrapper) {
            wrapper = wrapper.unwrapToMemory();
            if (wrapper instanceof ImageWrapper32 mono) {
                return mono;
            }
            if (wrapper instanceof RGBImage rgb) {
                var metadata = new HashMap<>(rgb.metadata());
                return switch (channel) {
                    case 0 -> new ImageWrapper32(rgb.width(), rgb.height(), rgb.r(), metadata);
                    case 1 -> new ImageWrapper32(rgb.width(), rgb.height(), rgb.g(), metadata);
                    case 2 -> new ImageWrapper32(rgb.width(), rgb.height(), rgb.b(), metadata);
                    default -> throw new IllegalArgumentException("Channel must be an int between 0 and 2");
                };
            }
        }
        throw new IllegalArgumentException("Unexpected argument type: " + image.getClass());
    }

    public Object toMono(Map<String ,Object> arguments) {
        BuiltinFunction.MONO.validateArgs(arguments);
        if (arguments.get("img") instanceof List) {
            return expandToImageList("toMono", "img", arguments, this::toMono);
        }
        var image = arguments.get("img");
        if (image instanceof ImageWrapper wrapper) {
            wrapper = wrapper.unwrapToMemory();
            if (wrapper instanceof ImageWrapper32 mono) {
                return mono;
            }
            if (wrapper instanceof RGBImage rgb) {
                return rgb.toMono();
            }
        }
        throw new IllegalArgumentException("Unexpected argument type: " + image.getClass());
    }

    public Object doGetAt(Map<String ,Object> arguments) {
        BuiltinFunction.GET_AT.validateArgs(arguments);
        if (!(arguments.get("list") instanceof List list)) {
            throw new IllegalArgumentException("get_at expects a list of images as first argument");
        }
        if (!list.isEmpty() && list.getFirst() instanceof List) {
            return expandToImageList("get_at", "list", arguments, this::doGetAt);
        }
        var index = intArg(arguments, "index", -1);
        return list.get(index);
    }

    /**
     * Concatenates a list of lists into a single list
     * @param arguments the list of lists to concatenate
     * @return a single list
     */
    public Object concat(Map<String ,Object> arguments) {
        BuiltinFunction.CONCAT.validateArgs(arguments);
        var list = (List<?>) arguments.get("list");
        if (list.stream().allMatch(i -> i instanceof List<?>)) {
            return list.stream()
                .map(List.class::cast)
                .flatMap(List::stream)
                .toList();
        }
        return arguments;
    }

    public static List<?> tryGetList(Map<String, Object> arguments, String key) {
        var value = arguments.get(key);
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() == 1 && map.get("list") instanceof List<?> list) {
                return list;
            }
        }
        return null;
    }

    public Object weightedAverage(Map<String ,Object> arguments) {
        BuiltinFunction.WEIGHTED_AVG.validateArgs(arguments);
        if (!(tryGetList(arguments, "images") instanceof List<?> images)) {
            throw new IllegalArgumentException("weighted_average expects a list of images as first argument");
        }
        if (!(tryGetList(arguments, "weights") instanceof List<?> weights)) {
            throw new IllegalArgumentException("weighted_average expects a list of weights as second argument");
        }
        if (images.size() != weights.size()) {
            throw new IllegalArgumentException("weighted_average expects the same number of images and weights");
        }
        if (images.isEmpty()) {
            return List.of();
        }
        var firstImage = (ImageWrapper) images.getFirst();
        var width = firstImage.width();
        var height = firstImage.height();
        var data = new float[height][width];
        var monoImages = new ArrayList<ImageWrapper32>();
        for (Object image : images) {
            if (!(image instanceof ImageWrapper wrapper)) {
                throw new IllegalArgumentException("weighted_average expects a list of images as first argument");
            }
            if (wrapper.width() != width || wrapper.height() != height) {
                throw new IllegalArgumentException("All images must have the same dimensions");
            }
            if (!(wrapper.unwrapToMemory() instanceof ImageWrapper32 mono)) {
                throw new IllegalArgumentException("weighted_average only supports mono images");
            }
            monoImages.add(mono);
            var pixels = mono.data();
            var weight = ((Number) weights.get(images.indexOf(image))).doubleValue();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    data[y][x] += weight * pixels[y][x];
                }
            }
        }
        var totalWeight = weights.stream().mapToDouble(o -> ((Number)o).doubleValue()).sum();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] /= totalWeight;
            }
        }
        var metadata = MetadataMerger.merge(monoImages);
        return new ImageWrapper32(width, height, data, metadata);
    }
}
