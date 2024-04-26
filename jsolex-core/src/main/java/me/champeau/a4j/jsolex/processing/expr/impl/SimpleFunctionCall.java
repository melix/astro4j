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
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.regression.EllipseRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.DoubleStream;

public class SimpleFunctionCall extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleFunctionCall.class);

    public SimpleFunctionCall(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object applyFunction(String name, List<Object> arguments, Function<DoubleStream, OptionalDouble> operator) {
        if (arguments.size() == 1) {
            if (arguments.get(0) instanceof List<?> list) {
                // unwrap
                //noinspection unchecked
                return applyFunction(name, (List<Object>) list, operator);
            }
        }
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' must have at least one argument");
        }
        var types = arguments.stream().map(Object::getClass).distinct().toList();
        if (types.size() > 1) {
            throw new IllegalArgumentException("'" + name + "' only works on arguments of the same type");
        }
        var type = types.get(0);
        if (Number.class.isAssignableFrom(type)) {
            return operator.apply(arguments.stream()
                    .map(Number.class::cast)
                    .mapToDouble(Number::doubleValue))
                .orElse(0);
        }
        if (ImageWrapper32.class.isAssignableFrom(type) || FileBackedImage.class.isAssignableFrom(type)) {
            var images = arguments
                .stream()
                .map(i -> i instanceof FileBackedImage fbi ? fbi.unwrapToMemory() : i)
                .map(ImageWrapper32.class::cast)
                .toList();
            var first = images.get(0);
            var width = first.width();
            var height = first.height();
            var length = first.data().length;
            if (images.stream().anyMatch(i -> i.data().length != length || i.width() != width || i.height() != height)) {
                throw new IllegalArgumentException(name + " function call failed: all images must have the same dimensions");
            }
            float[] result = new float[length];
            for (int i = 0; i < length; i++) {
                var idx = i;
                result[i] = (float) operator.apply(images.stream().mapToDouble(img -> img.data()[idx])).orElse(0);
            }
            Map<Class<?>, Object> metadata = new HashMap<>();
            CutoffStretchingStrategy.DEFAULT.stretch(new ImageWrapper32(width, height, result, metadata));
            List<Point2D> ellipseSamplePoints = new ArrayList<>();
            long avgDate = 0;
            int cpt = 0;
            for (ImageWrapper32 image : images) {
                image.findMetadata(Ellipse.class).ifPresent(ellipse -> {
                    // add sample points to the list in order to compute an "average" ellipse for all images
                    for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 4) {
                        ellipseSamplePoints.add(ellipse.toCartesian(theta));
                    }
                });
                var processParamsOptional = image.findMetadata(ProcessParams.class);
                if (processParamsOptional.isPresent()) {
                    var pp = processParamsOptional.get();
                    double date = pp.observationDetails().date().toEpochSecond();
                    avgDate = (long) (avgDate + (date - avgDate) / (++cpt));
                    metadata.put(ProcessParams.class, pp.withObservationDetails(
                        pp.observationDetails().withDate(
                            ZonedDateTime.ofInstant(Instant.ofEpochSecond(avgDate), ZoneId.of("UTC"))
                        )
                    ));
                }
            }
            if (!ellipseSamplePoints.isEmpty()) {
                try {
                    var ellipseFit = new EllipseRegression(ellipseSamplePoints);
                    metadata.put(Ellipse.class, ellipseFit.solve());
                } catch (Exception ex) {
                    LOGGER.warn("Cannot estimate average ellipse for many images");
                }
            }
            ProcessParams pp = (ProcessParams) metadata.get(ProcessParams.class);
            if (pp != null) {
                var solarParams = SolarParametersUtils.computeSolarParams(
                    pp.observationDetails().date().toLocalDateTime()
                );
                metadata.put(SolarParameters.class, solarParams);
            }
            return new ImageWrapper32(width, height, result, metadata);
        }
        throw new IllegalArgumentException("Unexpected argument type '" + type + "'");
    }
}
