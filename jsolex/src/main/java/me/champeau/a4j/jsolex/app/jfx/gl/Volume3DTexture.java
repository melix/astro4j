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
package me.champeau.a4j.jsolex.app.jfx.gl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_R32F;

/**
 * Manages a 3D texture for volume rendering of spectral data.
 * The texture stores intensity values from shell images stacked along the Z axis.
 */
public class Volume3DTexture {

    private static final Logger LOGGER = LoggerFactory.getLogger(Volume3DTexture.class);

    private int textureId;
    private int width;
    private int height;
    private int depth;

    public Volume3DTexture() {
        this.textureId = 0;
    }

    /**
     * Creates a 3D texture from a list of shell images.
     * Images are stacked along the Z axis (depth), with index 0 being the innermost shell.
     *
     * @param shellImages list of 2D float arrays representing shell intensities
     * @param layerMins minimum intensity values per layer (for normalization)
     * @param layerRanges intensity ranges per layer (max - min, for normalization)
     */
    public void create(List<float[][]> shellImages, float[] layerMins, float[] layerRanges) {
        if (shellImages == null || shellImages.isEmpty()) {
            throw new IllegalArgumentException("Shell images list cannot be null or empty");
        }

        dispose();

        depth = shellImages.size();
        height = shellImages.getFirst().length;
        width = shellImages.getFirst()[0].length;

        LOGGER.debug("Creating 3D texture: {}x{}x{}", width, height, depth);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(width * height * depth);

        for (var z = 0; z < depth; z++) {
            var shell = shellImages.get(z);
            var layerMin = layerMins[z];
            var layerRange = layerRanges[z];

            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var rawValue = shell[y][x];
                    var normalized = layerRange > 0.001f ? (rawValue - layerMin) / layerRange : 0.5f;
                    normalized = Math.min(1.0f, Math.max(0.0f, normalized));
                    buffer.put(normalized);
                }
            }
        }
        buffer.flip();

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, textureId);

        glTexImage3D(GL_TEXTURE_3D, 0, GL_R32F, width, height, depth, 0, GL_RED, GL_FLOAT, buffer);

        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        var error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGGER.error("OpenGL error after creating 3D texture: {}", error);
        }

        glBindTexture(GL_TEXTURE_3D, 0);
    }

    /**
     * Binds the 3D texture to the specified texture unit.
     *
     * @param textureUnit the texture unit (0-based, e.g., 0 for GL_TEXTURE0)
     */
    public void bind(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_3D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_3D, 0);
    }

    public int getTextureId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public void dispose() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }
}
