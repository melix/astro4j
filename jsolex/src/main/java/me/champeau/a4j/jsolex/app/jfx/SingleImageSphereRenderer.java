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

import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.math.regression.Ellipse;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
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

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        setupProjection(viewWidth, viewHeight);
        setupModelView();

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_TEXTURE_2D);
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        glDisable(GL_BLEND);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glBindTexture(GL_TEXTURE_2D, textureId);
        renderHemisphere(BASE_RADIUS);

        if (showProminences && ellipse != null && prominenceTextureId != -1) {
            glEnable(GL_BLEND);
            glBlendEquation(GL_MAX);
            renderProminenceBand(BASE_RADIUS);
            glBlendEquation(GL_FUNC_ADD);
            glDisable(GL_BLEND);
        }

        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
    }

    private void setupProjection(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        var aspect = (float) width / height;
        var fov = 45.0f;
        var near = 0.1f;
        var far = 100.0f;

        var top = (float) (near * Math.tan(Math.toRadians(fov / 2)));
        var bottom = -top;
        var right = top * aspect;
        var left = -right;

        glFrustum(left, right, bottom, top, near, far);
    }

    private void setupModelView() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glTranslatef(0, 0, -cameraDistance);
        glRotatef(rotationX, 1, 0, 0);
        glRotatef(rotationY, 0, 1, 0);
    }

    private void renderHemisphere(float radius) {
        float diskCenterU = 0.5f;
        float diskCenterV = 0.5f;
        float diskRadiusU = 0.5f;
        float diskRadiusV = 0.5f;

        if (ellipse != null) {
            var center = ellipse.center();
            var semiAxis = ellipse.semiAxis();
            diskCenterU = (float) (center.a() / textureWidth);
            diskCenterV = (float) (center.b() / textureHeight);
            diskRadiusU = (float) (semiAxis.a() / textureWidth);
            diskRadiusV = (float) (semiAxis.b() / textureHeight);
        }

        int divisions = SPHERE_DIVISIONS;

        glBegin(GL_TRIANGLES);

        for (int i = 0; i < divisions; i++) {
            for (int j = 0; j < divisions; j++) {
                float nx1 = -1.0f + 2.0f * i / divisions;
                float ny1 = -1.0f + 2.0f * j / divisions;
                float nx2 = -1.0f + 2.0f * (i + 1) / divisions;
                float ny2 = -1.0f + 2.0f * (j + 1) / divisions;

                float r1sq = nx1 * nx1 + ny1 * ny1;
                float r2sq = nx2 * nx2 + ny1 * ny1;
                float r3sq = nx2 * nx2 + ny2 * ny2;
                float r4sq = nx1 * nx1 + ny2 * ny2;

                if (r1sq > 1.0f && r2sq > 1.0f && r3sq > 1.0f && r4sq > 1.0f) {
                    continue;
                }

                float z1, z2, z3, z4;
                float px1 = nx1, py1 = ny1, px2 = nx2, py2 = ny1, px3 = nx2, py3 = ny2, px4 = nx1, py4 = ny2;

                if (r1sq >= 1.0f) {
                    float scale = 1.0f / (float) Math.sqrt(r1sq);
                    px1 = nx1 * scale;
                    py1 = ny1 * scale;
                    z1 = 0;
                } else {
                    z1 = (float) Math.sqrt(1.0f - r1sq);
                }

                if (r2sq >= 1.0f) {
                    float scale = 1.0f / (float) Math.sqrt(r2sq);
                    px2 = nx2 * scale;
                    py2 = ny1 * scale;
                    z2 = 0;
                } else {
                    z2 = (float) Math.sqrt(1.0f - r2sq);
                }

                if (r3sq >= 1.0f) {
                    float scale = 1.0f / (float) Math.sqrt(r3sq);
                    px3 = nx2 * scale;
                    py3 = ny2 * scale;
                    z3 = 0;
                } else {
                    z3 = (float) Math.sqrt(1.0f - r3sq);
                }

                if (r4sq >= 1.0f) {
                    float scale = 1.0f / (float) Math.sqrt(r4sq);
                    px4 = nx1 * scale;
                    py4 = ny2 * scale;
                    z4 = 0;
                } else {
                    z4 = (float) Math.sqrt(1.0f - r4sq);
                }

                float u1 = diskCenterU + px1 * diskRadiusU;
                float v1 = diskCenterV - py1 * diskRadiusV;
                float u2 = diskCenterU + px2 * diskRadiusU;
                float v2 = diskCenterV - py2 * diskRadiusV;
                float u3 = diskCenterU + px3 * diskRadiusU;
                float v3 = diskCenterV - py3 * diskRadiusV;
                float u4 = diskCenterU + px4 * diskRadiusU;
                float v4 = diskCenterV - py4 * diskRadiusV;

                glTexCoord2f(u1, v1);
                glVertex3f(px1 * radius, py1 * radius, z1 * radius);
                glTexCoord2f(u2, v2);
                glVertex3f(px2 * radius, py2 * radius, z2 * radius);
                glTexCoord2f(u3, v3);
                glVertex3f(px3 * radius, py3 * radius, z3 * radius);

                glTexCoord2f(u1, v1);
                glVertex3f(px1 * radius, py1 * radius, z1 * radius);
                glTexCoord2f(u3, v3);
                glVertex3f(px3 * radius, py3 * radius, z3 * radius);
                glTexCoord2f(u4, v4);
                glVertex3f(px4 * radius, py4 * radius, z4 * radius);
            }
        }

        glEnd();
    }

    private void renderProminenceBand(float radius) {
        if (ellipse == null || prominenceTextureId == -1) {
            return;
        }

        glBindTexture(GL_TEXTURE_2D, prominenceTextureId);

        var center = ellipse.center();
        var semiAxis = ellipse.semiAxis();
        float diskCenterU = (float) (center.a() / textureWidth);
        float diskCenterV = (float) (center.b() / textureHeight);
        float diskRadiusU = (float) (semiAxis.a() / textureWidth);
        float diskRadiusV = (float) (semiAxis.b() / textureHeight);

        int angularDivisions = SPHERE_DIVISIONS * 2;
        int radialSteps = 8;
        float maxImageExtent = 1.25f;
        float zOffset = 0.01f * radius;

        glBegin(GL_TRIANGLES);

        for (int i = 0; i < angularDivisions; i++) {
            float angle1 = (float) (2.0 * Math.PI * i / angularDivisions);
            float angle2 = (float) (2.0 * Math.PI * (i + 1) / angularDivisions);

            float cosA1 = (float) Math.cos(angle1);
            float sinA1 = (float) Math.sin(angle1);
            float cosA2 = (float) Math.cos(angle2);
            float sinA2 = (float) Math.sin(angle2);

            for (int j = 0; j < radialSteps; j++) {
                float t1 = (float) j / radialSteps;
                float t2 = (float) (j + 1) / radialSteps;

                float imgR1 = 1.0f + t1 * (maxImageExtent - 1.0f);
                float imgR2 = 1.0f + t2 * (maxImageExtent - 1.0f);

                float u1 = diskCenterU + cosA1 * imgR1 * diskRadiusU;
                float v1 = diskCenterV - sinA1 * imgR1 * diskRadiusV;
                float u2 = diskCenterU + cosA2 * imgR1 * diskRadiusU;
                float v2 = diskCenterV - sinA2 * imgR1 * diskRadiusV;
                float u3 = diskCenterU + cosA2 * imgR2 * diskRadiusU;
                float v3 = diskCenterV - sinA2 * imgR2 * diskRadiusV;
                float u4 = diskCenterU + cosA1 * imgR2 * diskRadiusU;
                float v4 = diskCenterV - sinA1 * imgR2 * diskRadiusV;

                if (u1 < 0 || u1 > 1 || v1 < 0 || v1 > 1 ||
                    u2 < 0 || u2 > 1 || v2 < 0 || v2 > 1 ||
                    u3 < 0 || u3 > 1 || v3 < 0 || v3 > 1 ||
                    u4 < 0 || u4 > 1 || v4 < 0 || v4 > 1) {
                    continue;
                }

                float r1 = radius * imgR1;
                float r2 = radius * imgR2;

                float x1 = cosA1 * r1, y1 = sinA1 * r1;
                float x2 = cosA2 * r1, y2 = sinA2 * r1;
                float x3 = cosA2 * r2, y3 = sinA2 * r2;
                float x4 = cosA1 * r2, y4 = sinA1 * r2;

                glTexCoord2f(u1, v1);
                glVertex3f(x1, y1, zOffset);
                glTexCoord2f(u2, v2);
                glVertex3f(x2, y2, zOffset);
                glTexCoord2f(u3, v3);
                glVertex3f(x3, y3, zOffset);

                glTexCoord2f(u1, v1);
                glVertex3f(x1, y1, zOffset);
                glTexCoord2f(u3, v3);
                glVertex3f(x3, y3, zOffset);
                glTexCoord2f(u4, v4);
                glVertex3f(x4, y4, zOffset);
            }
        }

        glEnd();

        glBindTexture(GL_TEXTURE_2D, textureId);
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
        texturesLoaded = false;
    }
}
