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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.python.PythonScriptExecutor;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the python() function for executing Python scripts.
 */
public class PythonScript extends AbstractFunctionImpl {
    private final AbstractImageExpressionEvaluator evaluator;
    private Path includesDir;

    public PythonScript(AbstractImageExpressionEvaluator evaluator,
                        Map<Class<?>, Object> context,
                        Broadcaster broadcaster) {
        super(context, broadcaster);
        this.evaluator = evaluator;
    }

    /**
     * Sets the includes directory for resolving relative script paths.
     *
     * @param includesDir the includes directory
     */
    public void setIncludesDir(Path includesDir) {
        this.includesDir = includesDir;
    }

    /**
     * Executes inline Python code and returns the result.
     *
     * @param arguments the function arguments
     * @return the script result
     */
    public Object executePython(Map<String, Object> arguments) {
        // Skip Python execution during shift-collecting phase (dummy 0-sized images)
        if (evaluator.isShiftCollecting()) {
            return null;
        }

        var script = stringArg(arguments, "script", null);
        if (script == null) {
            throw new IllegalArgumentException("python() requires a 'script' argument");
        }

        // Get or create cached executor (reused within session for performance)
        var executor = PythonScriptExecutor.getOrCreate(context, evaluator, broadcaster);

        // Merge current ImageMath variables
        var allVars = new HashMap<>(evaluator.getVariables());

        return executor.executeInline(script, allVars);
    }

    /**
     * Executes a Python file and returns the result.
     *
     * @param arguments the function arguments
     * @return the script result
     */
    public Object executePythonFile(Map<String, Object> arguments) {
        // Skip Python execution during shift-collecting phase (dummy 0-sized images)
        if (evaluator.isShiftCollecting()) {
            return null;
        }

        var file = stringArg(arguments, "file", null);
        if (file == null) {
            throw new IllegalArgumentException("python_file() requires a 'file' argument");
        }

        // Resolve script path
        var resolvedPath = resolveScriptPath(file);

        // Get or create cached executor (reused within session for performance)
        var executor = PythonScriptExecutor.getOrCreate(context, evaluator, broadcaster);

        // Merge current ImageMath variables
        var allVars = new HashMap<>(evaluator.getVariables());

        return executor.executeFile(resolvedPath, allVars);
    }

    /**
     * Resolves a script reference to an absolute path.
     */
    private String resolveScriptPath(String script) {
        var path = Path.of(script);

        // If absolute, use it
        if (path.isAbsolute()) {
            return path.toString();
        }

        // Try relative to includes directory
        if (includesDir != null) {
            var resolved = includesDir.resolve(script);
            if (resolved.toFile().exists()) {
                return resolved.toString();
            }
        }

        // Try relative to current working directory
        var cwd = Path.of(System.getProperty("user.dir")).resolve(script);
        if (cwd.toFile().exists()) {
            return cwd.toString();
        }

        // Return as-is (will fail with file not found if it doesn't exist)
        return script;
    }
}
