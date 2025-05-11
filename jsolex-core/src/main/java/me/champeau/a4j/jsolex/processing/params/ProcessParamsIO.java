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
package me.champeau.a4j.jsolex.processing.params;

import com.google.gson.Gson;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.stretching.AutohistogramStrategy;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.sun.FlatCorrection;
import me.champeau.a4j.jsolex.processing.sun.workflow.JaggingCorrection;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.ser.ColorMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static me.champeau.a4j.jsolex.processing.sun.BandingReduction.DEFAULT_BAND_SIZE;
import static me.champeau.a4j.jsolex.processing.sun.BandingReduction.DEFAULT_PASS_COUNT;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public abstract class ProcessParamsIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessParamsIO.class);

    private ProcessParamsIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = VersionUtil.getJsolexDir();
        try {
            createDirectoriesIfNeeded(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("process-params.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        builder.registerTypeAdapter(ObservationDetails.class, new ObservationDetailsSerializer());
        builder.registerTypeAdapter(SpectralRay.class, new SpectralRaySerializer());
        builder.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer());
        builder.registerTypeAdapter(GeometryParams.class, new GeometryParamsSerializer());
        builder.registerTypeAdapter(ImageMathParams.class, new ImageMathParamsSerializer());
        builder.registerTypeAdapter(EnhancementParams.class, new EnhancementParamsSerializer());
        builder.registerTypeAdapter(AutoStretchParams.class, new AutoStretchParamsSerializer());
        return builder.create();
    }

    public static ProcessParams loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        ProcessParams params = readFrom(defaultsFile);
        if (params != null) {
            return params;
        }
        LOGGER.debug(message("no.config.file.found"), defaultsFile, "process params");
        return createNewDefaults();
    }

    public static ProcessParams createNewDefaults() {
        return new ProcessParams(
                new SpectrumParams(SpectralRay.AUTO, 0, 3, Constants.DEFAULT_CONTINUUM_SHIFT, false),
                new ObservationDetails(
                        null,
                        null,
                        SpectroHeliograph.SOLEX,
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.now().atZone(ZoneId.of("UTC")),
                        "",
                        1,
                        2.4,
                        false,
                        false,
                        false),
                new ExtraParams(false, true, EnumSet.of(ImageFormat.PNG), FileNamingStrategy.DEFAULT_TEMPLATE, FileNamingStrategy.DEFAULT_DATETIME_FORMAT, FileNamingStrategy.DEFAULT_DATE_FORMAT, false, GlobeStyle.EQUATORIAL_COORDS),
                new VideoParams(ColorMode.MONO),
                new GeometryParams(null, null, false, false, false, false, false, RotationKind.NONE, AutocropMode.RADIUS_1_2, DeconvolutionMode.NONE, null, false, null, false),
                new BandingCorrectionParams(DEFAULT_BAND_SIZE, DEFAULT_PASS_COUNT),
                new RequestedImages(RequestedImages.FULL_MODE, List.of(0d), Set.of(), Set.of(), ImageMathParams.NONE, false),
                createDefaultClaheParams(),
                createDefaultAutoStretchParams(),
                ContrastEnhancement.AUTOSTRETCH,
                new EnhancementParams(false, FlatCorrection.DEFAULT_LO_PERCENTILE, FlatCorrection.DEFAULT_HI_PERCENTILE, FlatCorrection.DEFAULT_ORDER, null, new JaggingCorrectionParams(false, JaggingCorrection.DEFAULT_SIGMA))
        );
    }

    public static ProcessParams readFrom(Path configFile) {
        if (Files.exists(configFile)) {
            try (var reader = FilesUtils.newTextReader(configFile)) {
                var params = readFrom(reader);
                if (params != null) {
                    return params;
                }
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return null;
    }

    public static ProcessParams readFrom(Reader reader) {
        Gson gson = newGson();
        var params = gson.fromJson(reader, ProcessParams.class);
        if (params != null) {
            if (params.videoParams() == null) {
                // happens if loading an old config file
                params = new ProcessParams(
                        params.spectrumParams(),
                        params.observationDetails(),
                        params.extraParams(),
                        new VideoParams(ColorMode.MONO),
                        params.geometryParams(),
                        params.bandingCorrectionParams(),
                        params.requestedImages(),
                        params.claheParams(),
                        params.autoStretchParams(),
                        params.contrastEnhancement(),
                        params.enhancementParams()
                );
            }
            if (params.geometryParams() == null) {
                params = new ProcessParams(
                        params.spectrumParams(),
                        params.observationDetails(),
                        params.extraParams(),
                        params.videoParams(),
                        new GeometryParams(
                                null,
                                null,
                                false,
                                false,
                                false,
                                false,
                                false,
                                RotationKind.NONE,
                                AutocropMode.RADIUS_1_2,
                                DeconvolutionMode.NONE,
                                null,
                                false,
                                null,
                                false),
                        params.bandingCorrectionParams(),
                        params.requestedImages(),
                        params.claheParams(),
                        params.autoStretchParams(),
                        params.contrastEnhancement(),
                        params.enhancementParams()
                );
            }
            if (params.bandingCorrectionParams() == null) {
                params = new ProcessParams(
                        params.spectrumParams(),
                        params.observationDetails(),
                        params.extraParams(),
                        params.videoParams(),
                        params.geometryParams(),
                        new BandingCorrectionParams(
                                DEFAULT_BAND_SIZE,
                                DEFAULT_PASS_COUNT
                        ),
                        params.requestedImages(),
                        params.claheParams(),
                        params.autoStretchParams(),
                        params.contrastEnhancement(),
                        params.enhancementParams()
                );
            }
            if (params.requestedImages() == null) {
                params = new ProcessParams(
                        params.spectrumParams(),
                        params.observationDetails(),
                        params.extraParams(),
                        params.videoParams(),
                        params.geometryParams(),
                        params.bandingCorrectionParams(),
                        new RequestedImages(RequestedImages.FULL_MODE, List.of(0d), Set.of(), Set.of(), ImageMathParams.NONE, false),
                        params.claheParams(),
                        params.autoStretchParams(),
                        params.contrastEnhancement(),
                        params.enhancementParams()
                );
            }
            if (params.extraParams() == null) {
                params = params.withExtraParams(new ExtraParams(
                        false,
                        true,
                        EnumSet.of(ImageFormat.PNG),
                        FileNamingStrategy.DEFAULT_TEMPLATE,
                        FileNamingStrategy.DEFAULT_DATETIME_FORMAT,
                        FileNamingStrategy.DEFAULT_DATE_FORMAT,
                        false,
                        GlobeStyle.EQUATORIAL_COORDS
                ));
            }
            if (params.extraParams().datetimeFormat() == null) {
                params = params.withExtraParams(params.extraParams().withDateTimeFormat(FileNamingStrategy.DEFAULT_DATETIME_FORMAT));
            }
            if (params.extraParams().dateFormat() == null) {
                params = params.withExtraParams(params.extraParams().withDateFormat(FileNamingStrategy.DEFAULT_DATE_FORMAT));
            }
            if (params.claheParams() == null) {
                params = params.withClaheParams(createDefaultClaheParams());
            }
            if (params.autoStretchParams() == null) {
                params = params.withAutoStretchParams(createDefaultAutoStretchParams());
            }
            if (params.contrastEnhancement() == null) {
                params = params.withContrastEnhancement(ContrastEnhancement.AUTOSTRETCH);
            }
            if (params.enhancementParams() == null) {
                params = params.withEnhancementParams(new EnhancementParams(false, FlatCorrection.DEFAULT_LO_PERCENTILE, FlatCorrection.DEFAULT_HI_PERCENTILE, FlatCorrection.DEFAULT_ORDER, null, new JaggingCorrectionParams(false, JaggingCorrection.DEFAULT_SIGMA)));
            }
            if (params.extraParams().globeStyle() == null) {
                params = params.withExtraParams(params.extraParams().withGlobeStyle(GlobeStyle.EQUATORIAL_COORDS));
            }
            return params;
        }
        return null;
    }

    private static ClaheParams createDefaultClaheParams() {
        return new ClaheParams(ClaheStrategy.DEFAULT_TILE_SIZE, ClaheStrategy.DEFAULT_BINS, ClaheStrategy.DEFAULT_CLIP);
    }

    private static AutoStretchParams createDefaultAutoStretchParams() {
        return new AutoStretchParams(AutohistogramStrategy.DEFAULT_GAMMA, AutohistogramStrategy.DEFAULT_BACKGROUND_THRESHOLD, AutohistogramStrategy.DEFAULT_PROM_STRETCH);
    }

    public static String serializeToJson(ProcessParams params) {
        var gson = newGson();
        return gson.toJson(params);
    }

    public static void saveTo(ProcessParams params, File destination) {
        try {
            var parent = destination.getParentFile().toPath();
            createDirectoriesIfNeeded(parent);
            try (var writer = FilesUtils.newTextWriter(destination.toPath())) {
                var gson = newGson();
                writer.write(gson.toJson(params));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveDefaults(ProcessParams params) {
        var defaultsFile = resolveDefaultsFile();
        saveTo(params, defaultsFile.toFile());
    }

}
