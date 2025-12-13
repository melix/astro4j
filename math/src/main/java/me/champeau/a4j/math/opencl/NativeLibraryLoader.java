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

    private static final String[] MODULE_NAMES = {
        "org.lwjgl.natives",
        "org.lwjgl.opengl.natives",
        "org.lwjgl.glfw.natives"
    };

    private record NativeLib(String fileName, String subDir) {}

    private static final NativeLib[] LINUX_LIBS = {
        new NativeLib("liblwjgl.so", ""),
        new NativeLib("liblwjgl_opengl.so", "opengl/"),
        new NativeLib("libglfw.so", "glfw/")
    };
    private static final NativeLib[] WINDOWS_LIBS = {
        new NativeLib("lwjgl.dll", ""),
        new NativeLib("lwjgl_opengl.dll", "opengl/"),
        new NativeLib("glfw.dll", "glfw/")
    };
    private static final NativeLib[] MACOS_LIBS = {
        new NativeLib("liblwjgl.dylib", ""),
        new NativeLib("liblwjgl_opengl.dylib", "opengl/"),
        new NativeLib("libglfw.dylib", "glfw/")
    };
    private static final int GLFW_INDEX = 2;

    private static volatile Path nativesDir = null;

    private NativeLibraryLoader() {
    }

    /**
     * Ensures native libraries are extracted and available.
     * Sets the org.lwjgl.librarypath system property if extraction succeeds.
     */
    public static synchronized void ensureNativesLoaded() {
        if (hasNativesConfiguredExternally()) {
            return;
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

    /**
     * Ensures native libraries are extracted to a specific directory.
     * Uses a marker file to track the version and avoid re-extraction.
     *
     * @param targetDir the directory to extract natives to
     * @param version   the application version for cache invalidation
     */
    public static synchronized void ensureNativesLoaded(Path targetDir, String version) {
        if (hasNativesConfiguredExternally()) {
            return;
        }

        try {
            var markerFile = targetDir.resolve(".version");

            // Check if already extracted for this version
            if (Files.exists(markerFile)) {
                var existingVersion = Files.readString(markerFile).trim();
                if (version.equals(existingVersion) && hasRequiredLibraries(targetDir)) {
                    nativesDir = targetDir;
                    System.setProperty("org.lwjgl.librarypath", targetDir.toString());
                    return;
                }
            }

            // Clean up and re-extract
            deleteDirectory(targetDir);
            Files.createDirectories(targetDir);

            extractNativesTo(targetDir);

            // Write marker file
            Files.writeString(markerFile, version);

            nativesDir = targetDir;
            System.setProperty("org.lwjgl.librarypath", targetDir.toString());
        } catch (Exception e) {
            // Fall back to temp directory extraction
            ensureNativesLoaded();
        }
    }

    private static boolean hasNativesConfiguredExternally() {
        var existingPath = System.getProperty("org.lwjgl.librarypath");
        if (existingPath != null && !existingPath.isEmpty()) {
            return hasRequiredLibraries(Path.of(existingPath));
        }
        return false;
    }

    private static boolean hasRequiredLibraries(Path dir) {
        return Files.exists(dir.resolve(LINUX_LIBS[GLFW_INDEX].fileName)) ||
               Files.exists(dir.resolve(WINDOWS_LIBS[GLFW_INDEX].fileName)) ||
               Files.exists(dir.resolve(MACOS_LIBS[GLFW_INDEX].fileName));
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walker = Files.walk(dir)) {
            walker.sorted((a, b) -> -a.compareTo(b))
                  .forEach(path -> {
                      try {
                          Files.deleteIfExists(path);
                      } catch (IOException e) {
                          // Ignore deletion errors
                      }
                  });
        }
    }

    private static Path extractNatives() throws IOException {
        var tempDir = Files.createTempDirectory("lwjgl-natives");
        tempDir.toFile().deleteOnExit();
        extractNativesTo(tempDir, true);
        return tempDir;
    }

    private static void extractNativesTo(Path targetDir) throws IOException {
        extractNativesTo(targetDir, false);
    }

    private static void extractNativesTo(Path targetDir, boolean deleteOnExit) throws IOException {
        var os = System.getProperty("os.name").toLowerCase();
        var arch = System.getProperty("os.arch").toLowerCase();

        NativeLib[] libraries;
        String osDir;
        var archDir = mapArch(arch);

        if (os.contains("linux")) {
            osDir = "linux";
            libraries = LINUX_LIBS;
        } else if (os.contains("windows")) {
            osDir = "windows";
            libraries = WINDOWS_LIBS;
        } else if (os.contains("mac")) {
            osDir = "macos";
            libraries = MACOS_LIBS;
        } else {
            return;
        }

        var layer = ModuleLayer.boot();

        for (var lib : libraries) {
            var resourcePath = osDir + "/" + archDir + "/org/lwjgl/" + lib.subDir + lib.fileName;

            var resourceStream = findResource(layer, resourcePath);
            if (resourceStream == null) {
                continue;
            }

            var libraryPath = targetDir.resolve(lib.fileName);
            if (deleteOnExit) {
                libraryPath.toFile().deleteOnExit();
            }

            try (var stream = resourceStream) {
                Files.copy(stream, libraryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static InputStream findResource(ModuleLayer layer, String resourcePath) {
        for (var moduleName : MODULE_NAMES) {
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
