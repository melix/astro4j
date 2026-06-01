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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.scene.image.Image;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.align.AngularCorrelation;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Map;

/**
 * Compares a solar image with a GONG reference image to estimate the
 * horizontal/vertical flips and the rotation angle that best align the two.
 * The comparison relies on {@link AngularCorrelation} performed in polar space,
 * which is rotation-aware: each of the four flip combinations is scored by
 * {@code confidence × alignedNCC} and the best one is kept. The result is a
 * best-effort estimate and may be inaccurate on noisy or featureless disks.
 */
public final class GongOrientationAnalyzer {

    public record FlipDetection(boolean horizontalFlip, boolean verticalFlip, double angleDegrees, double score) {
        public boolean hasFlip() {
            return horizontalFlip || verticalFlip;
        }
    }

    private GongOrientationAnalyzer() {
    }

    public static FlipDetection detect(ImageWrapper userImage, double pAngle, Image gongImage, boolean searchFlips) {
        var scaling = new Scaling(Map.of(), Broadcaster.NO_OP, new Crop(Map.of(), Broadcaster.NO_OP));
        var gongGray = convertGongToGrayscale(gongImage);
        var gongWidth = (int) gongImage.getWidth();
        var gongHeight = (int) gongImage.getHeight();
        var gongCx = gongWidth / 2.0;
        var gongCy = gongHeight / 2.0;
        var gongRadius = Math.min(gongWidth, gongHeight) * 0.45;
        var targetRadius = (int) Math.round(gongRadius);

        // When searching flips: try all 4 combinations and score by
        // confidence × alignedNCC. When disabled: only try NONE so
        // the search is limited to rotation for an already-oriented image.
        var flips = searchFlips
            ? new boolean[][]{{false, false}, {true, false}, {false, true}, {true, true}}
            : new boolean[][]{{false, false}};
        var bestScore = -1.0;
        var bestAngle = 0.0;
        var bestHFlip = false;
        var bestVFlip = false;

        for (var flip : flips) {
            var candidate = userImage.copy();
            if (flip[0]) {
                candidate = Corrector.rotate(candidate, Math.PI, false);
                candidate = Corrector.verticalFlip(candidate);
            }
            if (flip[1]) {
                candidate = Corrector.verticalFlip(candidate);
            }
            candidate = Corrector.rotate(candidate, pAngle, false);
            var rescaled = scaling.rescaleToRadius(candidate, targetRadius, gongWidth, gongHeight);
            var mono = toMono(rescaled);
            if (mono == null) {
                continue;
            }
            var ellipse = mono.findMetadata(Ellipse.class).orElse(null);
            double cx;
            double cy;
            double radius;
            if (ellipse != null) {
                cx = ellipse.center().a();
                cy = ellipse.center().b();
                radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2.0;
            } else {
                cx = gongCx;
                cy = gongCy;
                radius = targetRadius;
            }

            var result = AngularCorrelation.detectAngularOffset(
                mono.data(), cx, cy, radius,
                gongGray, gongCx, gongCy, gongRadius
            );

            var score = result.confidence() * result.alignedNCC();
            if (score > bestScore) {
                bestScore = score;
                bestAngle = result.angleDegrees();
                bestHFlip = flip[0];
                bestVFlip = flip[1];
            }
        }

        // Normalize: if |angle| > 90°, convert to equivalent flip
        // with smaller angle (BOTH±180° ≡ NONE, H±180° ≡ V).
        // Skip when flips were not searched — the caller wants rotation only.
        if (searchFlips && Math.abs(bestAngle) > 90) {
            bestHFlip = !bestHFlip;
            bestVFlip = !bestVFlip;
            bestAngle = bestAngle > 0 ? bestAngle - 180 : bestAngle + 180;
        }
        return new FlipDetection(bestHFlip, bestVFlip, bestAngle, bestScore);
    }

    private static ImageWrapper32 toMono(ImageWrapper image) {
        if (image instanceof ImageWrapper32 mono) {
            return mono;
        } else if (image instanceof RGBImage rgb) {
            var mono = rgb.toMono();
            rgb.findMetadata(Ellipse.class).ifPresent(e -> mono.metadata().put(Ellipse.class, e));
            return mono;
        }
        return null;
    }

    private static float[][] convertGongToGrayscale(Image gongImage) {
        var width = (int) gongImage.getWidth();
        var height = (int) gongImage.getHeight();
        var reader = gongImage.getPixelReader();
        var gray = new float[height][width];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var color = reader.getColor(x, y);
                gray[y][x] = (float) ((0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) * 65535);
            }
        }
        return gray;
    }
}
