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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class MetadataMerger {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataMerger.class);

    private MetadataMerger() {
    }

    public static Map<Class<?>, Object> merge(List<? extends ImageWrapper> images) {
        return merge(images, Map.of());
    }

    public static Map<Class<?>, Object> merge(
        List<? extends ImageWrapper> images,
        Map<Class<?>, BiFunction<List<?>, Map<Class<?>, Object>, Object>> customMergers
    ) {
        if (images.isEmpty()) {
            return MutableMap.of();
        }

        var metadata = new LinkedHashMap<Class<?>, Object>();

        for (var image : images) {
            metadata.putAll(image.metadata());
        }

        for (var entry : customMergers.entrySet()) {
            var metadataClass = entry.getKey();
            var merger = entry.getValue();
            var values = images.stream()
                .map(img -> img.findMetadata(metadataClass).orElse(null))
                .filter(Objects::nonNull)
                .toList();
            if (!values.isEmpty()) {
                var merged = merger.apply(values, metadata);
                if (merged != null) {
                    metadata.put(metadataClass, merged);
                }
            }
        }

        return metadata;
    }

    public static Map<Class<?>, BiFunction<List<?>, Map<Class<?>, Object>, Object>> averagingMergers() {
        return Map.of(
            PixelShift.class, MetadataMerger::pickClosestPixelShift,
            Ellipse.class, MetadataMerger::averageEllipses,
            ProcessParams.class, MetadataMerger::averageProcessParams
        );
    }

    private static Object pickClosestPixelShift(List<?> values, Map<Class<?>, Object> metadata) {
        return values.stream()
            .map(PixelShift.class::cast)
            .min(Comparator.comparingDouble(a -> Math.abs(a.pixelShift())))
            .orElse(null);
    }

    private static Object averageEllipses(List<?> values, Map<Class<?>, Object> metadata) {
        List<Point2D> samplePoints = new ArrayList<>();
        for (var value : values) {
            var ellipse = (Ellipse) value;
            for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 4) {
                samplePoints.add(ellipse.toCartesian(theta));
            }
        }
        try {
            var ellipseFit = new EllipseRegression(samplePoints);
            return ellipseFit.solve();
        } catch (Exception ex) {
            LOGGER.warn("Cannot estimate average ellipse from {} images", values.size());
            return null;
        }
    }

    private static Object averageProcessParams(List<?> values, Map<Class<?>, Object> metadata) {
        long avgDate = 0;
        int count = 0;
        ProcessParams lastParams = null;
        for (var value : values) {
            var pp = (ProcessParams) value;
            lastParams = pp;
            double date = pp.observationDetails().date().toEpochSecond();
            avgDate = (long) (avgDate + (date - avgDate) / (++count));
        }
        if (lastParams != null) {
            var newParams = lastParams.withObservationDetails(
                lastParams.observationDetails().withDate(
                    ZonedDateTime.ofInstant(Instant.ofEpochSecond(avgDate), ZoneId.of("UTC"))
                )
            );
            var solarParams = SolarParametersUtils.computeSolarParams(
                newParams.observationDetails().date().toLocalDateTime()
            );
            metadata.put(SolarParameters.class, solarParams);
            return newParams;
        }
        return null;
    }
}