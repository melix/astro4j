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
import me.champeau.a4j.ser.ColorMode;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

abstract class ProcessParamsIO {
    private ProcessParamsIO() {

    }


    private static Path resolveDefaultsFile() {
        var jsolexDir = Paths.get(System.getProperty("user.home"), ".jsolex");
        try {
            Files.createDirectories(jsolexDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jsolexDir.resolve("process-params.json");
    }

    private static Gson newGson() {
        var builder = new Gson().newBuilder();
        builder.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer());
        builder.registerTypeAdapter(GeometryParams.class, new GeometryParamsSerializer());
        return builder.create();
    }

    public static ProcessParams loadDefaults() {
        var defaultsFile = resolveDefaultsFile();
        if (Files.exists(defaultsFile)) {
            try (var reader = new InputStreamReader(new FileInputStream(defaultsFile.toFile()), StandardCharsets.UTF_8)) {
                Gson gson = newGson();
                var params = gson.fromJson(reader, ProcessParams.class);
                if (params != null) {
                    if (params.videoParams() == null) {
                        // happens if loading an old config file
                        params = new ProcessParams(
                                params.spectrumParams(),
                                params.observationDetails(),
                                params.debugParams(),
                                new VideoParams(ColorMode.MONO),
                                params.geometryParams(),
                                params.bandingCorrectionParams()
                        );
                    }
                    if (params.geometryParams() == null) {
                        params = new ProcessParams(
                                params.spectrumParams(),
                                params.observationDetails(),
                                params.debugParams(),
                                params.videoParams(),
                                new GeometryParams(
                                        null,
                                        null
                                ),
                                params.bandingCorrectionParams()
                        );
                    }
                    if (params.bandingCorrectionParams() == null) {
                        params = new ProcessParams(
                                params.spectrumParams(),
                                params.observationDetails(),
                                params.debugParams(),
                                params.videoParams(),
                                params.geometryParams(),
                                new BandingCorrectionParams(
                                        24,
                                        3
                                )
                        );
                    }
                    return params;
                }
            } catch (IOException e) {
                // fallback to default params
            }
        }
        return new ProcessParams(
                new SpectrumParams(SpectralRay.H_ALPHA, SpectralRay.H_ALPHA.getDetectionThreshold(), 0),
                new ObservationDetails(
                        "",
                        "Sol'Ex",
                        "",
                        null,
                        LocalDateTime.now().atZone(ZoneId.of("UTC")),
                        ""
                ),
                new DebugParams(false, true),
                new VideoParams(ColorMode.MONO),
                new GeometryParams(null, null),
                new BandingCorrectionParams(24, 3)
        );
    }

    public static void saveDefaults(ProcessParams params) {
        var defaultsFile = resolveDefaultsFile();
        try (var writer = new OutputStreamWriter(new FileOutputStream(defaultsFile.toFile()), StandardCharsets.UTF_8)) {
            var gson = newGson();
            writer.write(gson.toJson(params));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
