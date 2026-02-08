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
package me.champeau.astro4j;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A task which parses the built-in functions YAML files and generates
 * Python stub files (.pyi) for IDE autocompletion.
 */
@CacheableTask
public abstract class PythonStubsGenerator extends AbstractBuiltinFunctionGenerator {

    // Python reserved keywords that cannot be used as parameter names
    private static final Set<String> PYTHON_KEYWORDS = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
            "while", "with", "yield"
    );

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        var outputDir = getOutputDirectory().getAsFile().get().toPath();
        var stubsDir = outputDir.resolve("python-stubs");
        Files.createDirectories(stubsDir);
        withModels(models -> {
            try (var writer = new PrintWriter(Files.newBufferedWriter(stubsDir.resolve("jsolex.pyi"), StandardCharsets.UTF_8))) {
                writeStubs(writer, models);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void writeStubs(PrintWriter out, List<BuiltinFunctionModel> models) {
        // Header
        out.println("# JSol'Ex Python Bridge - Type Stubs for IDE Completion");
        out.println("#");
        out.println("# Place this file in your project directory or Python path to enable");
        out.println("# autocompletion and type checking in PyCharm, VS Code, or other IDEs.");
        out.println("#");
        out.println("# Generated for JSol'Ex - https://github.com/melix/astro4j");
        out.println("# This file is auto-generated. Do not edit manually.");
        out.println();
        out.println("from typing import Any, Dict, List, Union, overload");
        out.println();

        // Image types
        out.println("# ============================================================================");
        out.println("# Image Types");
        out.println("# ============================================================================");
        out.println();
        out.println("class ImageWrapper:");
        out.println("    \"\"\"Base class for all image types in JSol'Ex.\"\"\"");
        out.println("    def width(self) -> int:");
        out.println("        \"\"\"Returns the image width in pixels.\"\"\"");
        out.println("        ...");
        out.println();
        out.println("    def height(self) -> int:");
        out.println("        \"\"\"Returns the image height in pixels.\"\"\"");
        out.println("        ...");
        out.println();
        out.println("class ImageWrapper32(ImageWrapper):");
        out.println("    \"\"\"32-bit floating point grayscale image.\"\"\"");
        out.println("    ...");
        out.println();
        out.println("class RGBImage(ImageWrapper):");
        out.println("    \"\"\"RGB color image.\"\"\"");
        out.println("    ...");
        out.println();
        out.println("class ProcessParams:");
        out.println("    \"\"\"Processing parameters for the current session.\"\"\"");
        out.println("    ...");
        out.println();

        // Variable accessor
        out.println("# ============================================================================");
        out.println("# Accessor Classes");
        out.println("# ============================================================================");
        out.println();
        out.println("class VariableAccessor:");
        out.println("    \"\"\"");
        out.println("    Pythonic access to ImageMath variables.");
        out.println();
        out.println("    Usage:");
        out.println("        value = jsolex.vars.my_variable");
        out.println("        jsolex.vars.my_variable = new_value");
        out.println();
        out.println("    Note: Variables must be declared in ImageMath before they can be set from Python.");
        out.println("    \"\"\"");
        out.println("    def __getattr__(self, name: str) -> Any:");
        out.println("        \"\"\"Get a variable value by name.\"\"\"");
        out.println("        ...");
        out.println();
        out.println("    def __setattr__(self, name: str, value: Any) -> None:");
        out.println("        \"\"\"Set a variable value by name. Variable must exist in ImageMath context.\"\"\"");
        out.println("        ...");
        out.println();

        // Function accessor with all functions
        out.println("class FunctionAccessor:");
        out.println("    \"\"\"");
        out.println("    Pythonic access to all ImageMath functions (builtin and user-defined).");
        out.println();
        out.println("    Usage:");
        out.println("        img = jsolex.funcs.IMG(shift=5)");
        out.println("        result = jsolex.funcs.SHARPEN(img, 1.5)");
        out.println("        result = jsolex.funcs.my_user_function(param=value)");
        out.println();
        out.println("    Functions can be called with positional and/or keyword arguments.");
        out.println("    Tries builtin functions first, then falls back to user-defined functions.");
        out.println("    \"\"\"");
        out.println("    def __getattr__(self, name: str) -> Any:");
        out.println("        \"\"\"Get a function by name. Returns a callable.\"\"\"");
        out.println("        ...");
        out.println();

        // Generate function stubs for each builtin function (uppercase)
        for (var model : models) {
            writeFunctionStub(out, model, true);
        }

        // Generate function stubs for each builtin function (lowercase)
        for (var model : models) {
            writeFunctionStub(out, model, false);
        }

        out.println();

        // Module-level variables
        out.println("# ============================================================================");
        out.println("# Module-level Variables");
        out.println("# ============================================================================");
        out.println();
        out.println("vars: VariableAccessor");
        out.println("\"\"\"Pythonic variable access: jsolex.vars.name\"\"\"");
        out.println();
        out.println("funcs: FunctionAccessor");
        out.println("\"\"\"Pythonic function access: jsolex.funcs.FUNC_NAME(...)\"\"\"");
        out.println();

        // Module-level functions
        out.println("# ============================================================================");
        out.println("# Module-level Functions");
        out.println("# ============================================================================");
        out.println();

        // Bridge functions
        writeBridgeFunctions(out);
    }

    private void writeFunctionStub(PrintWriter out, BuiltinFunctionModel model, boolean uppercase) {
        var funcName = uppercase ? model.getName() : model.getName().toLowerCase(Locale.ROOT);
        var args = model.getArguments();

        // Build parameter list
        var params = new StringBuilder();
        params.append("self");
        for (var arg : args) {
            params.append(", ");
            params.append(escapePythonKeyword(arg.getName()));
            params.append(": Any");
            if (arg.isOptional()) {
                var defaultVal = arg.getDefault();
                if (defaultVal != null) {
                    params.append(" = ").append(formatDefault(defaultVal));
                } else {
                    params.append(" = None");
                }
            }
        }

        // Get description
        var desc = model.getDescription();
        var descText = desc != null ? desc.getOrDefault("en", desc.values().iterator().next()) : null;

        out.println("    def " + funcName + "(" + params + ") -> Any:");
        if (descText != null) {
            out.println("        \"\"\"" + escapeDocstring(descText) + "\"\"\"");
        } else {
            out.println("        \"\"\"Call the " + model.getName() + " function.\"\"\"");
        }
        out.println("        ...");
        out.println();
    }

    private void writeBridgeFunctions(PrintWriter out) {
        out.println("def load(path: str) -> ImageWrapper:");
        out.println("    \"\"\"Load an image from a file path.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def save(img: ImageWrapper, path: str) -> None:");
        out.println("    \"\"\"Save an image to a FITS file.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def emit(img: ImageWrapper, title: str, name: str = None, category: str = None, description: str = None) -> None:");
        out.println("    \"\"\"");
        out.println("    Emit an image to the JSol'Ex UI during script execution.");
        out.println("    The image will be displayed in the image viewer as a script-generated image.");
        out.println();
        out.println("    Args:");
        out.println("        img: The image to emit");
        out.println("        title: The display title for the image");
        out.println("        name: The file name (optional, defaults to title)");
        out.println("        category: The category (optional)");
        out.println("        description: The description (optional)");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        out.println("def call(functionName: str, args: Dict[str, Any]) -> Any:");
        out.println("    \"\"\"");
        out.println("    Call any ImageMath builtin function by name with keyword arguments.");
        out.println();
        out.println("    Example:");
        out.println("        img = jsolex.call(\"IMG\", {\"shift\": 5})");
        out.println("        result = jsolex.call(\"SHARPEN\", {\"img\": img, \"amount\": 1.5})");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        out.println("def callUserFunction(name: str, args: Dict[str, Any]) -> Any:");
        out.println("    \"\"\"Call a user-defined ImageMath function.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def getVariable(name: str) -> Any:");
        out.println("    \"\"\"Get a variable from the ImageMath context.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def setVariable(name: str, value: Any) -> None:");
        out.println("    \"\"\"");
        out.println("    Set a variable in the ImageMath context.");
        out.println("    Note: The variable must already exist in ImageMath.");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        out.println("def getProcessParams() -> ProcessParams:");
        out.println("    \"\"\"Get the current processing parameters.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def width(img: ImageWrapper) -> int:");
        out.println("    \"\"\"Get the width of an image in pixels.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def height(img: ImageWrapper) -> int:");
        out.println("    \"\"\"Get the height of an image in pixels.\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def getPixel(img: ImageWrapper, x: int, y: int) -> float:");
        out.println("    \"\"\"Get a pixel value from an image (mono images only).\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def setPixel(img: ImageWrapper, x: int, y: int, value: float) -> None:");
        out.println("    \"\"\"Set a pixel value in an image (mono images only).\"\"\"");
        out.println("    ...");
        out.println();

        out.println("def getData(img: ImageWrapper) -> List[List[float]]:");
        out.println("    \"\"\"");
        out.println("    Get the raw pixel data from an image as a 2D array.");
        out.println("    For use with NumPy: np.array(jsolex.getData(img))");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        out.println("def createMono(width: int, height: int, data: Union[List[List[float]], Any]) -> ImageWrapper32:");
        out.println("    \"\"\"");
        out.println("    Create a new grayscale image from pixel data.");
        out.println("    Example: jsolex.createMono(w, h, enhanced.tolist())");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        // NumPy integration (high-level API)
        out.println("# ============================================================================");
        out.println("# NumPy Integration");
        out.println("# ============================================================================");
        out.println("# High-level functions for NumPy interop. These automatically use Apache Arrow");
        out.println("# for fast zero-copy transfer if pyarrow is installed, otherwise fall back to");
        out.println("# slower methods. Requires numpy; pyarrow is optional but recommended.");
        out.println();

        out.println("def toNumpy(img: ImageWrapper) -> Any:");
        out.println("    \"\"\"");
        out.println("    Convert image to NumPy array.");
        out.println();
        out.println("    Returns ndarray with shape (h, w) for mono or (h, w, 3) for RGB.");
        out.println("    Uses Arrow for fast transfer if pyarrow is installed.");
        out.println();
        out.println("    Requires: numpy");
        out.println("    Recommended: pyarrow (for performance)");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        out.println("def fromNumpy(data: Any) -> ImageWrapper:");
        out.println("    \"\"\"");
        out.println("    Create image from NumPy array.");
        out.println();
        out.println("    Accepts ndarray with shape (h, w) for mono or (h, w, 3) for RGB.");
        out.println("    Uses Arrow for fast transfer if pyarrow is installed.");
        out.println();
        out.println("    Requires: numpy");
        out.println("    Recommended: pyarrow (for performance)");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        // Low-level Arrow methods
        out.println("# ============================================================================");
        out.println("# Low-level Arrow Methods (Advanced)");
        out.println("# ============================================================================");
        out.println("# Direct Arrow C Data Interface access for advanced users.");
        out.println("# Most users should use toNumpy/fromNumpy instead.");
        out.println();

        out.println("def arrowExport(img: ImageWrapper) -> List[int]:");
        out.println("    \"\"\"");
        out.println("    Export image to Arrow. Returns [array_addr, schema_addr, width, height, channels].");
        out.println("    \"\"\"");
        out.println("    ...");
        out.println();

        out.println("def arrowImportMono(width: int, height: int, array_address: int, schema_address: int) -> ImageWrapper32:");
        out.println("    \"\"\"Import mono image from Arrow array.\"\"\"\n    ...");
        out.println();

        out.println("def arrowImportRGB(width: int, height: int, array_address: int, schema_address: int) -> RGBImage:");
        out.println("    \"\"\"Import RGB image from Arrow array (interleaved format).\"\"\"\n    ...");
        out.println();

        out.println("def arrowAllocate() -> List[int]:");
        out.println("    \"\"\"Allocate Arrow structures for export. Returns [array_addr, schema_addr].\"\"\"\n    ...");
    }

    private String formatDefault(String defaultValue) {
        if (defaultValue == null) {
            return "None";
        }
        // Try to detect type
        if ("true".equalsIgnoreCase(defaultValue)) {
            return "True";
        }
        if ("false".equalsIgnoreCase(defaultValue)) {
            return "False";
        }
        if ("null".equalsIgnoreCase(defaultValue)) {
            return "None";
        }
        // If it looks like a number, return as-is
        try {
            Double.parseDouble(defaultValue);
            return defaultValue;
        } catch (NumberFormatException e) {
            // It's a string
            return "\"" + defaultValue + "\"";
        }
    }

    private String escapeDocstring(String text) {
        return text.replace("\"", "'").replace("\n", " ");
    }

    private String escapePythonKeyword(String name) {
        if (PYTHON_KEYWORDS.contains(name)) {
            return name + "_";
        }
        return name;
    }
}
