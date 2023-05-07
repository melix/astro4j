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
package me.champeau.a4j.jsolex.processing.sun;

import javafx.scene.control.Alert;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.util.Constants;
import me.champeau.a4j.jsolex.app.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ImageLine;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.event.ProcessingStartEvent;
import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.DefaultImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitterFactory;
import me.champeau.a4j.jsolex.processing.sun.workflow.ProcessingWorkflow;
import me.champeau.a4j.jsolex.processing.sun.workflow.RenamingImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.StepFilteringImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowStep;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.tuples.DoubleTriplet;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SolexVideoProcessor implements Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);

    private final Set<ProcessingEventListener> progressEventListeners = new HashSet<>();

    private final File serFile;
    private final File debugDirectory;
    private final File processedDirectory;
    private final File rawImagesDirectory;
    private final ProcessParams processParams;
    private final boolean quickMode;

    public SolexVideoProcessor(File serFile,
                               File outputDirectory,
                               ProcessParams processParametersProvider,
                               boolean quickMode) {
        this.serFile = serFile;
        this.debugDirectory = new File(outputDirectory, Constants.DEBUG_DIRECTORY);
        this.rawImagesDirectory = new File(outputDirectory, Constants.RAW_DIRECTORY);
        this.processedDirectory = new File(outputDirectory, Constants.PROCESSED_DIRECTORY);
        this.processParams = processParametersProvider;
        this.quickMode = quickMode;
    }

    public void addEventListener(ProcessingEventListener listener) {
        progressEventListeners.add(listener);
    }

    public void removeEventListener(ProcessingEventListener listener) {
        progressEventListeners.remove(listener);
    }

    public void process() {
        broadcast(new ProcessingStartEvent(System.nanoTime()));
        var converter = ImageUtils.createImageConverter(processParams.videoParams().colorMode());
        var detector = new MagnitudeBasedSunEdgeDetector(converter);
        try (SerFileReader reader = SerFileReader.of(serFile)) {
            var header = reader.header();
            ImageGeometry geometry = header.geometry();
            var frameCount = header.frameCount();
            LOGGER.info("SER file contains {} frames", frameCount);
            LOGGER.info("Color mode : {} ({} bytes per pixel, depth = {} bits)", geometry.colorMode(), geometry.getBytesPerPixel(), geometry.pixelDepthPerPlane());
            LOGGER.info("Width: {}, height: {}", geometry.width(), geometry.height());
            LOGGER.info("Detecting limb... ");
            detector.detectEdges(reader);
            detector.ifEdgesDetected((start, end) -> {
                        LOGGER.info("Sun edges detected at frames {} and {}", start, end);
                        generateImages(converter, reader, Math.max(0, start - 40), Math.min(end + 40, frameCount) - 1);
                    }
                    , () -> {
                        LOGGER.info("Sun edges weren't detected, processing whole video");
                        generateImages(converter, reader, 0, frameCount - 1);
                    });
        } catch (Exception e) {
            broadcastError(e);
        }
    }

    private void broadcastError(Exception ex) {
        String trace = JSolEx.logError(ex);
        broadcast(new NotificationEvent(new Notification(Alert.AlertType.ERROR, "Unexpected error", "An error occurred during processing", trace)));
    }

    private void generateImages(ImageConverter<float[]> converter,
                                SerFileReader reader,
                                int start,
                                int end) {
        var geometry = reader.header().geometry();
        int width = geometry.width();
        int height = geometry.height();
        var fps = reader.estimateFps();
        int newHeight = end - start;
        broadcast(OutputImageDimensionsDeterminedEvent.of("raw", width, newHeight));
        List<WorkflowState> imageList = createWorkflowStateSteps(width, newHeight);
        LOGGER.info("Starting reconstruction...");
        performImageReconstruction(converter, reader, start, end, geometry, width, height, imageList.toArray(new WorkflowState[0]));
        ProcessParams currentParams = processParams;
        for (int step = 0; step < imageList.size(); step++) {
            WorkflowState state = imageList.get(step);
            int finalStep = step;
            var imageEmitterFactory = new ImageEmitterFactory() {
                @Override
                public ImageEmitter newEmitter(Broadcaster broadcaster, ParallelExecutor executor, File outputDirectory) {
                    ImageEmitter emitter = new DefaultImageEmitter(broadcaster, executor, outputDirectory);
                    var shift = state.pixelShift();
                    if (finalStep == 0) {
                        return emitter;
                    }
                    var suffix = " (" + (shift == 15 ? "continuum" : shift) + ")";
                    emitter = new RenamingImageEmitter(emitter, title -> title + suffix, name -> name + suffix);
                    return new StepFilteringImageEmitter(emitter, state.steps());
                }
            };
            var rotateLeft = ImageMath.newInstance().rotateLeft(state.reconstructed(), width, newHeight);
            var rotated = new ImageWrapper32(newHeight, width, rotateLeft);
            maybePerformFlips(rotated);
            state.setImage(rotated);
            ProcessingWorkflow workflow;
            try (var executor = ParallelExecutor.newExecutor()) {
                executor.setExceptionHandler(this::broadcastError);
                workflow = new ProcessingWorkflow(
                        this,
                        rawImagesDirectory,
                        debugDirectory,
                        processedDirectory,
                        executor,
                        imageList,
                        step,
                        currentParams,
                        fps.orElse(null),
                        imageEmitterFactory
                );
                workflow.start();
            } catch (Exception e) {
                throw new ProcessingException(e);
            }
            currentParams = currentParams.withGeometry(workflow.getTilt(), workflow.getXyRatio());
        }

        broadcast(new ProcessingDoneEvent(System.nanoTime()));
    }

    private void maybePerformFlips(ImageWrapper32 rotated) {
        var hflip = processParams.geometryParams().isHorizontalMirror();
        var vflip = processParams.geometryParams().isVerticalMirror();
        if (hflip || vflip) {
            var original = rotated.data();
            float[] flipped = new float[original.length];
            var height = rotated.height();
            var width = rotated.width();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var nx = hflip ? width - x - 1 : x;
                    var ny = vflip ? height - y - 1 : y;
                    flipped[nx + width * ny] = original[x + y * width];
                }
            }
            System.arraycopy(flipped, 0, original, 0, original.length);
        }
    }

    private List<WorkflowState> createWorkflowStateSteps(int width, int newHeight) {
        if (quickMode) {
            return List.of(WorkflowState.prepare(width, newHeight, processParams.spectrumParams().pixelShift(),
                    EnumSet.of(WorkflowStep.RAW_IMAGE, WorkflowStep.BANDING_CORRECTION)));
        }
        var dopplerShift = processParams.spectrumParams().dopplerShift();
        var imageList = processParams.spectrumParams().pixelShift() == 0 ? List.of(
                WorkflowState.prepare(width, newHeight, 0, EnumSet.allOf(WorkflowStep.class)),
                WorkflowState.prepare(width, newHeight, -dopplerShift),
                WorkflowState.prepare(width, newHeight, dopplerShift, WorkflowStep.DOPPLER_IMAGE),
                WorkflowState.prepare(width, newHeight, +15, WorkflowStep.BANDING_CORRECTION)
        ) : List.of(
                WorkflowState.prepare(width, newHeight, processParams.spectrumParams().pixelShift(), EnumSet.allOf(WorkflowStep.class))
        );
        return imageList;
    }

    private void performImageReconstruction(ImageConverter<float[]> converter, SerFileReader reader, int start, int end, ImageGeometry geometry, int width, int height, WorkflowState... images) {
        try (var executor = ParallelExecutor.newExecutor()) {
            executor.setExceptionHandler(this::broadcastError);
            reader.seekFrame(start);
            var analyzer = new SpectrumFrameAnalyzer(width, height, processParams.spectrumParams().spectralLineDetectionThreshold(), 5000d);
            float[] average = computeAverageImage(converter, reader, start, end, geometry, analyzer);
            analyzer.findDistortionPolynomial().ifPresent(polynomial -> {
                if (processParams.debugParams().generateDebugImages()) {
                    var outputFile = new File(debugDirectory, "average.png");
                    var rgb = new SpectralLineFrameImageCreator(analyzer, average, width, height)
                            .generateDebugImage();
                    broadcast(new ImageGeneratedEvent(new GeneratedImage("Average", outputFile.toPath(), rgb, LinearStrechingStrategy.DEFAULT)));
                }
                LOGGER.info("Distortion polynomial ax2 + bx + c = 0\n    - a = {}\n    - b = {}\n    - c = {}", polynomial.a(), polynomial.b(), polynomial.c());
                reader.seekFrame(start);
                for (int i = start, j = 0; i < end; i++, j += width) {
                    var original = converter.createBuffer(geometry);
                    // The converter makes sure we only have a single channel
                    converter.convert(i, reader.currentFrame().data(), geometry, original);
                    reader.nextFrame();
                    int offset = j;
                    for (WorkflowState state : images) {
                        executor.submit(() -> {
                            processSingleFrame(width, height, state.reconstructed(), offset, original, polynomial, state.pixelShift());
                        });
                    }
                }
            });
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        LOGGER.info("Reconstruction done. Generating images...");
    }

    private static float[] computeAverageImage(ImageConverter<float[]> converter, SerFileReader reader, int start, int end, ImageGeometry geometry, SpectrumFrameAnalyzer analyzer) {
        var average = converter.createBuffer(geometry);
        var current = converter.createBuffer(geometry);
        for (int i = start; i < end; i++) {
            converter.convert(i, reader.currentFrame().data(), geometry, current);
            for (int j = 0; j < current.length; j++) {
                average[j] = average[j] + (current[j] - average[j]) / ((1 + i - start));
            }
            reader.nextFrame();
        }
        analyzer.analyze(average);
        return average;
    }

    private void processSingleFrame(int width,
                                    int height,
                                    float[] outputBuffer,
                                    int offset,
                                    float[] buffer,
                                    DoubleTriplet p,
                                    int pixelShift) {

        var fun = p.asPolynomial();
        double[] line = new double[width];
        int lastY = 0;
        for (int x = 0; x < width; x++) {
            // To reconstruct the image, we use the polynom to find which pixel to use
            double yd = fun.applyAsDouble(x) + pixelShift;
            int yi = (int) yd;
            if (yi < 0 || yi >= height) {
                yi = lastY;
                yd = lastY;
            }
            double frac = yd - yi;
            float value;
            if (frac > 0) {
                float lo = buffer[x + width * yi];
                float hi = yi < height - 1 ? buffer[x + width * (yi + 1)] : buffer[x + width * yi];
                value = (float) (lo + frac * (hi - lo));
            } else {
                value = buffer[x + width * yi];
            }
            if (value < 0 || value > 65535) {
                throw new IllegalArgumentException("Unexpected value computed " + value + " which should be in the [0..65535] range");
            }
            outputBuffer[offset + x] = value;
            line[x] = value;
            lastY = yi;
        }
        broadcast(new PartialReconstructionEvent(new ImageLine(pixelShift, offset / width, line, quickMode || processParams.debugParams().generateDebugImages())));
    }

    @Override
    public void broadcast(ProcessingEvent<?> event) {
        for (ProcessingEventListener listener : progressEventListeners) {
            switch (event) {
                case OutputImageDimensionsDeterminedEvent e -> listener.onOutputImageDimensionsDetermined(e);
                case PartialReconstructionEvent e -> listener.onPartialReconstruction(e);
                case ImageGeneratedEvent e -> listener.onImageGenerated(e);
                case NotificationEvent e -> listener.onNotification(e);
                case SuggestionEvent e -> listener.onSuggestion(e);
                case ProcessingStartEvent e -> listener.onProcessingStart(e);
                case ProcessingDoneEvent e -> listener.onProcessingDone(e);
                case ProgressEvent e -> listener.onProgress(e);
            }
        }
    }

}
