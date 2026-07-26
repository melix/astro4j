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
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.MidtoneTransferFunctionAutostretchStrategy;
import me.champeau.a4j.jsolex.processing.stretching.MidtoneTransferFunctionStrategy;
import me.champeau.a4j.jsolex.processing.stretching.PercentileStretchStrategy;
import me.champeau.a4j.jsolex.processing.stretching.SigmoidStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

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
        return monoToMonoImageTransformer("mtf_autostretch", "img", arguments,
                image -> new MidtoneTransferFunctionAutostretchStrategy(shadowsClip, targetBg, statsMask(arguments, image)).stretch(image));
    }

    public Object percentileStretch(Map<String, Object> arguments) {
        BuiltinFunction.PERCENTILE_STRETCH.validateArgs(arguments);
        double lo = doubleArg(arguments, "lo", 0.1);
        double hi = doubleArg(arguments, "hi", 99.9);
        var clip = intArg(arguments, "clip", 1);
        var clipMode = switch (clip) {
            case 0 -> PercentileStretchStrategy.ClipMode.NONE;
            case 1 -> PercentileStretchStrategy.ClipMode.CLAMP;
            case 2 -> PercentileStretchStrategy.ClipMode.EXTEND;
            default -> throw new IllegalArgumentException("percentile_stretch clip must be 0 (no clipping), 1 (clip to black/white points) or 2 (extend the white point to the image maximum)");
        };
        return monoToMonoImageTransformer("percentile_stretch", "img", arguments,
                image -> new PercentileStretchStrategy(lo, hi, statsMask(arguments, image), clipMode).stretch(image));
    }

    public Object clamp(Map<String, Object> arguments) {
        BuiltinFunction.CLAMP.validateArgs(arguments);
        var lo = doubleArg(arguments, "lo", 0);
        var hi = doubleArg(arguments, "hi", Constants.MAX_PIXEL_VALUE);
        if (lo >= hi) {
            throw new IllegalArgumentException("clamp lo must be less than hi. Found: lo=" + lo + ", hi=" + hi);
        }
        return monoToMonoImageTransformer("clamp", "img", arguments,
                image -> new CutoffStretchingStrategy((float) lo, (float) hi).stretch(image));
    }

    public Object lift(Map<String, Object> arguments) {
        BuiltinFunction.LIFT.validateArgs(arguments);
        return monoToMonoImageTransformer("lift", "img", arguments, image -> {
            if (!(image instanceof ImageWrapper32 mono)) {
                throw new IllegalArgumentException("lift only supports mono images");
            }
            var data = mono.data();
            float min = 0;
            for (var line : data) {
                for (var v : line) {
                    if (v < min) {
                        min = v;
                    }
                }
            }
            if (min < 0) {
                var shift = -min;
                for (var line : data) {
                    for (int i = 0; i < line.length; i++) {
                        line[i] += shift;
                    }
                }
            }
        });
    }

    public Object sigmoidStretch(Map<String, Object> arguments) {
        BuiltinFunction.SIGMOID_STRETCH.validateArgs(arguments);
        double midpoint = doubleArg(arguments, "midpoint", 0.5);
        double steepness = doubleArg(arguments, "steepness", 10);
        return monoToMonoImageTransformer("sigmoid_stretch", "img", arguments, image -> new SigmoidStretchingStrategy(midpoint, steepness).stretch(image));
    }
}
