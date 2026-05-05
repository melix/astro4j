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

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural mesh generator for the unit-radius hemisphere and prominence band
 * used by the 3D viewers. Vertex positions match the original immediate-mode
 * code in OpenGLSphereRenderer / SingleImageSphereRenderer; per-frame radius
 * scaling is applied via the model-matrix uniform and UV mapping is computed
 * in the vertex shader from the disk-relative xy of each vertex.
 */
public final class SphereMeshBuilder {

    /**
     * A triangle mesh as flat float positions (x, y, z per vertex) and unsigned-int indices
     * suitable for {@code glBufferData} + {@code glDrawElements}.
     *
     * @param positions interleaved vertex positions, 3 floats per vertex
     * @param indices   triangle indices into {@code positions}
     */
    public record Mesh(float[] positions, int[] indices) {
        /**
         * Returns the number of vertices stored in {@link #positions()}.
         *
         * @return the vertex count
         */
        public int vertexCount() {
            return positions.length / 3;
        }

        /**
         * Returns the number of indices stored in {@link #indices()},
         * i.e. the value to pass as {@code count} to {@code glDrawElements}.
         *
         * @return the index count
         */
        public int indexCount() {
            return indices.length;
        }
    }

    private SphereMeshBuilder() {
    }

    /**
     * Builds the front-facing unit hemisphere on a regular grid in (nx, ny) in [-1,1]^2,
     * with z = sqrt(1 - r^2) for r &lt; 1 and edge-snapping (z = 0, xy projected onto the
     * unit circle) for grid cells crossing r &gt;= 1. Cells fully outside the disk are skipped.
     *
     * @param divisions number of grid subdivisions along each axis; the mesh has at most
     *                  {@code divisions * divisions} quads
     * @return the hemisphere mesh
     */
    public static Mesh buildHemisphere(int divisions) {
        var positions = new ArrayList<float[]>();
        var indices = new ArrayList<Integer>();

        for (int i = 0; i < divisions; i++) {
            for (int j = 0; j < divisions; j++) {
                float nx1 = -1f + 2f * i / divisions;
                float ny1 = -1f + 2f * j / divisions;
                float nx2 = -1f + 2f * (i + 1) / divisions;
                float ny2 = -1f + 2f * (j + 1) / divisions;

                float r1 = nx1 * nx1 + ny1 * ny1;
                float r2 = nx2 * nx2 + ny1 * ny1;
                float r3 = nx2 * nx2 + ny2 * ny2;
                float r4 = nx1 * nx1 + ny2 * ny2;

                if (r1 > 1f && r2 > 1f && r3 > 1f && r4 > 1f) {
                    continue;
                }

                var p1 = projectToHemisphere(nx1, ny1, r1);
                var p2 = projectToHemisphere(nx2, ny1, r2);
                var p3 = projectToHemisphere(nx2, ny2, r3);
                var p4 = projectToHemisphere(nx1, ny2, r4);

                int base = positions.size();
                positions.add(p1);
                positions.add(p2);
                positions.add(p3);
                positions.add(p4);

                indices.add(base);
                indices.add(base + 1);
                indices.add(base + 2);
                indices.add(base);
                indices.add(base + 2);
                indices.add(base + 3);
            }
        }

        return toMesh(positions, indices);
    }

    /**
     * Builds the prominence band: an annulus around the disk in the {@code z = zOffset} plane,
     * tessellated radially from {@code imgR = 1} to {@code imgR = maxImageExtent}.
     *
     * @param angularDivisions number of segments around the ring
     * @param radialSteps      number of radial subdivisions between the inner and outer edges
     * @param maxImageExtent   outer extent of the ring in disk-radius units (e.g. 1.25)
     * @param zOffset          z-plane offset for the ring (small positive value to sit in front of the limb)
     * @return the prominence band mesh
     */
    public static Mesh buildProminenceBand(int angularDivisions, int radialSteps, float maxImageExtent, float zOffset) {
        var positions = new ArrayList<float[]>();
        var indices = new ArrayList<Integer>();

        for (int i = 0; i < angularDivisions; i++) {
            float a1 = (float) (2.0 * Math.PI * i / angularDivisions);
            float a2 = (float) (2.0 * Math.PI * (i + 1) / angularDivisions);

            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            for (int j = 0; j < radialSteps; j++) {
                float t1 = (float) j / radialSteps;
                float t2 = (float) (j + 1) / radialSteps;
                float ir1 = 1f + t1 * (maxImageExtent - 1f);
                float ir2 = 1f + t2 * (maxImageExtent - 1f);

                int base = positions.size();
                positions.add(new float[]{c1 * ir1, s1 * ir1, zOffset});
                positions.add(new float[]{c2 * ir1, s2 * ir1, zOffset});
                positions.add(new float[]{c2 * ir2, s2 * ir2, zOffset});
                positions.add(new float[]{c1 * ir2, s1 * ir2, zOffset});

                indices.add(base);
                indices.add(base + 1);
                indices.add(base + 2);
                indices.add(base);
                indices.add(base + 2);
                indices.add(base + 3);
            }
        }

        return toMesh(positions, indices);
    }

    private static float[] projectToHemisphere(float nx, float ny, float rSq) {
        if (rSq >= 1f) {
            float scale = 1f / (float) Math.sqrt(rSq);
            return new float[]{nx * scale, ny * scale, 0f};
        }
        return new float[]{nx, ny, (float) Math.sqrt(1f - rSq)};
    }

    private static Mesh toMesh(List<float[]> positions, List<Integer> indices) {
        var pos = new float[positions.size() * 3];
        for (int i = 0; i < positions.size(); i++) {
            var p = positions.get(i);
            pos[i * 3] = p[0];
            pos[i * 3 + 1] = p[1];
            pos[i * 3 + 2] = p[2];
        }
        var idx = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            idx[i] = indices.get(i);
        }
        return new Mesh(pos, idx);
    }
}
