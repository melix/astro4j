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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides runtime detection and management of OpenCL support.
 * Unlike {@link me.champeau.a4j.math.VectorApiSupport}, OpenCL acceleration
 * must be explicitly enabled by setting the environment variable
 * {@code OPENCL_ENABLED=true} or system property {@code opencl.enabled=true}.
 */
public class OpenCLSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenCLSupport.class);

    /**
     * Environment variable name for enabling OpenCL.
     */
    public static final String OPENCL_ENV_VAR = "OPENCL_ENABLED";

    /**
     * System property name for enabling OpenCL.
     */
    public static final String OPENCL_SYSTEM_PROPERTY = "opencl.enabled";

    private static final long PROBE_TIMEOUT_SECONDS = 15;
    private static final String CRASH_MARKER_FILENAME = "opencl-init.marker";

    private static final AtomicReference<CompletableFuture<Boolean>> AVAILABILITY_PROBE = new AtomicReference<>();
    private static final AtomicReference<Path> CRASH_MARKER_DIR = new AtomicReference<>();
    private static final AtomicReference<OpenCLContext> SHARED_CONTEXT = new AtomicReference<>();

    private static boolean isExplicitlyEnabled() {
        var propEnabled = System.getProperty(OPENCL_SYSTEM_PROPERTY);
        var enabled = propEnabled != null ? propEnabled : System.getenv(OPENCL_ENV_VAR);
        return Boolean.parseBoolean(enabled);
    }

    private static boolean probeAvailability() {
        var probe = AVAILABILITY_PROBE.get();
        if (probe == null) {
            var newProbe = new CompletableFuture<Boolean>();
            if (AVAILABILITY_PROBE.compareAndSet(null, newProbe)) {
                var thread = new Thread(() -> {
                    try {
                        newProbe.complete(checkOpenCLAvailable());
                    } catch (Throwable t) {
                        newProbe.complete(false);
                    }
                }, "opencl-availability-probe");
                thread.setDaemon(true);
                thread.start();
                probe = newProbe;
            } else {
                probe = AVAILABILITY_PROBE.get();
            }
        }
        try {
            return probe.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.warn("OpenCL initialization did not complete within {} seconds, disabling GPU acceleration", PROBE_TIMEOUT_SECONDS);
            probe.complete(false);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    private static boolean checkOpenCLAvailable() {
        var marker = crashMarkerPath();
        if (marker != null && Files.exists(marker)) {
            LOGGER.warn("OpenCL initialization previously crashed the application, GPU acceleration is disabled. Re-enable GPU acceleration in the settings to retry");
            return false;
        }
        writeCrashMarker(marker);
        try {
            // Try to access a class from the LWJGL OpenCL module
            // This will fail if LWJGL is not on the module path
            // Ensure native libraries are extracted and available
            NativeLibraryLoader.ensureNativesLoaded();
            // Test that we can actually create a context and run a simple kernel
            return testOpenCLExecution();
        } catch (NoClassDefFoundError | UnsatisfiedLinkError e) {
            // LWJGL OpenCL not available
            return false;
        } finally {
            deleteCrashMarker();
        }
    }

    private static Path crashMarkerPath() {
        var dir = CRASH_MARKER_DIR.get();
        return dir == null ? null : dir.resolve(CRASH_MARKER_FILENAME);
    }

    private static void writeCrashMarker(Path marker) {
        if (marker == null) {
            return;
        }
        try {
            Files.createDirectories(marker.getParent());
            Files.createFile(marker);
        } catch (IOException e) {
            LOGGER.warn("Could not create OpenCL crash marker file: {}", e.getMessage());
        }
    }

    /**
     * Sets the directory in which the crash marker file is written. The marker
     * exists only while the OpenCL probe is running, so finding one at startup
     * means the previous probe crashed the JVM and must not be retried.
     * When no directory is set, no crash detection is performed.
     *
     * @param directory the directory for the marker file
     */
    public static void setCrashMarkerDirectory(Path directory) {
        CRASH_MARKER_DIR.set(directory);
    }

    /**
     * Removes the marker left behind by a crashed probe, allowing OpenCL
     * to be probed again on next use.
     */
    public static void deleteCrashMarker() {
        var marker = crashMarkerPath();
        if (marker == null) {
            return;
        }
        try {
            Files.deleteIfExists(marker);
        } catch (IOException e) {
            LOGGER.warn("Could not delete OpenCL crash marker file: {}", e.getMessage());
        }
    }

    private static boolean testOpenCLExecution() {
        OpenCLContext ctx = null;
        try {
            ctx = OpenCLContext.tryCreate();
            if (ctx == null) {
                return false;
            }
            // Run a simple test kernel to verify the full pipeline works
            return ctx.runSelfTest();
        } catch (Exception e) {
            LOGGER.error("Self-test failed", e);
            return false;
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private OpenCLSupport() {
    }

    /**
     * Checks if OpenCL acceleration is enabled.
     * OpenCL must be both available AND explicitly enabled by the user.
     *
     * @return true if OpenCL is available and enabled
     */
    public static boolean isEnabled() {
        return isExplicitlyEnabled() && getContext() != null;
    }

    /**
     * Checks if OpenCL libraries are available (but not necessarily enabled).
     *
     * @return true if LWJGL OpenCL bindings are present
     */
    public static boolean isAvailable() {
        return probeAvailability();
    }

    /**
     * Returns the shared OpenCL context, creating it if necessary.
     * Returns null if OpenCL is not available or context creation fails.
     *
     * @return the shared context or null
     */
    public static OpenCLContext getContext() {
        if (!isExplicitlyEnabled() || !probeAvailability()) {
            return null;
        }
        var ctx = SHARED_CONTEXT.get();
        if (ctx == null) {
            ctx = OpenCLContext.tryCreate();
            if (ctx != null && !SHARED_CONTEXT.compareAndSet(null, ctx)) {
                ctx.close();
                ctx = SHARED_CONTEXT.get();
            } else if (ctx != null) {
                LOGGER.info("Using GPU: {}", ctx.getCapabilities().deviceName());
            }
        }
        return ctx;
    }

    /**
     * Returns the name of the OpenCL device being used, or null if not available.
     *
     * @return the device name or null
     */
    public static String getDeviceName() {
        var ctx = getContext();
        if (ctx != null) {
            return ctx.getCapabilities().deviceName();
        }
        return null;
    }

    /**
     * Releases the shared OpenCL context if it exists.
     * This should be called during application shutdown.
     */
    public static void releaseContext() {
        var ctx = SHARED_CONTEXT.getAndSet(null);
        if (ctx != null) {
            ctx.close();
        }
    }
}
