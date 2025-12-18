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

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralEvolution4DData;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A 4D viewer for spectral evolution data with animation support.
 * Extends the base 3D viewer with slicing and animation capabilities.
 */
public class SpectralEvolution4DViewer extends AbstractSpectral3DViewer {
    private final SpectralEvolution4DData data;
    private final Ellipse ellipse;

    private double currentSliceFraction;
    private SliceMode currentSliceMode = SliceMode.WAVELENGTH;
    private AnimationTimer animationTimer;
    private Slider sliceSlider;
    private Label slicePositionLabel;
    private Button playButton;
    private ComboBox<SliceMode> sliceModeCombo;
    private boolean animationMode = false;
    private int animationResolution;
    private static final int INITIAL_ANIMATION_RESOLUTION = 512;
    private static final int MIN_ANIMATION_RESOLUTION = 128;
    private static final double TARGET_FPS = 15.0;
    private static final long TARGET_FRAME_NANOS = (long) (1_000_000_000L / TARGET_FPS);
    private static final double ANIMATION_DURATION_SECONDS = 10.0;

    private final Label sliceOverlayLabel;
    private final PauseTransition sliderDebounce;
    private static final int SLIDER_DEBOUNCE_MS = 150;

    private final VBox profileOverlay;
    private final Canvas profileCanvas;
    private final Label overlayTitleLabel;
    private static final int PROFILE_WIDTH = 200;
    private static final int PROFILE_HEIGHT = 100;
    private double[] cachedSpectralProfile;
    private final AtomicBoolean buildingMesh = new AtomicBoolean(false);
    private WritableImage cachedProfileBackground;
    private WritableImage cachedDiskBackground;

    private Group clickMarker;
    private int clickedFrameIndex = -1;
    private int clickedSlitIndex = -1;
    private boolean settingSliderProgrammatically = false;
    private SpectralCube4DHelpOverlay helpOverlay;
    private Node helpButton;

    /**
     * Creates a new 4D spectral evolution viewer.
     *
     * @param data the spectral evolution data to visualize
     * @param ellipse the ellipse representing the solar disk boundary, may be null
     */
    public SpectralEvolution4DViewer(SpectralEvolution4DData data, Ellipse ellipse) {
        super();
        this.data = data;
        this.ellipse = ellipse;
        this.currentSliceFraction = data.getCenterWavelengthFraction();
        this.sliderDebounce = new PauseTransition(Duration.millis(SLIDER_DEBOUNCE_MS));
        this.sliderDebounce.setOnFinished(e -> buildSurface());

        sliceOverlayLabel = createSliceOverlayLabel();
        graphPane.getChildren().add(sliceOverlayLabel);
        StackPane.setAlignment(sliceOverlayLabel, Pos.TOP_LEFT);
        StackPane.setMargin(sliceOverlayLabel, new Insets(10, 0, 0, 10));

        profileCanvas = new Canvas(PROFILE_WIDTH, PROFILE_HEIGHT);
        overlayTitleLabel = new Label();
        profileOverlay = createProfileOverlay();
        graphPane.getChildren().add(profileOverlay);
        StackPane.setAlignment(profileOverlay, Pos.TOP_RIGHT);
        StackPane.setMargin(profileOverlay, new Insets(10, 10, 0, 0));

        initClickMarker();

        initializeView();
        addHelpOverlay();
        updateSliceOverlay();
        updateProfileOverlay();
    }

    private void addHelpOverlay() {
        helpOverlay = new SpectralCube4DHelpOverlay();
        helpOverlay.setMouseTransparent(true);
        graphPane.getChildren().add(helpOverlay);
        helpButton = helpOverlay.createStandaloneButton();
        graphPane.getChildren().add(helpButton);
    }

    @Override
    protected void beforeExport() {
        if (helpOverlay != null) {
            helpOverlay.setVisible(false);
        }
        if (helpButton != null) {
            helpButton.setVisible(false);
        }
    }

    @Override
    protected void afterExport() {
        if (helpOverlay != null) {
            helpOverlay.setVisible(true);
        }
        if (helpButton != null) {
            helpButton.setVisible(true);
        }
    }

    private Label createSliceOverlayLabel() {
        var label = new Label();
        label.setStyle("-fx-background-color: rgba(255, 165, 0, 0.85); " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 6 12 6 12; " +
                "-fx-background-radius: 4;");
        return label;
    }

    private VBox createProfileOverlay() {
        overlayTitleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");

        profileCanvas.setOnMouseClicked(this::handleProfileCanvasClick);

        var box = new VBox(4, overlayTitleLabel, profileCanvas);
        box.setStyle("-fx-background-color: transparent; -fx-padding: 8;");
        box.setAlignment(Pos.TOP_RIGHT);
        box.setPickOnBounds(false);
        box.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        return box;
    }

    private void handleProfileCanvasClick(MouseEvent event) {
        if (currentSliceMode == SliceMode.WAVELENGTH) {
            double margin = 5;
            var plotW = PROFILE_WIDTH - 2 * margin;
            var clickX = event.getX();
            var fraction = (clickX - margin) / plotW;
            fraction = Math.max(0, Math.min(1, fraction));
            sliceSlider.setValue(fraction);
        } else if (ellipse != null) {
            var fraction = computeDiskClickFraction(event.getX(), event.getY());
            if (fraction >= 0 && fraction <= 1) {
                sliceSlider.setValue(fraction);
            }
        }
    }

    private double computeDiskClickFraction(double clickX, double clickY) {
        double margin = 5;
        var availableW = PROFILE_WIDTH - 2 * margin;
        var availableH = PROFILE_HEIGHT - 2 * margin;

        var boundingBox = ellipse.boundingBox();
        var ellipseMinX = boundingBox.a();
        var ellipseMaxX = boundingBox.b();
        var ellipseMinY = boundingBox.c();
        var ellipseMaxY = boundingBox.d();

        var slitPositions = data.slitPositions();
        var frameIndices = data.frameIndices();
        double dataMinX = frameIndices[0];
        double dataMaxX = frameIndices[frameIndices.length - 1];
        double dataMinY = slitPositions[0];
        double dataMaxY = slitPositions[slitPositions.length - 1];

        var totalMinX = Math.min(ellipseMinX, dataMinX);
        var totalMaxX = Math.max(ellipseMaxX, dataMaxX);
        var totalMinY = Math.min(ellipseMinY, dataMinY);
        var totalMaxY = Math.max(ellipseMaxY, dataMaxY);
        var totalOrigW = totalMaxX - totalMinX;
        var totalOrigH = totalMaxY - totalMinY;

        var scale = Math.min(availableW / totalOrigW, availableH / totalOrigH);

        var offsetX = margin + (availableW - totalOrigW * scale) / 2;
        var offsetY = margin + (availableH - totalOrigH * scale) / 2;

        if (currentSliceMode == SliceMode.SLIT) {
            var dataY = (clickY - offsetY) / scale + totalMinY;
            return (dataY - dataMinY) / (dataMaxY - dataMinY);
        } else {
            var dataX = (clickX - offsetX) / scale + totalMinX;
            return (dataX - dataMinX) / (dataMaxX - dataMinX);
        }
    }

    private void updateProfileOverlay() {
        if (currentSliceMode == SliceMode.WAVELENGTH) {
            profileOverlay.setVisible(true);
            overlayTitleLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", "profile.overlay.title"));
            if (cachedSpectralProfile == null) {
                cachedSpectralProfile = data.getAverageSpectralProfile();
            }
            if (cachedProfileBackground == null) {
                cachedProfileBackground = renderProfileBackground(cachedSpectralProfile);
            }
            drawProfileWithMarker(cachedProfileBackground, currentSliceFraction);
        } else if (ellipse != null) {
            profileOverlay.setVisible(true);
            overlayTitleLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", "disk.overlay.title"));
            if (cachedDiskBackground == null) {
                cachedDiskBackground = renderDiskBackground();
            }
            drawDiskWithSlice(cachedDiskBackground, currentSliceMode, currentSliceFraction);
        } else {
            profileOverlay.setVisible(false);
        }
    }

    private WritableImage renderProfileBackground(double[] profile) {
        var tempCanvas = new Canvas(PROFILE_WIDTH, PROFILE_HEIGHT);
        var gc = tempCanvas.getGraphicsContext2D();
        var w = tempCanvas.getWidth();
        var h = tempCanvas.getHeight();

        double margin = 5;
        var plotW = w - 2 * margin;
        var plotH = h - 2 * margin;

        var minVal = Double.MAX_VALUE;
        var maxVal = Double.MIN_VALUE;
        for (var v : profile) {
            minVal = Math.min(minVal, v);
            maxVal = Math.max(maxVal, v);
        }
        var range = maxVal - minVal;
        if (range == 0) {
            range = 1;
        }

        gc.setStroke(Color.CYAN);
        gc.setLineWidth(1.5);
        gc.beginPath();
        for (var i = 0; i < profile.length; i++) {
            var x = margin + (double) i / (profile.length - 1) * plotW;
            var y = margin + plotH - ((profile[i] - minVal) / range) * plotH;
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();

        var params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return tempCanvas.snapshot(params, null);
    }

    private void drawProfileWithMarker(WritableImage background, double markerFraction) {
        var gc = profileCanvas.getGraphicsContext2D();
        var w = profileCanvas.getWidth();
        var h = profileCanvas.getHeight();

        gc.clearRect(0, 0, w, h);
        gc.drawImage(background, 0, 0);

        double margin = 5;
        var plotW = w - 2 * margin;
        var plotH = h - 2 * margin;

        var markerX = margin + markerFraction * plotW;
        gc.setStroke(Color.ORANGE);
        gc.setLineWidth(2);
        gc.strokeLine(markerX, margin, markerX, margin + plotH);
    }

    private WritableImage renderDiskBackground() {
        var tempCanvas = new Canvas(PROFILE_WIDTH, PROFILE_HEIGHT);
        var gc = tempCanvas.getGraphicsContext2D();
        var w = tempCanvas.getWidth();
        var h = tempCanvas.getHeight();

        double margin = 5;
        var availableW = w - 2 * margin;
        var availableH = h - 2 * margin;

        var boundingBox = ellipse.boundingBox();
        var ellipseMinX = boundingBox.a();
        var ellipseMaxX = boundingBox.b();
        var ellipseMinY = boundingBox.c();
        var ellipseMaxY = boundingBox.d();
        var ellipseOrigW = ellipseMaxX - ellipseMinX;
        var ellipseOrigH = ellipseMaxY - ellipseMinY;

        var slitPositions = data.slitPositions();
        var frameIndices = data.frameIndices();
        double dataMinX = frameIndices[0];
        double dataMaxX = frameIndices[frameIndices.length - 1];
        double dataMinY = slitPositions[0];
        double dataMaxY = slitPositions[slitPositions.length - 1];

        var totalMinX = Math.min(ellipseMinX, dataMinX);
        var totalMaxX = Math.max(ellipseMaxX, dataMaxX);
        var totalMinY = Math.min(ellipseMinY, dataMinY);
        var totalMaxY = Math.max(ellipseMaxY, dataMaxY);
        var totalOrigW = totalMaxX - totalMinX;
        var totalOrigH = totalMaxY - totalMinY;

        var scale = Math.min(availableW / totalOrigW, availableH / totalOrigH);

        var offsetX = margin + (availableW - totalOrigW * scale) / 2;
        var offsetY = margin + (availableH - totalOrigH * scale) / 2;

        var ellipseDrawX = offsetX + (ellipseMinX - totalMinX) * scale;
        var ellipseDrawY = offsetY + (ellipseMinY - totalMinY) * scale;
        var ellipseDrawW = ellipseOrigW * scale;
        var ellipseDrawH = ellipseOrigH * scale;

        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(2);
        gc.strokeOval(ellipseDrawX, ellipseDrawY, ellipseDrawW, ellipseDrawH);

        var params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return tempCanvas.snapshot(params, null);
    }

    private void drawDiskWithSlice(WritableImage background, SliceMode mode, double sliceFraction) {
        var gc = profileCanvas.getGraphicsContext2D();
        var w = profileCanvas.getWidth();
        var h = profileCanvas.getHeight();

        gc.clearRect(0, 0, w, h);
        gc.drawImage(background, 0, 0);

        double margin = 5;
        var availableW = w - 2 * margin;
        var availableH = h - 2 * margin;

        var boundingBox = ellipse.boundingBox();
        var ellipseMinX = boundingBox.a();
        var ellipseMaxX = boundingBox.b();
        var ellipseMinY = boundingBox.c();
        var ellipseMaxY = boundingBox.d();

        var slitPositions = data.slitPositions();
        var frameIndices = data.frameIndices();
        double dataMinX = frameIndices[0];
        double dataMaxX = frameIndices[frameIndices.length - 1];
        double dataMinY = slitPositions[0];
        double dataMaxY = slitPositions[slitPositions.length - 1];

        var totalMinX = Math.min(ellipseMinX, dataMinX);
        var totalMaxX = Math.max(ellipseMaxX, dataMaxX);
        var totalMinY = Math.min(ellipseMinY, dataMinY);
        var totalMaxY = Math.max(ellipseMaxY, dataMaxY);
        var totalOrigW = totalMaxX - totalMinX;
        var totalOrigH = totalMaxY - totalMinY;

        var scale = Math.min(availableW / totalOrigW, availableH / totalOrigH);

        var offsetX = margin + (availableW - totalOrigW * scale) / 2;
        var offsetY = margin + (availableH - totalOrigH * scale) / 2;

        gc.setStroke(Color.ORANGE);
        gc.setLineWidth(2);
        if (mode == SliceMode.SLIT) {
            var slitIndex = data.getSliceIndex(SpectralEvolution4DData.SliceMode.SLIT, sliceFraction);
            double slitPos = slitPositions[slitIndex];
            var lineY = offsetY + (slitPos - totalMinY) * scale;
            gc.strokeLine(0, lineY, w, lineY);
        } else if (mode == SliceMode.FRAME) {
            var frameIndex = data.getSliceIndex(SpectralEvolution4DData.SliceMode.FRAME, sliceFraction);
            double framePos = frameIndices[frameIndex];
            var lineX = offsetX + (framePos - totalMinX) * scale;
            gc.strokeLine(lineX, 0, lineX, h);
        }
    }

    private void initClickMarker() {
        // Semi-transparent red for better visibility when crossing the volume
        var material = new PhongMaterial(Color.rgb(255, 0, 0, 0.7));
        material.setSpecularColor(Color.WHITE);

        // Create arrow head (cone) - tip at origin, body extends in +Y
        var cone = createCone(3, 10, 8);
        cone.setMaterial(material);

        // Create arrow shaft (cylinder) - long enough to extend well above the surface
        var shaft = new Cylinder(1, 80);
        shaft.setMaterial(material);
        shaft.setTranslateY(10 + 40); // Position after cone (cone height + half shaft)

        // Group the arrow parts - tip at origin, shaft extends in +Y direction
        var arrow = new Group(cone, shaft);

        // In surfaceGroup, Y is flipped (Scale 1,-1,1), so +Y in local = down visually
        // We want arrow coming from above, so rotate to tilt the shaft
        arrow.getTransforms().addAll(
            new Rotate(35, Rotate.X_AXIS),   // Tilt so shaft goes back and up
            new Rotate(-45, Rotate.Y_AXIS)   // Rotate around vertical
        );

        clickMarker = new Group(arrow);
        clickMarker.setVisible(false);
        // Will be added to surfaceGroup after it's created in buildSurface
    }

    private MeshView createCone(double radius, double height, int divisions) {
        var mesh = new TriangleMesh();

        // Vertices: center top point + bottom circle points + center bottom point
        var points = new float[(divisions + 2) * 3];

        // Top point (tip of cone)
        points[0] = 0;
        points[1] = 0;
        points[2] = 0;

        // Bottom circle points
        for (var i = 0; i < divisions; i++) {
            var angle = 2 * Math.PI * i / divisions;
            var idx = (i + 1) * 3;
            points[idx] = (float) (radius * Math.cos(angle));
            points[idx + 1] = (float) height;
            points[idx + 2] = (float) (radius * Math.sin(angle));
        }

        // Center bottom point
        var centerIdx = (divisions + 1) * 3;
        points[centerIdx] = 0;
        points[centerIdx + 1] = (float) height;
        points[centerIdx + 2] = 0;

        mesh.getPoints().addAll(points);

        // Texture coordinates (simple)
        mesh.getTexCoords().addAll(0, 0);

        // Faces: cone surface + bottom cap
        var faces = new int[divisions * 2 * 6];
        var faceIdx = 0;

        // Cone surface triangles
        for (var i = 0; i < divisions; i++) {
            var next = (i + 1) % divisions;
            // Triangle from tip to two adjacent bottom points
            faces[faceIdx++] = 0; faces[faceIdx++] = 0;
            faces[faceIdx++] = i + 1; faces[faceIdx++] = 0;
            faces[faceIdx++] = next + 1; faces[faceIdx++] = 0;
        }

        // Bottom cap triangles
        var centerPoint = divisions + 1;
        for (var i = 0; i < divisions; i++) {
            var next = (i + 1) % divisions;
            faces[faceIdx++] = centerPoint; faces[faceIdx++] = 0;
            faces[faceIdx++] = next + 1; faces[faceIdx++] = 0;
            faces[faceIdx++] = i + 1; faces[faceIdx++] = 0;
        }

        mesh.getFaces().addAll(faces);

        return new MeshView(mesh);
    }

    /**
     * Brings this viewer window to the front and requests focus.
     */
    public void bringToFront() {
        var scene = getScene();
        if (scene != null && scene.getWindow() != null) {
            scene.getWindow().requestFocus();
        }
    }

    /**
     * Sets the viewer position based on a click location from another view.
     * This method updates the slice mode to wavelength and positions the viewer
     * at the specified frame, slit position, and wavelength offset.
     *
     * @param frameNumber the frame number that was clicked
     * @param slitPosition the slit position that was clicked
     * @param pixelShift the pixel shift from the center wavelength
     */
    public void setPositionFromClick(int frameNumber, int slitPosition, double pixelShift) {
        var frameIndex = findClosestIndex(data.frameIndices(), frameNumber);
        var slitIndex = findClosestIndex(data.slitPositions(), slitPosition);

        clickedFrameIndex = frameIndex;
        clickedSlitIndex = slitIndex;

        if (currentSliceMode != SliceMode.WAVELENGTH) {
            currentSliceMode = SliceMode.WAVELENGTH;
            sliceModeCombo.getSelectionModel().select(SliceMode.WAVELENGTH);
            invalidateMesh();
            rebuildAxes();
            updateLegendLabels();
        }

        var wavelengthFraction = data.getWavelengthFractionForPixelShift(pixelShift);

        settingSliderProgrammatically = true;
        sliceSlider.setValue(wavelengthFraction);
        settingSliderProgrammatically = false;

        // Rebuild surface and update marker
        buildSurface();
        updateSlicePositionLabel();
        updateSliceOverlay();
        updateProfileOverlay();
        updateClickMarkerPosition();
    }

    private int findClosestIndex(int[] array, int value) {
        var closest = 0;
        var minDiff = Math.abs(array[0] - value);
        for (var i = 1; i < array.length; i++) {
            var diff = Math.abs(array[i] - value);
            if (diff < minDiff) {
                minDiff = diff;
                closest = i;
            }
        }
        return closest;
    }

    private void clearClickMarker() {
        clickedFrameIndex = -1;
        clickedSlitIndex = -1;
        if (clickMarker != null) {
            clickMarker.setVisible(false);
        }
    }

    private void updateClickMarkerPosition() {
        if (clickedFrameIndex < 0 || clickedSlitIndex < 0) {
            clickMarker.setVisible(false);
            return;
        }

        var frameIndices = data.frameIndices();
        var slitPositions = data.slitPositions();

        double surfaceX;
        double surfaceZ;
        double intensity;

        var surfaceData = data.toSurfaceData(currentSliceMode.toDataSliceMode(), currentSliceFraction);

        switch (currentSliceMode) {
            case SLIT -> {
                surfaceX = (double) clickedFrameIndex / (frameIndices.length - 1);
                surfaceZ = 0.5;
                var xIdx = Math.min(clickedFrameIndex, surfaceData.xAxisCount() - 1);
                var zIdx = surfaceData.wavelengthCount() / 2;
                intensity = surfaceData.normalizedIntensity(xIdx, zIdx);
            }
            case FRAME -> {
                surfaceX = (double) clickedSlitIndex / (slitPositions.length - 1);
                surfaceZ = 0.5;
                var xIdx = Math.min(clickedSlitIndex, surfaceData.xAxisCount() - 1);
                var zIdx = surfaceData.wavelengthCount() / 2;
                intensity = surfaceData.normalizedIntensity(xIdx, zIdx);
            }
            case WAVELENGTH -> {
                surfaceX = (double) clickedFrameIndex / (frameIndices.length - 1);
                surfaceZ = (double) clickedSlitIndex / (slitPositions.length - 1);
                var xIdx = Math.min(clickedFrameIndex, surfaceData.xAxisCount() - 1);
                var zIdx = Math.min(clickedSlitIndex, surfaceData.wavelengthCount() - 1);
                intensity = surfaceData.normalizedIntensity(xIdx, zIdx);
            }
            default -> {
                clickMarker.setVisible(false);
                return;
            }
        }

        var x3D = surfaceX * currentSurfaceXSize;
        var z3D = surfaceZ * currentSurfaceZSize;
        var y3D = applyScale((float) intensity) * SURFACE_SIZE * 0.5;

        double markerOffset = 10;
        y3D = y3D + markerOffset;

        clickMarker.setTranslateX(x3D);
        clickMarker.setTranslateY(y3D);
        clickMarker.setTranslateZ(z3D);
        clickMarker.setVisible(true);
    }

    @Override
    protected void buildSurface() {
        if (buildingMesh.compareAndSet(false, true)) {
            try {
                var surfaceData = data.toSurfaceData(currentSliceMode.toDataSliceMode(), currentSliceFraction);
                var fullXCount = surfaceData.xAxisCount();
                var fullZCount = surfaceData.wavelengthCount();

                int meshXCount;
                int meshZCount;
                if (animationMode) {
                    meshXCount = Math.min(fullXCount, animationResolution);
                    meshZCount = Math.min(fullZCount, animationResolution);
                } else {
                    meshXCount = fullXCount;
                    meshZCount = fullZCount;
                }

                boolean preserveAspectRatio = currentSliceMode == SliceMode.WAVELENGTH;
                buildSurfaceFromData(surfaceData, meshXCount, meshZCount, preserveAspectRatio);

                if (clickMarker != null && surfaceGroup != null && !surfaceGroup.getChildren().contains(clickMarker)) {
                    surfaceGroup.getChildren().add(clickMarker);
                }
            } finally {
                buildingMesh.set(false);
            }
        }
    }

    @Override
    protected void addAxesWithLabels() {
        rebuildAxes();
    }

    private void rebuildAxes() {
        if (axesGroup != null) {
            root3D.getChildren().remove(axesGroup);
        }
        axesGroup = new Group();

        var surfaceXStart = -currentSurfaceXSize / 2;
        var surfaceXEnd = currentSurfaceXSize / 2;
        var surfaceZStart = -currentSurfaceZSize / 2;
        var surfaceZEnd = currentSurfaceZSize / 2;
        double axisOffset = 15;

        var xAxisMaterial = new PhongMaterial(Color.RED);
        var xAxis = createAxisLine(currentSurfaceXSize, xAxisMaterial);
        xAxis.setRotationAxis(new Point3D(0, 0, 1));
        xAxis.setRotate(-90);
        xAxis.setTranslateX(0);
        xAxis.setTranslateY(0);
        xAxis.setTranslateZ(surfaceZStart - axisOffset);

        var yAxisMaterial = new PhongMaterial(Color.LIGHTGREEN);
        var yAxis = createAxisLine(SURFACE_SIZE * 0.5, yAxisMaterial);
        yAxis.setTranslateX(surfaceXStart - axisOffset);
        yAxis.setTranslateY(-SURFACE_SIZE * 0.25);
        yAxis.setTranslateZ(surfaceZStart - axisOffset);

        var zAxisMaterial = new PhongMaterial(Color.DODGERBLUE);
        var zAxis = createAxisLine(currentSurfaceZSize, zAxisMaterial);
        zAxis.setRotationAxis(new Point3D(1, 0, 0));
        zAxis.setRotate(90);
        zAxis.setTranslateX(surfaceXStart - axisOffset);
        zAxis.setTranslateY(0);
        zAxis.setTranslateZ(0);

        var xAxisKey = getXAxisLabelKey();
        var zAxisKey = getZAxisLabelKey();

        var xLabel = create3DLabel(I18N.string(JSolEx.class, "spectral-surface-3d", xAxisKey), Color.RED);
        xLabel.setTranslateX(surfaceXEnd + 10);
        xLabel.setTranslateY(0);
        xLabel.setTranslateZ(surfaceZStart - axisOffset);

        var yLabel = create3DLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "axis.y.short"), Color.LIGHTGREEN);
        yLabel.setTranslateX(surfaceXStart - axisOffset - 10);
        yLabel.setTranslateY(-SURFACE_SIZE * 0.5 - 15);
        yLabel.setTranslateZ(surfaceZStart - axisOffset);

        var zLabel = create3DLabel(I18N.string(JSolEx.class, "spectral-surface-3d", zAxisKey), Color.DODGERBLUE);
        zLabel.setTranslateX(surfaceXStart - axisOffset);
        zLabel.setTranslateY(0);
        zLabel.setTranslateZ(surfaceZEnd + 10);

        axesGroup.getChildren().addAll(xAxis, yAxis, zAxis, xLabel, yLabel, zLabel);

        addXAxisTickMarks(surfaceZStart - axisOffset);
        addZAxisTickMarks(surfaceXStart - axisOffset);

        root3D.getChildren().add(axesGroup);
    }

    private String getXAxisLabelKey() {
        return switch (currentSliceMode) {
            case SLIT, WAVELENGTH -> "evolution.axis.x.short";
            case FRAME -> "axis.x.short";
        };
    }

    private String getZAxisLabelKey() {
        return switch (currentSliceMode) {
            case SLIT, FRAME -> "axis.z.short";
            case WAVELENGTH -> "axis.x.short";
        };
    }

    private void addXAxisTickMarks(double zPos) {
        var surfaceXStart = -currentSurfaceXSize / 2;
        var numTicks = 5;
        var tickMaterial = new PhongMaterial(Color.RED);

        var xValues = switch (currentSliceMode) {
            case SLIT, WAVELENGTH -> data.frameIndices();
            case FRAME -> data.slitPositions();
        };
        var minVal = xValues[0];
        var maxVal = xValues[xValues.length - 1];

        for (var i = 0; i < numTicks; i++) {
            var fraction = (double) i / (numTicks - 1);
            var value = minVal + (int) (fraction * (maxVal - minVal));
            var xScreenPos = surfaceXStart + fraction * currentSurfaceXSize;

            var tickMark = new Cylinder(1, 8);
            tickMark.setMaterial(tickMaterial);
            tickMark.setRotationAxis(new Point3D(1, 0, 0));
            tickMark.setRotate(90);
            tickMark.setTranslateX(xScreenPos);
            tickMark.setTranslateY(0);
            tickMark.setTranslateZ(zPos);

            var tickText = String.valueOf(value);
            var tickLabel = create3DLabel(tickText, Color.RED);
            tickLabel.setFont(Font.font("System", 10));
            tickLabel.setTranslateX(xScreenPos);
            tickLabel.setTranslateY(0);
            tickLabel.setTranslateZ(zPos - 20);

            axesGroup.getChildren().addAll(tickMark, tickLabel);
        }
    }

    private void addZAxisTickMarks(double xPos) {
        var surfaceZStart = -currentSurfaceZSize / 2;
        var numTicks = 5;
        var tickMaterial = new PhongMaterial(Color.DODGERBLUE);

        if (currentSliceMode == SliceMode.WAVELENGTH) {
            var slitPositions = data.slitPositions();
            var minVal = slitPositions[0];
            var maxVal = slitPositions[slitPositions.length - 1];

            for (var i = 0; i < numTicks; i++) {
                var fraction = (double) i / (numTicks - 1);
                var value = minVal + (int) (fraction * (maxVal - minVal));
                var zPos = surfaceZStart + fraction * currentSurfaceZSize;

                var tickMark = new Cylinder(1, 8);
                tickMark.setMaterial(tickMaterial);
                tickMark.setRotationAxis(new Point3D(0, 0, 1));
                tickMark.setRotate(90);
                tickMark.setTranslateX(xPos);
                tickMark.setTranslateY(0);
                tickMark.setTranslateZ(zPos);

                var tickText = String.valueOf(value);
                var tickLabel = create3DLabel(tickText, Color.DODGERBLUE);
                tickLabel.setFont(Font.font("System", 10));
                tickLabel.setTranslateX(xPos - 35);
                tickLabel.setTranslateY(0);
                tickLabel.setTranslateZ(zPos);

                axesGroup.getChildren().addAll(tickMark, tickLabel);
            }
        } else {
            var wavelengthOffsets = data.wavelengthOffsets();
            var minWl = wavelengthOffsets[0];
            var maxWl = wavelengthOffsets[wavelengthOffsets.length - 1];
            var range = maxWl - minWl;
            var centerWavelength = data.centerWavelength().angstroms();

            for (var i = 0; i < numTicks; i++) {
                var fraction = (double) i / (numTicks - 1);
                var wavelengthOffset = minWl + fraction * range;
                var absoluteWavelength = centerWavelength + wavelengthOffset;
                var zPos = surfaceZStart + fraction * currentSurfaceZSize;

                var tickMark = new Cylinder(1, 8);
                tickMark.setMaterial(tickMaterial);
                tickMark.setRotationAxis(new Point3D(0, 0, 1));
                tickMark.setRotate(90);
                tickMark.setTranslateX(xPos);
                tickMark.setTranslateY(0);
                tickMark.setTranslateZ(zPos);

                var tickText = String.format(Locale.US, "%.2fÅ", absoluteWavelength);
                var tickLabel = create3DLabel(tickText, Color.DODGERBLUE);
                tickLabel.setFont(Font.font("System", 10));
                tickLabel.setTranslateX(xPos - 50);
                tickLabel.setTranslateY(0);
                tickLabel.setTranslateZ(zPos);

                axesGroup.getChildren().addAll(tickMark, tickLabel);
            }
        }
    }

    private void updateSliceOverlay() {
        if (sliceOverlayLabel != null) {
            var index = getCurrentSliceIndex();
            var label = data.getSliceLabel(currentSliceMode.toDataSliceMode(), index);
            var modeKey = switch (currentSliceMode) {
                case SLIT -> "slice.slit";
                case FRAME -> "slice.frame";
                case WAVELENGTH -> "slice.wavelength";
            };
            sliceOverlayLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", modeKey) + ": " + label);
        }
    }

    private int getCurrentSliceIndex() {
        return data.getSliceIndex(currentSliceMode.toDataSliceMode(), currentSliceFraction);
    }

    @Override
    protected HBox createDescriptionPanel() {
        var axisInfo = new HBox(15);
        axisInfo.setAlignment(Pos.CENTER_LEFT);

        var xAxisLabel = createAxisInfoLabel(
                I18N.string(JSolEx.class, "spectral-surface-3d", "evolution.axis.x"),
                String.format(Locale.US, "%d - %d",
                        data.frameIndices()[0],
                        data.frameIndices()[data.frameCount() - 1]),
                Color.RED
        );

        var minWl = data.wavelengthOffsets()[0];
        var maxWl = data.wavelengthOffsets()[data.wavelengthCount() - 1];
        var zAxisLabel = createAxisInfoLabel(
                I18N.string(JSolEx.class, "spectral-surface-3d", "axis.z"),
                String.format(Locale.US, "%.2f to %.2f Å", minWl, maxWl),
                Color.DODGERBLUE
        );

        var yAxisLabel = createAxisInfoLabel(
                I18N.string(JSolEx.class, "spectral-surface-3d", "axis.y"),
                String.format(Locale.US, "%.0f - %.0f", data.minIntensity(), data.maxIntensity()),
                Color.GREEN
        );

        var wavelengthLabel = createAxisInfoLabel(
                I18N.string(JSolEx.class, "spectral-surface-3d", "center.wavelength"),
                String.format(Locale.US, "%.2f Å", data.centerWavelength().angstroms()),
                Color.GRAY
        );
        wavelengthLabel.setCursor(javafx.scene.Cursor.HAND);
        wavelengthLabel.setOnMouseClicked(e -> resetToCenter());

        axisInfo.getChildren().addAll(xAxisLabel, zAxisLabel, yAxisLabel, new Separator(Orientation.VERTICAL), wavelengthLabel);

        var panel = new HBox(10, axisInfo);
        panel.setPadding(new Insets(8));
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        return panel;
    }

    @Override
    protected HBox createControlPanel() {
        var sliceModeLabel = createStyledLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "slice.mode"));
        sliceModeCombo = new ComboBox<>();
        sliceModeCombo.getItems().addAll(SliceMode.values());
        sliceModeCombo.getSelectionModel().select(currentSliceMode);
        sliceModeCombo.setOnAction(e -> {
            var selected = sliceModeCombo.getSelectionModel().getSelectedItem();
            if (selected != null && selected != currentSliceMode) {
                currentSliceMode = selected;
                currentSliceFraction = 0.5;
                settingSliderProgrammatically = true;
                sliceSlider.setValue(0.5);
                settingSliderProgrammatically = false;
                invalidateMesh();
                buildSurface();
                rebuildAxes();
                updateSlicePositionLabel();
                updateSliceOverlay();
                updateProfileOverlay();
                updateLegendLabels();
                clearClickMarker();
            }
        });
        var sliceModeBox = new HBox(3, sliceModeLabel, sliceModeCombo);
        sliceModeBox.setAlignment(Pos.CENTER_LEFT);

        sliceSlider = new Slider(0, 1, currentSliceFraction);
        sliceSlider.setPrefWidth(150);
        sliceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentSliceFraction = newVal.doubleValue();
            updateSlicePositionLabel();
            updateSliceOverlay();
            updateProfileOverlay();
            // Clear marker if user manually moved the slider
            if (!settingSliderProgrammatically) {
                clearClickMarker();
            }
            if (animationMode) {
                buildSurface();
            } else {
                sliderDebounce.playFromStart();
            }
        });
        sliceSlider.setOnMouseReleased(e -> {
            sliderDebounce.stop();
            buildSurface();
        });
        slicePositionLabel = new Label();
        slicePositionLabel.setMinSize(Label.USE_PREF_SIZE, Label.USE_PREF_SIZE);
        updateSlicePositionLabel();
        var sliceBox = new HBox(5, sliceSlider, slicePositionLabel);
        sliceBox.setAlignment(Pos.CENTER_LEFT);

        playButton = createStyledButton(I18N.string(JSolEx.class, "spectral-surface-3d", "play"));
        playButton.setOnAction(e -> toggleAnimation());

        return createCommonControlPanel(
                new Separator(Orientation.VERTICAL),
                sliceModeBox,
                sliceBox,
                playButton
        );
    }

    private void updateSlicePositionLabel() {
        var index = getCurrentSliceIndex();
        slicePositionLabel.setText(data.getSliceLabel(currentSliceMode.toDataSliceMode(), index));
    }

    private void resetToCenter() {
        if (animationTimer != null) {
            return;
        }
        currentSliceFraction = data.getCenterWavelengthFraction();
        sliceSlider.setValue(currentSliceFraction);
        buildSurface();
    }

    private void toggleAnimation() {
        if (animationTimer != null) {
            // Stop animation first, then set animationMode to false
            // This prevents the slider listener from triggering buildSurface in animation mode
            var timer = animationTimer;
            animationTimer = null;
            timer.stop();
            animationMode = false;
            playButton.setText(I18N.string(JSolEx.class, "spectral-surface-3d", "play"));
            sliceSlider.setDisable(false);
            sliceModeCombo.setDisable(false);
            invalidateMesh();
            // Use Platform.runLater to ensure any pending animation frame has completed
            javafx.application.Platform.runLater(this::buildSurface);
        } else {
            sliceSlider.setDisable(true);
            sliceModeCombo.setDisable(true);
            playButton.setText(I18N.string(JSolEx.class, "spectral-surface-3d", "stop"));

            animationMode = true;
            animationResolution = INITIAL_ANIMATION_RESOLUTION;
            invalidateMesh();
            buildSurface();

            var sliceCount = data.getSliceCount(currentSliceMode.toDataSliceMode());
            var totalFrames = (int) (ANIMATION_DURATION_SECONDS * TARGET_FPS);
            var step = Math.max(1, sliceCount / totalFrames);
            var stepSize = (double) step / (sliceCount - 1);

            var lastUpdateTime = new long[]{System.nanoTime()};
            long[] frameStartTime = {0};

            animationTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    // Check if animation was stopped
                    if (animationTimer == null || !animationMode) {
                        return;
                    }

                    if (frameStartTime[0] == 0) {
                        frameStartTime[0] = now;
                        return;
                    }

                    var frameDuration = now - frameStartTime[0];
                    frameStartTime[0] = now;

                    if (now - lastUpdateTime[0] >= TARGET_FRAME_NANOS) {
                        lastUpdateTime[0] = now;

                        adjustResolution(frameDuration);

                        var newValue = sliceSlider.getValue() + stepSize;
                        if (newValue > 1.0) {
                            newValue = 0.0;
                        }
                        sliceSlider.setValue(newValue);
                    }
                }
            };
            animationTimer.start();
        }
    }

    private void adjustResolution(long lastFrameDurationNanos) {
        var maxResolution = Math.max(data.frameCount(), data.wavelengthCount());
        var oldResolution = animationResolution;

        if (lastFrameDurationNanos > TARGET_FRAME_NANOS * 1.2) {
            // Too slow, decrease resolution by ~10%
            animationResolution = Math.max(MIN_ANIMATION_RESOLUTION, animationResolution * 9 / 10);
        } else if (lastFrameDurationNanos < TARGET_FRAME_NANOS * 0.8) {
            // Fast enough, increase resolution by ~10%
            animationResolution = Math.min(maxResolution, animationResolution * 11 / 10);
        }

        if (oldResolution != animationResolution) {
            invalidateMesh();
            buildSurface();
        }
    }

    @Override
    protected String getInterpretationKey() {
        return "legend.interpret." + currentSliceMode.name().toLowerCase();
    }

    @Override
    protected double getMinIntensity() {
        return data.minIntensity();
    }

    @Override
    protected double getMaxIntensity() {
        return data.maxIntensity();
    }

    @Override
    protected boolean supportsVideoExport() {
        return true;
    }

    @Override
    protected int getVideoFrameCount() {
        return data.getSliceCount(currentSliceMode.toDataSliceMode());
    }

    @Override
    protected void setVideoFrameIndex(int index) {
        var sliceCount = getVideoFrameCount();
        var fraction = sliceCount > 1 ? (double) index / (sliceCount - 1) : 0;
        currentSliceFraction = fraction;
        sliceSlider.setValue(fraction);
        updateSlicePositionLabel();
        updateSliceOverlay();
        buildSurface();
    }

    @Override
    protected void onExportPng() {
        exportToPng("spectral_evolution_4d.png");
    }

    @Override
    protected void onExportVideo() {
        exportToVideo(
                "spectral_evolution_4d",
                () -> {
                    sliceSlider.setDisable(true);
                    sliceModeCombo.setDisable(true);
                },
                () -> {
                    sliceSlider.setDisable(false);
                    sliceModeCombo.setDisable(false);
                }
        );
    }

    /**
     * Stops the animation if it is currently running.
     */
    public void stopAnimation() {
        if (animationTimer != null) {
            var timer = animationTimer;
            animationTimer = null;
            timer.stop();
            animationMode = false;
        }
    }

    /**
     * Creates and displays a new spectral evolution 4D viewer in a new window.
     *
     * @param data the spectral evolution data to visualize
     * @param ellipse the ellipse representing the solar disk boundary, may be null
     * @param title the window title
     * @return the created viewer instance
     */
    public static SpectralEvolution4DViewer show(SpectralEvolution4DData data, Ellipse ellipse, String title) {
        var viewer = new SpectralEvolution4DViewer(data, ellipse);
        var stage = FXUtils.newStage();
        stage.setTitle(title);
        var scene = newScene(viewer, 1100, 700);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setOnHidden(e -> viewer.stopAnimation());
        stage.show();
        return viewer;
    }

    private enum SliceMode {
        SLIT,
        FRAME,
        WAVELENGTH;

        /**
         * Converts this UI slice mode to the corresponding data layer slice mode.
         *
         * @return the corresponding data slice mode
         */
        public SpectralEvolution4DData.SliceMode toDataSliceMode() {
            return SpectralEvolution4DData.SliceMode.valueOf(name());
        }

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "spectral-surface-3d", "slice." + name().toLowerCase());
        }
    }
}
