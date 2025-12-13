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

import javafx.scene.shape.TriangleMesh;

/**
 * Utility class for building UV-mapped sphere meshes for JavaFX 3D.
 * Provides both equirectangular and orthographic UV mapping for different use cases.
 */
public final class SphereMeshBuilder {

    private SphereMeshBuilder() {
    }

    /**
     * Creates a UV-mapped sphere mesh with orthographic projection UV mapping.
     * This is suitable for solar disk images which are captured as orthographic projections.
     * The UV coordinates map the circular disk image onto the visible hemisphere.
     *
     * @param radius    the radius of the sphere
     * @param divisions the number of divisions for latitude and longitude (higher = smoother)
     * @return a TriangleMesh representing the sphere
     */
    public static TriangleMesh createSphere(double radius, int divisions) {
        var mesh = new TriangleMesh();

        int latDivisions = divisions;
        int lonDivisions = divisions * 2;

        int numVertices = (latDivisions + 1) * (lonDivisions + 1);
        var points = new float[numVertices * 3];
        var texCoords = new float[numVertices * 2];

        int pointIdx = 0;
        int texIdx = 0;

        for (int lat = 0; lat <= latDivisions; lat++) {
            double theta = Math.PI * lat / latDivisions;
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);

            for (int lon = 0; lon <= lonDivisions; lon++) {
                double phi = 2 * Math.PI * lon / lonDivisions;
                double sinPhi = Math.sin(phi);
                double cosPhi = Math.cos(phi);

                double x = radius * sinTheta * cosPhi;
                double y = radius * cosTheta;
                double z = radius * sinTheta * sinPhi;

                points[pointIdx++] = (float) x;
                points[pointIdx++] = (float) y;
                points[pointIdx++] = (float) z;

                // Orthographic projection UV mapping for solar disk images:
                // Project the 3D point onto a plane (x,y) and normalize to [0,1]
                // The solar disk image shows what we see looking at the Sun face-on
                float u = (float) (0.5 + 0.5 * sinTheta * cosPhi);
                float v = (float) (0.5 - 0.5 * cosTheta);
                texCoords[texIdx++] = u;
                texCoords[texIdx++] = v;
            }
        }

        int numFaces = latDivisions * lonDivisions * 2;
        var faces = new int[numFaces * 6];
        int faceIdx = 0;

        for (int lat = 0; lat < latDivisions; lat++) {
            for (int lon = 0; lon < lonDivisions; lon++) {
                int current = lat * (lonDivisions + 1) + lon;
                int next = current + lonDivisions + 1;

                faces[faceIdx++] = current;
                faces[faceIdx++] = current;
                faces[faceIdx++] = next;
                faces[faceIdx++] = next;
                faces[faceIdx++] = current + 1;
                faces[faceIdx++] = current + 1;

                faces[faceIdx++] = current + 1;
                faces[faceIdx++] = current + 1;
                faces[faceIdx++] = next;
                faces[faceIdx++] = next;
                faces[faceIdx++] = next + 1;
                faces[faceIdx++] = next + 1;
            }
        }

        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
        mesh.getFaces().setAll(faces);

        return mesh;
    }

    /**
     * Creates a hemisphere mesh (front-facing half of a sphere) with orthographic UV mapping.
     * Uses full image as texture (UV 0-1 maps to full image).
     *
     * @param radius    the radius of the hemisphere
     * @param divisions the number of divisions (higher = smoother)
     * @return a TriangleMesh representing the front hemisphere
     */
    public static TriangleMesh createHemisphere(double radius, int divisions) {
        return createHemisphere(radius, divisions, 0.5, 0.5, 0.5, 0.5);
    }

    /**
     * Creates a hemisphere mesh with orthographic UV mapping relative to a disk region.
     * The disk is defined by its center in UV coordinates and its radius in UV units.
     *
     * @param radius         the 3D radius of the hemisphere
     * @param divisions      the number of divisions (higher = smoother)
     * @param diskCenterU    the U coordinate of the disk center (0-1)
     * @param diskCenterV    the V coordinate of the disk center (0-1)
     * @param diskRadiusU    the radius of the disk in U units
     * @param diskRadiusV    the radius of the disk in V units
     * @return a TriangleMesh representing the front hemisphere
     */
    public static TriangleMesh createHemisphere(double radius, int divisions,
                                                 double diskCenterU, double diskCenterV,
                                                 double diskRadiusU, double diskRadiusV) {
        var mesh = new TriangleMesh();

        int radialDivisions = divisions;
        int angularDivisions = divisions * 4;

        int numVertices = radialDivisions * angularDivisions + 1;
        var points = new float[numVertices * 3];
        var texCoords = new float[numVertices * 2];

        int pointIdx = 0;
        int texIdx = 0;

        // Center point - facing toward camera (negative Z)
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        points[pointIdx++] = (float) -radius;
        texCoords[texIdx++] = (float) diskCenterU;
        texCoords[texIdx++] = (float) diskCenterV;

        // Build rings from center outward, parameterized by angle from center (0 to π/2)
        for (int r = 1; r <= radialDivisions; r++) {
            // theta goes from 0 (center) to π/2 (edge)
            double theta = (Math.PI / 2.0) * r / radialDivisions;
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);

            // 3D position on hemisphere
            double xyRadius = sinTheta * radius;  // distance from Z axis
            double z = -cosTheta * radius;        // Z coordinate (negative = toward camera)

            for (int a = 0; a < angularDivisions; a++) {
                double phi = 2 * Math.PI * a / angularDivisions;
                double x = xyRadius * Math.cos(phi);
                double y = xyRadius * Math.sin(phi);

                points[pointIdx++] = (float) x;
                points[pointIdx++] = (float) y;
                points[pointIdx++] = (float) z;

                // Orthographic projection: UV distance from center = sin(theta)
                // Map to the disk region defined by center and radius
                float u = (float) (diskCenterU + diskRadiusU * sinTheta * Math.cos(phi));
                float v = (float) (diskCenterV - diskRadiusV * sinTheta * Math.sin(phi));
                texCoords[texIdx++] = u;
                texCoords[texIdx++] = v;
            }
        }

        int numFaces = angularDivisions + (radialDivisions - 1) * angularDivisions * 2;
        var faces = new int[numFaces * 6];
        int faceIdx = 0;

        // Center triangles - winding order for front-facing (toward negative Z)
        for (int a = 0; a < angularDivisions; a++) {
            int next = (a + 1) % angularDivisions;
            faces[faceIdx++] = 0;
            faces[faceIdx++] = 0;
            faces[faceIdx++] = 1 + next;
            faces[faceIdx++] = 1 + next;
            faces[faceIdx++] = 1 + a;
            faces[faceIdx++] = 1 + a;
        }

        // Ring quads (as two triangles each)
        for (int r = 1; r < radialDivisions; r++) {
            int ringStart = 1 + (r - 1) * angularDivisions;
            int nextRingStart = 1 + r * angularDivisions;

            for (int a = 0; a < angularDivisions; a++) {
                int next = (a + 1) % angularDivisions;

                int current = ringStart + a;
                int currentNext = ringStart + next;
                int outer = nextRingStart + a;
                int outerNext = nextRingStart + next;

                // First triangle - winding for front face
                faces[faceIdx++] = current;
                faces[faceIdx++] = current;
                faces[faceIdx++] = currentNext;
                faces[faceIdx++] = currentNext;
                faces[faceIdx++] = outer;
                faces[faceIdx++] = outer;

                // Second triangle
                faces[faceIdx++] = currentNext;
                faces[faceIdx++] = currentNext;
                faces[faceIdx++] = outerNext;
                faces[faceIdx++] = outerNext;
                faces[faceIdx++] = outer;
                faces[faceIdx++] = outer;
            }
        }

        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
        mesh.getFaces().setAll(faces);

        return mesh;
    }
}
