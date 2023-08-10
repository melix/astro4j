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
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.math.tuples.DoubleSextuplet;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTable;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.FitsFactory;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import nom.tam.fits.ImageHDU;
import nom.tam.fits.header.DataDescription;
import nom.tam.fits.header.IFitsHeader;
import nom.tam.fits.header.InstrumentDescription;
import nom.tam.fits.header.Standard;
import nom.tam.fits.header.extra.SBFitsExt;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static nom.tam.fits.header.extra.NOAOExt.CAMERA;

public class FitsUtils {
    public static final String JSOLEX_HEADER_KEY = "JSOLEX";
    public static final String ELLIPSE_VALUE = "Ellipse";
    public static final String PROCESS_PARAMS_VALUE = "PrParams";
    private final ProcessParams params;
    private final File destination;

    private FitsUtils(ProcessParams params, File destination) {
        this.params = params;
        this.destination = destination;
    }

    static void writeFitsFile(ImageWrapper image, File destination, ProcessParams params) {
        new FitsUtils(params, destination).write(image);
    }

    public static ImageWrapper readFitsFile(File source) {
        try (var fits = new Fits(source)) {
            float[] data = null;
            float[][] rgb = null;
            int rows = 0;
            int cols = 0;
            Map<Class<?>, Object> metadata = new HashMap<>();
            var hdus = fits.read();
            for (BasicHDU<?> hdu : hdus) {
                if (hdu instanceof ImageHDU imageHdu) {
                    var kernel = imageHdu.getKernel();
                    if (kernel instanceof short[][] mono) {
                        rows = mono.length;
                        cols = rows == 0 ? 0 : mono[0].length;
                        data = readChannel(mono, rows, cols);
                    } else if (kernel instanceof short[][][] channels) {
                        rgb = new float[3][];
                        for (int i = 0; i < channels.length; i++) {
                            short[][] channel = channels[i];
                            rows = channel.length;
                            cols = rows == 0 ? 0 : channel[0].length;
                            rgb[i] = readChannel(channel, rows, cols);
                        }
                    } else {
                        throw new UnsupportedOperationException("Unsupported FITS file format");
                    }
                } else if (hdu instanceof BinaryTableHDU binaryTableHdu) {
                    readMetadata(binaryTableHdu, metadata);
                }
            }
            if (data != null) {
                return new ImageWrapper32(cols, rows, data, metadata);
            }
            if (rgb != null) {
                return new RGBImage(cols, rows, rgb[0], rgb[1], rgb[2], metadata);
            }

        } catch (IOException | FitsException e) {
            throw new ProcessingException(e);
        }
        throw new UnsupportedOperationException("Unsupported FITS file format");
    }

    private static float[] readChannel(short[][] mono, int rows, int cols) {
        float[] data;
        data = new float[rows * cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                data[x + y * cols] = mono[y][x] + 32768f;
            }
        }
        return data;
    }

    private static void readMetadata(BinaryTableHDU binaryTableHdu, Map<Class<?>, Object> metadata) throws FitsException {
        var iterator = binaryTableHdu.getHeader().iterator();
        while (iterator.hasNext()) {
            var card = iterator.next();
            if (JSOLEX_HEADER_KEY.equals(card.getKey())) {
                if (ELLIPSE_VALUE.equals(card.getValue())) {
                    var cart = new double[6];
                    var binaryTable = binaryTableHdu.getData();
                    for (int i = 0; i < cart.length; i++) {
                        cart[i] = binaryTable.getDouble(0, i);
                    }
                    metadata.put(Ellipse.class, Ellipse.ofCartesian(new DoubleSextuplet(
                            cart[0],
                            cart[1],
                            cart[2],
                            cart[3],
                            cart[4],
                            cart[5]
                    )));
                } else if (PROCESS_PARAMS_VALUE.equals(card.getValue())) {
                    var bytes = (byte[]) binaryTableHdu.getData().get(0, 0);
                    var json = new String(bytes, StandardCharsets.UTF_8);
                    var pp = ProcessParamsIO.readFrom(new StringReader(json));
                    metadata.put(ProcessParams.class, pp);
                }
            }
        }
    }

    private void write(ImageWrapper image) {
        if (image instanceof FileBackedImage fbi) {
            image = fbi.unwrapToMemory();
        }
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
            writeMetadata(rgb, fits);
            fits.write(destination);
        } catch (IOException | FitsException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeColorized(ColorizedImageWrapper colorized) {
        var rgb = colorized.converter().apply(colorized.mono().data());
        writeRGB(new RGBImage(colorized.width(), colorized.height(), rgb[0], rgb[1], rgb[2], colorized.metadata()));
    }

    private void writeMono(ImageWrapper32 mono) {
        try (var fits = new Fits()) {
            var hdu = FitsFactory.hduFactory(toRows(mono.width(), mono.data()));
            var header = hdu.getHeader();
            writeHeader(mono, header);
            fits.addHDU(hdu);
            writeMetadata(mono, fits);
            fits.write(destination);
        } catch (IOException | FitsException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeMetadata(ImageWrapper image, Fits fits) throws FitsException {
        if (!image.metadata().isEmpty()) {
            writeEllipse(image, fits);
            writeProcessParams(image, fits);
        }
    }

    private static void writeEllipse(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(Ellipse.class);
        if (metadata.isPresent()) {
            var ellipse = metadata.get();
            var table = new BinaryTable();
            var cart = ellipse.getCartesianCoefficients();
            table.addRow(new Double[]{
                    cart.a(),
                    cart.b(),
                    cart.c(),
                    cart.d(),
                    cart.e(),
                    cart.f()
            });
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, ELLIPSE_VALUE, "Ellipse parameters");
            fits.addHDU(binaryTableHDU);
        }
    }

    private static void writeProcessParams(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(ProcessParams.class);
        if (metadata.isPresent()) {
            var processParams = metadata.get();
            var json = ProcessParamsIO.serializeToJson(processParams);
            var table = new BinaryTable();
            table.addRow(new Object[] { json.getBytes(StandardCharsets.UTF_8) });
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, PROCESS_PARAMS_VALUE, "Process parameters");
            fits.addHDU(binaryTableHDU);
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
        var wavelength = params.spectrumParams().ray().wavelength();
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
                result[x + y * width] = (short) ((value - 32768) & 0xFFFF);
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
