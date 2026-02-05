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

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.ArrayList;
import java.util.List;

/**
 * Help overlay for the differential velocity measurement feature.
 * Displays a 4-phase animated diagram explaining how the measurement works:
 * 1. Measuring East/West points at the same latitude
 * 2. Voigt profile fitting to find line centers
 * 3. Averaging multiple measurements at each latitude
 * 4. The resulting rotation profile chart
 */
public class DifferentialVelocityHelpOverlay extends AbstractHelpOverlay {

    private static final double DIAGRAM_WIDTH = 580;
    private static final double DIAGRAM_HEIGHT = 380;
    private static final Duration DISPLAY_DURATION = Duration.seconds(8);

    private static final String I18N_BUNDLE = "messages";
    private static final String VIEWER_ID = "differential-velocity";

    private static final Color COLOR_SUN = Color.rgb(255, 200, 100);
    private static final Color COLOR_CHROMOSPHERE = Color.rgb(255, 102, 51);
    private static final Color COLOR_EAST = Color.rgb(100, 150, 255);
    private static final Color COLOR_WEST = Color.rgb(255, 100, 100);
    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_TEXT_DIM = Color.rgb(136, 136, 136);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);
    private static final Color COLOR_LATITUDE = Color.rgb(150, 150, 150);

    private HelpAnimationController popupDiagramController;
    private HelpAnimationController maximizedController;

    public DifferentialVelocityHelpOverlay() {
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
        var titleLabel = new Label(I18N.string(JSolEx.class, I18N_BUNDLE, "analysis.differential.velocity.help.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        var helpText = I18N.string(JSolEx.class, I18N_BUNDLE, "analysis.differential.velocity.help.text");

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
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        var content = createFlexibleContentPane(scrollPane, 800, 700);
        return wrapInOverlay(content);
    }

    private record DiagramResult(VBox diagram, HelpAnimationController controller) {}

    private DiagramResult createDiagramWithController() {
        var phase1Result = createPhase1Pane();
        var phase2Result = createPhase2VoigtPane();
        var phase3Result = createPhase3MeasurementsPane();
        var phase4Result = createPhase4ResultPane();

        var phases = List.of(phase1Result.pane(), phase2Result.pane(), phase3Result.pane(), phase4Result.pane());
        var phaseAnimations = List.of(phase1Result.animation(), phase2Result.animation(), phase3Result.animation(), phase4Result.animation());
        var phaseResetters = List.of(phase1Result.resetElements(), phase2Result.resetElements(), phase3Result.resetElements(), phase4Result.resetElements());

        var phaseContainer = new StackPane();
        phaseContainer.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        phaseContainer.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        phaseContainer.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        phaseContainer.setStyle("-fx-background-color: #1a1a24;");
        phaseContainer.getChildren().addAll(phases);

        for (var i = 1; i < phases.size(); i++) {
            phases.get(i).setOpacity(0);
        }

        var phaseIndicators = new HBox(8);
        phaseIndicators.setAlignment(Pos.CENTER);
        for (var i = 0; i < phases.size(); i++) {
            var dot = new Circle(5);
            dot.setFill(i == 0 ? Color.WHITE : Color.gray(0.4));
            dot.setStroke(Color.gray(0.6));
            dot.setStrokeWidth(1);
            var index = i;
            dot.setOnMouseClicked(e -> {
                if (popupDiagramController != null) {
                    popupDiagramController.switchToPhase(index);
                }
            });
            dot.setStyle("-fx-cursor: hand;");
            phaseIndicators.getChildren().add(dot);
        }

        var controller = new HelpAnimationController(phaseAnimations, phaseResetters, phases, phaseIndicators, DISPLAY_DURATION);
        controller.createDiagramAnimation();

        var diagram = new VBox(10, phaseContainer, phaseIndicators);
        diagram.setAlignment(Pos.CENTER);

        return new DiagramResult(diagram, controller);
    }

    private HelpAnimationController.PhaseResult createPhase1Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2 - 60;  // Shift sun left to make room for observer
        var centerY = DIAGRAM_HEIGHT / 2 - 20;
        var sunRadius = 100.0;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "analysis.differential.velocity.help.phase1.title"),
                DIAGRAM_WIDTH / 2, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        var sunGlow = new Circle(centerX, centerY, sunRadius + 15);
        sunGlow.setFill(new RadialGradient(0, 0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.7, Color.TRANSPARENT),
                new Stop(1.0, COLOR_CHROMOSPHERE.deriveColor(0, 1, 1, 0.3))));

        var sun = new Circle(centerX, centerY, sunRadius);
        sun.setFill(new RadialGradient(0, 0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, COLOR_SUN),
                new Stop(0.8, COLOR_SUN.darker()),
                new Stop(1.0, COLOR_CHROMOSPHERE)));

        var sunElements = List.of(sunGlow, sun);
        pane.getChildren().addAll(sunElements);

        // Observer on the right side
        var observerX = DIAGRAM_WIDTH - 50;
        var observerY = centerY;
        var observerElements = createObserver(observerX, observerY);
        pane.getChildren().addAll(observerElements);

        // Line of sight from observer to sun center
        var lineOfSight = new Line(observerX - 25, observerY, centerX + sunRadius + 20, observerY);
        lineOfSight.setStroke(Color.gray(0.4));
        lineOfSight.setStrokeWidth(1);
        lineOfSight.getStrokeDashArray().addAll(10.0, 5.0);
        pane.getChildren().add(lineOfSight);

        var earthLabel = createDiagramText("Earth", observerX, observerY + 35, 10, false, COLOR_TEXT_DIM);
        pane.getChildren().add(earthLabel);

        List<Line> latitudeLines = new ArrayList<>();
        double[] latitudes = {0, 30, -30};
        for (var lat : latitudes) {
            var yOffset = sunRadius * Math.sin(Math.toRadians(lat));
            var xHalf = sunRadius * Math.cos(Math.toRadians(lat));
            var line = new Line(centerX - xHalf, centerY - yOffset, centerX + xHalf, centerY - yOffset);
            line.setStroke(COLOR_LATITUDE);
            line.setStrokeWidth(1);
            line.getStrokeDashArray().addAll(5.0, 5.0);
            latitudeLines.add(line);
        }
        pane.getChildren().addAll(latitudeLines);

        // Measurement points at top (West) and bottom (East) of the sun
        // showing the tangential rotation velocity
        var westPointX = centerX;
        var westPointY = centerY - sunRadius;  // Top of sun
        var eastPointX = centerX;
        var eastPointY = centerY + sunRadius;  // Bottom of sun

        var westPoint = new Circle(westPointX, westPointY, 6);
        westPoint.setFill(COLOR_WEST);
        westPoint.setStroke(Color.WHITE);
        westPoint.setStrokeWidth(2);

        var eastPoint = new Circle(eastPointX, eastPointY, 6);
        eastPoint.setFill(COLOR_EAST);
        eastPoint.setStroke(Color.WHITE);
        eastPoint.setStrokeWidth(2);

        var westLabel = createDiagramText("W", westPointX + 12, westPointY + 5, 11, true, COLOR_WEST);
        var eastLabel = createDiagramText("E", eastPointX + 12, eastPointY + 5, 11, true, COLOR_EAST);

        var measureElements = List.of(westPoint, eastPoint, westLabel, eastLabel);
        pane.getChildren().addAll(measureElements);

        // Tangential velocity arrows showing rotation direction
        // West (top): arrow pointing LEFT (plasma moving away from observer)
        // East (bottom): arrow pointing RIGHT (plasma moving toward observer)
        var westTangentialArrow = createHorizontalArrow(westPointX, westPointY, 40, false, COLOR_WEST);  // Points left
        var eastTangentialArrow = createHorizontalArrow(eastPointX, eastPointY, 40, true, COLOR_EAST);   // Points right
        pane.getChildren().addAll(westTangentialArrow);
        pane.getChildren().addAll(eastTangentialArrow);

        // Animated rotating point with tangential velocity vector
        var rotationAngle = new SimpleDoubleProperty(0);  // Angle in degrees, 0 = right (East limb)
        var rotatingMarker = new Circle(5);
        rotatingMarker.setFill(COLOR_HIGHLIGHT);
        rotatingMarker.setStroke(Color.WHITE);
        rotatingMarker.setStrokeWidth(2);

        var tangentArrow = new Line();
        tangentArrow.setStroke(COLOR_HIGHLIGHT);
        tangentArrow.setStrokeWidth(2);

        var tangentArrowHead = new Polygon();
        tangentArrowHead.setFill(COLOR_HIGHLIGHT);

        // Bind the rotating marker position to the angle
        rotatingMarker.centerXProperty().bind(rotationAngle.map(angle -> centerX + sunRadius * Math.cos(Math.toRadians(angle.doubleValue()))));
        rotatingMarker.centerYProperty().bind(rotationAngle.map(angle -> centerY - sunRadius * Math.sin(Math.toRadians(angle.doubleValue()))));

        // Bind the tangent arrow to follow the marker with tangential direction
        // Tangent direction is perpendicular to radius, counterclockwise rotation means tangent points in +angle direction
        var arrowLength = 30.0;
        tangentArrow.startXProperty().bind(rotatingMarker.centerXProperty());
        tangentArrow.startYProperty().bind(rotatingMarker.centerYProperty());
        tangentArrow.endXProperty().bind(rotationAngle.map(angle -> {
            var a = angle.doubleValue();
            var tangentAngle = a + 90;  // Tangent is 90° counterclockwise from radius
            return centerX + sunRadius * Math.cos(Math.toRadians(a)) + arrowLength * Math.cos(Math.toRadians(tangentAngle));
        }));
        tangentArrow.endYProperty().bind(rotationAngle.map(angle -> {
            var a = angle.doubleValue();
            var tangentAngle = a + 90;
            return centerY - sunRadius * Math.sin(Math.toRadians(a)) - arrowLength * Math.sin(Math.toRadians(tangentAngle));
        }));

        // Update arrowhead position and rotation
        rotationAngle.addListener((obs, oldVal, newVal) -> {
            var angle = newVal.doubleValue();
            var tangentAngle = angle + 90;
            var endX = centerX + sunRadius * Math.cos(Math.toRadians(angle)) + arrowLength * Math.cos(Math.toRadians(tangentAngle));
            var endY = centerY - sunRadius * Math.sin(Math.toRadians(angle)) - arrowLength * Math.sin(Math.toRadians(tangentAngle));

            // Arrowhead points in tangent direction
            var headSize = 8.0;
            var headAngle = Math.toRadians(tangentAngle);
            var perpAngle = headAngle + Math.PI / 2;

            tangentArrowHead.getPoints().setAll(
                    endX + headSize * Math.cos(headAngle), endY - headSize * Math.sin(headAngle),
                    endX - headSize * 0.5 * Math.cos(headAngle) + headSize * 0.4 * Math.cos(perpAngle),
                    endY + headSize * 0.5 * Math.sin(headAngle) - headSize * 0.4 * Math.sin(perpAngle),
                    endX - headSize * 0.5 * Math.cos(headAngle) - headSize * 0.4 * Math.cos(perpAngle),
                    endY + headSize * 0.5 * Math.sin(headAngle) + headSize * 0.4 * Math.sin(perpAngle)
            );
        });

        var rotatingElements = List.of(rotatingMarker, tangentArrow, tangentArrowHead);
        pane.getChildren().addAll(rotatingElements);

        // Animation for the rotating marker (continuous loop)
        var rotationAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(rotationAngle, 0)),
                new KeyFrame(Duration.seconds(4), new KeyValue(rotationAngle, 360))
        );
        rotationAnimation.setCycleCount(Timeline.INDEFINITE);

        var explainBox = new Rectangle(70, 275, 440, 90);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText("The Sun rotates. From Earth, we see:", 290, 293, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText("East limb: plasma moves toward us → blue shift", 290, 313, 11, false, COLOR_EAST);
        var explain3 = createDiagramText("West limb: plasma moves away from us → red shift", 290, 333, 11, false, COLOR_WEST);
        var explain4 = createDiagramText("Δv = v_west - v_east = 2 × rotational velocity", 290, 355, 11, true, COLOR_HIGHLIGHT);

        var explainElements = List.of(explainBox, explain1, explain2, explain3, explain4);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            observerElements.forEach(n -> n.setOpacity(0));
            lineOfSight.setOpacity(0);
            earthLabel.setOpacity(0);
            latitudeLines.forEach(n -> n.setOpacity(0));
            measureElements.forEach(n -> n.setOpacity(0));
            westTangentialArrow.forEach(n -> n.setOpacity(0));
            eastTangentialArrow.forEach(n -> n.setOpacity(0));
            rotatingElements.forEach(n -> n.setOpacity(0));
            rotationAnimation.stop();
            rotationAngle.set(0);
            explainElements.forEach(n -> n.setOpacity(0));
        };
        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        // Show observer first
        var observerAndLine = new ArrayList<javafx.scene.Node>();
        observerAndLine.addAll(observerElements);
        observerAndLine.add(lineOfSight);
        observerAndLine.add(earthLabel);
        HelpAnimationController.addFadeIn(animation, observerAndLine, t, 0.4);
        t += 0.7;

        HelpAnimationController.addFadeIn(animation, latitudeLines, t, 0.4);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, measureElements, t, 0.4);
        t += 0.6;

        // Show velocity arrows
        var allArrows = new ArrayList<javafx.scene.Node>();
        allArrows.addAll(westTangentialArrow);
        allArrows.addAll(eastTangentialArrow);
        HelpAnimationController.addFadeIn(animation, allArrows, t, 0.4);
        t += 0.5;

        // Show rotating marker and start rotation animation
        HelpAnimationController.addFadeIn(animation, rotatingElements, t, 0.3);
        animation.getKeyFrames().add(new KeyFrame(Duration.seconds(t + 0.3), e -> rotationAnimation.play()));
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.5);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase2VoigtPane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "analysis.differential.velocity.help.phase2.title"),
                centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // Chart area for the spectral profiles
        var chartX = 60.0;
        var chartY = 50.0;
        var chartWidth = 340.0;  // Reduced to make room for legend on the right
        var chartHeight = 180.0;

        var chartBg = new Rectangle(chartX, chartY, chartWidth, chartHeight);
        chartBg.setFill(Color.rgb(30, 30, 40));
        chartBg.setStroke(COLOR_LATITUDE);
        chartBg.setStrokeWidth(1);

        // X-axis (wavelength)
        var xAxis = new Line(chartX, chartY + chartHeight, chartX + chartWidth, chartY + chartHeight);
        xAxis.setStroke(COLOR_TEXT_DIM);
        xAxis.setStrokeWidth(2);

        // Y-axis (intensity)
        var yAxis = new Line(chartX, chartY, chartX, chartY + chartHeight);
        yAxis.setStroke(COLOR_TEXT_DIM);
        yAxis.setStrokeWidth(2);

        var xLabel = createDiagramText("Wavelength (λ)", chartX + chartWidth / 2, chartY + chartHeight + 20, 10, false, COLOR_TEXT);
        var yLabel = createDiagramText("Intensity", chartX - 30, chartY + chartHeight / 2, 10, false, COLOR_TEXT);

        var chartElements = List.of(chartBg, xAxis, yAxis, xLabel, yLabel);
        pane.getChildren().addAll(chartElements);

        // Continuum level - the wings of the Voigt profiles will be at this level
        var continuumY = chartY + 25;
        var continuumLabel = createDiagramText("continuum", chartX + chartWidth - 45, continuumY - 8, 9, false, COLOR_TEXT_DIM);
        var continuumElements = List.of(continuumLabel);
        pane.getChildren().addAll(continuumElements);

        // Reference wavelength marker (λ0)
        var lambda0X = chartX + chartWidth / 2;
        var lambda0Line = new Line(lambda0X, chartY + 5, lambda0X, chartY + chartHeight - 5);
        lambda0Line.setStroke(COLOR_TEXT_DIM.deriveColor(0, 1, 1, 0.5));
        lambda0Line.setStrokeWidth(1);
        lambda0Line.getStrokeDashArray().addAll(5.0, 5.0);

        var lambda0Label = createDiagramText("λ₀", lambda0X, chartY + chartHeight + 35, 11, true, COLOR_TEXT);
        var lambda0Elements = List.of(lambda0Line, lambda0Label);
        pane.getChildren().addAll(lambda0Elements);

        // Create three Voigt profiles: reference, blue-shifted (East), red-shifted (West)
        // The wings extend to the continuum level at the top
        var profileWidth = 200.0;  // Wide enough for wings to reach continuum
        var profileHeight = chartHeight - 40;
        var profileY = continuumY;

        // Reference profile (at λ0) - dimmed/background
        var refProfile = createVoigtProfile(lambda0X, profileY, profileWidth, profileHeight, COLOR_TEXT_DIM.deriveColor(0, 1, 1, 0.4));
        pane.getChildren().addAll(refProfile);

        // Blue-shifted profile (East limb - toward observer)
        // Small shift (~10 pixels) to reflect the actual tiny Doppler shift (~0.4 pixels in reality)
        var blueShift = -10.0;
        var blueProfile = createVoigtProfile(lambda0X + blueShift, profileY, profileWidth, profileHeight, COLOR_EAST);
        pane.getChildren().addAll(blueProfile);

        // Red-shifted profile (West limb - away from observer)
        var redShift = 10.0;
        var redProfile = createVoigtProfile(lambda0X + redShift, profileY, profileWidth, profileHeight, COLOR_WEST);
        pane.getChildren().addAll(redProfile);

        // Shift indicators with arrows - positioned below the profiles
        var arrowY = chartY + chartHeight - 25;

        // Blue shift arrow (pointing left from λ0)
        var blueArrowStart = lambda0X - 2;
        var blueArrowEnd = lambda0X + blueShift;
        var blueArrow = new Line(blueArrowStart, arrowY, blueArrowEnd, arrowY);
        blueArrow.setStroke(COLOR_EAST);
        blueArrow.setStrokeWidth(2);
        var blueArrowHead = new Polygon(
                blueArrowEnd - 5, arrowY,
                blueArrowEnd + 3, arrowY - 3,
                blueArrowEnd + 3, arrowY + 3
        );
        blueArrowHead.setFill(COLOR_EAST);
        var blueShiftLabel = createDiagramText("Δλ (East)", lambda0X - 60, arrowY + 4, 9, false, COLOR_EAST);
        var blueArrowElements = List.of(blueArrow, blueArrowHead, blueShiftLabel);
        pane.getChildren().addAll(blueArrowElements);

        // Red shift arrow (pointing right from λ0)
        var redArrowStart = lambda0X + 2;
        var redArrowEnd = lambda0X + redShift;
        var redArrow = new Line(redArrowStart, arrowY + 12, redArrowEnd, arrowY + 12);
        redArrow.setStroke(COLOR_WEST);
        redArrow.setStrokeWidth(2);
        var redArrowHead = new Polygon(
                redArrowEnd + 5, arrowY + 12,
                redArrowEnd - 3, arrowY + 12 - 3,
                redArrowEnd - 3, arrowY + 12 + 3
        );
        redArrowHead.setFill(COLOR_WEST);
        var redShiftLabel = createDiagramText("Δλ (West)", lambda0X + 60, arrowY + 16, 9, false, COLOR_WEST);
        var redArrowElements = List.of(redArrow, redArrowHead, redShiftLabel);
        pane.getChildren().addAll(redArrowElements);

        // Legend - positioned well outside the chart on the right
        var legendX = chartX + chartWidth + 50;
        var legendY = chartY + 50;
        var refLegend = createDiagramText("— Reference (λ₀)", legendX, legendY, 8, false, COLOR_TEXT_DIM);
        var blueLegend = createDiagramText("— East (blue shift)", legendX, legendY + 15, 8, false, COLOR_EAST);
        var redLegend = createDiagramText("— West (red shift)", legendX, legendY + 30, 8, false, COLOR_WEST);
        var legendElements = List.of(refLegend, blueLegend, redLegend);
        pane.getChildren().addAll(legendElements);

        // Explanation box
        var explainBox = new Rectangle(70, 265, 440, 100);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText("Each point's spectrum is fitted with a Voigt profile", 290, 285, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText("The line center position reveals the Doppler shift", 290, 305, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText("Δv = c × (λ_west - λ_east) / λ₀", 290, 330, 11, true, COLOR_HIGHLIGHT);
        var explain4 = createDiagramText("This gives twice the rotational velocity at that latitude", 290, 350, 11, false, COLOR_TEXT_DIM);

        var explainElements = List.of(explainBox, explain1, explain2, explain3, explain4);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            chartElements.forEach(n -> n.setOpacity(0));
            continuumElements.forEach(n -> n.setOpacity(0));
            lambda0Elements.forEach(n -> n.setOpacity(0));
            refProfile.forEach(n -> n.setOpacity(0));
            blueProfile.forEach(n -> n.setOpacity(0));
            redProfile.forEach(n -> n.setOpacity(0));
            blueArrowElements.forEach(n -> n.setOpacity(0));
            redArrowElements.forEach(n -> n.setOpacity(0));
            legendElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };
        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        // Show chart and continuum
        HelpAnimationController.addFadeIn(animation, chartElements, t, 0.4);
        HelpAnimationController.addFadeIn(animation, continuumElements, t + 0.2, 0.3);
        t += 0.5;

        // Show λ0 reference line
        HelpAnimationController.addFadeIn(animation, lambda0Elements, t, 0.3);
        t += 0.4;

        // Show reference profile (dimmed)
        HelpAnimationController.addFadeIn(animation, refProfile, t, 0.4);
        t += 0.5;

        // Show blue-shifted profile
        HelpAnimationController.addFadeIn(animation, blueProfile, t, 0.4);
        HelpAnimationController.addFadeIn(animation, blueArrowElements, t + 0.2, 0.3);
        t += 0.7;

        // Show red-shifted profile
        HelpAnimationController.addFadeIn(animation, redProfile, t, 0.4);
        HelpAnimationController.addFadeIn(animation, redArrowElements, t + 0.2, 0.3);
        t += 0.7;

        // Show legend
        HelpAnimationController.addFadeIn(animation, legendElements, t, 0.3);
        t += 0.5;

        // Show explanation
        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private List<javafx.scene.Node> createVoigtProfile(double centerX, double topY, double width, double height, Color color) {
        var elements = new ArrayList<javafx.scene.Node>();

        // Approximate Voigt profile with a combination of points
        // Absorption line: wings at continuum level (topY), dip toward bottom at line center
        var numPoints = 80;
        var points = new double[numPoints * 2];

        for (var i = 0; i < numPoints; i++) {
            var x = centerX - width / 2 + (width * i / (numPoints - 1));
            var relX = (x - centerX) / (width / 6);  // Normalized x, narrower core

            // Voigt-like profile (simplified as a mix of Gaussian and Lorentzian)
            var gaussian = Math.exp(-relX * relX * 0.8);
            var lorentzian = 1.0 / (1.0 + relX * relX * 0.5);
            var profile = 0.6 * gaussian + 0.4 * lorentzian;

            // Absorption line: wings at continuum (y = topY), center dips down
            // profile=0 at wings → y = topY (continuum)
            // profile=1 at center → y = topY + depth (absorption dip)
            var y = topY + profile * height * 0.7;

            points[i * 2] = x;
            points[i * 2 + 1] = y;
        }

        var polyline = new javafx.scene.shape.Polyline(points);
        polyline.setStroke(color);
        polyline.setStrokeWidth(2.5);
        polyline.setFill(null);
        elements.add(polyline);

        return elements;
    }

    private List<javafx.scene.Node> createObserver(double x, double y) {
        var elements = new ArrayList<javafx.scene.Node>();

        // Simple telescope/observer representation
        // Head
        var head = new Circle(x, y - 12, 8);
        head.setFill(Color.gray(0.6));
        head.setStroke(Color.gray(0.8));
        head.setStrokeWidth(1);
        elements.add(head);

        // Body
        var body = new Line(x, y - 4, x, y + 15);
        body.setStroke(Color.gray(0.6));
        body.setStrokeWidth(3);
        elements.add(body);

        // Telescope pointing left toward the sun
        var telescope = new Line(x - 20, y, x + 5, y);
        telescope.setStroke(Color.gray(0.7));
        telescope.setStrokeWidth(4);
        elements.add(telescope);

        // Telescope aperture
        var aperture = new Circle(x - 20, y, 5);
        aperture.setFill(Color.rgb(50, 50, 80));
        aperture.setStroke(Color.gray(0.7));
        aperture.setStrokeWidth(2);
        elements.add(aperture);

        return elements;
    }

    private List<javafx.scene.Node> createVerticalArrow(double x, double y, double length, boolean pointingUp, Color color) {
        var elements = new ArrayList<javafx.scene.Node>();

        double startY = y;
        double endY = pointingUp ? y - length : y + length;

        var line = new Line(x, startY, x, endY);
        line.setStroke(color);
        line.setStrokeWidth(3);
        elements.add(line);

        var arrowHead = new Polygon();
        if (pointingUp) {
            arrowHead.getPoints().addAll(
                    x, endY - 8.0,
                    x - 5.0, endY + 2.0,
                    x + 5.0, endY + 2.0
            );
        } else {
            arrowHead.getPoints().addAll(
                    x, endY + 8.0,
                    x - 5.0, endY - 2.0,
                    x + 5.0, endY - 2.0
            );
        }
        arrowHead.setFill(color);
        elements.add(arrowHead);

        return elements;
    }

    private List<javafx.scene.Node> createHorizontalArrow(double x, double y, double length, boolean pointingRight, Color color) {
        var elements = new ArrayList<javafx.scene.Node>();

        double endX = pointingRight ? x + length : x - length;

        var line = new Line(x, y, endX, y);
        line.setStroke(color);
        line.setStrokeWidth(3);
        elements.add(line);

        var arrowHead = new Polygon();
        if (pointingRight) {
            arrowHead.getPoints().addAll(
                    endX + 8.0, y,
                    endX - 2.0, y - 5.0,
                    endX - 2.0, y + 5.0
            );
        } else {
            arrowHead.getPoints().addAll(
                    endX - 8.0, y,
                    endX + 2.0, y - 5.0,
                    endX + 2.0, y + 5.0
            );
        }
        arrowHead.setFill(color);
        elements.add(arrowHead);

        return elements;
    }

    private HelpAnimationController.PhaseResult createPhase3MeasurementsPane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "analysis.differential.velocity.help.phase3.title"),
                centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        var sunCenterY = 140.0;
        var sunRadius = 90.0;

        var sun = new Circle(centerX, sunCenterY, sunRadius);
        sun.setFill(new RadialGradient(0, 0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, COLOR_SUN),
                new Stop(0.8, COLOR_SUN.darker()),
                new Stop(1.0, COLOR_CHROMOSPHERE)));
        pane.getChildren().add(sun);

        // Add latitude lines overlay (dimmed, for context)
        List<javafx.scene.Node> latitudeElements = new ArrayList<>();
        double[] latitudes = {45, 30, 15, 0, -15, -30, -45};

        for (var lat : latitudes) {
            var yOffset = sunRadius * Math.sin(Math.toRadians(lat));
            var xHalf = sunRadius * Math.cos(Math.toRadians(lat));
            var line = new Line(centerX - xHalf, sunCenterY - yOffset, centerX + xHalf, sunCenterY - yOffset);
            line.setStroke(lat == 30 ? COLOR_HIGHLIGHT : COLOR_LATITUDE.deriveColor(0, 1, 1, 0.4));
            line.setStrokeWidth(lat == 30 ? 2 : 1);
            if (lat != 30) {
                line.getStrokeDashArray().addAll(4.0, 4.0);
            }
            latitudeElements.add(line);

            // Add latitude label on the right
            var labelColor = lat == 30 ? COLOR_HIGHLIGHT : COLOR_LATITUDE.deriveColor(0, 1, 1, 0.5);
            if (lat != 0) {
                var label = createDiagramText((lat > 0 ? "+" : "") + (int) lat + "°",
                        centerX + xHalf + 12, sunCenterY - yOffset + 4, 9, lat == 30, labelColor);
                latitudeElements.add(label);
            } else {
                var label = createDiagramText("0°", centerX + sunRadius + 12, sunCenterY + 4, 9, false, labelColor);
                latitudeElements.add(label);
            }
        }
        pane.getChildren().addAll(latitudeElements);

        // E/W labels
        var eastLabel = createDiagramText("E", centerX - sunRadius - 15, sunCenterY + 4, 11, true, COLOR_EAST);
        var westLabel = createDiagramText("W", centerX + sunRadius + 5, sunCenterY + 4, 11, true, COLOR_WEST);
        var limbLabels = List.of(eastLabel, westLabel);
        pane.getChildren().addAll(limbLabels);

        // Multiple measurement pairs at 30° latitude (the highlighted one)
        var selectedLat = 30.0;
        var yOffset = sunRadius * Math.sin(Math.toRadians(selectedLat));
        var xHalf = sunRadius * Math.cos(Math.toRadians(selectedLat));
        var measureY = sunCenterY - yOffset;

        // Create multiple E/W pairs that will appear one by one
        // Each pair is at a different position along the latitude line, moving inward
        int numPairs = 5;
        List<List<javafx.scene.Node>> measurementPairs = new ArrayList<>();

        // Offsets from the limb: start at the edge, move progressively inward
        double[] inwardOffsets = {5, 15, 28, 42, 55};

        for (var i = 0; i < numPairs; i++) {
            var pair = new ArrayList<javafx.scene.Node>();
            var offset = inwardOffsets[i];

            // East point (left side) - moves inward (toward center) each iteration
            var eastX = centerX - xHalf + offset;
            var eastPoint = new Circle(eastX, measureY, 5);
            eastPoint.setFill(COLOR_EAST);
            eastPoint.setStroke(Color.WHITE);
            eastPoint.setStrokeWidth(1.5);

            // West point (right side) - moves inward (toward center) each iteration
            var westX = centerX + xHalf - offset;
            var westPoint = new Circle(westX, measureY, 5);
            westPoint.setFill(COLOR_WEST);
            westPoint.setStroke(Color.WHITE);
            westPoint.setStrokeWidth(1.5);

            // Connecting line between the pair
            var connector = new Line(eastX + 5, measureY, westX - 5, measureY);
            connector.setStroke(COLOR_HIGHLIGHT.deriveColor(0, 1, 1, 0.3));
            connector.setStrokeWidth(1);
            connector.getStrokeDashArray().addAll(3.0, 3.0);

            pair.add(connector);
            pair.add(eastPoint);
            pair.add(westPoint);
            measurementPairs.add(pair);
        }

        // Add all pairs to pane
        for (var pair : measurementPairs) {
            pane.getChildren().addAll(pair);
        }

        // Counter label showing measurement number
        var counterLabel = createDiagramText("Measurement 1/" + numPairs, centerX, measureY - 20, 10, true, COLOR_HIGHLIGHT);
        pane.getChildren().add(counterLabel);

        var medianArrow = new Line(centerX - 50, 255, centerX + 50, 255);
        medianArrow.setStroke(COLOR_HIGHLIGHT);
        medianArrow.setStrokeWidth(3);

        var arrowHead1 = new Line(centerX + 40, 250, centerX + 50, 255);
        arrowHead1.setStroke(COLOR_HIGHLIGHT);
        arrowHead1.setStrokeWidth(3);
        var arrowHead2 = new Line(centerX + 40, 260, centerX + 50, 255);
        arrowHead2.setStroke(COLOR_HIGHLIGHT);
        arrowHead2.setStrokeWidth(3);

        var arrowLabel = createDiagramText("Median averaging", centerX, 275, 12, true, COLOR_HIGHLIGHT);
        var arrowElements = List.of(medianArrow, arrowHead1, arrowHead2, arrowLabel);
        pane.getChildren().addAll(arrowElements);

        var explainBox = new Rectangle(90, 295, 400, 75);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText("Multiple E/W pairs measured at same latitude", 290, 315, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText("Median averaging reduces noise from bad frames", 290, 335, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText("Process repeated for each latitude band", 290, 355, 11, false, COLOR_HIGHLIGHT);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            latitudeElements.forEach(n -> n.setOpacity(0));
            limbLabels.forEach(n -> n.setOpacity(0));
            for (var pair : measurementPairs) {
                pair.forEach(n -> n.setOpacity(0));
            }
            counterLabel.setOpacity(0);
            arrowElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };
        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        // Show latitude lines first
        HelpAnimationController.addFadeIn(animation, latitudeElements, t, 0.4);
        t += 0.5;

        // Show E/W labels
        HelpAnimationController.addFadeIn(animation, limbLabels, t, 0.3);
        t += 0.4;

        // Animate measurement pairs appearing one by one
        for (var i = 0; i < measurementPairs.size(); i++) {
            var pair = measurementPairs.get(i);
            var measureNum = i + 1;

            // Update counter text
            var finalT = t;
            animation.getKeyFrames().add(new KeyFrame(Duration.seconds(finalT),
                    e -> counterLabel.setText("Measurement " + measureNum + "/" + numPairs)));

            if (i == 0) {
                // First pair: fade in counter too
                var withCounter = new ArrayList<>(pair);
                withCounter.add(counterLabel);
                HelpAnimationController.addFadeIn(animation, withCounter, t, 0.3);
            } else {
                // Fade out previous pair, fade in new pair
                var prevPair = measurementPairs.get(i - 1);
                HelpAnimationController.addFadeOut(animation, prevPair, t, 0.2);
                HelpAnimationController.addFadeIn(animation, pair, t + 0.2, 0.3);
            }
            t += 0.7;
        }

        // Keep last pair visible, show arrow and explanation
        t += 0.3;
        HelpAnimationController.addFadeIn(animation, arrowElements, t, 0.4);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase4ResultPane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, I18N_BUNDLE, "analysis.differential.velocity.help.phase4.title"),
                centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        var chartX = 80.0;
        var chartY = 50.0;
        var chartWidth = 420.0;
        var chartHeight = 200.0;

        var chartBg = new Rectangle(chartX, chartY, chartWidth, chartHeight);
        chartBg.setFill(Color.rgb(30, 30, 40));
        chartBg.setStroke(COLOR_LATITUDE);
        chartBg.setStrokeWidth(1);

        var xAxis = new Line(chartX, chartY + chartHeight, chartX + chartWidth, chartY + chartHeight);
        xAxis.setStroke(COLOR_TEXT_DIM);
        xAxis.setStrokeWidth(2);

        var yAxis = new Line(chartX, chartY, chartX, chartY + chartHeight);
        yAxis.setStroke(COLOR_TEXT_DIM);
        yAxis.setStrokeWidth(2);

        var xLabel = createDiagramText("Latitude (°)", chartX + chartWidth / 2, chartY + chartHeight + 25, 11, false, COLOR_TEXT);
        var yLabel = createDiagramText("Velocity", chartX - 35, chartY + chartHeight / 2, 11, false, COLOR_TEXT);

        var chartElements = List.of(chartBg, xAxis, yAxis, xLabel, yLabel);
        pane.getChildren().addAll(chartElements);

        List<Circle> dataPoints = new ArrayList<>();
        double[] latValues = {-60, -45, -30, -15, 0, 15, 30, 45, 60};
        double[] velFactors = {0.5, 0.65, 0.8, 0.95, 1.0, 0.95, 0.8, 0.65, 0.5};

        for (var i = 0; i < latValues.length; i++) {
            var x = chartX + 30 + (latValues[i] + 60) * (chartWidth - 60) / 120;
            var y = chartY + chartHeight - 20 - velFactors[i] * (chartHeight - 40);
            var point = new Circle(x, y, 5);
            point.setFill(COLOR_HIGHLIGHT);
            point.setStroke(Color.WHITE);
            point.setStrokeWidth(1);
            dataPoints.add(point);
        }
        pane.getChildren().addAll(dataPoints);

        List<Line> theoreticalCurve = new ArrayList<>();
        for (var i = 0; i < latValues.length - 1; i++) {
            var x1 = chartX + 30 + (latValues[i] + 60) * (chartWidth - 60) / 120;
            var y1 = chartY + chartHeight - 20 - velFactors[i] * 0.95 * (chartHeight - 40);
            var x2 = chartX + 30 + (latValues[i + 1] + 60) * (chartWidth - 60) / 120;
            var y2 = chartY + chartHeight - 20 - velFactors[i + 1] * 0.95 * (chartHeight - 40);
            var segment = new Line(x1, y1, x2, y2);
            segment.setStroke(COLOR_EAST);
            segment.setStrokeWidth(2);
            segment.getStrokeDashArray().addAll(8.0, 4.0);
            theoreticalCurve.add(segment);
        }
        pane.getChildren().addAll(theoreticalCurve);

        var legendBox = new Rectangle(chartX + chartWidth - 150, chartY + 10, 140, 45);
        legendBox.setFill(Color.rgb(40, 40, 50, 0.8));
        legendBox.setStroke(Color.rgb(100, 100, 120, 0.5));

        var legendMeasured = createDiagramText("● Measured", chartX + chartWidth - 80, chartY + 28, 10, false, COLOR_HIGHLIGHT);
        var legendTheory = createDiagramText("-- Theory", chartX + chartWidth - 80, chartY + 45, 10, false, COLOR_EAST);

        var legendElements = List.of(legendBox, legendMeasured, legendTheory);
        pane.getChildren().addAll(legendElements);

        var explainBox = new Rectangle(90, 280, 400, 85);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText("Result: velocity vs. latitude chart", 290, 300, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText("Compared with Snodgrass & Ulrich (1990)", 290, 320, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText("Solar differential rotation:", 290, 340, 11, false, COLOR_HIGHLIGHT);
        var explain4 = createDiagramText("Equator rotates faster than poles", 290, 358, 11, false, COLOR_TEXT_DIM);

        var explainElements = List.of(explainBox, explain1, explain2, explain3, explain4);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            chartElements.forEach(n -> n.setOpacity(0));
            dataPoints.forEach(n -> n.setOpacity(0));
            theoreticalCurve.forEach(n -> n.setOpacity(0));
            legendElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };
        resetElements.run();

        var animation = new Timeline();
        var t = 0.3;

        HelpAnimationController.addFadeIn(animation, chartElements, t, 0.4);
        t += 0.8;

        HelpAnimationController.addStaggeredFadeIn(animation, dataPoints, t, 0.1, 0.2);
        t += dataPoints.size() * 0.1 + 0.5;

        HelpAnimationController.addFadeIn(animation, theoreticalCurve, t, 0.5);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, legendElements, t, 0.3);
        t += 0.5;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }
}
