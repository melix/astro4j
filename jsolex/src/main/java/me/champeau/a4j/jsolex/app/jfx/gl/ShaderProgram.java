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

import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL20.*;

/**
 * Utility class for compiling and managing OpenGL shader programs.
 */
public class ShaderProgram {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProgram.class);

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    /**
     * Creates a new shader program instance.
     */
    public ShaderProgram() {
        this.programId = 0;
        this.vertexShaderId = 0;
        this.fragmentShaderId = 0;
    }

    /**
     * Compiles and links vertex and fragment shaders into a program.
     *
     * @param vertexSource the vertex shader source code
     * @param fragmentSource the fragment shader source code
     * @throws ShaderCompilationException if shader compilation or linking fails
     */
    public void compile(String vertexSource, String fragmentSource) throws ShaderCompilationException {
        vertexShaderId = compileShader(GL_VERTEX_SHADER, vertexSource);
        fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

        programId = glCreateProgram();
        if (programId == 0) {
            throw new ShaderCompilationException("Failed to create shader program");
        }

        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        int linkStatus = glGetProgrami(programId, GL_LINK_STATUS);
        if (linkStatus == GL_FALSE) {
            var log = glGetProgramInfoLog(programId);
            dispose();
            throw new ShaderCompilationException("Failed to link shader program: " + log);
        }

        glValidateProgram(programId);
        int validateStatus = glGetProgrami(programId, GL_VALIDATE_STATUS);
        if (validateStatus == GL_FALSE) {
            LOGGER.warn("Shader program validation warning: {}", glGetProgramInfoLog(programId));
        }
    }

    private int compileShader(int type, String source) throws ShaderCompilationException {
        int shaderId = glCreateShader(type);
        if (shaderId == 0) {
            throw new ShaderCompilationException("Failed to create shader of type: " + type);
        }

        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        int compileStatus = glGetShaderi(shaderId, GL_COMPILE_STATUS);
        if (compileStatus == GL_FALSE) {
            var log = glGetShaderInfoLog(shaderId);
            glDeleteShader(shaderId);
            var typeName = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new ShaderCompilationException("Failed to compile " + typeName + " shader: " + log);
        }

        return shaderId;
    }

    /**
     * Loads shader source code from a resource file.
     *
     * @param resourcePath the path to the shader resource
     * @return the shader source code as a string
     * @throws IOException if the resource cannot be read
     */
    public static String loadShaderSource(String resourcePath) throws IOException {
        try (InputStream is = ShaderProgram.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Shader resource not found: " + resourcePath);
            }
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Activates this shader program for use in rendering.
     */
    public void use() {
        glUseProgram(programId);
    }

    /**
     * Deactivates the current shader program.
     */
    public void unbind() {
        glUseProgram(0);
    }

    /**
     * Retrieves the location of a uniform variable in the shader program.
     *
     * @param name the name of the uniform variable
     * @return the location of the uniform, or -1 if not found
     */
    public int getUniformLocation(String name) {
        return uniformLocations.computeIfAbsent(name, n -> {
            int loc = glGetUniformLocation(programId, n);
            if (loc == -1) {
                LoggerFactory.getLogger(ShaderProgram.class).warn("Uniform '{}' not found in shader", n);
            }
            return loc;
        });
    }

    /**
     * Sets an integer uniform variable.
     *
     * @param name the name of the uniform variable
     * @param value the integer value to set
     */
    public void setUniform(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    /**
     * Sets a float uniform variable.
     *
     * @param name the name of the uniform variable
     * @param value the float value to set
     */
    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    /**
     * Sets a 2D vector uniform variable.
     *
     * @param name the name of the uniform variable
     * @param x the x component
     * @param y the y component
     */
    public void setUniform(String name, float x, float y) {
        glUniform2f(getUniformLocation(name), x, y);
    }

    /**
     * Sets a 3D vector uniform variable.
     *
     * @param name the name of the uniform variable
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    public void setUniform(String name, float x, float y, float z) {
        glUniform3f(getUniformLocation(name), x, y, z);
    }

    /**
     * Sets a 4D vector uniform variable.
     *
     * @param name the name of the uniform variable
     * @param x the x component
     * @param y the y component
     * @param z the z component
     * @param w the w component
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        glUniform4f(getUniformLocation(name), x, y, z, w);
    }

    /**
     * Sets a 3x3 matrix uniform variable.
     *
     * @param name the name of the uniform variable
     * @param matrix the matrix values in column-major order
     */
    public void setUniformMatrix3(String name, float[] matrix) {
        glUniformMatrix3fv(getUniformLocation(name), false, matrix);
    }

    /**
     * Sets a 4x4 matrix uniform variable.
     *
     * @param name the name of the uniform variable
     * @param matrix the matrix values in column-major order
     */
    public void setUniformMatrix4(String name, float[] matrix) {
        glUniformMatrix4fv(getUniformLocation(name), false, matrix);
    }

    /**
     * Returns the OpenGL program ID.
     *
     * @return the program ID
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Cleans up and releases OpenGL resources used by this shader program.
     */
    public void dispose() {
        unbind();
        if (programId != 0) {
            if (vertexShaderId != 0) {
                glDetachShader(programId, vertexShaderId);
                glDeleteShader(vertexShaderId);
            }
            if (fragmentShaderId != 0) {
                glDetachShader(programId, fragmentShaderId);
                glDeleteShader(fragmentShaderId);
            }
            glDeleteProgram(programId);
        }
        uniformLocations.clear();
        programId = 0;
        vertexShaderId = 0;
        fragmentShaderId = 0;
    }

    /**
     * Exception thrown when shader compilation or linking fails.
     */
    public static class ShaderCompilationException extends Exception {
        /**
         * Creates a new shader compilation exception.
         *
         * @param message the error message
         */
        public ShaderCompilationException(String message) {
            super(message);
        }
    }
}
