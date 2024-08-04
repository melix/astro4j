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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Filtering extends AbstractFunctionImpl {
    public Filtering(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object filter(List<Object> arguments) {
        assertExpectedArgCount(arguments, "filter takes 4 arguments (images, subject, function, value)", 4, 4);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("filter", arguments, this::filter).stream().filter(Objects::nonNull).toList();
        }
        var image = getArgument(ImageWrapper.class, arguments, 0).get();
        var subject = stringArg(arguments, 1);
        var function = stringArg(arguments, 2);
        var value = stringArg(arguments, 3);
        if (test(image, subject, function, value)) {
            return image;
        }
        return null;
    }

    private boolean test(ImageWrapper image, String subject, String function, String value) {
        var subjectObject = switch (subject) {
            case "fileName", "file-name" -> image.findMetadata(SourceInfo.class).map(SourceInfo::serFileName);
            case "dirName", "dir-name" -> image.findMetadata(SourceInfo.class).map(SourceInfo::parentDirName);
            case "pixelShift", "pixel-shift" -> image.findMetadata(PixelShift.class).map(PixelShift::pixelShift);
            case "date", "datetime", "dateTime" -> image.findMetadata(SourceInfo.class).map(SourceInfo::dateTime);
            case "time" -> image.findMetadata(SourceInfo.class).map(SourceInfo::dateTime).map(ZonedDateTime::toLocalTime);
            default -> Optional.empty();
        };
        return (switch (function) {
            case "==", "eq", "equals" -> subjectObject.map(v -> {
                if (v instanceof Number) {
                    return ((Number) v).doubleValue() == Double.parseDouble(value);
                }
                if (v instanceof ZonedDateTime) {
                    return v.equals(ZonedDateTime.parse(value));
                }
                if (v instanceof LocalTime) {
                    return v.equals(LocalTime.parse(value));
                }
                return v.equals(value);
            });
            case ">", "gt" -> subjectObject.map(v -> {
                if (v instanceof Number) {
                    return ((Number) v).doubleValue() > Double.parseDouble(value);
                }
                if (v instanceof ZonedDateTime zdt) {
                    return zdt.isAfter(ZonedDateTime.parse(value));
                }
                if (v instanceof LocalTime lt) {
                    return lt.isAfter(LocalTime.parse(value));
                }
                return false;
            });
            case "<", "lt" -> subjectObject.map(v -> {
                if (v instanceof Number) {
                    return ((Number) v).doubleValue() < Double.parseDouble(value);
                }
                if (v instanceof ZonedDateTime zdt) {
                    return zdt.isBefore(ZonedDateTime.parse(value));
                }
                if (v instanceof LocalTime lt) {
                    return lt.isBefore(LocalTime.parse(value));
                }
                return false;
            });
            case ">=", "ge" -> subjectObject.map(v -> {
                if (v instanceof Number) {
                    return ((Number) v).doubleValue() >= Double.parseDouble(value);
                }
                if (v instanceof ZonedDateTime zdt) {
                    return !zdt.isBefore(ZonedDateTime.parse(value));
                }
                if (v instanceof LocalTime lt) {
                    return !lt.isBefore(LocalTime.parse(value));
                }
                return false;
            });
            case "<=", "le" -> subjectObject.map(v -> {
                if (v instanceof Number) {
                    return ((Number) v).doubleValue() <= Double.parseDouble(value);
                }
                if (v instanceof ZonedDateTime zdt) {
                    return !zdt.isAfter(ZonedDateTime.parse(value));
                }
                if (v instanceof LocalTime lt) {
                    return !lt.isAfter(LocalTime.parse(value));
                }
                return false;
            });
            case "!=", "ne", "not-equals" -> subjectObject.map(v -> {
                if (v instanceof Number) {
                    return ((Number) v).doubleValue() != Double.parseDouble(value);
                }
                if (v instanceof ZonedDateTime zdt) {
                    return !zdt.equals(ZonedDateTime.parse(value));
                }
                if (v instanceof LocalTime lt) {
                    return !lt.equals(LocalTime.parse(value));
                }
                return !v.equals(value);
            });
            case "contains" -> subjectObject.filter(String.class::isInstance).map(String.class::cast).map(v -> v.contains(value));
            case "starts-with" -> subjectObject.filter(String.class::isInstance).map(String.class::cast).map(v -> v.startsWith(value));
            case "ends-with" -> subjectObject.filter(String.class::isInstance).map(String.class::cast).map(v -> v.endsWith(value));
            default -> Optional.<Boolean>empty();
        }).orElse(false);
    }
}
