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
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.FitsUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bridge class that exposes ImageMath functions to Python scripts.
 * Methods annotated with @HostAccess.Export are callable from Python.
 */
public class PythonImageMathBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonImageMathBridge.class);

    private final AbstractImageExpressionEvaluator evaluator;
    private final Map<Class<?>, Object> context;
    private final Broadcaster broadcaster;
    private final BufferAllocator allocator;

    public PythonImageMathBridge(AbstractImageExpressionEvaluator evaluator,
                                  Map<Class<?>, Object> context,
                                  Broadcaster broadcaster) {
        this.evaluator = evaluator;
        this.context = context;
        this.broadcaster = broadcaster;
        this.allocator = new RootAllocator();
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
     * Sets a variable in the ImageMath context.
     * The variable must already exist - this method can only update existing variables,
     * not create new ones. This ensures that variables are properly declared in ImageMath
     * before being modified by Python, allowing static analysis to work correctly.
     *
     * @param name the variable name
     * @param value the variable value
     * @throws IllegalArgumentException if the variable does not exist
     */
    @HostAccess.Export
    public void setVariable(String name, Object value) {
        if (!evaluator.getVariables().containsKey(name)) {
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

        var vector = new Float4Vector("pixels", allocator);
        vector.allocateNew(totalElements);

        int index = 0;
        for (float[] row : data) {
            for (float value : row) {
                vector.set(index++, value);
            }
        }
        vector.setValueCount(totalElements);

        var arrowArray = ArrowArray.allocateNew(allocator);
        var arrowSchema = ArrowSchema.allocateNew(allocator);
        Data.exportVector(allocator, vector, null, arrowArray, arrowSchema);

        return new long[]{arrowArray.memoryAddress(), arrowSchema.memoryAddress(), width, height, 1};
    }

    private long[] arrowExportRGB(RGBImage rgb) {
        int height = rgb.height();
        int width = rgb.width();
        int totalElements = width * height * 3;

        var vector = new Float4Vector("pixels", allocator);
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

        var arrowArray = ArrowArray.allocateNew(allocator);
        var arrowSchema = ArrowSchema.allocateNew(allocator);
        Data.exportVector(allocator, vector, null, arrowArray, arrowSchema);

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

            var vector = (Float4Vector) Data.importVector(allocator, arrowArray, arrowSchema, null);
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

            var vector = (Float4Vector) Data.importVector(allocator, arrowArray, arrowSchema, null);
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
        var arrowArray = ArrowArray.allocateNew(allocator);
        var arrowSchema = ArrowSchema.allocateNew(allocator);
        return new long[]{arrowArray.memoryAddress(), arrowSchema.memoryAddress()};
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
