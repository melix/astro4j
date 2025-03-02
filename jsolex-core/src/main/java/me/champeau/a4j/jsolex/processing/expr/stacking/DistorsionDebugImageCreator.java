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
package me.champeau.a4j.jsolex.processing.expr.stacking;

import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.expr.impl.Stacking;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.awt.Color;
import java.awt.GradientPaint;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;

public class DistorsionDebugImageCreator {
    private final ImageEmitter imageEmitter;
    private final Scaling scaling;
    private final ImageDraw imageDraw;

    public DistorsionDebugImageCreator(ImageEmitter imageEmitter, Scaling scaling, ImageDraw draw) {
        this.imageEmitter = imageEmitter;
        this.scaling = scaling;
        this.imageDraw = draw;
    }

    public void createDebugImage(ImageWrapper32 referenceImage,
                                 ImageWrapper32 stacked,
                                 DistorsionMap distorsion,
                                 ImageWrapper32 original,
                                 int tileSize,
                                 int increment,
                                 int index,
                                 float[][] dedistorted,
                                 Stacking.ReferenceSelection referenceSelection) {
        var width = stacked.width();
        var height = stacked.height();
        var separator = 20 + 10 * width / 100;
        var panelHeight = 2 * height + 3 * separator;
        var panelWidth = 4 * width + 5 * separator;
        var scale = Math.min(0.5, 2160d/panelHeight);
        var scaledWidth = (int) (panelWidth * scale);
        var scaledHeight = (int) (panelHeight * scale);
        imageEmitter.newColorImage(GeneratedImageKind.DEBUG, null, "displacement", "displacementXY_" + index, null, scaledWidth, scaledHeight, new HashMap<>(stacked.metadata()), () -> {
            var heatmap = new float[3][panelHeight][panelWidth];
            float[][] diffImage = new float[height][width];
            float minDiff = Float.POSITIVE_INFINITY;
            float maxDiff = Float.NEGATIVE_INFINITY;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    diffImage[y][x] = original.data()[y][x] - dedistorted[y][x];
                    minDiff = Math.min(minDiff, diffImage[y][x]);
                    maxDiff = Math.max(maxDiff, diffImage[y][x]);
                }
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    diffImage[y][x] += Math.abs(minDiff);
                }
            }
            double maxAmplitude = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < stacked.width(); x++) {
                    var displacement = distorsion.findDistorsion(x, y);
                    var dx = displacement.dx();
                    var dy = displacement.dy();
                    if (Double.isFinite(dx) && Double.isFinite(dy)) {
                        maxAmplitude = Math.max(maxAmplitude, Math.abs(dx));
                        maxAmplitude = Math.max(maxAmplitude, Math.abs(dy));
                    }
                }
            }
            for (float[][] data : heatmap) {
                for (float[] line : data) {
                    Arrays.fill(line, MAX_PIXEL_VALUE);
                }
            }
            for (int y = 0; y < height; y++) {
                var displayY = y + separator;
                for (int x = 0; x < stacked.width(); x++) {
                    var displacement = distorsion.findDistorsion(x, y);
                    var dx = displacement.dx();
                    var dy = displacement.dy();
                    var displayX = width + x + 2 * separator;
                    if (Double.isFinite(dx) && Double.isFinite(dy)) {
                        var halfADU = MAX_PIXEL_VALUE / 2;
                        var dxADU = (float) Math.abs(dx / maxAmplitude) * halfADU;
                        var dyADU = (float) Math.abs(dy / maxAmplitude) * halfADU;
                        if (dx < 0) {
                            heatmap[0][displayY][displayX] = halfADU + dxADU;
                            heatmap[1][displayY][displayX] = halfADU;
                            heatmap[2][displayY][displayX] = halfADU;
                        } else if (dx > 0) {
                            heatmap[0][displayY][displayX] = halfADU;
                            heatmap[1][displayY][displayX] = halfADU;
                            heatmap[2][displayY][displayX] = halfADU + dxADU;
                        }
                        displayX += width + separator;
                        if (dy < 0) {
                            heatmap[0][displayY][displayX] = halfADU + dyADU;
                            heatmap[1][displayY][displayX] = halfADU;
                            heatmap[2][displayY][displayX] = halfADU;
                        } else if (dy > 0) {
                            heatmap[0][displayY][displayX] = halfADU;
                            heatmap[1][displayY][displayX] = halfADU;
                            heatmap[2][displayY][displayX] = halfADU + dyADU;
                        }
                        displayX += width + separator;
                        var amplitude = (float) (MAX_PIXEL_VALUE * Math.sqrt(dx * dx + dy * dy) / maxAmplitude);
                        heatmap[0][displayY][displayX] = MAX_PIXEL_VALUE - amplitude;
                        heatmap[1][displayY][displayX] = MAX_PIXEL_VALUE - amplitude;
                        heatmap[2][displayY][displayX] = MAX_PIXEL_VALUE - amplitude;
                    }
                }
                displayY += height + separator;
                for (int x = 0; x < stacked.width(); x++) {
                    var displayX = x + separator;
                    var reference = referenceImage.data()[y][x];
                    heatmap[0][displayY][displayX] = reference;
                    heatmap[1][displayY][displayX] = reference;
                    heatmap[2][displayY][displayX] = reference;
                    displayX += width + separator;
                    var orig = original.data()[y][x];
                    heatmap[0][displayY][displayX] = orig;
                    heatmap[1][displayY][displayX] = orig;
                    heatmap[2][displayY][displayX] = orig;
                    displayX += width + separator;
                    var dedist = dedistorted[y][x];
                    heatmap[0][displayY][displayX] = dedist;
                    heatmap[1][displayY][displayX] = dedist;
                    heatmap[2][displayY][displayX] = dedist;
                    displayX += width + separator;
                    var diff = diffImage[y][x];
                    heatmap[0][displayY][displayX] = diff;
                    heatmap[1][displayY][displayX] = diff;
                    heatmap[2][displayY][displayX] = diff;
                }
            }
            double finalMaxAmplitude = maxAmplitude;
            RGBImage image = (RGBImage) scaling.relativeRescale(List.of(imageDraw.drawOnImage(new RGBImage(heatmap[0][0].length, heatmap[0].length, heatmap[0], heatmap[1], heatmap[2], MutableMap.of()), (g, img) -> {
                g.setColor(Color.BLACK);
                g.setFont(g.getFont().deriveFont(16f * (height / 384f)));
                g.drawString("Displacement X", 2 * separator + width + width / 6, height + 1.5f * separator);
                g.drawString("Displacement Y", 3 * separator + 2 * width + width / 6, height + 1.5f * separator);
                g.drawString("Amplitude", 4 * separator + 3 * width + width / 6, height + 1.5f * separator);
                g.drawString("Reference image", separator + width / 6, 2 * height + 2.5f * separator);
                g.drawString("Distorted image", 2 * separator + width + width / 6, 2 * height + 2.5f * separator);
                g.drawString("Corrected image", 3 * separator + 2 * width + width / 6, 2 * height + 2.5f * separator);
                g.drawString("Difference", 4 * separator + 3 * width + width / 6, 2 * height + 2.5f * separator);
                original.findMetadata(MetadataTable.class).ifPresent(metadata -> {
                    metadata.get(MetadataTable.FILE_NAME).ifPresent(name -> {
                        g.drawString("Source file : " + name, separator, separator);
                    });
                });
                g.drawString("Tile size: " + tileSize + "px", separator, separator + 60);
                g.drawString("Sample every: " + increment + "px", separator, separator + 120);
                g.drawString("Maximal amplitude: " + Math.round(finalMaxAmplitude) + "px", separator, separator + 180);
                g.drawString("Reference selection method: " + referenceSelection, separator, separator + 240);
                // draw displacement scale between the two displacement images
                g.setColor(Color.BLACK);
                var middleLegend = (int) (2 * width + 2.5 * separator);
                g.drawLine(middleLegend, separator, middleLegend, height + separator);
                g.setFont(g.getFont().deriveFont(12f * (height / 384f)));
                var gradient = new GradientPaint(middleLegend - 20, separator, new Color(255, 128, 128),
                    middleLegend - 20, separator + height / 2, new Color(128, 128, 128));
                g.setPaint(gradient);
                g.fillRect(middleLegend - 40, separator, 40, height / 2);
                gradient = new GradientPaint(middleLegend - 20, separator + height / 2, new Color(128, 128, 128),
                    middleLegend - 20, separator + height, new Color(128, 128, 255));
                g.setPaint(gradient);
                g.fillRect(middleLegend - 40, separator + height / 2, 40, height / 2);
                g.setPaint(Color.BLACK);
                for (int i = -10; i <= 10; i += 2) {
                    int y = height / 2 + separator + (i * height / 20);
                    g.drawLine(middleLegend - 20, y, middleLegend + 20, y);
                    double pixels = -i * finalMaxAmplitude / 10;
                    g.drawString(String.format(Locale.US, "%.2fpx", pixels), (int) (2 * width + 2.5 * separator) + 15, y);
                }

            }), scale, scale));

            return new float[][][]{image.r(), image.g(), image.b()};
        });
    }

}
