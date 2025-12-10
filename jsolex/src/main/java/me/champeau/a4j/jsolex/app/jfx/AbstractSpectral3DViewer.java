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
    protected static final double INITIAL_DISTANCE = 800;
    protected static final double MIN_DISTANCE = 200;
    protected static final double MAX_DISTANCE = 2000;
    protected static final double SURFACE_SIZE = 400;

    protected final Group root3D;
    protected final PerspectiveCamera camera;
    protected final Rotate rotateX;
    protected final Rotate rotateY;
    protected final Translate cameraTranslate;

    private double anchorX;
    private double anchorY;
    private double anchorAngleX;
    private double anchorAngleY;

    protected IntensityScale scale = IntensityScale.LINEAR;
    protected Group surfaceGroup;
    protected Group axesGroup;

    protected double currentSurfaceXSize = SURFACE_SIZE;
    protected double currentSurfaceZSize = SURFACE_SIZE;

    protected Label legendTitleLabel;
    protected Label legendHighLabel;
    protected Label legendLowLabel;
    protected Label legendInterpretLabel;

    protected final StackPane graphPane;
    protected boolean exportGraphOnly = true;

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

    protected abstract void buildSurface();

    protected abstract void addAxesWithLabels();

    protected abstract HBox createDescriptionPanel();

    protected abstract HBox createControlPanel();

    protected abstract String getInterpretationKey();

    protected abstract double getMinIntensity();

    protected abstract double getMaxIntensity();

    protected abstract boolean supportsVideoExport();

    protected abstract int getVideoFrameCount();

    protected abstract void setVideoFrameIndex(int index);

    protected void buildSurfaceFromData(SpectralLineSurfaceData surfaceData, int meshXCount, int meshZCount) {
        buildSurfaceFromData(surfaceData, meshXCount, meshZCount, false);
    }

    protected void buildSurfaceFromData(SpectralLineSurfaceData surfaceData, int meshXCount, int meshZCount,
                                        boolean preserveAspectRatio) {
        if (surfaceGroup != null) {
            root3D.getChildren().remove(surfaceGroup);
        }

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

        var mesh = createMesh(surfaceData, fullXCount, fullZCount, meshXCount, meshZCount, surfaceXSize, surfaceZSize);
        var meshView = new MeshView(mesh);
        meshView.setMaterial(createMaterial());
        meshView.setCullFace(CullFace.NONE);
        meshView.setDrawMode(DrawMode.FILL);

        var volumeGroup = new Group(meshView);

        var bottomMesh = createBottomMesh(surfaceXSize, surfaceZSize);
        var bottomView = new MeshView(bottomMesh);
        var bottomMaterial = new PhongMaterial(Color.rgb(40, 40, 50));
        bottomView.setMaterial(bottomMaterial);
        bottomView.setCullFace(CullFace.NONE);
        volumeGroup.getChildren().add(bottomView);

        var wallMesh = createWallMesh(surfaceData, fullXCount, fullZCount, meshXCount, meshZCount, surfaceXSize, surfaceZSize);
        var wallView = new MeshView(wallMesh);
        wallView.setMaterial(createMaterial());
        wallView.setCullFace(CullFace.NONE);
        volumeGroup.getChildren().add(wallView);

        surfaceGroup = volumeGroup;
        surfaceGroup.getTransforms().addAll(
                new Translate(-surfaceXSize / 2, 0, -surfaceZSize / 2),
                new Scale(1, -1, 1)
        );

        root3D.getChildren().add(surfaceGroup);
    }

    protected TriangleMesh createBottomMesh(double surfaceXSize, double surfaceZSize) {
        var mesh = new TriangleMesh();

        mesh.getPoints().addAll(
                0, 0, 0,
                (float) surfaceXSize, 0, 0,
                (float) surfaceXSize, 0, (float) surfaceZSize,
                0, 0, (float) surfaceZSize
        );

        mesh.getTexCoords().addAll(0, 0.5f);

        mesh.getFaces().addAll(
                0, 0, 1, 0, 2, 0,
                0, 0, 2, 0, 3, 0
        );

        return mesh;
    }

    protected TriangleMesh createWallMesh(SpectralLineSurfaceData surfaceData, int fullXCount, int fullZCount,
                                          int meshXCount, int meshZCount, double surfaceXSize, double surfaceZSize) {
        var mesh = new TriangleMesh();

        for (var x = 0; x < meshXCount; x++) {
            var srcX = meshXCount == fullXCount ? x : x * (fullXCount - 1) / (meshXCount - 1);
            var xPos = (float) x / (meshXCount - 1) * (float) surfaceXSize;

            var frontNormalized = surfaceData.normalizedIntensity(srcX, 0);
            var frontYPos = applyScale(frontNormalized) * (float) (SURFACE_SIZE * 0.5);
            mesh.getPoints().addAll(xPos, frontYPos, 0);
            mesh.getPoints().addAll(xPos, 0, 0);
            mesh.getTexCoords().addAll(applyScale(frontNormalized), 0.5f);
            mesh.getTexCoords().addAll(0, 0.5f);

            var srcZBack = meshZCount == fullZCount ? meshZCount - 1 : (meshZCount - 1) * (fullZCount - 1) / (meshZCount - 1);
            var backNormalized = surfaceData.normalizedIntensity(srcX, srcZBack);
            var backYPos = applyScale(backNormalized) * (float) (SURFACE_SIZE * 0.5);
            mesh.getPoints().addAll(xPos, backYPos, (float) surfaceZSize);
            mesh.getPoints().addAll(xPos, 0, (float) surfaceZSize);
            mesh.getTexCoords().addAll(applyScale(backNormalized), 0.5f);
            mesh.getTexCoords().addAll(0, 0.5f);
        }

        for (var z = 0; z < meshZCount; z++) {
            var srcZ = meshZCount == fullZCount ? z : z * (fullZCount - 1) / (meshZCount - 1);
            var zPos = (float) z / (meshZCount - 1) * (float) surfaceZSize;

            var leftNormalized = surfaceData.normalizedIntensity(0, srcZ);
            var leftYPos = applyScale(leftNormalized) * (float) (SURFACE_SIZE * 0.5);
            mesh.getPoints().addAll(0, leftYPos, zPos);
            mesh.getPoints().addAll(0, 0, zPos);
            mesh.getTexCoords().addAll(applyScale(leftNormalized), 0.5f);
            mesh.getTexCoords().addAll(0, 0.5f);

            var srcXRight = meshXCount == fullXCount ? meshXCount - 1 : (meshXCount - 1) * (fullXCount - 1) / (meshXCount - 1);
            var rightNormalized = surfaceData.normalizedIntensity(srcXRight, srcZ);
            var rightYPos = applyScale(rightNormalized) * (float) (SURFACE_SIZE * 0.5);
            mesh.getPoints().addAll((float) surfaceXSize, rightYPos, zPos);
            mesh.getPoints().addAll((float) surfaceXSize, 0, zPos);
            mesh.getTexCoords().addAll(applyScale(rightNormalized), 0.5f);
            mesh.getTexCoords().addAll(0, 0.5f);
        }

        var frontBaseIdx = 0;
        for (var x = 0; x < meshXCount - 1; x++) {
            var topLeft = frontBaseIdx + x * 4;
            var bottomLeft = topLeft + 1;
            var topRight = frontBaseIdx + (x + 1) * 4;
            var bottomRight = topRight + 1;
            mesh.getFaces().addAll(
                    topLeft, topLeft, bottomLeft, bottomLeft, bottomRight, bottomRight,
                    topLeft, topLeft, bottomRight, bottomRight, topRight, topRight
            );
        }

        var backBaseIdx = 2;
        for (var x = 0; x < meshXCount - 1; x++) {
            var topLeft = backBaseIdx + x * 4;
            var bottomLeft = topLeft + 1;
            var topRight = backBaseIdx + (x + 1) * 4;
            var bottomRight = topRight + 1;
            mesh.getFaces().addAll(
                    topLeft, topLeft, topRight, topRight, bottomRight, bottomRight,
                    topLeft, topLeft, bottomRight, bottomRight, bottomLeft, bottomLeft
            );
        }

        var sideBaseIdx = meshXCount * 4;
        for (var z = 0; z < meshZCount - 1; z++) {
            var topLeft = sideBaseIdx + z * 4;
            var bottomLeft = topLeft + 1;
            var topRight = sideBaseIdx + (z + 1) * 4;
            var bottomRight = topRight + 1;
            mesh.getFaces().addAll(
                    topLeft, topLeft, topRight, topRight, bottomRight, bottomRight,
                    topLeft, topLeft, bottomRight, bottomRight, bottomLeft, bottomLeft
            );
        }

        var rightBaseIdx = sideBaseIdx + 2;
        for (var z = 0; z < meshZCount - 1; z++) {
            var topLeft = rightBaseIdx + z * 4;
            var bottomLeft = topLeft + 1;
            var topRight = rightBaseIdx + (z + 1) * 4;
            var bottomRight = topRight + 1;
            mesh.getFaces().addAll(
                    topLeft, topLeft, bottomLeft, bottomLeft, bottomRight, bottomRight,
                    topLeft, topLeft, bottomRight, bottomRight, topRight, topRight
            );
        }

        return mesh;
    }

    protected TriangleMesh createMesh(SpectralLineSurfaceData surfaceData, int fullXCount, int fullZCount,
                                      int meshXCount, int meshZCount, double surfaceXSize, double surfaceZSize) {
        var mesh = new TriangleMesh();

        for (var z = 0; z < meshZCount; z++) {
            var srcZ = meshZCount == fullZCount ? z : z * (fullZCount - 1) / (meshZCount - 1);
            for (var x = 0; x < meshXCount; x++) {
                var srcX = meshXCount == fullXCount ? x : x * (fullXCount - 1) / (meshXCount - 1);

                var xPos = (float) x / (meshXCount - 1) * (float) surfaceXSize;
                var normalizedValue = surfaceData.normalizedIntensity(srcX, srcZ);
                var yPos = applyScale(normalizedValue) * (float) (SURFACE_SIZE * 0.5);
                var zPos = (float) z / (meshZCount - 1) * (float) surfaceZSize;
                mesh.getPoints().addAll(xPos, yPos, zPos);

                var u = applyScale(normalizedValue);
                mesh.getTexCoords().addAll(u, 0.5f);
            }
        }

        for (var z = 0; z < meshZCount - 1; z++) {
            for (var x = 0; x < meshXCount - 1; x++) {
                var p00 = z * meshXCount + x;
                var p10 = z * meshXCount + x + 1;
                var p01 = (z + 1) * meshXCount + x;
                var p11 = (z + 1) * meshXCount + x + 1;

                mesh.getFaces().addAll(
                        p00, p00, p10, p10, p11, p11,
                        p00, p00, p11, p11, p01, p01
                );
            }
        }

        return mesh;
    }

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

    protected Group createAxisLine(double length, PhongMaterial material) {
        var shaft = new Cylinder(1.5, length);
        shaft.setMaterial(material);
        return new Group(shaft);
    }

    protected Text create3DLabel(String text, Color color) {
        var label = new Text(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setFill(color);
        return label;
    }

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

    protected void updateLegendLabels() {
        var isLog = scale != IntensityScale.LINEAR;
        var titleKey = isLog ? "legend.intensity.log" : "legend.intensity";
        var highKey = isLog ? "legend.high.log" : "legend.high";
        var lowKey = isLog ? "legend.low.log" : "legend.low";

        legendTitleLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", titleKey));
        legendHighLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", highKey));
        legendLowLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", lowKey));
        legendInterpretLabel.setText(I18N.string(JSolEx.class, "spectral-surface-3d", getInterpretationKey()));
    }

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

    protected void resetView() {
        rotateX.setAngle(-30);
        rotateY.setAngle(30);
        cameraTranslate.setZ(-INITIAL_DISTANCE);
    }

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

    protected static Button createStyledButton(String text) {
        var button = new Button(text);
        button.getStyleClass().add("image-viewer-button");
        button.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);
        return button;
    }

    protected static Label createStyledLabel(String text) {
        var label = new Label(text);
        label.setMinSize(Label.USE_PREF_SIZE, Label.USE_PREF_SIZE);
        return label;
    }

    protected abstract void onExportPng();

    protected abstract void onExportVideo();

    protected enum IntensityScale {
        LINEAR,
        SQUARE,
        LOG2,
        LOG10;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "spectral-surface-3d", "scale." + name().toLowerCase());
        }
    }
}
