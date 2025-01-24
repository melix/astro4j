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
package me.champeau.a4j.jsolex.processing.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public record RGBImage(
    int width,
    int height,
    float[][] r,
    float[][] g,
    float[][] b,
    Map<Class<?>, Object> metadata
) implements ImageWrapper {
    @Override
    public RGBImage copy() {
        return new RGBImage(width, height, ImageWrapper.copyData(r), ImageWrapper.copyData(g), ImageWrapper.copyData(b), new LinkedHashMap<>(metadata));
    }

    public static RGBImage toRGB(ImageWrapper32 mono) {
        var monoData = mono.data();
        return new RGBImage(mono.width(), mono.height(), ImageWrapper.copyData(monoData), ImageWrapper.copyData(monoData), ImageWrapper.copyData(monoData), new LinkedHashMap<>(mono.metadata()));
    }

    public static RGBImage fromMono(ImageWrapper32 mono, Function<ImageWrapper32, float[][][]> converter) {
        return fromMono(mono, converter, new LinkedHashMap<>(mono.metadata()));
    }

    public static RGBImage fromMono(ImageWrapper32 mono, Function<ImageWrapper32, float[][][]> converter, Map<Class<?>, Object> metadata) {
        var rgb = converter.apply(mono);
        return new RGBImage(mono.width(), mono.height(), rgb[0], rgb[1], rgb[2], metadata);
    }

    public ImageWrapper32 toMono() {
        float[][] monoData = new float[height][width];
        for (int y = 0; y < height; y++) {
            monoData[y] = new float[width];
            for (int x = 0; x < width; x++) {
                monoData[y][x] = 0.299f * r[y][x] + 0.587f * g[y][x] + 0.114f * b[y][x];
            }
        }
        return new ImageWrapper32(width, height, monoData, new LinkedHashMap<>(metadata));
    }
}
