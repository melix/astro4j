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
import me.champeau.a4j.jsolex.processing.event.FileGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.GenericMessage;
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
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.event.ReconstructionDoneEvent;
import me.champeau.a4j.jsolex.processing.event.ScriptExecutionResultEvent;
import me.champeau.a4j.jsolex.processing.event.SuggestionEvent;
import me.champeau.a4j.jsolex.processing.event.TrimmingParametersDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.VideoMetadataEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.InvalidExpression;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.params.EnhancementParams;
import me.champeau.a4j.jsolex.processing.params.ImageMathParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.RotationKind;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.spectrum.FlatCreator;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.detection.PhenomenaDetector;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.tasks.EllipseFittingTask;
import me.champeau.a4j.jsolex.processing.sun.tasks.GeometryCorrector;
import me.champeau.a4j.jsolex.processing.sun.workflow.DefaultImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.DiscardNonRequiredImages;
import me.champeau.a4j.jsolex.processing.sun.workflow.DopplerSupport;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.HeliumLineProcessor;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitterFactory;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.JaggingCorrection;
import me.champeau.a4j.jsolex.processing.sun.workflow.NamingStrategyAwareImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.NoOpImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.ProcessingWorkflow;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.RenamingImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.sun.workflow.TruncatedDisk;
import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowResults;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.jsolex.processing.util.SpectralLineFrameImageCreator;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.jsolex.processing.util.Wavelen;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.image.Image;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleQuadruplet;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static me.champeau.a4j.jsolex.processing.spectrum.FlatCreator.prepareFlatFromAverage;
import static me.champeau.a4j.jsolex.processing.sun.BackgroundRemoval.removeZeroPixels;
import static me.champeau.a4j.jsolex.processing.util.Constants.MAX_PIXEL_VALUE;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.logError;
import static me.champeau.a4j.ser.SerFileReader.JSOLEX_RECORDER;

public class SolexVideoProcessor implements Broadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);
    public static final int INMEMORY_BUFFERS_PER_CORE = 16;
    public static final int BATCH_LINES = 128;

    private final Set<ProcessingEventListener> progressEventListeners = new HashSet<>();

    private final File serFile;
    private final int sequenceNumber;
    private final LocalDateTime processingDate;
    private final boolean batchMode;
    private final int memoryRestrictionMultiplier;
    private final ProgressOperation rootOperation;
    private final Path outputDirectory;
    private ProcessParams processParams;
    private boolean binningIsReliable;
    private Redshifts redshifts;
    private ActiveRegions activeRegions;
    private DoubleUnaryOperator polynomial;
    private float[][] averageImage;
    private PixelShiftRange pixelShiftRange;
    private boolean ignoreIncompleteShifts;
    private boolean forceDetectActiveRegions;
    private EllipseFittingTask.Result cachedEllipse;

    public SolexVideoProcessor(File serFile,
                               Path outputDirectory,
                               int sequenceNumber,
                               ProcessParams processParametersProvider,
                               LocalDateTime processingDate,
                               boolean batchMode,
                               int memoryRestrictionMultiplier,
                               ProgressOperation rootOperation) {
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
        this.sequenceNumber = sequenceNumber;
        this.processParams = processParametersProvider;
        this.processingDate = processingDate;
        this.batchMode = batchMode;
        this.memoryRestrictionMultiplier = memoryRestrictionMultiplier;
        this.rootOperation = rootOperation;
    }

    public void setCachedEllipse(Ellipse cachedEllipse) {
        this.cachedEllipse = new EllipseFittingTask.Result(cachedEllipse, List.of());
    }

    public void setIgnoreIncompleteShifts(boolean ignoreIncompleteShifts) {
        this.ignoreIncompleteShifts = ignoreIncompleteShifts;
    }

    public void setAverageImage(float[][] averageImage) {
        this.averageImage = averageImage;
    }

    public void setPolynomial(DoubleUnaryOperator polynomial) {
        this.polynomial = polynomial;
    }

    public Redshifts getRedshifts() {
        return redshifts;
    }

    public void setRedshifts(Redshifts redshifts) {
        this.redshifts = redshifts;
    }

    public void setForceDetectActiveRegions(boolean forceDetectActiveRegions) {
        this.forceDetectActiveRegions = forceDetectActiveRegions;
    }

    public void addEventListener(ProcessingEventListener listener) {
        progressEventListeners.add(listener);
    }

    public void process() {
        if (tryReadMetadata()) {
            binningIsReliable = true;
        }
        if (processParams.extraParams().autosave()) {
            File configFile = outputDirectory.resolve("config.json").toFile();
            processParams.saveTo(configFile);
        }
        broadcast(ProcessingStartEvent.of(System.nanoTime(), processParams));
        var converter = ImageUtils.createImageConverter(processParams.videoParams().colorMode(), processParams.geometryParams().isSpectrumVFlip());
        var detector = new AverageImageCreator(converter, rootOperation, this);
        AtomicInteger frameCountRef = new AtomicInteger();
        AtomicReference<Header> headerRef = new AtomicReference<>();
        AtomicReference<Double> fpsRef = new AtomicReference<>();
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
            if (averageImage == null) {
                detector.computeAverageImage(reader);
            }
            frameCountRef.set(frameCount);
            headerRef.set(header);
            fpsRef.set(reader.estimateFps().orElse(null));
        } catch (Exception e) {
            broadcastError(e);
        }
        var header = headerRef.get();
        if (header != null) {
            if (averageImage == null) {
                averageImage = detector.getAverageImage();
            }
            generateImages(converter, header, fpsRef.get(), serFile);
        }
    }

    private boolean tryReadMetadata() {
        var md = CaptureSoftwareMetadataHelper.readSharpcapMetadata(serFile)
                .or(() -> CaptureSoftwareMetadataHelper.readFireCaptureMetadata(serFile));
        if (md.isPresent()) {
            var obsDetails = processParams.observationDetails();
            if (!obsDetails.forceCamera()) {
                obsDetails = obsDetails.withCamera(md.get().camera());
            }
            obsDetails = obsDetails.withBinning(md.get().binning());
            processParams = processParams.withObservationDetails(obsDetails);
            return true;
        }
        return false;
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

    private void generateImages(ImageConverter<float[][]> converter, Header header, Double fps, File serFile) {
        List<WorkflowState> imageList = new ArrayList<>();
        var imageNamingStrategy = new FileNamingStrategy(processParams.extraParams().fileNamePattern(), processParams.extraParams().datetimeFormat(), processParams.extraParams().dateFormat(), processingDate, header);
        var baseName = serFile.getName().substring(0, serFile.getName().lastIndexOf("."));
        var geometry = header.geometry();
        int width = geometry.width();
        int height = geometry.height();
        int newHeight = header.frameCount();
        int end = newHeight - 1;
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
        long imageSizeInBytes = width * newHeight * 4L * 3;
        long maxMemory = Runtime.getRuntime().maxMemory();
        int batchSize = (int) (Math.ceil(maxMemory / (4d * imageSizeInBytes * memoryRestrictionMultiplier)));
        checkAvailableDiskSpace(imageList, imageSizeInBytes);
        var analysis = analyzeAverageImage(width, height, averageImage, imageNamingStrategy, header);
        if (polynomial == null && processParams.geometryParams().isForcePolynomial()) {
            var forcedPolynomialString = processParams.geometryParams().forcedPolynomial();
            if (forcedPolynomialString.isPresent()) {
                var forcedPolynomial = DoubleQuadruplet.parsePolynomial(forcedPolynomialString.get());
                forcedPolynomial.ifPresentOrElse(doubleQuadruplet -> {
                            polynomial = doubleQuadruplet.asPolynomial();
                            LOGGER.info(message("forced.polynomial"));
                        },
                        () -> LOGGER.error(message("invalid.forced.polynomial"), forcedPolynomialString.get()));
            }
        }
        var maybePolynomial = Optional.ofNullable(polynomial).or(analysis::distortionPolynomial);
        if (maybePolynomial.isPresent()) {
            var polynomial = maybePolynomial.get();
            var analyzer = new SpectrumFrameAnalyzer(width, height, header.isJSolexTrimmedSer(), null);
            analyzer.analyze(averageImage);
            pixelShiftRange = PixelShiftRange.computePixelShiftRange(analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(end), height, polynomial);
            var continuumShift = processParams.spectrumParams().continuumShift();
            var minShift = pixelShiftRange.minPixelShift();
            var maxShift = pixelShiftRange.maxPixelShift();
            if (continuumShift > pixelShiftRange.maxPixelShift() || continuumShift < minShift) {
                double newContinuumShift;
                if (-continuumShift > minShift) {
                    newContinuumShift = -continuumShift;
                } else if (maxShift > 0) {
                    newContinuumShift = maxShift;
                } else {
                    newContinuumShift = minShift;
                }
                LOGGER.warn(String.format(message("invalid.continuum.shift"), newContinuumShift));
                processParams = processParams.withSpectrumParams(processParams.spectrumParams().withContinuumShift(newContinuumShift));
                if (imageList.stream().noneMatch(s -> s.pixelShift() == newContinuumShift)) {
                    var state = new WorkflowState(width, newHeight, newContinuumShift);
                    imageList.add(state);
                }
            }
            if (!ignoreIncompleteShifts) {
                imageList.removeIf(s -> s.pixelShift() < minShift || s.pixelShift() > maxShift);
            } else if (imageList.stream().anyMatch(s -> s.pixelShift() < minShift || s.pixelShift() > maxShift)) {
                LOGGER.warn(message("some.shifts.outside.range"));
            }
            var avgImage = new Image(width, height, averageImage);
            var pixelSize = processParams.observationDetails().pixelSize();
            if (processParams.spectrumParams().ray().equals(SpectralRay.AUTO) && pixelSize != null && pixelSize > 0) {
                var instrument = processParams.observationDetails().instrument();
                var candidates = new ArrayList<SpectrumAnalyzer.QueryDetails>();
                for (var line : SpectralRay.predefined()) {
                    if (line.wavelength().nanos() > 0 && !line.emission()) {
                        if (binningIsReliable) {
                            candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, processParams.observationDetails().binning(), instrument));
                        } else {
                            candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, 1, instrument));
                            candidates.add(new SpectrumAnalyzer.QueryDetails(line, pixelSize, 2, instrument));
                        }
                    }
                }
                var map = candidates
                        .stream()
                        .collect(Collectors.toMap(d -> d, details -> SpectrumAnalyzer.computeDataPoints(details, polynomial, 0, width, width, height, averageImage), (e1, e2) -> e1, LinkedHashMap::new));
                var bestMatch = SpectrumAnalyzer.findBestMatch(map);
                LOGGER.info(String.format(Locale.US, message("auto.detected.spectral.line"), bestMatch.line(), bestMatch.binning(), bestMatch.pixelSize()));
                var spectrumParams = processParams.spectrumParams();
                processParams = processParams.withSpectrumParams(spectrumParams.withRay(bestMatch.line()));
                var obsDetails = processParams.observationDetails();
                processParams = processParams.withObservationDetails(obsDetails.withBinning(bestMatch.binning()));
            }
            var heliumLineShift = computeHeliumLineShift();
            var canGenerateHeliumD3Images = heliumLineVisible(pixelShiftRange, heliumLineShift) && processParams.requestedImages().isEnabled(GeneratedImageKind.GEOMETRY_CORRECTED_PROCESSED);
            addPixelShiftsForRequestedByWavelength(width, newHeight, imageList);
            addPixelShiftsForAutoContinnum(canGenerateHeliumD3Images, width, newHeight, imageList, heliumLineShift);
            addPixelShiftsDynamicallyRequiredByScripts(converter, header, fps, serFile, imageList, end, width, newHeight, geometry, height, polynomial, imageNamingStrategy, baseName);
            broadcast(new AverageImageComputedEvent(new AverageImageComputedEvent.AverageImage(avgImage, polynomial, analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(width), processParams)));
            LOGGER.info(message("starting.reconstruction"));
            LOGGER.info(message("distortion.polynomial"), polynomial);
            var current = new AtomicInteger(0);
            var totalImages = imageList.size();
            var serFileReader = new AtomicReference<SerFileReader>();
            long sd = System.nanoTime();
            List<List<WorkflowState>> batches = batches(imageList, batchSize);
            LOGGER.info(message("memory.pressure"), memoryRestrictionMultiplier);
            if (batches.size() > 1) {
                LOGGER.info(message("reconstruction.batches"), batches.size(), batchSize);
            }

            for (int i = 0; i < batches.size(); i++) {
                var batch = batches.get(i);
                LOGGER.info(message("processing.batch"), i + 1, batches.size());
                try (var reader = serFileReader.get() != null ? serFileReader.get().reopen() : SerFileReader.of(serFile)) {
                    long serFileSize = Files.size(serFile.toPath());
                    long unitSd = System.nanoTime();
                    var outputs = performImageReconstruction(converter, reader, 0, end, geometry, width, height, polynomial, current, totalImages, batch.toArray(new WorkflowState[0]));
                    maybeProduceRedshiftDetectionImages(outputs.redshifts, width, height, reader, converter, geometry, polynomial, imageNamingStrategy, baseName);
                    if (redshifts == null && outputs.redshifts != null) {
                        redshifts = new Redshifts(outputs.redshifts);
                    }
                    if (activeRegions == null && outputs.activeRegions != null) {
                        activeRegions = outputs.activeRegions;
                    }
                    serFileReader.set(reader);
                    // Compute megabytes processed per second
                    long ed = System.nanoTime();
                    long time = ed - unitSd;
                    double mbps = (serFileSize / 1024.0 / 1024.0) / (time / 1_000_000_000.0);
                    LOGGER.info(String.format(Locale.US, message("reconstruction.mbs"), mbps));
                } catch (Exception e) {
                    throw new ProcessingException(e);
                }
                System.gc();
            }

            if (activeRegions != null) {
                for (WorkflowState state : imageList) {
                    state.reconstructed().metadata().put(ActiveRegions.class, activeRegions);
                }
            }
            try {
                long serFileSize = Files.size(serFile.toPath());
                long ed = System.nanoTime();
                long time = ed - sd;
                double mbps = (serFileSize / 1024.0 / 1024.0) / (time / 1_000_000_000.0);
                LOGGER.info(String.format(Locale.US, message("global.reconstruction.mbs"), mbps));
            } catch (IOException e) {
                // ignore
            }
            LOGGER.info(message("processing.done.generate.images"));
            broadcast(ReconstructionDoneEvent.of(serFileReader.get()));
            var generationOperation = rootOperation.createChild(message("generating.images"));
            broadcast(generationOperation);
            startWorkflow(header, fps, imageList, imageNamingStrategy, baseName);
            var runnables = new ArrayList<Runnable>();
            if (!imageList.isEmpty() && processParams.requestedImages().isEnabled(GeneratedImageKind.DOPPLER) || processParams.requestedImages().isEnabled(GeneratedImageKind.DOPPLER_ECLIPSE)) {
                runnables.add(() -> {
                    var imageEmitterFactory = createImageEmitterFactory(imageList, imageNamingStrategy, baseName);
                    var producer = new DopplerSupport(processParams, imageList, imageEmitterFactory.newEmitter(this, outputDirectory));
                    producer.produceDopplerImage();
                });
            }
            if (canGenerateHeliumD3Images) {
                runnables.add(() -> {
                    var imageEmitterFactory = createImageEmitterFactory(imageList, imageNamingStrategy, baseName);
                    var heliumLineProcessor = new HeliumLineProcessor(processParams, rootOperation, pixelShiftRange, imageList, heliumLineShift, imageEmitterFactory.newEmitter(this, outputDirectory), this);
                    heliumLineProcessor.process();
                });
            }
            runnables.add(() -> {
                var mathImages = processParams.requestedImages().mathImages();
                var missingShiftLock = new ReentrantLock();
                generateImageMaths(imageNamingStrategy, baseName, imageList, mathImages,
                        shift -> computeMissingImageShift(converter, header, fps, serFile, 0, end, shift, missingShiftLock, width, newHeight, geometry, height, polynomial, imageNamingStrategy, baseName),
                        minShift, maxShift,
                        header);
            });
            var progress = new AtomicInteger();
            runnables.stream()
                    .parallel()
                    .forEach(task -> {
                        broadcast(generationOperation.update(((double) progress.get()) / runnables.size()));
                        try {
                            task.run();
                        } finally {
                            broadcast(generationOperation.update(((double) progress.incrementAndGet()) / runnables.size()));
                        }
                    });
        } else {
            LOGGER.error(message("unable.find.spectral.line"));
        }
        EllipseFittingTask.Result mainEllipse = null;
        Ellipse ellipse = null;
        ImageStats imageStats = null;
        var images = new HashMap<Double, ImageWrapper>();
        for (WorkflowState workflowState : imageList) {
            var result = workflowState.findResult(WorkflowResults.GEOMETRY_CORRECTION);
            if (mainEllipse == null) {
                mainEllipse = (EllipseFittingTask.Result) workflowState.findResult(WorkflowResults.MAIN_ELLIPSE_FITTING).orElse(null);
            }
            if (result.isPresent() && result.get() instanceof GeometryCorrector.Result geo) {
                images.put(workflowState.pixelShift(), FileBackedImage.wrap(geo.corrected()));
                ellipse = geo.correctedCircle();
                imageStats = new ImageStats(geo.blackpoint());
            } else {
                Map<Class<?>, Object> metadata = ellipse != null ? MutableMap.of(Ellipse.class, ellipse) : MutableMap.of();
                images.put(workflowState.pixelShift(), new ImageWrapper32(workflowState.width(), workflowState.height(), workflowState.reconstructed().data(), metadata).wrap());
            }
        }
        if (!JSOLEX_RECORDER.equals(header.fileId()) && maybePolynomial.isPresent()) {
            imageList.stream()
                    .flatMap(workflowState -> workflowState.findResult(WorkflowResults.GEOMETRY_CORRECTION).stream())
                    .findFirst()
                    .ifPresent(result -> {
                        if (result instanceof GeometryCorrector.Result geo) {
                            var boundingBox = geo.originalEllipse().boundingBox();
                            var x1 = boundingBox.a();
                            var y1 = boundingBox.c();
                            var x2 = boundingBox.b();
                            var y2 = boundingBox.d();
                            // Fix the bounding box according to the left rotaton which happened
                            // before geometry correction
                            var firstFrame = (int) (newHeight - x2);
                            var lastFrame = (int) (newHeight - x1);
                            var marginFrames = 10 * (lastFrame - firstFrame) / 100;
                            var marginWidth = 10 * (y2 - y1) / 100;
                            var lambda0 = processParams.spectrumParams().ray().wavelength();
                            var observationDetails = processParams.observationDetails();
                            var instrument = observationDetails.instrument();
                            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(
                                    instrument,
                                    lambda0,
                                    observationDetails.pixelSize() * observationDetails.binning()
                            );
                            broadcast(TrimmingParametersDeterminedEvent.of(
                                    serFile,
                                    Math.max(0, firstFrame - marginFrames),
                                    Math.min(lastFrame + marginFrames, newHeight - 1),
                                    (int) Math.abs(pixelShiftRange.minPixelShift()),
                                    (int) Math.abs(pixelShiftRange.maxPixelShift()),
                                    (int) Math.max(0, y1 - marginWidth),
                                    (int) Math.min(y2 + marginWidth, width - 1),
                                    maybePolynomial.get(),
                                    processParams.geometryParams().isSpectrumVFlip(),
                                    newHeight,
                                    width,
                                    dispersion
                            ));
                        }
                    });

        }
        broadcast(ProcessingDoneEvent.of(System.nanoTime(),
                images,
                createCustomImageEmitter(imageNamingStrategy, baseName),
                mainEllipse == null ? null : mainEllipse.ellipse(),
                ellipse,
                imageStats,
                redshifts == null ? Collections.emptyList() : redshifts.redshifts(),
                polynomial,
                averageImage,
                processParams,
                pixelShiftRange,
                activeRegions == null ? 0 : activeRegions.regionList().size()
        ));
    }

    private void addPixelShiftsDynamicallyRequiredByScripts(ImageConverter<float[][]> converter, Header header, Double fps, File serFile, List<WorkflowState> imageList, int end, int width, int newHeight, ImageGeometry geometry, int height, DoubleUnaryOperator polynomial, FileNamingStrategy imageNamingStrategy, String baseName) {
        List<File> scripts = processParams.requestedImages().mathImages().scriptFiles();
        if (!scripts.isEmpty()) {
            // add missing shifts
            Map<Double, ImageWrapper> imageCache = new ConcurrentHashMap<>();
            for (var state : imageList) {
                state.findResult(WorkflowResults.GEOMETRY_CORRECTION).ifPresent(result -> imageCache.put(state.pixelShift(), ((GeometryCorrector.Result) result).corrected()));
            }
            var collectingExecutor = new DefaultImageScriptExecutor(
                    imageCache::get,
                    new HashMap<>(createMetadata(processParams, serFile.toPath(), pixelShiftRange, header))
            );
            var missingShifts = new TreeSet<Double>();
            for (File scriptFile : scripts) {
                try {
                    var shiftCollectionResult = collectingExecutor.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                    missingShifts.addAll(shiftCollectionResult.outputShifts());
                    missingShifts.addAll(shiftCollectionResult.internalShifts());
                } catch (IOException e) {
                    // ignore
                }
            }
            imageList.stream().map(WorkflowState::pixelShift).toList().forEach(missingShifts::remove);
            if (!missingShifts.isEmpty()) {
                imageList.addAll(missingShifts.stream().map(s -> {
                    var state = new WorkflowState(width, newHeight, s);
                    state.setInternal(true);
                    return state;
                }).toList());
            }
        }
    }

    private ProcessAwareImageEmitterFactory createImageEmitterFactory(List<WorkflowState> imageList, FileNamingStrategy imageNamingStrategy, String baseName) {
        var ref = imageList.stream()
                .filter(i -> i.pixelShift() == processParams.spectrumParams().pixelShift())
                .findFirst()
                .orElse(imageList.getFirst());
        return new ProcessAwareImageEmitterFactory(ref, imageNamingStrategy, baseName);
    }

    /*
     * In some rare cases, the image shift that is requested may not be available. This
     * can happen when a shift is derived from a dynamic shift value, such as in the
     * expression find_shift(...)+1.
     * In this case, we need to compute the missing image shift dynamically. Note that
     * for performance reasons it is important to avoid calling this method too often
     * since it will not perform reconstruction in parallel like with precomputed shifts.
     */
    private ImageWrapper computeMissingImageShift(ImageConverter<float[][]> converter,
                                                  Header header,
                                                  Double fps,
                                                  File serFile,
                                                  int start,
                                                  int end,
                                                  Double shift,
                                                  ReentrantLock missingShiftLock,
                                                  int width,
                                                  int newHeight,
                                                  ImageGeometry geometry,
                                                  int height,
                                                  DoubleUnaryOperator polynomial,
                                                  FileNamingStrategy imageNamingStrategy,
                                                  String baseName) {
        missingShiftLock.lock();
        try (var reader = SerFileReader.of(serFile)) {
            var state = new WorkflowState(width, newHeight, shift);
            state.setInternal(true);
            var states = new WorkflowState[]{state};
            var outputs = performImageReconstruction(converter, reader, start, end, geometry, width, height, polynomial, new AtomicInteger(), 1, states);
            maybeProduceRedshiftDetectionImages(outputs.redshifts, width, height, reader, converter, geometry, polynomial, imageNamingStrategy, baseName);
            startWorkflow(header, fps, List.of(states), imageNamingStrategy, baseName);
            var result = state.findResult(WorkflowResults.GEOMETRY_CORRECTION);
            if (result.isPresent() && result.get() instanceof GeometryCorrector.Result geo) {
                return geo.corrected();
            }
            return null;
        } catch (Exception e) {
            throw new ProcessingException(e);
        } finally {
            missingShiftLock.unlock();
        }
    }

    private ProgressOperation newOperation(String task) {
        return rootOperation.createChild(task);
    }

    private void startWorkflow(Header header, Double fps, List<WorkflowState> imageList, FileNamingStrategy imageNamingStrategy, String baseName) {
        var prepareOperation = newOperation(message("preparing.for.corrections"));
        broadcast(prepareOperation);
        var progress = new AtomicInteger(0);
        var imageEmitterFactory = createImageEmitterFactory(imageList, imageNamingStrategy, baseName);
        var initialFit = performInitialEllipseFit(imageList);
        imageList.stream()
                .parallel()
                .forEach(i -> {
                    double pg = ((double) progress.incrementAndGet()) / imageList.size();
                    prepareImageForCorrections(i, header, initialFit.orElse(null), imageEmitterFactory.newEmitter(this, outputDirectory));
                    broadcast(prepareOperation.update(pg));
                });
        var fitting = performEllipseFitting(imageList, imageEmitterFactory);
        IntStream.range(0, imageList.size()).parallel().forEach(i -> {
            var state = imageList.get(i);
            state.recordResult(WorkflowResults.MAIN_ELLIPSE_FITTING, fitting);
            // update metadata of images to determine if the ellipse crosses the truncation
            updateTruncatedDiskMetadata(fitting, state);
        });
        if (processParams.enhancementParams().artificialFlatCorrection()) {
            performFlatCorrection(imageList, fitting, processParams.enhancementParams());
        }
        var generationOperation = newOperation(message("generating.images"));
        progress.set(0);
        IntStream.range(0, imageList.size()).mapToObj(i -> new Object() {
            private final WorkflowState state = imageList.get(i);
            private final int step = i;
        }).parallel().forEach(o -> {
            broadcast(generationOperation.update(((double) progress.get()) / imageList.size()));
            var state = o.state;
            var step = o.step;
            var ief = new ProcessAwareImageEmitterFactory(state, imageNamingStrategy, baseName);
            var workflow = new ProcessingWorkflow(generationOperation, this, outputDirectory, imageList, step, processParams, fps, ief, serFile.toPath(), header);
            workflow.start();
            broadcast(generationOperation.update(((double) progress.incrementAndGet()) / imageList.size()));
        });
        broadcast(generationOperation.complete());
    }

    private Optional<Ellipse> performInitialEllipseFit(List<WorkflowState> imageList) {
        var operation = rootOperation.createChild(message("ellipse.fitting"));
        return imageList.stream()
                .sorted(Comparator.comparingDouble(WorkflowState::pixelShift))
                .map(state -> {
                    var task = new EllipseFittingTask(
                            this,
                            operation,
                            state::reconstructed,
                            processParams,
                            ImageEmitter.NO_OP
                    ) {
                        @Override
                        protected Image prepareForDetection(Image source, ImageMath imageMath) {
                            int width = source.width();
                            int height = source.height();
                            var downsampled = imageMath.rescale(source, width / 8, height / 8);
                            return imageMath.rescale(downsampled, width, height);
                        }
                    };
                    try {
                        var result = task.get();
                        if (result != null) {
                            return Optional.of(result.ellipse());
                        }
                    } catch (ProcessingException | IllegalArgumentException ex) {
                        // ignore
                    }
                    return Optional.<Ellipse>empty();
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static void updateTruncatedDiskMetadata(EllipseFittingTask.Result fitting, WorkflowState state) {
        var ellipse = fitting == null ? null : fitting.ellipse();
        if (ellipse != null) {
            var box = ellipse.boundingBox();
            var truncationDetails = state.getTruncationDetails();
            var min = truncationDetails.getMin();
            var max = truncationDetails.getMax();
            boolean truncated;
            if (truncationDetails.isHorizontal()) {
                truncated = box.a() < min || box.c() > max;
            } else {
                truncated = box.b() < min || box.d() > max;
            }
            state.image().metadata().put(TruncatedDisk.class, new TruncatedDisk(truncated));
        }
    }

    private static void performFlatCorrection(List<WorkflowState> imageList, EllipseFittingTask.Result fitting, EnhancementParams enhancementParams) {
        if (fitting == null) {
            return;
        }
        var ellipse = fitting.ellipse();
        var flatCorrector = new FlatCorrection(enhancementParams.artificialFlatCorrectionLoPercentile(), enhancementParams.artificialFlatCorrectionHiPercentile(), enhancementParams.artificialFlatCorrectionOrder());
        IntStream.range(0, imageList.size()).parallel().forEach(i -> {
            var state = imageList.get(i);
            var corrected = flatCorrector.correctImage(state.image(), flatCorrector.computeCorrectionFactors(state.image(), ellipse));
            state.setImage(corrected);
        });
    }

    private void prepareImageForCorrections(WorkflowState state, Header header, Ellipse ellipse, ImageEmitter emitter) {
        ImageWrapper32 rotated;
        ImageWrapper32 reconstructed = maybeRemoveZeroPixels(state.reconstructed());
        state.recordResult(WorkflowResults.RECONSTRUCTED, reconstructed);
        reconstructed = reconstructed.copy();
        reconstructed.metadata().put(PixelShift.class, new PixelShift(state.pixelShift()));
        performBandingCorrection(reconstructed, ellipse);
        if (ellipse != null) {
            var jaggingCorrection = new JaggingCorrection(state, processParams, emitter);
            jaggingCorrection.maybePerformJaggedEdgesCorrection(reconstructed, ellipse);
        }
        var recon = reconstructed.asImage();
        var rotateLeft = ImageMath.newInstance().rotateLeft(recon);
        var metadata = new HashMap<>(reconstructed.metadata());
        metadata.putAll(createMetadata(processParams, serFile.toPath(), pixelShiftRange, header));
        if (redshifts != null) {
            metadata.put(Redshifts.class, redshifts);
        }
        if (activeRegions != null) {
            var width = recon.width();
            metadata.put(ActiveRegions.class, activeRegions.transform(p -> new Point2D(p.y(), width - p.x())));
        }
        rotated = ImageWrapper32.fromImage(rotateLeft, metadata);
        TransformationHistory.recordTransform(rotated, message("rotate.left"));

        maybePerformFlips(rotated);
        rotated = maybePerformRotation(rotated, processParams.geometryParams().rotation());
        updateTruncationDetails(state, rotated);
        state.setImage(rotated);
    }

    private ImageWrapper32 maybeRemoveZeroPixels(ImageWrapper32 reconstructed) {
        for (var line : reconstructed.data()) {
            for (float v : line) {
                if (v == 0) {
                    LOGGER.warn(message("zero.pixel.warning"));
                    return removeZeroPixels(reconstructed);
                }
            }
        }
        return reconstructed;
    }

    private void performBandingCorrection(ImageWrapper32 reconstructed, Ellipse e) {
        var operation = rootOperation.createChild(message("banding.correction"));
        broadcast(operation);
        // banding correction works horizontally, so we need to temporarily rotate the image
        var imageMath = ImageMath.newInstance();
        var rotated = imageMath.rotateLeft(reconstructed.asImage());
        var passes = processParams.bandingCorrectionParams().passes();
        var bandSize = processParams.bandingCorrectionParams().width();
        var width = rotated.width();
        var height = rotated.height();
        var buffer = rotated.data();
        var ellipse = e != null ? e.rotate(-Math.PI/2, reconstructed.width(), reconstructed.height(), width, height) : null;
        for (int i = 0; i < passes; i++) {
            BandingReduction.reduceBanding(width, height, buffer, bandSize, ellipse);
            broadcast(operation.update((i + 1d / passes)));
        }
        broadcast(operation.complete());
        // rotate back to original orientation
        rotated = imageMath.rotateRight(rotated);
        for (int y = 0; y < reconstructed.height(); y++) {
            System.arraycopy(rotated.data()[y], 0, reconstructed.data()[y], 0, reconstructed.width());
        }
        TransformationHistory.recordTransform(reconstructed, "Banding reduction (band size: " + bandSize + " passes: " + passes + ")");
    }

    private void updateTruncationDetails(WorkflowState state, ImageWrapper32 rotated) {
        var td = new TruncationDetails();
        var hasHFlip = processParams.geometryParams().isHorizontalMirror();
        var hasVFlip = processParams.geometryParams().isVerticalMirror();
        td.setHorizontal(false); // rotate left
        var min = state.getTruncationDetails().getMin();
        if (min == null) {
            min = -1;
        }
        var max = state.getTruncationDetails().getMax();
        if (max == null) {
            max = rotated.height();
        }
        if (hasHFlip) {
            min = rotated.height() - min;
            max = rotated.height() - max;
        }
        if (hasVFlip) {
            min = rotated.width() - min;
            max = rotated.width() - max;
        }
        if (min > max) {
            var tmp = min;
            min = max;
            max = tmp;
        }
        td.setMin(min);
        td.setMax(max);
        state.setTruncationDetails(td);
    }

    private boolean heliumLineVisible(PixelShiftRange pixelShiftRange, Double heliumLineShift) {
        if (heliumLineShift == null) {
            return false;
        }
        return heliumLineShift >= pixelShiftRange.minPixelShift() && heliumLineShift <= pixelShiftRange.maxPixelShift();
    }

    private Double computeHeliumLineShift() {
        var pixelSize = processParams.observationDetails().pixelSize();
        var binning = processParams.observationDetails().binning();
        if (pixelSize == null || binning == null) {
            return null;
        }
        var pixelShift = SpectrumAnalyzer.computePixelShift(
                pixelSize,
                binning,
                processParams.spectrumParams().ray().wavelength(),
                SpectralRay.HELIUM_D3.wavelength(),
                processParams.observationDetails().instrument()
        );
        return pixelShift;
    }

    private void addPixelShiftsForRequestedByWavelength(int width, int newHeight, List<WorkflowState> imageList) {
        if (!processParams.requestedImages().requestedWaveLengths().isEmpty()) {
            var finalPS = processParams.observationDetails().pixelSize() == null ? 2.4 : processParams.observationDetails().pixelSize();
            var binning = processParams.observationDetails().binning() == null ? 1 : processParams.observationDetails().binning();
            var lambda0 = processParams.spectrumParams().ray().wavelength();
            var instrument = processParams.observationDetails().instrument();
            var implicitPixelShifts = processParams.requestedImages().requestedWaveLengths().stream()
                    .mapToDouble(wavelength -> SpectrumAnalyzer.computePixelShift(finalPS, binning, lambda0, Wavelen.ofAngstroms(wavelength), instrument))
                    .boxed()
                    .toList();
            var explicit = processParams.requestedImages().pixelShifts();
            var internal = processParams.requestedImages().internalPixelShifts();
            for (Double implicitPixelShift : implicitPixelShifts) {
                if (!explicit.contains(implicitPixelShift) && !internal.contains(implicitPixelShift)) {
                    var state = new WorkflowState(width, newHeight, implicitPixelShift);
                    state.setInternal(true);
                    imageList.add(state);
                }
            }
        }
    }

    private void addPixelShiftsForAutoContinnum(boolean canGenerateHeliumD3Images, int width, int newHeight, List<WorkflowState> imageList, Double heliumLineShift) {
        if (canGenerateHeliumD3Images || processParams.requestedImages().autoContinuum()) {
            var explicit = processParams.requestedImages().pixelShifts();
            var internal = processParams.requestedImages().internalPixelShifts();
            var min = pixelShiftRange.minPixelShift();
            var max = pixelShiftRange.maxPixelShift();
            var step = pixelShiftRange.step();
            for (var s = min; s <= max; s += step) {
                Double s2 = s;
                if (!explicit.contains(s) && !internal.contains(s) && imageList.stream().map(WorkflowState::pixelShift).noneMatch(s2::equals)) {
                    var state = new WorkflowState(width, newHeight, s);
                    state.setInternal(true);
                    imageList.add(state);
                }
            }
            if (canGenerateHeliumD3Images && heliumLineShift != null) {
                if (imageList.stream().map(WorkflowState::pixelShift).noneMatch(heliumLineShift::equals)) {
                    var state = new WorkflowState(width, newHeight, heliumLineShift);
                    state.setInternal(true);
                    imageList.add(state);
                }
            }
        }
    }

    private static void checkAvailableDiskSpace(List<WorkflowState> imageList, long imageSizeInBytes) {
        var requiredDiskSpaceBytes = (imageList.size() * 1.5 * imageSizeInBytes);
        var requiredDiskSpace = requiredDiskSpaceBytes / 1024 / 1024;
        String unit = "MB";
        if (requiredDiskSpace > 1024) {
            unit = "GB";
            requiredDiskSpace /= 1024;
        }
        LOGGER.info(message("processing.disk.requirements"), String.format("%.2f", requiredDiskSpace), unit);
        try {
            var path = TemporaryFolder.tempDir();
            var freespace = Files.getFileStore(path)
                    .getUsableSpace();
            if (freespace < requiredDiskSpace) {
                throw new ProcessingException(String.format(message("not.enough.disk.space"), path, String.format("%.2f", requiredDiskSpace), unit));
            }
        } catch (IOException ex) {
            // ignore
        }
    }

    public static <T> List<List<T>> batches(List<T> source, int length) {
        if (length <= 0) {
            return List.of(source);
        }
        int size = source.size();
        if (size == 0) {
            return List.of();
        }
        int fullChunks = (size - 1) / length;
        return IntStream.range(0, fullChunks + 1)
                .mapToObj(n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length))
                .toList();
    }

    private void maybeProduceRedshiftDetectionImages(List<RedshiftArea> redshifts,
                                                     int width,
                                                     int height,
                                                     SerFileReader reader,
                                                     ImageConverter<float[][]> converter,
                                                     ImageGeometry geometry,
                                                     DoubleUnaryOperator polynomial,
                                                     FileNamingStrategy fileNamingStrategy,
                                                     String baseName) {
        if (redshifts != null && !redshifts.isEmpty() && processParams.requestedImages().isEnabled(GeneratedImageKind.REDSHIFT)) {
            var analyzer = new SpectrumFrameAnalyzer(
                    width,
                    height,
                    reader.header().isJSolexTrimmedSer(),
                    null
            );
            var reversed = redshifts.reversed();
            for (var redshift : reversed) {
                var speed = redshift.kmPerSec();
                var buffer = converter.createBuffer(geometry);
                var frameNb = redshift.maxX();
                reader.seekFrame(frameNb);
                converter.convert(frameNb, reader.currentFrame().data(), geometry, buffer);
                var creator = new SpectralLineFrameImageCreator(analyzer, buffer, width, height);
                var image = creator.generateSpectrumImage(polynomial, true, rgb -> {
                    var spacing = rgb.spacing();
                    var w = rgb.width();
                    var h = rgb.height();
                    for (int x = redshift.y1(); x <= redshift.y2(); x++) {
                        var correctedX = width - x - 1;
                        for (int y = 0; y < h; y++) {
                            rgb.r()[y + spacing + height][correctedX] = MAX_PIXEL_VALUE;
                            rgb.g()[y + spacing + height][correctedX] = 0;
                            rgb.b()[y + spacing + height][correctedX] = 0;
                        }
                        var y = ((int) Math.round(rgb.polynomial().applyAsDouble(correctedX))) + redshift.relPixelShift();
                        if (y < 0 || x < 0 || y >= height || x >= width) {
                            continue;
                        }
                        rgb.r()[y][correctedX] = MAX_PIXEL_VALUE;
                        rgb.g()[y][correctedX] = 0;
                        rgb.b()[y][correctedX] = 0;
                    }
                    var draw = new ImageDraw(Map.of(), Broadcaster.NO_OP);
                    var copy = new RGBImage(w, 2 * h + spacing, rgb.r(), rgb.g(), rgb.b(), Map.of());
                    RGBImage color = (RGBImage) draw.drawOnImage(copy, (g, img) -> {
                        g.setColor(java.awt.Color.GREEN);
                        g.setFont(g.getFont().deriveFont(16f));
                        g.drawString("Frame " + frameNb + " shift " + redshift.relPixelShift(), 16, img.height() - 16);
                    });
                    System.arraycopy(color.r(), 0, rgb.r(), 0, color.r().length);
                    System.arraycopy(color.g(), 0, rgb.g(), 0, color.g().length);
                    System.arraycopy(color.b(), 0, rgb.b(), 0, color.b().length);
                });
                var targetFile = outputDirectory.resolve(fileNamingStrategy.render(sequenceNumber, "redshift", Constants.TYPE_PROCESSED, "redshift", baseName + "_" + redshift.id(), image));
                broadcast(new ImageGeneratedEvent(
                        new GeneratedImage(
                                GeneratedImageKind.REDSHIFT,
                                "Redshift %s (%.2f km/s)".formatted(redshift.id(), speed),
                                targetFile,
                                FileBackedImage.wrap(image),
                                message("individual.redshift.image.description")
                        )
                ));
                LOGGER.info(message("found.speed"), String.format("%.2f km/s", speed), redshift.x1(), redshift.y1(), redshift.x2(), redshift.y2(), redshift.relPixelShift());
            }
        }
    }

    private void generateImageMaths(FileNamingStrategy imageNamingStrategy,
                                    String baseName,
                                    List<WorkflowState> imageList,
                                    ImageMathParams mathImages,
                                    Function<Double, ImageWrapper> missingShiftSupplier,
                                    double minShift,
                                    double maxShift,
                                    Header header) {
        if (!mathImages.scriptFiles().isEmpty() && !imageList.isEmpty()) {
            var images = new HashMap<Double, ImageWrapper>();
            Ellipse ellipse = null;
            ImageStats imageStats = null;
            for (WorkflowState workflowState : imageList) {
                var result = workflowState.findResult(WorkflowResults.GEOMETRY_CORRECTION);
                double pixelShift = workflowState.pixelShift();
                if (result.isPresent() && result.get() instanceof GeometryCorrector.Result geo) {
                    images.put(pixelShift, geo.corrected());
                    if (ellipse == null) {
                        ellipse = geo.correctedCircle();
                    }
                    if (imageStats == null) {
                        imageStats = new ImageStats(geo.blackpoint());
                    }
                } else {
                    Map<Class<?>, Object> metadata = ellipse != null ? MutableMap.of(Ellipse.class, ellipse) : MutableMap.of();
                    var wrapper = new ImageWrapper32(workflowState.width(), workflowState.height(), workflowState.reconstructed().data(), metadata);
                    images.put(pixelShift, wrapper.wrap());
                    var rounded = Math.round(100d * pixelShift) / 100d;
                    if (!images.containsKey(rounded)) {
                        images.put(rounded, wrapper);
                    }
                }
            }
            var emitter = createCustomImageEmitter(imageNamingStrategy, baseName);
            var circle = ellipse;
            ImageStats finalImageStats = imageStats;
            var scriptsOperation = newOperation(message("running.scripts"));
            mathImages.scriptFiles().stream().parallel().forEach(scriptFile -> {
                broadcast(scriptsOperation.update(0, message("running.scripts") + " : " + scriptFile.getName()));
                var context = createMetadata(processParams, serFile.toPath(), pixelShiftRange, header);
                context.put(ImageEmitter.class, emitter);
                context.put(ProgressOperation.class, scriptsOperation);
                if (circle != null) {
                    context.put(Ellipse.class, circle);
                }
                if (finalImageStats != null) {
                    context.put(ImageStats.class, finalImageStats);
                }
                var scriptRunner = new DefaultImageScriptExecutor(shift -> {
                    double lookup = shift;
                    if (lookup < minShift) {
                        LOGGER.warn("Cropping window doesn't allow use of shift {}, replacing with {}", lookup, minShift);
                        lookup = minShift;
                    } else if (lookup > maxShift) {
                        LOGGER.warn("Cropping window doesn't allow use of shift {}, replacing with {}", lookup, maxShift);
                        lookup = maxShift;
                    }
                    var img = images.get(lookup);
                    if (img == null) {
                        // this can happen in situations where a shift is dynamic and cannot be computed
                        // in advance, for example with expression find_shift(5254) + 1
                        img = missingShiftSupplier.apply(lookup);
                        images.put(lookup, img);
                    }
                    return img;
                }, Collections.unmodifiableMap(context), this);
                try {
                    var result = scriptRunner.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
                    ImageMathScriptExecutor.render(result, emitter);
                    if (!result.invalidExpressions().isEmpty()) {
                        var sb = new StringBuilder();
                        for (InvalidExpression expression : result.invalidExpressions()) {
                            LOGGER.error("Found invalid expression {} ({}): {}", expression.label(), expression.expression(), expression.error().getMessage());
                            sb.append("Found invalid expression %s (%s): %s%n".formatted(expression.label(), expression.expression(), expression.error())).append("\n");
                        }
                        broadcast(new NotificationEvent(new Notification(Notification.AlertType.ERROR, message("invalid.expressions"), message("invalid.expressions"), sb.toString())));
                    }
                    broadcast(new ScriptExecutionResultEvent(result));
                } catch (IOException e) {
                    throw new ProcessingException(e);
                } finally {
                    broadcast(scriptsOperation.update(1.0, "Running script " + scriptFile.getName()));
                }
            });
        }
    }

    public static Map<Class<?>, Object> createMetadata(ProcessParams processParams, Path serFile, PixelShiftRange pixelShiftRange, Header header) {
        Map<Class<?>, Object> context = new HashMap<>();
        context.put(ProcessParams.class, processParams);
        context.put(SolarParameters.class, SolarParametersUtils.computeSolarParams(processParams.observationDetails().date().toLocalDateTime()));
        context.put(ReferenceCoords.class, new ReferenceCoords(List.of()));
        var file = serFile.toFile();
        context.put(SourceInfo.class, new SourceInfo(file.getName(), file.getParentFile().getName(), header.metadata().utcDateTime()));
        if (pixelShiftRange != null) {
            context.put(PixelShiftRange.class, pixelShiftRange);
        }
        return context;
    }

    private ImageEmitter createCustomImageEmitter(FileNamingStrategy imageNamingStrategy, String baseName) {
        return new NamingStrategyAwareImageEmitter(new DefaultImageEmitter(this, rootOperation, outputDirectory.toFile()), imageNamingStrategy, sequenceNumber, baseName);
    }

    private EllipseFittingTask.Result performEllipseFitting(List<WorkflowState> imageList, ImageEmitterFactory imageEmitterFactory) {
        if (cachedEllipse != null) {
            return cachedEllipse;
        }
        var selected = imageList.stream().sorted(Comparator.comparing(WorkflowState::pixelShift)).map(state -> {
            var ellipseFittingTask = new EllipseFittingTask(this, newOperation(message("ellipse.fitting")), state::image, processParams, imageEmitterFactory.newEmitter(this, outputDirectory));
            try {
                return Optional.ofNullable(ellipseFittingTask.call());
            } catch (Exception e) {
                return Optional.<EllipseFittingTask.Result>empty();
            }
        }).filter(Optional::isPresent).map(Optional::get).findFirst();
        if (selected.isEmpty()) {
            LOGGER.error(message("ellipse.fitting.failed"));
            return null;
        }
        EllipseFittingTask.Result result = selected.get();
        cachedEllipse = result;
        return result;
    }

    private void maybePerformFlips(ImageWrapper32 rotated) {
        var hflip = processParams.geometryParams().isHorizontalMirror();
        var vflip = processParams.geometryParams().isVerticalMirror();
        if (hflip || vflip) {
            var original = rotated.data();
            var width = rotated.width();
            var height = rotated.height();
            float[][] flipped = new float[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var nx = hflip ? width - x - 1 : x;
                    var ny = vflip ? height - y - 1 : y;
                    flipped[ny][nx] = original[y][x];
                }
            }
            rotated.findMetadata(Redshifts.class).ifPresent(redshifts -> {
                var rotatedRedshifts = redshifts.redshifts().stream()
                        .map(area -> {
                                    var x1 = area.x1();
                                    var x2 = area.x2();
                                    var y1 = area.y1();
                                    var y2 = area.y2();
                                    var maxX = area.maxX();
                                    var maxY = area.maxY();
                                    var pixelShift = area.pixelShift();
                                    var relPixelShift = area.relPixelShift();
                                    var kmPerSec = area.kmPerSec();
                                    if (hflip) {
                                        var tempX1 = width - x1 - 1;
                                        var tempX2 = width - x2 - 1;
                                        x1 = Math.min(tempX1, tempX2);
                                        x2 = Math.max(tempX1, tempX2);
                                        maxX = width - maxX - 1;
                                    }
                                    if (vflip) {
                                        var tempY1 = height - y1 - 1;
                                        var tempY2 = height - y2 - 1;
                                        y1 = Math.min(tempY1, tempY2);
                                        y2 = Math.max(tempY1, tempY2);
                                        maxY = height - maxY - 1;
                                    }
                                    return new RedshiftArea(area.id(), pixelShift, relPixelShift, kmPerSec, x1, y1, x2, y2, maxX, maxY);
                                }
                        ).toList();
                redshifts = new Redshifts(rotatedRedshifts);
                rotated.metadata().put(Redshifts.class, redshifts);
            });
            rotated.findMetadata(ActiveRegions.class).ifPresent(activeRegions -> {
                var flippedActiveRegions = activeRegions.transform(p -> {
                    var x = p.x();
                    var y = p.y();
                    if (hflip) {
                        x = width - x - 1;
                    }
                    if (vflip) {
                        y = height - y - 1;
                    }
                    return new Point2D(x, y);
                });
                rotated.metadata().put(ActiveRegions.class, flippedActiveRegions);
            });
            System.arraycopy(flipped, 0, original, 0, original.length);
            TransformationHistory.recordTransform(rotated, message("flip"));
        }
    }

    private ImageWrapper32 maybePerformRotation(ImageWrapper32 rotated, RotationKind kind) {
        if (kind == RotationKind.NONE) {
            return rotated;
        }

        var original = rotated.data();
        var width = rotated.width();
        var height = rotated.height();
        var newWidth = height;
        var newHeight = width;
        float[][] newData = new float[newHeight][newWidth];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (kind == RotationKind.LEFT) {
                    newData[width - x - 1][y] = original[y][x];
                } else {
                    newData[x][height - y - 1] = original[y][x];
                }
            }
        }

        var newMetadata = new HashMap<>(rotated.metadata());

        rotated.findMetadata(Redshifts.class).ifPresent(redshifts -> {
            var rotatedRedshifts = redshifts.redshifts().stream()
                    .map(area -> {
                        int x1 = area.x1(), x2 = area.x2();
                        int y1 = area.y1(), y2 = area.y2();

                        int nx1, nx2, ny1, ny2, nMaxX, nMaxY;
                        if (kind == RotationKind.LEFT) {
                            int tX1 = y1, tY1 = width - x1 - 1;
                            int tX2 = y2, tY2 = width - x2 - 1;

                            nx1 = Math.min(tX1, tX2);
                            nx2 = Math.max(tX1, tX2);
                            ny1 = Math.min(tY1, tY2);
                            ny2 = Math.max(tY1, tY2);

                            nMaxX = area.maxY();
                            nMaxY = width - area.maxX() - 1;
                        } else {
                            int tX1 = height - y1 - 1, tY1 = x1;
                            int tX2 = height - y2 - 1, tY2 = x2;

                            nx1 = Math.min(tX1, tX2);
                            nx2 = Math.max(tX1, tX2);
                            ny1 = Math.min(tY1, tY2);
                            ny2 = Math.max(tY1, tY2);

                            nMaxX = height - area.maxY() - 1;
                            nMaxY = area.maxX();
                        }

                        return new RedshiftArea(
                                area.id(),
                                area.pixelShift(),
                                area.relPixelShift(),
                                area.kmPerSec(),
                                nx1, ny1, nx2, ny2,
                                nMaxX, nMaxY
                        );
                    })
                    .toList();

            newMetadata.put(Redshifts.class, new Redshifts(rotatedRedshifts));
        });

        rotated.findMetadata(ActiveRegions.class).ifPresent(activeRegions -> {
            var rotatedActiveRegions = activeRegions.transform(p -> {
                var x = p.x();
                var y = p.y();
                return kind == RotationKind.LEFT
                        ? new Point2D(y, width - x - 1)
                        : new Point2D(height - y - 1, x);
            });
            newMetadata.put(ActiveRegions.class, rotatedActiveRegions);
        });


        var newImage = new ImageWrapper32(newWidth, newHeight, newData, newMetadata);
        TransformationHistory.recordTransform(newImage, message("rotate." + kind.name().toLowerCase()));
        return newImage;
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

    private ReconstructionOutputs performImageReconstruction(ImageConverter<float[][]> converter,
                                                             SerFileReader reader,
                                                             int start,
                                                             int end,
                                                             ImageGeometry geometry,
                                                             int width,
                                                             int height,
                                                             DoubleUnaryOperator polynomial,
                                                             AtomicInteger processedCount,
                                                             int totalCount,
                                                             WorkflowState... images) {
        reader.seekFrame(start);
        int totalLines = end - start;
        var lambda0 = processParams.spectrumParams().ray().wavelength();
        var observationDetails = processParams.observationDetails();
        var instrument = observationDetails.instrument();
        var dispersion = SpectrumAnalyzer.computeSpectralDispersion(
                instrument,
                lambda0,
                observationDetails.pixelSize() * observationDetails.binning()
        );
        var phenomenaDetector = new PhenomenaDetector(dispersion, lambda0, width);
        var hasRedshifts = new AtomicBoolean();
        var hasActiveRegions = new AtomicBoolean();
        var latch = new CountDownLatch(end - start);
        var semaphore = new Semaphore(INMEMORY_BUFFERS_PER_CORE * Runtime.getRuntime().availableProcessors());
        var jSolexSer = reader.header().isJSolexTrimmedSer();
        var flat = loadReadFlat(geometry.width(), geometry.height()).orElse(null);
        try (var executor = ParallelExecutor.newExecutor(Math.max(2, Runtime.getRuntime().availableProcessors()))) {
            var reconstructedImages = new float[images.length][totalLines][width];
            for (int i = start, j = 0; i < end; i++, j += 1) {
                semaphore.acquire();
                var currentFrame = reader.currentFrame().data().array();
                byte[] copy = new byte[currentFrame.length];
                System.arraycopy(currentFrame, 0, copy, 0, currentFrame.length);
                reader.nextFrame();
                var original = converter.createBuffer(geometry);
                int y = j;
                int frameId = i;
                executor.submit(() -> {
                    var progressOperation = newOperation(message("reconstruction"));
                    try {
                        // The converter makes sure we only have a single channel
                        converter.convert(frameId, ByteBuffer.wrap(copy), geometry, original);
                        if (flat != null) {
                            applyFlatCorrection(original, flat);
                        }
                        IntStream.range(0, images.length).parallel().forEach(idx -> {
                            try {
                                var state = images[idx];
                                var buffer = reconstructedImages[idx];
                                var truncationDetails = new TruncationDetails();
                                processSingleFrame(state.isInternal(), width, height, buffer, y, original, polynomial, state.pixelShift(), totalLines, jSolexSer, truncationDetails);
                                if (state.pixelShift() == 0) {
                                    phenomenaDetector.setDetectBorders(processParams.enhancementParams().jaggingCorrectionParams().enabled());
                                    phenomenaDetector.setDetectRedshifts(
                                            processParams.spectrumParams().ray().label().equalsIgnoreCase(SpectralRay.H_ALPHA.label()) && processParams.requestedImages().isEnabled(GeneratedImageKind.REDSHIFT)
                                    );
                                    phenomenaDetector.setDetectActiveRegions(forceDetectActiveRegions || processParams.requestedImages().isEnabled(GeneratedImageKind.ACTIVE_REGIONS));
                                    phenomenaDetector.performDetection(frameId, width, height, original, polynomial, reader.header());
                                    hasRedshifts.set(hasRedshifts.get() || phenomenaDetector.isRedShiftDetectionEnabled());
                                    hasActiveRegions.set(hasActiveRegions.get() || phenomenaDetector.isActiveRegionsDetectionEnabled());
                                }
                                state.setTruncationDetails(truncationDetails);
                            } finally {
                                broadcast(progressOperation.update(((double) processedCount.incrementAndGet()) / totalCount));
                            }
                        });
                    } finally {
                        semaphore.release();
                        latch.countDown();
                        broadcast(progressOperation.complete());
                    }
                });
            }

            latch.await(); // Ensure all tasks have completed
            if (hasRedshifts.get() && !phenomenaDetector.hasRedshifts()) {
                LOGGER.warn(message("no.redshifts.detected"));
            }
            var borders = phenomenaDetector.getBorderDetection();
            for (int i = 0; i < images.length; i++) {
                var state = images[i];
                var reconstructedImage = reconstructedImages[i];
                var recon = new ImageWrapper32(width, totalLines, reconstructedImage, MutableMap.of());
                state.recordResult(WorkflowResults.RECONSTRUCTED, recon);
                state.recordResult(WorkflowResults.BORDERS, borders);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessingException(e);
        } catch (Exception e) {
            throw new ProcessingException(e);
        }

        return new ReconstructionOutputs(
                hasRedshifts.get() ? phenomenaDetector.getMaxRedshiftAreas(5) : null,
                hasActiveRegions.get() ? phenomenaDetector.getActiveRegions() : null
        );
    }

    private static void applyFlatCorrection(float[][] original, float[] flat) {
        for (var line : original) {
            for (int xx = 0; xx < line.length; xx++) {
                line[xx] /= flat[xx];
            }
        }
    }

    private Optional<float[]> loadReadFlat(int expectedWidth, int expectedHeight) {
        var masterFlat = processParams.enhancementParams().masterFlatFile();
        if (masterFlat == null) {
            return Optional.empty();
        }
        if (!Files.exists(masterFlat)) {
            LOGGER.error(message("master.flat.not.found") + " " + masterFlat);
            return Optional.empty();
        }
        var loaded = Loader.loadImage(masterFlat.toFile()).unwrapToMemory();
        if (!(loaded instanceof ImageWrapper32 flatImage)) {
            LOGGER.error(message("invalid.master.flat.type"));
            return Optional.empty();
        }
        if (flatImage.width() != expectedWidth || flatImage.height() != expectedHeight) {
            LOGGER.error(message("invalid.master.flat.size"), flatImage.width(), flatImage.height(), expectedWidth, expectedHeight);
            return Optional.empty();
        }
        var flat = prepareFlatFromAverage(flatImage.data(), flatImage.width(), flatImage.height(), FlatCreator.DEFAULT_CUTOFF);

        // normalize to [0, 1] range
        var max = 0f;
        for (int xx = 0; xx < flatImage.width(); xx++) {
            max = Math.max(max, flat[xx]);
        }
        for (int xx = 0; xx < flatImage.width(); xx++) {
            flat[xx] /= max;
        }

        return Optional.of(flat);
    }


    private SpectrumFrameAnalyzer.Result analyzeAverageImage(int width, int height, float[][] averageImage, FileNamingStrategy imageNamingStrategy, Header header) {
        SpectrumFrameAnalyzer analyzer = new SpectrumFrameAnalyzer(width, height, header.isJSolexTrimmedSer(), null);
        var result = analyzer.analyze(averageImage);
        if (processParams.extraParams().generateDebugImages()) {
            var emitter = new DiscardNonRequiredImages(
                    new NamingStrategyAwareImageEmitter(new DefaultImageEmitter(this, rootOperation, outputDirectory.toFile()), imageNamingStrategy, 0, serFile.getName().substring(0, serFile.getName().lastIndexOf("."))),
                    processParams.requestedImages().images());
            emitter.newColorImage(GeneratedImageKind.DEBUG, null, message("average"), "average", message("average.image.description"), width, height, MutableMap.of(), () -> {
                var rgb = new SpectralLineFrameImageCreator(analyzer, averageImage, width, height).generateDebugImage();
                return new float[][][]{rgb.r(), rgb.g(), rgb.b()};
            });
        }
        return result;
    }

    private void processSingleFrame(boolean internal,
                                    int width,
                                    int height,
                                    float[][] outputBuffer,
                                    int y,
                                    float[][] source,
                                    DoubleUnaryOperator p,
                                    double pixelShift,
                                    int totalLines,
                                    boolean isJolexSer,
                                    TruncationDetails truncationDetails) {
        double[] line = new double[width];
        var truncated = new boolean[width];
        IntStream.iterate(0, x -> x < width, x -> x + BATCH_LINES)
                .parallel()
                .forEach(batchStart -> {
                    int batchEnd = Math.min(batchStart + BATCH_LINES, width);
                    // interpolation is NOT needed if the processed file is a Jolex SER truncated file,
                    // because it has already been interpolated when the file was trimmed
                    double range = isJolexSer ? 0 : 1;
                    for (int x = batchStart; x < batchEnd; x++) {
                        double value = 0;
                        double weightSum = 0;

                        for (double dy = -range; dy <= range; dy += 0.5) {
                            double yd = p.applyAsDouble(x) + pixelShift + dy;

                            int y1 = (int) Math.floor(yd);
                            int y2 = y1 + 1;

                            double frac = yd - y1;

                            double weight = Math.exp(-0.5 * dy * dy);  // Gaussian decay

                            if (y1 >= 0 && y1 < height && y2 >= 0 && y2 < height) {
                                double yValue = (1 - frac) * source[y1][x] + frac * source[y2][x];
                                value += weight * yValue;
                                weightSum += weight;
                            } else {
                                truncated[x] = true;
                            }
                        }

                        if (weightSum > 0) {
                            value /= weightSum;
                        }

                        outputBuffer[y][x] = (float) value;
                        line[x] = (float) value;
                    }

                });

        if (!internal) {
            boolean display = processParams.extraParams().generateDebugImages() || processParams.requestedImages().isEnabled(GeneratedImageKind.RECONSTRUCTION);
            if (display) {
                var imageLine = new ImageLine(pixelShift,
                        y,
                        totalLines,
                        line,
                        display,
                        new Image(width, height, source)
                );
                broadcast(new PartialReconstructionEvent(imageLine));
            }
        }
        if (truncated[0]) {
            for (int x = 0; x < width; x++) {
                if (truncated[x]) {
                    truncationDetails.setMin(x);
                } else {
                    break;
                }
            }
        }
        if (truncated[width - 1]) {
            for (int x = width - 1; x >= 0; x--) {
                if (truncated[x]) {
                    truncationDetails.setMax(x);
                } else {
                    break;
                }
            }
        }

    }

    @Override
    public void broadcast(ProcessingEvent<?> event) {
        for (ProcessingEventListener listener : progressEventListeners) {
            switch (event) {
                case ProcessingStartEvent e -> listener.onProcessingStart(e);
                case ProcessingDoneEvent e -> listener.onProcessingDone(e);
                case ProgressEvent e -> listener.onProgress(e);
                case GenericMessage<?> e -> listener.onGenericMessage(e);
                case NotificationEvent e -> listener.onNotification(e);
                case SuggestionEvent e -> listener.onSuggestion(e);
                case ImageGeneratedEvent e -> listener.onImageGenerated(e);
                case FileGeneratedEvent e -> listener.onFileGenerated(e);
                case OutputImageDimensionsDeterminedEvent e -> listener.onOutputImageDimensionsDetermined(e);
                case PartialReconstructionEvent e -> listener.onPartialReconstruction(e);
                case VideoMetadataEvent e -> listener.onVideoMetadataAvailable(e);
                case ScriptExecutionResultEvent e -> listener.onScriptExecutionResult(e);
                case AverageImageComputedEvent e -> listener.onAverageImageComputed(e);
                case ReconstructionDoneEvent e -> listener.onReconstructionDone(e);
                case TrimmingParametersDeterminedEvent e -> listener.onTrimmingParametersDetermined(e);
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
        public ImageEmitter newEmitter(Broadcaster broadcaster, Path outputDirectory) {
            if (state.isInternal()) {
                return new NoOpImageEmitter();
            }
            ImageEmitter emitter = new DefaultImageEmitter(broadcaster, rootOperation, outputDirectory.toFile());
            var shift = state.pixelShift();

            emitter = new NamingStrategyAwareImageEmitter(new RenamingImageEmitter(emitter, name -> {
                if (name.toLowerCase(Locale.US).contains("doppler")) {
                    return name;
                }
                var spectrumParams = processParams.spectrumParams();
                var suffix = shift == spectrumParams.pixelShift() ? "" : " (" + (shift == spectrumParams.continuumShift() ? "continuum" : shift) + ")";
                return name + suffix;
            }, name -> {
                if (name.toLowerCase(Locale.US).contains("doppler")) {
                    return name;
                }
                var suffix = "_" + String.format(Locale.US, "%.2f", shift).replace('.', '_');
                return name + suffix;
            }), imageNamingStrategy, sequenceNumber, baseName);
            return new DiscardNonRequiredImages(emitter, processParams.requestedImages().images());
        }
    }

    private record ReconstructionOutputs(
            List<RedshiftArea> redshifts,
            ActiveRegions activeRegions
    ) {

    }

}
