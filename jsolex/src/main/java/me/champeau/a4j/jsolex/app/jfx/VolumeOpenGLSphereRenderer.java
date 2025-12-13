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

import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.gl.ShaderProgram;
import me.champeau.a4j.jsolex.app.jfx.gl.Volume3DTexture;
import me.champeau.a4j.jsolex.processing.spectrum.SphericalTomographyData;
import me.champeau.a4j.math.regression.Ellipse;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL renderer for spherical tomography using shader-based volume ray marching.
 * Provides smoother volumetric appearance compared to the shell-based renderer.
 */
public class VolumeOpenGLSphereRenderer implements SphereRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VolumeOpenGLSphereRenderer.class);

    private static final float BASE_RADIUS = 0.8f;
    private static final String VERTEX_SHADER_PATH = "/me/champeau/a4j/jsolex/app/shaders/volume.vert";
    private static final String FRAGMENT_SHADER_PATH = "/me/champeau/a4j/jsolex/app/shaders/volume.frag";

    private static final int NUM_STEPS = 128;

    private final SphericalTomographyData data;
    private final Set<Double> hiddenShells = new HashSet<>();

    private ShaderProgram shader;
    private Volume3DTexture volumeTexture;
    private int quadVao;
    private int quadVbo;

    private float cameraDistance = 3.0f;
    private float rotationX = 0;
    private float rotationY = 0;
    private float radialExaggeration = 0.2f;
    private SphereRenderer.ColorMap colorMap = SphereRenderer.ColorMap.MONO;
    private float globalOpacity = 1.0f;
    private boolean showProminences = false;
    private final AtomicBoolean contrastEnhanced = new AtomicBoolean(false);
    private final AtomicBoolean needsTextureReload = new AtomicBoolean(false);

    private boolean initialized = false;
    private List<float[][]> imageDataList;
    private List<float[][]> enhancedImageDataList;
    private float[] layerMins;
    private float[] layerRanges;
    private double minRadius;
    private double maxRadius;
    private int textureWidth;
    private int textureHeight;
    private float diskCenterU = 0.5f;
    private float diskCenterV = 0.5f;
    private float diskRadiusU = 0.5f;
    private float diskRadiusV = 0.5f;
    private float lineCenterDepth = 0.5f;  // Texture depth for pixel shift 0

    public VolumeOpenGLSphereRenderer(SphericalTomographyData data) {
        this.data = data;
    }

    @Override
    public boolean needsTextureReload() {
        return needsTextureReload.get();
    }

    @Override
    public void reloadTextures() {
        if (!needsTextureReload.get() || imageDataList == null) {
            return;
        }

        var currentDataList = getCurrentImageDataList();
        if (currentDataList.isEmpty()) {
            needsTextureReload.set(false);
            return;
        }
        computeLayerStats(currentDataList);
        volumeTexture.create(currentDataList, layerMins, layerRanges);
        updateLineCenterDepth();
        needsTextureReload.set(false);
    }

    @Override
    public void loadTextures() {
        if (initialized) {
            return;
        }

        try {
            initShaders();
            initQuad();
            preprocessData();
            createVolumeTexture();
            initialized = true;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize volume renderer", e);
            throw new RuntimeException("Volume renderer initialization failed", e);
        }
    }

    private void initShaders() throws IOException, ShaderProgram.ShaderCompilationException {
        var vertexSource = ShaderProgram.loadShaderSource(VERTEX_SHADER_PATH);
        var fragmentSource = ShaderProgram.loadShaderSource(FRAGMENT_SHADER_PATH);

        shader = new ShaderProgram();
        shader.compile(vertexSource, fragmentSource);
        LOGGER.debug("Volume shaders compiled successfully");
    }

    private void initQuad() {
        float[] vertices = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
             1.0f,  1.0f,
            -1.0f, -1.0f,
             1.0f,  1.0f,
            -1.0f,  1.0f
        };

        quadVao = glGenVertexArrays();
        glBindVertexArray(quadVao);

        quadVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        int positionAttrib = glGetAttribLocation(shader.getProgramId(), "position");
        glEnableVertexAttribArray(positionAttrib);
        glVertexAttribPointer(positionAttrib, 2, GL_FLOAT, false, 0, 0);

        glBindVertexArray(0);
    }

    private void preprocessData() {
        var shellCount = data.shellCount();

        imageDataList = new ArrayList<>();
        Ellipse ellipse = null;
        for (var i = 0; i < shellCount; i++) {
            var shellData = data.shells().get(i);
            var image = shellData.image();
            var imgData = image.data();
            imageDataList.add(imgData);

            if (i == 0) {
                textureWidth = imgData[0].length;
                textureHeight = imgData.length;
                ellipse = image.findMetadata(Ellipse.class).orElse(null);
            }
        }

        if (ellipse != null) {
            diskCenterU = (float) (ellipse.center().a() / textureWidth);
            diskCenterV = (float) (ellipse.center().b() / textureHeight);
            diskRadiusU = (float) (ellipse.semiAxis().a() / textureWidth);
            diskRadiusV = (float) (ellipse.semiAxis().b() / textureHeight);
        }

        if (data.hasEnhancedShells()) {
            enhancedImageDataList = new ArrayList<>();
            for (var shellData : data.enhancedShells()) {
                var imgData = shellData.image().data();
                enhancedImageDataList.add(imgData);
            }
        }

        minRadius = Double.MAX_VALUE;
        maxRadius = Double.MIN_VALUE;
        int lineCenterIndex = 0;
        double minAbsShift = Double.MAX_VALUE;
        for (var i = 0; i < shellCount; i++) {
            var shellData = data.shells().get(i);
            var radius = shellData.normalizedRadius();
            if (radius < minRadius) {
                minRadius = radius;
            }
            if (radius > maxRadius) {
                maxRadius = radius;
            }
            // Find shell closest to pixel shift 0
            var absShift = Math.abs(shellData.pixelShift());
            if (absShift < minAbsShift) {
                minAbsShift = absShift;
                lineCenterIndex = i;
            }
        }
        // Calculate texture depth for line center (0 to 1 range)
        lineCenterDepth = shellCount > 1 ? (float) lineCenterIndex / (shellCount - 1) : 0.5f;
        LOGGER.debug("Line center at index {} (depth={}), pixelShift={}",
                     lineCenterIndex, lineCenterDepth, data.shells().get(lineCenterIndex).pixelShift());
    }

    private void createVolumeTexture() {
        var currentDataList = getCurrentImageDataList();
        computeLayerStats(currentDataList);

        volumeTexture = new Volume3DTexture();
        volumeTexture.create(currentDataList, layerMins, layerRanges);
    }

    private void computeLayerStats(List<float[][]> dataList) {
        var shellCount = dataList.size();
        layerMins = new float[shellCount];
        layerRanges = new float[shellCount];

        for (var i = 0; i < shellCount; i++) {
            var imgData = dataList.get(i);
            var height = imgData.length;
            var width = imgData[0].length;

            var min = Float.MAX_VALUE;
            var max = Float.MIN_VALUE;

            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var val = imgData[y][x];
                    if (val < min) {
                        min = val;
                    }
                    if (val > max) {
                        max = val;
                    }
                }
            }

            layerMins[i] = min;
            layerRanges[i] = max - min;
        }
    }

    private List<float[][]> getCurrentImageDataList() {
        var baseList = contrastEnhanced.get() && enhancedImageDataList != null
                ? enhancedImageDataList
                : imageDataList;

        if (hiddenShells.isEmpty()) {
            return baseList;
        }

        // Filter out hidden shells
        var filteredList = new ArrayList<float[][]>();
        for (var i = 0; i < data.shellCount(); i++) {
            var shellData = data.shells().get(i);
            if (!hiddenShells.contains(shellData.pixelShift())) {
                filteredList.add(baseList.get(i));
            }
        }
        return filteredList;
    }

    private void updateLineCenterDepth() {
        if (hiddenShells.isEmpty()) {
            // Recalculate from original data
            var shellCount = data.shellCount();
            int lineCenterIndex = 0;
            double minAbsShift = Double.MAX_VALUE;
            for (var i = 0; i < shellCount; i++) {
                var shellData = data.shells().get(i);
                var absShift = Math.abs(shellData.pixelShift());
                if (absShift < minAbsShift) {
                    minAbsShift = absShift;
                    lineCenterIndex = i;
                }
            }
            lineCenterDepth = shellCount > 1 ? (float) lineCenterIndex / (shellCount - 1) : 0.5f;
        } else {
            // Recalculate for visible shells only
            var visibleShells = new ArrayList<SphericalTomographyData.ShellData>();
            for (var shellData : data.shells()) {
                if (!hiddenShells.contains(shellData.pixelShift())) {
                    visibleShells.add(shellData);
                }
            }
            if (visibleShells.isEmpty()) {
                lineCenterDepth = 0.5f;
                return;
            }
            int lineCenterIndex = 0;
            double minAbsShift = Double.MAX_VALUE;
            for (var i = 0; i < visibleShells.size(); i++) {
                var absShift = Math.abs(visibleShells.get(i).pixelShift());
                if (absShift < minAbsShift) {
                    minAbsShift = absShift;
                    lineCenterIndex = i;
                }
            }
            lineCenterDepth = visibleShells.size() > 1 ? (float) lineCenterIndex / (visibleShells.size() - 1) : 0.5f;
        }
    }

    @Override
    public void render(int viewWidth, int viewHeight) {
        if (!initialized) {
            LOGGER.warn("render() called but not initialized");
            return;
        }

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        shader.use();

        // Compute rotation matrix once on CPU
        float[] rotMatrix = computeRotationMatrix();
        shader.setUniformMatrix3("rotationMatrix", rotMatrix);
        shader.setUniform("cameraDistance", cameraDistance);
        shader.setUniform("aspectRatio", (float) viewWidth / viewHeight);

        // Volume parameters
        shader.setUniform("baseRadius", BASE_RADIUS);
        shader.setUniform("radialExaggeration", radialExaggeration);

        // Rendering parameters
        shader.setUniform("numSteps", NUM_STEPS);
        shader.setUniform("colorMapMode", colorMap.ordinal());
        shader.setUniform("globalOpacity", globalOpacity);
        shader.setUniform("showProminences", showProminences ? 1 : 0);

        // Texture mapping parameters (ellipse-based UV mapping)
        shader.setUniform("diskCenterU", diskCenterU);
        shader.setUniform("diskCenterV", diskCenterV);
        shader.setUniform("diskRadiusU", diskRadiusU);
        shader.setUniform("diskRadiusV", diskRadiusV);
        shader.setUniform("lineCenterDepth", lineCenterDepth);

        // Bind 3D texture
        volumeTexture.bind(0);
        shader.setUniform("volumeTexture", 0);

        // Draw fullscreen quad
        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        shader.unbind();
    }

    private float[] computeRotationMatrix() {
        float radX = (float) Math.toRadians(rotationX);
        float radY = (float) Math.toRadians(rotationY);

        float cosX = (float) Math.cos(radX);
        float sinX = (float) Math.sin(radX);
        float cosY = (float) Math.cos(radY);
        float sinY = (float) Math.sin(radY);

        // Combined rotation: rotateY * rotateX (column-major for OpenGL)
        // Column 0, Column 1, Column 2
        return new float[] {
            cosY,   0,      -sinY,
            sinX * sinY,  cosX,   sinX * cosY,
            cosX * sinY,  -sinX,  cosX * cosY
        };
    }

    // Getters and setters matching OpenGLSphereRenderer interface

    @Override
    public void setCameraDistance(float distance) {
        this.cameraDistance = Math.max(1.5f, Math.min(10.0f, distance));
    }

    @Override
    public float getCameraDistance() {
        return cameraDistance;
    }

    @Override
    public void setRotation(float x, float y) {
        this.rotationX = x;
        this.rotationY = y;
    }

    @Override
    public float getRotationX() {
        return rotationX;
    }

    @Override
    public float getRotationY() {
        return rotationY;
    }

    @Override
    public void setRadialExaggeration(float exaggeration) {
        this.radialExaggeration = exaggeration;
    }

    @Override
    public float getRadialExaggeration() {
        return radialExaggeration;
    }

    @Override
    public void setColorMap(SphereRenderer.ColorMap colorMap) {
        this.colorMap = colorMap;
    }

    @Override
    public SphereRenderer.ColorMap getColorMap() {
        return colorMap;
    }

    public void setGlobalOpacity(float opacity) {
        this.globalOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }

    public float getGlobalOpacity() {
        return globalOpacity;
    }

    @Override
    public void setShowProminences(boolean show) {
        this.showProminences = show;
    }

    @Override
    public boolean isShowProminences() {
        return showProminences;
    }

    @Override
    public void setContrastEnhanced(boolean enhanced) {
        if (contrastEnhanced.compareAndSet(!enhanced, enhanced)) {
            needsTextureReload.set(true);
        }
    }

    @Override
    public boolean isContrastEnhanced() {
        return contrastEnhanced.get();
    }

    @Override
    public boolean hasContrastEnhancement() {
        return data.hasEnhancedShells();
    }

    @Override
    public void setShellVisible(double pixelShift, boolean visible) {
        if (visible) {
            hiddenShells.remove(pixelShift);
        } else {
            hiddenShells.add(pixelShift);
        }
        // Note: For volume rendering, shell visibility would require rebuilding
        // the 3D texture with only visible shells. For now, this is a placeholder.
        needsTextureReload.set(true);
    }

    @Override
    public boolean isShellVisible(double pixelShift) {
        return !hiddenShells.contains(pixelShift);
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (volumeTexture != null) {
            volumeTexture.dispose();
            volumeTexture = null;
        }
        if (quadVao != 0) {
            glDeleteVertexArrays(quadVao);
            quadVao = 0;
        }
        if (quadVbo != 0) {
            glDeleteBuffers(quadVbo);
            quadVbo = 0;
        }
        initialized = false;
    }
}
