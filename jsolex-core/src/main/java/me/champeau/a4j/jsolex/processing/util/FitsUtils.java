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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import nom.tam.fits.header.DataDescription;
import nom.tam.fits.header.IFitsHeader;
import nom.tam.fits.header.InstrumentDescription;
import nom.tam.fits.header.Standard;
import nom.tam.fits.header.extra.SBFitsExt;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static nom.tam.fits.header.extra.NOAOExt.CAMERA;

class FitsUtils {
    private final ProcessParams params;
    private final File destination;

    private FitsUtils(ProcessParams params, File destination) {
        this.params = params;
        this.destination = destination;
    }

    static void writeFitsFile(ImageWrapper image, File destination, ProcessParams params) {
        new FitsUtils(params, destination).write(image);
    }

    private void write(ImageWrapper image) {
        if (Objects.requireNonNull(image) instanceof ImageWrapper32 mono) {
            writeMono(mono);
        } else if (image instanceof ColorizedImageWrapper colorized) {
            writeColorized(colorized);
        } else if (image instanceof RGBImage rgb) {
            writeRGB(rgb);
        }
    }

    private void writeRGB(RGBImage rgb) {
        try (var fits = new Fits()) {
            var hdu = FitsFactory.hduFactory(toRows(rgb.width(), rgb.r(), rgb.g(), rgb.b()));
            var header = hdu.getHeader();
            writeHeader(rgb, header);
            fits.addHDU(hdu);
            fits.write(destination);
        } catch (IOException | FitsException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeColorized(ColorizedImageWrapper colorized) {
        throw new UnsupportedOperationException();
    }

    private void writeMono(ImageWrapper32 mono) {
        try (var fits = new Fits()) {
            var hdu = FitsFactory.hduFactory(toRows(mono.width(), mono.data()));
            var header = hdu.getHeader();
            writeHeader(mono, header);
            fits.addHDU(hdu);
            fits.write(destination);
        } catch (IOException | FitsException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeHeader(ImageWrapper image, Header header) throws HeaderCardException {
        int naxis = image instanceof RGBImage ? 3 : 2;
        header.addValue(Standard.BITPIX, 16);
        header.addValue(Standard.NAXIS, naxis);
        header.addValue(Standard.NAXIS1, image.width());
        header.addValue(Standard.NAXIS2, image.height());
        if (image instanceof RGBImage) {
            header.addValue("NAXIS3", 3, null);
        }
        header.addValue(Standard.BSCALE, 1);
        header.addValue(Standard.BZERO, 0);
        header.addValue(Standard.OBJECT, "Sun");
        var obs = params.observationDetails();
        maybeAdd(header, Standard.OBSERVER, obs.observer());
        maybeAdd(header, Standard.INSTRUME, obs.instrument());
        maybeAdd(header, CAMERA, obs.camera());
        header.addValue(Standard.DATE_OBS, obs.date().format(
                DateTimeFormatter.ISO_DATE_TIME
        ));
        var email = obs.email();
        if (notEmpty(email)) {
            header.addValue("CONTACT", normalize(email), "Contact email");
        }
        var coordinates = obs.coordinates();
        if (coordinates != null) {
            header.addValue(SBFitsExt.SITELAT, String.valueOf(coordinates.a()));
            header.addValue(SBFitsExt.SITELONG, String.valueOf(coordinates.b()));
        }
        header.addValue(DataDescription.CREATOR, "JSol'Ex");
        var fl = obs.focalLength();
        if (fl != null) {
            header.addValue(SBFitsExt.FOCALLEN, fl.floatValue());
        }
        var aperture = obs.aperture();
        if (aperture != null) {
            header.addValue(InstrumentDescription.APERTURE, String.valueOf(aperture));
        }
        var wavelength = params.spectrumParams().ray().getWavelength();
        if (wavelength != 0) {
            header.addValue("WAVELNTH", wavelength, "Wavelength (nm)");
        }
    }

    private static void maybeAdd(Header header, IFitsHeader key, String string) throws HeaderCardException {
        if (notEmpty(string)) {
            header.addValue(key, normalize(string));
        }
    }

    private static String normalize(String string) {
        return Normalizer.normalize(string, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    }

    private static boolean notEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    private short[] toRows(int width, float[] data) {
        var rowCount = data.length / width;
        var result = new short[data.length];
        for (int y = 0; y < rowCount; y++) {
            for (int x = 0; x < width; x++) {
                int value = Math.round(data[x + y * width]);
                if (value < 0) {
                    value = 0;
                } else if (value > 65536) {
                    value = 65536;
                }
                result[x + y * width] = (short) ((value-32768) & 0xFFFF);
            }
        }
        return result;
    }

    private short[][] toRows(int width, float[] r, float[] g, float[] b) {
        var rgb = new short[3][];
        rgb[0] = toRows(width, r);
        rgb[1] = toRows(width, g);
        rgb[2] = toRows(width, b);
        return rgb;
    }
}
