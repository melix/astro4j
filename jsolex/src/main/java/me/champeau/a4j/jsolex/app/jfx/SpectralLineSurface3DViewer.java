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

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point3D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.text.Font;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineSurfaceData;
import me.champeau.a4j.jsolex.processing.spectrum.SpectralLineSurfaceData.ViewMode;

import java.util.Locale;

import static me.champeau.a4j.jsolex.app.JSolEx.newScene;

/**
 * A 3D viewer for spectral line profile surfaces.
 * Displays intensity as a function of slit position (X) and wavelength offset (Z).
 * The height (Y) and color represent the intensity value.
 */
public class SpectralLineSurface3DViewer extends AbstractSpectral3DViewer {

    private final SpectralLineSurfaceData data;

    public SpectralLineSurface3DViewer(SpectralLineSurfaceData data) {
        super();
        this.data = data;
        initializeView();
    }

    @Override
    protected void buildSurface() {
        buildSurfaceFromData(data, data.xAxisCount(), data.wavelengthCount());
    }

    @Override
    protected void addAxesWithLabels() {
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

        var xAxisKey = data.viewMode() == ViewMode.EVOLUTION ? "evolution.axis.x.short" : "axis.x.short";
        var xLabel = create3DLabel(I18N.string(JSolEx.class, "spectral-surface-3d", xAxisKey), Color.RED);
        xLabel.setTranslateX(surfaceXEnd + 10);
        xLabel.setTranslateY(0);
        xLabel.setTranslateZ(surfaceZStart - axisOffset);

        var yLabel = create3DLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "axis.y.short"), Color.LIGHTGREEN);
        yLabel.setTranslateX(surfaceXStart - axisOffset - 10);
        yLabel.setTranslateY(-SURFACE_SIZE * 0.5 - 15);
        yLabel.setTranslateZ(surfaceZStart - axisOffset);

        var zLabel = create3DLabel(I18N.string(JSolEx.class, "spectral-surface-3d", "axis.z.short"), Color.DODGERBLUE);
        zLabel.setTranslateX(surfaceXStart - axisOffset);
        zLabel.setTranslateY(0);
        zLabel.setTranslateZ(surfaceZEnd + 10);

        axesGroup.getChildren().addAll(xAxis, yAxis, zAxis, xLabel, yLabel, zLabel);

        addWavelengthTickMarks(surfaceXStart - axisOffset);
        addXAxisTickMarks(surfaceZStart - axisOffset);

        root3D.getChildren().add(axesGroup);
    }

    private void addWavelengthTickMarks(double xPos) {
        var wavelengthOffsets = data.wavelengthOffsets();
        var minWl = wavelengthOffsets[0];
        var maxWl = wavelengthOffsets[wavelengthOffsets.length - 1];
        var range = maxWl - minWl;
        var centerWavelength = data.centerWavelength().angstroms();

        var surfaceZStart = -currentSurfaceZSize / 2;

        var numTicks = 5;
        var tickMaterial = new PhongMaterial(Color.DODGERBLUE);

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

    private void addXAxisTickMarks(double zPos) {
        var xPositions = data.xAxisPositions();
        var minX = xPositions[0];
        var maxX = xPositions[xPositions.length - 1];

        var surfaceXStart = -currentSurfaceXSize / 2;

        var numTicks = 5;
        var tickMaterial = new PhongMaterial(Color.RED);

        for (var i = 0; i < numTicks; i++) {
            var fraction = (double) i / (numTicks - 1);
            var xValue = minX + (int) (fraction * (maxX - minX));
            var xScreenPos = surfaceXStart + fraction * currentSurfaceXSize;

            var tickMark = new Cylinder(1, 8);
            tickMark.setMaterial(tickMaterial);
            tickMark.setRotationAxis(new Point3D(1, 0, 0));
            tickMark.setRotate(90);
            tickMark.setTranslateX(xScreenPos);
            tickMark.setTranslateY(0);
            tickMark.setTranslateZ(zPos);

            var tickText = String.valueOf(xValue);
            var tickLabel = create3DLabel(tickText, Color.RED);
            tickLabel.setFont(Font.font("System", 10));
            tickLabel.setTranslateX(xScreenPos);
            tickLabel.setTranslateY(0);
            tickLabel.setTranslateZ(zPos - 20);

            axesGroup.getChildren().addAll(tickMark, tickLabel);
        }
    }

    @Override
    protected HBox createDescriptionPanel() {
        var axisInfo = new HBox(15);
        axisInfo.setAlignment(Pos.CENTER_LEFT);

        var isEvolution = data.viewMode() == ViewMode.EVOLUTION;
        var xAxisKey = isEvolution ? "evolution.axis.x" : "axis.x";
        var xAxisValue = isEvolution
                ? String.format(Locale.US, "%d - %d",
                        data.xAxisPositions()[0],
                        data.xAxisPositions()[data.xAxisCount() - 1])
                : String.format(Locale.US, "%d - %d px",
                        data.xAxisPositions()[0],
                        data.xAxisPositions()[data.xAxisCount() - 1]);
        var xAxisLabel = createAxisInfoLabel(
                I18N.string(JSolEx.class, "spectral-surface-3d", xAxisKey),
                xAxisValue,
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

        axisInfo.getChildren().addAll(xAxisLabel, zAxisLabel, yAxisLabel, new Separator(Orientation.VERTICAL), wavelengthLabel);

        var panel = new HBox(10, axisInfo);
        panel.setPadding(new Insets(8));
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        return panel;
    }

    @Override
    protected HBox createControlPanel() {
        return createCommonControlPanel();
    }

    @Override
    protected String getInterpretationKey() {
        return data.viewMode() == ViewMode.EVOLUTION ? "evolution.interpret" : "legend.interpret";
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
        return false;
    }

    @Override
    protected int getVideoFrameCount() {
        return 0;
    }

    @Override
    protected void setVideoFrameIndex(int index) {
        // No-op for static 3D viewer
    }

    @Override
    protected void onExportPng() {
        exportToPng("spectral_surface_3d.png");
    }

    @Override
    protected void onExportVideo() {
        // No-op for static 3D viewer
    }

    /**
     * Shows the 3D spectral surface viewer in a new window.
     *
     * @param data  the surface data to display
     * @param title the window title
     */
    public static void show(SpectralLineSurfaceData data, String title) {
        var viewer = new SpectralLineSurface3DViewer(data);
        var stage = FXUtils.newStage();
        stage.setTitle(title);
        var scene = newScene(viewer, 1000, 700);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }
}
