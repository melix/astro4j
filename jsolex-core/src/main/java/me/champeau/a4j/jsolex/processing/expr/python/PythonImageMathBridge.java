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
package me.champeau.a4j.jsolex.processing.expr.python;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.sun.workflow.SpectralLinePolynomial;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.spectrum.SpectrumAnalyzer;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.ImageConverter;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.Point2D;
import me.champeau.a4j.math.regression.Ellipse;
import org.apache.arrow.c.ArrowArray;
import org.apache.arrow.c.ArrowSchema;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float4Vector;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bridge class that exposes ImageMath functions to Python scripts.
 * Methods annotated with @HostAccess.Export are callable from Python.
 */
public class PythonImageMathBridge implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonImageMathBridge.class);

    private final AbstractImageExpressionEvaluator evaluator;
    private final Map<Class<?>, Object> context;
    private final Broadcaster broadcaster;
    private final boolean allowVariableCreation;
    private BufferAllocator allocator;
    private SerFileReader reopenedReader;

    public PythonImageMathBridge(AbstractImageExpressionEvaluator evaluator,
                                  Map<Class<?>, Object> context,
                                  Broadcaster broadcaster,
                                  boolean allowVariableCreation) {
        this.evaluator = evaluator;
        this.context = context;
        this.broadcaster = broadcaster;
        this.allowVariableCreation = allowVariableCreation;
    }

    @Override
    public void close() {
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
        if (reopenedReader != null) {
            try {
                reopenedReader.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing reopened SER file reader", e);
            }
            reopenedReader = null;
        }
    }

    private BufferAllocator getAllocator() {
        if (allocator == null) {
            allocator = new RootAllocator();
        }
        return allocator;
    }

    private SerFileReader getOpenReader() {
        if (reopenedReader == null) {
            var reader = (SerFileReader) context.get(SerFileReader.class);
            if (reader != null) {
                try {
                    reopenedReader = reader.reopen();
                } catch (Exception e) {
                    LOGGER.warn("Failed to reopen SER file reader", e);
                }
            }
        }
        return reopenedReader;
    }

    // ========== Core Functions ==========

    /**
     * Loads an image from a file path.
     *
     * @param path the file path
     * @return the loaded image
     */
    @HostAccess.Export
    public ImageWrapper load(String path) {
        return (ImageWrapper) evaluator.functionCall(BuiltinFunction.LOAD, Map.of("file", path));
    }

    /**
     * Saves an image to a FITS file.
     *
     * @param img the image to save
     * @param path the file path
     */
    @HostAccess.Export
    public void save(ImageWrapper img, String path) {
        var processParams = (ProcessParams) context.get(ProcessParams.class);
        FitsUtils.writeFitsFile(unwrap(img), Path.of(path).toFile(), processParams);
    }

    /**
     * Emits an image to the JSol'Ex UI during script execution.
     * The image will be displayed in the image viewer as a script-generated image.
     *
     * @param img the image to emit
     * @param title the display title for the image
     * @param name the file name (optional, defaults to title)
     * @param category the category (optional)
     * @param description the description (optional)
     */
    @HostAccess.Export
    public void emit(ImageWrapper img, String title, String name, String category, String description) {
        var imageEmitter = (ImageEmitter) context.get(ImageEmitter.class);
        if (imageEmitter == null) {
            LOGGER.warn("No ImageEmitter in context, cannot emit image: {}", title);
            return;
        }
        var unwrapped = unwrap(img);
        var effectiveName = name != null ? name : title;
        if (unwrapped instanceof ImageWrapper32 mono) {
            imageEmitter.newMonoImage(GeneratedImageKind.IMAGE_MATH, category, title, effectiveName, description, mono);
        } else if (unwrapped instanceof RGBImage rgb) {
            imageEmitter.newColorImage(GeneratedImageKind.IMAGE_MATH, category, title, effectiveName, description,
                    rgb.width(), rgb.height(), rgb.metadata(), () -> new float[][][] {rgb.r(), rgb.g(), rgb.b()});
        } else {
            LOGGER.warn("Unsupported image type for emit: {}", unwrapped.getClass().getName());
        }
        LOGGER.debug("Emitted image: {}", title);
    }

    /**
     * Generic function call - invokes any ImageMath builtin function by name.
     *
     * @param functionName the function name (case-insensitive)
     * @param args the function arguments as a map
     * @return the function result
     */
    @HostAccess.Export
    public Object call(String functionName, Object args) {
        var builtin = BuiltinFunction.valueOf(functionName.toUpperCase(Locale.US));
        var argsMap = convertToJavaMap(args);
        return evaluator.functionCall(builtin, argsMap);
    }

    /**
     * Generic function call with positional arguments - invokes any ImageMath builtin function by name.
     * Positional arguments are mapped to parameter names based on the function's parameter order.
     *
     * @param functionName the function name (case-insensitive)
     * @param positionalArgs positional arguments as a list
     * @param kwargs keyword arguments as a map
     * @return the function result
     */
    @HostAccess.Export
    public Object callWithPositionalArgs(String functionName, Object positionalArgs, Object kwargs) {
        var builtin = BuiltinFunction.valueOf(functionName.toUpperCase(Locale.US));
        var argsMap = new HashMap<String, Object>();

        // Get parameter names in order
        var paramInfo = builtin.getParameterInfo();
        var paramNames = paramInfo.stream()
                .map(BuiltinFunction.FunctionParameter::name)
                .toList();

        // Map positional arguments to parameter names
        var posList = convertToJavaList(positionalArgs);
        for (int i = 0; i < posList.size() && i < paramNames.size(); i++) {
            argsMap.put(paramNames.get(i), convertFromPythonValue(posList.get(i)));
        }

        // Merge keyword arguments (they override positional)
        argsMap.putAll(convertToJavaMap(kwargs));

        return evaluator.functionCall(builtin, argsMap);
    }

    /**
     * Calls a user-defined ImageMath function.
     *
     * @param name the function name
     * @param args the function arguments
     * @return the function result
     */
    @HostAccess.Export
    public Object callUserFunction(String name, Object args) {
        var userFunctions = evaluator.getUserFunctions();
        var fn = userFunctions.get(name);
        if (fn == null) {
            throw new IllegalArgumentException("User function not found: " + name);
        }
        var argsMap = convertToJavaMap(args);
        return fn.invoke(argsMap);
    }

    /**
     * Calls a user-defined ImageMath function with positional arguments.
     *
     * @param name the function name
     * @param positionalArgs positional arguments as a list
     * @param kwargs keyword arguments as a map
     * @return the function result
     */
    @HostAccess.Export
    public Object callUserFunctionWithPositionalArgs(String name, Object positionalArgs, Object kwargs) {
        var userFunctions = evaluator.getUserFunctions();
        var fn = userFunctions.get(name);
        if (fn == null) {
            throw new IllegalArgumentException("User function not found: " + name);
        }

        var argsMap = new HashMap<String, Object>();

        // Get parameter names from the user function
        var paramNames = fn.arguments();

        // Map positional arguments to parameter names
        var posList = convertToJavaList(positionalArgs);
        for (int i = 0; i < posList.size() && i < paramNames.size(); i++) {
            argsMap.put(paramNames.get(i), convertFromPythonValue(posList.get(i)));
        }

        // Merge keyword arguments (they override positional)
        argsMap.putAll(convertToJavaMap(kwargs));

        return fn.invoke(argsMap);
    }

    /**
     * Calls any function by name - tries builtin first, then falls back to user-defined.
     *
     * @param functionName the function name
     * @param args the function arguments as a map
     * @return the function result
     */
    @HostAccess.Export
    public Object callAny(String functionName, Object args) {
        // Try builtin first - separate lookup from execution to avoid catching
        // IllegalArgumentException from the function call itself
        var builtin = lookupBuiltin(functionName);
        if (builtin != null) {
            var argsMap = convertToJavaMap(args);
            return evaluator.functionCall(builtin, argsMap);
        }

        // Not a builtin, try user function
        var userFunctions = evaluator.getUserFunctions();
        var fn = userFunctions.get(functionName);
        if (fn != null) {
            var argsMap = convertToJavaMap(args);
            return fn.invoke(argsMap);
        }
        throw new IllegalArgumentException("Unknown function: " + functionName);
    }

    /**
     * Calls any function by name with positional arguments - tries builtin first, then falls back to user-defined.
     *
     * @param functionName the function name
     * @param positionalArgs positional arguments as a list
     * @param kwargs keyword arguments as a map
     * @return the function result
     */
    @HostAccess.Export
    public Object callAnyWithPositionalArgs(String functionName, Object positionalArgs, Object kwargs) {
        // Try builtin first - separate lookup from execution to avoid catching
        // IllegalArgumentException from the function call itself
        var builtin = lookupBuiltin(functionName);
        if (builtin != null) {
            var argsMap = new HashMap<String, Object>();

            var paramInfo = builtin.getParameterInfo();
            var paramNames = paramInfo.stream()
                    .map(BuiltinFunction.FunctionParameter::name)
                    .toList();

            var posList = convertToJavaList(positionalArgs);
            for (int i = 0; i < posList.size() && i < paramNames.size(); i++) {
                argsMap.put(paramNames.get(i), convertFromPythonValue(posList.get(i)));
            }

            argsMap.putAll(convertToJavaMap(kwargs));
            return evaluator.functionCall(builtin, argsMap);
        }

        // Not a builtin, try user function
        var userFunctions = evaluator.getUserFunctions();
        var fn = userFunctions.get(functionName);
        if (fn != null) {
            var argsMap = new HashMap<String, Object>();
            var paramNames = fn.arguments();

            var posList = convertToJavaList(positionalArgs);
            for (int i = 0; i < posList.size() && i < paramNames.size(); i++) {
                argsMap.put(paramNames.get(i), convertFromPythonValue(posList.get(i)));
            }

            argsMap.putAll(convertToJavaMap(kwargs));
            return fn.invoke(argsMap);
        }
        throw new IllegalArgumentException("Unknown function: " + functionName);
    }

    // ========== Variable Access ==========

    /**
     * Gets a variable from the ImageMath context.
     *
     * @param name the variable name
     * @return the variable value, or null if not found
     */
    @HostAccess.Export
    public Object getVariable(String name) {
        return evaluator.getVariables().get(name);
    }

    /**
     * Gets a variable from the ImageMath context with a default value.
     *
     * @param name the variable name
     * @param defaultValue the default value to return if the variable is not found or is null
     * @return the variable value, or the default value if not found or null
     */
    @HostAccess.Export
    public Object getVariable(String name, Object defaultValue) {
        var value = evaluator.getVariables().get(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Sets a variable in the ImageMath context.
     * For embedded Python (via python() function), the variable must already exist.
     * For standalone Python scripts, new variables can be created.
     *
     * @param name the variable name
     * @param value the variable value
     */
    @HostAccess.Export
    public void setVariable(String name, Object value) {
        if (!allowVariableCreation && !evaluator.getVariables().containsKey(name)) {
            throw new IllegalArgumentException(
                    "Variable '" + name + "' does not exist. " +
                    "Variables must be declared in ImageMath before they can be modified by Python. " +
                    "Example: declare '" + name + " = 0' before the python() call.");
        }
        var convertedValue = convertFromPythonValue(value);
        evaluator.putVariable(name, convertedValue);
    }

    /**
     * Gets the current processing parameters.
     *
     * @return the process parameters, or null if not available
     */
    @HostAccess.Export
    public ProcessParams getProcessParams() {
        return (ProcessParams) context.get(ProcessParams.class);
    }

    // ========== Image Properties ==========

    /**
     * Gets the raw pixel data from an image as a 2D array.
     *
     * @param img the image
     * @return the pixel data
     */
    @HostAccess.Export
    public float[][] getData(ImageWrapper img) {
        var unwrapped = unwrap(img);
        if (unwrapped instanceof ImageWrapper32 mono) {
            return mono.data();
        }
        throw new IllegalArgumentException("getData only works on mono images");
    }

    /**
     * Creates a new mono image from pixel data.
     *
     * @param width the image width
     * @param height the image height
     * @param data the pixel data
     * @return the new image
     */
    @HostAccess.Export
    public ImageWrapper32 createMono(int width, int height, Object data) {
        var floatData = convertToFloatArray(data, width, height);
        return new ImageWrapper32(width, height, floatData, new HashMap<>());
    }

    /**
     * Copies all metadata from source image to target image.
     * This allows images generated by Python (e.g., matplotlib plots) to inherit
     * metadata like Ellipse, SolarParameters, etc. from the original processed images.
     *
     * @param source the source image to copy metadata from
     * @param target the target image to copy metadata to
     */
    @HostAccess.Export
    public void copyMetadataFrom(ImageWrapper source, ImageWrapper target) {
        var srcUnwrapped = unwrap(source);
        var tgtUnwrapped = unwrap(target);
        tgtUnwrapped.metadata().putAll(srcUnwrapped.metadata());
    }

    // ========== Apache Arrow Zero-Copy Methods ==========

    /**
     * Exports image data as Apache Arrow array for zero-copy transfer to Python.
     * Works with both mono and RGB images.
     *
     * @param img the image to export
     * @return [array_address, schema_address, width, height, channels] where channels is 1 for mono, 3 for RGB
     */
    @HostAccess.Export
    public long[] arrowExport(ImageWrapper img) {
        var unwrapped = unwrap(img);
        if (unwrapped instanceof ImageWrapper32 mono) {
            return arrowExportMono(mono);
        } else if (unwrapped instanceof RGBImage rgb) {
            return arrowExportRGB(rgb);
        }
        throw new IllegalArgumentException("Unsupported image type: " + unwrapped.getClass().getName());
    }

    private long[] arrowExportMono(ImageWrapper32 mono) {
        var data = mono.data();
        int height = mono.height();
        int width = mono.width();
        int totalElements = width * height;
        var alloc = getAllocator();

        var vector = new Float4Vector("pixels", alloc);
        vector.allocateNew(totalElements);

        int index = 0;
        for (float[] row : data) {
            for (float value : row) {
                vector.set(index++, value);
            }
        }
        vector.setValueCount(totalElements);

        var arrowArray = ArrowArray.allocateNew(alloc);
        var arrowSchema = ArrowSchema.allocateNew(alloc);
        Data.exportVector(alloc, vector, null, arrowArray, arrowSchema);

        return new long[]{arrowArray.memoryAddress(), arrowSchema.memoryAddress(), width, height, 1};
    }

    private long[] arrowExportRGB(RGBImage rgb) {
        int height = rgb.height();
        int width = rgb.width();
        int totalElements = width * height * 3;
        var alloc = getAllocator();

        var vector = new Float4Vector("pixels", alloc);
        vector.allocateNew(totalElements);

        // Interleave RGB data: [r0, g0, b0, r1, g1, b1, ...]
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                vector.set(index++, rgb.r()[y][x]);
                vector.set(index++, rgb.g()[y][x]);
                vector.set(index++, rgb.b()[y][x]);
            }
        }
        vector.setValueCount(totalElements);

        var arrowArray = ArrowArray.allocateNew(alloc);
        var arrowSchema = ArrowSchema.allocateNew(alloc);
        Data.exportVector(alloc, vector, null, arrowArray, arrowSchema);

        return new long[]{arrowArray.memoryAddress(), arrowSchema.memoryAddress(), width, height, 3};
    }

    /**
     * Creates a mono image from Arrow array data.
     *
     * @param width the image width
     * @param height the image height
     * @param arrayAddress the C Data Interface array memory address
     * @param schemaAddress the C Data Interface schema memory address
     * @return the new mono image
     */
    @HostAccess.Export
    public ImageWrapper32 arrowImportMono(int width, int height, long arrayAddress, long schemaAddress) {
        try (var arrowArray = ArrowArray.wrap(arrayAddress);
             var arrowSchema = ArrowSchema.wrap(schemaAddress)) {

            var vector = (Float4Vector) Data.importVector(getAllocator(), arrowArray, arrowSchema, null);
            int totalElements = width * height;

            if (vector.getValueCount() < totalElements) {
                throw new IllegalArgumentException(
                        "Arrow array has " + vector.getValueCount() + " elements, expected " + totalElements);
            }

            var result = new float[height][width];
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    result[y][x] = vector.get(index++);
                }
            }

            vector.close();
            return new ImageWrapper32(width, height, result, new HashMap<>());
        }
    }

    /**
     * Creates an RGB image from Arrow array data (interleaved RGB format).
     *
     * @param width the image width
     * @param height the image height
     * @param arrayAddress the C Data Interface array memory address
     * @param schemaAddress the C Data Interface schema memory address
     * @return the new RGB image
     */
    @HostAccess.Export
    public RGBImage arrowImportRGB(int width, int height, long arrayAddress, long schemaAddress) {
        try (var arrowArray = ArrowArray.wrap(arrayAddress);
             var arrowSchema = ArrowSchema.wrap(schemaAddress)) {

            var vector = (Float4Vector) Data.importVector(getAllocator(), arrowArray, arrowSchema, null);
            int totalElements = width * height * 3;

            if (vector.getValueCount() < totalElements) {
                throw new IllegalArgumentException(
                        "Arrow array has " + vector.getValueCount() + " elements, expected " + totalElements);
            }

            var r = new float[height][width];
            var g = new float[height][width];
            var b = new float[height][width];

            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    r[y][x] = vector.get(index++);
                    g[y][x] = vector.get(index++);
                    b[y][x] = vector.get(index++);
                }
            }

            vector.close();
            return new RGBImage(width, height, r, g, b, new HashMap<>());
        }
    }

    /**
     * Allocates Arrow C Data Interface structures for Python to export into.
     *
     * @return [array_address, schema_address]
     */
    @HostAccess.Export
    public long[] arrowAllocate() {
        var alloc = getAllocator();
        var arrowArray = ArrowArray.allocateNew(alloc);
        var arrowSchema = ArrowSchema.allocateNew(alloc);
        return new long[]{arrowArray.memoryAddress(), arrowSchema.memoryAddress()};
    }

    // ========== Spectral Profile Extraction ==========

    /**
     * Extracts a spectral profile at the given image coordinates.
     * This reads directly from the SER file to get the raw spectral data
     * along the polynomial baseline at the position corresponding to (x, y).
     *
     * @param img the reference image (used to get metadata)
     * @param x   the x coordinate in image space
     * @param y   the y coordinate in image space
     * @return array of intensity values across the spectral range, or null if not available
     */
    @HostAccess.Export
    public float[] extractProfile(ImageWrapper img, double x, double y) {
        var pixelShiftRange = (PixelShiftRange) context.get(PixelShiftRange.class);
        if (pixelShiftRange == null) {
            LOGGER.warn("PixelShiftRange not available in context");
            return null;
        }

        var reader = getOpenReader();
        if (reader == null) {
            LOGGER.warn("SerFileReader not available in context");
            return null;
        }

        var polynomial = (SpectralLinePolynomial) context.get(SpectralLinePolynomial.class);
        if (polynomial == null) {
            LOGGER.warn("SpectralLinePolynomial not available in context");
            return null;
        }

        // Get ReferenceCoords to transform image coordinates back to frame coordinates
        var refCoords = img.findMetadata(ReferenceCoords.class).orElse(null);
        if (refCoords == null) {
            refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
        }
        if (refCoords == null) {
            LOGGER.warn("ReferenceCoords not available");
            return null;
        }

        // Transform image coordinates to original/frame coordinates
        var original = refCoords.determineOriginalCoordinates(new Point2D(x, y), ReferenceCoords.NO_LIMIT);
        int column = (int) Math.round(original.x());
        int frameNumber = (int) Math.round(original.y());

        double minShift = pixelShiftRange.minPixelShift();
        double maxShift = pixelShiftRange.maxPixelShift();
        // Number of samples when iterating from minShift to < maxShift with step 1.0
        int numSamples = (int) Math.ceil(maxShift - minShift);

        var profile = new float[numSamples];

        try {
            var header = reader.header();
            var geometry = header.geometry();
            int frameHeight = geometry.height();
            int frameWidth = geometry.width();

            if (frameNumber < 0 || frameNumber >= header.frameCount()) {
                LOGGER.warn("Frame number {} out of range [0, {})", frameNumber, header.frameCount());
                return null;
            }
            if (column < 0 || column >= frameWidth) {
                LOGGER.warn("Column {} out of range [0, {})", column, frameWidth);
                return null;
            }

            // Create fresh converter and buffer for each call (like the built-in does)
            // This avoids race conditions when multiple scripts call extractProfile concurrently
            var converter = ImageUtils.createImageConverter(geometry.colorMode());
            float[][] frameBuffer;

            // Synchronize on reader since it's shared between parallel scripts
            synchronized (reader) {
                reader.seekFrame(frameNumber);
                var frame = reader.currentFrame();
                var buffer = converter.createBuffer(geometry);
                converter.convert(frameNumber, frame.data(), geometry, buffer);
                // Create a copy to use outside the synchronized block
                frameBuffer = buffer;
            }

            // Extract profile along the polynomial baseline
            int index = 0;
            int avgRadius = 4; // Average over nearby columns for noise reduction (matches built-in)

            for (double pixelShift = minShift; pixelShift < maxShift; pixelShift++) {
                double sum = 0;
                int count = 0;

                for (int colOffset = -avgRadius; colOffset <= avgRadius; colOffset++) {
                    int col = column + colOffset;
                    if (col >= 0 && col < frameWidth) {
                        double colPolyValue = polynomial.applyAsDouble(col);
                        double exactY = colPolyValue + pixelShift;
                        int lowerY = (int) Math.floor(exactY);
                        int upperY = (int) Math.ceil(exactY);

                        if (lowerY >= 0 && upperY < frameHeight) {
                            float lowerValue = frameBuffer[lowerY][col];
                            float upperValue = frameBuffer[upperY][col];
                            // Linear interpolation
                            sum += lowerValue + (upperValue - lowerValue) * (exactY - lowerY);
                            count++;
                        }
                    }
                }

                profile[index] = count > 0 ? (float) (sum / count) : 0f;
                index++;
            }

            return profile;
        } catch (Exception e) {
            LOGGER.warn("Error extracting profile from SER file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns the polynomial coefficients for the spectral line position.
     * The polynomial is: y = a*x³ + b*x² + c*x + d where x is the column number.
     *
     * @return array [a, b, c, d] or null if not available
     */
    @HostAccess.Export
    public double[] getPolynomialCoefficients() {
        var polynomial = (SpectralLinePolynomial) context.get(SpectralLinePolynomial.class);
        if (polynomial == null) {
            return null;
        }
        var coeffs = polynomial.coefficients();
        return new double[]{coeffs.a(), coeffs.b(), coeffs.c(), coeffs.d()};
    }

    /**
     * Reads a single frame from the SER file.
     * Returns an ImageWrapper32 that can be converted to numpy via toNumpy().
     *
     * @param frameNumber the frame number to read
     * @return ImageWrapper32 containing the frame data, or null if failed
     */
    @HostAccess.Export
    public ImageWrapper32 readFrame(int frameNumber) {
        var reader = getOpenReader();
        if (reader == null) {
            LOGGER.warn("SerFileReader not available in context");
            return null;
        }

        try {
            var header = reader.header();
            var geometry = header.geometry();

            if (frameNumber < 0 || frameNumber >= header.frameCount()) {
                LOGGER.warn("Frame number {} out of range [0, {})", frameNumber, header.frameCount());
                return null;
            }

            var converter = ImageUtils.createImageConverter(geometry.colorMode());
            float[][] buffer;

            synchronized (reader) {
                reader.seekFrame(frameNumber);
                var frame = reader.currentFrame();
                buffer = converter.createBuffer(geometry);
                converter.convert(frameNumber, frame.data(), geometry, buffer);
            }

            return new ImageWrapper32(geometry.width(), geometry.height(), buffer, Map.of());
        } catch (Exception e) {
            LOGGER.warn("Error reading frame {}: {}", frameNumber, e.getMessage());
            return null;
        }
    }

    /**
     * Returns debug information about profile extraction at a given image position.
     * Useful for diagnosing why velocity measurements might be returning unexpected values.
     *
     * @param img the image (used to get metadata)
     * @param x   the x coordinate in image space
     * @param y   the y coordinate in image space
     * @return map with debug information including coordinate transformations, profile stats, etc.
     */
    @HostAccess.Export
    public Map<String, Object> debugExtractProfile(ImageWrapper img, double x, double y) {
        var result = new LinkedHashMap<String, Object>();

        // Check context items
        var pixelShiftRange = (PixelShiftRange) context.get(PixelShiftRange.class);
        result.put("hasPixelShiftRange", pixelShiftRange != null);
        if (pixelShiftRange != null) {
            result.put("minShift", pixelShiftRange.minPixelShift());
            result.put("maxShift", pixelShiftRange.maxPixelShift());
        }

        var reader = getOpenReader();
        result.put("hasSerReader", reader != null);

        var polynomial = (SpectralLinePolynomial) context.get(SpectralLinePolynomial.class);
        result.put("hasPolynomial", polynomial != null);
        if (polynomial != null) {
            var coeffs = polynomial.coefficients();
            result.put("polyA", coeffs.a());
            result.put("polyB", coeffs.b());
            result.put("polyC", coeffs.c());
            result.put("polyD", coeffs.d());
        }

        // Check image metadata
        var refCoords = img.findMetadata(ReferenceCoords.class).orElse(null);
        if (refCoords == null) {
            refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
            result.put("refCoordsSource", "context");
        } else {
            result.put("refCoordsSource", "imageMetadata");
        }
        result.put("hasReferenceCoords", refCoords != null);

        if (refCoords != null) {
            result.put("refCoordsOperations", refCoords.operations().size());
            // Transform coordinates
            var original = refCoords.determineOriginalCoordinates(new Point2D(x, y), ReferenceCoords.NO_LIMIT);
            result.put("originalX", original.x());
            result.put("originalY", original.y());
            result.put("frameNumber", (int) Math.round(original.y()));
            result.put("column", (int) Math.round(original.x()));
        }

        // Try to read frame and extract sample values
        if (reader != null && refCoords != null && polynomial != null && pixelShiftRange != null) {
            try {
                var original = refCoords.determineOriginalCoordinates(new Point2D(x, y), ReferenceCoords.NO_LIMIT);
                int column = (int) Math.round(original.x());
                int frameNumber = (int) Math.round(original.y());

                var header = reader.header();
                var geometry = header.geometry();
                int frameHeight = geometry.height();
                int frameWidth = geometry.width();

                result.put("frameHeight", frameHeight);
                result.put("frameWidth", frameWidth);
                result.put("totalFrames", header.frameCount());
                result.put("columnInRange", column >= 0 && column < frameWidth);
                result.put("frameInRange", frameNumber >= 0 && frameNumber < header.frameCount());

                if (column >= 0 && column < frameWidth && frameNumber >= 0 && frameNumber < header.frameCount()) {
                    // Compute polynomial value at column
                    double polyValue = polynomial.applyAsDouble(column);
                    result.put("polyValueAtColumn", polyValue);

                    // Extract profile and compute stats
                    var profile = extractProfile(img, x, y);
                    if (profile != null) {
                        result.put("profileLength", profile.length);

                        // Find min and max
                        float minVal = Float.MAX_VALUE;
                        float maxVal = Float.MIN_VALUE;
                        int minIdx = 0;
                        for (int i = 0; i < profile.length; i++) {
                            if (profile[i] < minVal) {
                                minVal = profile[i];
                                minIdx = i;
                            }
                            if (profile[i] > maxVal) {
                                maxVal = profile[i];
                            }
                        }
                        result.put("profileMin", minVal);
                        result.put("profileMax", maxVal);
                        result.put("profileMinIndex", minIdx);
                        result.put("profileMinShift", pixelShiftRange.minPixelShift() + minIdx);

                        // Sample some profile values
                        var samples = new ArrayList<Double>();
                        int step = Math.max(1, profile.length / 10);
                        for (int i = 0; i < profile.length; i += step) {
                            samples.add((double) profile[i]);
                        }
                        result.put("profileSamples", samples);
                    } else {
                        result.put("profileError", "extractProfile returned null");
                    }
                }
            } catch (Exception e) {
                result.put("error", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Returns the available pixel shift range for spectral sampling.
     *
     * @return map with minShift, maxShift, and step values, or null if not available
     */
    @HostAccess.Export
    public Map<String, Object> getPixelShiftRange() {
        var pixelShiftRange = (PixelShiftRange) context.get(PixelShiftRange.class);
        if (pixelShiftRange == null) {
            return null;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("minShift", pixelShiftRange.minPixelShift());
        result.put("maxShift", pixelShiftRange.maxPixelShift());
        // extractProfile uses step=1 (like the built-in extractPointProfile),
        // not the step from PixelShiftRange which is used for different purposes
        result.put("step", 1.0);
        return result;
    }

    /**
     * Returns image orientation flags based on reference coordinates.
     * Used to determine east/west and north/south sign conventions.
     *
     * @return map with hasHFlip and hasVFlip boolean flags
     */
    @HostAccess.Export
    public Map<String, Object> getOrientationFlags() {
        var refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
        var result = new LinkedHashMap<String, Object>();

        if (refCoords == null) {
            result.put("hasHFlip", false);
            result.put("hasVFlip", false);
            result.put("available", false);
            return result;
        }

        boolean hasHFlip = refCoords.operations().stream()
            .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.HFLIP);
        boolean hasVFlip = refCoords.operations().stream()
            .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.VFLIP);

        result.put("hasHFlip", hasHFlip);
        result.put("hasVFlip", hasVFlip);
        result.put("available", true);
        return result;
    }

    /**
     * Returns source file information.
     *
     * @return map with file name, parent directory, datetime, width, and height
     */
    @HostAccess.Export
    public Map<String, Object> getSourceInfo() {
        var sourceInfo = (SourceInfo) context.get(SourceInfo.class);
        if (sourceInfo == null) {
            return null;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("fileName", sourceInfo.serFileName());
        result.put("parentDir", sourceInfo.parentDirName());
        result.put("dateTime", sourceInfo.dateTime() != null ? sourceInfo.dateTime().toString() : null);
        result.put("width", sourceInfo.width());
        result.put("height", sourceInfo.height());
        return result;
    }

    /**
     * Returns the spectral dispersion (wavelength per pixel) if available.
     * Computed from instrument parameters (grating, focal length, pixel size).
     *
     * @return map with angstromsPerPixel and nanosPerPixel, or null if not available
     */
    @HostAccess.Export
    public Map<String, Object> getDispersion() {
        var processParams = (ProcessParams) context.get(ProcessParams.class);
        if (processParams == null) {
            return null;
        }
        var obs = processParams.observationDetails();
        var instrument = obs.instrument();
        var pixelSize = obs.pixelSize();
        var binning = obs.binning();
        var lambda0 = processParams.spectrumParams().ray().wavelength();

        if (instrument == null || pixelSize == null || binning == null || lambda0.nanos() <= 0) {
            return null;
        }

        try {
            var dispersion = SpectrumAnalyzer.computeSpectralDispersion(instrument, lambda0, pixelSize * binning);
            var result = new LinkedHashMap<String, Object>();
            result.put("angstromsPerPixel", dispersion.angstromsPerPixel());
            result.put("nanosPerPixel", dispersion.nanosPerPixel());
            return result;
        } catch (Exception e) {
            LOGGER.debug("Could not compute dispersion: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns the spectral line wavelength from process parameters.
     *
     * @return map with angstroms, nanos, and label, or null if not available
     */
    @HostAccess.Export
    public Map<String, Object> getWavelength() {
        var processParams = (ProcessParams) context.get(ProcessParams.class);
        if (processParams == null) {
            return null;
        }
        var ray = processParams.spectrumParams().ray();
        if (ray == null) {
            return null;
        }
        var lambda0 = ray.wavelength();
        if (lambda0.nanos() <= 0) {
            return null;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("angstroms", lambda0.angstroms());
        result.put("nanos", lambda0.nanos());
        result.put("label", ray.label());
        return result;
    }

    // ========== Coordinate Conversion ==========

    /**
     * Converts image coordinates to SER frame coordinates.
     * Frame coordinates are (frameNumber, xInFrame, yInFrame) where:
     * - frameNumber: which frame in the SER file (derived from image y after reversing transforms)
     * - xInFrame: horizontal position within the frame
     * - yInFrame: vertical position within the frame (spectral dimension), computed from polynomial + pixelShift
     *
     * @param img        the image (used to get metadata including PixelShift if pixelShift is null)
     * @param x          the x coordinate in image space
     * @param y          the y coordinate in image space
     * @param pixelShift the pixel shift (wavelength offset), or null to use image's PixelShift metadata
     * @return map with frameNumber, xInFrame, yInFrame (yInFrame is null if polynomial unavailable)
     */
    @HostAccess.Export
    public Map<String, Object> imageToFrameCoords(ImageWrapper img, double x, double y, Double pixelShift) {
        var result = new LinkedHashMap<String, Object>();

        // Get pixel shift from parameter or image metadata
        double effectivePixelShift;
        if (pixelShift != null) {
            effectivePixelShift = pixelShift;
        } else {
            var imgPixelShift = img.findMetadata(PixelShift.class).orElse(null);
            if (imgPixelShift != null) {
                effectivePixelShift = imgPixelShift.pixelShift();
            } else {
                effectivePixelShift = 0.0;
            }
        }

        // Get ReferenceCoords to reverse transformations
        var refCoords = img.findMetadata(ReferenceCoords.class).orElse(null);
        if (refCoords == null) {
            refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
        }

        double frameNumber;
        double xInFrame;

        if (refCoords == null) {
            // No transformation info, assume identity
            frameNumber = y;
            xInFrame = x;
            result.put("available", false);
        } else {
            var original = refCoords.determineOriginalCoordinates(new Point2D(x, y), ReferenceCoords.NO_LIMIT);
            frameNumber = original.y();
            xInFrame = original.x();
            result.put("available", true);
        }

        result.put("frameNumber", (int) Math.round(frameNumber));
        result.put("xInFrame", xInFrame);
        result.put("pixelShift", effectivePixelShift);

        // Compute yInFrame if polynomial is available
        var polynomial = (SpectralLinePolynomial) context.get(SpectralLinePolynomial.class);
        if (polynomial != null) {
            double yInFrame = polynomial.computeYInFrame(xInFrame, effectivePixelShift);
            result.put("yInFrame", yInFrame);
        } else {
            result.put("yInFrame", null);
        }

        return result;
    }

    /**
     * Converts SER frame coordinates to image coordinates.
     * This applies all transformations recorded in ReferenceCoords in forward order.
     *
     * @param img         the image (used to get metadata)
     * @param frameNumber the frame number in the SER file
     * @param xInFrame    the x position within the frame
     * @return map with image x and y coordinates
     */
    @HostAccess.Export
    public Map<String, Object> frameToImageCoords(ImageWrapper img, int frameNumber, double xInFrame) {
        var refCoords = img.findMetadata(ReferenceCoords.class).orElse(null);
        if (refCoords == null) {
            refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
        }

        var result = new LinkedHashMap<String, Object>();
        if (refCoords == null) {
            result.put("x", xInFrame);
            result.put("y", (double) frameNumber);
            result.put("available", false);
            return result;
        }

        // Frame coordinates: x = xInFrame, y = frameNumber
        var transformed = applyForwardTransformations(refCoords, new Point2D(xInFrame, frameNumber));
        result.put("x", transformed.x());
        result.put("y", transformed.y());
        result.put("available", true);
        return result;
    }

    /**
     * Applies forward transformations (the inverse of determineOriginalCoordinates).
     */
    private Point2D applyForwardTransformations(ReferenceCoords refCoords, Point2D point) {
        var current = point;
        for (var operation : refCoords.operations()) {
            switch (operation.kind()) {
                case ROTATION -> {
                    if (operation.values().length >= 3) {
                        Point2D rotationCenter = new Point2D(operation.value(1), operation.value(2));
                        current = rotatePoint(rotationCenter, current, operation.value());
                    }
                }
                case LEFT_ROTATION -> {
                    // Forward: (x, y) -> (y, height - x)
                    double originalHeight = operation.value();
                    current = new Point2D(current.y(), originalHeight - current.x());
                }
                case RIGHT_ROTATION -> {
                    // Forward: (x, y) -> (width - y, x)
                    double originalWidth = operation.value();
                    current = new Point2D(originalWidth - current.y(), current.x());
                }
                case HFLIP -> current = new Point2D(operation.value() - current.x(), current.y());
                case VFLIP -> current = new Point2D(current.x(), operation.value() - current.y());
                case SCALE_X -> current = new Point2D(current.x() * operation.value(), current.y());
                case SCALE_Y -> current = new Point2D(current.x(), current.y() * operation.value());
                case SHEAR_SHIFT_COMBINED -> {
                    double shear = operation.value(0);
                    double shift = operation.value(1);
                    var newX = current.x() - shift + current.y() * shear;
                    current = new Point2D(newX, current.y());
                }
                case OFFSET_2D -> current = new Point2D(current.x() - operation.value(0), current.y() - operation.value(1));
                case MARKER -> { /* Skip markers */ }
            }
        }
        return current;
    }

    private static Point2D rotatePoint(Point2D rotationCenter, Point2D point, double angle) {
        var dx = point.x() - rotationCenter.x();
        var dy = point.y() - rotationCenter.y();
        var cos = Math.cos(angle);
        var sin = Math.sin(angle);
        return new Point2D(
            rotationCenter.x() + dx * cos - dy * sin,
            rotationCenter.y() + dx * sin + dy * cos
        );
    }

    // ========== Heliographic Coordinate Conversion ==========

    /**
     * Returns solar parameters (B0, L0, P angles) from context.
     *
     * @return map with b0, l0, p (in degrees), carringtonRotation, and apparentSize
     */
    @HostAccess.Export
    public Map<String, Object> getSolarParameters() {
        var solarParams = (SolarParameters) context.get(SolarParameters.class);
        if (solarParams == null) {
            return null;
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("b0", Math.toDegrees(solarParams.b0()));
        result.put("l0", Math.toDegrees(solarParams.l0()));
        result.put("p", Math.toDegrees(solarParams.p()));
        result.put("carringtonRotation", solarParams.carringtonRotation());
        result.put("apparentSize", solarParams.apparentSize());
        return result;
    }

    /**
     * Returns ellipse (sun disk) parameters from the image or context.
     *
     * @param img the image
     * @return map with centerX, centerY, semiAxisA, semiAxisB, radius (average), rotationAngle
     */
    @HostAccess.Export
    public Map<String, Object> getEllipseParams(ImageWrapper img) {
        var ellipse = img.findMetadata(Ellipse.class).orElse(null);
        if (ellipse == null) {
            ellipse = (Ellipse) context.get(Ellipse.class);
        }
        if (ellipse == null) {
            return null;
        }
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        var result = new LinkedHashMap<String, Object>();
        result.put("centerX", center.a());
        result.put("centerY", center.b());
        result.put("semiAxisA", semiAxis.a());
        result.put("semiAxisB", semiAxis.b());
        result.put("radius", (semiAxis.a() + semiAxis.b()) / 2.0);
        result.put("rotationAngle", Math.toDegrees(ellipse.rotationAngle()));
        return result;
    }

    /**
     * Converts heliographic coordinates (latitude, longitude) to image pixel coordinates.
     * Automatically handles HFLIP/VFLIP orientation corrections based on ReferenceCoords
     * so that scripts don't need to know about image orientation.
     *
     * @param img    the image (used to get ellipse and solar parameters)
     * @param latDeg heliographic latitude in degrees
     * @param lonDeg heliographic longitude in degrees (positive = east, negative = west in Stonyhurst convention)
     * @return map with x, y coordinates and visible flag (true if on front side of sun)
     */
    @HostAccess.Export
    public Map<String, Object> heliographicToImage(ImageWrapper img, double latDeg, double lonDeg) {
        // Get metadata from image first (like built-in does), fall back to context
        var solarParams = img.findMetadata(SolarParameters.class).orElse(null);
        if (solarParams == null) {
            solarParams = (SolarParameters) context.get(SolarParameters.class);
        }
        var ellipse = img.findMetadata(Ellipse.class).orElse(null);
        if (ellipse == null) {
            ellipse = (Ellipse) context.get(Ellipse.class);
        }

        var result = new LinkedHashMap<String, Object>();
        if (solarParams == null || ellipse == null) {
            result.put("x", Double.NaN);
            result.put("y", Double.NaN);
            result.put("visible", false);
            result.put("available", false);
            return result;
        }

        // Check for HFLIP/VFLIP to determine actual image orientation
        // This matches the built-in rotation profile behavior
        var refCoords = img.findMetadata(ReferenceCoords.class).orElse(null);
        if (refCoords == null) {
            refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
        }

        // Longitude sign convention: HFLIP swaps east/west
        // Latitude sign convention: VFLIP swaps north/south
        int lonSign = 1;
        int latSign = 1;
        if (refCoords != null) {
            var hasHFlip = refCoords.operations().stream()
                .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.HFLIP);
            var hasVFlip = refCoords.operations().stream()
                .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.VFLIP);
            lonSign = hasHFlip ? -1 : 1;
            latSign = hasVFlip ? -1 : 1;
        }

        double b0 = solarParams.b0();
        double angleP = solarParams.p();
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        double radius = (semiAxis.a() + semiAxis.b()) / 2.0;
        double centerX = center.a();
        double centerY = center.b();

        // Apply orientation corrections (same as built-in computeRotationProfile)
        double effectiveLatDeg = latSign * latDeg;
        double effectiveLonDeg = lonSign * lonDeg;

        // Convert to radians using colatitude convention (same as built-in computeSphereCoords)
        // colatitude = π/2 - latitude, where latitude=0 is equator, +90 is north pole
        double colatitude = Math.PI / 2 - Math.toRadians(effectiveLatDeg);
        double longitude = Math.toRadians(effectiveLonDeg);

        // Convert spherical to 3D Cartesian, then apply rotations
        var coords = sphericalToCartesian(longitude, colatitude, radius);
        coords = rotateX(coords, -b0);
        coords = rotateZ(coords, -angleP);

        result.put("x", centerX + coords[0]);
        result.put("y", centerY + coords[1]);
        result.put("visible", coords[2] > 0);
        result.put("available", true);
        return result;
    }

    /**
     * Converts image pixel coordinates to heliographic coordinates.
     * Automatically handles HFLIP/VFLIP orientation corrections based on ReferenceCoords
     * so that scripts don't need to know about image orientation.
     *
     * @param img the image (used to get ellipse and solar parameters)
     * @param x   the x coordinate in image space
     * @param y   the y coordinate in image space
     * @return map with lat, lon (in degrees), mu (cosine of heliocentric angle), and onDisk flag
     */
    @HostAccess.Export
    public Map<String, Object> imageToHeliographic(ImageWrapper img, double x, double y) {
        // Get metadata from image first (like built-in does), fall back to context
        var solarParams = img.findMetadata(SolarParameters.class).orElse(null);
        if (solarParams == null) {
            solarParams = (SolarParameters) context.get(SolarParameters.class);
        }
        var ellipse = img.findMetadata(Ellipse.class).orElse(null);
        if (ellipse == null) {
            ellipse = (Ellipse) context.get(Ellipse.class);
        }

        var result = new LinkedHashMap<String, Object>();
        if (solarParams == null || ellipse == null) {
            result.put("lat", Double.NaN);
            result.put("lon", Double.NaN);
            result.put("mu", Double.NaN);
            result.put("onDisk", false);
            result.put("available", false);
            return result;
        }

        // Check for HFLIP/VFLIP to determine actual image orientation
        // This matches the built-in rotation profile behavior
        var refCoords = img.findMetadata(ReferenceCoords.class).orElse(null);
        if (refCoords == null) {
            refCoords = (ReferenceCoords) context.get(ReferenceCoords.class);
        }

        // Longitude sign convention: HFLIP swaps east/west
        // Latitude sign convention: VFLIP swaps north/south
        int lonSign = 1;
        int latSign = 1;
        if (refCoords != null) {
            var hasHFlip = refCoords.operations().stream()
                .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.HFLIP);
            var hasVFlip = refCoords.operations().stream()
                .anyMatch(op -> op.kind() == ReferenceCoords.OperationKind.VFLIP);
            lonSign = hasHFlip ? -1 : 1;
            latSign = hasVFlip ? -1 : 1;
        }

        double b0 = solarParams.b0();
        double angleP = solarParams.p();
        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        double radius = (semiAxis.a() + semiAxis.b()) / 2.0;
        double centerX = center.a();
        double centerY = center.b();

        // Normalize to unit disk
        double nx = (x - centerX) / radius;
        double ny = (y - centerY) / radius;
        double rSq = nx * nx + ny * ny;

        if (rSq > 1.0) {
            result.put("lat", Double.NaN);
            result.put("lon", Double.NaN);
            result.put("mu", Double.NaN);
            result.put("onDisk", false);
            result.put("available", true);
            return result;
        }

        // Compute z from unit sphere
        double nz = Math.sqrt(1.0 - rSq);
        double mu = nz; // mu = cos(heliocentric angle)

        // Reverse rotations: first +P around Z, then +B0 around X
        double[] coords = {nx, ny, nz};
        coords = rotateZ(coords, angleP);
        coords = rotateX(coords, b0);

        // Convert back to spherical coordinates
        // Using the colatitude convention: y = cos(colatitude)
        // colatitude = 0 at north pole, π/2 at equator, π at south pole
        // heliographic latitude = 90° - colatitude_degrees
        double colatitudeRad = Math.acos(coords[1]);
        double latDeg = 90 - Math.toDegrees(colatitudeRad);

        double sinColat = Math.sin(colatitudeRad);
        double lonDeg;
        if (Math.abs(sinColat) < 1e-10) {
            lonDeg = 0;
        } else {
            double sinLon = coords[0] / sinColat;
            double cosLon = coords[2] / sinColat;
            lonDeg = Math.toDegrees(Math.atan2(sinLon, cosLon));
        }

        // Apply orientation corrections (inverse of heliographicToImage)
        result.put("lat", latSign * latDeg);
        result.put("lon", lonSign * lonDeg);
        result.put("mu", mu);
        result.put("onDisk", true);
        result.put("available", true);
        return result;
    }

    // Spherical to Cartesian conversion (matching ImageDraw.ofSpherical)
    private double[] sphericalToCartesian(double longitude, double latitude, double radius) {
        return new double[]{
            Math.sin(longitude) * Math.sin(latitude) * radius,
            Math.cos(latitude) * radius,
            Math.cos(longitude) * Math.sin(latitude) * radius
        };
    }

    // Rotate around X axis
    private double[] rotateX(double[] coords, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{
            coords[0],
            coords[1] * cos - coords[2] * sin,
            coords[1] * sin + coords[2] * cos
        };
    }

    // Rotate around Z axis
    private double[] rotateZ(double[] coords, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{
            coords[0] * cos - coords[1] * sin,
            coords[0] * sin + coords[1] * cos,
            coords[2]
        };
    }

    // ========== Helper Methods ==========

    private BuiltinFunction lookupBuiltin(String functionName) {
        try {
            return BuiltinFunction.valueOf(functionName.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ImageWrapper unwrap(ImageWrapper img) {
        if (img instanceof FileBackedImage fileBacked) {
            return fileBacked.unwrapToMemory();
        }
        return img;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToJavaMap(Object args) {
        if (args == null) {
            return Map.of();
        }
        if (args instanceof Map) {
            var result = new HashMap<String, Object>();
            for (var entry : ((Map<?, ?>) args).entrySet()) {
                var key = entry.getKey().toString();
                var value = convertFromPythonValue(entry.getValue());
                result.put(key, value);
            }
            return result;
        }
        if (args instanceof Value value) {
            if (value.hasHashEntries()) {
                var result = new HashMap<String, Object>();
                var iterator = value.getHashKeysIterator();
                while (iterator.hasIteratorNextElement()) {
                    var key = iterator.getIteratorNextElement();
                    var keyStr = key.isString() ? key.asString() : key.toString();
                    var val = value.getHashValue(key);
                    result.put(keyStr, convertFromPythonValue(val));
                }
                return result;
            }
        }
        throw new IllegalArgumentException("Expected a dict/map for function arguments, got: " + args.getClass());
    }

    private List<Object> convertToJavaList(Object args) {
        if (args == null) {
            return List.of();
        }
        if (args instanceof List<?> list) {
            var result = new ArrayList<Object>();
            for (var item : list) {
                result.add(convertFromPythonValue(item));
            }
            return result;
        }
        if (args instanceof Value value && value.hasArrayElements()) {
            var result = new ArrayList<Object>();
            for (int i = 0; i < value.getArraySize(); i++) {
                result.add(convertFromPythonValue(value.getArrayElement(i)));
            }
            return result;
        }
        // Single value - wrap in list
        return List.of(convertFromPythonValue(args));
    }

    private Object convertFromPythonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Value v) {
            if (v.isNull()) {
                return null;
            }
            if (v.isHostObject()) {
                return v.asHostObject();
            }
            if (v.isBoolean()) {
                return v.asBoolean();
            }
            if (v.isNumber()) {
                return v.asDouble();
            }
            if (v.isString()) {
                return v.asString();
            }
            if (v.hasArrayElements()) {
                var list = new ArrayList<>();
                for (int i = 0; i < v.getArraySize(); i++) {
                    list.add(convertFromPythonValue(v.getArrayElement(i)));
                }
                return list;
            }
            if (v.hasHashEntries()) {
                return convertToJavaMap(v);
            }
            try {
                return v.as(Object.class);
            } catch (Exception e) {
                return v.toString();
            }
        }
        return value;
    }

    private float[][] convertToFloatArray(Object data, int width, int height) {
        var result = new float[height][width];
        if (data instanceof Value value && value.hasArrayElements()) {
            for (int y = 0; y < height && y < value.getArraySize(); y++) {
                var row = value.getArrayElement(y);
                if (row.hasArrayElements()) {
                    for (int x = 0; x < width && x < row.getArraySize(); x++) {
                        result[y][x] = row.getArrayElement(x).asFloat();
                    }
                }
            }
        } else if (data instanceof List<?> rows) {
            for (int y = 0; y < height && y < rows.size(); y++) {
                var row = rows.get(y);
                if (row instanceof List<?> cols) {
                    for (int x = 0; x < width && x < cols.size(); x++) {
                        var val = cols.get(x);
                        if (val instanceof Number num) {
                            result[y][x] = num.floatValue();
                        }
                    }
                }
            }
        } else if (data instanceof float[][] floatData) {
            return floatData;
        }
        return result;
    }
}
