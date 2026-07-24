/*
 * Copyright 2026 the original author or authors.
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
import me.champeau.a4j.jsolex.processing.util.AnnulusMask;
import me.champeau.a4j.jsolex.processing.util.ImageMask;
import me.champeau.a4j.jsolex.processing.util.RangeMask;

import java.util.Map;

public class Masking extends AbstractFunctionImpl {

    public Masking(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object annulusMask(Map<String, Object> arguments) {
        BuiltinFunction.ANNULUS_MASK.validateArgs(arguments);
        return new AnnulusMask(
                doubleArg(arguments, "rmin", 0),
                doubleArg(arguments, "rmax", Double.POSITIVE_INFINITY)
        );
    }

    public Object rangeMask(Map<String, Object> arguments) {
        BuiltinFunction.RANGE_MASK.validateArgs(arguments);
        return new RangeMask(
                doubleArg(arguments, "lo", 0),
                doubleArg(arguments, "hi", Double.POSITIVE_INFINITY)
        );
    }

    public Object invertMask(Map<String, Object> arguments) {
        BuiltinFunction.INVERT_MASK.validateArgs(arguments);
        if (arguments.get("mask") instanceof ImageMask mask) {
            return ImageMask.inverted(mask);
        }
        throw new IllegalArgumentException("invert_mask requires a mask, use annulus_mask or range_mask to create one");
    }
}
