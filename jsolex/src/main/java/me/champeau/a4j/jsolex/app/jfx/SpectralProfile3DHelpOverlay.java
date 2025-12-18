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
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
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
 * Help overlay for the 3D spectral profile viewer.
 * Displays a 3-phase animated diagram explaining how the 3D profile is built:
 * 1. Averaged spectrum with curved absorption line
 * 2. Single column extraction showing intensity profile along wavelength axis
 * 3. Stacking columns to form 3D surface
 */
public class SpectralProfile3DHelpOverlay extends AbstractHelpOverlay {

    private static final double DIAGRAM_WIDTH = 580;
    private static final double DIAGRAM_HEIGHT = 340;
    private static final Duration DISPLAY_DURATION = Duration.seconds(9);

    private static final String I18N_BUNDLE = "spectral-profile-3d";
    private static final String VIEWER_ID = "spectral-profile-3d";

    private static final Color COLOR_CONTINUUM = Color.rgb(180, 180, 180);
    private static final Color COLOR_ABSORPTION = Color.rgb(40, 40, 40);
    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_TEXT_DIM = Color.rgb(136, 136, 136);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);
    private static final Color COLOR_COLUMN = Color.rgb(100, 200, 255);
    private static final Color COLOR_CURVE = Color.rgb(255, 100, 100);
    private static final Color COLOR_AXIS_X = Color.RED;
    private static final Color COLOR_AXIS_Y = Color.LIGHTGREEN;
    private static final Color COLOR_AXIS_Z = Color.DODGERBLUE;

    private HelpAnimationController popupDiagramController;
    private HelpAnimationController maximizedController;

    public SpectralProfile3DHelpOverlay() {
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
        scrollPane.setMaxHeight(620);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        var content = createContentPane(scrollPane, 750, 680);
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

        // Averaged spectrum frame - wide and short like real spectrum
        double frameX = 40;
        double frameY = 60;
        double frameW = 400;
        double frameH = 100;

        var spectrumBg = new Rectangle(frameX, frameY, frameW, frameH);
        spectrumBg.setFill(COLOR_CONTINUUM);
        spectrumBg.setStroke(COLOR_TEXT);
        spectrumBg.setStrokeWidth(1);

        // Curved absorption line - curves UPWARD (convex shape like real spectrum)
        var absorptionCurve = new CubicCurve(
                frameX, frameY + frameH / 2 + 8,
                frameX + frameW * 0.3, frameY + frameH / 2 - 10,
                frameX + frameW * 0.7, frameY + frameH / 2 - 12,
                frameX + frameW, frameY + frameH / 2 + 5
        );
        absorptionCurve.setFill(null);
        absorptionCurve.setStroke(COLOR_ABSORPTION);
        absorptionCurve.setStrokeWidth(10);

        // Secondary absorption lines (fainter, same curvature shape as main line)
        // Use CubicCurve with same relative control point offsets
        double line1Y = frameY + 25;
        var secondaryCurve1 = new CubicCurve(
                frameX, line1Y + 5,
                frameX + frameW * 0.3, line1Y - 8,
                frameX + frameW * 0.7, line1Y - 10,
                frameX + frameW, line1Y + 3
        );
        secondaryCurve1.setFill(null);
        secondaryCurve1.setStroke(Color.rgb(120, 120, 120));
        secondaryCurve1.setStrokeWidth(4);

        double line2Y = frameY + frameH - 25;
        var secondaryCurve2 = new CubicCurve(
                frameX, line2Y + 5,
                frameX + frameW * 0.3, line2Y - 8,
                frameX + frameW * 0.7, line2Y - 10,
                frameX + frameW, line2Y + 3
        );
        secondaryCurve2.setFill(null);
        secondaryCurve2.setStroke(Color.rgb(120, 120, 120));
        secondaryCurve2.setStrokeWidth(4);

        var spectrumElements = List.of(spectrumBg, absorptionCurve, secondaryCurve1, secondaryCurve2);
        pane.getChildren().addAll(spectrumElements);

        // Minimal labels
        var spectrumLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.averaged.spectrum"), frameX + frameW / 2, frameY + frameH + 20, 11, false, COLOR_TEXT);
        var xAxisLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.slit.position"), frameX + frameW / 2, frameY - 12, 10, false, COLOR_AXIS_X);
        var yAxisLabel = createDiagramText("λ", frameX + frameW + 20, frameY + frameH / 2, 12, false, COLOR_AXIS_Z);

        var labelElements = List.of(spectrumLabel, xAxisLabel, yAxisLabel);
        pane.getChildren().addAll(labelElements);

        // Mini column illustration on right - showing one column = intensity profile
        double miniX = 480;
        double miniY = 60;
        double miniW = 15;
        double miniH = 100;

        var miniColumn = new Rectangle(miniX, miniY, miniW, miniH);
        miniColumn.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, COLOR_CONTINUUM),
                new Stop(0.45, COLOR_CONTINUUM),
                new Stop(0.5, COLOR_ABSORPTION),
                new Stop(0.55, COLOR_CONTINUUM),
                new Stop(1, COLOR_CONTINUUM)
        ));
        miniColumn.setStroke(COLOR_COLUMN);
        miniColumn.setStrokeWidth(2);

        var miniLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.one.column"), miniX + miniW / 2, miniY + miniH + 18, 10, false, COLOR_COLUMN);

        var miniElements = List.of(miniColumn, miniLabel);
        pane.getChildren().addAll(miniElements);

        // Brief explanation
        var explain = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.explain"), centerX, 230, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase1.explain2"), centerX, 250, 11, false, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(explain, explain2);

        var explainElements = List.of(explain, explain2);

        Runnable resetElements = () -> {
            spectrumElements.forEach(n -> n.setOpacity(0));
            labelElements.forEach(n -> n.setOpacity(0));
            miniElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        HelpAnimationController.addFadeIn(animation, spectrumElements, t, 0.5);
        t += 0.7;

        HelpAnimationController.addFadeIn(animation, labelElements, t, 0.3);
        t += 0.5;

        HelpAnimationController.addFadeIn(animation, miniElements, t, 0.4);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase2Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // Column representation on left
        double colX = 60;
        double colY = 50;
        double colW = 30;
        double colH = 180;

        var columnBg = new Rectangle(colX, colY, colW, colH);
        columnBg.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, COLOR_CONTINUUM),
                new Stop(0.45, COLOR_CONTINUUM),
                new Stop(0.5, COLOR_ABSORPTION),
                new Stop(0.55, COLOR_CONTINUUM),
                new Stop(1, COLOR_CONTINUUM)
        ));
        columnBg.setStroke(COLOR_COLUMN);
        columnBg.setStrokeWidth(2);

        var columnLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.one.column"), colX + colW / 2, colY + colH + 18, 10, false, COLOR_COLUMN);
        var lambdaLabel = createDiagramText("λ", colX - 15, colY + colH / 2, 12, false, COLOR_AXIS_Z);

        pane.getChildren().addAll(columnBg, columnLabel, lambdaLabel);

        // Arrow to graph
        var arrowElements = createArrow(colX + colW + 25, colY + colH / 2, 170, colY + colH / 2, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(arrowElements);

        // Intensity profile graph - Gaussian-like curve (inverted - dip in center)
        double graphX = 200;
        double graphY = 50;
        double graphW = 200;
        double graphH = 180;

        var graphBg = new Rectangle(graphX, graphY, graphW, graphH);
        graphBg.setFill(Color.rgb(25, 25, 35));
        graphBg.setStroke(COLOR_TEXT);
        graphBg.setStrokeWidth(1);

        // Smooth Gaussian absorption profile using CubicCurve
        // High at edges, dip in center
        var gaussianCurve = new CubicCurve(
                graphX + graphW - 15, graphY + 15,              // Start: high intensity at top
                graphX + graphW - 15, graphY + graphH * 0.35,   // Control: coming down
                graphX + 25, graphY + graphH * 0.35,            // Control: at minimum
                graphX + 25, graphY + graphH / 2                // End: minimum in center
        );
        gaussianCurve.setFill(null);
        gaussianCurve.setStroke(COLOR_CURVE);
        gaussianCurve.setStrokeWidth(3);

        var gaussianCurve2 = new CubicCurve(
                graphX + 25, graphY + graphH / 2,               // Start: minimum in center
                graphX + 25, graphY + graphH * 0.65,            // Control: going up
                graphX + graphW - 15, graphY + graphH * 0.65,   // Control: reaching high
                graphX + graphW - 15, graphY + graphH - 15      // End: high intensity at bottom
        );
        gaussianCurve2.setFill(null);
        gaussianCurve2.setStroke(COLOR_CURVE);
        gaussianCurve2.setStrokeWidth(3);

        // Axis labels on graph
        var intensityLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.intensity"), graphX + graphW / 2, graphY - 12, 10, false, COLOR_CURVE);
        var wavelengthLabel2 = createDiagramText("λ", graphX - 12, graphY + graphH / 2, 11, false, COLOR_AXIS_Z);

        // Dip annotation
        var dipLine = new Line(graphX + 35, graphY + graphH / 2, graphX + 80, graphY + graphH / 2);
        dipLine.setStroke(COLOR_HIGHLIGHT);
        dipLine.setStrokeWidth(1);
        dipLine.getStrokeDashArray().addAll(4.0, 4.0);
        var dipLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.absorption"), graphX + 100, graphY + graphH / 2 + 4, 9, false, COLOR_HIGHLIGHT);

        var curveElements = List.of(graphBg, gaussianCurve, gaussianCurve2);
        var graphLabels = List.of(intensityLabel, wavelengthLabel2, dipLine, dipLabel);
        pane.getChildren().addAll(curveElements);
        pane.getChildren().addAll(graphLabels);

        // Simple explanation
        var explain = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.explain"), 300, 260, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase2.explain2"), 300, 280, 11, false, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(explain, explain2);

        var explainElements = List.of(explain, explain2);

        Runnable resetElements = () -> {
            columnBg.setOpacity(0);
            columnLabel.setOpacity(0);
            lambdaLabel.setOpacity(0);
            arrowElements.forEach(n -> n.setOpacity(0));
            curveElements.forEach(n -> n.setOpacity(0));
            graphLabels.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        // Show column
        var columnElements = List.of(columnBg, columnLabel, lambdaLabel);
        HelpAnimationController.addFadeIn(animation, columnElements, t, 0.4);
        t += 0.6;

        // Show arrow
        HelpAnimationController.addFadeIn(animation, arrowElements, t, 0.3);
        t += 0.5;

        // Show graph
        HelpAnimationController.addFadeIn(animation, curveElements, t, 0.5);
        t += 0.4;

        HelpAnimationController.addFadeIn(animation, graphLabels, t, 0.3);
        t += 0.5;

        // Show explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase3Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // Multiple 2D profiles (like Phase 2) stacked with 3D perspective
        // Each profile is rotated 90° left, so intensity extends horizontally (into depth)
        int numProfiles = 10;
        double baseX = 70;
        double baseY = 290;
        double profileWidth = 380;  // Width of each profile curve (wavelength axis)
        double intensityScale = 70;  // Height scale for intensity
        double perspectiveShiftX = 12;  // X shift per profile for 3D effect
        double perspectiveShiftY = -14;  // Y shift per profile (going up/back)

        List<Node> profileElements = new ArrayList<>();

        // Background color for fill (matches diagram background)
        var bgColor = Color.rgb(26, 26, 36);

        // Draw profiles from back to front
        for (var i = numProfiles - 1; i >= 0; i--) {
            double profileX = baseX + i * perspectiveShiftX;
            double profileY = baseY + i * perspectiveShiftY;

            // Absorption dip position varies across profiles (curved absorption line)
            double dipCenter = 0.5 + 0.12 * Math.sin(Math.PI * i / (numProfiles - 1));

            // Generate Gaussian profile points for filled polygon
            int numPoints = 50;
            // Polygon: profile curve + baseline (for occlusion fill)
            var polygonPoints = new double[(numPoints + 2) * 2];

            // Start at baseline left
            polygonPoints[0] = profileX;
            polygonPoints[1] = profileY;

            // Profile curve points
            for (var p = 0; p < numPoints; p++) {
                double t = (double) p / (numPoints - 1);
                double x = profileX + t * profileWidth;

                // Gaussian absorption dip
                double distFromDip = t - dipCenter;
                double intensity = 1.0 - 0.7 * Math.exp(-distFromDip * distFromDip / 0.008);

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
            for (var p = 0; p < numPoints; p++) {
                linePoints[p * 2] = polygonPoints[(p + 1) * 2];
                linePoints[p * 2 + 1] = polygonPoints[(p + 1) * 2 + 1];
            }
            var profileLine = new Polyline(linePoints);

            // Color gradient based on depth (back profiles dimmer)
            double brightness = 0.4 + 0.6 * (1.0 - (double) i / numProfiles);
            profileLine.setStroke(Color.rgb(
                    (int) (255 * brightness),
                    (int) (100 * brightness),
                    (int) (100 * brightness)
            ));
            profileLine.setStrokeWidth(2.0 - i * 0.08);
            profileLine.setFill(null);

            profileElements.add(fillPolygon);
            profileElements.add(profileLine);
        }

        pane.getChildren().addAll(profileElements);

        // Draw a line connecting the dips to show the curved valley
        var valleyPoints = new double[numProfiles * 2];
        for (var i = 0; i < numProfiles; i++) {
            double profileX = baseX + i * perspectiveShiftX;
            double profileY = baseY + i * perspectiveShiftY;
            double dipCenter = 0.5 + 0.12 * Math.sin(Math.PI * i / (numProfiles - 1));
            double x = profileX + dipCenter * profileWidth;
            double intensity = 1.0 - 0.7;  // At the dip
            double y = profileY - intensity * intensityScale;
            valleyPoints[i * 2] = x;
            valleyPoints[i * 2 + 1] = y;
        }
        var valleyLine = new Polyline(valleyPoints);
        valleyLine.setStroke(COLOR_HIGHLIGHT);
        valleyLine.setStrokeWidth(2);
        valleyLine.getStrokeDashArray().addAll(6.0, 4.0);
        pane.getChildren().add(valleyLine);

        // Axis labels
        var xLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.axis.x"), baseX + numProfiles * perspectiveShiftX / 2 - 30, baseY + 25, 10, false, COLOR_AXIS_X);
        var zLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.axis.z"), baseX + profileWidth + 20, baseY - 20, 10, false, COLOR_AXIS_Z);
        var yLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.axis.y"), baseX - 30, baseY - intensityScale / 2, 10, false, COLOR_AXIS_Y);

        // Valley annotation
        var valleyLabel = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.valley"), baseX + profileWidth + 30, baseY - intensityScale * 0.3 + numProfiles * perspectiveShiftY / 2, 10, false, COLOR_HIGHLIGHT);

        var labelElements = List.of(xLabel, zLabel, yLabel, valleyLabel);
        pane.getChildren().addAll(labelElements);

        // Brief explanation
        var explain = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "phase3.explain"), centerX, baseY + 50, 11, false, COLOR_TEXT);
        pane.getChildren().add(explain);

        Runnable resetElements = () -> {
            profileElements.forEach(n -> n.setOpacity(0));
            valleyLine.setOpacity(0);
            labelElements.forEach(n -> n.setOpacity(0));
            explain.setOpacity(0);
        };

        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        // Animate profiles appearing one by one from back to front
        // Each profile has 2 elements (fill + line), animate them together
        double profileDelay = 0.15;
        for (var i = 0; i < profileElements.size(); i += 2) {
            var fillNode = profileElements.get(i);
            var lineNode = profileElements.get(i + 1);
            int profileIndex = i / 2;
            double startTime = t + profileIndex * profileDelay;
            // Use addFadeIn to ensure proper initial keyframe at 0.01s
            HelpAnimationController.addFadeIn(animation, fillNode, startTime, 0.3);
            HelpAnimationController.addFadeIn(animation, lineNode, startTime, 0.3);
        }
        t += numProfiles * profileDelay + 0.3;

        // Show valley line
        HelpAnimationController.addFadeIn(animation, valleyLine, t, 0.4);
        t += 0.5;

        // Show labels
        HelpAnimationController.addFadeIn(animation, labelElements, t, 0.4);
        t += 0.6;

        // Show explanation
        HelpAnimationController.addFadeIn(animation, explain, t, 0.4);

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

    private List<Node> createSmallArrowHead(double x, double y, double angleDeg, Color color) {
        var angle = Math.toRadians(angleDeg);
        var size = 6;

        var arrowHead = new Polygon(
                x, y,
                x - size * Math.cos(angle - Math.PI / 6), y - size * Math.sin(angle - Math.PI / 6),
                x - size * Math.cos(angle + Math.PI / 6), y - size * Math.sin(angle + Math.PI / 6)
        );
        arrowHead.setFill(color);

        return List.of(arrowHead);
    }
}
