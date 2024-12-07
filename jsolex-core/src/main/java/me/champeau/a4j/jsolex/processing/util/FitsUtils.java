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

import me.champeau.a4j.jsolex.processing.expr.stacking.DistorsionMap;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static nom.tam.fits.header.extra.NOAOExt.CAMERA;

public class FitsUtils {
    public static final String JSOLEX_HEADER_KEY = "JSOLEX";
    public static final String ELLIPSE_VALUE = "Ellipse";
    public static final String PROCESS_PARAMS_VALUE = "PrParams";
    public static final String SOLAR_PARAMS_VALUE = "SoParams";
    public static final String TRANSFORMS_VALUE = "Transforms";
    public static final String PIXELSHIFT_VALUE = "PixelShift";
    public static final String REDSHIFTS_VALUE = "Redshifts";
    public static final String REFCOORDS_VALUE = "RefCoords";
    public static final String SOURCEINFO_VALUE = "SourceInfo";
    public static final String METADATA_TABLE_VALUE = "TMetadata";
    public static final String DISTORSION_MAP_VALUE = "DistorsionMap";

    // INTI metadata
    public static final String INTI_CENTER_X = "CENTER_X";
    public static final String INTI_CENTER_Y = "CENTER_Y";
    public static final String INTI_SOLAR_R = "SOLAR_R";

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
            float[][] data = null;
            float[][][] rgb = null;
            int rows = 0;
            int cols = 0;
            Map<Class<?>, Object> metadata = new HashMap<>();
            var hdus = fits.read();
            boolean isJSolEx = false;
            for (BasicHDU<?> basicHDU : hdus) {
                if (basicHDU instanceof BinaryTableHDU binaryTableHdu) {
                    isJSolEx = isJSolEx(binaryTableHdu);
                    if (isJSolEx) {
                        break;
                    }
                }
            }
            for (BasicHDU<?> hdu : hdus) {
                if (hdu instanceof ImageHDU imageHdu) {
                    var kernel = imageHdu.getKernel();
                    int bzero = 0;
                    if (imageHdu.getHeader().containsKey(Standard.BZERO)) {
                        bzero = imageHdu.getHeader().getIntValue(Standard.BZERO);
                        if (isJSolEx && bzero == 0) {
                            // fix for older JSol'Ex files
                            bzero = 32768;
                        }
                    }
                    if (kernel instanceof short[][] mono) {
                        rows = mono.length;
                        cols = rows == 0 ? 0 : mono[0].length;
                        data = readChannel(mono, rows, cols, bzero);
                    } else if (kernel instanceof short[][][] channels) {
                        rgb = new float[3][][];
                        for (int i = 0; i < channels.length; i++) {
                            short[][] channel = channels[i];
                            rows = channel.length;
                            cols = rows == 0 ? 0 : channel[0].length;
                            rgb[i] = readChannel(channel, rows, cols, bzero);
                        }
                    } else {
                        throw new UnsupportedOperationException("Unsupported FITS file format");
                    }
                    readMetadata(imageHdu.getHeader(), metadata);
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

    private static void readMetadata(Header header, Map<Class<?>, Object> metadata) {
        // try to see if we can read some INTI metadata and convert it to JSol'Ex metadata
        if (header.containsKey(INTI_CENTER_X) && header.containsKey(INTI_CENTER_Y) && header.containsKey(INTI_SOLAR_R)) {
            // cool, we have an ellipse!
            double centerX = header.getIntValue(INTI_CENTER_X);
            double centerY = header.getIntValue(INTI_CENTER_Y);
            double solarR = header.getIntValue(INTI_SOLAR_R);
            // need to convert to Ellipse parameters
            var ellipse = Ellipse.ofCartesian(new DoubleSextuplet(
                1,
                0,
                1,
                -2d * centerX,
                -2d * centerY,
                centerX * centerX + centerY * centerY - solarR * solarR
            ));
            metadata.put(Ellipse.class, ellipse);
        }
    }

    private static float[][] readChannel(short[][] mono, int rows, int cols, int bzero) {
        float[][] data = new float[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                data[y][x] = mono[y][x] + bzero;
            }
        }
        return data;
    }

    private static boolean isJSolEx(BinaryTableHDU binaryTableHdu) {
        var iterator = binaryTableHdu.getHeader().iterator();
        while (iterator.hasNext()) {
            var card = iterator.next();
            if (JSOLEX_HEADER_KEY.equals(card.getKey())) {
                return true;
            }
        }
        return false;
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
                } else if (SOLAR_PARAMS_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var sp = new SolarParameters(
                        binaryTable.getNumber(0, 0).intValue(),
                        binaryTable.getDouble(0, 1),
                        binaryTable.getDouble(0, 2),
                        binaryTable.getDouble(0, 3),
                        binaryTable.getDouble(0, 4)
                    );
                    metadata.put(SolarParameters.class, sp);
                } else if (TRANSFORMS_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var transforms = new TransformationHistory();
                    Object[] row = binaryTable.getRow(0);
                    for (Object o : row) {
                        var bytes = (byte[]) o;
                        transforms = transforms.transform(new String(bytes, StandardCharsets.UTF_8));
                    }
                    metadata.put(TransformationHistory.class, transforms);
                } else if (PIXELSHIFT_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var pixelShift = new PixelShift(binaryTable.getDouble(0, 0));
                    metadata.put(PixelShift.class, pixelShift);
                } else if (REFCOORDS_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    int cpt = binaryTable.getNRows();
                    var values = new ArrayList<ReferenceCoords.Operation>();
                    for (int i = 0; i < cpt; i++) {
                        var kind = binaryTable.getString(i, 0);
                        var value = binaryTable.getDouble(i, 1);
                        values.add(new ReferenceCoords.Operation(ReferenceCoords.OperationKind.valueOf(kind), value));
                    }
                    metadata.put(ReferenceCoords.class, new ReferenceCoords(Collections.unmodifiableList(values)));
                } else if (SOURCEINFO_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var fileName = (String) binaryTable.get(0, 0);
                    var parentName = (String) binaryTable.get(0, 1);
                    var date = ZonedDateTime.parse((String) binaryTable.get(0, 2));
                    metadata.put(SourceInfo.class, new SourceInfo(fileName, parentName, date));
                } else if (REDSHIFTS_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    int cpt = binaryTable.getNRows();
                    var values = new ArrayList<RedshiftArea>();
                    for (int i = 0; i < cpt; i++) {
                        var redshift = new RedshiftArea(
                            binaryTable.getSize() == 10 ? binaryTable.getString(i, 9) : null,
                            binaryTable.getNumber(i, 0).intValue(),
                            binaryTable.getNumber(i, 1).intValue(),
                            binaryTable.getNumber(i, 2).doubleValue(),
                            binaryTable.getNumber(i, 3).intValue(),
                            binaryTable.getNumber(i, 4).intValue(),
                            binaryTable.getNumber(i, 5).intValue(),
                            binaryTable.getNumber(i, 6).intValue(),
                            binaryTable.getNumber(i, 7).intValue(),
                            binaryTable.getNumber(i, 8).intValue()
                        );
                        values.add(redshift);
                    }
                    metadata.put(Redshifts.class, new Redshifts(values));
                } else if (METADATA_TABLE_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var table = new HashMap<String, String>();
                    for (int i = 0; i < binaryTable.getNRows(); i++) {
                        table.put((String) binaryTable.get(i, 0), String.valueOf(binaryTable.get(i, 1)));
                    }
                    metadata.put(MetadataTable.class, new MetadataTable(table));
                } else if (DISTORSION_MAP_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var bytes = (byte[]) binaryTable.get(0, 0);
                    metadata.put(DistorsionMap.class, DistorsionMap.loadFrom(new ByteArrayInputStream(bytes)));
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
            writeSolarParams(image, fits);
            writeTransformationHistory(image, fits);
            writePixelShift(image, fits);
            writeRefCoords(image, fits);
            writeRedshifts(image, fits);
            writeSourceInfo(image, fits);
            writeMetadataTable(image, fits);
            writeDistorsionMap(image, fits);
        }
    }

    private static void writeDistorsionMap(ImageWrapper image, Fits fits) {
        image.findMetadata(DistorsionMap.class).ifPresent(map -> {
            var baos = new ByteArrayOutputStream();
            try {
                map.saveTo(baos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            var table = new BinaryTable();
            table.addRow(new Object[]{baos.toByteArray()});
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, DISTORSION_MAP_VALUE, "Distorsion map");
            try {
                fits.addHDU(binaryTableHDU);
            } catch (FitsException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void writeMetadataTable(ImageWrapper image, Fits fits) {
        image.findMetadata(MetadataTable.class).ifPresent(metadataTable -> {
            var table = new BinaryTable();
            metadataTable.properties().forEach((key, value) -> table.addRow(new Object[]{key, value}));
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, METADATA_TABLE_VALUE, "Metadata table");
            try {
                fits.addHDU(binaryTableHDU);
            } catch (FitsException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void writeRedshifts(ImageWrapper image, Fits fits) {
        image.findMetadata(Redshifts.class).ifPresent(redshifts -> {
            var table = new BinaryTable();
            redshifts.redshifts().forEach(redshift -> table.addRow(new Object[]{
                redshift.pixelShift(),
                redshift.relPixelShift(),
                redshift.kmPerSec(),
                redshift.x1(),
                redshift.y1(),
                redshift.x2(),
                redshift.y2(),
                redshift.maxX(),
                redshift.maxY(),
                redshift.id()
            }));
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, REDSHIFTS_VALUE, "Measured redshifts");
            try {
                fits.addHDU(binaryTableHDU);
            } catch (FitsException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void writePixelShift(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(PixelShift.class);
        if (metadata.isPresent()) {
            var pixelShift = metadata.get();
            var table = new BinaryTable();
            table.addRow(new Object[]{pixelShift.pixelShift()});
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, PIXELSHIFT_VALUE, "Pixel shift");
            fits.addHDU(binaryTableHDU);
        }
    }

    private static void writeRefCoords(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(ReferenceCoords.class);
        if (metadata.isPresent()) {
            var referenceCoords = metadata.get();
            var table = new BinaryTable();
            referenceCoords.operations().forEach(op -> table.addRow(new Object[]{op.kind().name(), op.value()}));
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, REFCOORDS_VALUE, "Reference coordinate transforms");
            fits.addHDU(binaryTableHDU);
        }
    }

    private static void writeSourceInfo(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(SourceInfo.class);
        if (metadata.isPresent()) {
            var sourceInfo = metadata.get();
            var table = new BinaryTable();
            table.addRow(new Object[]{sourceInfo.serFileName(), sourceInfo.parentDirName(), sourceInfo.dateTime().toString()});
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, SOURCEINFO_VALUE, "Source file information");
            fits.addHDU(binaryTableHDU);
        }
    }

    private static void writeTransformationHistory(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(TransformationHistory.class);
        if (metadata.isPresent()) {
            var history = metadata.get();
            var table = new BinaryTable();
            table.addRow(history.transforms().stream().map(s -> s.getBytes(StandardCharsets.UTF_8)).toArray());
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, TRANSFORMS_VALUE, "Transformation history");
            fits.addHDU(binaryTableHDU);
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

    private static void writeSolarParams(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(SolarParameters.class);
        if (metadata.isPresent()) {
            var sp = metadata.get();
            var table = new BinaryTable();
            table.addRow(new Object[]{
                sp.carringtonRotation(),
                sp.b0(),
                sp.l0(),
                sp.p(),
                sp.apparentSize()
            });
            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, SOLAR_PARAMS_VALUE, "Solar parameters");
            fits.addHDU(binaryTableHDU);
        }
    }

    private static void writeProcessParams(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(ProcessParams.class);
        if (metadata.isPresent()) {
            var processParams = metadata.get();
            var json = ProcessParamsIO.serializeToJson(processParams);
            var table = new BinaryTable();
            table.addRow(new Object[]{json.getBytes(StandardCharsets.UTF_8)});
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
        header.addValue(Standard.BZERO, 32768);
        header.addValue(Standard.OBJECT, "Sun");
        var obs = params.observationDetails();
        maybeAdd(header, Standard.OBSERVER, obs.observer());
        maybeAdd(header, Standard.INSTRUME, obs.instrument().label());
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
        var obsParams = params.observationDetails();
        // alter the wavelength according to the pixel shift
        if (wavelength != 0) {
            var pixelShift = image.findMetadata(PixelShift.class).map(PixelShift::pixelShift);
            var pixelSize = obsParams.pixelSize();
            var binning = obsParams.binning();
            if (pixelShift.isPresent() && pixelSize != null && pixelSize > 0 && binning != null && binning > 0) {
                var dispersion = SpectrumAnalyzer.computeSpectralDispersionNanosPerPixel(obsParams.instrument(), wavelength, pixelSize * binning);
                wavelength += pixelShift.get() * dispersion;
            }
            header.addValue("WAVELNTH", wavelength, "Wavelength (nm)");
        }
    }

    private static void maybeAdd(Header header, IFitsHeader key, String string) throws HeaderCardException {
        if (notEmpty(string)) {
            header.addValue(key, normalize(string));
        }
    }

    private static String normalize(String string) {
        return Normalizer.normalize(string, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^\\x20-\\x7E]", "_");
    }

    private static boolean notEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    private short[] toRows(int width, float[][] data) {
        var rowCount = data.length;
        var result = new short[rowCount * width];
        for (int y = 0; y < rowCount; y++) {
            for (int x = 0; x < width; x++) {
                int value = Math.round(data[y][x]);
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

    private short[][] toRows(int width, float[][] r, float[][] g, float[][] b) {
        var rgb = new short[3][];
        rgb[0] = toRows(width, r);
        rgb[1] = toRows(width, g);
        rgb[2] = toRows(width, b);
        return rgb;
    }
}
