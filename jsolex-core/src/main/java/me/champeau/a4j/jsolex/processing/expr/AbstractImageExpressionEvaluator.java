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
package me.champeau.a4j.jsolex.processing.expr;

import com.google.gson.*;
import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.expr.ExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.impl.*;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.ProcessParamsIO;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.stretching.CutoffStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.*;
import me.champeau.a4j.jsolex.processing.util.*;
import me.champeau.a4j.math.image.ImageMath;
import me.champeau.a4j.math.regression.Ellipse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public abstract class AbstractImageExpressionEvaluator extends ExpressionEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractImageExpressionEvaluator.class);

    private final Map<Class<?>, Object> context = new HashMap<>();
    private final ImageMath imageMath = ImageMath.newInstance();
    private final Broadcaster broadcaster;

    // Function implementations
    private final AdjustContrast adjustContrast;
    private final Animate animate;
    private final BackgroundRemoval bgRemoval;
    private final Clahe clahe;
    private final Colorize colorize;
    private final Convolution convolution;
    private final Crop crop;
    private final Dedistort dedistort;
    private final DiskFill diskFill;
    private final EllipseFit ellipseFit;
    private final Filtering filtering;
    private final FixBanding fixBanding;
    private final ArtifificialFlatCorrector flatCorrector;
    private final GeometryCorrection geometryCorrection;
    private final ImageDraw imageDraw;
    private final Inverse inverse;
    private final Loader loader;
    private final MathFunctions math;
    private final MosaicComposition mosaicComposition;
    private final RemoteScriptGen remoteScriptGen;
    private final Rotate rotate;
    private final Saturation saturation;
    private final Scaling scaling;
    private final SimpleFunctionCall simpleFunctionCall;
    private final Stretching stretching;
    private final Stacking stacking;
    private final Utilities utilities;

    protected AbstractImageExpressionEvaluator(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.adjustContrast = new AdjustContrast(context, broadcaster);
        this.animate = new Animate(context, broadcaster);
        this.bgRemoval = new BackgroundRemoval(context, broadcaster);
        this.clahe = new Clahe(context, broadcaster);
        this.colorize = new Colorize(context, broadcaster);
        this.convolution = new Convolution(context, broadcaster);
        this.crop = new Crop(context, broadcaster);
        this.dedistort = new Dedistort(context, broadcaster);
        this.diskFill = new DiskFill(context, broadcaster);
        this.ellipseFit = new EllipseFit(context, broadcaster);
        this.filtering = new Filtering(context, broadcaster);
        this.fixBanding = new FixBanding(context, broadcaster);
        this.flatCorrector = new ArtifificialFlatCorrector(context, broadcaster);
        this.geometryCorrection = new GeometryCorrection(context, broadcaster, ellipseFit);
        this.imageDraw = new ImageDraw(context, broadcaster);
        this.inverse = new Inverse(context, broadcaster);
        this.loader = new Loader(context, broadcaster);
        this.math = new MathFunctions(context, broadcaster);
        this.remoteScriptGen = new RemoteScriptGen(this, context, broadcaster);
        this.rotate = new Rotate(context, broadcaster);
        this.saturation = new Saturation(context, broadcaster);
        this.scaling = new Scaling(context, broadcaster, crop);
        this.simpleFunctionCall = new SimpleFunctionCall(context, broadcaster);
        this.stretching = new Stretching(context, broadcaster);
        this.stacking = new Stacking(context, scaling, crop, simpleFunctionCall, imageDraw, broadcaster);
        this.mosaicComposition = new MosaicComposition(context, broadcaster, stacking, ellipseFit, scaling);
        this.utilities = new Utilities(context, broadcaster);
    }

    public <T> void putInContext(Class<T> key, T value) {
        context.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getFromContext(Class<T> type) {
        return (Optional<T>) Optional.ofNullable(context.get(type));
    }

    @Override
    public Object plus(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            return Stream.concat(leftList.stream(), rightList.stream()).toList();
        }
        if (left instanceof CharSequence leftString && right instanceof CharSequence rightString) {
            return leftString.toString() + rightString;
        }
        if (left instanceof CharSequence leftString) {
            return leftString.toString() + format(right);
        }
        if (right instanceof CharSequence rightString) {
            return String.valueOf(left) + format(rightString);
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, Double::sum);
    }

    private static String format(Object o) {
        if (o instanceof Double d) {
            return String.format(Locale.US, "%.2f", d);
        }
        if (o instanceof Float f) {
            return String.format(Locale.US, "%.2f", f);
        }
        return o.toString();
    }

    @Override
    public Object minus(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            var copy = new ArrayList<>(leftList);
            copy.removeAll(rightList);
            return Collections.unmodifiableList(copy);
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a - b);
    }

    @Override
    public Object mul(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if (leftList.size() == rightList.size()) {
                return IntStream.range(0, leftList.size())
                        .mapToObj(i -> mul(leftList.get(i), rightList.get(i)))
                        .toList();
            }
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a * b);
    }

    @Override
    public Object div(Object left, Object right) {
        if (left instanceof List<?> leftList && right instanceof List<?> rightList) {
            if (leftList.size() == rightList.size()) {
                return IntStream.range(0, leftList.size())
                        .mapToObj(i -> div(leftList.get(i), rightList.get(i)))
                        .toList();
            }
        }
        var leftImage = asImage(left);
        var rightImage = asImage(right);
        var leftScalar = asScalar(left);
        var rightScalar = asScalar(right);
        return apply(leftImage, rightImage, leftScalar, rightScalar, (a, b) -> a / b);
    }

    @Override
    public Object functionCall(BuiltinFunction function, List<Object> arguments) {
        return switch (function) {
            case A2PX -> angstromsToPixels(arguments);
            case ADJUST_CONTRAST -> adjustContrast.adjustContrast(arguments);
            case ADJUST_GAMMA -> adjustContrast.adjustGamma(arguments);
            case ANIM -> animate.createAnimation(arguments);
            case ASINH_STRETCH -> stretching.asinhStretch(arguments);
            case AUTO_CONTRAST -> adjustContrast.autoContrast(arguments);
            case AUTOCROP -> crop.autocrop(arguments);
            case AUTOCROP2 -> crop.autocrop2(arguments);
            case AVG -> simpleFunctionCall.applyFunction("avg", arguments, DoubleStream::average);
            case BG_MODEL -> bgRemoval.backgroundModel(arguments);
            case GET_B -> utilities.extractChannel(arguments, 2);
            case BLUR -> convolution.blur(arguments);
            case CLAHE -> clahe.clahe(arguments);
            case CHOOSE_FILE -> loader.chooseFile(arguments);
            case CHOOSE_FILES -> loader.chooseFiles(arguments);
            case COLORIZE -> colorize.colorize(arguments);
            case CONCAT -> utilities.concat(arguments);
            case CONTINUUM -> createContinuumImage();
            case CROP -> crop.crop(arguments);
            case CROP_RECT -> crop.cropToRect(arguments);
            case CROP_AR -> crop.cropActiveRegions(arguments);
            case DEDISTORT -> dedistort.dedistort(arguments);
            case DISK_FILL -> diskFill.fill(arguments);
            case DISK_MASK -> diskFill.mask(arguments);
            case DRAW_ARROW -> imageDraw.drawArrow(arguments);
            case DRAW_CIRCLE -> imageDraw.drawCircle(arguments);
            case DRAW_RECT -> imageDraw.drawRectangle(arguments);
            case DRAW_GLOBE -> imageDraw.drawGlobe(arguments);
            case DRAW_OBS_DETAILS -> imageDraw.drawObservationDetails(arguments);
            case DRAW_SOLAR_PARAMS -> imageDraw.drawSolarParameters(arguments);
            case DRAW_TEXT -> imageDraw.drawText(arguments);
            case DRAW_EARTH -> imageDraw.drawEarth(arguments);
            case ELLIPSE_FIT -> ellipseFit.fit(arguments);
            case EXP -> math.exp(arguments);
            case FILTER -> filtering.filter(arguments);
            case FIX_BANDING -> fixBanding.fixBanding(arguments);
            case FIX_GEOMETRY -> geometryCorrection.fixGeometry(arguments);
            case FLAT_CORRECTION -> flatCorrector.performFlatCorrection(arguments);
            case HFLIP -> rotate.hflip(arguments);
            case GET_AT -> utilities.doGetAt(arguments);
            case GET_G -> utilities.extractChannel(arguments, 1);
            case IMG -> image(arguments);
            case INVERT -> inverse.invert(arguments);
            case LINEAR_STRETCH -> stretching.linearStretch(arguments);
            case LIST -> arguments;
            case LOAD -> loader.load(arguments);
            case LOAD_MANY -> loader.loadMany(arguments);
            case LOG -> math.log(arguments);
            case MAX -> simpleFunctionCall.applyFunction("max", arguments, DoubleStream::max);
            case MEDIAN ->
                    simpleFunctionCall.applyFunction("median", arguments, AbstractImageExpressionEvaluator::median);
            case MIN -> simpleFunctionCall.applyFunction("min", arguments, DoubleStream::min);
            case MONO -> utilities.toMono(arguments);
            case MOSAIC -> mosaicComposition.mosaic(arguments);
            case NEUTRALIZE_BG -> bgRemoval.neutralizeBackground(arguments);
            case POW -> math.pow(arguments);
            case PX2A -> pixelsToAngstroms(arguments);
            case GET_R -> utilities.extractChannel(arguments, 0);
            case RADIUS_RESCALE -> scaling.radiusRescale(arguments);
            case RANGE -> createRange(arguments);
            case REMOVE_BG -> bgRemoval.removeBackground(arguments);
            case RESCALE_ABS -> scaling.absoluteRescale(arguments);
            case RESCALE_REL -> scaling.relativeRescale(arguments);
            case RL_DECON -> convolution.richardsonLucy(arguments);
            case ROTATE_LEFT -> rotate.rotateLeft(arguments);
            case ROTATE_RIGHT -> rotate.rotateRight(arguments);
            case ROTATE_DEG -> rotate.rotateDegrees(arguments);
            case ROTATE_RAD -> rotate.rotateRadians(arguments);
            case REMOTE_SCRIPTGEN -> remoteScriptGen.callRemoteScriptGen(arguments);
            case RGB -> RGBCombination.combine(arguments);
            case SATURATE -> saturation.saturate(arguments);
            case SHARPEN -> convolution.sharpen(arguments);
            case FIND_SHIFT -> pixelShiftFor(arguments);
            case STACK -> stacking.stack(arguments);
            case STACK_REF -> stacking.chooseReference(arguments);
            case SORT -> utilities.sort(arguments);
            case AR_OVERLAY -> imageDraw.activeRegionsOverlay(arguments);
            case VFLIP -> rotate.vflip(arguments);
            case VIDEO_DATETIME -> utilities.videoDateTime(arguments);
            case WAVELEN -> wavelenthOfImage(arguments);
            case WEIGHTED_AVG -> utilities.weightedAverage(arguments);
            case WORKDIR -> setWorkDir(arguments);
        };
    }

    public ImageWrapper32 createContinuumImage() {
        record ImageWithAverage(ImageWrapper image, double average) {
        }
        var fullRange = (PixelShiftRange) context.get(PixelShiftRange.class);
        List<ImageWrapper> samples;
        if (fullRange != null) {
            samples = createRange(List.of(fullRange.minPixelShift(), fullRange.maxPixelShift(), fullRange.step()));
        } else {
            samples = createRange(List.of(-15, 15, 3));
        }
        // remove the samples which are too close to the studied line
        int samplesSize = samples.size();
        if (samplesSize > 0) {
            var list = samples.stream()
                    .filter(image -> {
                        var metadata = image.findMetadata(PixelShift.class);
                        if (metadata.isPresent()) {
                            var pixelShift = metadata.get().pixelShift();
                            if (Math.abs(pixelShift) < 8) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .map(img -> {
                        var data = ((ImageWrapper32) img.unwrapToMemory()).data();
                        var avg = imageMath.averageOf(data);
                        return new ImageWithAverage(img, avg);
                    })
                    .toList();
            samples = list.stream()
                    .sorted(Comparator.comparingDouble(ImageWithAverage::average).reversed())
                    .map(ImageWithAverage::image)
                    .limit((long) Math.ceil(list.size() / 5d))
                    .toList();
        }
        return (ImageWrapper32) functionCall(BuiltinFunction.MEDIAN, List.of(samples));
    }

    public Object pixelShiftFor(List<Object> arguments) {
        if (arguments.size() != 1 && arguments.size() != 2) {
            throw new IllegalArgumentException("find_shift() accepts one or 2 arguments (wavelength in angstroms or spectral ray to find, wavelength or spectral ray of reference)");
        }
        var first = arguments.getFirst();
        if (first instanceof List<?>) {
            return utilities.expandToImageList("find_shift", arguments, this::pixelShiftFor);
        }
        var target = toWavelength(first);
        var reference = arguments.size() == 2 ? toWavelength(arguments.get(1)) : null;
        ProcessParams params = (ProcessParams) context.get(ProcessParams.class);
        return computePixelShift(params, target, reference);
    }

    private static Wavelen toWavelength(Object firstArg) {
        double targetWaveLength = 0;
        if (firstArg instanceof String rayName) {
            var first = SpectralRayIO.loadDefaults()
                    .stream()
                    .filter(ray -> ray.label().equalsIgnoreCase(rayName))
                    .findFirst();
            if (first.isPresent()) {
                targetWaveLength = first.get().wavelength().angstroms();
            }
        } else {
            targetWaveLength = asScalar(firstArg).doubleValue();
        }
        return Wavelen.ofAngstroms(targetWaveLength);
    }

    private Object wavelenthOfImage(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("wavelength() accepts a single argument (image)");
        }
        Object first = arguments.getFirst();
        if (first instanceof List<?>) {
            return utilities.expandToImageList("wavelen", arguments, this::wavelenthOfImage);
        }
        var image = asImage(first);
        var metadata = image.metadata();
        var pixelShift = (PixelShift) metadata.get(PixelShift.class);
        if (pixelShift == null) {
            throw new IllegalArgumentException("Image must have a pixel shift metadata");
        }
        var params = (ProcessParams) context.get(ProcessParams.class);
        var lambda0 = params.spectrumParams().ray().wavelength();
        var dispersion = computeDispersion(params, lambda0);
        return round2digits(lambda0.plus(pixelShift.pixelShift(), dispersion).angstroms());
    }

    public Object angstromsToPixels(List<Object> arguments) {
        if (arguments.size() != 1 && arguments.size() != 2) {
            throw new IllegalArgumentException("a2px() accepts 1 or 2 argument (angstroms, [reference wavelength angstroms])");
        }
        double angstroms = asScalar(arguments.getFirst()).doubleValue();
        var params = (ProcessParams) context.get(ProcessParams.class);
        var lambda0 = params.spectrumParams().ray().wavelength();
        if (arguments.size() == 2) {
            lambda0 = toWavelength(arguments.get(1));
        }
        var dispersion = computeDispersion(params, lambda0);
        return round2digits(angstroms / dispersion.angstromsPerPixel());
    }

    public Object pixelsToAngstroms(List<Object> arguments) {
        if (arguments.size() != 1 && arguments.size() != 2) {
            throw new IllegalArgumentException("px2a() accepts 1 or 2 arguments (pixels, [reference wavelength angstroms])");
        }
        double pixels = asScalar(arguments.getFirst()).doubleValue();
        var params = (ProcessParams) context.get(ProcessParams.class);
        var lambda0 = params.spectrumParams().ray().wavelength();
        if (arguments.size() == 2) {
            lambda0 = toWavelength(arguments.get(1));
        }
        var dispersion = computeDispersion(params, lambda0);
        double v = pixels * dispersion.angstromsPerPixel();
        return round2digits(v);
    }

    private static double round2digits(double v) {
        return Math.round(100d * v) / 100d;
    }

    static Dispersion computeDispersion(ProcessParams params, Wavelen lambda0) {
        var instrument = params.observationDetails().instrument();
        var pixelSize = params.observationDetails().pixelSize();
        var binning = params.observationDetails().binning();
        if (pixelSize == null) {
            pixelSize = 2.4;
        }
        if (binning == null) {
            binning = 1;
        }
        return SpectrumAnalyzer.computeSpectralDispersion(
                instrument,
                lambda0,
                pixelSize * binning
        );
    }

    protected double computePixelShift(ProcessParams params, Wavelen targetWaveLength, Wavelen referenceWavelength) {
        if (params == null) {
            return 0;
        }
        var pixelSize = params.observationDetails().pixelSize();
        var binning = params.observationDetails().binning();
        if (pixelSize == null) {
            pixelSize = 2.4;
        }
        if (binning == null) {
            binning = 1;
        }
        var lambda0 = referenceWavelength == null ? params.spectrumParams().ray().wavelength() : referenceWavelength;
        var instrument = params.observationDetails().instrument();
        return SpectrumAnalyzer.computePixelShift(pixelSize, binning, lambda0, targetWaveLength, instrument);
    }

    public static OptionalDouble median(DoubleStream doubleStream) {
        var array = doubleStream.toArray();
        if (array.length == 0) {
            return OptionalDouble.empty();
        }
        Arrays.sort(array);
        double median;
        int length = array.length;
        if (length % 2 == 0) {
            median = (array[length / 2 - 1] + array[length / 2]) / 2.0;
        } else {
            median = array[length / 2];
        }
        return OptionalDouble.of(median);
    }

    private Object setWorkDir(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalStateException("workdir accepts a single argument: path to directory");
        }
        var path = arguments.get(0).toString();
        loader.setWorkingDirectory(Paths.get(path));
        return path;
    }

    public abstract ImageWrapper findImage(double shift);

    private ImageWrapper image(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("img() call must have a single argument (image shift)");
        }
        var arg = arguments.get(0);
        if (arg instanceof Number shift) {
            return findImage(shift.doubleValue());
        }
        throw new IllegalArgumentException("img() argument must be a number representing an image shift");

    }

    private static Object apply(ImageWrapper32 leftImage,
                                ImageWrapper32 rightImage,
                                Number leftScalar,
                                Number rightScalar,
                                DoubleBinaryOperator operator) {
        if (leftImage != null && rightImage != null) {
            var leftData = leftImage.data();
            var rightData = rightImage.data();
            var length = leftData.length;
            var width = leftImage.width();
            var height = leftImage.height();
            if (width != rightImage.width() || height != rightImage.height()) {
                throw new IllegalArgumentException("Both images must have the same dimensions");
            }
            float[][] result = new float[height][width];
            float min = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = (float) operator.applyAsDouble(leftData[y][x], rightData[y][x]);
                    if (v < min) {
                        min = v;
                    }
                    result[y][x] = v;
                }
            }
            normalize(result, min);
            Map<Class<?>, Object> metadata = new LinkedHashMap<>();
            metadata.putAll(leftImage.metadata());
            metadata.putAll(rightImage.metadata());
            return new ImageWrapper32(width, height, result, metadata);
        }
        if (leftImage != null && rightScalar != null) {
            var scalar = rightScalar.doubleValue();
            var leftData = leftImage.data();
            var length = leftData.length;
            var width = leftImage.width();
            var height = leftImage.height();
            float[][] result = new float[height][width];
            float min = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = (float) operator.applyAsDouble(leftData[y][x], scalar);
                    if (v < min) {
                        min = v;
                    }
                    result[y][x] = v;
                }
            }
            normalize(result, min);
            return new ImageWrapper32(width, height, result, leftImage.metadata());
        }
        if (rightImage != null && leftScalar != null) {
            var scalar = leftScalar.doubleValue();
            var rightData = rightImage.data();
            var width = rightImage.width();
            var height = rightImage.height();
            float[][] result = new float[height][width];
            float min = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var v = (float) operator.applyAsDouble(rightData[y][x], scalar);
                    if (v < min) {
                        min = v;
                    }
                    result[y][x] = v;
                }
            }
            normalize(result, min);
            return new ImageWrapper32(width, height, result, rightImage.metadata());
        }
        if (leftScalar != null && rightScalar != null) {
            return operator.applyAsDouble(leftScalar.doubleValue(), rightScalar.doubleValue());
        }
        throw new IllegalArgumentException("Unexpected operand types");
    }

    private static void normalize(float[][] result, float min) {
        if (min < 0) {
            // shift all values so that they are positive
            var abs = Math.abs(min);
            for (float[] line : result) {
                for (int i = 0; i < line.length; i++) {
                    line[i] += abs;
                }
            }
        }
    }

    private static ImageWrapper32 asImage(Object source) {
        if (source instanceof ImageWrapper32 image) {
            return image;
        }
        if (source instanceof FileBackedImage fileBackedImage) {
            return asImage(fileBackedImage.unwrapToMemory());
        }
        return null;
    }

    private static Number asScalar(Object source) {
        if (source instanceof Number number) {
            return number;
        }
        return null;
    }

    private List<ImageWrapper> createRange(List<Object> arguments) {
        if (arguments.size() < 2) {
            return List.of();
        }
        Number from = asScalar(arguments.get(0));
        Number to = asScalar(arguments.get(1));
        Number step = 1;
        if (arguments.size() == 3) {
            step = asScalar(arguments.get(2));
            if (step == null) {
                step = 1;
            }
        }
        List<ImageWrapper> images = new ArrayList<>();
        if (from != null && to != null) {
            double fromDouble = from.doubleValue();
            double toDouble = to.doubleValue();
            var maxRange = (PixelShiftRange) context.get(PixelShiftRange.class);
            if (maxRange != null) {
                var fromDoubleFixed = Math.max(fromDouble, maxRange.minPixelShift());
                var toDoubleFixed = Math.min(toDouble, maxRange.maxPixelShift());
                if (fromDoubleFixed != fromDouble || toDoubleFixed != toDouble) {
                    LOGGER.warn(String.format(message("restricting.range"), fromDoubleFixed, toDoubleFixed));
                    fromDouble = fromDoubleFixed;
                    toDouble = toDoubleFixed;
                }
            }
            double stepDouble = step.doubleValue();
            if (fromDouble > toDouble) {
                double tmp = fromDouble;
                fromDouble = toDouble;
                toDouble = tmp;
            }
            for (double i = fromDouble; i <= toDouble; i += stepDouble) {
                images.add(findImage(i));
            }
        }
        return Collections.unmodifiableList(images);
    }

    public String exportAsJson() {
        var gson = new Gson()
                .newBuilder()
                .setPrettyPrinting()
                .registerTypeHierarchyAdapter(ImageWrapper.class, new ImageWrapperSerializer((ProcessParams) context.get(ProcessParams.class)))
                .registerTypeAdapter(Ellipse.class, new EllipseSerializer())
                .registerTypeAdapter(SourceInfo.class, new SourceInfoSerializer())
                .registerTypeAdapter(ProcessParams.class, new ProcessParamsSerializer())
                .addSerializationExclusionStrategy(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                        return false;
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> aClass) {
                        return ProgressOperation.class.isAssignableFrom(aClass) ||
                                ReferenceCoords.class.isAssignableFrom(aClass) ||
                                ImageEmitter.class.isAssignableFrom(aClass) ||
                                Broadcaster.class.isAssignableFrom(aClass);
                    }
                })
                .create();
        Map<String, Object> root = new HashMap<>();
        root.put("variables", getVariables());
        Map<String, Object> exportedContext = new HashMap<>();
        context.forEach((k, v) -> {
            switch (k.getSimpleName()) {
                case "ProcessParams" -> exportedContext.put("processParams", v);
                case "PixelShiftRange" -> exportedContext.put("pixelShiftRange", v);
                case "SourceInfo" -> exportedContext.put("sourceInfo", v);
                case "SolarParameters" -> exportedContext.put("solarParams", v);
            }
        });
        root.put("context", exportedContext);
        try {
            return gson.toJson(root);
        } catch (Exception e) {
            return gson.toJson(Map.of("error", e.getMessage()));
        }
    }

    private static class ImageWrapperSerializer implements JsonSerializer<ImageWrapper> {

        private final ProcessParams processParams;

        private ImageWrapperSerializer(ProcessParams processParams) {
            if (processParams == null) {
                processParams = ProcessParamsIO.loadDefaults();
            }
            this.processParams = processParams.withExtraParams(
                    processParams.extraParams().withImageFormats(Set.of(ImageFormat.FITS))
            );
        }

        @Override
        public JsonElement serialize(ImageWrapper imageWrapper, Type type, JsonSerializationContext jsonSerializationContext) {
            var obj = new JsonObject();
            obj.addProperty("type", "image");
            var width = imageWrapper.width();
            var height = imageWrapper.height();
            obj.addProperty("width", width);
            obj.addProperty("height", height);
            if (width > 0 && height > 0) {
                try {
                    var tempFile = TemporaryFolder.newTempFile("image", ".fits");
                    var files = new ImageSaver(CutoffStretchingStrategy.DEFAULT, processParams).save(imageWrapper, tempFile.toFile());
                    obj.addProperty("file", files.getFirst().getAbsolutePath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            var metadata = new LinkedHashMap<String, Object>();
            imageWrapper.metadata().forEach((k, v) -> {
                if (!ProcessParams.class.isAssignableFrom(v.getClass())) {
                    var simpleName = k.getSimpleName();
                    simpleName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
                    metadata.put(simpleName, v);
                }
            });
            obj.add("metadata", jsonSerializationContext.serialize(metadata));
            return obj;
        }
    }

    private static class EllipseSerializer implements JsonSerializer<Ellipse> {

        @Override
        public JsonElement serialize(Ellipse ellipse, Type type, JsonSerializationContext jsonSerializationContext) {
            var coefs = ellipse.getCartesianCoefficients();
            return jsonSerializationContext.serialize(coefs);
        }
    }

    private static class SourceInfoSerializer implements JsonSerializer<SourceInfo> {

        @Override
        public JsonElement serialize(SourceInfo sourceInfo, Type type, JsonSerializationContext jsonSerializationContext) {
            var obj = new JsonObject();
            obj.addProperty("serFileName", sourceInfo.serFileName());
            obj.addProperty("parentDirName", sourceInfo.parentDirName());
            obj.addProperty("dateTime", sourceInfo.dateTime().toString());
            return obj;
        }
    }

    private static class ProcessParamsSerializer implements JsonSerializer<ProcessParams> {

        @Override
        public JsonElement serialize(ProcessParams processParams, Type type, JsonSerializationContext jsonSerializationContext) {
            // inefficient but we don't care
            var jsonString = ProcessParamsIO.serializeToJson(processParams);
            return JsonParser.parseString(jsonString);
        }
    }
}
