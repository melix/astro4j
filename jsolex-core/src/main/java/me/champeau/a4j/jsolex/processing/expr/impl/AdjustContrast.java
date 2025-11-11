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
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ContrastAdjustmentStrategy;
import me.champeau.a4j.jsolex.processing.stretching.GammaStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.tasks.ImageAnalysis;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdjustContrast extends AbstractFunctionImpl {
    public AdjustContrast(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object adjustContrast(Map<String, Object> arguments) {
        BuiltinFunction.ADJUST_CONTRAST.validateArgs(arguments);
        int min = intArg(arguments, "min", 0);
        int max = intArg(arguments, "max", 255);
        if (min < 0 || min > 255) {
            throw new IllegalArgumentException("adjust_contrast min must be between 0 and 255");
        }
        if (max < 0 || max > 255) {
            throw new IllegalArgumentException("adjust_contrast max must be between 0 and 255");
        }
        return monoToMonoImageTransformer("adjust_contrast", "img", arguments, image -> new ContrastAdjustmentStrategy(min << 8, max << 8).stretch(image));
    }

    public Object adjustGamma(Map<String, Object> arguments) {
        BuiltinFunction.ADJUST_GAMMA.validateArgs(arguments);
        double gamma = doubleArg(arguments, "gamma", 1);
        if (gamma <= 0) {
            throw new IllegalArgumentException("gamma must be positive");
        }
        return monoToMonoImageTransformer("adjust_gamma", "img", arguments, image -> new GammaStrategy(gamma).stretch(image));
    }

    public Object autoContrast(Map<String, Object> arguments) {
        BuiltinFunction.AUTO_CONTRAST.validateArgs(arguments);
        double gamma = doubleArg(arguments, "gamma", 1);
        if (gamma < 1) {
            throw new IllegalArgumentException("gamma must be greater than 1");
        }
        double bgThreshold = doubleArg(arguments, "bg", AutohistogramStrategy.DEFAULT_BACKGROUND_THRESHOLD);
        double protusStretch = doubleArg(arguments, "protusStretch", AutohistogramStrategy.DEFAULT_PROM_STRETCH);
        return monoToMonoImageTransformer("auto_contrast", "img", arguments, image -> new AutohistogramStrategy(gamma, true, bgThreshold, protusStretch).stretch(image));
    }

    public Object equalize(Map<String, Object> arguments) {
        BuiltinFunction.EQUALIZE.validateArgs(arguments);
        if (!(arguments.get("list") instanceof List<?> topLevel) || topLevel.size() != 1) {
            throw new IllegalArgumentException("equalize requires a list of images");
        }
        if (topLevel.getFirst() instanceof Map<?, ?> map && map.size()==1 && map.containsKey("list")) {
            topLevel = List.of(map.get("list"));
        }
        if (!(topLevel.getFirst() instanceof List<?> list)) {
            throw new IllegalArgumentException("equalize requires a list of images");
        }
        if (list.size() < 2) {
            return list;
        }
        var firstImage = ((ImageWrapper) list.getFirst()).unwrapToMemory();
        if (firstImage instanceof ImageWrapper32) {
            return equalizeMono(list);
        } else if (firstImage instanceof RGBImage) {
            return equalizeRGB(list);
        } else {
            throw new IllegalArgumentException("equalize only supports mono and RGB images");
        }
    }

    private Object equalizeMono(List<?> list) {
        var stats = list.stream()
                .parallel()
                .map(i -> {
                    var img = ((ImageWrapper) i).unwrapToMemory();
                    if (!(img instanceof ImageWrapper32 mono)) {
                        throw new IllegalArgumentException("equalize requires all images to be of the same type");
                    }
                    return new Object() {
                        private final ImageWrapper32 image = mono.copy();
                        private final ImageAnalysis analysis = ImageAnalysis.of(image.data());
                    };
                })
                .collect(Collectors.toMap(o -> o.image, o -> o.analysis, (e1, e2) -> e1, LinkedHashMap::new));
        var average = stats.values().stream()
                .mapToDouble(ImageAnalysis::avg)
                .average()
                .orElse(0);
        var stddev = stats.values().stream()
                .mapToDouble(ImageAnalysis::stddev)
                .average()
                .orElse(0);
        return stats.entrySet()
                .stream()
                .map(e -> {
                    var image = e.getKey();
                    var analysis = e.getValue();
                    equalizeChannel(image.data(), analysis, (float) average, (float) stddev);
                    return FileBackedImage.wrap(image);
                })
                .toList();
    }

    private Object equalizeRGB(List<?> list) {
        record ChannelStats(ImageAnalysis r, ImageAnalysis g, ImageAnalysis b) {}
        var stats = list.stream()
                .parallel()
                .map(i -> {
                    var img = ((ImageWrapper) i).unwrapToMemory();
                    if (!(img instanceof RGBImage rgb)) {
                        throw new IllegalArgumentException("equalize requires all images to be of the same type");
                    }
                    return new Object() {
                        private final RGBImage image = rgb.copy();
                        private final ChannelStats analysis = new ChannelStats(
                                ImageAnalysis.of(image.r()),
                                ImageAnalysis.of(image.g()),
                                ImageAnalysis.of(image.b())
                        );
                    };
                })
                .collect(Collectors.toMap(o -> o.image, o -> o.analysis, (e1, e2) -> e1, LinkedHashMap::new));
        var avgR = stats.values().stream()
                .mapToDouble(cs -> cs.r.avg())
                .average()
                .orElse(0);
        var stddevR = stats.values().stream()
                .mapToDouble(cs -> cs.r.stddev())
                .average()
                .orElse(0);
        var avgG = stats.values().stream()
                .mapToDouble(cs -> cs.g.avg())
                .average()
                .orElse(0);
        var stddevG = stats.values().stream()
                .mapToDouble(cs -> cs.g.stddev())
                .average()
                .orElse(0);
        var avgB = stats.values().stream()
                .mapToDouble(cs -> cs.b.avg())
                .average()
                .orElse(0);
        var stddevB = stats.values().stream()
                .mapToDouble(cs -> cs.b.stddev())
                .average()
                .orElse(0);
        return stats.entrySet()
                .stream()
                .map(e -> {
                    var image = e.getKey();
                    var analysis = e.getValue();
                    equalizeChannel(image.r(), analysis.r, (float) avgR, (float) stddevR);
                    equalizeChannel(image.g(), analysis.g, (float) avgG, (float) stddevG);
                    equalizeChannel(image.b(), analysis.b, (float) avgB, (float) stddevB);
                    return FileBackedImage.wrap(image);
                })
                .toList();
    }

    private static void equalizeChannel(float[][] data, ImageAnalysis analysis, float targetAvg, float targetStddev) {
        int height = data.length;
        int width = data[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double v = data[y][x] - analysis.avg();
                v = v * targetStddev / analysis.stddev();
                v += targetAvg;
                data[y][x] = (float) Math.clamp(v, 0, Constants.MAX_PIXEL_VALUE);
            }
        }
    }
}
