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
import me.champeau.a4j.jsolex.processing.stretching.RangeExpansionStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * A utility class for drawing debug images.
 */
public class DebugImageHelper {
    private DebugImageHelper() {

    }

    public static void maybeDisplayTiltImage(ProcessParams processParams,
                                             ImageEmitter processedImagesEmitter,
                                             ImageWrapper32 bandingFixed,
                                             Ellipse ellipse,
                                             Point2D... interestPoints) {
        if (processParams.extraParams().generateDebugImages()) {
            var copy = bandingFixed.copy();
            RangeExpansionStrategy.DEFAULT.stretch(copy);
            var width = bandingFixed.width();
            var height = bandingFixed.height();
            drawEllipse(width, height, ellipse, copy.data());
            for (Point2D point : interestPoints) {
                plot(point, width, height, copy.data(), 12);
            }
            produceOverlayImage(processedImagesEmitter, width, height, copy.data(), bandingFixed.data(), bandingFixed.metadata());
        }
    }

    private static void produceOverlayImage(ImageEmitter processedImagesEmitter, int width, int height, float[][] newData, float[][] original, Map<Class<?>, Object> metadata) {
        processedImagesEmitter.newColorImage(GeneratedImageKind.DEBUG, null, message("tilt.detection"), "tilt", width, height, metadata, () -> {
            float[][][] rgb = new float[3][][];
            rgb[0] = newData;
            rgb[1] = original;
            rgb[2] = original;
            return rgb;
        });
    }

    public static void drawEllipse(int width, int height, Ellipse ellipse, float[][] buffer) {
        var center = ellipse.center();
        var cx = (int) center.a();
        var cy = (int) center.b();
        for (double alpha = -Math.PI; alpha <= Math.PI; alpha += 0.004d) {
            var p = ellipse.toCartesian(alpha);
            plot(p, width, height, buffer, (int) (4 + alpha / Math.PI));
        }
        for (int x = Math.max(0, cx - 10); x < Math.min(width, cx + 11); x++) {
            plot(x, cy, width, height, buffer);
        }
        for (int y = Math.max(0, cy - 10); y < Math.min(width, cy + 11); y++) {
            plot(cx, y, width, height, buffer);
        }
        drawCircle(cx, cy, (int) ellipse.semiAxis().a(), width, height, buffer);
        drawCircle(cx, cy, (int) ellipse.semiAxis().b(), width, height, buffer);
        var theta = ellipse.rotationAngle();
        for (double i = -ellipse.semiAxis().a(); i < ellipse.semiAxis().a(); i += .2) {
            var x = cx + i * Math.cos(theta);
            var y = cy + i * Math.sin(theta);
            plot((int) x, (int) y, width, height, buffer);
        }
        for (double i = -ellipse.semiAxis().b(); i < ellipse.semiAxis().b(); i += .2) {
            var x = cx + i * Math.cos(theta - Math.PI / 2);
            var y = cy + i * Math.sin(theta - Math.PI / 2);
            plot((int) x, (int) y, width, height, buffer);
        }
        var vertices = ellipse.findVertices();
        for (Point2D vertex : vertices) {
            plot((int) vertex.x(), (int) vertex.y(), width, height, buffer, 8);
        }
    }

    public static void drawCircle(int cx, int cy, int radius, int width, int height, float[][] data) {
        for (double angle = 0; angle <= 2 * Math.PI; angle += 0.01) {
            var x = cx + radius * Math.cos(angle);
            var y = cy + radius * Math.sin(angle);
            plot((int) x, (int) y, width, height, data);
        }
    }

    public static void plot(int x, int y, int width, int height, float[][] data) {
        plot(x, y, width, height, data, 1);
    }

    public static void plot(Point2D p, int width, int height, float[][] data, int size) {
        plot((int) p.x(), (int) p.y(), width, height, data, size);
    }

    public static void plot(int x, int y, int width, int height, float[][] data, int size) {
        for (int yy = Math.max(0, y - size); yy < Math.min(height, y + size + 1); yy++) {
            for (int xx = Math.max(0, x - size); xx < Math.min(width, x + size + 1); xx++) {
                data[yy][xx] = Constants.MAX_PIXEL_VALUE;
            }
        }
    }
}
