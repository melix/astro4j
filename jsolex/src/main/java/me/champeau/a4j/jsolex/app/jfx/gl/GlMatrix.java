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

/**
 * Column-major 4x4 matrix helpers, laid out for direct upload via
 * glUniformMatrix4fv with transpose=false.
 */
public final class GlMatrix {

    private GlMatrix() {
    }

    /**
     * Returns a fresh 4x4 identity matrix.
     *
     * @return the identity matrix in column-major order
     */
    public static float[] identity() {
        var m = new float[16];
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
        return m;
    }

    /**
     * Builds a perspective frustum projection matrix, equivalent to the legacy {@code glFrustum}.
     *
     * @param left   left clip plane in eye space
     * @param right  right clip plane in eye space
     * @param bottom bottom clip plane in eye space
     * @param top    top clip plane in eye space
     * @param near   near clip plane distance (must be positive)
     * @param far    far clip plane distance (must be greater than {@code near})
     * @return the projection matrix in column-major order
     */
    public static float[] frustum(float left, float right, float bottom, float top, float near, float far) {
        var m = new float[16];
        m[0] = 2f * near / (right - left);
        m[5] = 2f * near / (top - bottom);
        m[8] = (right + left) / (right - left);
        m[9] = (top + bottom) / (top - bottom);
        m[10] = -(far + near) / (far - near);
        m[11] = -1f;
        m[14] = -2f * far * near / (far - near);
        return m;
    }

    /**
     * Builds a translation matrix, equivalent to the legacy {@code glTranslatef}.
     *
     * @param x translation along the X axis
     * @param y translation along the Y axis
     * @param z translation along the Z axis
     * @return the translation matrix in column-major order
     */
    public static float[] translation(float x, float y, float z) {
        var m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    /**
     * Builds a rotation matrix around the X axis, equivalent to the legacy
     * {@code glRotatef(angleDeg, 1, 0, 0)}.
     *
     * @param angleDeg rotation angle in degrees
     * @return the rotation matrix in column-major order
     */
    public static float[] rotationX(float angleDeg) {
        var rad = (float) Math.toRadians(angleDeg);
        var c = (float) Math.cos(rad);
        var s = (float) Math.sin(rad);
        var m = identity();
        m[5] = c;
        m[6] = s;
        m[9] = -s;
        m[10] = c;
        return m;
    }

    /**
     * Builds a rotation matrix around the Y axis, equivalent to the legacy
     * {@code glRotatef(angleDeg, 0, 1, 0)}.
     *
     * @param angleDeg rotation angle in degrees
     * @return the rotation matrix in column-major order
     */
    public static float[] rotationY(float angleDeg) {
        var rad = (float) Math.toRadians(angleDeg);
        var c = (float) Math.cos(rad);
        var s = (float) Math.sin(rad);
        var m = identity();
        m[0] = c;
        m[2] = -s;
        m[8] = s;
        m[10] = c;
        return m;
    }

    /**
     * Builds a uniform scaling matrix, equivalent to the legacy {@code glScalef(s, s, s)}.
     *
     * @param s scale factor applied to X, Y and Z
     * @return the scaling matrix in column-major order
     */
    public static float[] scale(float s) {
        var m = new float[16];
        m[0] = s;
        m[5] = s;
        m[10] = s;
        m[15] = 1f;
        return m;
    }

    /**
     * Multiplies two column-major 4x4 matrices and returns the result {@code a * b}.
     *
     * @param a left-hand matrix in column-major order
     * @param b right-hand matrix in column-major order
     * @return a fresh column-major matrix containing {@code a * b}
     */
    public static float[] multiply(float[] a, float[] b) {
        var r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float v = 0f;
                for (int k = 0; k < 4; k++) {
                    v += a[k * 4 + row] * b[col * 4 + k];
                }
                r[col * 4 + row] = v;
            }
        }
        return r;
    }
}
