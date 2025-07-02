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
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CurveTransformStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.MidtoneTransferFunctionStrategy;
import me.champeau.a4j.jsolex.processing.stretching.MidtoneTransferFunctionAutostretchStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.Constants;

import java.util.Map;

public class Stretching extends AbstractFunctionImpl {

    public Stretching(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object asinhStretch(Map<String, Object> arguments) {
        BuiltinFunction.ASINH_STRETCH.validateArgs(arguments);
        float blackpoint = floatArg(arguments, "bp", 0);
        float stretch = floatArg(arguments, "strech", 1);
        return monoToMonoImageTransformer("asinh_stretch", "img", arguments, image -> new ArcsinhStretchingStrategy(blackpoint, stretch, stretch).stretch(image));
    }

    public Object linearStretch(Map<String, Object> arguments) {
        BuiltinFunction.LINEAR_STRETCH.validateArgs(arguments);
        float lo = Math.clamp(floatArg(arguments, "lo", 0), 0, Constants.MAX_PIXEL_VALUE);
        float hi = Math.clamp(floatArg(arguments, "hi", Constants.MAX_PIXEL_VALUE), 0, Constants.MAX_PIXEL_VALUE);
        return monoToMonoImageTransformer("linear_stretch", "img", arguments, image -> new LinearStrechingStrategy(lo, hi).stretch(image));
    }

    public Object curveTransform(Map<String, Object> arguments) {
        BuiltinFunction.CURVE_TRANSFORM.validateArgs(arguments);
        int in = intArg(arguments, "in", 0);
        int out = intArg(arguments, "out", 255);
        if (in < 0 || in > 255) {
            throw new IllegalArgumentException("curve_transform in must be between 0 and 255");
        }
        if (out < 0 || out > 255) {
            throw new IllegalArgumentException("curve_transform out must be between 0 and 255");
        }
        int protectLo = intArg(arguments, "protectLo", 0);
        int protectHi = intArg(arguments, "protectHi", 255);
        return monoToMonoImageTransformer("curve_transform", "img", arguments, image -> new CurveTransformStrategy(in << 8, out << 8, protectLo << 8, protectHi << 8).stretch(image));
    }

    public Object mtf(Map<String, Object> arguments) {
        BuiltinFunction.MTF.validateArgs(arguments);
        double shadows = doubleArg(arguments, "shadows", 0);
        double midtones = doubleArg(arguments, "midtones", 1.0);
        double highlights = doubleArg(arguments, "highlights", 255);
        if (shadows < 0 || shadows > 255) {
            throw new IllegalArgumentException("mtf shadows must be between 0 and 255");
        }
        if (highlights < 0 || highlights > 255) {
            throw new IllegalArgumentException("mtf highlights must be between 0 and 255");
        }
        return monoToMonoImageTransformer("mtf", "img", arguments, image -> new MidtoneTransferFunctionStrategy(shadows, midtones, highlights).stretch(image));
    }

    public Object mtfAutostretch(Map<String, Object> arguments) {
        BuiltinFunction.MTF_AUTOSTRETCH.validateArgs(arguments);
        double shadowsClip = doubleArg(arguments, "shadows_clip", MidtoneTransferFunctionAutostretchStrategy.DEFAULT_SHADOWS_CLIP);
        double targetBg = doubleArg(arguments, "target_bg", MidtoneTransferFunctionAutostretchStrategy.DEFAULT_TARGET_BG);
        return monoToMonoImageTransformer("mtf_autostretch", "img", arguments, image -> new MidtoneTransferFunctionAutostretchStrategy(shadowsClip, targetBg).stretch(image));
    }
}
