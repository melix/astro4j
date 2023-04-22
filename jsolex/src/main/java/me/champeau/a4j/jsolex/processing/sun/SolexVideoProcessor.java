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
import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.ImageLine;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.event.OutputImageDimensionsDeterminedEvent;
import me.champeau.a4j.jsolex.processing.event.PartialReconstructionEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEvent;
import me.champeau.a4j.jsolex.processing.event.ProcessingEventListener;
import me.champeau.a4j.jsolex.processing.stats.DefaultImageStatsComputer;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CompositeStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.DoubleTriplet;
import me.champeau.a4j.math.IntPair;
import me.champeau.a4j.math.fft.FastFourierTransform;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.ChannelExtractingConverter;
import me.champeau.a4j.ser.bayer.DemosaicingRGBImageConverter;
import me.champeau.a4j.ser.bayer.FloatPrecisionImageConverter;
import me.champeau.a4j.ser.bayer.ImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static me.champeau.a4j.math.fft.FFTSupport.nextPowerOf2;
import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class SolexVideoProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);
    private static final String CORRECTED_DIRECTORY = "corrected";
    private static final String RAW_DIRECTORY = "raw";

    private final Set<ProcessingEventListener> progressEventListeners = new HashSet<>();

    private final File serFile;
    private final File outputDirectory;
    private final File correctedFilesDirectory;
    private final File rawImageDirectory;
    private final boolean generateDebugImages;
    private final double spectrumDetectionThreshold;

    public SolexVideoProcessor(File serFile,
                               File outputDirectory,
                               boolean generateDebugImages,
                               double spectrumDetectionThreshold) {
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
        this.correctedFilesDirectory = new File(outputDirectory, CORRECTED_DIRECTORY);
        this.rawImageDirectory = new File(outputDirectory, RAW_DIRECTORY);
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
        var startTime = System.nanoTime();
        var converter = createImageConverter();
        var statsComputer = new DefaultImageStatsComputer();
        var detector = new SimpleSunEdgeDetector(statsComputer, converter);
        try (SerFileReader reader = SerFileReader.of(serFile)) {
            Files.createDirectories(outputDirectory.toPath());
            Files.createDirectories(correctedFilesDirectory.toPath());
            Files.createDirectories(rawImageDirectory.toPath());
            LOGGER.info("SER file contains {} frames", reader.header().frameCount());
            LOGGER.info("Width: {}, height: {}", reader.header().geometry().width(), reader.header().geometry().height());
            LOGGER.info("Detecting sun edges... ");
            detector.detectEdges(reader);
            detector.ifEdgesDetected((start, end) -> {
                        LOGGER.info("Sun edges detected at frames {} and {}", start, end);
                        generateImages(converter, reader, start, end);
                    }
            );
        } catch (Exception e) {
            LOGGER.error("Error while processing", e);
            var out = new ByteArrayOutputStream();
            var s = new PrintWriter(out);
            e.printStackTrace(s);
            s.flush();
            broadcast(new NotificationEvent(new Notification(Alert.AlertType.ERROR, "Unexpected error", "An error occurred during processing", out.toString())));
            throw new ProcessingException(e);
        } finally {
            var duration = Duration.ofNanos(System.nanoTime() - startTime).toSeconds();
            LOGGER.info("Processing done in {} s", duration);
            broadcast(new NotificationEvent(new Notification(Alert.AlertType.INFORMATION, "Congratulations!", "Processing finished in " + duration + " s", "")));
        }
    }

    private static ImageConverter<float[]> createImageConverter() {
        return new FloatPrecisionImageConverter(
                new ChannelExtractingConverter(
                        new DemosaicingRGBImageConverter(
                                new BilinearDemosaicingStrategy(),
                                null
                        ),
                        GREEN
                )
        );
    }

    private void generateImages(ImageConverter<float[]> converter,
                                SerFileReader reader,
                                int start,
                                int end) {
        var geometry = reader.header().geometry();
        int width = geometry.width();
        int height = geometry.height();
        reader.seekFrame(start);
        int newHeight = end - start;
        var outputBuffer = new float[width * newHeight];
        double ratio = (double) width / newHeight;
        broadcast(new OutputImageDimensionsDeterminedEvent(new IntPair(width, newHeight)));
        LOGGER.info("SX/SY = {}", ratio);
        LOGGER.info("Starting reconstruction...");
        try (var executor = ParallelExecutor.newExecutor()) {
            for (int i = start, j = 0; i < end; i++, j += width) {
                var original = converter.createBuffer(geometry);
                // The converter makes sure we only have a single channel
                converter.convert(i, reader.currentFrame().data(), geometry, original);
                reader.nextFrame();
                int frameId = i;
                int offset = j;
                executor.submit(() -> {
                    var analyzer = new SpectrumFrameAnalyzer(width, height, spectrumDetectionThreshold, 50d);
                    processSingleFrame(width, height, outputBuffer, analyzer, offset, frameId, original);
                });
            }
        } catch (Exception e) {
            throw new ProcessingException(e);
        }
        LOGGER.info("Reconstruction done. Generating images...");
        emitImage("Raw (Linear)", "linear", width, newHeight, outputBuffer, image -> {
            var stretchingStrategy = CompositeStretchingStrategy.of(
                    new CutoffStretchingStrategy(1f, 255f),
                    new LinearStrechingStrategy(240f)
            );
            stretchingStrategy.stretch(image.data);
        });
        emitImage("Raw (Stretched)", "streched", width, newHeight, outputBuffer, image -> {
            var stretchingStrategy = CompositeStretchingStrategy.of(
                    new CutoffStretchingStrategy(1f, 255f),
                    new ArcsinhStretchingStrategy(1f, 0.1f),
                    new LinearStrechingStrategy(240f)
            );
            stretchingStrategy.stretch(image.data);
        });
        emitImage("Banding fixed", "banding-fixed", width, newHeight, outputBuffer, image -> {
            var imageMath = ImageMath.newInstance();
            // Perform one iteration vertically, were there are not so many lines
            BandingReduction.reduceBanding(width, newHeight, image.data, 1, 16);
            // Then perform multiple iterations vertically, were there are many line artifacts
            // we need to transpose the image to compute the average value of each line
            float[] transposed = imageMath.rotateLeft(image.data, width, newHeight);
            BandingReduction.reduceBanding(newHeight, width, transposed, 4, 64);
            transposed = imageMath.rotateRight(transposed, newHeight, width);
            System.arraycopy(transposed, 0, image.data, 0, transposed.length);
            var stretchingStrategy = CompositeStretchingStrategy.of(
                    new CutoffStretchingStrategy(1f, 255f),
                    new ArcsinhStretchingStrategy(1f, 0.1f),
                    new LinearStrechingStrategy(240f)
            );
            stretchingStrategy.stretch(image.data);
        });

        if (true) {
            emitImage("Lo pass", "fft", width, newHeight, outputBuffer, image -> {
                var transformed = FastFourierTransform.pad(image.data, width);
                int padWidth = nextPowerOf2(width);
                int padHeight = nextPowerOf2(newHeight);
                var fft = FastFourierTransform.ofArray2D(transformed, padWidth, padHeight);
                LOGGER.info("Computing Lo pass filter");
                fft.transform();
                float[] real = fft.real();
                float[] im = fft.imaginary();
                int length = real.length;
                int lo = (int) (0.99f * length);
                Arrays.fill(real, lo, length, 0f);
                Arrays.fill(im, lo, length, 0f);
                fft.inverseTransform();

                var stretchingStrategy = CompositeStretchingStrategy.of(
                        new CutoffStretchingStrategy(1f, 255f),
                        new ArcsinhStretchingStrategy(1f, 0.1f),
                        new LinearStrechingStrategy(240f)
                );
                stretchingStrategy.stretch(transformed);

                image.data = real;
                image.width = padWidth;
                image.height = padHeight;
            });
        }
    }

    private void emitImage(String title, String name, int width, int height, float[] original, Consumer<ImageWrapper> modifier) {
        float[] copy = new float[original.length];
        System.arraycopy(original, 0, copy, 0, original.length);
        ImageWrapper image = new ImageWrapper(copy, width, height);
        modifier.accept(image);
        File outputFile = new File(rawImageDirectory, name + ".png");
        ImageUtils.writeMonoImage(image.width, image.height, image.data, outputFile);
        broadcast(new ImageGeneratedEvent(new GeneratedImage(title, outputFile.toPath())));
    }

    private void processSingleFrame(int width,
                                    int height,
                                    float[] outputBuffer,
                                    SpectrumFrameAnalyzer analyzer,
                                    int offset,
                                    int frameId,
                                    float[] buffer) {
        analyzer.analyze(buffer);
        analyzer.findDistortionPolynomial().ifPresent(p -> {
                    processCorrectedImage(width, height, buffer, analyzer, frameId, p);
                    double[] line = new double[width];
                    int lastY = 0;
                    for (int x = 0; x < width; x++) {
                        // To reconstruct the image, we use the polynom to find which pixel to use
                        int y = (int) (p.a() * x * x + p.b() * x + p.c());
                        if (y < 0 && y >= height) {
                            y = lastY;
                        }
                        float value = buffer[x + width * y];
                        outputBuffer[offset + x] = value;
                        line[x] = value;
                        lastY = y;
                    }
                    broadcast(new PartialReconstructionEvent(new ImageLine(offset / width, line)));
                }
        );
    }

    private void processCorrectedImage(int width,
                                       int height,
                                       float[] original,
                                       SpectrumFrameAnalyzer analyzer,
                                       int frameId,
                                       DoubleTriplet p) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Distortion polynomial of frame {} : [{}, {}, {}]", frameId, p.a(), p.b(), p.c());
        }
        int size = width * height;
        if (generateDebugImages) {
            var distorsionCorrection = new DistortionCorrection(original, width, height);
            float[] corrected = distorsionCorrection.secondOrderPolynomialCorrection(p);
            // We create RGB images for debugging, which contain the original image at top
            // and the corrected one at the bottom
            int spacing = 10 * width;
            int offset = size + spacing;
            float[] rr = new float[2 * size + spacing];
            float[] gg = new float[2 * size + spacing];
            float[] bb = new float[2 * size + spacing];
            System.arraycopy(original, 0, rr, 0, size);
            System.arraycopy(original, 0, gg, 0, size);
            System.arraycopy(original, 0, bb, 0, size);
            System.arraycopy(corrected, 0, rr, offset, size);
            System.arraycopy(corrected, 0, gg, offset, size);
            System.arraycopy(corrected, 0, bb, offset, size);

            for (int x = 0; x < width; x++) {
                rr[x + size + 5 * width] = 240;
                gg[x + size + 5 * width] = 120;
                bb[x + size + 5 * width] = 240;
            }

            analyzer.analyze(corrected);
            analyzer.leftSunBorder().ifPresent(bx -> {
                for (int y = 0; y < height; y++) {
                    rr[offset + bx + y * width] = 255;
                    gg[offset + bx + y * width] = 0;
                    bb[offset + bx + y * width] = 0;
                }
            });
            analyzer.rightSunBorder().ifPresent(bx -> {
                for (int y = 0; y < height; y++) {
                    rr[offset + bx + y * width] = 255;
                    gg[offset + bx + y * width] = 0;
                    bb[offset + bx + y * width] = 0;
                }
            });

            // Draw a line on the top graph corresponding to the detected curvature
            for (int x = 0; x < width; x++) {
                int cy = (int) ((p.a() * x * x + p.b() * x + p.c()) * width);
                int idx = x + cy;
                if (idx < 0 || idx >= size) {
                    break;
                }
                rr[idx] = 255;
                gg[idx] = 0;
                bb[idx] = 0;
            }

            // Add green lines showing the detected spectrum line
            SpectrumLine[] spectrumLines = analyzer.spectrumLinesArray();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    SpectrumLine spectrumLine = spectrumLines[x];
                    if (spectrumLine != null && spectrumLine.top() <= y && spectrumLine.bottom() >= y) {
                        rr[offset + x + y * width] = 0;
                        gg[offset + x + y * width] = 255;
                        bb[offset + x + y * width] = 0;
                    }
                }
            }
            String fileName = String.format("%06d-corrected.png", frameId);
            ImageUtils.writeRgbImage(width, 2 * height + 10, rr, gg, bb, new File(correctedFilesDirectory, fileName));
        }
    }

    private void broadcast(ProcessingEvent<?> event) {
        for (ProcessingEventListener listener : progressEventListeners) {
            switch (event) {
                case OutputImageDimensionsDeterminedEvent e -> listener.onOutputImageDimensionsDetermined(e);
                case PartialReconstructionEvent e -> listener.onPartialReconstruction(e);
                case ImageGeneratedEvent e -> listener.onImageGenerated(e);
                case NotificationEvent e -> listener.onNotification(e);
            }
        }
    }

    private static class ImageWrapper {
        private float[] data;
        private int width;
        private int height;

        public ImageWrapper(float[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }
}
