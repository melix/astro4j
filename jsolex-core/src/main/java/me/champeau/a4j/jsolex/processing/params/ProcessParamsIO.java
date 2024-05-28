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

public abstract class ProcessParamsIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessParamsIO.class);

    private ProcessParamsIO() {

    }

    private static Path resolveDefaultsFile() {
        var jsolexDir = VersionUtil.getJsolexDir();
        try {
            Files.createDirectories(jsolexDir);
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
        return builder.create();
    }

    public static ProcessParams loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        ProcessParams params = readFrom(defaultsFile);
        if (params != null) {
            return params;
        }
        LOGGER.info("No config file found at {}. Using default parameters", defaultsFile);
        return new ProcessParams(
            new SpectrumParams(SpectralRay.AUTO, 0, 3, false),
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
                2.4),
            new ExtraParams(false, true, EnumSet.of(ImageFormat.PNG), FileNamingStrategy.DEFAULT_TEMPLATE, FileNamingStrategy.DEFAULT_DATETIME_FORMAT, FileNamingStrategy.DEFAULT_DATE_FORMAT),
            new VideoParams(ColorMode.MONO),
            new GeometryParams(null, null, false, false, false, false, true, RotationKind.NONE, AutocropMode.OFF, DeconvolutionMode.NONE, null),
            new BandingCorrectionParams(24, 3),
            new RequestedImages(RequestedImages.FULL_MODE, List.of(0d), Set.of(), ImageMathParams.NONE),
            createDefaultClaheParams(),
            createDefaultAutoStretchParams(),
            ContrastEnhancement.AUTOSTRETCH
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
                    params.contrastEnhancement()
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
                        true,
                        RotationKind.NONE,
                        AutocropMode.OFF,
                        DeconvolutionMode.NONE,
                        null),
                    params.bandingCorrectionParams(),
                    params.requestedImages(),
                    params.claheParams(),
                    params.autoStretchParams(),
                    params.contrastEnhancement()
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
                        24,
                        3
                    ),
                    params.requestedImages(),
                    params.claheParams(),
                    params.autoStretchParams(),
                    params.contrastEnhancement()
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
                    new RequestedImages(RequestedImages.FULL_MODE, List.of(0d), Set.of(), ImageMathParams.NONE),
                    params.claheParams(),
                    params.autoStretchParams(),
                    params.contrastEnhancement()
                );
            }
            if (params.extraParams() == null) {
                params = params.withExtraParams(new ExtraParams(
                    false,
                    true,
                    EnumSet.of(ImageFormat.PNG),
                    FileNamingStrategy.DEFAULT_TEMPLATE,
                    FileNamingStrategy.DEFAULT_DATETIME_FORMAT,
                    FileNamingStrategy.DEFAULT_DATE_FORMAT
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
            return params;
        }
        return null;
    }

    private static ClaheParams createDefaultClaheParams() {
        return new ClaheParams(ClaheStrategy.DEFAULT_TILE_SIZE, ClaheStrategy.DEFAULT_BINS, ClaheStrategy.DEFAULT_CLIP);
    }

    private static AutoStretchParams createDefaultAutoStretchParams() {
        return new AutoStretchParams(AutohistogramStrategy.DEFAULT_GAMMA);
    }

    public static String serializeToJson(ProcessParams params) {
        var gson = newGson();
        return gson.toJson(params);
    }

    public static void saveTo(ProcessParams params, File destination) {
        try {
            Files.createDirectories(destination.getParentFile().toPath());
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
