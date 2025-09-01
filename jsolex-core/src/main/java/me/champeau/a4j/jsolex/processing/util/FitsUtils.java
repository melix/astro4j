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
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegion;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.detection.Flare;
import me.champeau.a4j.jsolex.processing.sun.detection.Flares;
import me.champeau.a4j.jsolex.processing.sun.detection.RedshiftArea;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.MetadataTable;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.math.Point2D;
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static nom.tam.fits.header.ObservationDescription.OBJNAME;
import static nom.tam.fits.header.extra.MaxImDLExt.*;
import static nom.tam.fits.header.extra.NOAOExt.CAMERA;

public class FitsUtils {

    private static final AtomicBoolean DEFAULT_PIPP_COMPATIBILITY = new AtomicBoolean(false);

    public static final String JSOLEX_HEADER_KEY = "JSOLEX";
    public static final String ELLIPSE_VALUE = "Ellipse";
    public static final String PROCESS_PARAMS_VALUE = "PrParams";
    public static final String SOLAR_PARAMS_VALUE = "SoParams";
    public static final String TRANSFORMS_VALUE = "Transforms";
    public static final String PIXELSHIFT_VALUE = "PixelShift";
    public static final String REDSHIFTS_VALUE = "Redshifts";
    public static final String REFCOORDS2_VALUE = "RefCoords2";
    public static final int REF_COORDS_VERSION = 1;
    public static final String SOURCEINFO_VALUE = "SourceInfo";
    public static final String METADATA_TABLE_VALUE = "TMetadata";
    public static final String DISTORSION_MAP_VALUE = "DistorsionMap";
    public static final String ACTIVE_REGION_VALUE = "AR";
    public static final String FLARE = "FLARE";

    // INTI metadata
    public static final String CENTER_X = "CENTER_X";
    public static final String INTI_CENTER_X = "INTI_XC";
    public static final String CENTER_Y = "CENTER_Y";
    public static final String INTI_CENTER_Y = "INTI_YC";
    public static final String SOLAR_R = "SOLAR_R";
    public static final String INTI_SOLAR_R = "INTI_R";

    // Bounding box in INTI
    public static final String INTI_X1 = "INTI_X1";
    public static final String INTI_Y1 = "INTI_Y1";
    public static final String INTI_X2 = "INTI_X2";
    public static final String INTI_Y2 = "INTI_Y2";

    // BASS2000 Specific
    public static final String SPECTRO = "SPECTRO";
    public static final String ERF = "ERF";
    public static final String SOLAR_P = "SOLAR_P";
    public static final String BIN1 = "BIN1";
    public static final String BIN2 = "BIN2";
    public static final String DIAPH = "DIAPH";
    public static final String FEQUIV = "FEQUIV";
    public static final String CAMPIX = "CAMPIX";
    public static final String FCOL = "FCOL";
    public static final String FCAM = "FCAM";
    public static final String GROOVES = "GROOVES";
    public static final String ORDER = "ORDER";
    public static final String SHGANGLE = "SHGANGLE";
    public static final String SLWIDTH = "SLWIDTH";
    public static final String SLHEIGHT = "SLHEIGHT";
    public static final String PHYSPARA = "PHYSPARA";
    public static final String WAVEUNIT = "WAVEUNIT";
    public static final String FILENAME = "FILENAME";
    public static final String CONTACT = "CONTACT";
    public static final String SEP_LAT = "SEP_LAT";
    public static final String SEP_LON = "SEP_LON";
    public static final String CAR_ROT = "CAR_ROT";
    public static final String WAVEBAND = "WAVEBAND";

    private final ProcessParams params;
    private final File destination;
    private final boolean pippCompat;

    public static void setPippCompatibility(boolean pippCompatibility) {
        DEFAULT_PIPP_COMPATIBILITY.set(pippCompatibility);
    }

    private FitsUtils(ProcessParams params, File destination, boolean pippCompat) {
        this.params = params;
        this.destination = destination;
        this.pippCompat = pippCompat;
    }

    public static void writeFitsFile(ImageWrapper image, File destination, ProcessParams params) {
        new FitsUtils(params, destination, DEFAULT_PIPP_COMPATIBILITY.get()).write(image);
    }

    public static void writeFitsFile(ImageWrapper image, File destination, ProcessParams params, boolean compatibleWithPIPP) {
        new FitsUtils(params, destination, compatibleWithPIPP).write(image);
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
                        // JSol'Ex up to 3.3.x used to flip the Y axis
                        boolean hasSpectroHeader = imageHdu.getHeader().containsKey(SPECTRO);
                        boolean shouldFlipY = isJSolEx && !hasSpectroHeader;
                        data = readChannel(mono, rows, cols, bzero, shouldFlipY);
                    } else if (kernel instanceof short[][][] channels) {
                        rgb = new float[3][][];
                        for (int i = 0; i < channels.length; i++) {
                            short[][] channel = channels[i];
                            rows = channel.length;
                            cols = rows == 0 ? 0 : channel[0].length;
                            boolean hasSpectroHeader = imageHdu.getHeader().containsKey(SPECTRO);
                            boolean shouldFlipY = isJSolEx && !hasSpectroHeader;
                            rgb[i] = readChannel(channel, rows, cols, bzero, shouldFlipY);
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
        if (header.containsKey(CENTER_X) && header.containsKey(CENTER_Y) && header.containsKey(SOLAR_R)) {
            // cool, we have an ellipse!
            double centerX = header.getIntValue(CENTER_X);
            double centerY = header.getIntValue(CENTER_Y);
            double solarR = header.getIntValue(SOLAR_R);
            // need to convert to Ellipse parameters
            var ellipse = Ellipse.ofCartesian(new DoubleSextuplet(
                    1,
                    0,
                    1,
                    -2d * centerX,
                    -2d * centerY,
                    centerX * centerX + centerY * centerY - solarR * solarR
            ));
            metadata.putIfAbsent(Ellipse.class, ellipse);
        }
    }

    private static float[][] readChannel(short[][] mono, int rows, int cols, int bzero, boolean flipY) {
        float[][] data = new float[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int sourceY = flipY ? (rows - y - 1) : y;
                data[y][x] = mono[sourceY][x] + bzero;
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

    private static void readMetadata(BinaryTableHDU binaryTableHdu, Map<Class<?>, Object> metadata) throws FitsException, IOException {
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
                } else if (REFCOORDS2_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    int cpt = binaryTable.getNRows();
                    var values = new ArrayList<ReferenceCoords.Operation>();

                    // Check version number from first row (uses special index -1)
                    int startIndex = 0;
                    if (cpt > 0) {
                        int firstIndex = binaryTable.getNumber(0, 0).intValue();
                        if (firstIndex == -1) {
                            int version = (int) binaryTable.getDouble(0, 1);

                            // Handle different versions
                            if (version == REF_COORDS_VERSION) {
                                startIndex = 1; // Skip version row
                            } else {
                                throw new RuntimeException("Unsupported RefCoords version: " + version + ", expected: " + REF_COORDS_VERSION);
                            }
                        }
                    }

                    // Parse operations starting from startIndex
                    for (int i = startIndex; i < cpt; i++) {
                        int kindIndex = binaryTable.getNumber(i, 0).intValue();

                        // Convert ordinal index back to OperationKind
                        var operationKinds = ReferenceCoords.OperationKind.values();
                        if (kindIndex < 0 || kindIndex >= operationKinds.length) {
                            throw new RuntimeException("Invalid operation kind index: " + kindIndex);
                        }
                        var kind = operationKinds[kindIndex];

                        // Read values from Double columns (max 3 values)
                        var valuesList = new ArrayList<Double>();
                        for (int col = 1; col <= 3; col++) {
                            double val = binaryTable.getDouble(i, col);
                            if (!Double.isNaN(val)) {
                                valuesList.add(val);
                            }
                        }

                        double[] operationValues = valuesList.stream().mapToDouble(Double::doubleValue).toArray();
                        values.add(new ReferenceCoords.Operation(kind, operationValues));
                    }
                    metadata.put(ReferenceCoords.class, new ReferenceCoords(Collections.unmodifiableList(values)));
                } else if (SOURCEINFO_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var fileName = (String) binaryTable.get(0, 0);
                    var parentName = (String) binaryTable.get(0, 1);
                    var date = ZonedDateTime.parse((String) binaryTable.get(0, 2));
                    int width = 0;
                    int height = 0;
                    if (binaryTable.getNCols() > 3) {
                        width = binaryTable.getNumber(0, 3).intValue();
                        height = binaryTable.getNumber(0, 4).intValue();
                    }
                    metadata.put(SourceInfo.class, new SourceInfo(fileName, parentName, date, width, height));
                } else if (REDSHIFTS_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    int cpt = binaryTable.getNRows();
                    var values = new ArrayList<RedshiftArea>();
                    for (int i = 0; i < cpt; i++) {
                        var redshift = new RedshiftArea(
                                binaryTable.getNCols() == 10 ? binaryTable.getString(i, 9) : null,
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
                } else if (ACTIVE_REGION_VALUE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var activeRegions = new ArrayList<ActiveRegion>();
                    for (int i = 0; i < binaryTable.getNRows(); i++) {
                        var row = binaryTable.getRow(i);
                        var data = (byte[]) row[0];
                        var dais = new DataInputStream(new ByteArrayInputStream(data));
                        var activeRegionCount = dais.readInt();
                        for (int j = 0; j < activeRegionCount; j++) {
                            var pointCount = dais.readInt();
                            var points = new ArrayList<Point2D>();
                            for (int k = 0; k < pointCount; k++) {
                                var x = dais.readDouble();
                                var y = dais.readDouble();
                                points.add(new Point2D(x, y));
                            }
                            activeRegions.add(ActiveRegion.of(points));
                        }
                    }
                    metadata.put(ActiveRegions.class, new ActiveRegions(activeRegions));
                } else if (FLARE.equals(card.getValue())) {
                    var binaryTable = binaryTableHdu.getData();
                    var flareList = new ArrayList<Flare>();
                    for (int i = 0; i < binaryTable.getNRows(); i++) {
                        var row = binaryTable.getRow(i);
                        var data = (byte[]) row[0];
                        var dais = new DataInputStream(new ByteArrayInputStream(data));
                        var bombCount = dais.readInt();
                        for (int j = 0; j < bombCount; j++) {
                            int kind = dais.readInt();
                            var frameId = dais.readInt();
                            var sourceX = dais.readInt();
                            var x = dais.readDouble();
                            var y = dais.readDouble();
                            var score = dais.readDouble();
                            flareList.add(new Flare(Flare.Kind.values()[kind], frameId, sourceX, x, y, score));
                        }
                    }
                    metadata.put(Flares.class, new Flares(flareList));
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

    private void writeMetadata(ImageWrapper image, Fits fits) throws FitsException, IOException {
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
            writeActiveRegions(image, fits);
            writeFlares(image, fits);
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
            writeBinaryTable(table, DISTORSION_MAP_VALUE, "Distorsion map", fits);
        });
    }

    private static void writeActiveRegions(ImageWrapper image, Fits fits) throws IOException {
        var metadata = image.findMetadata(ActiveRegions.class);
        if (metadata.isPresent()) {
            var activeRegions = metadata.get();
            var table = new BinaryTable();
            var baos = new ByteArrayOutputStream();
            var daos = new DataOutputStream(baos);
            var activeRegionList = activeRegions.regionList();
            daos.writeInt(activeRegionList.size());
            for (var activeRegion : activeRegionList) {
                var points = activeRegion.points();
                daos.writeInt(points.size());
                // we only need to store the points, not the bounding box
                for (var p : points) {
                    daos.writeDouble(p.x());
                    daos.writeDouble(p.y());
                }
            }
            table.addRow(new Object[]{baos.toByteArray()});
            writeBinaryTable(table, ACTIVE_REGION_VALUE, "Active regions", fits);
        }
    }

    private static void writeFlares(ImageWrapper image, Fits fits) throws IOException {
        var metadata = image.findMetadata(Flares.class);
        if (metadata.isPresent()) {
            var flares = metadata.get();
            var table = new BinaryTable();
            var baos = new ByteArrayOutputStream();
            var daos = new DataOutputStream(baos);
            var flaresList = flares.flares();
            daos.writeInt(flaresList.size());
            for (var flare : flaresList) {
                daos.writeInt(flare.kind().ordinal());
                daos.writeInt(flare.frameId());
                daos.writeInt(flare.sourceX());
                daos.writeDouble(flare.x());
                daos.writeDouble(flare.y());
                daos.writeDouble(flare.score());
            }
            table.addRow(new Object[]{baos.toByteArray()});
            writeBinaryTable(table, FLARE, "Flares", fits);
        }
    }

    private static void writeMetadataTable(ImageWrapper image, Fits fits) {
        image.findMetadata(MetadataTable.class).ifPresent(metadataTable -> {
            var table = new BinaryTable();
            metadataTable.properties().forEach((key, value) -> table.addRow(new Object[]{key, value}));
            writeBinaryTable(table, METADATA_TABLE_VALUE, "Metadata table", fits);
        });
    }

    private static void writeBinaryTable(BinaryTable table, String metadataTableValue, String Metadata_table, Fits fits) {
        var binaryTableHDU = BinaryTableHDU.wrap(table);
        binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, metadataTableValue, Metadata_table);
        try {
            fits.addHDU(binaryTableHDU);
        } catch (FitsException e) {
            throw new RuntimeException(e);
        }
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
            writeBinaryTable(table, REDSHIFTS_VALUE, "Measured redshifts", fits);
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

            // Add version number as first row to enable future format changes
            // Use special negative index (-1) to indicate version row
            table.addRow(new Object[]{-1, (double) REF_COORDS_VERSION, Double.NaN, Double.NaN});

            referenceCoords.operations().forEach(op -> {
                // Store operation kind as ordinal index to avoid FITS string truncation
                int kindIndex = op.kind().ordinal();

                // Store values as separate Double columns (max 3 values supported currently)
                // Fill unused columns with NaN to indicate no value
                double val1 = op.values().length > 0 ? op.values()[0] : Double.NaN;
                double val2 = op.values().length > 1 ? op.values()[1] : Double.NaN;
                double val3 = op.values().length > 2 ? op.values()[2] : Double.NaN;

                table.addRow(new Object[]{kindIndex, val1, val2, val3});
            });

            var binaryTableHDU = BinaryTableHDU.wrap(table);
            binaryTableHDU.getHeader().addValue(JSOLEX_HEADER_KEY, REFCOORDS2_VALUE, "Reference coordinate transforms v2");
            fits.addHDU(binaryTableHDU);
        }
    }

    private static void writeSourceInfo(ImageWrapper image, Fits fits) throws FitsException {
        var metadata = image.findMetadata(SourceInfo.class);
        if (metadata.isPresent()) {
            var sourceInfo = metadata.get();
            var table = new BinaryTable();
            table.addRow(new Object[]{sourceInfo.serFileName(), sourceInfo.parentDirName(), sourceInfo.dateTime().toString(), sourceInfo.width(), sourceInfo.height()});
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
        header.addValue(ROWORDER, ROWORDER_BOTTOM_UP);
        header.addValue(Standard.BITPIX, 16);
        header.addValue(Standard.NAXIS, naxis);
        header.addValue(Standard.NAXIS1, image.width());
        header.addValue(Standard.NAXIS2, image.height());
        if (image instanceof RGBImage) {
            header.addValue("NAXIS3", 3, null);
        }
        header.addValue(Standard.BSCALE, 1);
        header.addValue(Standard.BZERO, 32768);
        if (pippCompat) {
            return;
        }
        header.addValue(Standard.OBJECT, "Sun");
        header.addValue(OBJNAME, "Sun");
        var obs = params.observationDetails();
        maybeAdd(header, Standard.OBSERVER, obs.observer());
        maybeAdd(header, Standard.INSTRUME, obs.instrument().label());
        maybeAdd(header, CAMERA, obs.camera());
        header.addValue(Standard.DATE_OBS, obs.date().format(
                DateTimeFormatter.ISO_DATE_TIME
        ));
        var email = obs.email();
        if (notEmpty(email)) {
            header.addValue(CONTACT, normalize(email), "Contact email");
        }
        var coordinates = obs.coordinates();
        if (coordinates != null) {
            header.addValue(SBFitsExt.SITELAT, String.valueOf(coordinates.a()));
            header.addValue(SBFitsExt.SITELONG, String.valueOf(coordinates.b()));
        }
        header.addValue(DataDescription.CREATOR, "JSol'Ex " + VersionUtil.getVersion());

        header.addValue(PHYSPARA, "Intensity", "Physical parameter");
        header.addValue(WAVEUNIT, -10, "Wavelength unit");

        header.addValue(FILENAME, destination.getName(), "Original filename");

        // Add solar parameters if available
        image.findMetadata(SolarParameters.class).ifPresent(solarParams -> {
            header.addValue(SEP_LAT, Math.toDegrees(solarParams.b0()), "Latitude of disk centre (B0 angle)");
            header.addValue(SEP_LON, Math.toDegrees(solarParams.l0()), "Carrington longitude of central meridian");
            header.addValue(CAR_ROT, solarParams.carringtonRotation(), "Carrington rotation number");
            header.addValue(SOLAR_P, Math.toDegrees(solarParams.p()), "P angle (degrees)");
        });

        var fl = obs.focalLength();
        if (fl != null) {
            header.addValue(SBFitsExt.FOCALLEN, fl.floatValue());
        }
        int aperture = obs.aperture() != null ? obs.aperture() : 0;
        header.addValue(InstrumentDescription.APERTURE, String.valueOf(aperture));

        int stop = obs.stop() == null ? aperture : obs.stop();
        header.addValue(DIAPH, stop, "Diaphragm diameter (mm)");
        var erf = obs.energyRejectionFilter() == null ? "" : obs.energyRejectionFilter();
        header.addValue(ERF, erf, "Energy Rejection Filter");

        var instrument = obs.instrument();
        header.addValue(SPECTRO, instrument.bass2000Id(), "BASS2000 spectroheliograph ID");
        header.addValue(FEQUIV, fl != null ? fl.floatValue() : 0.0f, "Equivalent focal length of telescope (mm)");
        header.addValue(CAMERA, obs.camera() != null ? obs.camera() : "Unknown");
        header.addValue(CAMPIX, obs.pixelSize() != null ? obs.pixelSize().floatValue() / 1000.0f : 0.0f, "Pixel size of sensor (mm)");
        header.addValue(FCOL, (float) instrument.collimatorFocalLength(), "Focal length of collimator (mm)");
        header.addValue(FCAM, (float) instrument.focalLength(), "Focal length of camera lens (mm)");
        header.addValue(GROOVES, instrument.density(), "Number of grooves/mm of grating");
        header.addValue(ORDER, instrument.order(), "Interference order on grating");
        header.addValue(SHGANGLE, (float) instrument.totalAngleDegrees(), "Angle between incident and diffracted rays (degrees)");
        header.addValue(SLWIDTH, (float) (instrument.slitWidthMicrons() / 1000.0), "Slit width (mm)");
        header.addValue(SLHEIGHT, (float) instrument.slitHeightMillimeters(), "Slit height (mm)");
        header.addValue(BIN1, obs.binning() != null ? obs.binning() : 1, "Binning factor in X direction");
        header.addValue(BIN2, obs.binning() != null ? obs.binning() : 1, "Binning factor in Y direction");
        var declaredWavelength = params.spectrumParams().ray().wavelength();
        var wavelength = declaredWavelength.nanos();
        var pixelSize = obs.pixelSize();
        var binning = obs.binning();
        if (wavelength != 0 && pixelSize != null && pixelSize > 0 && binning != null && binning > 0) {
            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, Wavelen.ofNanos(wavelength), pixelSize * binning);
            header.addValue(WAVEBAND, (float) dispersion.angstromsPerPixel(), "Spectral dispersion (Angstrom/pixel)");
        } else {
            header.addValue(WAVEBAND, 0.0f, "Spectral dispersion (Angstrom/pixel) - unknown");
        }
        Optional<Bass2000Compatibility> bass2000Compatibility = image.findMetadata(Bass2000Compatibility.class);
        if (bass2000Compatibility.isPresent()) {
            header.addValue("WAVELNTH", bass2000Compatibility.get().forcedWavelengthAngstroms(), "Wavelength (Angstroms)");
        } else {
            wavelength = declaredWavelength.nanos();
            // alter the wavelength according to the pixel shift
            if (wavelength != 0) {
                var pixelShift = image.findMetadata(PixelShift.class).map(PixelShift::pixelShift);
                if (pixelShift.isPresent() && pixelSize != null && pixelSize > 0 && binning != null && binning > 0) {
                    var dispersion = SpectrumAnalyzer.computeSpectralDispersion(obs.instrument(), Wavelen.ofNanos(wavelength), pixelSize * binning);
                    wavelength += pixelShift.get() * dispersion.nanosPerPixel();
                }
                // convert to Angstroms
                wavelength = 10 * wavelength;
                // then round to 3 digits
                wavelength = Math.round(wavelength * 1000.0) / 1000.0;
                header.addValue("WAVELNTH", wavelength, "Wavelength (Angstroms)");
            }
        }

        writeIntiCompatibleFields(image, header);
    }

    private void writeIntiCompatibleFields(ImageWrapper image, Header header) {
        image.findMetadata(Ellipse.class).ifPresent(ellipse -> {
            var center = ellipse.center();
            int cx = (int) Math.round(center.a());
            int cy = (int) Math.round(center.b());
            int radius = (int) Math.round((ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2);
            var boundingBox = ellipse.boundingBox();
            int x1 = (int) Math.max(0, Math.round(boundingBox.a()));
            int x2 = (int) Math.max(0, Math.round(boundingBox.b()));
            int y1 = (int) Math.min(image.width() - 1, Math.round(boundingBox.c()));
            int y2 = (int) Math.min(image.width() - 1, Math.round(boundingBox.d()));
            header.addValue(CENTER_X, cx, "Center X");
            header.addValue(INTI_CENTER_X, cx, "Center X");
            header.addValue(CENTER_Y, cy, "Center Y");
            header.addValue(INTI_CENTER_Y, cy, "Center Y");
            header.addValue(SOLAR_R, radius, "Solar radius");
            header.addValue(INTI_SOLAR_R, radius, "Solar radius");
            header.addValue(INTI_X1, x1, "Bounding box X1");
            header.addValue(INTI_Y1, y1, "Bounding box Y1");
            header.addValue(INTI_X2, x2, "Bounding box X2");
            header.addValue(INTI_Y2, y2, "Bounding box Y2");
        });
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
                // BASS2000 requires bottom-up order
                int value = Math.round(data[rowCount - y - 1][x]);
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
