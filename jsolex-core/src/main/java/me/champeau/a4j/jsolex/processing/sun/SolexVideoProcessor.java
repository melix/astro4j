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

import me.champeau.a4j.jsolex.processing.event.AverageImageComputedEvent;
import me.champeau.a4j.jsolex.processing.event.DebugEvent;
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
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
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.InvalidExpression;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.workflow.DefaultImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.DiscardNonRequiredImages;
import me.champeau.a4j.jsolex.processing.sun.workflow.DopplerSupport;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitterFactory;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.NamingStrategyAwareImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.NoOpImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ProcessingWorkflow;
import me.champeau.a4j.jsolex.processing.sun.workflow.RenamingImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowResults;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;

public class SolexVideoProcessor implements Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);

    private final Set<ProcessingEventListener> progressEventListeners = new HashSet<>();

    private final File serFile;
    private final int sequenceNumber;
    private final LocalDateTime processingDate;
    private final boolean batchMode;
    private final Path outputDirectory;
    private ProcessParams processParams;

    public SolexVideoProcessor(File serFile, Path outputDirectory, int sequenceNumber, ProcessParams processParametersProvider, LocalDateTime processingDate, boolean batchMode) {
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
        this.sequenceNumber = sequenceNumber;
        this.processParams = processParametersProvider;
        this.processingDate = processingDate;
        this.batchMode = batchMode;
    }

    public void addEventListener(ProcessingEventListener listener) {
        progressEventListeners.add(listener);
    }

    public void process() {
        if (processParams.extraParams().autosave()) {
            File configFile = outputDirectory.resolve("config.json").toFile();
            processParams.saveTo(configFile);
        }
        broadcast(ProcessingStartEvent.of(System.nanoTime(), processParams));
        var converter = ImageUtils.createImageConverter(processParams.videoParams().colorMode());
        var detector = new MagnitudeBasedSunEdgeDetector(converter, this);
        AtomicInteger frameCountRef = new AtomicInteger();
        AtomicReference<Header> headerRef = new AtomicReference<>();
        AtomicReference<Double> fpsRef = new AtomicReference<>();
        BackgroundOperations.exclusiveIO(() -> {
            try (SerFileReader reader = SerFileReader.of(serFile)) {
                var header = reader.header();
                broadcast(new VideoMetadataEvent(header));
                maybeUpdateProcessParams(header);
                ImageGeometry geometry = header.geometry();
                var frameCount = header.frameCount();
                var dateTime = header.metadata().utcDateTime().toLocalDateTime();
                LOGGER.info(message("ser.file.date"), dateTime);
                LOGGER.info(message("ser.file.contains"), frameCount);
                LOGGER.info(message("color.mode.geometry"), geometry.colorMode(), geometry.getBytesPerPixel(), geometry.pixelDepthPerPlane());
                LOGGER.info(message("width.height"), geometry.width(), geometry.height());
                LOGGER.info(message("solar.parameters"), SolarParametersUtils.computeSolarParams(dateTime));
                LOGGER.info(message("computing.average.image.limb.detect"));
                // We use the IO executor to make sure we only read as single SER file at a time
                detector.detectEdges(reader);
                frameCountRef.set(frameCount);
                headerRef.set(header);
                fpsRef.set(reader.estimateFps().orElse(null));
            } catch (Exception e) {
                broadcastError(e);
            }
        });
        var header = headerRef.get();
        if (header != null) {
            var averageImage = detector.getAverageImage();
            detector.ifEdgesDetected((start, end) -> {
                LOGGER.info(message("sun.edges.detected"), start, end);
                generateImages(converter, header, fpsRef.get(), serFile, Math.max(0, start - 40), Math.min(end + 40, frameCountRef.get()) - 1, averageImage);
            }, () -> {
                LOGGER.info(message("sun.edges.detected.full"));
                generateImages(converter, header, fpsRef.get(), serFile, 0, frameCountRef.get() - 1, averageImage);
            });
        }
    }

    private void maybeUpdateProcessParams(Header header) {
        if (batchMode) {
            processParams = processParams.withExtraParams(processParams.extraParams().withAutosave(true)).withObservationDetails(processParams.observationDetails().withDate(header.metadata().utcDateTime()));
        }
    }

    private void broadcastError(Throwable ex) {
        String trace = logError(ex);
        broadcast(new NotificationEvent(new Notification(Notification.AlertType.ERROR, message("unexpected.error"), message("error.during.processing"), trace)));
    }

    private void generateImages(ImageConverter<float[]> converter, Header header, Double fps, File serFile, int start, int end, float[] averageImage) {
        List<WorkflowState> imageList = new ArrayList<>();
        var imageNamingStrategy = new FileNamingStrategy(processParams.extraParams().fileNamePattern(), processParams.extraParams().datetimeFormat(), processParams.extraParams().dateFormat(), processingDate, header);
        var baseName = serFile.getName().substring(0, serFile.getName().lastIndexOf("."));
        var geometry = header.geometry();
        int width = geometry.width();
        int height = geometry.height();
        int newHeight = end - start;
        broadcast(OutputImageDimensionsDeterminedEvent.of(Constants.TYPE_RAW, width, newHeight));
        createWorkflowStateSteps(imageList, width, newHeight);
        var internalShifts = processParams.requestedImages().internalPixelShifts();
        if (!internalShifts.isEmpty()) {
            for (WorkflowState state : imageList) {
                if (internalShifts.contains(state.pixelShift())) {
                    state.setInternal(true);
                }
            }
        }
        var maybePolynomial = findPolynomial(width, height, averageImage, imageNamingStrategy);
        if (maybePolynomial.isPresent()) {
            var polynomial = maybePolynomial.get();
            var avgImage = new Image(width, height, averageImage);
            var leftBorder = Math.max(0, start);
            var rightBorder = Math.min(end, width);
            if (leftBorder >= rightBorder) {
                // unreliable detection which happens on some SER files
                leftBorder = 0;
                rightBorder = width;
            }
            broadcast(new AverageImageComputedEvent(new AverageImageComputedEvent.AverageImage(avgImage, polynomial, leftBorder, rightBorder, processParams.spectrumParams().ray(), processParams.observationDetails())));
            BackgroundOperations.exclusiveIO(() -> {
                try (var reader = SerFileReader.of(serFile)) {
                    performImageReconstruction(converter, reader, start, end, geometry, width, height, polynomial, imageList.toArray(new WorkflowState[0]));
                } catch (Exception e) {
                    throw new ProcessingException(e);
                }
            });
            imageList.stream().parallel().forEach(state -> {
                ImageWrapper32 rotated;
                var recon = new Image(width, newHeight, state.reconstructed());
                var rotateLeft = ImageMath.newInstance().rotateLeft(recon);
                rotated = ImageWrapper32.fromImage(rotateLeft, createMetadata(processParams));
                TransformationHistory.recordTransform(rotated, message("rotate.left"));
                maybePerformFlips(rotated);
                state.setImage(rotated);
            });
            var fitting = performEllipseFitting(imageList, (broadcaster, kind, outputDirectory) -> {
                ImageEmitter emitter = new DefaultImageEmitter(broadcaster, outputDirectory.toFile());
                emitter = new NamingStrategyAwareImageEmitter(emitter, imageNamingStrategy, sequenceNumber, kind, baseName);
                return new DiscardNonRequiredImages(emitter, processParams.requestedImages().images());
            });
            IntStream.range(0, imageList.size()).mapToObj(i -> new Object() {
                private final WorkflowState state = imageList.get(i);
                private final int step = i;
            }).parallel().forEach(o -> {
                var state = o.state;
                var step = o.step;
                var imageEmitterFactory = new ProcessAwareImageEmitterFactory(state, imageNamingStrategy, baseName);
                state.recordResult(WorkflowResults.MAIN_ELLIPSE_FITTING, fitting);
                var workflow = new ProcessingWorkflow(this, outputDirectory, imageList, step, processParams, fps, imageEmitterFactory, header);
                workflow.start();
            });
            var runnables = new ArrayList<Runnable>();
            if (processParams.requestedImages().isEnabled(GeneratedImageKind.DOPPLER)) {
                runnables.add(() -> {
                    var imageEmitterFactory = new ProcessAwareImageEmitterFactory(imageList.get(0), imageNamingStrategy, baseName);
                    var producer = new DopplerSupport(processParams, imageList, imageEmitterFactory.newEmitter(this, "processed", outputDirectory));
                    producer.produceDopplerImage();
                });
            }
            runnables.add(() -> {
                var mathImages = processParams.requestedImages().mathImages();
                generateImageMaths(imageNamingStrategy, baseName, imageList, mathImages);
            });
            runnables.stream()
                .parallel()
                .forEach(Runnable::run);
        } else {
            LOGGER.error(message("unable.find.spectral.line"));
        }
        Ellipse ellipse = null;
        ImageStats imageStats = null;
        var images = new HashMap<Double, ImageWrapper>();
        for (WorkflowState workflowState : imageList) {
            var result = workflowState.findResult(WorkflowResults.GEOMETRY_CORRECTION);
            if (result.isPresent() && result.get() instanceof GeometryCorrector.Result geo) {
                images.put(workflowState.pixelShift(), FileBackedImage.wrap(geo.corrected()));
                ellipse = geo.correctedCircle();
                imageStats = new ImageStats(geo.blackpoint());
            } else {
                Map<Class<?>, Object> metadata = ellipse != null ? MutableMap.of(Ellipse.class, ellipse) : MutableMap.of();
                images.put(workflowState.pixelShift(), FileBackedImage.wrap(new ImageWrapper32(workflowState.width(), workflowState.height(), workflowState.reconstructed(), metadata)));
            }
        }
        broadcast(ProcessingDoneEvent.of(System.nanoTime(), images, createCustomImageEmitter(imageNamingStrategy, baseName), ellipse, imageStats));
    }

    private void generateImageMaths(FileNamingStrategy imageNamingStrategy, String baseName, List<WorkflowState> imageList, ImageMathParams mathImages) {
        if (!mathImages.scriptFiles().isEmpty()) {
            var images = new HashMap<Double, ImageWrapper32>();
            Ellipse ellipse = null;
            ImageStats imageStats = null;
            for (WorkflowState workflowState : imageList) {
                var result = workflowState.findResult(WorkflowResults.GEOMETRY_CORRECTION);
                if (result.isPresent() && result.get() instanceof GeometryCorrector.Result geo) {
                    images.put(workflowState.pixelShift(), geo.corrected());
                    if (ellipse == null) {
                        ellipse = geo.correctedCircle();
                    }
                    if (imageStats == null) {
                        imageStats = new ImageStats(geo.blackpoint());
                    }
                } else {
                    Map<Class<?>, Object> metadata = ellipse != null ? MutableMap.of(Ellipse.class, ellipse) : MutableMap.of();
                    images.put(workflowState.pixelShift(), new ImageWrapper32(workflowState.width(), workflowState.height(), workflowState.reconstructed(), metadata));
                }
            }
            var emitter = createCustomImageEmitter(imageNamingStrategy, baseName);
            var circle = ellipse;
            ImageStats finalImageStats = imageStats;
            mathImages.scriptFiles().stream().parallel().forEach(scriptFile -> {
                broadcast(ProgressEvent.of(0, "Running script " + scriptFile.getName()));
                var context = createMetadata(processParams);
                if (circle != null) {
                    context.put(Ellipse.class, circle);
                }
                if (finalImageStats != null) {
                    context.put(ImageStats.class, finalImageStats);
                }
                var scriptRunner = new DefaultImageScriptExecutor(images::get, Collections.unmodifiableMap(context), this);
                try {
                    var result = scriptRunner.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                    ImageMathScriptExecutor.render(result, emitter);
                    if (!result.invalidExpressions().isEmpty()) {
                        var sb = new StringBuilder();
                        for (InvalidExpression expression : result.invalidExpressions()) {
                            LOGGER.error("Found invalid expression {} ({}): {}", expression.label(), expression.expression(), expression.error());
                            sb.append("Found invalid expression %s (%s): %s%n".formatted(expression.label(), expression.expression(), expression.error())).append("\n");
                        }
                        broadcast(new NotificationEvent(new Notification(Notification.AlertType.ERROR, message("invalid.expressions"), message("invalid.expressions"), sb.toString())));
                    }
                    broadcast(new ScriptExecutionResultEvent(result));
                } catch (IOException e) {
                    throw new ProcessingException(e);
                } finally {
                    broadcast(ProgressEvent.of(1.0, "Running script " + scriptFile.getName()));
                }
            });
        }
    }

    public static Map<Class<?>, Object> createMetadata(ProcessParams processParams) {
        Map<Class<?>, Object> context = new HashMap<>();
        context.put(ProcessParams.class, processParams);
        context.put(SolarParameters.class, SolarParametersUtils.computeSolarParams(processParams.observationDetails().date().toLocalDateTime()));
        return context;
    }

    private ImageEmitter createCustomImageEmitter(FileNamingStrategy imageNamingStrategy, String baseName) {
        return new NamingStrategyAwareImageEmitter(new DefaultImageEmitter(this, outputDirectory.toFile()), imageNamingStrategy, sequenceNumber, Constants.TYPE_CUSTOM, baseName);
    }

    private EllipseFittingTask.Result performEllipseFitting(List<WorkflowState> imageList, ImageEmitterFactory imageEmitterFactory) {
        var selected = imageList.stream().sorted(Comparator.comparing(WorkflowState::pixelShift)).map(state -> {
            var ellipseFittingTask = new EllipseFittingTask(this, state::image, processParams, imageEmitterFactory.newEmitter(this, Constants.TYPE_DEBUG, outputDirectory));
            try {
                return Optional.of(ellipseFittingTask.call());
            } catch (Exception e) {
                return Optional.<EllipseFittingTask.Result>empty();
            }
        }).filter(Optional::isPresent).map(Optional::get).findFirst();
        if (selected.isEmpty()) {
            LOGGER.error("Unable to perform ellipse regression");
            return null;
        }
        return selected.get();
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
            TransformationHistory.recordTransform(rotated, message("flip"));
        }
    }

    private void createWorkflowStateSteps(List<WorkflowState> imageList, int width, int newHeight) {
        var list = processParams.requestedImages().pixelShifts().stream().map(i -> WorkflowState.prepare(width, newHeight, i)).toList();
        var detectionShift = processParams.spectrumParams().pixelShift() - 6;
        if (list.stream().noneMatch(s -> s.pixelShift() == detectionShift)) {
            // add an internal state used for edge detection only
            var internalState = WorkflowState.prepare(width, newHeight, detectionShift);
            internalState.setInternal(true);
            list = new ArrayList<>(list);
            list.add(internalState);
        }
        imageList.addAll(list);
    }

    private void performImageReconstruction(ImageConverter<float[]> converter, SerFileReader reader, int start, int end, ImageGeometry geometry, int width, int height, DoubleUnaryOperator polynomial, WorkflowState... images) {
        LOGGER.info(message("starting.reconstruction"));
        reader.seekFrame(start);
        LOGGER.info(message("distortion.polynomial"), polynomial);
        reader.seekFrame(start);
        int totalLines = end - start;
        try (var executor = Executors.newFixedThreadPool(Math.min(32, 8 * Runtime.getRuntime().availableProcessors()))) {
            for (int i = start, j = 0; i < end; i++, j += width) {
                var currentFrame = reader.currentFrame().data().array();
                byte[] copy = new byte[currentFrame.length];
                System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                reader.nextFrame();
                var original = converter.createBuffer(geometry);
                int offset = j;
                int frameId = i;
                executor.submit(() -> {
                    // The converter makes sure we only have a single channel
                    converter.convert(frameId, ByteBuffer.wrap(copy), geometry, original);
                    Arrays.stream(images).parallel().forEach(state ->
                        processSingleFrame(state.isInternal(), width, height, state.reconstructed(), offset, original, polynomial, state.pixelShift(), totalLines)
                    );
                });
            }
        }
        LOGGER.info(message("processing.done.generate.images"));
    }

    private Optional<DoubleUnaryOperator> findPolynomial(int width, int height, float[] averageImage, FileNamingStrategy imageNamingStrategy) {
        SpectrumFrameAnalyzer analyzer = new SpectrumFrameAnalyzer(width, height, 5000d);
        analyzer.analyze(averageImage);
        var result = analyzer.findDistortionPolynomial();
        if (processParams.extraParams().generateDebugImages()) {
            var emitter = new DiscardNonRequiredImages(
                new NamingStrategyAwareImageEmitter(new DefaultImageEmitter(this, outputDirectory.toFile()), imageNamingStrategy, 0, Constants.TYPE_DEBUG, serFile.getName().substring(0, serFile.getName().lastIndexOf("."))),
                processParams.requestedImages().images());
            emitter.newColorImage(GeneratedImageKind.DEBUG, message("average"), "average", width, height, () -> {
                var rgb = new SpectralLineFrameImageCreator(analyzer, averageImage, width, height).generateDebugImage();
                return new float[][]{rgb.r(), rgb.g(), rgb.b()};
            });
        }
        return result;
    }

    private void processSingleFrame(boolean internal, int width, int height, float[] outputBuffer, int offset, float[] buffer, DoubleUnaryOperator p, double pixelShift, int totalLines) {
        double[] line = new double[width];
        int lastY = 0;
        for (int x = 0; x < width; x++) {
            // To reconstruct the image, we use the polynom to find which pixel to use
            double yd = p.applyAsDouble(x) + pixelShift;
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
        if (!internal) {
            broadcast(new PartialReconstructionEvent(new ImageLine(pixelShift, offset / width, totalLines, line, processParams.extraParams().generateDebugImages() || processParams.requestedImages().isEnabled(GeneratedImageKind.RECONSTRUCTION))));
        }
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
            } else if (event instanceof FileGeneratedEvent e) {
                listener.onFileGenerated(e);
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
            } else if (event instanceof VideoMetadataEvent e) {
                listener.onVideoMetadataAvailable(e);
            } else if (event instanceof ScriptExecutionResultEvent e) {
                listener.onScriptExecutionResult(e);
            } else if (event instanceof AverageImageComputedEvent e) {
                listener.onAverageImageComputed(e);
            }
        }
    }

    private class ProcessAwareImageEmitterFactory implements ImageEmitterFactory {
        private final WorkflowState state;
        private final FileNamingStrategy imageNamingStrategy;
        private final String baseName;

        public ProcessAwareImageEmitterFactory(WorkflowState state, FileNamingStrategy imageNamingStrategy, String baseName) {
            this.state = state;
            this.imageNamingStrategy = imageNamingStrategy;
            this.baseName = baseName;
        }

        @Override
        public ImageEmitter newEmitter(Broadcaster broadcaster, String kind, Path outputDirectory) {
            if (state.isInternal()) {
                return new NoOpImageEmitter();
            }
            ImageEmitter emitter = new DefaultImageEmitter(broadcaster, outputDirectory.toFile());
            var shift = state.pixelShift();
            if ("doppler".equals(kind)) {
                emitter = new NamingStrategyAwareImageEmitter(emitter, imageNamingStrategy, sequenceNumber, kind, baseName);
            } else {
                emitter = new NamingStrategyAwareImageEmitter(new RenamingImageEmitter(emitter, name -> {
                    if (name.toLowerCase(Locale.US).contains("doppler")) {
                        return name;
                    }
                    var suffix = shift == processParams.spectrumParams().pixelShift() ? "" : " (" + (shift == Constants.CONTINUUM_SHIFT ? "continuum" : shift) + ")";
                    return name + suffix;
                }, name -> {
                    if (name.toLowerCase(Locale.US).contains("doppler")) {
                        return name;
                    }
                    var suffix = "_" + String.format(Locale.US, "%.2f", shift).replace('.', '_');
                    return name + suffix;
                }), imageNamingStrategy, sequenceNumber, kind, baseName);
            }
            return new DiscardNonRequiredImages(emitter, processParams.requestedImages().images());
        }
    }
}
