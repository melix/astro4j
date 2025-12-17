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

import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL15.GL_STREAM_READ;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;

/**
 * A JavaFX ImageView that displays OpenGL rendered content.
 * Uses LWJGL for OpenGL rendering with FBO and PBO for efficient pixel transfer.
 */
public class OpenGLImageView extends ImageView {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGLImageView.class);

    private int width;
    private int height;
    private final AtomicReference<Consumer<OpenGLImageView>> renderCallbackRef;
    private final Runnable initCallback;
    private final AtomicReference<Runnable> cleanupCallbackRef;

    private WritableImage writableImage;
    private PixelBuffer<IntBuffer> pixelBuffer;
    private IntBuffer imageBuffer;

    private volatile int pendingWidth;
    private volatile int pendingHeight;
    private final AtomicBoolean resizePending = new AtomicBoolean(false);

    // OpenGL resources
    private long glfwWindow;
    private int framebuffer;
    private int colorTexture;
    private int depthRenderbuffer;
    private int pbo;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean renderRequested = false;
    private volatile boolean disposed = false;
    private final ReentrantLock renderLock = new ReentrantLock();
    private final Condition renderCondition = renderLock.newCondition();
    private final Condition renderCompleteCondition = renderLock.newCondition();
    private volatile boolean renderComplete = false;

    private final AtomicReference<Throwable> initializationError = new AtomicReference<>();
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private Consumer<Throwable> errorCallback;
    private final AtomicBoolean shadersSupported = new AtomicBoolean(false);

    private volatile boolean switchRequested = false;
    private Runnable switchInitCallback;
    private Runnable switchOldCleanupCallback;

    private Thread renderThread;

    /**
     * Creates a new OpenGL ImageView with the specified dimensions and callbacks.
     *
     * @param width the initial width of the render target
     * @param height the initial height of the render target
     * @param initCallback callback invoked once on the render thread after OpenGL initialization
     * @param renderCallback callback invoked on the render thread for each frame
     * @param cleanupCallback callback invoked on the render thread when disposing
     */
    public OpenGLImageView(int width, int height, Runnable initCallback, Consumer<OpenGLImageView> renderCallback, Runnable cleanupCallback) {
        this.width = width;
        this.height = height;
        this.initCallback = initCallback;
        this.renderCallbackRef = new AtomicReference<>(renderCallback);
        this.cleanupCallbackRef = new AtomicReference<>(cleanupCallback);

        // Create JavaFX image buffer
        imageBuffer = MemoryUtil.memAllocInt(width * height);
        pixelBuffer = new PixelBuffer<>(width, height, imageBuffer, PixelFormat.getIntArgbPreInstance());
        writableImage = new WritableImage(pixelBuffer);
        setImage(writableImage);

        // Start render thread
        startRenderThread();
    }

    private void startRenderThread() {
        renderThread = new Thread(() -> {
            try {
                initOpenGL();

                // Call init callback on render thread (for texture loading etc.)
                if (initCallback != null) {
                    try {
                        initCallback.run();
                    } catch (Exception e) {
                        LOGGER.error("Init callback error", e);
                    }
                }

                initialized.set(true);
                initLatch.countDown();
                signalRender(); // Request initial render

                while (!disposed) {
                    renderLock.lock();
                    try {
                        while (!renderRequested && !disposed) {
                            renderCondition.await();
                        }
                        renderRequested = false;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } finally {
                        renderLock.unlock();
                    }
                    if (!disposed) {
                        renderFrame();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("OpenGL initialization/render error", e);
                initializationError.set(e);
                initLatch.countDown();
                notifyError(e);
            } finally {
                var cleanup = cleanupCallbackRef.get();
                if (cleanup != null) {
                    try {
                        cleanup.run();
                    } catch (Exception e) {
                        LOGGER.error("Cleanup callback error", e);
                    }
                }
                cleanupOpenGL();
            }
        }, "OpenGL-Render-Thread");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void notifyError(Throwable error) {
        if (errorCallback != null) {
            Platform.runLater(() -> errorCallback.accept(error));
        }
    }

    private void initOpenGL() {
        // Create an offscreen OpenGL context using LWJGL
        if (org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.MACOSX) {
            // On macOS, use glfw_async build which dispatches Cocoa calls to the main thread
            // This allows GLFW to work alongside JavaFX without requiring -XstartOnFirstThread
            Configuration.GLFW_LIBRARY_NAME.set("glfw_async");
            Configuration.GLFW_CHECK_THREAD0.set(false);
        }
        GLFW.glfwInit();
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);

        glfwWindow = GLFW.glfwCreateWindow(1, 1, "", 0, 0);
        if (glfwWindow == 0) {
            throw new RuntimeException("Failed to create GLFW window for OpenGL context");
        }
        glfwMakeContextCurrent(glfwWindow);
        GL.createCapabilities();

        // Create framebuffer
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);

        // Create color texture
        colorTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);

        // Create depth renderbuffer
        depthRenderbuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbuffer);

        // Check framebuffer status
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer incomplete: " + status);
        }

        // Create PBO for async pixel transfer
        pbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        GL15.glBufferData(GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL_STREAM_READ);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);

        glViewport(0, 0, width, height);
        glEnable(GL_DEPTH_TEST);

        // Check shader support
        shadersSupported.set(checkShaderSupport());
        LOGGER.debug("Shader support: {}", shadersSupported.get());
    }

    private boolean checkShaderSupport() {
        try {
            // Test vertex shader
            int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            if (vertexShader == 0) {
                LOGGER.debug("Failed to create vertex shader");
                return false;
            }

            String testVertexSource = "#version 150 core\nin vec2 pos;\nout vec2 vTexCoord;\nvoid main() { gl_Position = vec4(pos, 0.0, 1.0); vTexCoord = pos; }";
            GL20.glShaderSource(vertexShader, testVertexSource);
            GL20.glCompileShader(vertexShader);

            int vertexStatus = GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS);
            if (vertexStatus == 0) {
                LOGGER.debug("Vertex shader compilation failed: {}", GL20.glGetShaderInfoLog(vertexShader));
                GL20.glDeleteShader(vertexShader);
                return false;
            }

            // Test fragment shader with sampler3D (critical for volume rendering)
            int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            if (fragmentShader == 0) {
                LOGGER.debug("Failed to create fragment shader");
                GL20.glDeleteShader(vertexShader);
                return false;
            }

            String testFragmentSource = """
                #version 150 core
                in vec2 vTexCoord;
                out vec4 fragColor;
                uniform sampler3D volumeTexture;
                void main() {
                    vec3 texCoord = vec3(vTexCoord, 0.5);
                    float value = texture(volumeTexture, texCoord).r;
                    fragColor = vec4(value, value, value, 1.0);
                }
                """;
            GL20.glShaderSource(fragmentShader, testFragmentSource);
            GL20.glCompileShader(fragmentShader);

            int fragmentStatus = GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS);
            if (fragmentStatus == 0) {
                LOGGER.debug("Fragment shader with sampler3D compilation failed: {}", GL20.glGetShaderInfoLog(fragmentShader));
                GL20.glDeleteShader(vertexShader);
                GL20.glDeleteShader(fragmentShader);
                return false;
            }

            // Test linking the program
            int program = GL20.glCreateProgram();
            if (program == 0) {
                LOGGER.debug("Failed to create shader program");
                GL20.glDeleteShader(vertexShader);
                GL20.glDeleteShader(fragmentShader);
                return false;
            }

            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);

            int linkStatus = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
            if (linkStatus == 0) {
                LOGGER.debug("Shader program linking failed: {}", GL20.glGetProgramInfoLog(program));
            }

            // Cleanup
            GL20.glDetachShader(program, vertexShader);
            GL20.glDetachShader(program, fragmentShader);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            GL20.glDeleteProgram(program);

            if (linkStatus == 0) {
                return false;
            }

            // Test 3D texture support
            int texture3D = GL11.glGenTextures();
            if (texture3D == 0) {
                LOGGER.debug("Failed to create 3D texture");
                return false;
            }

            GL11.glBindTexture(GL30.GL_TEXTURE_3D, texture3D);
            int error = GL11.glGetError();
            GL11.glDeleteTextures(texture3D);

            if (error != GL11.GL_NO_ERROR) {
                LOGGER.debug("3D texture binding failed with error: {}", error);
                return false;
            }

            LOGGER.debug("Shader support check passed: vertex, fragment with sampler3D, and 3D textures supported");
            return true;
        } catch (Exception e) {
            LOGGER.debug("Shader support check failed", e);
            return false;
        }
    }

    private void resizeFramebuffer(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) {
            return;
        }

        this.width = newWidth;
        this.height = newHeight;

        // Resize color texture
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        // Resize depth renderbuffer
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, width, height);

        // Resize PBO
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        GL15.glBufferData(GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL_STREAM_READ);
        GL15.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);

        // Recreate JavaFX image buffer
        if (imageBuffer != null) {
            MemoryUtil.memFree(imageBuffer);
        }
        imageBuffer = MemoryUtil.memAllocInt(width * height);
        pixelBuffer = new PixelBuffer<>(width, height, imageBuffer, PixelFormat.getIntArgbPreInstance());
        writableImage = new WritableImage(pixelBuffer);

        Platform.runLater(() -> {
            if (!disposed) {
                setImage(writableImage);
            }
        });
    }

    private void renderFrame() {
        // Handle renderer switch if requested
        if (switchRequested) {
            switchRequested = false;
            if (switchOldCleanupCallback != null) {
                try {
                    switchOldCleanupCallback.run();
                } catch (Exception e) {
                    LOGGER.error("Old cleanup callback error during switch", e);
                }
            }
            if (switchInitCallback != null) {
                try {
                    switchInitCallback.run();
                } catch (Exception e) {
                    LOGGER.error("New init callback error during switch", e);
                }
            }
            switchInitCallback = null;
            switchOldCleanupCallback = null;
        }

        // Check if resize is pending
        if (resizePending.compareAndSet(true, false)) {
            resizeFramebuffer(pendingWidth, pendingHeight);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, width, height);

        // Clear buffers with a visible color to verify rendering works
        glClearColor(0.1f, 0.1f, 0.2f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Call user's render callback
        var renderCallback = renderCallbackRef.get();
        try {
            if (renderCallback != null) {
                renderCallback.accept(this);
            }
        } catch (Exception e) {
            LOGGER.error("Render callback error", e);
        }

        // Ensure all OpenGL commands are complete
        GL11.glFinish();

        // Read pixels directly (simpler than PBO for debugging)
        ByteBuffer directBuffer = MemoryUtil.memAlloc(width * height * 4);
        glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_BYTE, directBuffer);

        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            LOGGER.error("OpenGL error after glReadPixels: {}", error);
        }

        // Copy to image buffer (flip Y axis)
        for (int y = 0; y < height; y++) {
            int srcRow = (height - 1 - y) * width * 4;
            int dstRow = y * width;
            for (int x = 0; x < width; x++) {
                int srcIdx = srcRow + x * 4;
                byte b = directBuffer.get(srcIdx);
                byte g = directBuffer.get(srcIdx + 1);
                byte r = directBuffer.get(srcIdx + 2);
                byte a = directBuffer.get(srcIdx + 3);
                // ARGB format for JavaFX
                imageBuffer.put(dstRow + x, ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
            }
        }

        MemoryUtil.memFree(directBuffer);

        // Signal that render is complete
        renderLock.lock();
        try {
            renderComplete = true;
            renderCompleteCondition.signalAll();
        } finally {
            renderLock.unlock();
        }

        // Update JavaFX image on FX thread
        Platform.runLater(() -> {
            if (!disposed) {
                pixelBuffer.updateBuffer(pb -> null);
            }
        });
    }

    /**
     * Request a new frame to be rendered.
     */
    public void requestRender() {
        if (initialized.get() && !disposed) {
            signalRender();
        }
    }

    private void signalRender() {
        renderLock.lock();
        try {
            renderRequested = true;
            renderCondition.signal();
        } finally {
            renderLock.unlock();
        }
    }

    /**
     * Request a resize of the render target.
     *
     * @param newWidth the new width
     * @param newHeight the new height
     */
    public void requestResize(int newWidth, int newHeight) {
        if (newWidth > 0 && newHeight > 0) {
            pendingWidth = newWidth;
            pendingHeight = newHeight;
            resizePending.set(true);
            requestRender();
        }
    }

    /**
     * Sets a callback to be invoked on the JavaFX thread when an initialization error occurs.
     *
     * @param callback the error callback
     */
    public void setOnError(Consumer<Throwable> callback) {
        this.errorCallback = callback;
        // If an error already occurred, notify immediately
        var error = initializationError.get();
        if (error != null) {
            Platform.runLater(() -> callback.accept(error));
        }
    }

    /**
     * Get the render width.
     *
     * @return the current render width in pixels
     */
    public int getRenderWidth() {
        return width;
    }

    /**
     * Get the render height.
     *
     * @return the current render height in pixels
     */
    public int getRenderHeight() {
        return height;
    }

    /**
     * Returns whether shaders are supported on this system.
     * Must be called after initialization is complete.
     *
     * @return true if shaders are supported, false otherwise
     */
    public boolean areShadersSupported() {
        return shadersSupported.get();
    }

    /**
     * Waits for the OpenGL context to be fully initialized.
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if initialized, false if timeout or error
     */
    public boolean waitForInitialization(long timeoutMs) {
        try {
            return initLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Renders a frame synchronously and returns the pixels as a BufferedImage.
     * This method blocks until the render is complete.
     *
     * @return the rendered image, or null if rendering failed or timed out
     */
    public BufferedImage renderAndCapture() {
        // Wait for initialization if not yet done
        if (!initialized.get()) {
            if (!waitForInitialization(5000)) {
                LOGGER.warn("renderAndCapture: initialization timeout");
                return null;
            }
        }

        if (disposed) {
            LOGGER.warn("renderAndCapture: view is disposed");
            return null;
        }

        // Request render and wait for completion
        renderLock.lock();
        try {
            renderComplete = false;
            renderRequested = true;
            renderCondition.signal();

            // Wait for render to complete
            while (!renderComplete && !disposed) {
                if (!renderCompleteCondition.await(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("renderAndCapture: render timeout");
                    return null;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            renderLock.unlock();
        }

        if (disposed) {
            return null;
        }

        // Capture the image buffer - safe to read now since render is complete
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var argb = imageBuffer.get(y * width + x);
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    /**
     * Switches to a new renderer by providing new callbacks.
     * The old cleanup callback will be called, then the new init callback will be called.
     * This method is non-blocking - the switch happens asynchronously on the render thread.
     *
     * @param newInitCallback the initialization callback for the new renderer
     * @param newRenderCallback the render callback for the new renderer
     * @param newCleanupCallback the cleanup callback for the new renderer
     */
    public void switchRenderer(Runnable newInitCallback, Consumer<OpenGLImageView> newRenderCallback, Runnable newCleanupCallback) {
        if (!initialized.get() || disposed) {
            return;
        }

        var oldCleanupCallback = this.cleanupCallbackRef.get();
        this.cleanupCallbackRef.set(newCleanupCallback);
        this.renderCallbackRef.set(newRenderCallback);

        renderLock.lock();
        try {
            switchRequested = true;
            switchInitCallback = newInitCallback;
            switchOldCleanupCallback = oldCleanupCallback;
            renderCondition.signal();
        } finally {
            renderLock.unlock();
        }

        requestRender();
    }

    /**
     * Dispose OpenGL resources.
     * This method is non-blocking - cleanup happens asynchronously on the render thread.
     */
    public void dispose() {
        disposed = true;
        // Signal the render thread to wake up and exit
        signalRender();
        // Don't block waiting for render thread - let it clean up asynchronously
        // The render thread is a daemon thread so it will be terminated when the JVM exits
    }

    private void cleanupOpenGL() {
        if (pbo != 0) {
            GL15.glDeleteBuffers(pbo);
        }
        if (depthRenderbuffer != 0) {
            GL30.glDeleteRenderbuffers(depthRenderbuffer);
        }
        if (colorTexture != 0) {
            GL11.glDeleteTextures(colorTexture);
        }
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
        }
        if (glfwWindow != 0) {
            GLFW.glfwDestroyWindow(glfwWindow);
            glfwWindow = 0;
        }
        // Free the image buffer
        if (imageBuffer != null) {
            MemoryUtil.memFree(imageBuffer);
            imageBuffer = null;
        }
    }
}
