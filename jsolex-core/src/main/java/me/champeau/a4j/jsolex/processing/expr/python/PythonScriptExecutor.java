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

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Manages GraalPy context lifecycle and Python script execution.
 * Instances are cached in the evaluation context for reuse across multiple python() calls.
 */
public class PythonScriptExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonScriptExecutor.class);

    private static final AtomicReference<Context> SHARED_CONTEXT = new AtomicReference<>();
    private static final ReentrantLock CONTEXT_LOCK = new ReentrantLock();

    // GraalPy cannot run on virtual threads, so we use a cached thread pool for script execution
    private static final ExecutorService PYTHON_EXECUTOR = Executors.newCachedThreadPool(r -> {
        var thread = new Thread(r, "python-executor");
        thread.setDaemon(true);
        return thread;
    });

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

    private final PythonImageMathBridge bridge;
    private final Broadcaster broadcaster;

    /**
     * Creates a new Python script executor.
     *
     * @param evaluator             the ImageMath evaluator
     * @param contextMap            the evaluation context map
     * @param broadcaster           the broadcaster for progress events
     * @param allowVariableCreation whether Python scripts can create new variables
     */
    public PythonScriptExecutor(AbstractImageExpressionEvaluator evaluator,
                                Map<Class<?>, Object> contextMap,
                                Broadcaster broadcaster,
                                boolean allowVariableCreation) {
        this.bridge = new PythonImageMathBridge(evaluator, contextMap, broadcaster, allowVariableCreation);
        this.broadcaster = broadcaster;
    }

    /**
     * Gets or creates the shared GraalPy context.
     * Must be called from a platform thread (via executeOnPlatformThread).
     */
    private Context getOrCreateContext() {
        var context = SHARED_CONTEXT.get();
        if (context != null) {
            return context;
        }
        synchronized (SHARED_CONTEXT) {
            context = SHARED_CONTEXT.get();
            if (context != null) {
                return context;
            }
            LOGGER.info("Initializing GraalPy context...");

            // Broadcast progress during initialization
            ProgressOperation initProgress = null;
            if (broadcaster != null) {
                initProgress = ProgressOperation.root(message("python.context.initializing"), _ -> {});
                broadcaster.broadcast(initProgress);
            }

            try {
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
                LOGGER.info("GraalPy context initialized");
            } finally {
                if (initProgress != null) {
                    broadcaster.broadcast(initProgress.complete());
                }
            }
        }
        return context;
    }

    /**
     * Gets or creates a PythonScriptExecutor from the context map.
     * This enables context reuse across multiple python() calls for better performance.
     * Used for embedded Python (via python() function) - variable creation is not allowed.
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
                _ -> new PythonScriptExecutor(evaluator, contextMap, broadcaster, false));
    }

    /**
     * Registers the synthetic 'jsolex' Python module so that Python scripts
     * can use standard import syntax: 'import jsolex' or 'from jsolex import ...'.
     * Must be called from a platform thread with CONTEXT_LOCK held.
     */
    private void registerJsolexModule(Context context) {
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

                    # Outputs class for single() function - allows outputs.myvar = value
                    class Outputs:
                        def __init__(self, data=None):
                            object.__setattr__(self, '_data', data if data is not None else {})

                        def __getattr__(self, name):
                            return self._data.get(name)

                        def __setattr__(self, name, value):
                            if name == '_data':
                                object.__setattr__(self, name, value)
                            else:
                                self._data[name] = value

                        def _get_data(self):
                            return self._data

                        def __repr__(self):
                            return f"Outputs({self._data})"

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

                    # Metadata copying - allows generated images to inherit metadata from source images
                    _jsolex_module.copyMetadataFrom = _java_bridge.copyMetadataFrom

                    # Spectral profile extraction
                    _jsolex_module.extractProfile = _java_bridge.extractProfile
                    _jsolex_module.debugExtractProfile = lambda img, x, y: dict(_java_bridge.debugExtractProfile(img, x, y))
                    _jsolex_module.getPixelShiftRange = lambda: dict(_java_bridge.getPixelShiftRange()) if _java_bridge.getPixelShiftRange() else None

                    # Polynomial coefficients for spectral line position: y = a*x³ + b*x² + c*x + d
                    _jsolex_module.getPolynomialCoefficients = _java_bridge.getPolynomialCoefficients

                    # Raw frame access - returns ImageWrapper32, use toNumpy() if numpy conversion needed
                    _jsolex_module.readFrame = _java_bridge.readFrame
                    _jsolex_module.getSourceInfo = lambda: dict(_java_bridge.getSourceInfo()) if _java_bridge.getSourceInfo() else None
                    _jsolex_module.getDispersion = lambda: dict(_java_bridge.getDispersion()) if _java_bridge.getDispersion() else None
                    _jsolex_module.getWavelength = lambda: dict(_java_bridge.getWavelength()) if _java_bridge.getWavelength() else None

                    # Coordinate conversion - image to/from frame coordinates
                    # imageToFrameCoords returns: {frameNumber, xInFrame, yInFrame, pixelShift, available}
                    _jsolex_module.imageToFrameCoords = lambda img, x, y, pixelShift=None: dict(_java_bridge.imageToFrameCoords(img, x, y, pixelShift))
                    _jsolex_module.frameToImageCoords = lambda img, frameNumber, xInFrame: dict(_java_bridge.frameToImageCoords(img, frameNumber, xInFrame))

                    # Heliographic coordinate conversion
                    _jsolex_module.getSolarParameters = lambda: dict(_java_bridge.getSolarParameters()) if _java_bridge.getSolarParameters() else None
                    _jsolex_module.getEllipseParams = lambda img: dict(_java_bridge.getEllipseParams(img)) if _java_bridge.getEllipseParams(img) else None
                    _jsolex_module.heliographicToImage = lambda img, lat, lon: dict(_java_bridge.heliographicToImage(img, lat, lon))
                    _jsolex_module.imageToHeliographic = lambda img, x, y: dict(_java_bridge.imageToHeliographic(img, x, y))

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
    }

    /**
     * Executes a task under the context lock, handling reentrant calls.
     * If the current thread already holds the lock (nested Python call), executes directly.
     * Otherwise, submits to the executor and acquires the lock there.
     * GraalPy cannot run on virtual threads, so we use platform threads.
     */
    private <T> T runUnderLock(Callable<T> task) {
        // If we already hold the lock (reentrant call from nested python()), execute directly
        if (CONTEXT_LOCK.isHeldByCurrentThread()) {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        // Otherwise, submit to executor and acquire lock there
        try {
            return PYTHON_EXECUTOR.submit(() -> {
                CONTEXT_LOCK.lock();
                try {
                    return task.call();
                } finally {
                    CONTEXT_LOCK.unlock();
                }
            }).get();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python execution interrupted", e);
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
        return runUnderLock(() -> doExecuteInline(script, variables));
    }

    /**
     * Resets the Python context by clearing cached user modules and global variables.
     * This prevents variable pollution between script executions.
     *
     * @param context the Python context
     * @param scriptDir if provided, also clears cached modules from this directory to force reimport
     */
    private void resetContext(Context context, String scriptDir) {
        var bindings = context.getBindings("python");
        bindings.putMember("_reset_script_dir", scriptDir);
        context.eval("python", """
                import sys
                import os
                # Clear cached user modules from script directory (forces reimport)
                if _reset_script_dir:
                    # Normalize the script directory path for comparison (handles Windows paths)
                    _norm_script_dir = os.path.normcase(os.path.normpath(_reset_script_dir))
                    _to_remove = []
                    for name, module in list(sys.modules.items()):
                        if module is None:
                            continue
                        if not hasattr(module, '__file__') or module.__file__ is None:
                            continue
                        # Normalize module path for comparison
                        _norm_module_path = os.path.normcase(os.path.normpath(module.__file__))
                        if _norm_module_path.startswith(_norm_script_dir + os.sep) or _norm_module_path.startswith(_norm_script_dir + '/'):
                            _to_remove.append(name)
                    for name in _to_remove:
                        del sys.modules[name]
                # Clear user-defined global variables
                _keep = {'__builtins__', '__name__', '__doc__', '__package__', '__loader__', '__spec__',
                         'sys', 'os', 'jsolex', '_jsolex_module', '_java_bridge', 'VariableAccessor', 'FunctionAccessor', 'Outputs'}
                _to_delete = [name for name in list(globals().keys()) if name not in _keep and not name.startswith('_')]
                for name in _to_delete:
                    try:
                        del globals()[name]
                    except:
                        pass
                del _reset_script_dir
                """);
    }

    private Object doExecuteInline(String script, Map<String, Object> variables) {
        try {
            var context = getOrCreateContext();
            registerJsolexModule(context);
            resetContext(context, null);
            injectVariables(context, variables);
            // Create outputs object for implicit single mode
            context.eval("python", "outputs = Outputs()");
            LOGGER.debug("Executing inline Python script: {}", script.length() > 100 ? script.substring(0, 100) + "..." : script);
            var evalResult = context.eval("python", script);
            return extractResult(context, evalResult);
        } catch (PolyglotException e) {
            LOGGER.warn("PolyglotException caught: isGuestException={}, message={}", e.isGuestException(), e.getMessage());
            var message = buildErrorMessage(e);
            LOGGER.error("Error executing Python script: {}", message, e);
            throw new IllegalStateException(message, e);
        } catch (Exception e) {
            LOGGER.warn("Unexpected exception type caught: {}", e.getClass().getName());
            LOGGER.error("Unexpected error executing Python script: {}", e.getMessage(), e);
            throw new IllegalStateException("Python script error: " + e.getMessage(), e);
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
        return runUnderLock(() -> doExecuteFile(filePath, variables));
    }

    private Object doExecuteFile(String filePath, Map<String, Object> variables) {
        try {
            var context = getOrCreateContext();
            registerJsolexModule(context);
            injectVariables(context, variables);
            var path = Path.of(filePath).toAbsolutePath();
            LOGGER.debug("Executing Python file: {}", filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Python file not found: " + filePath);
            }
            // Add script's directory to sys.path so imports work
            var scriptDir = path.getParent().toString();
            var scriptDirEscaped = scriptDir.replace("\\", "\\\\");
            context.eval("python", "import sys; sys.path.insert(0, '" + scriptDirEscaped + "') if '" + scriptDirEscaped + "' not in sys.path else None");

            // Reset context to clear cached modules and global variables (use unescaped path)
            resetContext(context, scriptDir);

            // Create outputs object for implicit single mode
            context.eval("python", "outputs = Outputs()");

            var source = Source.newBuilder("python", path.toFile())
                    .name(path.getFileName().toString())
                    .build();
            var evalResult = context.eval(source);
            return extractResult(context, evalResult);
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
        }
    }

    /**
     * Checks if the Python context has a function with the given name defined.
     *
     * @param functionName the name of the function to check
     * @return true if the function exists and is callable
     */
    public boolean hasFunction(String functionName) {
        return runUnderLock(() -> {
            var context = getOrCreateContext();
            var bindings = context.getBindings("python");
            var member = bindings.getMember(functionName);
            return member != null && !member.isNull() && member.canExecute();
        });
    }

    /**
     * Checks if a Python script contains a function definition with the given name
     * by parsing the AST without executing the script.
     *
     * @param script the Python script source code
     * @param functionName the name of the function to look for
     * @return true if the script defines a function with that name
     */
    public boolean scriptDefinesFunction(String script, String functionName) {
        return runUnderLock(() -> {
            try {
                var context = getOrCreateContext();
                var bindings = context.getBindings("python");
                // Pass script and function name through bindings to avoid escaping issues
                bindings.putMember("_script_source", script);
                bindings.putMember("_func_name", functionName);
                var result = context.eval("python", """
                    import ast
                    _tree = ast.parse(_script_source)
                    _has_func = any(
                        isinstance(node, ast.FunctionDef) and node.name == _func_name
                        for node in ast.walk(_tree)
                    )
                    _has_func
                    """);
                return result.asBoolean();
            } catch (Exception e) {
                LOGGER.debug("Could not parse script AST: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Calls a Python function by name with the given arguments.
     *
     * @param functionName the name of the function to call
     * @param args arguments to pass to the function (will be passed as keyword arguments)
     * @return the return value of the function, converted to Java types
     */
    public Object callFunction(String functionName, Map<String, Object> args) {
        return runUnderLock(() -> doCallFunction(functionName, args));
    }

    private Object doCallFunction(String functionName, Map<String, Object> args) {
        try {
            var context = getOrCreateContext();
            var bindings = context.getBindings("python");
            var func = bindings.getMember(functionName);
            if (func == null || func.isNull() || !func.canExecute()) {
                throw new IllegalStateException("Function not found or not callable: " + functionName);
            }

            // Inject arguments as variables so they're accessible
            for (var entry : args.entrySet()) {
                bindings.putMember(entry.getKey(), convertToPython(entry.getValue()));
            }

            // Build the call expression with keyword arguments
            var callExpr = new StringBuilder(functionName).append("(");
            var first = true;
            for (var key : args.keySet()) {
                if (!first) {
                    callExpr.append(", ");
                }
                callExpr.append(key).append("=").append(key);
                first = false;
            }
            callExpr.append(")");

            LOGGER.debug("Calling Python function: {}", callExpr);
            var result = context.eval("python", callExpr.toString());
            return convertFromPython(result);
        } catch (PolyglotException e) {
            var message = buildErrorMessage(e);
            LOGGER.error("Error calling Python function {}: {}", functionName, message, e);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Calls the single() function with an outputs object.
     * The script can use `outputs.myvar = value` to set outputs.
     *
     * @return a Map of output name to value
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> callSingleFunction() {
        return runUnderLock(() -> {
            try {
                var context = getOrCreateContext();
                var bindings = context.getBindings("python");

                // Create an outputs object and inject it
                context.eval("python", "outputs = Outputs()");

                // Call single()
                var func = bindings.getMember("single");
                if (func == null || func.isNull() || !func.canExecute()) {
                    throw new IllegalStateException("Function not found or not callable: single");
                }

                LOGGER.debug("Calling Python function: single()");
                context.eval("python", "single()");

                // Extract the outputs
                var outputsObj = bindings.getMember("outputs");
                if (outputsObj != null && !outputsObj.isNull()) {
                    var dataValue = context.eval("python", "outputs._get_data()");
                    return (Map<String, Object>) convertFromPython(dataValue);
                }
                return Map.of();
            } catch (PolyglotException e) {
                var message = buildErrorMessage(e);
                LOGGER.error("Error calling Python function single: {}", message, e);
                throw new IllegalStateException(message, e);
            }
        });
    }

    /**
     * Calls the batch() function with collected results.
     * The script receives `results` with list-valued attributes (e.g., results.myvar is a list).
     *
     * @param collectedResults a Map of output name to list of values from all single() calls
     * @return a Map of output name to value
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> callBatchFunction(Map<String, List<Object>> collectedResults) {
        return runUnderLock(() -> {
            try {
                var context = getOrCreateContext();
                var bindings = context.getBindings("python");

                // Create a results object with the collected data
                bindings.putMember("_batch_results_data", convertToPython(collectedResults));
                context.eval("python", "results = Outputs(_batch_results_data)");

                // Also create an outputs object for batch outputs
                context.eval("python", "outputs = Outputs()");

                // Call batch(results)
                var func = bindings.getMember("batch");
                if (func == null || func.isNull() || !func.canExecute()) {
                    throw new IllegalStateException("Function not found or not callable: batch");
                }

                LOGGER.debug("Calling Python function: batch(results)");
                context.eval("python", "batch(results)");

                // Extract the outputs
                var outputsObj = bindings.getMember("outputs");
                if (outputsObj != null && !outputsObj.isNull()) {
                    var dataValue = context.eval("python", "outputs._get_data()");
                    return (Map<String, Object>) convertFromPython(dataValue);
                }
                return Map.of();
            } catch (PolyglotException e) {
                var message = buildErrorMessage(e);
                LOGGER.error("Error calling Python function batch: {}", message, e);
                throw new IllegalStateException(message, e);
            }
        });
    }

    private void injectVariables(Context context, Map<String, Object> variables) {
        var bindings = context.getBindings("python");
        for (var entry : variables.entrySet()) {
            bindings.putMember(entry.getKey(), convertToPython(entry.getValue()));
        }
        bindings.removeMember("result");
    }

    private Object extractResult(Context context, Value evalResult) {
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
     * Extracts outputs set via `outputs.name = value` syntax.
     * Call this after executeInline/executeFile to get outputs for implicit single mode.
     *
     * @return a Map of output name to value, or empty map if no outputs were set
     */
    public Map<String, Object> extractOutputs() {
        return runUnderLock(() -> {
            var context = getOrCreateContext();
            var bindings = context.getBindings("python");
            var result = new LinkedHashMap<String, Object>();

            // Extract outputs from the outputs object
            var outputsObj = bindings.getMember("outputs");
            if (outputsObj != null && !outputsObj.isNull()) {
                try {
                    var dataValue = context.eval("python", "outputs._get_data()");
                    var outputsMap = convertFromPython(dataValue);
                    if (outputsMap instanceof Map<?, ?> map) {
                        for (var entry : map.entrySet()) {
                            result.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not extract outputs: {}", e.getMessage());
                }
            }

            return result;
        });
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
        return convertToPython(value, new IdentityHashMap<>());
    }

    private Object convertToPython(Object value, IdentityHashMap<Object, Object> visited) {
        if (value == null) {
            return null;
        }
        // Avoid infinite recursion on circular references
        if (visited.containsKey(value)) {
            return visited.get(value);
        }
        // Don't try to convert polyglot types - they're already Python-compatible
        if (value instanceof Value || value.getClass().getName().contains("Polyglot")) {
            return value;
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
            visited.put(value, result);
            for (var item : list) {
                result.add(convertToPython(item, visited));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            var result = new HashMap<String, Object>();
            visited.put(value, result);
            for (var entry : map.entrySet()) {
                result.put(entry.getKey().toString(), convertToPython(entry.getValue(), visited));
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
