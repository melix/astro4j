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

import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Manages GraalPy context lifecycle and Python script execution.
 * Instances are cached in the evaluation context for reuse across multiple python() calls.
 */
public class PythonScriptExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonScriptExecutor.class);

    private static final AtomicReference<Context> SHARED_CONTEXT = new AtomicReference<>();
    private static final ReentrantLock CONTEXT_LOCK = new ReentrantLock();

    private static volatile Path graalPyExecutable;

    /**
     * Sets the GraalPy executable path to use for Python contexts.
     * This should be called once at application startup from the Configuration class.
     * Changes take effect on the next PythonScriptExecutor creation.
     *
     * @param path the path to the GraalPy executable, or null to use bundled GraalPy
     */
    public static void setGraalPyExecutable(Path path) {
        graalPyExecutable = path;
        if (path != null) {
            LOGGER.info("GraalPy executable configured: {}", path);
        }
    }

    /**
     * Returns the configured GraalPy executable path.
     *
     * @return the path, or empty if using bundled GraalPy
     */
    public static Optional<Path> getGraalPyExecutable() {
        return Optional.ofNullable(graalPyExecutable);
    }

    private final Context context;
    private final PythonImageMathBridge bridge;

    /**
     * Creates a new Python script executor.
     *
     * @param evaluator   the ImageMath evaluator
     * @param contextMap  the evaluation context map
     * @param broadcaster the broadcaster for progress events
     */
    public PythonScriptExecutor(AbstractImageExpressionEvaluator evaluator,
                                Map<Class<?>, Object> contextMap,
                                Broadcaster broadcaster) {
        this.context = createContext();
        this.bridge = new PythonImageMathBridge(evaluator, contextMap, broadcaster);
    }

    private Context createContext() {
        var context = SHARED_CONTEXT.get();
        if (context != null) {
            return context;
        }
        synchronized (SHARED_CONTEXT) {
            context = SHARED_CONTEXT.get();
            if (context != null) {
                return context;
            }
            var builder = Context.newBuilder("python")
                    .allowExperimentalOptions(false)
                    .allowAllAccess(false)
                    .allowHostAccess(HostAccess.ALL)
                    .allowCreateThread(true)
                    .allowNativeAccess(true)
                    .allowPolyglotAccess(PolyglotAccess.ALL)
                    .allowIO(IOAccess.ALL)
                    .option("python.PosixModuleBackend", "java")
                    .option("python.DontWriteBytecodeFlag", "true")
                    .option("python.ForceImportSite", "true")
                    .option("python.CheckHashPycsMode", "never")
                    .option("python.WarnExperimentalFeatures", "false")
                    .out(new LoggingOutputStream(line -> LOGGER.info("[Python] {}", line)))
                    .err(new LoggingOutputStream(line -> LOGGER.warn("[Python] {}", line)));

            // Use custom GraalPy executable if configured
            if (graalPyExecutable != null) {
                LOGGER.info("Using GraalPy executable: {}", graalPyExecutable);
                builder.option("python.Executable", graalPyExecutable.toString());
            }
            context = builder.build();
            SHARED_CONTEXT.set(context);
        }
        return context;
    }

    /**
     * Gets or creates a PythonScriptExecutor from the context map.
     * This enables context reuse across multiple python() calls for better performance.
     *
     * @param contextMap  the evaluation context map
     * @param evaluator   the ImageMath evaluator
     * @param broadcaster the broadcaster for progress events
     * @return the cached or newly created executor
     */
    public static PythonScriptExecutor getOrCreate(Map<Class<?>, Object> contextMap,
                                                   AbstractImageExpressionEvaluator evaluator,
                                                   Broadcaster broadcaster) {
        return (PythonScriptExecutor) contextMap.computeIfAbsent(
                PythonScriptExecutor.class,
                k -> new PythonScriptExecutor(evaluator, contextMap, broadcaster));
    }

    /**
     * Registers the synthetic 'jsolex' Python module so that Python scripts
     * can use standard import syntax: 'import jsolex' or 'from jsolex import ...'.
     */
    private void registerJsolexModule() {
        CONTEXT_LOCK.lock();
        try {
            // Inject the bridge as a global first
            context.getBindings("python").putMember("_java_bridge", bridge);

            // Create the jsolex module and register it in sys.modules
            context.eval("python", """
                    import sys
                    from types import ModuleType
                    
                    # Create a Pythonic variable accessor
                    class VariableAccessor:
                        def __init__(self, bridge):
                            object.__setattr__(self, '_bridge', bridge)
                    
                        def __getattr__(self, name):
                            return self._bridge.getVariable(name)
                    
                        def __setattr__(self, name, value):
                            if name == '_bridge':
                                object.__setattr__(self, name, value)
                            else:
                                self._bridge.setVariable(name, value)
                    
                    # Create a Pythonic function accessor (tries builtin first, then user functions)
                    class FunctionAccessor:
                        def __init__(self, bridge):
                            self._bridge = bridge
                    
                        def __getattr__(self, name):
                            def func_wrapper(*args, **kwargs):
                                # callAny tries builtin first, falls back to user function
                                if args:
                                    return self._bridge.callAnyWithPositionalArgs(name, args, kwargs)
                                return self._bridge.callAny(name, kwargs)
                            return func_wrapper
                    
                    # Create jsolex module
                    _jsolex_module = ModuleType('jsolex')
                    _jsolex_module.__dict__['_bridge'] = _java_bridge
                    
                    # Export bridge methods as module-level functions
                    _jsolex_module.load = _java_bridge.load
                    _jsolex_module.save = _java_bridge.save
                    
                    def _emit(img, title, name=None, category=None, description=None):
                        # Emit an image to the JSol'Ex UI during script execution
                        _java_bridge.emit(img, title, name, category, description)
                    _jsolex_module.emit = _emit
                    
                    _jsolex_module.call = _java_bridge.call
                    _jsolex_module.callUserFunction = _java_bridge.callUserFunction
                    _jsolex_module.getVariable = _java_bridge.getVariable
                    _jsolex_module.setVariable = _java_bridge.setVariable
                    _jsolex_module.getProcessParams = _java_bridge.getProcessParams
                    _jsolex_module.getData = _java_bridge.getData
                    _jsolex_module.createMono = _java_bridge.createMono
                    
                    # Arrow methods (low-level, for advanced users)
                    _jsolex_module.arrowExport = _java_bridge.arrowExport
                    _jsolex_module.arrowImportMono = _java_bridge.arrowImportMono
                    _jsolex_module.arrowImportRGB = _java_bridge.arrowImportRGB
                    _jsolex_module.arrowAllocate = _java_bridge.arrowAllocate
                    
                    # High-level NumPy integration (uses Arrow if available, falls back to slow path)
                    def _toNumpy(img):
                        # Convert image to NumPy array. Uses Arrow if pyarrow is installed (fast), otherwise falls back to getData (slow).
                        try:
                            import numpy as np
                        except ImportError:
                            raise ImportError("NumPy is required for toNumpy(). Install with: pip install numpy")
                    
                        try:
                            import pyarrow as pa
                            # Fast path: use Arrow
                            info = _java_bridge.arrowExport(img)
                            array_addr, schema_addr, w, h, channels = info[0], info[1], int(info[2]), int(info[3]), int(info[4])
                            arrow_array = pa.Array._import_from_c(array_addr, schema_addr)
                            if channels == 1:
                                return arrow_array.to_numpy().reshape((h, w))
                            else:
                                return arrow_array.to_numpy().reshape((h, w, channels))
                        except ImportError:
                            # Slow path: use getData
                            data = _java_bridge.getData(img)
                            return np.array(data)
                    
                    def _fromNumpy(data):
                        # Create image from NumPy array. Uses Arrow if pyarrow is installed (fast), otherwise falls back to createMono (slow).
                        try:
                            import numpy as np
                        except ImportError:
                            raise ImportError("NumPy is required for fromNumpy(). Install with: pip install numpy")
                    
                        if not isinstance(data, np.ndarray):
                            raise TypeError("Expected numpy.ndarray, got " + type(data).__name__)
                    
                        shape = data.shape
                        if len(shape) == 2:
                            h, w = shape
                            channels = 1
                        elif len(shape) == 3 and shape[2] == 3:
                            h, w, channels = shape
                        else:
                            raise ValueError("Expected shape (h, w) for mono or (h, w, 3) for RGB, got " + str(shape))
                    
                        try:
                            import pyarrow as pa
                            # Fast path: use Arrow
                            flat = data.astype(np.float32).flatten()
                            arrow_array = pa.array(flat, type=pa.float32())
                            addrs = _java_bridge.arrowAllocate()
                            arrow_array._export_to_c(addrs[0], addrs[1])
                            if channels == 1:
                                return _java_bridge.arrowImportMono(w, h, addrs[0], addrs[1])
                            else:
                                return _java_bridge.arrowImportRGB(w, h, addrs[0], addrs[1])
                        except ImportError:
                            # Slow path: use createMono (RGB not supported in slow path)
                            if channels != 1:
                                raise ImportError("RGB images require pyarrow. Install with: pip install pyarrow")
                            return _java_bridge.createMono(w, h, data.tolist())
                    
                    _jsolex_module.toNumpy = _toNumpy
                    _jsolex_module.fromNumpy = _fromNumpy
                    
                    # Pythonic variable access: jsolex.vars.foo instead of jsolex.getVariable("foo")
                    _jsolex_module.vars = VariableAccessor(_java_bridge)
                    
                    # Pythonic function access: jsolex.funcs.FUNC(...)
                    # Tries builtin functions first, falls back to user-defined functions
                    _jsolex_module.funcs = FunctionAccessor(_java_bridge)
                    
                    # Register in sys.modules so 'import jsolex' works
                    sys.modules['jsolex'] = _jsolex_module
                    
                    # Also make it available as a global for inline scripts
                    jsolex = _jsolex_module
                    """);
        } finally {
            CONTEXT_LOCK.unlock();
        }
    }

    /**
     * Executes inline Python code and returns the result.
     *
     * @param script    the Python code to execute
     * @param variables variables to inject into the Python namespace
     * @return the value of the 'result' variable after execution, or null
     */
    public Object executeInline(String script, Map<String, Object> variables) {
        CONTEXT_LOCK.lock();
        try {
            registerJsolexModule();
            injectVariables(variables);
            LOGGER.debug("Executing inline Python script: {}", script.length() > 100 ? script.substring(0, 100) + "..." : script);
            var evalResult = context.eval("python", script);
            return extractResult(evalResult);
        } catch (PolyglotException e) {
            LOGGER.warn("PolyglotException caught: isGuestException={}, message={}", e.isGuestException(), e.getMessage());
            var message = buildErrorMessage(e);
            LOGGER.error("Error executing Python script: {}", message, e);
            throw new IllegalStateException(message, e);
        } catch (Exception e) {
            LOGGER.warn("Unexpected exception type caught: {}", e.getClass().getName());
            LOGGER.error("Unexpected error executing Python script: {}", e.getMessage(), e);
            throw new IllegalStateException("Python script error: " + e.getMessage(), e);
        } finally {
            CONTEXT_LOCK.unlock();
        }
    }

    /**
     * Executes a Python file and returns the result.
     *
     * @param filePath  the path to the Python file
     * @param variables variables to inject into the Python namespace
     * @return the value of the 'result' variable after execution, or null
     */
    public Object executeFile(String filePath, Map<String, Object> variables) {
        CONTEXT_LOCK.lock();
        try {
            registerJsolexModule();
            injectVariables(variables);
            var path = Path.of(filePath);
            LOGGER.debug("Executing Python file: {}", filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Python file not found: " + filePath);
            }
            var source = Source.newBuilder("python", path.toFile())
                    .name(path.getFileName().toString())
                    .build();
            var evalResult = context.eval(source);
            return extractResult(evalResult);
        } catch (PolyglotException e) {
            LOGGER.warn("PolyglotException caught: isGuestException={}, message={}", e.isGuestException(), e.getMessage());
            var message = buildErrorMessage(e);
            LOGGER.error("Error executing Python file: {}", message, e);
            throw new IllegalStateException(message, e);
        } catch (IOException e) {
            LOGGER.error("Error reading Python file: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to read Python file: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.warn("Unexpected exception type caught: {}", e.getClass().getName());
            LOGGER.error("Unexpected error executing Python file: {}", e.getMessage(), e);
            throw new IllegalStateException("Python file error: " + e.getMessage(), e);
        } finally {
            CONTEXT_LOCK.unlock();
        }
    }

    private void injectVariables(Map<String, Object> variables) {
        var bindings = context.getBindings("python");
        for (var entry : variables.entrySet()) {
            bindings.putMember(entry.getKey(), convertToPython(entry.getValue()));
        }
        bindings.removeMember("result");
    }

    private Object extractResult(Value evalResult) {
        var bindings = context.getBindings("python");

        // Get the 'result' variable if it was set
        var resultValue = bindings.getMember("result");
        if (resultValue != null && !resultValue.isNull()) {
            return convertFromPython(resultValue);
        }

        // If no explicit result variable, check if the script returned a value
        // But ignore None and module objects (the eval result might be the last expression)
        if (evalResult != null && !evalResult.isNull()) {
            // If it's a primitive value that can be meaningfully returned, do so
            if (evalResult.isNumber() || evalResult.isString() || evalResult.isBoolean()) {
                return convertFromPython(evalResult);
            }
            // For host objects like ImageWrapper that were returned, return them
            if (evalResult.isHostObject()) {
                return evalResult.asHostObject();
            }
        }

        LOGGER.debug("Python script returned null (no result variable set)");
        return null;
    }

    /**
     * Builds a detailed error message from a PolyglotException.
     */
    private String buildErrorMessage(PolyglotException e) {
        try {
            var sb = new StringBuilder();
            if (e.isGuestException()) {
                sb.append("Python error: ");
            }
            var message = e.getMessage();
            sb.append(message != null ? message : "Unknown error");

            // Add source location if available
            try {
                var sourceLocation = e.getSourceLocation();
                if (sourceLocation != null) {
                    sb.append(" at line ").append(sourceLocation.getStartLine());
                    var source = sourceLocation.getSource();
                    if (source != null && source.getName() != null) {
                        sb.append(" in ").append(source.getName());
                    }
                }
            } catch (Exception ignored) {
                // Source location not available
            }

            // Add Python stack trace for guest exceptions
            if (e.isGuestException()) {
                try {
                    var stackTrace = e.getPolyglotStackTrace();
                    if (stackTrace != null) {
                        sb.append("\nTraceback:");
                        for (var frame : stackTrace) {
                            if (frame.isGuestFrame()) {
                                sb.append("\n  ");
                                var loc = frame.getSourceLocation();
                                if (loc != null) {
                                    sb.append("line ").append(loc.getStartLine());
                                    var source = loc.getSource();
                                    if (source != null && source.getName() != null) {
                                        sb.append(" in ").append(source.getName());
                                    }
                                }
                                var rootName = frame.getRootName();
                                if (rootName != null) {
                                    sb.append(": ").append(rootName);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Stack trace not available
                }
            }

            return sb.toString();
        } catch (Exception ex) {
            // Fallback if anything goes wrong
            return "Python error: " + (e.getMessage() != null ? e.getMessage() : ex.getMessage());
        }
    }

    /**
     * Converts a Python Value to a Java object.
     *
     * @param value the Python value
     * @return the Java object
     */
    private Object convertFromPython(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            // Already a Java object (e.g., ImageWrapper passed through)
            return value.asHostObject();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asDouble(); // Always return as Double for consistency
            }
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            return convertPythonList(value);
        }
        if (value.hasHashEntries()) {
            return convertPythonDict(value);
        }
        // For other objects, try to return as-is or as string
        if (value.canExecute()) {
            // It's a function, can't return it meaningfully
            return null;
        }
        // Try to get as host object if possible
        try {
            return value.as(Object.class);
        } catch (Exception e) {
            LOGGER.warn("Could not convert Python value to Java: {}", value);
            return value.toString();
        }
    }

    /**
     * Converts a Python list to a Java List.
     */
    private List<Object> convertPythonList(Value value) {
        var list = new ArrayList<Object>();
        for (int i = 0; i < value.getArraySize(); i++) {
            list.add(convertFromPython(value.getArrayElement(i)));
        }
        return list;
    }

    /**
     * Converts a Python dict to a Java Map.
     */
    private Map<String, Object> convertPythonDict(Value value) {
        var map = new HashMap<String, Object>();
        var iterator = value.getHashKeysIterator();
        while (iterator.hasIteratorNextElement()) {
            var key = iterator.getIteratorNextElement();
            var keyStr = key.isString() ? key.asString() : key.toString();
            var val = value.getHashValue(key);
            map.put(keyStr, convertFromPython(val));
        }
        return map;
    }

    /**
     * Converts a Java value to a Python-compatible type.
     * Primitive types (Number, String, Boolean) are converted to their primitive
     * representations so Python can use native operators on them.
     *
     * @param value the Java value
     * @return the Python-compatible value
     */
    private Object convertToPython(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double d) {
            return d.doubleValue();
        }
        if (value instanceof Float f) {
            return f.doubleValue();
        }
        if (value instanceof Integer i) {
            return i.intValue();
        }
        if (value instanceof Long l) {
            return l.longValue();
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Boolean b) {
            return b.booleanValue();
        }
        if (value instanceof String) {
            return value;
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<>();
            for (var item : list) {
                result.add(convertToPython(item));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            var result = new HashMap<String, Object>();
            for (var entry : map.entrySet()) {
                result.put(entry.getKey().toString(), convertToPython(entry.getValue()));
            }
            return result;
        }
        // For other types like ImageWrapper, pass through as host objects
        return value;
    }

    /**
     * An OutputStream that buffers output and logs complete lines.
     */
    private static class LoggingOutputStream extends OutputStream {
        private final Consumer<String> logger;
        private final StringBuilder buffer = new StringBuilder();

        LoggingOutputStream(Consumer<String> logger) {
            this.logger = logger;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                flushBuffer();
            } else {
                buffer.append((char) b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            var str = new String(b, off, len, StandardCharsets.UTF_8);
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == '\n') {
                    flushBuffer();
                } else {
                    buffer.append(c);
                }
            }
        }

        @Override
        public void flush() {
            if (!buffer.isEmpty()) {
                flushBuffer();
            }
        }

        private void flushBuffer() {
            if (!buffer.isEmpty()) {
                logger.accept(buffer.toString());
                buffer.setLength(0);
            }
        }
    }
}
