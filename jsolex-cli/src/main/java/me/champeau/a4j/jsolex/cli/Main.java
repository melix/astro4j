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
package me.champeau.a4j.jsolex.cli;

import ch.qos.logback.classic.Level;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.core.annotation.ReflectiveAccess;
import me.champeau.a4j.jsolex.processing.params.BandingCorrectionParams;
import me.champeau.a4j.jsolex.processing.params.DebugParams;
import me.champeau.a4j.jsolex.processing.params.GeometryParams;
import me.champeau.a4j.jsolex.processing.params.ObservationDetails;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectrumParams;
import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.tuples.DoublePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;


@Command(name = "jsolex", description = "Sol'Ex spectroheliograph video processing",
        mixinStandardHelpOptions = true)
@ReflectiveAccess
public class Main implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;

    @Option(names = {"-i", "--input"}, description = "Input SER file", required = true)
    File inputFile;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    File outputDir;

    @Option(names = {"-q", "--quick-mode"}, description = "Quick mode")
    boolean quickMode;

    @Option(names = {"-c", "--config"}, description = "Read parameters from a JSON configuration file")
    File configFile;

    @CommandLine.ArgGroup
    SpectrumOptions spectrumOptions = new SpectrumOptions();

    @CommandLine.ArgGroup
    ObservationOptions observationOptions = new ObservationOptions();

    @CommandLine.ArgGroup
    MiscOptions miscOptions = new MiscOptions();

    @CommandLine.ArgGroup
    GeometryOptions geometryOptions = new GeometryOptions();

    @CommandLine.ArgGroup
    BandingCorrectionOptions bandingCorrectionOptions = new BandingCorrectionOptions();

    public static void main(String[] args) {
        PicocliRunner.run(Main.class, args);
    }

    public void run() {
        if (verbose) {
            logger(LoggingListener.class.getName()).setLevel(Level.DEBUG);
        } else {
            logger("me.champeau.a4j.jsolex.processing").setLevel(Level.ERROR);
            logger("me.champeau.a4j.math").setLevel(Level.ERROR);
        }
        var processParams = ProcessParams.loadDefaults();
        if (configFile != null) {
            var fromFile = ProcessParams.readFrom(configFile);
            if (fromFile.isPresent()) {
                processParams = fromFile.get();
            } else {
                LOGGER.error("Configuration file {} not found, falling back to defaults", configFile);
            }
        }
        processParams = processParams.withSpectrumParams(spectrumOptions.applyTo(processParams.spectrumParams()));
        processParams = processParams.withObservationDetails(observationOptions.applyTo(processParams.observationDetails()));
        processParams = processParams.withDebugParams(miscOptions.applyTo(processParams.debugParams()).withAutosave(true));
        processParams = processParams.withGeometryParams(geometryOptions.applyTo(processParams.geometryParams()));
        processParams = processParams.withBandingCorrectionParams(bandingCorrectionOptions.applyTo(processParams.bandingCorrectionParams()));
        LOGGER.info("Processing parameters: {}", processParams);
        if (outputDir == null) {
            var baseName = inputFile.getName().substring(0, inputFile.getName().lastIndexOf("."));
            outputDir = new File(inputFile.getParentFile(), baseName);
            int i = 0;
            while (Files.exists(outputDir.toPath())) {
                String suffix = String.format("-%04d", i++);
                outputDir = new File(inputFile.getParentFile(), baseName + suffix);
            }
        }
        try {
            Files.createDirectories(outputDir.toPath());
            var processor = new SolexVideoProcessor(
                    inputFile,
                    outputDir.toPath(),
                    processParams,
                    quickMode
            );
            processor.addEventListener(new LoggingListener(processParams));
            processor.process();
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static ch.qos.logback.classic.Logger logger(String name) {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
    }

    @FunctionalInterface
    interface OptionsConfigurer<T> {
        T applyTo(T params);
    }

    static class SpectrumOptions implements OptionsConfigurer<SpectrumParams> {
        @Option(names = {"-wl", "--wavelength"},
                description = "Wavelength (one of ${COMPLETION-CANDIDATES})",
                completionCandidates = SpectralRayCandidates.class
        )
        SpectralRay spectralRay;

        @Option(names = {"-sl", "--spectrum-detection-threshold"},
                description = "Spectrum detection threshold")
        Double detectionThreshold;

        @Option(names = {"-ps", "--pixel-shift"}, description = "Pixel shifting")
        Integer pixelShift;

        @Option(names = {"-ds", "--doppler-shift"}, description = "Doppler shifting")
        Integer dopplerShift;

        @Option(names = {"-irb", "--inverse-red-blue"}, description = "Inverse red and blue channels in Doppler image")
        Boolean switchRedBlueChannels;

        @Override
        public SpectrumParams applyTo(SpectrumParams params) {
            var result = params;
            if (spectralRay != null) {
                result = result.withRay(spectralRay);
            }
            if (detectionThreshold != null) {
                result = result.withDetectionThreshold(detectionThreshold);
            }
            if (pixelShift != null) {
                result = result.withPixelShift(pixelShift);
            }
            if (dopplerShift != null) {
                result = result.withDopplerShift(dopplerShift);
            }
            if (switchRedBlueChannels != null) {
                result = result.withSwitchRedBlueChannels(switchRedBlueChannels);
            }
            return result;
        }
    }

    static class ObservationOptions implements OptionsConfigurer<ObservationDetails> {
        @Option(names = {"-ob", "--observer"}, description = "The observer name")
        String observer;

        @Option(names = {"-em", "--email"}, description = "The observer contact email address")
        String email;

        @Option(names = {"-in", "--instrument"}, description = "The instrument used (defaults to Sol'Ex)", defaultValue = "Sol'Ex")
        String instrument;

        @Option(names = {"-t", "--telescope"}, description = "The telescope")
        String telescope;

        @Option(names = {"-fl", "--focal-length"}, description = "The focal length (in mm)")
        Integer focalLength;

        @Option(names = {"-a", "--aperture"}, description = "The aperture (in mm)")
        Integer aperture;

        @Option(names = {"-gla", "--latitude"}, description = "The site latitude in degrees")
        Double latitude;

        @Option(names = {"-glo", "--longitude"}, description = "The site longitude in degrees")
        Double longitude;

        @Option(names = {"-d", "--date"}, description = "The observation date")
        String date;

        @Option(names = {"-ca", "--camera"}, description = "The camera used to capture the images")
        String camera;

        @Override
        public ObservationDetails applyTo(ObservationDetails params) {
            var result = params;
            if (observer != null) {
                result = result.withObserver(observer);
            }
            if (email != null) {
                result = result.withEmail(email);
            }
            if (instrument != null) {
                result = result.withInstrument(instrument);
            }
            if (telescope != null) {
                result = result.withInstrument(telescope);
            }
            if (focalLength != null) {
                result = result.withFocalLength(focalLength);
            }
            if (aperture != null) {
                result = result.withAperture(aperture);
            }
            if (latitude != null && longitude != null) {
                result = result.withCoordinates(new DoublePair(latitude, longitude));
            }
            if (date != null) {
                var parsed = ZonedDateTime.parse(date).toLocalDateTime().atZone(ZoneId.of("UTC"));
                result = result.withDate(parsed);
            }
            if (camera != null) {
                result = result.withCamera(camera);
            }
            return result;
        }
    }

    static class MiscOptions implements OptionsConfigurer<DebugParams> {
        @Option(names = {"--debug"}, description = "Generate debug images", negatable = true)
        Boolean debugImages;

        @Option(names = {"--fits"}, description = "Generate FITS images", negatable = true)
        Boolean generateFits;

        @Override
        public DebugParams applyTo(DebugParams params) {
            var result = params;
            if (debugImages != null) {
                result = result.withGenerateDebugImages(debugImages);
            }
            if (generateFits != null) {
                result = result.withGenerateFits(generateFits);
            }
            return result;
        }
    }

    static class GeometryOptions implements OptionsConfigurer<GeometryParams> {
        @Option(names = {"-tilt", "--tilt"}, description = "Override tilt")
        Double tilt;

        @Option(names = {"-xy", "--xy-ratio"}, description = "Override X/Y ratio")
        Double xyRatio;

        @Option(names = {"-hm", "--horizontal-mirror"}, description = "Perform horizontal flip")
        Boolean horizontalFlip;

        @Option(names = {"-vm", "--vertical-mirror"}, description = "Perform vertical flip")
        Boolean verticalFlip;

        @Override
        public GeometryParams applyTo(GeometryParams params) {
            var result = params;
            if (tilt != null) {
                result = result.withTilt(tilt);
            }
            if (xyRatio != null) {
                result = result.withXYRatio(xyRatio);
            }
            if (horizontalFlip != null) {
                result = result.withHorizontalMirror(horizontalFlip);
            }
            if (verticalFlip != null) {
                result = result.withVerticalMirror(verticalFlip);
            }
            return result;
        }
    }

    static class BandingCorrectionOptions implements OptionsConfigurer<BandingCorrectionParams> {
        @Option(names = {"-bcw", "--banding-correction-width"}, description = "Banding correction width")
        Integer width;

        @Option(names = {"-bcp", "--banding-correction-passes"}, description = "Banding correction passes")
        Integer passes;

        @Override
        public BandingCorrectionParams applyTo(BandingCorrectionParams params) {
            var result = params;
            if (width != null) {
                result = result.withWidth(width);
            }
            if (passes != null) {
                result = result.withPasses(passes);
            }
            return result;
        }
    }

    static class SpectralRayCandidates extends ArrayList<String> {
        public SpectralRayCandidates() {
            super(SpectralRay.predefined().size());
            for (SpectralRay ray : SpectralRay.predefined()) {
                add(ray.label());
            }
        }
    }
}
