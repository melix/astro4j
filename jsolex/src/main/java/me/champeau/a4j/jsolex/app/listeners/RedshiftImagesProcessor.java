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

import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.FileOutput;
import me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast;
import me.champeau.a4j.jsolex.processing.expr.impl.Animate;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
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
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

public class RedshiftImagesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedshiftImagesProcessor.class);
    private final static int[] YELLOW = {255, 255, 0};
    private static final int SAMPLING = 4;
    private static final int BYTES_IN_FLOAT = 4;
    private static final int TMP_IMAGES_COUNT = 4;
    private static final int MAX_PANEL_SIZE = 7680;

    private final Map<Double, ImageWrapper> shiftImages;
    private final ProcessParams params;
    private final File serFile;
    private final Path outputDirectory;
    private final JSolExInterface owner;
    private final Broadcaster broadcaster;
    private final ImageEmitter imageEmitter;
    private final List<RedshiftArea> redshifts;
    private final DoubleUnaryOperator polynomial;
    private final float[][] averageImage;
    private final ProgressOperation operation;
    private final int imageWidth;
    private final int imageHeight;

    public RedshiftImagesProcessor(Map<Double, ImageWrapper> shiftImages,
                                   ProcessParams params,
                                   File serFile,
                                   Path outputDirectory,
                                   JSolExInterface owner,
                                   Broadcaster broadcaster,
                                   ImageEmitter imageEmitter,
                                   List<RedshiftArea> redshifts,
                                   DoubleUnaryOperator polynomial,
                                   float[][] averageImage,
                                   ProgressOperation operation) {
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
        this.operation = operation;
        var image = shiftImages.values().stream().findFirst();
        if (image.isPresent()) {
            imageWidth = image.get().width();
            imageHeight = image.get().height();
        } else {
            imageWidth = 0;
            imageHeight = 0;
        }
    }

    public RedshiftImagesProcessor withRedshifts(List<RedshiftArea> redshifts) {
        return new RedshiftImagesProcessor(shiftImages, params, serFile, outputDirectory, owner, broadcaster, imageEmitter, redshifts, polynomial, averageImage, operation);
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

    public void produceImages(RedshiftCreatorKind kind, int boxSize, int margin, boolean useFullRangePanels, boolean annotateAnimations, int[] annotationColor) {
        var requiredShifts = createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0));
        var missingShifts = requiredShifts.stream().filter(d -> !shiftImages.containsKey(d)).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        var progressOperation = operation.createChild("Producing redshift animations and panels");
        broadcaster.broadcast(progressOperation);
        double progress = 0;
        var computedShifts = new TreeSet<>(shiftImages.keySet());
        var adjustedRedshifts = shiftImages.get(0d)
            .findMetadata(Redshifts.class)
            .map(Redshifts::redshifts)
            .map(List::reversed)
            .orElse(List.of());
        var maxShift = adjustedRedshifts.stream()
                           .mapToDouble(RedshiftArea::pixelShift)
                           .max()
                           .orElse(0d) + margin;
        var min = Math.max(-maxShift, computedShifts.first());
        var max = Math.min(maxShift, computedShifts.last());
        var range = createMinMaxRange(min, max, .25).stream().sorted().toList();
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var initialImages = range.stream().map(shiftImages::get).toList();
        var constrastAdjusted = contrast.autoContrast(Map.of("img", initialImages, "gamma", params.autoStretchParams().gamma()));
        if (constrastAdjusted instanceof List list) {
            var shiftToContrastAdjusted = new HashMap<Double, ImageWrapper>();
            for (int i = 0; i < range.size(); i++) {
                shiftToContrastAdjusted.put(range.get(i), (ImageWrapper) list.get(i));
            }
            for (var redshift : adjustedRedshifts) {
                broadcaster.broadcast(progressOperation.update(progress / redshifts.size(), "Producing images for redshift " + redshift));
                produceImagesForRedshift(redshift, kind, boxSize, useFullRangePanels, annotateAnimations, shiftToContrastAdjusted, annotationColor);
                progress++;
            }
        }
        broadcaster.broadcast(progressOperation.complete());
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

    private LinkedHashSet<Double> createMinMaxRange(double minShift, double maxShift, double increment) {
        var requiredShifts = new LinkedHashSet<Double>();
        for (double i = minShift; i <= maxShift; i += increment) {
            requiredShifts.add(i);
        }
        return requiredShifts;
    }

    private void produceImagesForRedshift(RedshiftArea redshift,
                                          RedshiftCreatorKind kind,
                                          int boxSize,
                                          boolean useFullRangePanels,
                                          boolean annotateAnimations,
                                          Map<Double, ImageWrapper> shiftToContrastAdjusted,
                                          int[] annotationColor) {
        var centerX = redshift.maxX();
        var centerY = redshift.maxY();
        // grow x1/x2/y1/y2 so that the area is centered and fits the box size
        var dx = boxSize / 2;
        var dy = boxSize / 2;
        var x1 = Math.max(0, centerX - dx);
        var y1 = Math.max(0, centerY - dy);
        var crop = new Crop(Map.of(), broadcaster);
        var constrastAdjusted = shiftToContrastAdjusted.keySet().stream().sorted().map(shiftToContrastAdjusted::get).toList();
        var animate = new Animate(Map.of(), broadcaster);
        var cropped = crop.crop(Map.of("img", constrastAdjusted, "left", x1, "top", y1, "width", boxSize, "height", boxSize));
        if (kind == RedshiftCreatorKind.ANIMATION || kind == RedshiftCreatorKind.ALL) {
            var annotationColorHex = toHex(annotationColor);
            generateAnim(redshift, animate, cropped, annotateAnimations, boxSize, boxSize, new Scaling(Map.of(), broadcaster, crop), annotationColorHex);
        }
        if (kind == RedshiftCreatorKind.PANEL || kind == RedshiftCreatorKind.ALL) {
            generatePanel(redshift, (List<ImageWrapper>) cropped, boxSize, crop, useFullRangePanels, annotationColor);
        }
    }

    public String toAngstroms(double shift) {
        var lambda0 = params.spectrumParams().ray().wavelength();
        var instrument = params.observationDetails().instrument();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, params.observationDetails().pixelSize() * params.observationDetails().binning());
        var angstroms = shift * dispersion.angstromsPerPixel();
        return String.format(Locale.US, "%.2fÅ", angstroms);
    }

    public void generateStandaloneAnimation(int x, int y, int width, int height, double minShift, double maxShift, String title, String name, boolean annotate, int delay, int[] annotationColor) {
        var crop = new Crop(Map.of(), broadcaster);
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var animate = new Animate(Map.of(), broadcaster);
        var range = createMinMaxRange(minShift, maxShift, .25);
        var missingShifts = range.stream().filter(d -> !shiftImages.containsKey(d)).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        var initialImages = range.stream().map(shiftImages::get).toList();
        var maxWidth = initialImages.stream().mapToInt(ImageWrapper::width).max().orElse(0);
        var maxHeight = initialImages.stream().mapToInt(ImageWrapper::height).max().orElse(0);
        // make sure that x+width <= maxWidth and y+height <= maxHeight
        var cropWidth = Math.min(width, maxWidth - x);
        var cropHeight = Math.min(height, maxHeight - y);
        var constrastAdjusted = contrast.autoContrast(Map.of("img", initialImages, "gamma", params.autoStretchParams().gamma()));
        var cropped = crop.crop(Map.of("img", constrastAdjusted, "left", x, "top", y, "width", cropWidth, "height", cropHeight));
        var scaling = new Scaling(Map.of(), broadcaster, crop);
        var annotationColorHex = toHex(annotationColor);
        List<ImageWrapper> frames = createFrames(cropWidth, cropHeight, annotate, cropped, scaling, annotationColorHex);
        var anim = (FileOutput) animate.createAnimation(Map.of("images", frames, "delay", delay));
        imageEmitter.newGenericFile(
            GeneratedImageKind.CROPPED,
            null, title,
            name,
            null,
            anim.file());
    }

    private static String toHex(int[] annotationColor) {
        return String.format("%02x%02x%02x", annotationColor[0], annotationColor[1], annotationColor[2]);
    }

    private List<ImageWrapper> createFrames(int width, int height, boolean annotate, Object cropped, Scaling scaling, String annotationColorHex) {
        List<ImageWrapper> frames;
        if (annotate && cropped instanceof List list) {
            var lambda0 = params.spectrumParams().ray().wavelength();
            var instrument = params.observationDetails().instrument();
            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, params.observationDetails().pixelSize() * params.observationDetails().binning());
            var draw = new ImageDraw(Map.of(), broadcaster);
            int finalWidth;
            int finalHeight;
            if (width < 128) {
                // rescale so that drawing text is readable
                var scale = 128d / width;
                list = (List) scaling.relativeRescale(Map.of("img", list, "sx", scale, "sy", scale));
                finalWidth = 128;
                finalHeight = (int) (height * scale);
            } else {
                finalWidth = width;
                finalHeight = height;
            }
            var fontSize = finalWidth / 16f;
            var progress = new AtomicInteger(0);
            var progressOperation = operation.createChild("Annotating frames");
            broadcaster.broadcast(progressOperation);
            double totalImages = list.size();
            frames = ((List<Object>)list).stream()
                .parallel()
                .map(o -> {
                    var frame = (ImageWrapper) o;
                    double pixelShift = frame.findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0d);
                    var angstroms = pixelShift * dispersion.angstromsPerPixel();
                    var legend = String.format(Locale.US, "%.2fÅ (%.2f km/s)", angstroms, Math.abs(PhenomenaDetector.speedOf(pixelShift, dispersion, lambda0)));
                    var annotated = (ImageWrapper) draw.drawText(frame, "*" + legend + "*", (int) fontSize, (int) (finalHeight - 2 * fontSize / 3), annotationColorHex, (int) fontSize);
                    broadcaster.broadcast(progressOperation.update(progress.incrementAndGet() / totalImages));
                    return annotated;
                })
                .map(FileBackedImage::wrap)
                .map(ImageWrapper.class::cast)
                .toList();
            broadcaster.broadcast(progressOperation.complete());
        } else {
            frames = (List<ImageWrapper>) cropped;
        }
        return frames;
    }

    public void generateStandalonePanel(int x, int y, int width, int height, double minShift, double maxShift, String title, String name, int[] annotationColor) {
        var crop = new Crop(Map.of(), broadcaster);
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var range = createMinMaxRange(minShift, maxShift, 1);
        var missingShifts = range.stream().filter(d -> !shiftImages.containsKey(d)).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        var initialImages = range.stream().map(shiftImages::get).toList();
        var constrastAdjusted = contrast.autoContrast(Map.of("img", initialImages, "gamma", params.autoStretchParams().gamma()));
        // make sure that x+width <= maxWidth and y+height <= maxHeight
        var cropWidth = Math.min(width, initialImages.stream().mapToInt(ImageWrapper::width).max().orElse(0) - x);
        var cropHeight = Math.min(height, initialImages.stream().mapToInt(ImageWrapper::height).max().orElse(0) - y);
        var cropped = crop.crop(Map.of("img", constrastAdjusted, "left", x, "top", y, "width", cropWidth, "height", cropHeight));
        // compute individual width/height so that the final image width doesn't exceed 7680 pixels
        var maxWidth = MAX_PANEL_SIZE / Math.sqrt(initialImages.size());
        var maxHeight = MAX_PANEL_SIZE / Math.sqrt(initialImages.size());
        var maxBoxSize = Math.min(maxWidth, maxHeight);
        int finalWidth;
        int finalHeight;
        List<ImageWrapper> frames;
        if (cropWidth < 128 || cropHeight < 128) {
            // rescale so that drawing text is readable and final image not too big
            var scaling = new Scaling(Map.of(), broadcaster, crop);
            var scale = 128d / cropWidth;
            frames = (List<ImageWrapper>) scaling.relativeRescale(Map.of("img", cropped, "sx", scale, "sy", scale));
            finalWidth = 128;
            finalHeight = (int) (cropHeight * scale);
        } else if (cropWidth > maxBoxSize || cropHeight > maxBoxSize) {
            // rescale so that the final image doesn't exceed 7680 pixels
            var scale = Math.min(maxBoxSize / (double) cropWidth, maxBoxSize / (double) cropHeight);
            frames = (List<ImageWrapper>) new Scaling(Map.of(), broadcaster, crop).relativeRescale(Map.of("img", cropped, "sx", scale, "sy", scale));
            finalWidth = (int) (cropWidth * scale);
            finalHeight = (int) (cropHeight * scale);
        } else {
            frames = (List<ImageWrapper>) cropped;
            finalWidth = cropWidth;
            finalHeight = cropHeight;
        }

        createSinglePanel(frames, finalWidth, finalHeight, title, name, annotationColor);
    }

    private void generateAnim(RedshiftArea redshift, Animate animate, Object cropped, boolean annotateAnimations, int width, int height, Scaling scaling, String annotationColorHex) {
        var frames = createFrames(width, height, annotateAnimations, cropped, scaling, annotationColorHex);
        var anim = (FileOutput) animate.createAnimation(Map.of("images", frames, "delay", 25));
        imageEmitter.newGenericFile(
            GeneratedImageKind.REDSHIFT,
            null, String.format("Panel %s (%.2f km/s)", redshift.id(), redshift.kmPerSec()),
            "redshift-" + redshift.id(),
            null,
            anim.file());
    }

    private void generatePanel(RedshiftArea redshift, List<ImageWrapper> cropped, int boxSize, Crop crop, boolean useFullRangePanels, int[] annotationColor) {
        var snapshots = cropped;
        if (boxSize <= 128) {
            // this is a bit small to display the text, so we're going to scale by a factor of 2
            var scaling = new Scaling(Map.of(), broadcaster, crop);
            snapshots = (List<ImageWrapper>) scaling.relativeRescale(Map.of("img", snapshots, "sx", 2, "sy", 2));
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
        var title = String.format("Panel %s (%.2f km/s)", redshift.id(), redshift.kmPerSec());
        var name = "redshift-" + redshift.id();
        var width = boxSize;
        var height = boxSize;
        createSinglePanel(snapshotsToDisplay, width, height, title, name, annotationColor);
    }

    private void createSinglePanel(List<ImageWrapper> snapshotsToDisplay, int width, int height, String title, String name, int[] annotationColor) {
        int cols = (int) Math.ceil(Math.sqrt(snapshotsToDisplay.size()));
        int rows = (int) Math.ceil((double) snapshotsToDisplay.size() / cols);
        int panelWidth = cols * width;
        int panelHeight = rows * height;
        var lambda0 = params.spectrumParams().ray().wavelength();
        var instrument = params.observationDetails().instrument();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, params.observationDetails().pixelSize() * params.observationDetails().binning());
        imageEmitter.newColorImage(
            GeneratedImageKind.REDSHIFT,
            null, title,
            name,
            null,
            panelWidth,
            panelHeight,
            Map.of(), () -> {
                var rgb = new float[3][panelHeight][panelWidth];
                var r = rgb[0];
                var g = rgb[1];
                var b = rgb[2];

                for (int i = 0; i < snapshotsToDisplay.size(); i++) {
                    var snap = snapshotsToDisplay.get(i);
                    var mono = (ImageWrapper32) snap.unwrapToMemory();
                    var data = mono.data();
                    var row = i / cols;
                    var col = i % cols;
                    var yOffset = row * height;
                    var xOffset = col * width;
                    var pixelShift = snap.findMetadata(PixelShift.class).map(PixelShift::pixelShift).orElse(0d);
                    var angstroms = pixelShift * dispersion.angstromsPerPixel();
                    var legend = String.format(Locale.US, "%.2fÅ (%.2f km/s)", angstroms, Math.abs(PhenomenaDetector.speedOf(pixelShift, dispersion, lambda0)));
                    // draw legend on a dummy image
                    var legendImage = createLegendImage(width, height, legend);
                    var legendOverlay = legendImage.getData();
                    var legendPixel = new int[1];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            var gray = data[y][x];
                            legendOverlay.getPixel(x, y, legendPixel);
                            if (legendPixel[0] > 0) {
                                r[yOffset + y][xOffset + x] = Constants.MAX_PIXEL_VALUE * annotationColor[0] / 255;
                                g[yOffset + y][xOffset + x] = Constants.MAX_PIXEL_VALUE * annotationColor[1] / 255;
                                b[yOffset + y][xOffset + x] = Constants.MAX_PIXEL_VALUE * annotationColor[2] / 255;
                            } else {
                                r[yOffset + y][xOffset + x] = gray;
                                g[yOffset + y][xOffset + x] = gray;
                                b[yOffset + y][xOffset + x] = gray;
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
        var solexVideoProcessor = new SolexVideoProcessor(serFile, outputDirectory, 0, tmpParams, LocalDateTime.now(), false, Configuration.getInstance().getMemoryRestrictionMultiplier(), operation);
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
        solexVideoProcessor.setIgnoreIncompleteShifts(true);
        solexVideoProcessor.process();
    }

    public double estimateRequiredBytesForProcessing(double n) {
        return n * imageWidth * imageHeight * BYTES_IN_FLOAT * TMP_IMAGES_COUNT * SAMPLING;
    }

    public String estimateRequiredDiskSpace(double n) {
        var size = estimateRequiredBytesForProcessing(n) / 1024 / 1024;
        if (size > 1024) {
            return String.format(message("disk.requirement"), size / 1024, "GB");
        }
        return String.format(message("disk.requirement"), size, "MB");
    }

    public String estimateRequiredDiskSpaceWithMargin(int margin) {
        return estimateRequiredDiskSpace(createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0)).size() / SAMPLING);
    }

    public double estimateRequiredBytesForProcessingWithMargin(int margin) {
        return estimateRequiredBytesForProcessing(createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0)).size() / SAMPLING);
    }

    public enum RedshiftCreatorKind {
        ANIMATION,
        PANEL,
        ALL
    }
}
