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
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineSurfaceData;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.VideoEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Abstract base class for 3D spectral surface viewers.
 * Provides common functionality for camera, lighting, mesh creation, legend, and export.
 */
public abstract class AbstractSpectral3DViewer extends BorderPane {
    /** Initial camera distance from the surface. */
    protected static final double INITIAL_DISTANCE = 800;
    /** Minimum zoom distance. */
    protected static final double MIN_DISTANCE = 200;
    /** Maximum zoom distance. */
    protected static final double MAX_DISTANCE = 2000;
    /** Default surface size in 3D units. */
    protected static final double SURFACE_SIZE = 400;

    /** Root group for all 3D objects. */
    protected final Group root3D;
    /** Perspective camera for 3D rendering. */
    protected final PerspectiveCamera camera;
    /** Rotation transform around X axis. */
    protected final Rotate rotateX;
    /** Rotation transform around Y axis. */
    protected final Rotate rotateY;
    /** Translation transform for camera distance. */
    protected final Translate cameraTranslate;

    private double anchorX;
    private double anchorY;
    private double anchorAngleX;
    private double anchorAngleY;

    /** Current intensity scale mode. */
    protected IntensityScale scale = IntensityScale.LINEAR;
    /** Group containing the 3D surface mesh. */
    protected Group surfaceGroup;
    /** Group containing the axes and labels. */
    protected Group axesGroup;

    /** Current X size of the surface in 3D units. */
    protected double currentSurfaceXSize = SURFACE_SIZE;
    /** Current Z size of the surface in 3D units. */
    protected double currentSurfaceZSize = SURFACE_SIZE;

    /** Reusable mesh for performance optimization. */
    protected TriangleMesh reusableMesh;
    /** Last X count used for mesh creation. */
    protected int lastMeshXCount = -1;
    /** Last Z count used for mesh creation. */
    protected int lastMeshZCount = -1;

    /** Label displaying the legend title. */
    protected Label legendTitleLabel;
    /** Label for high intensity values. */
    protected Label legendHighLabel;
    /** Label for low intensity values. */
    protected Label legendLowLabel;
    /** Label for interpretation text. */
    protected Label legendInterpretLabel;

    /** Pane containing the 3D graph. */
    protected final StackPane graphPane;
    /** Whether to export only the graph or the entire window. */
    protected boolean exportGraphOnly = true;

    /** Creates a new 3D viewer with default camera and lighting. */
    protected AbstractSpectral3DViewer() {
        this.root3D = new Group();
        this.camera = new PerspectiveCamera(true);
        this.rotateX = new Rotate(-30, Rotate.X_AXIS);
        this.rotateY = new Rotate(30, Rotate.Y_AXIS);
        this.cameraTranslate = new Translate(0, 0, -INITIAL_DISTANCE);

        setupCamera();
        setupLighting();

        var subScene = new SubScene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.rgb(30, 30, 40));
        subScene.setCamera(camera);

        subScene.widthProperty().bind(widthProperty().subtract(180));
        subScene.heightProperty().bind(heightProperty().subtract(100));

        setupMouseHandlers(subScene);

        graphPane = new StackPane(subScene);
    }

    /** Initializes the view by building the surface and setting up the UI panels. */
    protected void initializeView() {
        buildSurface();
        addAxesWithLabels();

        setCenter(graphPane);
        setRight(createLegendPanel());
        setTop(createDescriptionPanel());
        setBottom(createControlPanel());
    }

    private void setupCamera() {
        camera.setNearClip(1);
        camera.setFarClip(5000);
        camera.getTransforms().addAll(rotateY, rotateX, cameraTranslate);
    }

    private void setupLighting() {
        var ambientLight = new AmbientLight(Color.rgb(120, 120, 120));
        var pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(SURFACE_SIZE);
        pointLight.setTranslateY(-SURFACE_SIZE);
        pointLight.setTranslateZ(-SURFACE_SIZE);
        root3D.getChildren().addAll(ambientLight, pointLight);
    }

    /** Builds or rebuilds the 3D surface mesh. */
    protected abstract void buildSurface();

    /** Adds coordinate axes with labels to the 3D scene. */
    protected abstract void addAxesWithLabels();

    /**
     * Creates the description panel shown at the top of the viewer.
     * @return the description panel
     */
    protected abstract HBox createDescriptionPanel();

    /**
     * Creates the control panel with buttons and sliders.
     * @return the control panel
     */
    protected abstract HBox createControlPanel();

    /**
     * Returns the i18n key for the interpretation text.
     * @return the interpretation key
     */
    protected abstract String getInterpretationKey();

    /**
     * Returns the minimum intensity value for the legend.
     * @return the minimum intensity
     */
    protected abstract double getMinIntensity();

    /**
     * Returns the maximum intensity value for the legend.
     * @return the maximum intensity
     */
    protected abstract double getMaxIntensity();

    /**
     * Returns whether this viewer supports video export.
     * @return true if video export is supported
     */
    protected abstract boolean supportsVideoExport();

    /**
     * Returns the number of frames for video export.
     * @return the frame count
     */
    protected abstract int getVideoFrameCount();

    /**
     * Sets the frame index during video export.
     * @param index the frame index to display
     */
    protected abstract void setVideoFrameIndex(int index);

    /**
     * Builds the surface from data without preserving aspect ratio.
     * @param surfaceData the spectral data to render
     * @param meshXCount the number of mesh points along the X axis
     * @param meshZCount the number of mesh points along the Z axis
     */
    protected void buildSurfaceFromData(SpectralLineSurfaceData surfaceData, int meshXCount, int meshZCount) {
        buildSurfaceFromData(surfaceData, meshXCount, meshZCount, false);
    }

    /**
     * Builds the surface from spectral data with configurable aspect ratio.
     * @param surfaceData the spectral data to render
     * @param meshXCount the number of mesh points along the X axis
     * @param meshZCount the number of mesh points along the Z axis
     * @param preserveAspectRatio whether to preserve the data aspect ratio
     */
    protected void buildSurfaceFromData(SpectralLineSurfaceData surfaceData, int meshXCount, int meshZCount,
                                        boolean preserveAspectRatio) {
        var fullXCount = surfaceData.xAxisCount();
        var fullZCount = surfaceData.wavelengthCount();

        var surfaceXSize = SURFACE_SIZE;
        var surfaceZSize = SURFACE_SIZE;

        if (preserveAspectRatio) {
            var xPositions = surfaceData.xAxisPositions();
            var zPositions = surfaceData.wavelengthOffsets();
            double xRange = xPositions[xPositions.length - 1] - xPositions[0];
            var zRange = zPositions[zPositions.length - 1] - zPositions[0];

            var aspectRatio = (xRange != 0 && zRange != 0) ? xRange / zRange : 1.0;
            if (aspectRatio > 1) {
                surfaceZSize = SURFACE_SIZE / aspectRatio;
            } else {
                surfaceXSize = SURFACE_SIZE * aspectRatio;
            }
        }

        currentSurfaceXSize = surfaceXSize;
        currentSurfaceZSize = surfaceZSize;

        boolean canReuseMesh = reusableMesh != null
                && lastMeshXCount == meshXCount
                && lastMeshZCount == meshZCount;

        if (canReuseMesh) {
            updateMeshPoints(reusableMesh, surfaceData, fullXCount, fullZCount, meshXCount, meshZCount, surfaceXSize, surfaceZSize);
        } else {
            if (surfaceGroup != null) {
                root3D.getChildren().remove(surfaceGroup);
            }

            reusableMesh = createCombinedMesh(surfaceData, fullXCount, fullZCount, meshXCount, meshZCount, surfaceXSize, surfaceZSize);
            lastMeshXCount = meshXCount;
            lastMeshZCount = meshZCount;

            var meshView = new MeshView(reusableMesh);
            meshView.setMaterial(createMaterial());
            meshView.setCullFace(CullFace.NONE);
            meshView.setDrawMode(DrawMode.FILL);
            meshView.setMouseTransparent(true);

            surfaceGroup = new Group(meshView);
            surfaceGroup.setMouseTransparent(true);
            surfaceGroup.getTransforms().addAll(
                    new Translate(-surfaceXSize / 2, 0, -surfaceZSize / 2),
                    new Scale(1, -1, 1)
            );

            root3D.getChildren().add(surfaceGroup);
        }
    }

    /** Invalidates the cached mesh, forcing a rebuild on next update. */
    protected void invalidateMesh() {
        reusableMesh = null;
        lastMeshXCount = -1;
        lastMeshZCount = -1;
    }

    /**
     * Updates mesh vertex positions with new surface data.
     * @param mesh the mesh to update
     * @param surfaceData the spectral data
     * @param fullXCount total X points in data
     * @param fullZCount total Z points in data
     * @param meshXCount mesh X resolution
     * @param meshZCount mesh Z resolution
     * @param surfaceXSize surface width in 3D units
     * @param surfaceZSize surface depth in 3D units
     */
    protected void updateMeshPoints(TriangleMesh mesh, SpectralLineSurfaceData surfaceData, int fullXCount, int fullZCount,
                                    int meshXCount, int meshZCount, double surfaceXSize, double surfaceZSize) {
        int surfaceVertices = meshXCount * meshZCount;
        int wallVertices = meshXCount * 4 + meshZCount * 4;
        int bottomVertices = 4;
        int totalVertices = surfaceVertices + wallVertices + bottomVertices;

        var points = new float[totalVertices * 3];
        var texCoords = new float[totalVertices * 2];

        float xScale = (float) surfaceXSize / (meshXCount - 1);
        float zScale = (float) surfaceZSize / (meshZCount - 1);
        float yScale = (float) (SURFACE_SIZE * 0.5);

        int pointIdx = 0;
        int texIdx = 0;

        // === SURFACE VERTICES ===
        for (var z = 0; z < meshZCount; z++) {
            var srcZ = meshZCount == fullZCount ? z : z * (fullZCount - 1) / (meshZCount - 1);
            float zPos = z * zScale;
            for (var x = 0; x < meshXCount; x++) {
                var srcX = meshXCount == fullXCount ? x : x * (fullXCount - 1) / (meshXCount - 1);
                var normalizedValue = surfaceData.normalizedIntensity(srcX, srcZ);
                var scaledValue = applyScale(normalizedValue);

                points[pointIdx++] = x * xScale;
                points[pointIdx++] = scaledValue * yScale;
                points[pointIdx++] = zPos;

                texCoords[texIdx++] = scaledValue;
                texCoords[texIdx++] = 0.5f;
            }
        }

        // === WALL VERTICES ===
        int srcZBack = meshZCount == fullZCount ? meshZCount - 1 : (meshZCount - 1) * (fullZCount - 1) / (meshZCount - 1);
        int srcXRight = meshXCount == fullXCount ? meshXCount - 1 : (meshXCount - 1) * (fullXCount - 1) / (meshXCount - 1);

        for (var x = 0; x < meshXCount; x++) {
            var srcX = meshXCount == fullXCount ? x : x * (fullXCount - 1) / (meshXCount - 1);
            float xPos = x * xScale;

            var frontNormalized = surfaceData.normalizedIntensity(srcX, 0);
            var frontScaled = applyScale(frontNormalized);
            points[pointIdx++] = xPos;
            points[pointIdx++] = frontScaled * yScale;
            points[pointIdx++] = 0;
            texCoords[texIdx++] = frontScaled;
            texCoords[texIdx++] = 0.5f;

            points[pointIdx++] = xPos;
            points[pointIdx++] = 0;
            points[pointIdx++] = 0;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;

            var backNormalized = surfaceData.normalizedIntensity(srcX, srcZBack);
            var backScaled = applyScale(backNormalized);
            points[pointIdx++] = xPos;
            points[pointIdx++] = backScaled * yScale;
            points[pointIdx++] = (float) surfaceZSize;
            texCoords[texIdx++] = backScaled;
            texCoords[texIdx++] = 0.5f;

            points[pointIdx++] = xPos;
            points[pointIdx++] = 0;
            points[pointIdx++] = (float) surfaceZSize;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;
        }

        for (var z = 0; z < meshZCount; z++) {
            var srcZ = meshZCount == fullZCount ? z : z * (fullZCount - 1) / (meshZCount - 1);
            float zPos = z * zScale;

            var leftNormalized = surfaceData.normalizedIntensity(0, srcZ);
            var leftScaled = applyScale(leftNormalized);
            points[pointIdx++] = 0;
            points[pointIdx++] = leftScaled * yScale;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = leftScaled;
            texCoords[texIdx++] = 0.5f;

            points[pointIdx++] = 0;
            points[pointIdx++] = 0;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;

            var rightNormalized = surfaceData.normalizedIntensity(srcXRight, srcZ);
            var rightScaled = applyScale(rightNormalized);
            points[pointIdx++] = (float) surfaceXSize;
            points[pointIdx++] = rightScaled * yScale;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = rightScaled;
            texCoords[texIdx++] = 0.5f;

            points[pointIdx++] = (float) surfaceXSize;
            points[pointIdx++] = 0;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;
        }

        // === BOTTOM VERTICES (static - no intensity changes) ===
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        points[pointIdx++] = (float) surfaceXSize;
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        points[pointIdx++] = (float) surfaceXSize;
        points[pointIdx++] = 0;
        points[pointIdx++] = (float) surfaceZSize;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        points[pointIdx++] = (float) surfaceZSize;
        texCoords[texIdx++] = 0;
        texCoords[texIdx] = 0.5f;

        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
    }

    /**
     * Creates a combined mesh including surface, walls, and bottom.
     * @param surfaceData the spectral data
     * @param fullXCount total X points in data
     * @param fullZCount total Z points in data
     * @param meshXCount mesh X resolution
     * @param meshZCount mesh Z resolution
     * @param surfaceXSize surface width in 3D units
     * @param surfaceZSize surface depth in 3D units
     * @return the created triangle mesh
     */
    protected TriangleMesh createCombinedMesh(SpectralLineSurfaceData surfaceData, int fullXCount, int fullZCount,
                                              int meshXCount, int meshZCount, double surfaceXSize, double surfaceZSize) {
        var mesh = new TriangleMesh();

        // Calculate total sizes for pre-allocation
        // Surface: meshXCount * meshZCount vertices
        // Walls: front/back (meshXCount * 4) + left/right (meshZCount * 4)
        // Bottom: 4 vertices
        int surfaceVertices = meshXCount * meshZCount;
        int wallVertices = meshXCount * 4 + meshZCount * 4;
        int bottomVertices = 4;
        int totalVertices = surfaceVertices + wallVertices + bottomVertices;

        var points = new float[totalVertices * 3];
        var texCoords = new float[totalVertices * 2];

        float xScale = (float) surfaceXSize / (meshXCount - 1);
        float zScale = (float) surfaceZSize / (meshZCount - 1);
        float yScale = (float) (SURFACE_SIZE * 0.5);

        int pointIdx = 0;
        int texIdx = 0;

        // === SURFACE VERTICES ===
        for (var z = 0; z < meshZCount; z++) {
            var srcZ = meshZCount == fullZCount ? z : z * (fullZCount - 1) / (meshZCount - 1);
            float zPos = z * zScale;
            for (var x = 0; x < meshXCount; x++) {
                var srcX = meshXCount == fullXCount ? x : x * (fullXCount - 1) / (meshXCount - 1);
                var normalizedValue = surfaceData.normalizedIntensity(srcX, srcZ);
                var scaledValue = applyScale(normalizedValue);

                points[pointIdx++] = x * xScale;
                points[pointIdx++] = scaledValue * yScale;
                points[pointIdx++] = zPos;

                texCoords[texIdx++] = scaledValue;
                texCoords[texIdx++] = 0.5f;
            }
        }

        // === WALL VERTICES ===
        int wallBaseVertex = surfaceVertices;
        int srcZBack = meshZCount == fullZCount ? meshZCount - 1 : (meshZCount - 1) * (fullZCount - 1) / (meshZCount - 1);
        int srcXRight = meshXCount == fullXCount ? meshXCount - 1 : (meshXCount - 1) * (fullXCount - 1) / (meshXCount - 1);

        // Front and back walls (along X axis)
        for (var x = 0; x < meshXCount; x++) {
            var srcX = meshXCount == fullXCount ? x : x * (fullXCount - 1) / (meshXCount - 1);
            float xPos = x * xScale;

            // Front wall - top vertex
            var frontNormalized = surfaceData.normalizedIntensity(srcX, 0);
            var frontScaled = applyScale(frontNormalized);
            points[pointIdx++] = xPos;
            points[pointIdx++] = frontScaled * yScale;
            points[pointIdx++] = 0;
            texCoords[texIdx++] = frontScaled;
            texCoords[texIdx++] = 0.5f;

            // Front wall - bottom vertex
            points[pointIdx++] = xPos;
            points[pointIdx++] = 0;
            points[pointIdx++] = 0;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;

            // Back wall - top vertex
            var backNormalized = surfaceData.normalizedIntensity(srcX, srcZBack);
            var backScaled = applyScale(backNormalized);
            points[pointIdx++] = xPos;
            points[pointIdx++] = backScaled * yScale;
            points[pointIdx++] = (float) surfaceZSize;
            texCoords[texIdx++] = backScaled;
            texCoords[texIdx++] = 0.5f;

            // Back wall - bottom vertex
            points[pointIdx++] = xPos;
            points[pointIdx++] = 0;
            points[pointIdx++] = (float) surfaceZSize;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;
        }

        // Left and right walls (along Z axis)
        for (var z = 0; z < meshZCount; z++) {
            var srcZ = meshZCount == fullZCount ? z : z * (fullZCount - 1) / (meshZCount - 1);
            float zPos = z * zScale;

            // Left wall - top vertex
            var leftNormalized = surfaceData.normalizedIntensity(0, srcZ);
            var leftScaled = applyScale(leftNormalized);
            points[pointIdx++] = 0;
            points[pointIdx++] = leftScaled * yScale;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = leftScaled;
            texCoords[texIdx++] = 0.5f;

            // Left wall - bottom vertex
            points[pointIdx++] = 0;
            points[pointIdx++] = 0;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;

            // Right wall - top vertex
            var rightNormalized = surfaceData.normalizedIntensity(srcXRight, srcZ);
            var rightScaled = applyScale(rightNormalized);
            points[pointIdx++] = (float) surfaceXSize;
            points[pointIdx++] = rightScaled * yScale;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = rightScaled;
            texCoords[texIdx++] = 0.5f;

            // Right wall - bottom vertex
            points[pointIdx++] = (float) surfaceXSize;
            points[pointIdx++] = 0;
            points[pointIdx++] = zPos;
            texCoords[texIdx++] = 0;
            texCoords[texIdx++] = 0.5f;
        }

        // === BOTTOM VERTICES ===
        int bottomBaseVertex = wallBaseVertex + wallVertices;
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        points[pointIdx++] = (float) surfaceXSize;
        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        points[pointIdx++] = (float) surfaceXSize;
        points[pointIdx++] = 0;
        points[pointIdx++] = (float) surfaceZSize;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        points[pointIdx++] = 0;
        points[pointIdx++] = 0;
        points[pointIdx++] = (float) surfaceZSize;
        texCoords[texIdx++] = 0;
        texCoords[texIdx++] = 0.5f;

        // === CALCULATE FACE COUNTS ===
        int surfaceFaces = (meshXCount - 1) * (meshZCount - 1) * 2;
        int wallFaces = ((meshXCount - 1) * 2 + (meshZCount - 1) * 2) * 2;
        int bottomFaces = 2;
        int totalFaces = surfaceFaces + wallFaces + bottomFaces;
        var faces = new int[totalFaces * 6];
        int faceIdx = 0;

        // === SURFACE FACES ===
        for (var z = 0; z < meshZCount - 1; z++) {
            int rowStart = z * meshXCount;
            int nextRowStart = (z + 1) * meshXCount;
            for (var x = 0; x < meshXCount - 1; x++) {
                int p00 = rowStart + x;
                int p10 = p00 + 1;
                int p01 = nextRowStart + x;
                int p11 = p01 + 1;

                // First triangle
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p10;
                faces[faceIdx++] = p10;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p11;

                // Second triangle
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p01;
                faces[faceIdx++] = p01;
            }
        }

        // === WALL FACES ===
        // Front wall faces
        int frontBaseIdx = wallBaseVertex;
        for (var x = 0; x < meshXCount - 1; x++) {
            int topLeft = frontBaseIdx + x * 4;
            int bottomLeft = topLeft + 1;
            int topRight = frontBaseIdx + (x + 1) * 4;
            int bottomRight = topRight + 1;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = bottomLeft;
            faces[faceIdx++] = bottomLeft;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = topRight;
            faces[faceIdx++] = topRight;
        }

        // Back wall faces
        int backBaseIdx = wallBaseVertex + 2;
        for (var x = 0; x < meshXCount - 1; x++) {
            int topLeft = backBaseIdx + x * 4;
            int bottomLeft = topLeft + 1;
            int topRight = backBaseIdx + (x + 1) * 4;
            int bottomRight = topRight + 1;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topRight;
            faces[faceIdx++] = topRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomLeft;
            faces[faceIdx++] = bottomLeft;
        }

        // Left wall faces
        int sideBaseIdx = wallBaseVertex + meshXCount * 4;
        for (var z = 0; z < meshZCount - 1; z++) {
            int topLeft = sideBaseIdx + z * 4;
            int bottomLeft = topLeft + 1;
            int topRight = sideBaseIdx + (z + 1) * 4;
            int bottomRight = topRight + 1;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topRight;
            faces[faceIdx++] = topRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomLeft;
            faces[faceIdx++] = bottomLeft;
        }

        // Right wall faces
        int rightBaseIdx = sideBaseIdx + 2;
        for (var z = 0; z < meshZCount - 1; z++) {
            int topLeft = rightBaseIdx + z * 4;
            int bottomLeft = topLeft + 1;
            int topRight = rightBaseIdx + (z + 1) * 4;
            int bottomRight = topRight + 1;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = bottomLeft;
            faces[faceIdx++] = bottomLeft;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = topLeft;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = bottomRight;
            faces[faceIdx++] = topRight;
            faces[faceIdx++] = topRight;
        }

        // === BOTTOM FACES ===
        faces[faceIdx++] = bottomBaseVertex;
        faces[faceIdx++] = bottomBaseVertex;
        faces[faceIdx++] = bottomBaseVertex + 1;
        faces[faceIdx++] = bottomBaseVertex + 1;
        faces[faceIdx++] = bottomBaseVertex + 2;
        faces[faceIdx++] = bottomBaseVertex + 2;

        faces[faceIdx++] = bottomBaseVertex;
        faces[faceIdx++] = bottomBaseVertex;
        faces[faceIdx++] = bottomBaseVertex + 2;
        faces[faceIdx++] = bottomBaseVertex + 2;
        faces[faceIdx++] = bottomBaseVertex + 3;
        faces[faceIdx++] = bottomBaseVertex + 3;

        // Bulk add all data at once
        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
        mesh.getFaces().setAll(faces);

        return mesh;
    }

    /**
     * Applies the current intensity scale transformation to a normalized value.
     * @param normalizedValue the normalized intensity value in [0, 1]
     * @return the scaled value
     */
    protected float applyScale(float normalizedValue) {
        if (normalizedValue < 0 || normalizedValue > 1) {
            throw new IllegalArgumentException("Normalized value must be in [0, 1], got: " + normalizedValue);
        }
        return switch (scale) {
            case LINEAR -> normalizedValue;
            case SQUARE -> normalizedValue * normalizedValue;
            case LOG2 -> {
                // log2(1 + x) where x ∈ [0,1] → output ∈ [0, 1]
                var logValue = Math.log1p(normalizedValue) / Math.log(2);
                yield (float) logValue;
            }
            case LOG10 -> {
                // log10(1 + 9*x) where x ∈ [0,1] → output ∈ [0, 1]
                // Stronger compression than log2
                var logValue = Math.log10(1 + 9 * normalizedValue);
                yield (float) logValue;
            }
        };
    }

    /**
     * Creates the material with color gradient texture for the surface.
     * @return the material
     */
    protected PhongMaterial createMaterial() {
        var width = 256;
        var height = 1;
        var colorMapImage = new WritableImage(width, height);
        var writer = colorMapImage.getPixelWriter();

        for (var x = 0; x < width; x++) {
            var t = (double) x / (width - 1);
            var color = intensityToColor(t);
            writer.setColor(x, 0, color);
        }

        var material = new PhongMaterial();
        material.setDiffuseMap(colorMapImage);
        material.setSpecularColor(Color.rgb(100, 100, 100));
        return material;
    }

    /**
     * Converts a normalized intensity value to a color using the heat map gradient.
     * @param t the normalized intensity in [0, 1]
     * @return the corresponding color
     */
    protected Color intensityToColor(double t) {
        if (t < 0.25) {
            var ratio = t / 0.25;
            return Color.color(0, ratio, 1 - ratio * 0.5);
        } else if (t < 0.5) {
            var ratio = (t - 0.25) / 0.25;
            return Color.color(ratio, 1, 0.5 - ratio * 0.5);
        } else if (t < 0.75) {
            var ratio = (t - 0.5) / 0.25;
            return Color.color(1, 1 - ratio, 0);
        } else {
            var ratio = (t - 0.75) / 0.25;
            return Color.color(1, 0, ratio * 0.5);
        }
    }

    /**
     * Creates a cylindrical axis line with the given length and material.
     * @param length the length of the axis line
     * @param material the material to use
     * @return a group containing the axis line
     */
    protected Group createAxisLine(double length, PhongMaterial material) {
        var shaft = new Cylinder(1.5, length);
        shaft.setMaterial(material);
        return new Group(shaft);
    }

    /**
     * Creates a 3D text label with the specified text and color.
     * @param text the label text
     * @param color the text color
     * @return the created text node
     */
    protected Text create3DLabel(String text, Color color) {
        var label = new Text(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setFill(color);
        return label;
    }

    /**
     * Creates an axis info label showing axis name and value with a color indicator.
     * @param axisName the axis name
     * @param value the axis value text
     * @param color the color indicator
     * @return an HBox containing the label
     */
    protected HBox createAxisInfoLabel(String axisName, String value, Color color) {
        var colorBox = new Box(12, 12, 1);
        colorBox.setMaterial(new PhongMaterial(color));

        var nameLabel = new Label(axisName);
        nameLabel.setFont(Font.font(nameLabel.getFont().getFamily(), FontWeight.BOLD, 11));

        var valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: #333333;");

        var box = new HBox(5, colorBox, nameLabel, valueLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Creates the legend panel with color bar and labels.
     * @return the legend panel
     */
    protected VBox createLegendPanel() {
        legendTitleLabel = new Label();
        legendTitleLabel.setFont(Font.font(legendTitleLabel.getFont().getFamily(), FontWeight.BOLD, 11));

        var colorBar = createColorBar();

        var maxLabel = new Label(String.format(Locale.US, "%.0f", getMaxIntensity()));
        maxLabel.setStyle("-fx-text-fill: #333333; -fx-font-size: 9px;");

        var minLabel = new Label(String.format(Locale.US, "%.0f", getMinIntensity()));
        minLabel.setStyle("-fx-text-fill: #333333; -fx-font-size: 9px;");

        var labelsBox = new VBox();
        labelsBox.setAlignment(Pos.CENTER_RIGHT);
        var spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        labelsBox.getChildren().addAll(maxLabel, spacer, minLabel);
        labelsBox.setMinHeight(150);
        labelsBox.setMaxHeight(150);

        var barWithLabels = new HBox(3, colorBar, labelsBox);
        barWithLabels.setAlignment(Pos.CENTER);

        legendHighLabel = new Label();
        legendHighLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 9px;");

        legendLowLabel = new Label();
        legendLowLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 9px;");

        legendInterpretLabel = new Label();
        legendInterpretLabel.setWrapText(true);
        legendInterpretLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");
        legendInterpretLabel.setMaxWidth(140);

        updateLegendLabels();

        var panel = new VBox(5, legendTitleLabel, legendHighLabel, barWithLabels, legendLowLabel,
                new Separator(), legendInterpretLabel);
        panel.setPadding(new Insets(8));
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 0 1;");
        panel.setMinWidth(160);
        panel.setMaxWidth(160);

        return panel;
    }

    /** Updates the legend labels based on current scale mode. */
    protected void updateLegendLabels() {
        var isLog = scale != IntensityScale.LINEAR;
        var titleKey = isLog ? "legend.intensity.log" : "legend.intensity";
        var highKey = isLog ? "legend.high.log" : "legend.high";
        var lowKey = isLog ? "legend.low.log" : "legend.low";

        legendTitleLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", titleKey));
        legendHighLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", highKey));
        legendLowLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", lowKey));
        var interpretationText = I18N.string(JSolEx.class, "spectral-surface-3d", getInterpretationKey());
        legendInterpretLabel.setText(interpretationText);
        legendInterpretLabel.setTooltip(new Tooltip(interpretationText));
    }

    /**
     * Creates a vertical color bar canvas for the legend.
     * @return the canvas
     */
    protected Canvas createColorBar() {
        var width = 20;
        var height = 150;
        var canvas = new Canvas(width, height);
        var gc = canvas.getGraphicsContext2D();

        for (var y = 0; y < height; y++) {
            var t = 1.0 - (double) y / (height - 1);
            gc.setFill(intensityToColor(t));
            gc.fillRect(0, y, width, 1);
        }

        gc.setStroke(Color.rgb(100, 100, 100));
        gc.strokeRect(0, 0, width, height);

        return canvas;
    }

    /**
     * Sets up mouse handlers for rotation and zoom.
     * @param subScene the subscene to attach handlers to
     */
    protected void setupMouseHandlers(SubScene subScene) {
        subScene.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
        });

        subScene.setOnMouseDragged(event -> {
            var dx = event.getSceneX() - anchorX;
            var dy = event.getSceneY() - anchorY;
            rotateY.setAngle(anchorAngleY + dx * 0.5);
            rotateX.setAngle(anchorAngleX - dy * 0.5);
        });

        subScene.setOnScroll(event -> {
            var delta = event.getDeltaY();
            var currentZ = cameraTranslate.getZ();
            var newZ = currentZ + delta * 2;
            newZ = Math.max(-MAX_DISTANCE, Math.min(-MIN_DISTANCE, newZ));
            cameraTranslate.setZ(newZ);
        });
    }

    /** Resets the camera to the initial view position. */
    protected void resetView() {
        rotateX.setAngle(-30);
        rotateY.setAngle(30);
        cameraTranslate.setZ(-INITIAL_DISTANCE);
    }

    /**
     * Shows a dialog asking whether to export the graph only or the entire frame.
     * @return true if the user confirmed, false if cancelled
     */
    protected boolean askExportScope() {
        var graphOnly = I18N.string(JSolEx.class, "spectral-surface-3d", "export.scope.graph");
        var wholeFrame = I18N.string(JSolEx.class, "spectral-surface-3d", "export.scope.frame");

        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18N.string(JSolEx.class, "spectral-surface-3d", "export.scope.title"));
        alert.setHeaderText(I18N.string(JSolEx.class, "spectral-surface-3d", "export.scope.header"));
        alert.setContentText(null);

        var graphButton = new ButtonType(graphOnly);
        var frameButton = new ButtonType(wholeFrame);
        var cancelButton = ButtonType.CANCEL;

        alert.getButtonTypes().setAll(graphButton, frameButton, cancelButton);

        var result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancelButton) {
            return false;
        }
        exportGraphOnly = (result.get() == graphButton);
        return true;
    }

    /**
     * Exports the current view to a PNG file.
     * @param defaultFileName the default filename for the save dialog
     */
    protected void exportToPng(String defaultFileName) {
        if (!askExportScope()) {
            return;
        }

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "spectral-surface-3d", "export.title"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG", "*.png")
        );
        fileChooser.setInitialFileName(defaultFileName);

        var stage = (Stage) getScene().getWindow();
        var file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            var nodeToSnapshot = exportGraphOnly ? graphPane : this;
            var snapshot = nodeToSnapshot.snapshot(null, null);
            saveImage(snapshot, file);
        }
    }

    /**
     * Exports the view as a video animation.
     * @param defaultFileName the default filename
     * @param onDisableControls callback to disable controls during export
     * @param onEnableControls callback to re-enable controls after export
     */
    protected void exportToVideo(String defaultFileName, Runnable onDisableControls, Runnable onEnableControls) {
        if (!askExportScope()) {
            return;
        }

        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video.title"));
        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));

        var stage = (Stage) getScene().getWindow();
        var file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        var animationFormats = Configuration.getInstance().getAnimationFormats();
        if (animationFormats.isEmpty()) {
            animationFormats = EnumSet.of(AnimationFormat.MP4);
        }

        var basePath = file.getAbsolutePath();
        var lastDot = basePath.lastIndexOf('.');
        var lastSep = Math.max(basePath.lastIndexOf('/'), basePath.lastIndexOf(File.separatorChar));
        if (lastDot > lastSep && lastDot > 0) {
            basePath = basePath.substring(0, lastDot);
        }

        var existingFiles = new ArrayList<File>();
        for (var format : animationFormats) {
            var outputFile = new File(basePath + "." + format.name().toLowerCase());
            if (outputFile.exists()) {
                existingFiles.add(outputFile);
            }
        }

        if (!existingFiles.isEmpty()) {
            var fileNames = existingFiles.stream()
                    .map(File::getName)
                    .collect(Collectors.joining(", "));
            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video.overwrite.title"));
            alert.setHeaderText(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video.overwrite.header"));
            alert.setContentText(fileNames);
            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        if (onDisableControls != null) {
            onDisableControls.run();
        }

        var frameCount = getVideoFrameCount();

        var cancelled = new AtomicBoolean(false);

        var progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        var progressLabel = new Label(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video.progress"));
        var cancelButton = new Button(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video.cancel"));

        var progressBox = new VBox(10, progressLabel, progressBar, cancelButton);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(20));

        var progressStage = new Stage();
        progressStage.initOwner(stage);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setTitle(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video.title"));
        progressStage.setScene(new Scene(progressBox));
        progressStage.setResizable(false);
        progressStage.setOnCloseRequest(e -> cancelled.set(true));
        cancelButton.setOnAction(e -> cancelled.set(true));
        progressStage.show();

        var formats = animationFormats;
        var finalBasePath = basePath;

        new Thread(() -> {
            List<File> outputFiles = null;
            try {
                Consumer<Double> progressCallback = progress -> Platform.runLater(() -> {
                    progressBar.setProgress(progress);
                    progressLabel.setText((int) (progress * 100) + "%");
                });

                outputFiles = VideoEncoder.encodeToMultipleFormats(
                        finalBasePath,
                        formats,
                        frameCount,
                        20,
                        50,
                        idx -> cancelled.get() ? null : captureVideoFrame(idx, frameCount),
                        progressCallback
                );

                if (cancelled.get() && outputFiles != null) {
                    for (var outputFile : outputFiles) {
                        if (outputFile.exists()) {
                            outputFile.delete();
                        }
                    }
                }
            } catch (IOException e) {
                if (!cancelled.get()) {
                    Platform.runLater(() -> {
                        var alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Export Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    });
                }
            } finally {
                Platform.runLater(() -> {
                    progressStage.close();
                    if (onEnableControls != null) {
                        onEnableControls.run();
                    }
                });
            }
        }).start();
    }

    private BufferedImage captureVideoFrame(int frameIndex, int totalFrames) {
        var result = new AtomicReference<BufferedImage>();
        var latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            setVideoFrameIndex(frameIndex);

            Platform.runLater(() -> {
                var nodeToSnapshot = exportGraphOnly ? graphPane : this;
                var snapshot = nodeToSnapshot.snapshot(null, null);
                var bufferedImage = new BufferedImage(
                        (int) snapshot.getWidth(),
                        (int) snapshot.getHeight(),
                        BufferedImage.TYPE_INT_RGB
                );
                for (var y = 0; y < snapshot.getHeight(); y++) {
                    for (var x = 0; x < snapshot.getWidth(); x++) {
                        bufferedImage.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    }
                }
                result.set(bufferedImage);
                latch.countDown();
            });
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    /**
     * Saves a WritableImage to a PNG file.
     * @param image the image to save
     * @param file the destination file
     */
    protected void saveImage(WritableImage image, File file) {
        var bufferedImage = new BufferedImage(
                (int) image.getWidth(),
                (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                bufferedImage.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        try {
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            // Silently ignore
        }
    }

    /**
     * Creates the common control panel with reset, export, scale, and rotation controls.
     * @param additionalControls additional controls to include in the panel
     * @return the control panel
     */
    protected HBox createCommonControlPanel(Node... additionalControls) {
        var resetButton = createStyledButton(I18N.string(JSolEx.class, "spectral-surface-3d", "reset.view"));
        resetButton.setOnAction(e -> resetView());

        var exportButton = createStyledButton(I18N.string(JSolEx.class, "spectral-surface-3d", "export.png"));
        exportButton.setOnAction(e -> onExportPng());

        var scaleLabel = createStyledLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "scale"));
        var scaleCombo = new ComboBox<IntensityScale>();
        scaleCombo.getItems().addAll(IntensityScale.values());
        scaleCombo.getSelectionModel().select(scale);
        scaleCombo.setOnAction(e -> {
            scale = scaleCombo.getSelectionModel().getSelectedItem();
            buildSurface();
            updateLegendLabels();
        });
        var scaleBox = new HBox(3, scaleLabel, scaleCombo);
        scaleBox.setAlignment(Pos.CENTER_LEFT);

        var elevationSlider = new Slider(-90, 90, -30);
        elevationSlider.setPrefWidth(100);
        elevationSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                rotateX.setAngle(newVal.doubleValue()));

        var elevationLabel = createStyledLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "elevation"));
        var elevationBox = new HBox(3, elevationLabel, elevationSlider);
        elevationBox.setAlignment(Pos.CENTER_LEFT);

        var rotationSlider = new Slider(-180, 180, 30);
        rotationSlider.setPrefWidth(100);
        rotationSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                rotateY.setAngle(newVal.doubleValue()));

        var rotationLabel = createStyledLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "rotation"));
        var rotationBox = new HBox(3, rotationLabel, rotationSlider);
        rotationBox.setAlignment(Pos.CENTER_LEFT);

        var controls = new ArrayList<Node>();
        controls.add(resetButton);
        controls.add(exportButton);

        if (supportsVideoExport()) {
            var exportVideoButton = createStyledButton(I18N.string(JSolEx.class, "spectral-surface-3d", "export.video"));
            exportVideoButton.setOnAction(e -> onExportVideo());
            controls.add(exportVideoButton);
        }

        controls.add(new Separator(Orientation.VERTICAL));
        controls.add(scaleBox);

        controls.addAll(List.of(additionalControls));

        controls.add(new Separator(Orientation.VERTICAL));
        controls.add(elevationBox);
        controls.add(rotationBox);

        var buttonBox = new HBox(8, controls.toArray(new Node[0]));
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(6));
        buttonBox.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        return buttonBox;
    }

    /**
     * Creates a styled button with the viewer's button style.
     * @param text the button text
     * @return the styled button
     */
    protected static Button createStyledButton(String text) {
        var button = new Button(text);
        button.getStyleClass().add("image-viewer-button");
        button.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
        return button;
    }

    /**
     * Creates a styled label with minimum sizing.
     * @param text the label text
     * @return the styled label
     */
    protected static Label createStyledLabel(String text) {
        var label = new Label(text);
        label.setMinSize(Label.USE_PREF_SIZE, Label.USE_PREF_SIZE);
        return label;
    }

    /** Called when the PNG export button is clicked. */
    protected abstract void onExportPng();

    /** Called when the video export button is clicked. */
    protected abstract void onExportVideo();

    /** Available intensity scale modes for the 3D surface. */
    protected enum IntensityScale {
        /** Linear mapping (no transformation). */
        LINEAR,
        /** Square mapping (emphasizes high values). */
        SQUARE,
        /** Logarithmic base-2 mapping. */
        LOG2,
        /** Logarithmic base-10 mapping (stronger compression). */
        LOG10;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "spectral-surface-3d", "scale." + name().toLowerCase());
        }
    }
}
