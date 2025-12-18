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

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.ArrayList;
import java.util.List;

/**
 * Help overlay for the 4D spectral evolution viewer.
 * Displays a 3-phase animated diagram explaining how the 4D spectral cube works:
 * 1. Concept overview - 4 dimensions, two slicing modes, two results
 * 2. Wavelength slice - shows reconstructed solar disk
 * 3. Frame/slit slice - shows 3D spectral profile surface
 */
public class SpectralCube4DHelpOverlay extends AbstractHelpOverlay {

    private static final double DIAGRAM_WIDTH = 580;
    private static final double DIAGRAM_HEIGHT = 360;
    private static final Duration DISPLAY_DURATION = Duration.seconds(7);

    private static final String I18N_BUNDLE = "spectral-cube-4d";
    private static final String VIEWER_ID = "spectral-cube-4d";

    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_TEXT_DIM = Color.rgb(136, 136, 136);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);
    private static final Color COLOR_SLICE = Color.rgb(255, 100, 100);

    private HelpAnimationController popupDiagramController;
    private HelpAnimationController maximizedController;

    public SpectralCube4DHelpOverlay() {
        super(VIEWER_ID);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (popupDiagramController != null) {
            popupDiagramController.stop();
        }
        if (maximizedController != null) {
            maximizedController.stop();
        }
    }

    @Override
    protected void onPopupShown() {
        if (popupDiagramController != null) {
            popupDiagramController.start();
        }
    }

    @Override
    protected void onPopupHidden() {
        if (popupDiagramController != null) {
            popupDiagramController.stop();
        }
    }

    @Override
    protected StackPane createHelpPopup() {
        var titleLabel = new Label(I18N.string(JSolEx.class, I18N_BUNDLE, "help.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        var helpText = I18N.string(JSolEx.class, I18N_BUNDLE, "help.text");
        var textFlow = parseFormattedText(helpText);
        textFlow.setMaxWidth(700);

        var popupDiagramResult = createDiagramWithController();
        popupDiagramController = popupDiagramResult.controller();

        var diagram = createScalableDiagram(
                popupDiagramResult.diagram(),
                () -> {
                    var result = createDiagramWithController();
                    maximizedController = result.controller();
                    return result.diagram();
                },
                () -> {
                    if (maximizedController != null) {
                        maximizedController.start();
                    }
                },
                () -> {
                    if (maximizedController != null) {
                        maximizedController.stop();
                        maximizedController = null;
                    }
                }
        );

        var okButton = new Button("OK");
        okButton.setFont(Font.font("System", FontWeight.BOLD, 13));
        okButton.setMinWidth(80);
        okButton.setStyle(
                "-fx-background-color: rgba(80, 80, 100, 0.9); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 20 8 20;"
        );
        okButton.setOnMouseEntered(e -> okButton.setStyle(
                "-fx-background-color: rgba(100, 100, 130, 0.95); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 20 8 20;"
        ));
        okButton.setOnMouseExited(e -> okButton.setStyle(
                "-fx-background-color: rgba(80, 80, 100, 0.9); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 8 20 8 20;"
        ));
        okButton.setOnAction(e -> hidePopup());

        var buttonBox = new HBox(okButton);
        buttonBox.setAlignment(Pos.CENTER);

        var contentBox = new VBox(15, titleLabel, textFlow, diagram, buttonBox);
        contentBox.setPadding(new Insets(20));

        var scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setMaxHeight(680);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        var content = createContentPane(scrollPane, 750, 720);
        return wrapInOverlay(content);
    }

    private record DiagramResult(Node diagram, HelpAnimationController controller) {}

    private DiagramResult createDiagramWithController() {
        var diagramPane = new StackPane();
        diagramPane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setStyle("-fx-background-color: #1a1a24; -fx-background-radius: 8;");

        var phase1Result = createPhase1Pane();
        var phase2Result = createPhase2Pane();
        var phase3Result = createPhase3Pane();

        var phase1 = phase1Result.pane();
        var phase2 = phase2Result.pane();
        var phase3 = phase3Result.pane();

        var localPhaseAnimations = List.of(
                phase1Result.animation(),
                phase2Result.animation(),
                phase3Result.animation()
        );

        var localPhaseResetters = List.of(
                phase1Result.resetElements(),
                phase2Result.resetElements(),
                phase3Result.resetElements()
        );

        var localPhases = List.of(phase1, phase2, phase3);

        phase1.setOpacity(1.0);
        phase2.setOpacity(0.0);
        phase3.setOpacity(0.0);

        diagramPane.getChildren().addAll(phase3, phase2, phase1);

        var localPhaseIndicators = new HBox(8);
        localPhaseIndicators.setAlignment(Pos.CENTER);

        var controller = new HelpAnimationController(
                localPhaseAnimations,
                localPhaseResetters,
                localPhases,
                localPhaseIndicators,
                DISPLAY_DURATION
        );

        for (var i = 0; i < 3; i++) {
            var dot = new Circle(6);
            dot.setFill(i == 0 ? Color.WHITE : Color.gray(0.4));
            dot.setId("phase-indicator-" + i);
            dot.setCursor(javafx.scene.Cursor.HAND);
            var phaseIndex = i;
            dot.setOnMouseClicked(e -> controller.switchToPhase(phaseIndex));
            dot.setOnMouseEntered(e -> {
                if (phaseIndex != controller.getCurrentPhase()) {
                    dot.setFill(Color.gray(0.6));
                }
            });
            dot.setOnMouseExited(e -> {
                if (phaseIndex != controller.getCurrentPhase()) {
                    dot.setFill(Color.gray(0.4));
                }
            });
            localPhaseIndicators.getChildren().add(dot);
        }

        controller.createDiagramAnimation();

        var container = new VBox(8, diagramPane, localPhaseIndicators);
        container.setAlignment(Pos.CENTER);

        return new DiagramResult(container, controller);
    }


    private HelpAnimationController.PhaseResult createPhase1Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        var dimensionsExplain = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.dimensions.explain"), centerX, 45, 11, false, COLOR_TEXT_DIM);
        pane.getChildren().add(dimensionsExplain);

        // Simple two-row layout showing the two slicing categories
        double rowHeight = 120;
        double startY = 65;
        double leftX = 60;
        double rightX = 350;

        // Row 1: Spectral slicing (wavelength → disk)
        var spectralBox = new Rectangle(leftX, startY, 200, 80);
        spectralBox.setFill(Color.rgb(80, 50, 50, 0.8));
        spectralBox.setStroke(COLOR_SLICE);
        spectralBox.setStrokeWidth(2);
        spectralBox.setArcWidth(8);
        spectralBox.setArcHeight(8);

        var spectralTitle = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.wavelength"), leftX + 100, startY + 25, 11, true, COLOR_SLICE);
        var spectralSubtitle = createDiagramText("(spectral)", leftX + 100, startY + 45, 9, false, COLOR_TEXT_DIM);

        var spectralElements = List.of(spectralBox, spectralTitle, spectralSubtitle);
        pane.getChildren().addAll(spectralElements);

        // Arrow to disk
        var arrow1 = createArrow(leftX + 205, startY + 40, rightX - 5, startY + 40, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(arrow1);

        // Disk result
        var diskOval = new javafx.scene.shape.Ellipse(rightX + 60, startY + 40, 45, 28);
        diskOval.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(100, 255, 100)),
                new Stop(0.7, Color.rgb(50, 200, 50)),
                new Stop(1.0, Color.rgb(30, 100, 80))));
        diskOval.setStroke(Color.rgb(80, 200, 80));
        diskOval.setStrokeWidth(2);
        pane.getChildren().add(diskOval);

        var diskLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.result.disk"), rightX + 60, startY + 80, 10, false, COLOR_HIGHLIGHT);
        pane.getChildren().add(diskLabel);

        var row1Elements = new ArrayList<Node>();
        row1Elements.addAll(spectralElements);
        row1Elements.addAll(arrow1);
        row1Elements.add(diskOval);
        row1Elements.add(diskLabel);

        // Row 2: Spatial slicing (frame/slit → profile)
        double row2Y = startY + rowHeight;

        var spatialBox = new Rectangle(leftX, row2Y, 200, 80);
        spatialBox.setFill(Color.rgb(50, 70, 50, 0.8));
        spatialBox.setStroke(Color.rgb(100, 255, 100));
        spatialBox.setStrokeWidth(2);
        spatialBox.setArcWidth(8);
        spatialBox.setArcHeight(8);

        var spatialTitle = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.frame.slit"), leftX + 100, row2Y + 25, 11, true, Color.rgb(100, 255, 100));
        var spatialSubtitle = createDiagramText("(spatial)", leftX + 100, row2Y + 45, 9, false, COLOR_TEXT_DIM);

        var spatialElements = List.of(spatialBox, spatialTitle, spatialSubtitle);
        pane.getChildren().addAll(spatialElements);

        // Arrow to profile
        var arrow2 = createArrow(leftX + 205, row2Y + 40, rightX - 5, row2Y + 40, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(arrow2);

        // Profile result (Gaussian absorption valley)
        double profileCX = rightX + 60;
        double profileCY = row2Y + 40;
        double profileIconWidth = 80;
        double profileIconHeight = 35;
        int iconPoints = 25;
        var iconLinePoints = new double[iconPoints * 2];
        for (int p = 0; p < iconPoints; p++) {
            double t = (double) p / (iconPoints - 1);
            double x = profileCX - profileIconWidth / 2 + t * profileIconWidth;
            double distFromCenter = t - 0.5;
            double intensity = 1.0 - 0.7 * Math.exp(-distFromCenter * distFromCenter / 0.02);
            double y = profileCY + 15 - intensity * profileIconHeight;
            iconLinePoints[p * 2] = x;
            iconLinePoints[p * 2 + 1] = y;
        }
        var profileShape = new Polyline(iconLinePoints);
        profileShape.setStroke(Color.rgb(80, 180, 80));
        profileShape.setStrokeWidth(3);
        profileShape.setFill(null);
        pane.getChildren().add(profileShape);

        var profileLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.result.profile"), profileCX, row2Y + 80, 10, false, COLOR_HIGHLIGHT);
        pane.getChildren().add(profileLabel);

        var row2Elements = new ArrayList<Node>();
        row2Elements.addAll(spatialElements);
        row2Elements.addAll(arrow2);
        row2Elements.add(profileShape);
        row2Elements.add(profileLabel);

        Runnable resetElements = () -> {
            row1Elements.forEach(n -> n.setOpacity(0));
            row2Elements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        // Show row 1 (spectral → disk)
        HelpAnimationController.addFadeIn(animation, row1Elements, t, 0.5);
        t += 0.8;

        // Show row 2 (spatial → profile)
        HelpAnimationController.addFadeIn(animation, row2Elements, t, 0.5);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase2Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // Left side: Slider representation
        double sliderX = 60;
        double sliderY = 60;
        double sliderH = 120;

        var sliderTrack = new Rectangle(sliderX, sliderY, 8, sliderH);
        sliderTrack.setFill(Color.rgb(60, 60, 80));
        sliderTrack.setStroke(Color.rgb(100, 100, 120));
        sliderTrack.setArcWidth(4);
        sliderTrack.setArcHeight(4);

        var sliderThumb = new Rectangle(sliderX - 6, sliderY + sliderH / 2 - 8, 20, 16);
        sliderThumb.setFill(COLOR_SLICE);
        sliderThumb.setStroke(COLOR_SLICE.darker());
        sliderThumb.setArcWidth(4);
        sliderThumb.setArcHeight(4);

        var sliderLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.wavelength.slice"), sliderX + 4, sliderY + sliderH + 25, 10, false, COLOR_SLICE);

        var sliderElements = List.of(sliderTrack, sliderThumb, sliderLabel);
        pane.getChildren().addAll(sliderElements);

        // Arrow to result
        var arrowElements = createArrow(sliderX + 50, sliderY + sliderH / 2, 160, sliderY + sliderH / 2, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(arrowElements);

        // Right side: Isometric disk surface (like the actual 3D view)
        double surfaceX = 200;
        double surfaceY = 40;
        double surfaceW = 280;
        double surfaceH = 160;

        // Base platform (isometric)
        var basePlatform = new Polygon(
                surfaceX + surfaceW / 2, surfaceY + surfaceH,
                surfaceX, surfaceY + surfaceH * 0.6,
                surfaceX + surfaceW / 2, surfaceY + surfaceH * 0.2,
                surfaceX + surfaceW, surfaceY + surfaceH * 0.6
        );
        basePlatform.setFill(Color.rgb(30, 50, 100, 0.8));
        basePlatform.setStroke(Color.rgb(60, 100, 180));
        basePlatform.setStrokeWidth(1);

        // Disk surface with intensity variation (ellipse for isometric view)
        double diskCX = surfaceX + surfaceW / 2;
        double diskCY = surfaceY + surfaceH * 0.55;
        double diskRX = surfaceW * 0.35;
        double diskRY = surfaceH * 0.25;

        // Create disk with gradient showing intensity
        var diskGradient = new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(100, 255, 100)),
                new Stop(0.3, Color.rgb(150, 255, 100)),
                new Stop(0.6, Color.rgb(200, 200, 50)),
                new Stop(0.85, Color.rgb(50, 150, 200)),
                new Stop(1.0, Color.rgb(30, 80, 150))
        );
        var diskSurface = new javafx.scene.shape.Ellipse(diskCX, diskCY, diskRX, diskRY);
        diskSurface.setFill(diskGradient);
        diskSurface.setStroke(Color.rgb(100, 200, 100));
        diskSurface.setStrokeWidth(2);

        // Animated intensity spikes that change with wavelength
        List<Line> spikeElements = new ArrayList<>();
        double[] spikeXs = {diskCX - 35, diskCX + 25, diskCX - 15, diskCX + 45, diskCX + 5};
        double[] spikeYs = {diskCY - 8, diskCY + 8, diskCY + 12, diskCY - 3, diskCY - 15};
        double[] spikeBaseHeights = {20, 25, 15, 30, 22};
        for (int i = 0; i < spikeXs.length; i++) {
            var spike = new Line(spikeXs[i], spikeYs[i], spikeXs[i], spikeYs[i] - spikeBaseHeights[i]);
            spike.setStroke(Color.rgb(150, 255, 100, 0.8));
            spike.setStrokeWidth(2);
            spikeElements.add(spike);
        }

        var surfaceElements = new ArrayList<Node>();
        surfaceElements.add(basePlatform);
        surfaceElements.add(diskSurface);
        surfaceElements.addAll(spikeElements);
        pane.getChildren().addAll(surfaceElements);

        var resultLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.result"), diskCX, surfaceY + surfaceH + 20, 11, false, COLOR_HIGHLIGHT);
        pane.getChildren().add(resultLabel);

        // Explanation box
        var explainBox = new Rectangle(60, 230, 460, 85);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.explain1"), 290, 250, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.explain2"), 290, 270, 11, false, COLOR_HIGHLIGHT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.explain3"), 290, 295, 10, false, COLOR_TEXT_DIM);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        // Store spike base Y values and height variations for animation
        double[] spikeHeightsAtTop = {35, 10, 25, 15, 40};    // Heights when slider at top (blue wing)
        double[] spikeHeightsAtMid = {20, 25, 15, 30, 22};    // Heights when slider at middle
        double[] spikeHeightsAtBot = {10, 40, 30, 20, 12};    // Heights when slider at bottom (red wing)

        Runnable resetElements = () -> {
            sliderElements.forEach(n -> n.setOpacity(0));
            sliderThumb.setY(sliderY + sliderH / 2 - 8);
            arrowElements.forEach(n -> n.setOpacity(0));
            surfaceElements.forEach(n -> n.setOpacity(0));
            resultLabel.setOpacity(0);
            explainElements.forEach(n -> n.setOpacity(0));
            // Reset spike heights
            for (int i = 0; i < spikeElements.size(); i++) {
                spikeElements.get(i).setEndY(spikeYs[i] - spikeHeightsAtMid[i]);
            }
        };

        resetElements.run();

        var animation = new Timeline();
        var t = 0.2;

        // Show slider
        HelpAnimationController.addFadeIn(animation, sliderElements, t, 0.3);
        t += 0.4;

        // Animate slider moving up and down, with spikes changing height
        double sliderAnimDuration = 3.0;
        double sliderRange = sliderH - 16;
        double baseThumbY = sliderY + sliderH / 2 - 8;

        // Slider at middle
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t),
                new KeyValue(sliderThumb.yProperty(), baseThumbY)));
        for (int i = 0; i < spikeElements.size(); i++) {
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t),
                    new KeyValue(spikeElements.get(i).endYProperty(), spikeYs[i] - spikeHeightsAtMid[i])));
        }

        // Slider at top (blue wing)
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.25),
                new KeyValue(sliderThumb.yProperty(), sliderY, Interpolator.EASE_BOTH)));
        for (int i = 0; i < spikeElements.size(); i++) {
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.25),
                    new KeyValue(spikeElements.get(i).endYProperty(), spikeYs[i] - spikeHeightsAtTop[i], Interpolator.EASE_BOTH)));
        }

        // Slider back to middle
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.5),
                new KeyValue(sliderThumb.yProperty(), baseThumbY, Interpolator.EASE_BOTH)));
        for (int i = 0; i < spikeElements.size(); i++) {
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.5),
                    new KeyValue(spikeElements.get(i).endYProperty(), spikeYs[i] - spikeHeightsAtMid[i], Interpolator.EASE_BOTH)));
        }

        // Slider at bottom (red wing)
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.75),
                new KeyValue(sliderThumb.yProperty(), sliderY + sliderRange, Interpolator.EASE_BOTH)));
        for (int i = 0; i < spikeElements.size(); i++) {
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.75),
                    new KeyValue(spikeElements.get(i).endYProperty(), spikeYs[i] - spikeHeightsAtBot[i], Interpolator.EASE_BOTH)));
        }

        // Slider back to middle
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration),
                new KeyValue(sliderThumb.yProperty(), baseThumbY, Interpolator.EASE_BOTH)));
        for (int i = 0; i < spikeElements.size(); i++) {
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration),
                    new KeyValue(spikeElements.get(i).endYProperty(), spikeYs[i] - spikeHeightsAtMid[i], Interpolator.EASE_BOTH)));
        }

        t += 0.3;

        // Show arrow
        HelpAnimationController.addFadeIn(animation, arrowElements, t, 0.3);
        t += 0.4;

        // Show surface
        HelpAnimationController.addFadeIn(animation, surfaceElements, t, 0.4);
        HelpAnimationController.addFadeIn(animation, resultLabel, t, 0.6);
        t += sliderAnimDuration;

        // Show explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.3);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase3Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;
        var sliceColor = Color.rgb(100, 255, 100);

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // Left side: Slider representation
        double sliderX = 60;
        double sliderY = 60;
        double sliderW = 120;

        var sliderTrack = new Rectangle(sliderX, sliderY, sliderW, 8);
        sliderTrack.setFill(Color.rgb(60, 60, 80));
        sliderTrack.setStroke(Color.rgb(100, 100, 120));
        sliderTrack.setArcWidth(4);
        sliderTrack.setArcHeight(4);

        var sliderThumb = new Rectangle(sliderX + sliderW / 2 - 8, sliderY - 6, 16, 20);
        sliderThumb.setFill(sliceColor);
        sliderThumb.setStroke(sliceColor.darker());
        sliderThumb.setArcWidth(4);
        sliderThumb.setArcHeight(4);

        var sliderLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.frame.slice"), sliderX + sliderW / 2, sliderY + 35, 10, false, sliceColor);

        var sliderElements = List.of(sliderTrack, sliderThumb, sliderLabel);
        pane.getChildren().addAll(sliderElements);

        // Arrow to result
        var arrowElements = createArrow(sliderX + sliderW + 30, sliderY + 4, sliderX + sliderW + 80, sliderY + 4, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(arrowElements);

        // Right side: 3D profile surface (similar to 3D spectral profile)
        double surfaceX = 220;
        double surfaceY = 40;
        double surfaceW = 280;
        double surfaceH = 160;

        // Base platform (isometric)
        var basePlatform = new Polygon(
                surfaceX, surfaceY + surfaceH,
                surfaceX + surfaceW * 0.3, surfaceY + surfaceH + 30,
                surfaceX + surfaceW, surfaceY + surfaceH + 30,
                surfaceX + surfaceW - surfaceW * 0.3, surfaceY + surfaceH
        );
        basePlatform.setFill(Color.rgb(30, 50, 100, 0.8));
        basePlatform.setStroke(Color.rgb(60, 100, 180));
        basePlatform.setStrokeWidth(1);

        // Create 3D profile surface with Gaussian absorption valley
        var surfaceElements = new ArrayList<Node>();
        surfaceElements.add(basePlatform);

        // Background color for fill (matches diagram background)
        var bgColor = Color.rgb(26, 26, 36);

        // Draw profile curves (back to front for proper occlusion)
        int numProfiles = 8;
        double profileWidth = 200;
        double intensityScale = 70;
        double perspectiveShiftX = 10;
        double perspectiveShiftY = -12;
        double profileBaseX = surfaceX + 30;
        double profileBaseY = surfaceY + surfaceH - 10;

        for (int i = numProfiles - 1; i >= 0; i--) {
            double profileX = profileBaseX + i * perspectiveShiftX;
            double profileY = profileBaseY + i * perspectiveShiftY;

            // Absorption dip position varies slightly across profiles (curved valley)
            double dipCenter = 0.5 + 0.08 * Math.sin(Math.PI * i / (numProfiles - 1));

            // Generate Gaussian profile points
            int numPoints = 40;
            var polygonPoints = new double[(numPoints + 2) * 2];

            // Start at baseline left
            polygonPoints[0] = profileX;
            polygonPoints[1] = profileY;

            // Profile curve points using Gaussian absorption
            for (int p = 0; p < numPoints; p++) {
                double t = (double) p / (numPoints - 1);
                double x = profileX + t * profileWidth;

                // Gaussian absorption dip
                double distFromDip = t - dipCenter;
                double intensity = 1.0 - 0.65 * Math.exp(-distFromDip * distFromDip / 0.012);

                double y = profileY - intensity * intensityScale;
                polygonPoints[(p + 1) * 2] = x;
                polygonPoints[(p + 1) * 2 + 1] = y;
            }

            // End at baseline right
            polygonPoints[(numPoints + 1) * 2] = profileX + profileWidth;
            polygonPoints[(numPoints + 1) * 2 + 1] = profileY;

            // Create filled polygon for occlusion
            var fillPolygon = new Polygon(polygonPoints);
            fillPolygon.setFill(bgColor);
            fillPolygon.setStroke(null);

            // Create profile line on top
            var linePoints = new double[numPoints * 2];
            for (int p = 0; p < numPoints; p++) {
                linePoints[p * 2] = polygonPoints[(p + 1) * 2];
                linePoints[p * 2 + 1] = polygonPoints[(p + 1) * 2 + 1];
            }
            var profileLine = new Polyline(linePoints);

            // Color gradient based on depth (rainbow, back profiles dimmer)
            double hue = 120 - (numProfiles - 1 - i) * 15;
            double brightness = 0.5 + 0.5 * (1.0 - (double) i / numProfiles);
            profileLine.setStroke(Color.hsb(hue, 0.8, brightness));
            profileLine.setStrokeWidth(2.0 - i * 0.1);
            profileLine.setFill(null);

            surfaceElements.add(fillPolygon);
            surfaceElements.add(profileLine);
        }

        pane.getChildren().addAll(surfaceElements);

        var resultLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.result"), surfaceX + surfaceW / 2 + 30, surfaceY + surfaceH + 50, 11, false, COLOR_HIGHLIGHT);
        pane.getChildren().add(resultLabel);

        // Explanation box
        var explainBox = new Rectangle(60, 270, 460, 80);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.explain1"), 290, 288, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.explain2"), 290, 308, 11, false, COLOR_HIGHLIGHT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.explain3"), 290, 333, 10, false, COLOR_TEXT_DIM);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            sliderElements.forEach(n -> n.setOpacity(0));
            sliderThumb.setX(sliderX + sliderW / 2 - 8);
            arrowElements.forEach(n -> n.setOpacity(0));
            surfaceElements.forEach(n -> n.setOpacity(0));
            resultLabel.setOpacity(0);
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();
        var t = 0.2;

        // Show slider
        HelpAnimationController.addFadeIn(animation, sliderElements, t, 0.3);
        t += 0.4;

        // Animate slider moving left and right
        double sliderAnimDuration = 3.0;
        double sliderRange = sliderW - 16;
        double baseThumbX = sliderX + sliderW / 2 - 8;

        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t),
                new KeyValue(sliderThumb.xProperty(), baseThumbX)));
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.25),
                new KeyValue(sliderThumb.xProperty(), sliderX, Interpolator.EASE_BOTH)));
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.5),
                new KeyValue(sliderThumb.xProperty(), baseThumbX, Interpolator.EASE_BOTH)));
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration * 0.75),
                new KeyValue(sliderThumb.xProperty(), sliderX + sliderRange, Interpolator.EASE_BOTH)));
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + sliderAnimDuration),
                new KeyValue(sliderThumb.xProperty(), baseThumbX, Interpolator.EASE_BOTH)));

        t += 0.3;

        // Show arrow
        HelpAnimationController.addFadeIn(animation, arrowElements, t, 0.3);
        t += 0.4;

        // Show surface
        HelpAnimationController.addFadeIn(animation, surfaceElements, t, 0.4);
        HelpAnimationController.addFadeIn(animation, resultLabel, t, 0.6);
        t += sliderAnimDuration;

        // Show explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.3);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private List<Node> createArrow(double startX, double startY, double endX, double endY, Color color) {
        var line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(2);

        var angle = Math.atan2(endY - startY, endX - startX);
        var arrowSize = 8;

        var arrowHead = new Polygon(
                endX, endY,
                endX - arrowSize * Math.cos(angle - Math.PI / 6), endY - arrowSize * Math.sin(angle - Math.PI / 6),
                endX - arrowSize * Math.cos(angle + Math.PI / 6), endY - arrowSize * Math.sin(angle + Math.PI / 6)
        );
        arrowHead.setFill(color);

        return List.of(line, arrowHead);
    }
}
