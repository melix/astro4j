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
package me.champeau.a4j.jsolex.app.jfx;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;

/**
 * Utility class to check OpenGL availability on the system.
 * Should be called early during application startup to determine
 * whether OpenGL-based features (like 3D viewers) can be used.
 */
public final class OpenGLAvailability {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGLAvailability.class);

    private static final AtomicBoolean CHECKED = new AtomicBoolean(false);
    private static final AtomicBoolean AVAILABLE = new AtomicBoolean(false);
    private static volatile String errorMessage = null;

    private OpenGLAvailability() {
    }

    /**
     * Checks OpenGL availability asynchronously.
     * This method should be called early during application startup.
     * The check is performed in a background thread to avoid blocking the UI.
     *
     * @return a CompletableFuture that completes with true if OpenGL is available
     */
    public static CompletableFuture<Boolean> checkAsync() {
        if (CHECKED.get()) {
            return CompletableFuture.completedFuture(AVAILABLE.get());
        }

        return CompletableFuture.supplyAsync(() -> {
            if (CHECKED.compareAndSet(false, true)) {
                performCheck();
            }
            return AVAILABLE.get();
        });
    }

    /**
     * Returns whether OpenGL is available.
     * This method returns immediately with the cached result.
     * If the check has not been performed yet, it returns false.
     *
     * @return true if OpenGL is available and the check has completed
     */
    public static boolean isAvailable() {
        return AVAILABLE.get();
    }

    private static void performCheck() {
        LOGGER.debug("Checking OpenGL availability...");
        long window = 0;
        try {
            if (!GLFW.glfwInit()) {
                errorMessage = "Failed to initialize GLFW";
                LOGGER.warn("OpenGL not available: {}", errorMessage);
                return;
            }

            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);

            window = GLFW.glfwCreateWindow(1, 1, "", 0, 0);
            if (window == 0) {
                errorMessage = "Failed to create GLFW window - OpenGL may not be supported";
                LOGGER.warn("OpenGL not available: {}", errorMessage);
                return;
            }

            glfwMakeContextCurrent(window);
            GL.createCapabilities();

            AVAILABLE.set(true);
            LOGGER.info("OpenGL is available");

        } catch (Exception e) {
            errorMessage = e.getMessage();
            LOGGER.warn("OpenGL not available: {}", errorMessage, e);
        } finally {
            if (window != 0) {
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }
}
