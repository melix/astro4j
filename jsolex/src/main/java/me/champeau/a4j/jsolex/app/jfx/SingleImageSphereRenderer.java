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

import me.champeau.a4j.jsolex.app.jfx.gl.GlMatrix;
import me.champeau.a4j.jsolex.app.jfx.gl.ShaderProgram;
import me.champeau.a4j.jsolex.app.jfx.gl.SphereMeshBuilder;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;

/**
 * OpenGL renderer for a single solar image displayed as a 3D hemisphere.
 * Supports both mono and RGB images, preserving the original colors.
 */
public class SingleImageSphereRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleImageSphereRenderer.class);

    private static final int SPHERE_DIVISIONS = 256;
    private static final float BASE_RADIUS = 0.8f;

    private final ImageWrapper originalImage;
    private final boolean isRgb;

    private float cameraDistance = 3.0f;
    private float rotationX = 0;
    private float rotationY = 0;
    private boolean showProminences = false;

    private boolean texturesLoaded = false;
    private int textureId = -1;
    private int prominenceTextureId = -1;
    private int textureWidth;
    private int textureHeight;
    private Ellipse ellipse;
    private int maxTextureSize = -1;

    private ShaderProgram shader;
    private int sphereVao = -1;
    private int sphereVbo = -1;
    private int sphereEbo = -1;
    private int sphereIndexCount;
    private int bandVao = -1;
    private int bandVbo = -1;
    private int bandEbo = -1;
    private int bandIndexCount;

    public SingleImageSphereRenderer(ImageWrapper image) {
        this.originalImage = unwrapImage(image);
        this.isRgb = originalImage instanceof RGBImage;
    }

    private ImageWrapper unwrapImage(ImageWrapper image) {
        if (image instanceof FileBackedImage fileBackedImage) {
            return unwrapImage(fileBackedImage.unwrapToMemory());
        }
        return image;
    }

    public void loadTextures() {
        if (texturesLoaded) {
            return;
        }

        maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        LOGGER.debug("GPU max texture size: {}", maxTextureSize);

        ellipse = originalImage.findMetadata(Ellipse.class).orElse(null);

        createTextures();

        texturesLoaded = true;
    }

    public boolean needsTextureReload() {
        return false;
    }

    public void reloadTextures() {
        // No dynamic texture reloading needed for single image
    }

    private void createTextures() {
        int originalWidth = originalImage.width();
        int originalHeight = originalImage.height();
        textureWidth = originalWidth;
        textureHeight = originalHeight;

        // Downsample if needed
        if (maxTextureSize > 0 && (originalWidth > maxTextureSize || originalHeight > maxTextureSize)) {
            var scale = Math.min((float) maxTextureSize / originalWidth, (float) maxTextureSize / originalHeight);
            textureWidth = (int) (originalWidth * scale);
            textureHeight = (int) (originalHeight * scale);
            LOGGER.debug("Downscaling texture from {}x{} to {}x{}", originalWidth, originalHeight, textureWidth, textureHeight);
        }

        if (isRgb) {
            createRgbTexture((RGBImage) originalImage);
        } else {
            createMonoTexture((ImageWrapper32) originalImage);
        }
    }

    private void createRgbTexture(RGBImage rgb) {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        var r = rgb.r();
        var g = rgb.g();
        var b = rgb.b();
        int srcWidth = rgb.width();
        int srcHeight = rgb.height();

        // Find min/max for normalization
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (int y = 0; y < srcHeight; y++) {
            for (int x = 0; x < srcWidth; x++) {
                min = Math.min(min, Math.min(r[y][x], Math.min(g[y][x], b[y][x])));
                max = Math.max(max, Math.max(r[y][x], Math.max(g[y][x], b[y][x])));
            }
        }
        float range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
        }

        var buffer = BufferUtils.createByteBuffer(textureWidth * textureHeight * 4);
        float scaleX = (float) srcWidth / textureWidth;
        float scaleY = (float) srcHeight / textureHeight;

        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                int srcX = Math.min((int) (x * scaleX), srcWidth - 1);
                int srcY = Math.min((int) (y * scaleY), srcHeight - 1);

                float rv = (r[srcY][srcX] - min) / range;
                float gv = (g[srcY][srcX] - min) / range;
                float bv = (b[srcY][srcX] - min) / range;

                rv = Math.min(1.0f, Math.max(0.0f, rv));
                gv = Math.min(1.0f, Math.max(0.0f, gv));
                bv = Math.min(1.0f, Math.max(0.0f, bv));

                buffer.put((byte) (rv * 255));
                buffer.put((byte) (gv * 255));
                buffer.put((byte) (bv * 255));
                buffer.put((byte) 255);
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        // Create prominence texture from luminance
        createProminenceTextureFromRgb(rgb, min, range);
    }

    private void createMonoTexture(ImageWrapper32 mono) {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        var data = mono.data();
        int srcWidth = mono.width();
        int srcHeight = mono.height();

        // Find min/max for normalization
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for (int y = 0; y < srcHeight; y++) {
            for (int x = 0; x < srcWidth; x++) {
                min = Math.min(min, data[y][x]);
                max = Math.max(max, data[y][x]);
            }
        }
        float range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
        }

        var buffer = BufferUtils.createByteBuffer(textureWidth * textureHeight * 4);
        float scaleX = (float) srcWidth / textureWidth;
        float scaleY = (float) srcHeight / textureHeight;

        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                int srcX = Math.min((int) (x * scaleX), srcWidth - 1);
                int srcY = Math.min((int) (y * scaleY), srcHeight - 1);

                float v = (data[srcY][srcX] - min) / range;
                v = Math.min(1.0f, Math.max(0.0f, v));

                byte gray = (byte) (v * 255);
                buffer.put(gray);
                buffer.put(gray);
                buffer.put(gray);
                buffer.put((byte) 255);
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        // Create prominence texture
        createProminenceTextureFromMono(mono, min, range);
    }

    private void createProminenceTextureFromRgb(RGBImage rgb, float min, float range) {
        prominenceTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, prominenceTextureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        var r = rgb.r();
        var g = rgb.g();
        var b = rgb.b();
        int srcWidth = rgb.width();
        int srcHeight = rgb.height();

        var buffer = BufferUtils.createByteBuffer(textureWidth * textureHeight * 4);
        float scaleX = (float) srcWidth / textureWidth;
        float scaleY = (float) srcHeight / textureHeight;

        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                int srcX = Math.min((int) (x * scaleX), srcWidth - 1);
                int srcY = Math.min((int) (y * scaleY), srcHeight - 1);

                float rv = (r[srcY][srcX] - min) / range;
                float gv = (g[srcY][srcX] - min) / range;
                float bv = (b[srcY][srcX] - min) / range;

                rv = Math.min(1.0f, Math.max(0.0f, rv));
                gv = Math.min(1.0f, Math.max(0.0f, gv));
                bv = Math.min(1.0f, Math.max(0.0f, bv));

                buffer.put((byte) (rv * 255));
                buffer.put((byte) (gv * 255));
                buffer.put((byte) (bv * 255));
                buffer.put((byte) 255);
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    private void createProminenceTextureFromMono(ImageWrapper32 mono, float min, float range) {
        prominenceTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, prominenceTextureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        var data = mono.data();
        int srcWidth = mono.width();
        int srcHeight = mono.height();

        var buffer = BufferUtils.createByteBuffer(textureWidth * textureHeight * 4);
        float scaleX = (float) srcWidth / textureWidth;
        float scaleY = (float) srcHeight / textureHeight;

        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                int srcX = Math.min((int) (x * scaleX), srcWidth - 1);
                int srcY = Math.min((int) (y * scaleY), srcHeight - 1);

                float v = (data[srcY][srcX] - min) / range;
                v = Math.min(1.0f, Math.max(0.0f, v));

                byte gray = (byte) (v * 255);
                buffer.put(gray);
                buffer.put(gray);
                buffer.put(gray);
                buffer.put((byte) 255);
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    public void render(int viewWidth, int viewHeight) {
        if (!texturesLoaded) {
            LOGGER.warn("render() called but textures not loaded");
            return;
        }

        ensureGpuResources();

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        var mvp = computeMvp(viewWidth, viewHeight);
        var diskUv = computeDiskUv();

        shader.use();
        shader.setUniformMatrix4("mvp", mvp);
        shader.setUniform("diskCenter", diskUv[0], diskUv[1]);
        shader.setUniform("diskRadius", diskUv[2], diskUv[3]);
        shader.setUniform("tint", 1f, 1f, 1f, 1f);
        shader.setUniform("tex", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        glDisable(GL_BLEND);
        shader.setUniform("discardOutOfRange", 0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        GL30.glBindVertexArray(sphereVao);
        glDrawElements(GL_TRIANGLES, sphereIndexCount, GL_UNSIGNED_INT, 0L);

        if (showProminences && ellipse != null && prominenceTextureId != -1) {
            glEnable(GL_BLEND);
            glBlendEquation(GL_MAX);
            shader.setUniform("discardOutOfRange", 1);
            glBindTexture(GL_TEXTURE_2D, prominenceTextureId);
            GL30.glBindVertexArray(bandVao);
            glDrawElements(GL_TRIANGLES, bandIndexCount, GL_UNSIGNED_INT, 0L);
            glBlendEquation(GL_FUNC_ADD);
            glDisable(GL_BLEND);
        }

        GL30.glBindVertexArray(0);
        shader.unbind();

        glDisable(GL_DEPTH_TEST);
    }

    private void ensureGpuResources() {
        if (shader != null) {
            return;
        }
        try {
            shader = new ShaderProgram();
            shader.compile(
                    ShaderProgram.loadShaderSource("/me/champeau/a4j/jsolex/app/shaders/textured_sphere.vert"),
                    ShaderProgram.loadShaderSource("/me/champeau/a4j/jsolex/app/shaders/textured_sphere.frag"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile sphere shader", e);
        }

        var sphereMesh = SphereMeshBuilder.buildHemisphere(SPHERE_DIVISIONS);
        var sphereBuffers = uploadMesh(sphereMesh);
        sphereVao = sphereBuffers[0];
        sphereVbo = sphereBuffers[1];
        sphereEbo = sphereBuffers[2];
        sphereIndexCount = sphereMesh.indexCount();

        var bandMesh = SphereMeshBuilder.buildProminenceBand(SPHERE_DIVISIONS * 2, 8, 1.25f, 0.01f);
        var bandBuffers = uploadMesh(bandMesh);
        bandVao = bandBuffers[0];
        bandVbo = bandBuffers[1];
        bandEbo = bandBuffers[2];
        bandIndexCount = bandMesh.indexCount();
    }

    private int[] uploadMesh(SphereMeshBuilder.Mesh mesh) {
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        var posBuf = BufferUtils.createFloatBuffer(mesh.positions().length);
        posBuf.put(mesh.positions()).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, posBuf, GL15.GL_STATIC_DRAW);

        int positionLoc = GL20.glGetAttribLocation(shader.getProgramId(), "position");
        GL20.glEnableVertexAttribArray(positionLoc);
        GL20.glVertexAttribPointer(positionLoc, 3, GL_FLOAT, false, 0, 0L);

        int ebo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        var idxBuf = BufferUtils.createIntBuffer(mesh.indices().length);
        idxBuf.put(mesh.indices()).flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, idxBuf, GL15.GL_STATIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        return new int[]{vao, vbo, ebo};
    }

    private float[] computeMvp(int viewWidth, int viewHeight) {
        var aspect = (float) viewWidth / viewHeight;
        var fov = 45.0f;
        var near = 0.1f;
        var far = 100.0f;
        var top = (float) (near * Math.tan(Math.toRadians(fov / 2)));
        var right = top * aspect;
        var projection = GlMatrix.frustum(-right, right, -top, top, near, far);

        var translate = GlMatrix.translation(0f, 0f, -cameraDistance);
        var rotX = GlMatrix.rotationX(rotationX);
        var rotY = GlMatrix.rotationY(rotationY);
        var scale = GlMatrix.scale(BASE_RADIUS);
        var modelview = GlMatrix.multiply(GlMatrix.multiply(GlMatrix.multiply(translate, rotX), rotY), scale);
        return GlMatrix.multiply(projection, modelview);
    }

    private float[] computeDiskUv() {
        if (ellipse != null) {
            var center = ellipse.center();
            var semiAxis = ellipse.semiAxis();
            return new float[]{
                    (float) (center.a() / textureWidth),
                    (float) (center.b() / textureHeight),
                    (float) (semiAxis.a() / textureWidth),
                    (float) (semiAxis.b() / textureHeight)
            };
        }
        return new float[]{0.5f, 0.5f, 0.5f, 0.5f};
    }

    public void setCameraDistance(float distance) {
        this.cameraDistance = Math.max(1.5f, Math.min(10.0f, distance));
    }

    public float getCameraDistance() {
        return cameraDistance;
    }

    public void setRotation(float x, float y) {
        this.rotationX = x;
        this.rotationY = y;
    }

    public float getRotationX() {
        return rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public void setShowProminences(boolean show) {
        this.showProminences = show;
    }

    public boolean isShowProminences() {
        return showProminences;
    }

    public void dispose() {
        if (textureId != -1) {
            glDeleteTextures(textureId);
            textureId = -1;
        }
        if (prominenceTextureId != -1) {
            glDeleteTextures(prominenceTextureId);
            prominenceTextureId = -1;
        }
        if (sphereVao != -1) {
            GL30.glDeleteVertexArrays(sphereVao);
            sphereVao = -1;
        }
        if (sphereVbo != -1) {
            GL15.glDeleteBuffers(sphereVbo);
            sphereVbo = -1;
        }
        if (sphereEbo != -1) {
            GL15.glDeleteBuffers(sphereEbo);
            sphereEbo = -1;
        }
        if (bandVao != -1) {
            GL30.glDeleteVertexArrays(bandVao);
            bandVao = -1;
        }
        if (bandVbo != -1) {
            GL15.glDeleteBuffers(bandVbo);
            bandVbo = -1;
        }
        if (bandEbo != -1) {
            GL15.glDeleteBuffers(bandEbo);
            bandEbo = -1;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        texturesLoaded = false;
    }
}
