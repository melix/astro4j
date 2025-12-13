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
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.FileOutputResult;
import me.champeau.a4j.jsolex.processing.expr.impl.AdjustContrast;
import me.champeau.a4j.jsolex.processing.expr.impl.Animate;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
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
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

/**
 * Processes and generates visualization outputs for redshift phenomena detected in solar images.
 * This class handles the creation of animations and panel layouts showing wavelength shifts.
 */
public class RedshiftImagesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedshiftImagesProcessor.class);
    private final static int[] YELLOW = {255, 255, 0};
    private static final int SAMPLING = 4;
    private static final int BYTES_IN_FLOAT = 4;
    private static final int TMP_IMAGES_COUNT = 4;
    private static final int MAX_PANEL_SIZE = 7680;

    private final Map<PixelShift, ImageWrapper> shiftImages;
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
    private final FileNamingStrategy namingStrategy;
    private final int sequenceNumber;
    private final String serFileBaseName;
    private final int imageWidth;
    private final int imageHeight;

    /**
     * Creates a new redshift images processor.
     *
     * @param shiftImages map of pixel shifts to corresponding images
     * @param params processing parameters
     * @param serFile source SER video file
     * @param outputDirectory directory for output files
     * @param owner JSolEx interface for UI updates
     * @param broadcaster event broadcaster for progress updates
     * @param imageEmitter emitter for generated images
     * @param redshifts list of detected redshift areas
     * @param polynomial polynomial function for spectrum analysis
     * @param averageImage average image data
     * @param operation progress operation for tracking
     * @param namingStrategy strategy for naming output files
     * @param sequenceNumber sequence number for output naming
     * @param serFileBaseName base name of the SER file
     */
    public RedshiftImagesProcessor(Map<PixelShift, ImageWrapper> shiftImages,
                                   ProcessParams params,
                                   File serFile,
                                   Path outputDirectory,
                                   JSolExInterface owner,
                                   Broadcaster broadcaster,
                                   ImageEmitter imageEmitter,
                                   List<RedshiftArea> redshifts,
                                   DoubleUnaryOperator polynomial,
                                   float[][] averageImage,
                                   ProgressOperation operation,
                                   FileNamingStrategy namingStrategy,
                                   int sequenceNumber,
                                   String serFileBaseName) {
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
        this.namingStrategy = namingStrategy;
        this.sequenceNumber = sequenceNumber;
        this.serFileBaseName = serFileBaseName;
        var image = shiftImages.values().stream().findFirst();
        if (image.isPresent()) {
            imageWidth = image.get().width();
            imageHeight = image.get().height();
        } else {
            imageWidth = 0;
            imageHeight = 0;
        }
    }

    /**
     * Creates a new processor instance with different redshift areas.
     *
     * @param redshifts new list of redshift areas
     * @return new processor instance with updated redshifts
     */
    public RedshiftImagesProcessor withRedshifts(List<RedshiftArea> redshifts) {
        return new RedshiftImagesProcessor(shiftImages, params, serFile, outputDirectory, owner, broadcaster, imageEmitter, redshifts, polynomial, averageImage, operation, namingStrategy, sequenceNumber, serFileBaseName);
    }

    /**
     * Computes the sun radius from ellipse metadata.
     *
     * @return the average of semi-major and semi-minor axes, or empty if no ellipse metadata found
     */
    public Optional<Double> getSunRadius() {
        return shiftImages.values().stream()
            .map(i -> i.findMetadata(Ellipse.class).map(e -> (e.semiAxis().a() + e.semiAxis().b()) / 2))
            .findFirst()
            .orElse(Optional.empty());
    }

    /**
     * Returns the list of detected redshift areas.
     *
     * @return list of redshift areas
     */
    public List<RedshiftArea> getRedshifts() {
        return redshifts;
    }

    /**
     * Produces visualization images for all redshift areas.
     *
     * @param kind type of output to create (animation, panel, or both)
     * @param boxSize size of the cropped box around each redshift area
     * @param margin additional pixel shifts to include beyond detected maximum
     * @param useFullRangePanels whether to show full range of shifts in panels
     * @param annotateAnimations whether to add wavelength annotations to animation frames
     * @param annotationColor RGB color values for annotations
     */
    public void produceImages(RedshiftCreatorKind kind, int boxSize, int margin, boolean useFullRangePanels, boolean annotateAnimations, int[] annotationColor) {
        var requiredShifts = createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0));
        var missingShifts = requiredShifts.stream().filter(d -> !shiftImages.containsKey(new PixelShift(d))).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        var progressOperation = operation.createChild("Producing redshift animations and panels");
        broadcaster.broadcast(progressOperation);
        double progress = 0;
        var computedShifts = new TreeSet<>(shiftImages.keySet());
        var adjustedRedshifts = shiftImages.get(new PixelShift(0d))
            .findMetadata(Redshifts.class)
            .map(Redshifts::redshifts)
            .map(List::reversed)
            .orElse(List.of());
        var maxShift = adjustedRedshifts.stream()
                           .mapToDouble(RedshiftArea::pixelShift)
                           .max()
                           .orElse(0d) + margin;
        var min = Math.max(-maxShift, computedShifts.first().pixelShift());
        var max = Math.min(maxShift, computedShifts.last().pixelShift());
        var range = createMinMaxRange(min, max, .25).stream().sorted().toList();
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var initialImages = range.stream().map(s -> shiftImages.get(new PixelShift(s))).toList();
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

    /**
     * Creates a range of pixel shifts including fractional values.
     *
     * @param margin additional margin around the pixel shift
     * @param pixelShift maximum pixel shift value
     * @return set of pixel shift values with 0.25 increments
     */
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

    /**
     * Creates a range of pixel shifts between min and max values.
     *
     * @param minShift minimum shift value
     * @param maxShift maximum shift value
     * @param increment step size between values
     * @return set of pixel shift values
     */
    private LinkedHashSet<Double> createMinMaxRange(double minShift, double maxShift, double increment) {
        var requiredShifts = new LinkedHashSet<Double>();
        for (double i = minShift; i <= maxShift; i += increment) {
            requiredShifts.add(i);
        }
        return requiredShifts;
    }

    /**
     * Generates images for a specific redshift area.
     *
     * @param redshift the redshift area to process
     * @param kind type of output to create
     * @param boxSize size of the cropped region
     * @param useFullRangePanels whether to show full range in panels
     * @param annotateAnimations whether to annotate animation frames
     * @param shiftToContrastAdjusted map of pixel shifts to contrast-adjusted images
     * @param annotationColor RGB color for annotations
     */
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
        var animate = new Animate(Map.of(AnimationFormat.class, Configuration.getInstance().getAnimationFormats()), broadcaster);
        var cropped = crop.crop(Map.of("img", constrastAdjusted, "left", x1, "top", y1, "width", boxSize, "height", boxSize));
        if (kind == RedshiftCreatorKind.ANIMATION || kind == RedshiftCreatorKind.ALL) {
            var annotationColorHex = toHex(annotationColor);
            generateAnim(redshift, animate, cropped, annotateAnimations, boxSize, boxSize, new Scaling(Map.of(), broadcaster, crop), annotationColorHex);
        }
        if (kind == RedshiftCreatorKind.PANEL || kind == RedshiftCreatorKind.ALL) {
            generatePanel(redshift, (List<ImageWrapper>) cropped, boxSize, crop, useFullRangePanels, annotationColor);
        }
    }

    /**
     * Converts pixel shift to wavelength in angstroms.
     *
     * @param shift pixel shift value
     * @return formatted string representation in angstroms
     */
    public String toAngstroms(double shift) {
        var lambda0 = params.spectrumParams().ray().wavelength();
        var instrument = params.observationDetails().instrument();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, params.observationDetails().pixelSize() * params.observationDetails().binning());
        var angstroms = shift * dispersion.angstromsPerPixel();
        return String.format(Locale.US, "%.2fÅ", angstroms);
    }

    /**
     * Converts wavelength in angstroms to pixel shift.
     *
     * @param angstroms wavelength shift in angstroms
     * @return equivalent pixel shift value
     */
    public double toPixels(double angstroms) {
        var lambda0 = params.spectrumParams().ray().wavelength();
        var instrument = params.observationDetails().instrument();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, params.observationDetails().pixelSize() * params.observationDetails().binning());
        return angstroms / dispersion.angstromsPerPixel();
    }

    /**
     * Generates a standalone animation for a custom region.
     *
     * @param x left coordinate of the region
     * @param y top coordinate of the region
     * @param width width of the region
     * @param height height of the region
     * @param minShift minimum pixel shift for animation
     * @param maxShift maximum pixel shift for animation
     * @param title title for the animation
     * @param name base name for output files
     * @param annotate whether to add annotations
     * @param delay frame delay in milliseconds
     * @param annotationColor RGB color for annotations
     */
    public void generateStandaloneAnimation(int x, int y, int width, int height, double minShift, double maxShift, String title, String name, boolean annotate, int delay, int[] annotationColor) {
        var crop = new Crop(Map.of(), broadcaster);
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var animate = new Animate(Map.of(AnimationFormat.class, Configuration.getInstance().getAnimationFormats()), broadcaster);
        var range = createMinMaxRange(minShift, maxShift, .25);
        var missingShifts = range.stream().filter(d -> !shiftImages.containsKey(new PixelShift(d))).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        var initialImages = range.stream().map(s -> shiftImages.get(new PixelShift(s))).toList();
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
        var anim = (FileOutputResult) animate.createAnimation(Map.of("images", frames, "delay", delay));
        var displayFile = anim.displayFile();

        // Save non-display files manually without firing display events
        var baseName = namingStrategy.render(sequenceNumber, null,
            GeneratedImageKind.CROPPED.directoryKind().name().toLowerCase(Locale.US),
            name, serFileBaseName, null);
        try {
            FilesUtils.saveNonDisplayFiles(anim, outputDirectory, baseName);
        } catch (IOException e) {
            LOGGER.error(JSolEx.message("error.failed.save.animation"), e);
        }

        // Only emit the display file to avoid duplicate display
        imageEmitter.newGenericFile(
            GeneratedImageKind.CROPPED,
            null, title,
            name,
            null,
            displayFile);
    }

    /**
     * Converts RGB color array to hexadecimal string.
     *
     * @param annotationColor RGB values as integers
     * @return hexadecimal color string
     */
    private static String toHex(int[] annotationColor) {
        return String.format("%02x%02x%02x", annotationColor[0], annotationColor[1], annotationColor[2]);
    }

    /**
     * Creates animation frames with optional annotations.
     *
     * @param width frame width
     * @param height frame height
     * @param annotate whether to add wavelength annotations
     * @param cropped cropped images to process
     * @param scaling scaling operation for resizing
     * @param annotationColorHex hexadecimal color for annotations
     * @return list of processed image frames
     */
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

    /**
     * Generates a standalone panel for a custom region.
     *
     * @param x left coordinate of the region
     * @param y top coordinate of the region
     * @param width width of the region
     * @param height height of the region
     * @param minShift minimum pixel shift for panel
     * @param maxShift maximum pixel shift for panel
     * @param title title for the panel
     * @param name base name for output files
     * @param annotationColor RGB color for annotations
     */
    public void generateStandalonePanel(int x, int y, int width, int height, double minShift, double maxShift, String title, String name, int[] annotationColor) {
        var crop = new Crop(Map.of(), broadcaster);
        var contrast = new AdjustContrast(Map.of(), broadcaster);
        var range = createMinMaxRange(minShift, maxShift, 1);
        var missingShifts = range.stream().filter(d -> !shiftImages.containsKey(new PixelShift(d))).toList();
        if (!missingShifts.isEmpty()) {
            restartProcessForMissingShifts(new LinkedHashSet<>(missingShifts));
        }
        var initialImages = range.stream().map(s -> shiftImages.get(new PixelShift(s))).toList();
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

    /**
     * Generates an animation for a specific redshift area.
     *
     * @param redshift the redshift area
     * @param animate animation operation
     * @param cropped cropped images
     * @param annotateAnimations whether to annotate frames
     * @param width frame width
     * @param height frame height
     * @param scaling scaling operation
     * @param annotationColorHex hexadecimal annotation color
     */
    private void generateAnim(RedshiftArea redshift, Animate animate, Object cropped, boolean annotateAnimations, int width, int height, Scaling scaling, String annotationColorHex) {
        var frames = createFrames(width, height, annotateAnimations, cropped, scaling, annotationColorHex);
        var anim = (FileOutputResult) animate.createAnimation(Map.of("images", frames, "delay", 25));
        var displayFile = anim.displayFile();
        var name = "redshift-" + redshift.id();
        var title = String.format("Panel %s (%.2f km/s)", redshift.id(), redshift.kmPerSec());

        // Save non-display files manually without firing display events
        var baseName = namingStrategy.render(sequenceNumber, null,
            GeneratedImageKind.REDSHIFT.directoryKind().name().toLowerCase(Locale.US),
            name, serFileBaseName, null);
        try {
            FilesUtils.saveNonDisplayFiles(anim, outputDirectory, baseName);
        } catch (IOException e) {
            LOGGER.error(JSolEx.message("error.failed.save.animation"), e);
        }

        // Only emit the display file to avoid duplicate display
        imageEmitter.newGenericFile(
            GeneratedImageKind.REDSHIFT,
            null, title,
            name,
            null,
            displayFile);
    }

    /**
     * Generates a panel for a specific redshift area.
     *
     * @param redshift the redshift area
     * @param cropped cropped images
     * @param boxSize size of individual boxes
     * @param crop crop operation
     * @param useFullRangePanels whether to show full range
     * @param annotationColor RGB annotation color
     */
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

    /**
     * Creates a single panel image from multiple snapshots.
     *
     * @param snapshotsToDisplay list of images to arrange in panel
     * @param width width of each snapshot
     * @param height height of each snapshot
     * @param title panel title
     * @param name output file name
     * @param annotationColor RGB annotation color
     */
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

    /**
     * Creates an image containing only the legend text.
     *
     * @param snapWidth width of the legend image
     * @param snapHeight height of the legend image
     * @param legend text to render
     * @return buffered image with legend text
     */
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

    /**
     * Restarts processing to generate missing pixel shift images.
     *
     * @param missingShifts set of pixel shifts that need to be computed
     */
    private void restartProcessForMissingShifts(Set<Double> missingShifts) {
        LOGGER.warn(message("restarting.process.missing.shifts"), missingShifts.stream().map(d -> String.format("%.2f", d)).toList());
        // restart processing to include missing images
        var tmpParams = params.withRequestedImages(
            new RequestedImages(Set.of(GeneratedImageKind.GEOMETRY_CORRECTED),
                Stream.concat(params.requestedImages().pixelShifts().stream(), missingShifts.stream()).toList(),
                missingShifts,
                Set.of(),
                ImageMathParams.NONE,
                false,
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

    /**
     * Estimates storage requirements for processing.
     *
     * @param n number of pixel shift images
     * @return estimated bytes required
     */
    public double estimateRequiredBytesForProcessing(double n) {
        return n * imageWidth * imageHeight * BYTES_IN_FLOAT * TMP_IMAGES_COUNT * SAMPLING;
    }

    /**
     * Estimates required disk space as a formatted string.
     *
     * @param n number of pixel shift images
     * @return formatted disk space requirement
     */
    public String estimateRequiredDiskSpace(double n) {
        var size = estimateRequiredBytesForProcessing(n) / 1024 / 1024;
        if (size > 1024) {
            return String.format(message("disk.requirement"), size / 1024, "GB");
        }
        return String.format(message("disk.requirement"), size, "MB");
    }

    /**
     * Estimates required disk space including margin.
     *
     * @param margin additional pixel shifts margin
     * @return formatted disk space requirement
     */
    public String estimateRequiredDiskSpaceWithMargin(int margin) {
        return estimateRequiredDiskSpace(createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0)).size() / SAMPLING);
    }

    /**
     * Estimates storage requirements including margin.
     *
     * @param margin additional pixel shifts margin
     * @return estimated bytes required
     */
    public double estimateRequiredBytesForProcessingWithMargin(int margin) {
        return estimateRequiredBytesForProcessing(createRange(margin, redshifts.stream().mapToInt(RedshiftArea::pixelShift).max().orElse(0)).size() / SAMPLING);
    }

    /**
     * Defines the type of output to generate for redshift visualizations.
     */
    public enum RedshiftCreatorKind {
        /**
         * Generate animation output only.
         */
        ANIMATION,
        /**
         * Generate panel output only.
         */
        PANEL,
        /**
         * Generate both animation and panel outputs.
         */
        ALL
    }
}
