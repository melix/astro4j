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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Utilities extends AbstractFunctionImpl {
    private static final String DEFAULT_DATE_FORMAT = "yyyy/MM/dd HH:mm:ss [z]";
    public Utilities(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object videoDateTime(List<Object> arguments) {
        if (arguments.size() > 2) {
            throw new IllegalArgumentException("video_datetime() accepts one or two arguments (image, [format])");
        }
        if (arguments.getFirst() instanceof List) {
            return expandToImageList("video_datetime", arguments, this::videoDateTime);
        }
        if (arguments.getFirst() instanceof ImageWrapper image) {
            var params = image.findMetadata(ProcessParams.class).orElse((ProcessParams) context.get(ProcessParams.class));
            if (params == null) {
                return "Unknown date";
            }
            var date = params.observationDetails().date();
            if (arguments.size() == 1) {
                return date.format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
            }
            return date.format(DateTimeFormatter.ofPattern(stringArg(arguments, 1)));
        }
        return "Unknown date";
    }

    public List<Object> sort(List<Object> arguments) {
        if (arguments.size() > 2) {
            throw new IllegalArgumentException("sort() accepts two arguments (images, [sort order])");
        }
        if (!(arguments.getFirst() instanceof List list)) {
            throw new IllegalArgumentException("sort() accepts two arguments (images, [sort order])");
        }
        var ordering = arguments.size() == 2 ? stringArg(arguments, 1) : "shift";
        boolean reverse = ordering.endsWith(" desc");
        if (reverse) {
            ordering = ordering.substring(0, ordering.length() - 5);
        }
        Comparator<? super ImageWrapper> comparator = null;
        if ("date".equals(ordering)) {
            comparator = Comparator.comparing(image -> image.findMetadata(ProcessParams.class).orElse((ProcessParams) context.get(ProcessParams.class)).observationDetails().date());
        } else if ("shift".equals(ordering)) {
            comparator = Comparator.comparingDouble(image -> image.findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0.0));
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


}
