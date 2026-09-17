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
import io.micronaut.serde.annotation.Serdeable;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
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
import java.util.List;
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
    public HttpResponse<?> identify(@Body byte[] body,
                                         @Nullable @QueryValue Integer width,
                                         @Nullable @QueryValue Integer height,
                                         @Nullable @QueryValue String format,
                                         @Nullable @QueryValue Double pixelSize,
                                         @Nullable @QueryValue Integer binning,
                                         @Nullable @QueryValue String instrument,
                                         @Nullable @QueryValue Integer average,
                                         @Nullable @QueryValue Boolean dump) {
        var start = System.nanoTime();
        var params = ProcessParamsIO.loadDefaults();
        var details = params.observationDetails();
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
                dumped = dumpFrame(frame, params);
            } catch (IOException e) {
                return error("Unable to save the frame: " + e.getMessage());
            }
        }
        var binnings = binning != null ? new int[]{binning} : DEFAULT_BINNINGS;
        var frames = average == null ? DEFAULT_AVERAGED_FRAMES : Math.clamp(average, 1, MAX_AVERAGED_FRAMES);
        var identification = identifier.identify(frame.data(), frame.width(), frame.height(), shg, size, frames, binnings);
        return HttpResponse.ok(toResponse(identification, shg, size, (System.nanoTime() - start) / 1_000_000, dumped));
    }

    /**
     * Saves the frame exactly as received, so that the whole identification can be replayed
     * offline on it.
     */
    private static Path dumpFrame(ImageWrapper32 frame, ProcessParams params) throws IOException {
        var directory = Path.of(System.getProperty("java.io.tmpdir"), "jsolex-live-frames");
        Files.createDirectories(directory);
        var file = directory.resolve("frame-" + DUMP_TIMESTAMP.format(LocalDateTime.now()) + ".fits");
        FitsUtils.writeFitsFile(frame, file.toFile(), params);
        return file;
    }

    /**
     * Forgets the previous identifications.
     *
     * @return an empty JSON object
     */
    @Delete("/identify")
    public HttpResponse<?> reset() {
        identifier.reset();
        return HttpResponse.ok();
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

    private static HttpResponse<ErrorResponse> error(String message) {
        return HttpResponse.badRequest(new ErrorResponse(message));
    }

    private static IdentificationResponse toResponse(SpectralWindowIdentifier.Identification identification,
                                                     SpectroHeliograph instrument,
                                                     double pixelSize,
                                                     long durationMillis,
                                                     @Nullable Path dumped) {
        var polynomial = identification.polynomial();
        var anchor = identification.anchor();
        var identified = anchor != null;
        return new IdentificationResponse(
                identification.spectrumFound(),
                identified,
                identification.width(),
                identification.height(),
                instrument.label(),
                pixelSize,
                identification.flipped(),
                polynomial == null ? null : new Polynomial(polynomial.a(), polynomial.b(), polynomial.c(), polynomial.d()),
                polynomial == null ? null : identification.leftBorder(),
                polynomial == null ? null : identification.rightBorder(),
                identified ? new AnchorResponse(round(anchor.wavelength().angstroms()), anchor.name(), round(anchor.score()), round(anchor.margin()), anchor.binning()) : null,
                identified ? identification.angstromsPerPixel() : null,
                identified ? identification.dispersionScale() : null,
                identification.streak(),
                identification.confidence().name(),
                identification.lines().stream()
                        .map(line -> new LineResponse(round(line.wavelength().angstroms()), line.name(), round(line.pixelShift()), round(line.depth())))
                        .toList(),
                durationMillis,
                dumped == null ? null : dumped.toString());
    }

    private static double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.3f", value));
    }

    /**
     * The reason a request was refused.
     *
     * @param error the message
     */
    @Serdeable
    public record ErrorResponse(String error) {
    }

    /**
     * The curvature of the lines: the row of a line at column {@code x} is
     * {@code a x^3 + b x^2 + c x + d} plus the pixel shift of the line.
     *
     * @param a the cubic coefficient
     * @param b the quadratic coefficient
     * @param c the linear coefficient
     * @param d the constant
     */
    @Serdeable
    public record Polynomial(double a, double b, double c, double d) {
    }

    /**
     * A line found in the frame.
     *
     * @param wavelength the wavelength, in angstroms
     * @param name the name of the line, or null when it has none
     * @param pixelShift the position of the line relative to the centre line, in pixels
     * @param depth the depth of the line, as a fraction of the local continuum
     */
    @Serdeable
    public record LineResponse(double wavelength, @Nullable String name, double pixelShift, double depth) {
    }

    /**
     * The line the frame is centred on.
     *
     * @param wavelength the wavelength, in angstroms
     * @param name the name of the line, or null when it has none
     * @param score the correlation with the reference spectrum
     * @param margin the lead over the best competing line
     * @param binning the binning which best explains the frame
     */
    @Serdeable
    public record AnchorResponse(double wavelength, @Nullable String name, double score, double margin, int binning) {
    }

    /**
     * The identification of a frame.
     *
     * @param spectrumFound whether the frame carries a spectrum
     * @param identified whether a line was identified
     * @param width the frame width
     * @param height the frame height
     * @param instrument the name of the spectroheliograph
     * @param pixelSize the pixel size, in micrometers
     * @param flipped whether the rows of the frame run the other way than the wavelength
     * @param polynomial the curvature of the lines, when a spectrum was found
     * @param leftBorder the first column carrying spectrum, when a spectrum was found
     * @param rightBorder the column following the last one carrying spectrum, when a spectrum was found
     * @param anchor the centre line, when identified
     * @param angstromsPerPixel the dispersion at the centre line, when identified
     * @param dispersionScale the ratio of the dispersion to its theoretical value, when identified
     * @param streak how many successive frames identified the same line
     * @param confidence how much the identification can be trusted
     * @param lines the lines found in the frame
     * @param durationMillis how long the identification took
     * @param dumped the file the frame was saved to, when requested
     */
    @Serdeable
    public record IdentificationResponse(boolean spectrumFound,
                                         boolean identified,
                                         int width,
                                         int height,
                                         String instrument,
                                         double pixelSize,
                                         boolean flipped,
                                         @Nullable Polynomial polynomial,
                                         @Nullable Integer leftBorder,
                                         @Nullable Integer rightBorder,
                                         @Nullable AnchorResponse anchor,
                                         @Nullable Double angstromsPerPixel,
                                         @Nullable Double dispersionScale,
                                         int streak,
                                         String confidence,
                                         List<LineResponse> lines,
                                         long durationMillis,
                                         @Nullable String dumped) {
    }
}
