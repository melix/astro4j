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
import me.champeau.a4j.jsolex.app.util.Constants;
import me.champeau.a4j.jsolex.app.util.SpectralLineFrameImageCreator;
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
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.ProcessingWorkflow;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.tuples.DoubleTriplet;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class SolexVideoProcessor implements Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);
    private static final LinearStrechingStrategy LINEAR_STRECHING_STRATEGY = LinearStrechingStrategy.DEFAULT;

    private final Set<ProcessingEventListener> progressEventListeners = new HashSet<>();

    private final File serFile;
    private final File rootDirectory;
    private final File debugDirectory;
    private final File processedDirectory;
    private final File rawImagesDirectory;
    private final boolean generateDebugImages;
    private final double spectrumDetectionThreshold;

    public SolexVideoProcessor(File serFile,
                               File outputDirectory,
                               boolean generateDebugImages,
                               double spectrumDetectionThreshold) {
        this.serFile = serFile;
        this.rootDirectory = outputDirectory;
        this.debugDirectory = new File(outputDirectory, Constants.DEBUG_DIRECTORY);
        this.rawImagesDirectory = new File(outputDirectory, Constants.RAW_DIRECTORY);
        this.processedDirectory = new File(outputDirectory, Constants.PROCESSED_DIRECTORY);
        this.generateDebugImages = generateDebugImages;
        this.spectrumDetectionThreshold = spectrumDetectionThreshold;
    }

    public void addEventListener(ProcessingEventListener listener) {
        progressEventListeners.add(listener);
    }

    public void removeEventListener(ProcessingEventListener listener) {
        progressEventListeners.remove(listener);
    }

    public void process() {
        broadcast(new ProcessingStartEvent(System.nanoTime()));
        var converter = ImageUtils.createImageConverter();
        var detector = new MagnitudeBasedSunEdgeDetector(converter);
        try (SerFileReader reader = SerFileReader.of(serFile)) {
            Files.createDirectories(rootDirectory.toPath());
            Files.createDirectories(debugDirectory.toPath());
            Files.createDirectories(rawImagesDirectory.toPath());
            Files.createDirectories(processedDirectory.toPath());
            ImageGeometry geometry = reader.header().geometry();
            var frameCount = reader.header().frameCount();
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
        var out = new ByteArrayOutputStream();
        var s = new PrintWriter(out);
        ex.printStackTrace(s);
        s.flush();
        String trace = out.toString();
        LOGGER.error("Error while processing\n{}", trace);
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
        reader.seekFrame(start);
        int newHeight = end - start;
        var outputBuffer = new float[width * newHeight];
        broadcast(OutputImageDimensionsDeterminedEvent.of("raw", width, newHeight));
        LOGGER.info("Starting reconstruction...");
        performImageReconstruction(converter, reader, start, end, geometry, width, height, outputBuffer);
        var rawImage = new ImageWrapper32(width, newHeight, outputBuffer);
        try (var executor = ParallelExecutor.newExecutor()) {
            executor.setExceptionHandler(this::broadcastError);
            var workflow = new ProcessingWorkflow(
                    this,
                    rawImagesDirectory,
                    debugDirectory,
                    processedDirectory,
                    executor,
                    rawImage,
                    width,
                    newHeight,
                    outputBuffer,
                    generateDebugImages,
                    fps.orElse(null)
            );
            workflow.start();
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
    }

    private void performImageReconstruction(ImageConverter<float[]> converter, SerFileReader reader, int start, int end, ImageGeometry geometry, int width, int height, float[] outputBuffer) {
        try (var executor = ParallelExecutor.newExecutor()) {
            executor.setExceptionHandler(this::broadcastError);
            var fallbackPolynomial = new AtomicReference<DoubleTriplet>(null);
            for (int i = start, j = 0; i < end; i++, j += width) {
                var original = converter.createBuffer(geometry);
                // The converter makes sure we only have a single channel
                converter.convert(i, reader.currentFrame().data(), geometry, original);
                reader.nextFrame();
                int frameId = i;
                int offset = j;
                executor.submit(() -> {
                    var analyzer = new SpectrumFrameAnalyzer(width, height, spectrumDetectionThreshold, 5000d);
                    processSingleFrame(width, height, outputBuffer, analyzer, offset, frameId, original, fallbackPolynomial.get());
                    analyzer.findDistortionPolynomial().ifPresent(fallbackPolynomial::set);
                });
            }
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        LOGGER.info("Reconstruction done. Generating images...");
    }

    private void processSingleFrame(int width,
                                    int height,
                                    float[] outputBuffer,
                                    SpectrumFrameAnalyzer analyzer,
                                    int offset,
                                    int frameId,
                                    float[] buffer,
                                    DoubleTriplet fallbackPolynomial) {
        analyzer.analyze(buffer);
        var polynomial = analyzer.findDistortionPolynomial().or(() -> Optional.ofNullable(fallbackPolynomial));
        polynomial.ifPresent(p -> {
                    var fun = p.asPolynomial();
                    showCorrectedImageForDebugging(width, height, buffer, analyzer, frameId, p);
                    double[] line = new double[width];
                    int lastY = 0;
                    for (int x = 0; x < width; x++) {
                        // To reconstruct the image, we use the polynom to find which pixel to use
                        double yd = fun.applyAsDouble(x);
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
                    broadcast(new PartialReconstructionEvent(new ImageLine(offset / width, line)));
                }
        );
        if (polynomial.isEmpty()) {
            LOGGER.warn("Unable to find spectral line in frame {}", frameId);
        }
    }

    private void showCorrectedImageForDebugging(int width,
                                                int height,
                                                float[] original,
                                                SpectrumFrameAnalyzer analyzer,
                                                int frameId,
                                                DoubleTriplet p) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Distortion polynomial of frame {} : [{}, {}, {}]", frameId, p.a(), p.b(), p.c());
        }
        if (generateDebugImages) {
            var creator = new SpectralLineFrameImageCreator(
                    frameId, analyzer, original, width, height
            );
            String fileName = String.format("%06d-corrected.png", frameId);
            creator.generateDebugImage(new File(debugDirectory, fileName));
        }
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
            }
        }
    }

}
