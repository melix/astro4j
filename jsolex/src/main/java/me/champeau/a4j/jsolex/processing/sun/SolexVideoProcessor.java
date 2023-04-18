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

import me.champeau.a4j.jsolex.processing.stats.DefaultImageStatsComputer;
import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CompositeStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;
import me.champeau.a4j.math.DoubleTriplet;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.CachingImageConverter;
import me.champeau.a4j.ser.bayer.ChannelExtractingConverter;
import me.champeau.a4j.ser.bayer.DemosaicingRGBImageConverter;
import me.champeau.a4j.ser.bayer.DoublePrecisionImageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;

import static me.champeau.a4j.ser.bayer.BayerMatrixSupport.GREEN;

public class SolexVideoProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolexVideoProcessor.class);
    private static final String CORRECTED_DIRECTORY = "corrected";
    private static final String RAW_DIRECTORY = "raw";

    private final File serFile;
    private final File outputDirectory;
    private final File correctedFilesDirectory;
    private final File rawImageDirectory;

    public SolexVideoProcessor(File serFile, File outputDirectory) {
        this.serFile = serFile;
        this.outputDirectory = outputDirectory;
        this.correctedFilesDirectory = new File(outputDirectory, CORRECTED_DIRECTORY);
        this.rawImageDirectory = new File(outputDirectory, RAW_DIRECTORY);
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
            throw new RuntimeException(e);
        } finally {
            var duration = Duration.ofNanos(System.nanoTime() - startTime).toSeconds();
            LOGGER.info("Processing done in {} s", duration);
        }
    }

    private static CachingImageConverter<double[]> createImageConverter() {
        return new CachingImageConverter<>(new DoublePrecisionImageConverter(
                new ChannelExtractingConverter(
                        new DemosaicingRGBImageConverter(
                                new BilinearDemosaicingStrategy(),
                                null
                        ),
                        GREEN
                )
        ));
    }

    private void generateImages(CachingImageConverter<double[]> converter,
                                SerFileReader reader,
                                int start,
                                int end) {
        var geometry = reader.header().geometry();
        int width = geometry.width();
        int height = geometry.height();
        reader.seekFrame(start);
        int newHeight = end - start;
        var outputBuffer = new double[width * newHeight];
        double ratio = (double) width / newHeight;
        LOGGER.info("SX/SY = {}", ratio);
        LOGGER.info("Starting reconstruction...");
        try (var executor = ParallelExecutor.newExecutor()) {
            for (int i = start, j = 0; i < end; i++, j += width) {
                var analyzer = new SpectrumFrameAnalyzer(width, height, 0.85d, 50d);
                var original = converter.createBuffer(geometry);
                // The converter makes sure we only have a single channel
                converter.convert(i, reader.currentFrame().data(), geometry, original);
                reader.nextFrame();
                int frameId = i;
                int offset = j;
                executor.submit(() -> processSingleFrame(width, height, outputBuffer, analyzer, offset, frameId, original));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Reconstruction done. Generating images...");
        int outputsize = newHeight * width;
        double[] copy = new double[outputsize];
        System.arraycopy(outputBuffer, 0, copy, 0, outputsize);
        var stretchingStrategy = CompositeStretchingStrategy.of(
                new CutoffStretchingStrategy(1d, 255d),
                new LinearStrechingStrategy(240d)
        );
        stretchingStrategy.stretch(copy);
        ImageUtils.writeMonoImage(width, newHeight, copy, new File(rawImageDirectory, "linear.png"));
        System.arraycopy(outputBuffer, 0, copy, 0, outputsize);
        stretchingStrategy = CompositeStretchingStrategy.of(
                new CutoffStretchingStrategy(1d, 255d),
                new ArcsinhStretchingStrategy(1d, 0.1d),
                new LinearStrechingStrategy(240d)
        );
        stretchingStrategy.stretch(copy);
        ImageUtils.writeMonoImage(width, newHeight, copy, new File(rawImageDirectory, "stretched.png"));
        System.arraycopy(outputBuffer, 0, copy, 0, outputsize);
    }

    private void processSingleFrame(int width,
                                    int height,
                                    double[] outputBuffer,
                                    SpectrumFrameAnalyzer analyzer,
                                    int offset,
                                    int frameId,
                                    double[] buffer) {
        analyzer.analyze(buffer);
        analyzer.findDistortionPolynomial().ifPresent(p ->
                performDistortionCorrection(width, height, buffer, analyzer, frameId, p)
        );
        // At this stage, all images should have been corrected, with the spectral line
        // we're analyzing in the middle of the image and perfectly horizontal
        double top = 0;
        double bottom = 0;
        int spectrumLinesCount = 0;
        for (SpectrumLine line : analyzer.spectrumLinesArray()) {
            if (line != null) {
                spectrumLinesCount++;
                top = top + (line.top() - top) / spectrumLinesCount;
                bottom = bottom + (line.bottom() - bottom) / spectrumLinesCount;
            }
        }
        for (int x = 0; x < width; x++) {
            // To reconstruct the image, we take the middle of the spectrum line
            int middle = (int) (top + bottom) / 2;
            double value = buffer[x + width * middle];
            outputBuffer[offset + x] = value;
        }
    }

    private void performDistortionCorrection(int width,
                                             int height,
                                             double[] original,
                                             SpectrumFrameAnalyzer analyzer,
                                             int frameId,
                                             DoubleTriplet p) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Distortion polynomial of frame {} : [{}, {}, {}]", frameId, p.a(), p.b(), p.c());
        }
        var distorsionCorrection = new DistortionCorrection(original, width, height);
        double[] corrected = distorsionCorrection.secondOrderPolynomialCorrection(p);
        writeCorrectedImage(width, height, original, analyzer, frameId, p, corrected);
    }

    private void writeCorrectedImage(int width,
                                     int height,
                                     double[] buffer,
                                     SpectrumFrameAnalyzer analyzer,
                                     int frameId,
                                     DoubleTriplet p,
                                     double[] corrected) {
        // We create RGB images for debugging, which contain the original image at top
        // and the corrected one at the bottom
        int size = width * height;
        double[] rr = new double[2 * size];
        double[] gg = new double[2 * size];
        double[] bb = new double[2 * size];
        System.arraycopy(buffer, 0, rr, 0, size);
        System.arraycopy(buffer, 0, gg, 0, size);
        System.arraycopy(buffer, 0, bb, 0, size);
        System.arraycopy(corrected, 0, buffer, 0, size);
        analyzer.analyze(corrected);
        System.arraycopy(corrected, 0, rr, size, size);
        System.arraycopy(corrected, 0, gg, size, size);
        System.arraycopy(corrected, 0, bb, size, size);
        analyzer.leftSunBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[size + bx + y * width] = 255;
                gg[size + bx + y * width] = 0;
                bb[size + bx + y * width] = 0;
            }
        });
        analyzer.rightSunBorder().ifPresent(bx -> {
            for (int y = 0; y < height; y++) {
                rr[size + bx + y * width] = 255;
                gg[size + bx + y * width] = 0;
                bb[size + bx + y * width] = 0;
            }
        });

        // Draw a line on the top graph corresponding to the detected curvature
//        for (int x = 0; x < width; x++) {
//            int cy = (int) ((p.a() * x * x + p.b() * x + p.c()) * width);
//            int idx = x + cy;
//            if (idx < 0 || idx >= size) {
//                break;
//            }
//            rr[idx] = 255;
//            gg[idx] = 0;
//            bb[idx] = 0;
//        }

        // Add green lines showing the detected spectrum line
        SpectrumLine[] spectrumLines = analyzer.spectrumLinesArray();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                SpectrumLine spectrumLine = spectrumLines[x];
                if (spectrumLine != null && spectrumLine.top() <= y && spectrumLine.bottom() >= y) {
                    rr[size + x + y * width] = 0;
                    gg[size + x + y * width] = 255;
                    bb[size + x + y * width] = 0;
                }
            }
        }
        String fileName = String.format("%06d-corrected.png", frameId);
        ImageUtils.writeRgbImage(width, 2 * height, rr, gg, bb, new File(correctedFilesDirectory, fileName));
    }

}
