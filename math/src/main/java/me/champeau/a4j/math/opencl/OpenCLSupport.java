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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides runtime detection and management of OpenCL support.
 * Unlike {@link me.champeau.a4j.math.VectorApiSupport}, OpenCL acceleration
 * must be explicitly enabled by setting the environment variable
 * {@code OPENCL_ENABLED=true} or system property {@code opencl.enabled=true}.
 */
public class OpenCLSupport {
    /**
     * Environment variable name for enabling OpenCL.
     */
    public static final String OPENCL_ENV_VAR = "OPENCL_ENABLED";

    /**
     * System property name for enabling OpenCL.
     */
    public static final String OPENCL_SYSTEM_PROPERTY = "opencl.enabled";

    private static final boolean OPENCL_AVAILABLE;
    private static final AtomicReference<OpenCLContext> SHARED_CONTEXT = new AtomicReference<>();

    static {
        OPENCL_AVAILABLE = checkOpenCLAvailable();
    }

    private static boolean checkOpenCLAvailable() {
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
            System.err.println("[OpenCL] Self-test failed: " + e.getMessage());
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
        if (!OPENCL_AVAILABLE) {
            return false;
        }
        var envEnabled = System.getenv(OPENCL_ENV_VAR);
        var propEnabled = System.getProperty(OPENCL_SYSTEM_PROPERTY);
        var enabled = propEnabled != null ? propEnabled : envEnabled;
        if (enabled == null || !Boolean.parseBoolean(enabled)) {
            return false;
        }
        return getContext() != null;
    }

    /**
     * Checks if OpenCL libraries are available (but not necessarily enabled).
     *
     * @return true if LWJGL OpenCL bindings are present
     */
    public static boolean isAvailable() {
        return OPENCL_AVAILABLE;
    }

    /**
     * Returns the shared OpenCL context, creating it if necessary.
     * Returns null if OpenCL is not available or context creation fails.
     *
     * @return the shared context or null
     */
    public static OpenCLContext getContext() {
        if (!OPENCL_AVAILABLE) {
            return null;
        }
        var ctx = SHARED_CONTEXT.get();
        if (ctx == null) {
            ctx = OpenCLContext.tryCreate();
            if (ctx != null && !SHARED_CONTEXT.compareAndSet(null, ctx)) {
                ctx.close();
                ctx = SHARED_CONTEXT.get();
            } else if (ctx != null) {
                System.out.println("[OpenCL] Using GPU: " + ctx.getCapabilities().deviceName());
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
