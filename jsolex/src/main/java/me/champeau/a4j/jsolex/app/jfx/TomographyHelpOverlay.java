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
import javafx.scene.Cursor;
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
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.ArrayList;
import java.util.List;

/**
 * Help overlay for the spherical tomography 3D viewer.
 * Displays a 4-phase animated diagram explaining how tomography works:
 * 1. Height vs Wavelength relationship
 * 2. 5 images at different pixel shifts
 * 3. Projection onto hemispheres
 * 4. Blending layers into tomography
 */
public class TomographyHelpOverlay extends AbstractHelpOverlay {

    private static final double DIAGRAM_WIDTH = 580;
    private static final double DIAGRAM_HEIGHT = 380;
    private static final Duration DISPLAY_DURATION = Duration.seconds(8);

    private static final String I18N_BUNDLE = "spherical-tomography";
    private static final String VIEWER_ID = "tomography";

    // Colors matching the HTML prototype
    private static final Color COLOR_PHOTOSPHERE = Color.rgb(139, 115, 85);
    private static final Color COLOR_CHROMOSPHERE = Color.rgb(255, 102, 51);
    private static final Color COLOR_H_ALPHA = Color.rgb(204, 51, 51);
    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_TEXT_DIM = Color.rgb(136, 136, 136);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);

    // Layer colors: 0 Å (outermost/blue) to 1.5 Å (innermost/red) - matches real sphere colormap
    private static final Color[] LAYER_COLORS = {
            Color.rgb(60, 100, 255),   // 0 Å - outermost (blue - highest in chromosphere)
            Color.rgb(140, 80, 200),   // 0.4 Å
            Color.rgb(200, 80, 150),   // 0.8 Å
            Color.rgb(230, 80, 80),    // 1.2 Å
            Color.rgb(255, 100, 60)    // 1.5 Å - innermost (red - closest to photosphere)
    };

    private HelpAnimationController popupDiagramController;
    private HelpAnimationController maximizedController;

    public TomographyHelpOverlay() {
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

    private static final String ILLUSTRATION_TOKEN = "%ILLUSTRATION%";

    @Override
    protected StackPane createHelpPopup() {
        var titleLabel = new Label(I18N.string(JSolEx.class, I18N_BUNDLE, "help.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        var helpText = I18N.string(JSolEx.class, I18N_BUNDLE, "help.text");

        // Create the popup's diagram and store its controller
        var popupDiagramResult = createTomographyDiagramWithController();
        popupDiagramController = popupDiagramResult.controller();

        // For maximized view, create a fresh diagram with its own controller
        var diagram = createScalableDiagram(
                popupDiagramResult.diagram(),
                () -> {
                    var result = createTomographyDiagramWithController();
                    // Store the maximized controller temporarily to control it
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

        // Build content box - insert diagram at %ILLUSTRATION% position if present
        VBox contentBox;
        if (helpText.contains(ILLUSTRATION_TOKEN)) {
            var parts = helpText.split(ILLUSTRATION_TOKEN, 2);
            var textFlowBefore = parseFormattedText(parts[0].trim());
            textFlowBefore.setMaxWidth(700);
            var textFlowAfter = parseFormattedText(parts[1].trim());
            textFlowAfter.setMaxWidth(700);
            contentBox = new VBox(15, titleLabel, textFlowBefore, diagram, textFlowAfter, buttonBox);
        } else {
            var textFlow = parseFormattedText(helpText);
            textFlow.setMaxWidth(700);
            contentBox = new VBox(15, titleLabel, textFlow, diagram, buttonBox);
        }
        contentBox.setPadding(new Insets(20));

        var scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setMaxHeight(680);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        var content = createContentPane(scrollPane, 780, 720);
        return wrapInOverlay(content);
    }

    private record DiagramResult(Node diagram, HelpAnimationController controller) {}

    private DiagramResult createTomographyDiagramWithController() {
        var diagramPane = new StackPane();
        diagramPane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setStyle("-fx-background-color: #1a1a24; -fx-background-radius: 8;");

        // Create the 4 phase panes with their internal animations
        var phase1Result = createPhase1Pane();
        var phase2Result = createPhase2Pane();
        var phase3Result = createPhase3Pane();
        var phase4Result = createPhase4Pane();

        var phase1 = phase1Result.pane();
        var phase2 = phase2Result.pane();
        var phase3 = phase3Result.pane();
        var phase4 = phase4Result.pane();

        var localPhaseAnimations = List.of(
                phase1Result.animation(),
                phase2Result.animation(),
                phase3Result.animation(),
                phase4Result.animation()
        );

        var localPhaseResetters = List.of(
                phase1Result.resetElements(),
                phase2Result.resetElements(),
                phase3Result.resetElements(),
                phase4Result.resetElements()
        );

        var localPhases = List.of(phase1, phase2, phase3, phase4);

        // Initially show phase 1, hide others
        phase1.setOpacity(1.0);
        phase2.setOpacity(0.0);
        phase3.setOpacity(0.0);
        phase4.setOpacity(0.0);

        diagramPane.getChildren().addAll(phase4, phase3, phase2, phase1);

        // Create phase indicators - we'll wire up the controller after creating it
        var localPhaseIndicators = new HBox(8);
        localPhaseIndicators.setAlignment(Pos.CENTER);

        // Create controller
        var controller = new HelpAnimationController(
                localPhaseAnimations,
                localPhaseResetters,
                localPhases,
                localPhaseIndicators,
                DISPLAY_DURATION
        );

        // Now create the phase indicator dots with click handlers that use the controller
        for (var i = 0; i < 4; i++) {
            var dot = new Circle(6);
            dot.setFill(i == 0 ? Color.WHITE : Color.gray(0.4));
            dot.setId("phase-indicator-" + i);
            dot.setCursor(Cursor.HAND);
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

        // Create the animation
        controller.createDiagramAnimation();

        var container = new VBox(8, diagramPane, localPhaseIndicators);
        container.setAlignment(Pos.CENTER);

        return new DiagramResult(container, controller);
    }


    private HelpAnimationController.PhaseResult createPhase1Pane() {
        // Height vs Wavelength diagram
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        // Title
        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase1.title"), centerX, 25, 13, false, Color.gray(0.75));

        // Photosphere (convex curved edge on left)
        var photosphereArc = new Arc(-180, 170, 300, 300, -25, 50);
        photosphereArc.setType(ArcType.OPEN);
        photosphereArc.setFill(Color.TRANSPARENT);
        photosphereArc.setStroke(COLOR_PHOTOSPHERE);
        photosphereArc.setStrokeWidth(3);

        var photosphereLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.photosphere"), 70, 310, 11, false, COLOR_PHOTOSPHERE);

        // Height scale
        double scaleStartX = 150;
        double scaleEndX = 530;
        double scaleY = 70;

        var scaleLine = new Line(scaleStartX, scaleY, scaleEndX, scaleY);
        scaleLine.setStroke(COLOR_TEXT);
        scaleLine.setStrokeWidth(2);

        pane.getChildren().addAll(photosphereArc, photosphereLabel, scaleLine, title);

        // Height markers
        var heights = new int[]{0, 500, 1000, 1500, 2000};
        for (var i = 0; i < heights.length; i++) {
            var x = scaleStartX + (i / (double) (heights.length - 1)) * (scaleEndX - scaleStartX);
            var tick = new Line(x, scaleY - 8, x, scaleY + 8);
            tick.setStroke(COLOR_TEXT);
            tick.setStrokeWidth(2);
            var label = createDiagramText(heights[i] + " km", x, scaleY - 15, 10, false, COLOR_TEXT_DIM);
            pane.getChildren().addAll(tick, label);
        }

        // Hα full range bar
        var haStart = scaleStartX + 30;
        var haEnd = scaleEndX - 30;
        var haY = scaleY + 60;

        var haLine = new Line(haStart, haY, haEnd, haY);
        haLine.setStroke(COLOR_H_ALPHA);
        haLine.setStrokeWidth(5);

        var haStartTick = new Line(haStart, haY - 15, haStart, haY + 15);
        haStartTick.setStroke(COLOR_H_ALPHA);
        haStartTick.setStrokeWidth(2);

        var haEndTick = new Line(haEnd, haY - 15, haEnd, haY + 15);
        haEndTick.setStroke(COLOR_H_ALPHA);
        haEndTick.setStrokeWidth(2);

        var haLabel = createDiagramText("Hα", (haStart + haEnd) / 2, haY - 15, 14, false, COLOR_H_ALPHA);

        var haElements = List.of(haLine, haStartTick, haEndTick, haLabel);
        pane.getChildren().addAll(haElements);

        // Hα wings (left side, lower)
        var wingsEnd = haStart + 120;
        var wingsY = haY + 45;

        var wingsLine = new Line(haStart, wingsY, wingsEnd, wingsY);
        wingsLine.setStroke(COLOR_H_ALPHA);
        wingsLine.setStrokeWidth(4);
        wingsLine.setOpacity(0.6);

        var wingsLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.wings"), (haStart + wingsEnd) / 2, wingsY + 20, 11, false, COLOR_H_ALPHA);
        var wingsSubLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.lower.altitude"), (haStart + wingsEnd) / 2, wingsY + 35, 9, false, COLOR_TEXT_DIM);

        var wingsElements = List.of(wingsLine, wingsLabel, wingsSubLabel);
        pane.getChildren().addAll(wingsElements);

        // Hα center (right side, higher)
        var centerStart = haEnd - 180;
        var centerLineY = haY + 45;

        var centerLine = new Line(centerStart, centerLineY, haEnd, centerLineY);
        centerLine.setStroke(COLOR_H_ALPHA);
        centerLine.setStrokeWidth(4);

        var centerLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.center"), (centerStart + haEnd) / 2, centerLineY + 20, 11, false, COLOR_H_ALPHA);
        var centerSubLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.higher.altitude"), (centerStart + haEnd) / 2, centerLineY + 35, 9, false, COLOR_TEXT_DIM);

        var centerElements = List.of(centerLine, centerLabel, centerSubLabel);
        pane.getChildren().addAll(centerElements);

        // Explanation box
        var explainBox = new Rectangle(120, 235, 400, 80);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase1.explain1"), 320, 260, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase1.explain2"), 320, 280, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase1.explain3"), 320, 303, 12, false, COLOR_HIGHLIGHT);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        // Reset function for all animated elements
        Runnable resetElements = () -> {
            haElements.forEach(n -> n.setOpacity(0));
            wingsElements.forEach(n -> n.setOpacity(0));
            centerElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        // Set initial opacity to 0 for animated elements
        resetElements.run();

        // Create internal animation - phase 1 has longer delays for initial viewing
        var animation = new Timeline();
        var t = 0.5; // Initial delay before first element

        // Fade in Hα bar
        HelpAnimationController.addFadeIn(animation, haElements, t, 0.5);
        t += 1.2;

        // Fade in wings (wingsLine has special opacity 0.6)
        HelpAnimationController.addFadeIn(animation, wingsLabel, t, 0.5);
        HelpAnimationController.addFadeIn(animation, wingsSubLabel, t, 0.5);
        HelpAnimationController.addFadeInTo(animation, wingsLine, t, 0.5, 0.6);
        t += 1.2;

        // Fade in center
        HelpAnimationController.addFadeIn(animation, centerElements, t, 0.5);
        t += 1.2;

        // Fade in explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.5);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase2Pane() {
        // 5 images at different pixel shifts
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase2.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // 5 images
        double[] shifts = {0, 0.4, 0.8, 1.2, 1.5};
        double imgWidth = 80;
        double imgHeight = 80;
        double startX = 60;
        double spacing = 100;
        double y = 75;

        List<List<Node>> imageGroups = new ArrayList<>();

        for (var i = 0; i < shifts.length; i++) {
            var x = startX + i * spacing;
            List<Node> group = new ArrayList<>();

            // Image rectangle
            var rect = new Rectangle(x, y, imgWidth, imgHeight);
            rect.setFill(LAYER_COLORS[i].deriveColor(0, 1, 1, 0.3 + shifts[i] * 0.1));
            rect.setStroke(COLOR_CHROMOSPHERE);
            rect.setStrokeWidth(2);
            group.add(rect);

            // Sun circle inside
            var sun = new Circle(x + imgWidth / 2, y + imgHeight / 2, 30);
            sun.setFill(LAYER_COLORS[i].deriveColor(0, 1, 1, 0.7 + shifts[i] * 0.2));
            group.add(sun);

            // Wavelength label
            var shiftText = shifts[i] == 0 ? "0 Å" : String.format("+%.1f Å", shifts[i]);
            var shiftLabel = createDiagramText(shiftText, x + imgWidth / 2, y + imgHeight + 20, 11, false, COLOR_TEXT);
            group.add(shiftLabel);

            // Height indication
            if (shifts[i] == 0) {
                var heightText = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.highest"), x + imgWidth / 2, y + imgHeight + 35, 10, false, COLOR_HIGHLIGHT);
                group.add(heightText);
            } else if (shifts[i] == 1.5) {
                var heightText = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.lowest"), x + imgWidth / 2, y + imgHeight + 35, 10, false, COLOR_HIGHLIGHT);
                group.add(heightText);
            }

            pane.getChildren().addAll(group);
            imageGroups.add(group);
        }

        // Arrow showing direction
        var arrowY = y + imgHeight + 55;
        var arrowLine = new Line(startX + imgWidth / 2, arrowY, startX + 4 * spacing + imgWidth / 2, arrowY);
        arrowLine.setStroke(COLOR_TEXT_DIM);
        arrowLine.setStrokeWidth(2);

        var arrowHead1 = new Line(startX + 4 * spacing + imgWidth / 2 - 10, arrowY - 5, startX + 4 * spacing + imgWidth / 2, arrowY);
        arrowHead1.setStroke(COLOR_TEXT_DIM);
        arrowHead1.setStrokeWidth(2);
        var arrowHead2 = new Line(startX + 4 * spacing + imgWidth / 2 - 10, arrowY + 5, startX + 4 * spacing + imgWidth / 2, arrowY);
        arrowHead2.setStroke(COLOR_TEXT_DIM);
        arrowHead2.setStrokeWidth(2);

        var lineCenterLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.line.center"), startX + imgWidth / 2, arrowY + 18, 10, false, COLOR_HIGHLIGHT);
        var wingLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.wing"), startX + 4 * spacing + imgWidth / 2, arrowY + 18, 10, false, COLOR_TEXT_DIM);

        var arrowElements = List.of(arrowLine, arrowHead1, arrowHead2, lineCenterLabel, wingLabel);
        pane.getChildren().addAll(arrowElements);

        // Explanation box - positioned below the arrow labels (which end around y=228)
        var explainBox = new Rectangle(60, 260, 460, 95);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase2.explain1"), 290, 285, 12, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase2.explain2"), 290, 305, 12, false, COLOR_TEXT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase2.explain3"), 290, 330, 11, false, COLOR_HIGHLIGHT);
        var explain4 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase2.explain4"), 290, 348, 11, false, COLOR_TEXT_DIM);

        var explainElements = List.of(explainBox, explain1, explain2, explain3, explain4);
        pane.getChildren().addAll(explainElements);

        // Reset function for all animated elements
        Runnable resetElements = () -> {
            imageGroups.forEach(group -> group.forEach(n -> n.setOpacity(0)));
            arrowElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        // Set initial opacity to 0 for animated elements
        resetElements.run();

        // Create internal animation - images appear one by one
        var animation = new Timeline();
        double t = 0.3;

        for (var group : imageGroups) {
            HelpAnimationController.addFadeIn(animation, group, t, 0.3);
            t += 0.4;
        }

        // Fade in arrow
        HelpAnimationController.addFadeIn(animation, arrowElements, t, 0.3);
        t += 0.5;

        // Fade in explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase3Pane() {
        // Project each layer to sphere
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase3.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        double[] shifts = {0, 0.4, 0.8, 1.2, 1.5};

        // Left side: small 2D images (in inner to outer order for animation visual)
        double imgSize = 45;
        double imgStartX = 30;
        double imgStartY = 60;
        double imgSpacing = 55;

        // Animation order: inner to outer (1.5 Å first, 0 Å last)
        var animOrder = new int[]{4, 3, 2, 1, 0};

        List<List<Node>> layerGroups = new ArrayList<>();

        // Right side: concentric spheres (centered vertically with arrow)
        double sphereX = 380;
        double sphereY = 190;
        double baseRadius = 50;
        double radiusStep = 16;

        for (var step = 0; step < animOrder.length; step++) {
            var i = animOrder[step];
            List<Node> group = new ArrayList<>();

            // 2D image on left
            var imgY = imgStartY + step * imgSpacing;

            var rect = new Rectangle(imgStartX, imgY, imgSize, imgSize);
            rect.setFill(LAYER_COLORS[i].deriveColor(0, 1, 1, 0.25));
            rect.setStroke(LAYER_COLORS[i]);
            rect.setStrokeWidth(2);
            group.add(rect);

            var miniSun = new Circle(imgStartX + imgSize / 2, imgY + imgSize / 2, 15);
            miniSun.setFill(LAYER_COLORS[i].deriveColor(0, 1, 1, 0.6));
            group.add(miniSun);

            var shiftLabel = shifts[i] == 0 ? "0 Å" : String.format("+%.1f Å", shifts[i]);
            var label = createDiagramText(shiftLabel, imgStartX + imgSize + 35, imgY + imgSize / 2 + 4, 9, false, COLOR_TEXT);
            label.setTextAlignment(TextAlignment.LEFT);
            group.add(label);

            // Sphere on right
            var sphereRadius = baseRadius + (4 - i) * radiusStep;

            var sphere = new Circle(sphereX, sphereY, sphereRadius);
            sphere.setFill(LAYER_COLORS[i].deriveColor(0, 1, 1, 0.2));
            sphere.setStroke(LAYER_COLORS[i]);
            sphere.setStrokeWidth(2);
            group.add(sphere);

            // Grid lines for 3D sphere effect
            addSphereGridLines(group, sphereX, sphereY, sphereRadius, LAYER_COLORS[i]);

            pane.getChildren().addAll(group);
            layerGroups.add(group);
        }

        // Height arrow
        var arrowX = sphereX + baseRadius + 4 * radiusStep + 25;
        var heightLine = new Line(arrowX, sphereY + baseRadius, arrowX, sphereY - baseRadius - 4 * radiusStep);
        heightLine.setStroke(COLOR_HIGHLIGHT);
        heightLine.setStrokeWidth(2);

        var arrowHead1 = new Line(arrowX - 5, sphereY - baseRadius - 4 * radiusStep + 10, arrowX, sphereY - baseRadius - 4 * radiusStep);
        arrowHead1.setStroke(COLOR_HIGHLIGHT);
        arrowHead1.setStrokeWidth(2);
        var arrowHead2 = new Line(arrowX + 5, sphereY - baseRadius - 4 * radiusStep + 10, arrowX, sphereY - baseRadius - 4 * radiusStep);
        arrowHead2.setStroke(COLOR_HIGHLIGHT);
        arrowHead2.setStrokeWidth(2);

        var heightLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.height"), arrowX, sphereY - baseRadius - 4 * radiusStep - 12, 10, false, COLOR_HIGHLIGHT);

        var arrowElements = List.of(heightLine, arrowHead1, arrowHead2, heightLabel);
        pane.getChildren().addAll(arrowElements);

        // Explanation box
        var explainBox = new Rectangle(20, 345, 540, 30);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase3.explain"), 290, 365, 11, false, COLOR_TEXT);

        var explainElements = List.of(explainBox, explain);
        pane.getChildren().addAll(explainElements);

        // Reset function for all animated elements
        Runnable resetElements = () -> {
            layerGroups.forEach(group -> group.forEach(n -> n.setOpacity(0)));
            arrowElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        // Set initial opacity to 0 for animated elements
        resetElements.run();

        // Create internal animation - layers appear from inner to outer
        var animation = new Timeline();
        double t = 0.3;

        for (var group : layerGroups) {
            HelpAnimationController.addFadeIn(animation, group, t, 0.4);
            t += 0.5;
        }

        // Fade in arrow
        HelpAnimationController.addFadeIn(animation, arrowElements, t, 0.3);
        t += 0.4;

        // Fade in explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase4Pane() {
        // Final result - layers merging into blended tomography
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase4.title"), DIAGRAM_WIDTH / 2, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        var centerX = DIAGRAM_WIDTH / 2;
        double centerY = 175;
        double baseRadius = 50;
        double radiusStep = 16;
        var finalRadius = baseRadius; // Inner sphere radius - outer spheres collapse to this

        // Create 5 concentric spheres (will animate to merge)
        List<Circle> spheres = new ArrayList<>();
        var initialRadii = new double[5];

        // Create spheres from inner to outer (index 4 = innermost/red, index 0 = outermost/blue)
        for (var i = 4; i >= 0; i--) {
            var radius = baseRadius + (4 - i) * radiusStep;
            initialRadii[4 - i] = radius;

            var sphere = new Circle(centerX, centerY, radius);
            sphere.setFill(LAYER_COLORS[i].deriveColor(0, 1, 1, 0.25));
            sphere.setStroke(LAYER_COLORS[i]);
            sphere.setStrokeWidth(2);
            spheres.add(sphere);
        }

        // Add spheres in reverse order so outer ones are behind inner ones
        for (var i = spheres.size() - 1; i >= 0; i--) {
            pane.getChildren().add(spheres.get(i));
        }

        // Create the final gradient sun disk (hidden initially, will fade in)
        var sunDisk = new Circle(centerX, centerY, finalRadius);
        var gradient = new RadialGradient(
                0, 0, centerX, centerY, finalRadius, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(200, 180, 100, 0.9)),
                new Stop(0.3, Color.rgb(150, 200, 150, 0.9)),
                new Stop(0.6, Color.rgb(100, 180, 200, 0.9)),
                new Stop(0.85, Color.rgb(60, 140, 220, 0.9)),
                new Stop(1.0, Color.rgb(40, 100, 255, 0.95))
        );
        sunDisk.setFill(gradient);
        sunDisk.setStroke(Color.rgb(60, 120, 255));
        sunDisk.setStrokeWidth(3);
        sunDisk.setOpacity(0);
        pane.getChildren().add(sunDisk);

        // Explanation text
        var explain = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "help.diagram.phase4.summary"), centerX, centerY + finalRadius + 35, 12, false, COLOR_TEXT);
        pane.getChildren().add(explain);

        // Reset function
        Runnable resetElements = () -> {
            for (var i = 0; i < spheres.size(); i++) {
                var sphere = spheres.get(i);
                sphere.setRadius(initialRadii[i]);
                sphere.setOpacity(0);
            }
            sunDisk.setOpacity(0);
            explain.setOpacity(0);
        };

        resetElements.run();

        // Create animation: spheres appear, merge together, then blend into gradient disk
        var animation = new Timeline();
        double t = 0.3;

        // Ensure elements that appear later are hidden from the start
        HelpAnimationController.hideAtStart(animation, sunDisk);

        // Phase 1: Spheres fade in (staggered, outer to inner)
        for (var i = spheres.size() - 1; i >= 0; i--) {
            HelpAnimationController.addFadeIn(animation, spheres.get(i), t, 0.25);
            t += 0.15;
        }
        t += 0.3;

        // Phase 2: Spheres merge (all radii animate to finalRadius)
        var mergeStart = t;
        var mergeDuration = 1.2;
        for (var i = 0; i < spheres.size(); i++) {
            var sphere = spheres.get(i);
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(mergeStart),
                    new KeyValue(sphere.radiusProperty(), initialRadii[i])));
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(mergeStart + mergeDuration),
                    new KeyValue(sphere.radiusProperty(), finalRadius, Interpolator.EASE_BOTH)));
        }
        t = mergeStart + mergeDuration;

        // Phase 3: Cross-fade from spheres to gradient disk
        var fadeStart = t;
        var fadeDuration = 0.6;
        for (var sphere : spheres) {
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(fadeStart),
                    new KeyValue(sphere.opacityProperty(), 1)));
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(fadeStart + fadeDuration),
                    new KeyValue(sphere.opacityProperty(), 0, Interpolator.EASE_IN)));
        }
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(fadeStart),
                new KeyValue(sunDisk.opacityProperty(), 0)));
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(fadeStart + fadeDuration),
                new KeyValue(sunDisk.opacityProperty(), 1, Interpolator.EASE_OUT)));
        t = fadeStart + fadeDuration + 0.2;

        // Phase 4: Show explanation
        HelpAnimationController.addFadeIn(animation, explain, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private void addSphereGridLines(List<Node> group, double centerX, double centerY, double radius, Color baseColor) {
        var gridColor = baseColor.deriveColor(0, 1, 1, 0.4);

        // Longitude lines (vertical ellipses at different "rotations")
        var longitudeWidths = new double[]{0.0, 0.35, 0.65, 0.85};
        for (var widthFactor : longitudeWidths) {
            var longitude = new Ellipse(centerX, centerY, radius * widthFactor, radius);
            longitude.setFill(Color.TRANSPARENT);
            longitude.setStroke(gridColor);
            longitude.setStrokeWidth(1);
            group.add(longitude);
        }

        // Latitude lines (horizontal ellipses at different heights)
        var latitudePositions = new double[]{-0.6, -0.3, 0.0, 0.3, 0.6};
        for (var yFactor : latitudePositions) {
            var latY = centerY + radius * yFactor;
            var latRadiusX = radius * Math.sqrt(1 - yFactor * yFactor);
            var latRadiusY = latRadiusX * 0.3;
            var latitude = new Ellipse(centerX, latY, latRadiusX, latRadiusY);
            latitude.setFill(Color.TRANSPARENT);
            latitude.setStroke(gridColor);
            latitude.setStrokeWidth(1);
            group.add(latitude);
        }
    }

}
