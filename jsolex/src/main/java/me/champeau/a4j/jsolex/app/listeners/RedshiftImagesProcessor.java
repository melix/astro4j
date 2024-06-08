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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.expr.FileOutput;
import me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast;
import me.champeau.a4j.jsolex.processing.expr.impl.Animate;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RequestedImages;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class RedshiftImagesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedshiftImagesProcessor.class);

    private final Map<Double, ImageWrapper> shiftImages;
    private final ProcessParams params;
    private final File serFile;
    private final Path outputDirectory;
    private final JSolExInterface owner;
    private final Broadcaster broadcaster;
    private final ImageEmitter imageEmitter;
    private final List<RedshiftArea> redshifts;
    private final DoubleUnaryOperator polynomial;
    private final float[] averageImage;

    public RedshiftImagesProcessor(Map<Double, ImageWrapper> shiftImages,
                                   ProcessParams params,
                                   File serFile,
                                   Path outputDirectory,
                                   JSolExInterface owner,
                                   Broadcaster broadcaster,
                                   ImageEmitter imageEmitter,
                                   List<RedshiftArea> redshifts,
                                   DoubleUnaryOperator polynomial,
                                   float[] averageImage) {
        this.shiftImages = shiftImages;
        this.params = params;
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
        this.owner = owner;
        this.broadcaster = broadcaster;
        this.imageEmitter = imageEmitter;
        this.redshifts = redshifts;
        this.polynomial = polynomial;
        this.averageImage = averageImage;
    }

    public Optional<Double> getSunRadius() {
        return shiftImages.values().stream()
            .map(i -> i.findMetadata(Ellipse.class).map(e -> (e.semiAxis().a() + e.semiAxis().b()) / 2))
            .findFirst()
            .orElse(Optional.empty());
    }

    public List<RedshiftArea> getRedshifts() {
        return redshifts;
    }

    public void produceImages(RedshiftCreatorKind kind, int boxSize, int margin, boolean useFullRangePanels) {
        var requiredShifts = createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0));
        var missingShifts = requiredShifts.stream().filter(d -> !shiftImages.containsKey(d)).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        broadcaster.broadcast(ProgressEvent.of(0, "Producing redshift animations and panels"));
        double progress = 0;
        var adjustedRedshifts = shiftImages.get(0d)
            .findMetadata(Redshifts.class)
            .map(Redshifts::redshifts)
            .map(List::reversed)
            .orElse(List.of());
        for (var redshift : adjustedRedshifts) {
            broadcaster.broadcast(ProgressEvent.of(progress / redshifts.size(), "Producing images for redshift " + redshift));
            produceImagesForRedshift(redshift, kind, boxSize, margin, useFullRangePanels);
            progress++;
        }
        broadcaster.broadcast(ProgressEvent.of(1, "Producing redshift animations and panels done"));
    }

    private LinkedHashSet<Double> createRange(int margin, int pixelShift) {
        var range = pixelShift + margin;
        var requiredShifts = new LinkedHashSet<Double>();
        for (double i = -range; i < range; i++) {
            requiredShifts.add(i);
            requiredShifts.add(i + .25);
            requiredShifts.add(i + .5);
            requiredShifts.add(i + .75);
        }
        requiredShifts.add((double) range);
        return requiredShifts;
    }

    private void produceImagesForRedshift(RedshiftArea redshift, RedshiftCreatorKind kind, int boxSize, int margin, boolean useFullRangePanels) {
        var centerX = redshift.maxX();
        var centerY = redshift.maxY();
        // grow x1/x2/y1/y2 so that the area is centered and fits the box size
        var dx = boxSize / 2;
        var dy = boxSize / 2;
        var x1 = Math.max(0, centerX - dx);
        var y1 = Math.max(0, centerY - dy);
        var crop = new Crop(Map.of(), broadcaster);
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var animate = new Animate(Map.of(), broadcaster);
        var initialImages = createRange(margin, redshift.pixelShift()).stream().map(shiftImages::get).toList();
        var constrastAdjusted = contrast.autoContrast(List.of(initialImages, params.autoStretchParams().gamma()));
        var cropped = crop.crop(List.of(constrastAdjusted, x1, y1, boxSize, boxSize));
        if (kind == RedshiftCreatorKind.ANIMATION || kind == RedshiftCreatorKind.ALL) {
            generateAnim(redshift, animate, cropped);
        }
        if (kind == RedshiftCreatorKind.PANEL || kind == RedshiftCreatorKind.ALL) {
            generatePanel(redshift, (List<ImageWrapper>) cropped, boxSize, crop, useFullRangePanels);
        }
    }

    private void generateAnim(RedshiftArea redshift, Animate animate, Object cropped) {
        var anim = (FileOutput) animate.createAnimation(List.of(cropped, 25));
        imageEmitter.newGenericFile(
            GeneratedImageKind.REDSHIFT,
            String.format("Panel %s (%.2f km/s)", redshift.id(), redshift.kmPerSec()),
            "redshift-" + redshift.id(),
            anim.file());
    }

    private void generatePanel(RedshiftArea redshift, List<ImageWrapper> cropped, int boxSize, Crop crop, boolean useFullRangePanels) {
        var snapshots = cropped;
        if (boxSize <= 128) {
            // this is a bit small to display the text, so we're going to scale by a factor of 2
            var scaling = new Scaling(Map.of(), broadcaster, crop);
            snapshots = (List<ImageWrapper>) scaling.relativeRescale(List.of(snapshots, 2, 2));
            boxSize *= 2;
        }
        // snaphots are at pixel shifts n, n+0.25, n+0.5, n+0.75
        // but for a panel we don't need such a resolution, we're only going to
        // keep round pixel shifts
        var snapshotsToDisplay = IntStream.range(0, snapshots.size()).filter(i -> i % 4 == 0).mapToObj(snapshots::get).collect(Collectors.toList());
        if (!useFullRangePanels) {
            // then we're only going to keep the snapshots which pixel shift has the same sign as the red/blueshift
            var sign = Math.signum(redshift.relPixelShift());
            snapshotsToDisplay.removeIf(s -> {
                var shift = s.findMetadata(PixelShift.class);
                if (shift.isPresent()) {
                    var signum = Math.signum(shift.get().pixelShift());
                    return signum != 0 && signum != sign;
                }
                return true;
            });
            if (sign == -1) {
                Collections.reverse(snapshotsToDisplay);
            }
        }
        int cols = (int) Math.ceil(Math.sqrt(snapshotsToDisplay.size()));
        int rows = (int) Math.ceil((double) snapshotsToDisplay.size() / cols);
        int panelWidth = cols * boxSize;
        int panelHeight = rows * boxSize;
        var lambda0 = params.spectrumParams().ray().wavelength();
        var instrument = params.observationDetails().instrument();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersionNanosPerPixel(instrument, lambda0, params.observationDetails().pixelSize() * params.observationDetails().binning());
        int finalSnapHeight = boxSize;
        int finalSnapWidth = boxSize;
        imageEmitter.newColorImage(
            GeneratedImageKind.REDSHIFT,
            String.format("Panel %s (%.2f km/s)", redshift.id(), redshift.kmPerSec()),
            "redshift-" + redshift.id(),
            panelWidth,
            panelHeight,
            Map.of(),
            () -> {
                var rgb = new float[3][panelWidth * panelHeight];
                var r = rgb[0];
                var g = rgb[1];
                var b = rgb[2];

                for (int i = 0; i < snapshotsToDisplay.size(); i++) {
                    var snap = snapshotsToDisplay.get(i);
                    var mono = (ImageWrapper32) snap.unwrapToMemory();
                    var data = mono.data();
                    var row = i / cols;
                    var col = i % cols;
                    var offset = row * finalSnapHeight * panelWidth + col * finalSnapWidth;
                    var pixelShift = snap.findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0d);
                    var angstroms = 10 * pixelShift * dispersion;
                    var legend = String.format(Locale.US, "%.2fÅ (%.2f km/s)", angstroms, Math.abs(PhenomenaDetector.speedOf(pixelShift, dispersion, lambda0)));
                    // draw legend on a dummy image
                    var legendImage = createLegendImage(finalSnapWidth, finalSnapHeight, legend);
                    var legendOverlay = legendImage.getData();
                    var legendPixel = new int[1];
                    for (int y = 0; y < finalSnapHeight; y++) {
                        for (int x = 0; x < finalSnapWidth; x++) {
                            var idx = y * finalSnapWidth + x;
                            var panelIdx = offset + y * panelWidth + x;
                            var gray = data[idx];
                            legendOverlay.getPixel(x, y, legendPixel);
                            if (legendPixel[0] > 0) {
                                r[panelIdx] = 60395;
                                g[panelIdx] = 53970;
                                b[panelIdx] = 13364;
                            } else {
                                r[panelIdx] = gray;
                                g[panelIdx] = gray;
                                b[panelIdx] = gray;
                            }
                        }
                    }
                }

                return rgb;

            });
    }

    private static BufferedImage createLegendImage(int snapWidth, int snapHeight, String legend) {
        var legendImage = new BufferedImage(snapWidth, snapHeight, BufferedImage.TYPE_BYTE_GRAY);
        var graphics = legendImage.createGraphics();
        var fontSize = snapWidth / 16f;
        var font = graphics.getFont().deriveFont(fontSize).deriveFont(Font.BOLD);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.drawString(legend, fontSize, snapHeight - 2 * fontSize / 3);
        return legendImage;
    }

    private void restartProcessForMissingShifts(Set<Double> missingShifts) {
        LOGGER.warn(message("restarting.process.missing.shifts"), missingShifts.stream().map(d -> String.format("%.2f", d)).toList());
        // restart processing to include missing images
        var tmpParams = params.withRequestedImages(
            new RequestedImages(Set.of(GeneratedImageKind.GEOMETRY_CORRECTED),
                Stream.concat(params.requestedImages().pixelShifts().stream(), missingShifts.stream()).toList(),
                missingShifts,
                Set.of(),
                ImageMathParams.NONE,
                false)
        ).withExtraParams(params.extraParams().withAutosave(false));
        var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, tmpParams, LocalDateTime.now(), false);
        solexVideoProcessor.setRedshifts(new Redshifts(redshifts));
        solexVideoProcessor.setPolynomial(polynomial);
        solexVideoProcessor.setAverageImage(averageImage);
        solexVideoProcessor.addEventListener(new ProcessingEventListener() {
            @Override
            public void onProcessingDone(ProcessingDoneEvent e) {
                shiftImages.putAll(e.getPayload().shiftImages());
            }

            @Override
            public void onProgress(ProgressEvent e) {
                BatchOperations.submitOneOfAKind("progress", () -> owner.updateProgress(e.getPayload().progress(), e.getPayload().task()));
            }
        });
        solexVideoProcessor.process();
    }

    public enum RedshiftCreatorKind {
        ANIMATION,
        PANEL,
        ALL
    }
}
