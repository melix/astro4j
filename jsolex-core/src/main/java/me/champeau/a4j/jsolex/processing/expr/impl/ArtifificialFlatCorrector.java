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
import me.champeau.a4j.jsolex.processing.sun.FlatCorrection;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.List;
import java.util.Map;

/**
 * Provides artificial flat field correction for images.
 */
public class ArtifificialFlatCorrector extends AbstractFunctionImpl {
    /**
     * Creates a new artificial flat corrector function.
     *
     * @param context the evaluation context
     * @param broadcaster the broadcaster for progress events
     */
    public ArtifificialFlatCorrector(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    /**
     * Performs flat field correction on an image or list of images.
     *
     * @param arguments the function arguments
     * @return the corrected image or list of images
     */
    public Object performFlatCorrection(Map<String ,Object> arguments) {
        BuiltinFunction.FLAT_CORRECTION.validateArgs(arguments);
        if (arguments.isEmpty() || arguments.size() > 4) {
            throw new IllegalArgumentException("flat_correction takes 1 to 4 arguments (image(s), [percentileLo], [percentileHi], [order])");
        }
        if (arguments.get("img") instanceof List) {
            return expandToImageList("flat_correction", "img", arguments, this::performFlatCorrection);
        }
        if (arguments.get("img") instanceof ImageWrapper wrapper) {
            if (wrapper.unwrapToMemory() instanceof ImageWrapper32 mono) {
                double loPercentile = doubleArg(arguments, "lo", FlatCorrection.DEFAULT_LO_PERCENTILE);
                double hiPercentile = doubleArg(arguments, "hi", FlatCorrection.DEFAULT_HI_PERCENTILE);
                int order = intArg(arguments, "order", FlatCorrection.DEFAULT_ORDER);
                var corrector = new FlatCorrection(loPercentile, hiPercentile, order);
                return corrector.correctImage(mono);
            }
        }
        throw new IllegalArgumentException("flat_correction only supports mono images");
    }
}
