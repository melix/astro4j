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
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowStep;
import me.champeau.a4j.math.regression.Ellipse;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * A utility class for drawing debug images.
 */
public class DebugImageHelper {
    private DebugImageHelper() {

    }

    public static void maybeDisplayTiltImage(ProcessParams processParams, ImageEmitter processedImagesEmitter, ImageWrapper32 bandingFixed, int y0, int y1, int x0, int x1, int width, int height, Ellipse ellipse) {
        if (processParams.debugParams().generateDebugImages()) {
            var newData = new float[bandingFixed.data().length];
            var original = bandingFixed.data();
            System.arraycopy(original, 0, newData, 0, newData.length);
            LinearStrechingStrategy.DEFAULT.stretch(newData);
            plot(x0, y0, width, height, newData);
            plot(x1, y1, width, height, newData);
            for (int y = y0; y <= y1; y++) {
                plot(x0, y, width, height, newData);
            }
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                plot(x, y1, width, height, newData);
            }
            drawEllipse(width, height, ellipse, newData);
            produceOverlayImage(processedImagesEmitter, width, height, newData, original);
        }
    }

    private static void produceOverlayImage(ImageEmitter processedImagesEmitter, int width, int height, float[] newData, float[] original) {
        processedImagesEmitter.newColorImage(WorkflowStep.GEOMETRY_CORRECTION, message("tilt.detection"), "tilt", LinearStrechingStrategy.DEFAULT, width, height, () -> {
            float[][] rgb = new float[3][];
            rgb[0] = newData;
            rgb[1] = original;
            rgb[2] = original;
            return rgb;
        });
    }

    private static void drawEllipse(int width, int height, Ellipse ellipse, float[] newData) {
        for (int y = 0; y < height; y++) {
            var mx = -1;
            for (int x = 0; x < width; x++) {
                if (mx == -1 && ellipse.isWithin(x, y)) {
                    plot(x, y, width, height, newData);
                    mx = x;
                } else if (mx >= 0 && !ellipse.isWithin(x, y)) {
                    plot(x - 1, y, width, height, newData);
                    break;
                }
            }
        }
    }

    public static void plot(int x, int y, int width, int height, float[] data) {
        for (int yy = Math.max(0, y - 1); yy < Math.min(height, y + 2); yy++) {
            for (int xx = Math.max(0, x - 1); xx < Math.min(width, x + 2); xx++) {
                data[xx + yy * width] = Constants.MAX_PIXEL_VALUE;
            }
        }
    }
}
