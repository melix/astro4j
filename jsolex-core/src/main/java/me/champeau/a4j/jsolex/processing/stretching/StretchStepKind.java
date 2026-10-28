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
package me.champeau.a4j.jsolex.processing.stretching;

import java.util.List;

import static me.champeau.a4j.jsolex.processing.stretching.StretchParameter.choice;
import static me.champeau.a4j.jsolex.processing.stretching.StretchParameter.range;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

/**
 * The kinds of stretching steps which can be applied to an image, with the
 * description of their parameters.
 */
public enum StretchStepKind {
    LINEAR(false,
            range("low", 0, 255, 0, 1),
            range("high", 0, 255, 255, 1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            var low = to16Bit(step.value("low"));
            var high = to16Bit(step.value("high"));
            return new StretchingChain(RangeExpansionStrategy.DEFAULT, new ContrastAdjustmentStrategy(low, high), RangeExpansionStrategy.DEFAULT);
        }

        @Override
        String describe(StretchStep step) {
            return "Linear (low: " + (int) step.value("low") + ", high: " + (int) step.value("high") + ")";
        }
    },
    CURVE(false,
            range("in", 0, 255, 128, 1),
            range("out", 0, 255, 128, 1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            return new CurveTransformStrategy(256 * step.value("in"), 256 * step.value("out"));
        }

        @Override
        String describe(StretchStep step) {
            return "Curve (in: " + (int) step.value("in") + ", out: " + (int) step.value("out") + ")";
        }
    },
    MIDTONES(false,
            range("black.point", 0, 254, 0, 1),
            range("midtones", 0.01, 0.99, 0.5, 0.01),
            range("white.point", 1, 255, 255, 1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            var blackPoint = step.value("black.point");
            var whitePoint = Math.max(blackPoint + 1, step.value("white.point"));
            return new StretchingChain(RangeExpansionStrategy.DEFAULT, new MidtoneTransferFunctionStrategy(blackPoint, step.value("midtones"), whitePoint));
        }

        @Override
        String describe(StretchStep step) {
            return "MTF (shadows: " + (int) step.value("black.point") + ", midtones: " + step.value("midtones") + ", highlights: " + (int) step.value("white.point") + ")";
        }
    },
    ARCSINH(false,
            range("black.point", 0, 255, 0, 1),
            range("stretch", 0.1, 50, 2, 0.1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            var stretch = step.value("stretch");
            return new ArcsinhStretchingStrategy((float) (256 * step.value("black.point")), (float) stretch, stretch);
        }

        @Override
        String describe(StretchStep step) {
            return "Arcsinh (black point: " + (int) step.value("black.point") + ", stretch: " + step.value("stretch") + ")";
        }
    },
    AUTOSTRETCH(true,
            range("gamma", 1, 5, AutohistogramStrategy.DEFAULT_GAMMA, 0.05),
            range("background.threshold", 0.01, 1, AutohistogramStrategy.DEFAULT_BACKGROUND_THRESHOLD, 0.01),
            range("protus.stretch", 0, 15, AutohistogramStrategy.DEFAULT_PROM_STRETCH, 0.1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            return new AutohistogramStrategy(step.value("gamma"), true, step.value("background.threshold"), step.value("protus.stretch"));
        }

        @Override
        String describe(StretchStep step) {
            return "AutoStretch (gamma: " + step.value("gamma") + ")";
        }
    },
    CLAHE(false,
            choice("tile.size", ClaheStrategy.DEFAULT_TILE_SIZE, 8, 16, 32, 64, 128, 256, 512, 1024),
            choice("bins", ClaheStrategy.DEFAULT_BINS, 32, 64, 128, 256, 512, 1024),
            range("clip", 0.1, 10, ClaheStrategy.DEFAULT_CLIP, 0.1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            var tileSize = (int) step.value("tile.size");
            var bins = ClaheStrategy.effectiveBins(tileSize, (int) step.value("bins"));
            return new ClaheStrategy(tileSize, bins, step.value("clip"));
        }

        @Override
        String describe(StretchStep step) {
            return "CLAHE (tile size: " + (int) step.value("tile.size") + ", clip limit: " + step.value("clip") + ", bins: " + (int) step.value("bins") + ")";
        }
    },
    CLAHE2(false,
            range("clip", 0.1, 10, AdaptativeClaheStrategy.DEFAULT_CLIP, 0.1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            return new AdaptativeClaheStrategy(step.value("clip"));
        }

        @Override
        String describe(StretchStep step) {
            return "CLAHE2 (clip limit: " + step.value("clip") + ")";
        }
    },
    SHADOWS_HIGHLIGHTS(false,
            range("shadows", -ShadowsHighlightsStrategy.MAX_AMOUNT, ShadowsHighlightsStrategy.MAX_AMOUNT, ShadowsHighlightsStrategy.DEFAULT_SHADOWS, 1),
            range("highlights", -ShadowsHighlightsStrategy.MAX_AMOUNT, ShadowsHighlightsStrategy.MAX_AMOUNT, ShadowsHighlightsStrategy.DEFAULT_HIGHLIGHTS, 1),
            range("radius", 2, 200, ShadowsHighlightsStrategy.DEFAULT_RADIUS, 1)) {
        @Override
        StretchingStrategy toStrategy(StretchStep step) {
            return new StretchingChain(RangeExpansionStrategy.DEFAULT, new ShadowsHighlightsStrategy(step.value("shadows"), step.value("highlights"), (int) step.value("radius")));
        }

        @Override
        String describe(StretchStep step) {
            return "Shadows/highlights (shadows: " + (int) step.value("shadows") + ", highlights: " + (int) step.value("highlights") + ", radius: " + (int) step.value("radius") + ")";
        }
    };

    private final boolean requiresSolarDisk;
    private final List<StretchParameter> parameters;

    StretchStepKind(boolean requiresSolarDisk, StretchParameter... parameters) {
        this.requiresSolarDisk = requiresSolarDisk;
        this.parameters = List.of(parameters);
    }

    abstract StretchingStrategy toStrategy(StretchStep step);

    abstract String describe(StretchStep step);

    /**
     * Returns true if steps of this kind need the solar disk to be detected in the image.
     *
     * @return true if the solar disk is required
     */
    public boolean requiresSolarDisk() {
        return requiresSolarDisk;
    }

    /**
     * Returns the parameters of steps of this kind.
     *
     * @return the parameters
     */
    public List<StretchParameter> parameters() {
        return parameters;
    }

    int indexOf(String name) {
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown parameter " + name + " for " + this);
    }

    private static float to16Bit(double value8Bit) {
        return value8Bit >= 255 ? MAX_PIXEL_VALUE : (float) (256 * value8Bit);
    }
}
