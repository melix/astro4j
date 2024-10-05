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
import me.champeau.a4j.jsolex.processing.sun.FlatCorrection;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.List;
import java.util.Map;

public class ArtifificialFlatCorrector extends AbstractFunctionImpl {
    public ArtifificialFlatCorrector(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object performFlatCorrection(List<Object> arguments) {
        if (arguments.isEmpty() || arguments.size() > 4) {
            throw new IllegalArgumentException("flat_correction takes 1 to 4 arguments (image(s), [percentileLo], [percentileHi], [order])");
        }
        if (arguments.getFirst() instanceof List) {
            return expandToImageList("flat_correction", arguments, this::performFlatCorrection);
        }
        if (arguments.getFirst() instanceof ImageWrapper wrapper) {
            if (wrapper.unwrapToMemory() instanceof ImageWrapper32 mono) {
                double loPercentile = arguments.size()>= 2 ? doubleArg(arguments, 1) : FlatCorrection.DEFAULT_LO_PERCENTILE;
                double hiPercentile = arguments.size() >= 3 ? doubleArg(arguments, 2) : FlatCorrection.DEFAULT_HI_PERCENTILE;
                int order = arguments.size() >= 4 ? intArg(arguments, 3) : FlatCorrection.DEFAULT_ORDER;
                var corrector = new FlatCorrection(loPercentile, hiPercentile, order);
                return corrector.correctImage(mono);
            }
        }
        throw new IllegalArgumentException("flat_correction only supports mono images");
    }
}
