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

import me.champeau.a4j.jsolex.processing.event.DebugEvent;
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
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.DefaultImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitterFactory;
import me.champeau.a4j.jsolex.processing.sun.workflow.NamingStrategyAwareImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ProcessingWorkflow;
import me.champeau.a4j.jsolex.processing.sun.workflow.RenamingImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.StepFilteringImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowStep;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.tuples.DoubleTriplet;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

public class SolexVideoProcessor implements Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);

    private final Set<ProcessingEventListener> progressEventListeners = new HashSet<>();

    private final File serFile;
    private final ProcessParams processParams;
    private final boolean quickMode;
    private final Path outputDirectory;

    public SolexVideoProcessor(File serFile, Path outputDirectory, ProcessParams processParametersProvider, boolean quickMode) {
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
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
        if (processParams.debugParams().autosave()) {
            File configFile = outputDirectory.resolve("config.json").toFile();
            processParams.saveTo(configFile);
        }
        broadcast(new ProcessingStartEvent(System.nanoTime()));
        var converter = ImageUtils.createImageConverter(processParams.videoParams().colorMode());
        var detector = new MagnitudeBasedSunEdgeDetector(converter, this);
        try (SerFileReader reader = SerFileReader.of(serFile)) {
            var header = reader.header();
            ImageGeometry geometry = header.geometry();
            var frameCount = header.frameCount();
            LOGGER.info(message("ser.file.date"), header.metadata().utcDateTime());
            LOGGER.info(message("ser.file.contains"), frameCount);
            LOGGER.info(message("color.mode.geometry"), geometry.colorMode(), geometry.getBytesPerPixel(), geometry.pixelDepthPerPlane());
            LOGGER.info(message("width.height"), geometry.width(), geometry.height());
            LOGGER.info(message("computing.average.image.limb.detect"));
            detector.detectEdges(reader);
            var averageImage = detector.getAverageImage();
            detector.ifEdgesDetected((start, end) -> {
                LOGGER.info(message("sun.edges.detected"), start, end);
                generateImages(converter, reader, Math.max(0, start - 40), Math.min(end + 40, frameCount) - 1, averageImage);
            }, () -> {
                LOGGER.info(message("sun.edges.detected.full"));
                generateImages(converter, reader, 0, frameCount - 1, averageImage);
            });
        } catch (Exception e) {
            broadcastError(e);
        }
    }

    private void broadcastError(Throwable ex) {
        String trace = logError(ex);
        broadcast(new NotificationEvent(new Notification(Notification.AlertType.ERROR, message("unexpected.error"), message("error.during.processing"), trace)));
    }

    private void generateImages(ImageConverter<float[]> converter, SerFileReader reader, int start, int end, float[] averageImage) {
        var imageNamingStrategy = new FileNamingStrategy(
                processParams.debugParams().fileNamePattern(),
                LocalDateTime.now(),
                reader.header()
        );
        var geometry = reader.header().geometry();
        int width = geometry.width();
        int height = geometry.height();
        var fps = reader.estimateFps();
        int newHeight = end - start;
        broadcast(OutputImageDimensionsDeterminedEvent.of(Constants.TYPE_RAW, width, newHeight));
        List<WorkflowState> imageList = createWorkflowStateSteps(width, newHeight);
        LOGGER.info(message("starting.reconstruction"));
        var maybePolynomial = findPolynomial(width, height, processParams.spectrumParams().spectralLineDetectionThreshold(), averageImage, imageNamingStrategy);
        if (maybePolynomial.isPresent()) {
            var polynomial = maybePolynomial.get();
            performImageReconstruction(converter, reader, start, end, geometry, width, height, polynomial, imageList.toArray(new WorkflowState[0]));
            ProcessParams currentParams = processParams;
            for (int step = 0; step < imageList.size(); step++) {
                WorkflowState state = imageList.get(step);
                int finalStep = step;
                var imageEmitterFactory = new ImageEmitterFactory() {
                    @Override
                    public ImageEmitter newEmitter(Broadcaster broadcaster, ParallelExecutor executor, String kind, Path outputDirectory) {
                        var baseName = serFile.getName().substring(0, serFile.getName().lastIndexOf("."));
                        ImageEmitter emitter = new DefaultImageEmitter(broadcaster, executor, outputDirectory.toFile());
                        var shift = state.pixelShift();
                        if (finalStep == 0) {
                            return new NamingStrategyAwareImageEmitter(emitter,
                                    imageNamingStrategy,
                                    0,
                                    kind,
                                    baseName);
                        }
                        var suffix = " (" + (shift == 15 ? "continuum" : shift) + ")";
                        emitter = new NamingStrategyAwareImageEmitter(
                                new RenamingImageEmitter(emitter, title -> title + suffix, name -> name + suffix),
                                imageNamingStrategy,
                                0,
                                kind,
                                baseName);
                        return new StepFilteringImageEmitter(emitter, state.steps());
                    }
                };
                var rotateLeft = ImageMath.newInstance().rotateLeft(new Image(width, newHeight, state.reconstructed()));
                var rotated = ImageWrapper32.fromImage(rotateLeft);
                maybePerformFlips(rotated);
                state.setImage(rotated);
                ProcessingWorkflow workflow;
                try (var executor = ParallelExecutor.newExecutor()) {
                    executor.setExceptionHandler(this::broadcastError);
                    workflow = new ProcessingWorkflow(this, outputDirectory, executor, imageList, step, currentParams, fps.orElse(null), imageEmitterFactory);
                    workflow.start();
                } catch (Exception e) {
                    throw new ProcessingException(e);
                }
            }
        } else {
            LOGGER.error(message("unable.find.spectral.line"));
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
            return List.of(WorkflowState.prepare(width, newHeight, processParams.spectrumParams().pixelShift(), EnumSet.of(WorkflowStep.RAW_IMAGE, WorkflowStep.BANDING_CORRECTION)));
        }
        var dopplerShift = processParams.spectrumParams().dopplerShift();
        var imageList = processParams.spectrumParams().pixelShift() == 0 ? List.of(WorkflowState.prepare(width, newHeight, 0, EnumSet.allOf(WorkflowStep.class)), WorkflowState.prepare(width, newHeight, -dopplerShift), WorkflowState.prepare(width, newHeight, dopplerShift, WorkflowStep.DOPPLER_IMAGE), WorkflowState.prepare(width, newHeight, +15, WorkflowStep.STRECHED_IMAGE)) : List.of(WorkflowState.prepare(width, newHeight, processParams.spectrumParams().pixelShift(), EnumSet.allOf(WorkflowStep.class)));
        return imageList;
    }

    private void performImageReconstruction(ImageConverter<float[]> converter, SerFileReader reader, int start, int end, ImageGeometry geometry, int width, int height, DoubleTriplet polynomial, WorkflowState... images) {
        try (var executor = IOUtils.newExecutor(width * height * 4)) {
            executor.setExceptionHandler(this::broadcastError);
            reader.seekFrame(start);
            LOGGER.info(message("distortion.polynomial"), polynomial.a(), polynomial.b(), polynomial.c());
            reader.seekFrame(start);
            for (int i = start, j = 0; i < end; i++, j += width) {
                var original = converter.createBuffer(geometry);
                // The converter makes sure we only have a single channel
                converter.convert(i, reader.currentFrame().data(), geometry, original);
                reader.nextFrame();
                int offset = j;
                for (WorkflowState state : images) {
                    executor.submit(() -> processSingleFrame(width, height, state.reconstructed(), offset, original, polynomial, state.pixelShift()));
                }
            }
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        LOGGER.info(message("processing.done.generate.images"));
    }

    private Optional<DoubleTriplet> findPolynomial(int width, int height, double threshold, float[] averageImage, FileNamingStrategy imageNamingStrategy) {
        Optional<DoubleTriplet> result = Optional.empty();
        double t = threshold;
        SpectrumFrameAnalyzer analyzer = null;
        while (result.isEmpty() && t < 1.0d) {
            analyzer = new SpectrumFrameAnalyzer(width, height, t, 5000d);
            analyzer.analyze(averageImage);
            result = analyzer.findDistortionPolynomial();
            t = Math.min(1d, t + 0.10d);
            if (result.isEmpty()) {
                LOGGER.info("Sprectral line threshold too low, increasing to {}", String.format("%.2f", t));
            }
        }
        if (processParams.debugParams().generateDebugImages()) {
            var emitter = new NamingStrategyAwareImageEmitter(
                    new DefaultImageEmitter(this::broadcast, ParallelExecutor.newExecutor(), outputDirectory.toFile()),
                    imageNamingStrategy,
                    0,
                    Constants.TYPE_DEBUG,
                    serFile.getName().substring(0, serFile.getName().lastIndexOf(".")));
            SpectrumFrameAnalyzer finalAnalyzer = analyzer;
            emitter.newColorImage(WorkflowStep.AVERAGE_IMAGE, "average", "average", LinearStrechingStrategy.DEFAULT, width, height, () -> {
                var rgb = new SpectralLineFrameImageCreator(finalAnalyzer, averageImage, width, height).generateDebugImage();
                return new float[][]{rgb.r(), rgb.g(), rgb.b()};
            });
        }
        return result;
    }

    private void processSingleFrame(int width, int height, float[] outputBuffer, int offset, float[] buffer, DoubleTriplet p, int pixelShift) {

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
            if (Objects.requireNonNull(event) instanceof OutputImageDimensionsDeterminedEvent e) {
                listener.onOutputImageDimensionsDetermined(e);
            } else if (event instanceof PartialReconstructionEvent e) {
                listener.onPartialReconstruction(e);
            } else if (event instanceof ImageGeneratedEvent e) {
                listener.onImageGenerated(e);
            } else if (event instanceof NotificationEvent e) {
                listener.onNotification(e);
            } else if (event instanceof SuggestionEvent e) {
                listener.onSuggestion(e);
            } else if (event instanceof ProcessingStartEvent e) {
                listener.onProcessingStart(e);
            } else if (event instanceof ProcessingDoneEvent e) {
                listener.onProcessingDone(e);
            } else if (event instanceof ProgressEvent e) {
                listener.onProgress(e);
            } else if (event instanceof DebugEvent<?> e) {
                listener.onDebug(e);
            }
        }
    }

}
