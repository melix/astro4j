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
class NativeLibraryLoader {
    private static volatile boolean initialized = false;
    private static volatile Path nativesDir = null;

    private NativeLibraryLoader() {
    }

    /**
     * Ensures native libraries are extracted and available.
     * Sets the org.lwjgl.librarypath system property if extraction succeeds.
     */
    static synchronized void ensureNativesLoaded() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Check if librarypath is already set
        if (System.getProperty("org.lwjgl.librarypath") != null) {
            return;
        }

        try {
            nativesDir = extractNatives();
            if (nativesDir != null) {
                System.setProperty("org.lwjgl.librarypath", nativesDir.toString());
            }
        } catch (Exception e) {
            // Failed to extract natives, LWJGL will try its default loading mechanism
        }
    }

    private static Path extractNatives() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String libraryName;
        String resourcePath;

        if (os.contains("linux")) {
            libraryName = "liblwjgl.so";
            resourcePath = "linux/" + mapArch(arch) + "/org/lwjgl/" + libraryName;
        } else if (os.contains("windows")) {
            libraryName = "lwjgl.dll";
            resourcePath = "windows/" + mapArch(arch) + "/org/lwjgl/" + libraryName;
        } else if (os.contains("mac")) {
            libraryName = "liblwjgl.dylib";
            resourcePath = "macos/" + mapArch(arch) + "/org/lwjgl/" + libraryName;
        } else {
            return null;
        }

        // Try to find the native library from the org.lwjgl.natives module
        InputStream resourceStream = null;

        // First try to find the natives module in the module layer
        var layer = ModuleLayer.boot();
        var nativesModule = layer.findModule("org.lwjgl.natives");
        if (nativesModule.isPresent()) {
            try {
                resourceStream = nativesModule.get().getResourceAsStream(resourcePath);
            } catch (IOException e) {
                // Ignore and try other methods
            }
        }

        if (resourceStream == null) {
            // Try using LWJGL's classloader
            resourceStream = CL.class.getClassLoader()
                    .getResourceAsStream(resourcePath);
        }

        if (resourceStream == null) {
            // Try this class's classloader as fallback
            resourceStream = NativeLibraryLoader.class.getClassLoader()
                    .getResourceAsStream(resourcePath);
        }

        if (resourceStream == null) {
            return null;
        }

        // Extract to temp directory
        Path tempDir = Files.createTempDirectory("lwjgl-natives");
        tempDir.toFile().deleteOnExit();

        Path libraryPath = tempDir.resolve(libraryName);
        libraryPath.toFile().deleteOnExit();

        try (var stream = resourceStream) {
            Files.copy(stream, libraryPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return tempDir;
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
