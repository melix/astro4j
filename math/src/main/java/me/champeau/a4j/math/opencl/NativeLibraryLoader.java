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
package me.champeau.a4j.math.opencl;

import org.lwjgl.opencl.CL;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Extracts LWJGL native libraries from the classpath/module path at runtime.
 */
public class NativeLibraryLoader {

    private static volatile Path nativesDir = null;

    private NativeLibraryLoader() {
    }

    /**
     * Ensures native libraries are extracted and available.
     * Sets the org.lwjgl.librarypath system property if extraction succeeds.
     */
    public static synchronized void ensureNativesLoaded() {
        // Check if librarypath is already set externally and contains GLFW
        String existingPath = System.getProperty("org.lwjgl.librarypath");
        if (existingPath != null && !existingPath.isEmpty()) {
            Path extPath = Path.of(existingPath);
            if (Files.exists(extPath.resolve("libglfw.so")) ||
                Files.exists(extPath.resolve("glfw.dll")) ||
                Files.exists(extPath.resolve("libglfw.dylib"))) {
                return;
            }
        }

        try {
            nativesDir = extractNatives();
            if (nativesDir != null) {
                System.setProperty("org.lwjgl.librarypath", nativesDir.toString());
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private static Path extractNatives() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        record NativeLib(String fileName, String subDir) {}

        NativeLib[] libraries;
        String osDir;
        String archDir = mapArch(arch);

        if (os.contains("linux")) {
            osDir = "linux";
            libraries = new NativeLib[] {
                new NativeLib("liblwjgl.so", ""),
                new NativeLib("liblwjgl_opengl.so", "opengl/"),
                new NativeLib("libglfw.so", "glfw/")
            };
        } else if (os.contains("windows")) {
            osDir = "windows";
            libraries = new NativeLib[] {
                new NativeLib("lwjgl.dll", ""),
                new NativeLib("lwjgl_opengl.dll", "opengl/"),
                new NativeLib("glfw.dll", "glfw/")
            };
        } else if (os.contains("mac")) {
            osDir = "macos";
            libraries = new NativeLib[] {
                new NativeLib("liblwjgl.dylib", ""),
                new NativeLib("liblwjgl_opengl.dylib", "opengl/"),
                new NativeLib("libglfw.dylib", "glfw/")
            };
        } else {
            return null;
        }

        // Extract to temp directory
        Path tempDir = Files.createTempDirectory("lwjgl-natives");
        tempDir.toFile().deleteOnExit();

        var layer = ModuleLayer.boot();

        for (NativeLib lib : libraries) {
            String resourcePath = osDir + "/" + archDir + "/org/lwjgl/" + lib.subDir + lib.fileName;

            InputStream resourceStream = findResource(layer, resourcePath);
            if (resourceStream == null) {
                continue;
            }

            Path libraryPath = tempDir.resolve(lib.fileName);
            libraryPath.toFile().deleteOnExit();

            try (var stream = resourceStream) {
                Files.copy(stream, libraryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return tempDir;
    }

    private static InputStream findResource(ModuleLayer layer, String resourcePath) {
        // Try different native modules
        String[] moduleNames = {
            "org.lwjgl.natives",
            "org.lwjgl.opengl.natives",
            "org.lwjgl.glfw.natives"
        };

        for (String moduleName : moduleNames) {
            var nativesModule = layer.findModule(moduleName);
            if (nativesModule.isPresent()) {
                try {
                    var stream = nativesModule.get().getResourceAsStream(resourcePath);
                    if (stream != null) {
                        return stream;
                    }
                } catch (IOException e) {
                    // Ignore and try other methods
                }
            }
        }

        // Try using LWJGL's classloader
        var stream = CL.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream != null) {
            return stream;
        }

        // Try this class's classloader
        stream = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream != null) {
            return stream;
        }

        // Try thread context classloader
        stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (stream != null) {
            return stream;
        }

        // Try system classloader
        return ClassLoader.getSystemResourceAsStream(resourcePath);
    }

    private static String mapArch(String arch) {
        if (arch.contains("64")) {
            if (arch.contains("aarch") || arch.contains("arm")) {
                return "arm64";
            }
            return "x64";
        }
        return "x86";
    }
}
