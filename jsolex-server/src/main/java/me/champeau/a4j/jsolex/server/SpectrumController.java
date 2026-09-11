/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;
import me.champeau.a4j.jsolex.processing.params.SpectroHeliographsIO;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralWindowIdentifier;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Identifies the spectral lines visible in a frame sent by a capture software.
 */
@Controller("/api/spectrum")
public class SpectrumController {
    private static final int[] DEFAULT_BINNINGS = {1, 2};
    private static final int DEFAULT_AVERAGED_FRAMES = 4;
    private static final int MAX_AVERAGED_FRAMES = 64;
    private static final DateTimeFormatter DUMP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final SpectralWindowIdentifier identifier = new SpectralWindowIdentifier();

    /**
     * Creates the controller and prepares the reference spectrum in the background.
     */
    public SpectrumController() {
        Thread.startVirtualThread(SpectralWindowIdentifier::warmUp);
    }

    /**
     * Identifies the lines of a frame.
     *
     * @param body the frame: raw pixels, row after row, when width and height are given, an image file otherwise
     * @param width the frame width, for raw pixels
     * @param height the frame height, for raw pixels
     * @param format the pixel format of raw pixels (MONO8, MONO16, RGB24, RAW16...), or the file
     * format of the frame when it is not raw, "fits" or any format supported by ImageIO
     * @param pixelSize the sensor pixel size in micrometers, defaults to the observation details
     * @param binning the camera binning, both 1 and 2 are considered when absent
     * @param instrument the name of the spectroheliograph, defaults to the observation details
     * @param average how many frames the running average spans
     * @param dump whether to save the frame as received, as a FITS file in the temporary
     * directory, so that an identification which went wrong can be replayed offline
     * @return the identification as JSON
     */
    @Post("/identify")
    @Consumes(MediaType.ALL)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> identify(@Body byte[] body,
                                         @Nullable @QueryValue Integer width,
                                         @Nullable @QueryValue Integer height,
                                         @Nullable @QueryValue String format,
                                         @Nullable @QueryValue Double pixelSize,
                                         @Nullable @QueryValue Integer binning,
                                         @Nullable @QueryValue String instrument,
                                         @Nullable @QueryValue Integer average,
                                         @Nullable @QueryValue Boolean dump) {
        var start = System.nanoTime();
        var details = ProcessParamsIO.loadDefaults().observationDetails();
        var shg = instrument == null ? details.instrument() : findInstrument(instrument).orElse(null);
        if (shg == null) {
            return error("Unknown spectroheliograph: " + instrument);
        }
        var size = pixelSize != null ? pixelSize : details.pixelSize();
        if (size == null || size <= 0) {
            return error("The pixel size is not configured: pass the pixelSize query parameter or set it in the observation details");
        }
        ImageWrapper32 frame;
        try {
            frame = width != null && height != null ? rawFrame(body, width, height, format) : decodeFrame(body, format);
        } catch (IllegalArgumentException | IOException e) {
            return error(e.getMessage());
        }
        Path dumped = null;
        if (Boolean.TRUE.equals(dump)) {
            try {
                dumped = dumpFrame(frame);
            } catch (IOException e) {
                return error("Unable to save the frame: " + e.getMessage());
            }
        }
        var binnings = binning != null ? new int[]{binning} : DEFAULT_BINNINGS;
        var frames = average == null ? DEFAULT_AVERAGED_FRAMES : Math.clamp(average, 1, MAX_AVERAGED_FRAMES);
        var identification = identifier.identify(frame.data(), frame.width(), frame.height(), shg, size, frames, binnings);
        var json = toJson(identification, shg, size, (System.nanoTime() - start) / 1_000_000);
        if (dumped != null) {
            json.addProperty("dumped", dumped.toString());
        }
        return HttpResponse.ok(json.toString());
    }

    /**
     * Saves the frame exactly as received, so that the whole identification can be replayed
     * offline on it.
     */
    private static Path dumpFrame(ImageWrapper32 frame) throws IOException {
        var directory = Path.of(System.getProperty("java.io.tmpdir"), "jsolex-live-frames");
        Files.createDirectories(directory);
        var file = directory.resolve("frame-" + DUMP_TIMESTAMP.format(LocalDateTime.now()) + ".fits");
        FitsUtils.writeFitsFile(frame, file.toFile(), ProcessParamsIO.loadDefaults());
        return file;
    }

    /**
     * Forgets the previous identifications.
     *
     * @return an empty JSON object
     */
    @Delete("/identify")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> reset() {
        identifier.reset();
        return HttpResponse.ok("{}");
    }

    private static Optional<SpectroHeliograph> findInstrument(String label) {
        return SpectroHeliographsIO.loadDefaults().stream()
                .filter(shg -> shg.label().equalsIgnoreCase(label))
                .findFirst();
    }

    private static ImageWrapper32 rawFrame(byte[] body, int width, int height, String format) {
        if (format == null) {
            throw new IllegalArgumentException("The format query parameter is required for raw pixels, one of " + FrameDecoder.PixelFormat.supported());
        }
        var pixelFormat = FrameDecoder.PixelFormat.parse(format)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported pixel format: " + format + ", expected one of " + FrameDecoder.PixelFormat.supported()));
        return FrameDecoder.decode(body, width, height, pixelFormat);
    }

    private static ImageWrapper32 decodeFrame(byte[] body, String format) throws IOException {
        if ("fits".equalsIgnoreCase(format)) {
            var temp = Files.createTempFile("jsolex-frame", ".fits");
            try {
                Files.write(temp, body);
                return mono(FitsUtils.readFitsFile(temp.toFile()));
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        var image = ImageIO.read(new ByteArrayInputStream(body));
        if (image == null) {
            throw new IllegalArgumentException("Unsupported image format" + (format == null ? "" : ": " + format));
        }
        return mono(Loader.toImageWrapper(image, MutableMap.of()));
    }

    private static ImageWrapper32 mono(ImageWrapper image) {
        var unwrapped = image.unwrapToMemory();
        if (unwrapped instanceof RGBImage rgb) {
            return rgb.toMono();
        }
        return (ImageWrapper32) unwrapped;
    }

    private static HttpResponse<String> error(String message) {
        var json = new JsonObject();
        json.addProperty("error", message);
        return HttpResponse.badRequest(json.toString());
    }

    private static JsonObject toJson(SpectralWindowIdentifier.Identification identification,
                                     SpectroHeliograph instrument,
                                     double pixelSize,
                                     long durationMillis) {
        var json = new JsonObject();
        json.addProperty("spectrumFound", identification.spectrumFound());
        json.addProperty("identified", identification.identified());
        json.addProperty("width", identification.width());
        json.addProperty("height", identification.height());
        json.addProperty("instrument", instrument.label());
        json.addProperty("pixelSize", pixelSize);
        json.addProperty("flipped", identification.flipped());
        var polynomial = identification.polynomial();
        if (polynomial != null) {
            var coefficients = new JsonObject();
            coefficients.addProperty("a", polynomial.a());
            coefficients.addProperty("b", polynomial.b());
            coefficients.addProperty("c", polynomial.c());
            coefficients.addProperty("d", polynomial.d());
            json.add("polynomial", coefficients);
            json.addProperty("leftBorder", identification.leftBorder());
            json.addProperty("rightBorder", identification.rightBorder());
        }
        var anchor = identification.anchor();
        if (anchor != null) {
            var line = new JsonObject();
            line.addProperty("wavelength", round(anchor.wavelength().angstroms()));
            line.addProperty("name", anchor.name());
            line.addProperty("score", round(anchor.score()));
            line.addProperty("margin", round(anchor.margin()));
            line.addProperty("binning", anchor.binning());
            json.add("anchor", line);
            json.addProperty("angstromsPerPixel", identification.angstromsPerPixel());
            json.addProperty("dispersionScale", identification.dispersionScale());
        }
        json.addProperty("streak", identification.streak());
        json.addProperty("confidence", identification.confidence().name());
        var lines = new JsonArray();
        for (var line : identification.lines()) {
            var entry = new JsonObject();
            entry.addProperty("wavelength", round(line.wavelength().angstroms()));
            entry.addProperty("name", line.name());
            entry.addProperty("pixelShift", round(line.pixelShift()));
            entry.addProperty("depth", round(line.depth()));
            lines.add(entry);
        }
        json.add("lines", lines);
        json.addProperty("durationMillis", durationMillis);
        return json;
    }

    private static double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.3f", value));
    }
}
