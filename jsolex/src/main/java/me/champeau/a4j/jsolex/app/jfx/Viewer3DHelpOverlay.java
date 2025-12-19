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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay for 3D viewers showing a help button.
 * When clicked, displays explanatory information about the view.
 * This overlay should not be included in exports.
 * <p>
 * Uses a 4-phase animated diagram to explain orthographic projection:
 * 1. What we see from Earth (parallel rays, orthographic projection)
 * 2. The flat disk image we capture
 * 3. Mapping the flat image onto a hemisphere
 * 4. Limitations - we don't invent hidden data
 */
public class Viewer3DHelpOverlay extends AbstractHelpOverlay {

    private static final double DIAGRAM_WIDTH = 580;
    private static final double DIAGRAM_HEIGHT = 340;
    private static final Duration DISPLAY_DURATION = Duration.seconds(9);

    private static final Color COLOR_SUN = Color.rgb(255, 102, 51);
    private static final Color COLOR_OBSERVER = Color.rgb(102, 136, 255);
    private static final Color COLOR_FILAMENT = Color.rgb(204, 51, 51);
    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_TEXT_DIM = Color.rgb(136, 136, 136);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);
    private static final Color COLOR_SUCCESS = Color.rgb(68, 255, 68);
    private static final Color COLOR_WARNING = Color.rgb(255, 100, 100);

    private final String i18nBundle;
    private final boolean showDiagram;
    private HelpAnimationController popupDiagramController;
    private HelpAnimationController maximizedController;

    public Viewer3DHelpOverlay(String i18nBundle) {
        this(i18nBundle, false, "singleimage");
    }

    public Viewer3DHelpOverlay(String i18nBundle, boolean showDiagram) {
        this(i18nBundle, showDiagram, "singleimage");
    }

    public Viewer3DHelpOverlay(String i18nBundle, boolean showDiagram, String viewerId) {
        super(viewerId);
        this.i18nBundle = i18nBundle;
        this.showDiagram = showDiagram;
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
        var titleLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "help.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        var helpText = I18N.string(JSolEx.class, i18nBundle, "help.text");
        var textFlow = parseFormattedText(helpText);
        textFlow.setMaxWidth(700);

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
        if (showDiagram) {
            var popupDiagramResult = createDiagramWithController();
            popupDiagramController = popupDiagramResult.controller();

            var diagram = createScalableDiagram(
                    popupDiagramResult.diagram(),
                    () -> {
                        var result = createDiagramWithController();
                        maximizedController = result.controller();
                        // Start animation immediately - required for both maximized view and GIF export
                        maximizedController.start();
                        return result.diagram();
                    },
                    null, // Animation already started in supplier
                    () -> {
                        if (maximizedController != null) {
                            maximizedController.stop();
                            maximizedController = null;
                        }
                    }
            );
            contentBox = new VBox(15, titleLabel, textFlow, diagram, buttonBox);
        } else {
            var spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            contentBox = new VBox(15, titleLabel, textFlow, spacer, buttonBox);
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

    private DiagramResult createDiagramWithController() {
        var diagramPane = new StackPane();
        diagramPane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setStyle("-fx-background-color: #1a1a24; -fx-background-radius: 8;");

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

        phase1.setOpacity(1.0);
        phase2.setOpacity(0.0);
        phase3.setOpacity(0.0);
        phase4.setOpacity(0.0);

        diagramPane.getChildren().addAll(phase4, phase3, phase2, phase1);

        var localPhaseIndicators = new HBox(8);
        localPhaseIndicators.setAlignment(Pos.CENTER);

        var controller = new HelpAnimationController(
                localPhaseAnimations,
                localPhaseResetters,
                localPhases,
                localPhaseIndicators,
                DISPLAY_DURATION
        );

        for (var i = 0; i < 4; i++) {
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
        var centerY = DIAGRAM_HEIGHT / 2 - 10;

        var title = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase1.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        var observerLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.observer"), 55, centerY - 70, 11, false, COLOR_OBSERVER);

        var eye = new Ellipse(55, centerY, 18, 12);
        eye.setFill(Color.TRANSPARENT);
        eye.setStroke(COLOR_OBSERVER);
        eye.setStrokeWidth(2.5);
        var pupil = new Circle(55, centerY, 5, COLOR_OBSERVER);

        double sunRadius = 80;
        var sunX = centerX + 80;
        var sun = new Circle(sunX, centerY, sunRadius);
        sun.setFill(COLOR_SUN.deriveColor(0, 1, 1, 0.13));
        sun.setStroke(COLOR_SUN);
        sun.setStrokeWidth(2.5);
        var sunLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.sun"), sunX, centerY, 11, false, COLOR_SUN);

        var observerElements = List.of(observerLabel, eye, pupil);
        var sunElements = List.of(sun, sunLabel);
        pane.getChildren().addAll(observerElements);
        pane.getChildren().addAll(sunElements);

        // Create rays with dash pattern for animated movement effect
        var dashLength = 6.0;
        var gapLength = 10.0;
        var dashCycle = dashLength + gapLength;

        List<Line> rays = new ArrayList<>();
        for (var dy : new int[]{-60, -30, 0, 30, 60}) {
            var line = new Line(90, centerY + dy, sunX - sunRadius - 5, centerY + dy);
            line.setStroke(COLOR_OBSERVER.deriveColor(0, 1, 1, 0.7));
            line.setStrokeWidth(1.5);
            line.getStrokeDashArray().addAll(dashLength, gapLength);
            rays.add(line);
            pane.getChildren().add(line);
        }

        var raysLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.parallel.rays"), centerX - 30, centerY - 80, 10, false, COLOR_OBSERVER);
        pane.getChildren().add(raysLabel);

        var explainBox = new Rectangle(60, 250, 460, 75);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase1.explain1"), 290, 268, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase1.explain2"), 290, 288, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase1.explain3"), 290, 312, 12, false, COLOR_HIGHLIGHT);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            observerElements.forEach(n -> n.setOpacity(0));
            sunElements.forEach(n -> n.setOpacity(0));
            rays.forEach(n -> {
                n.setOpacity(0);
                n.setStrokeDashOffset(0);
            });
            raysLabel.setOpacity(0);
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();

        // Hide all elements at animation start to prevent flash on loop
        HelpAnimationController.hideAtStart(animation, sunElements);
        HelpAnimationController.hideAtStart(animation, observerElements);
        HelpAnimationController.hideAtStart(animation, rays);
        HelpAnimationController.hideAtStart(animation, raysLabel);
        HelpAnimationController.hideAtStart(animation, explainElements);

        var t = 0.3;

        HelpAnimationController.addFadeIn(animation, sunElements, t, 0.4);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, observerElements, t, 0.4);
        t += 0.5;

        // Fade in all rays together
        HelpAnimationController.addFadeIn(animation, rays, t, 0.3);

        // Animate dash offset to create movement from Sun to observer (right to left)
        // Positive offset makes dashes appear to move left (toward observer)
        var raysStartTime = t;
        var animationDuration = 5.0;
        var totalOffset = dashCycle * 15;

        for (var ray : rays) {
            animation.getKeyFrames().addAll(
                    new KeyFrame(Duration.seconds(raysStartTime),
                            new KeyValue(ray.strokeDashOffsetProperty(), 0)),
                    new KeyFrame(Duration.seconds(raysStartTime + animationDuration),
                            new KeyValue(ray.strokeDashOffsetProperty(), totalOffset, Interpolator.LINEAR))
            );
        }

        t += 0.4;
        HelpAnimationController.addFadeIn(animation, raysLabel, t, 0.3);
        t += 0.4;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase2Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;
        var centerY = DIAGRAM_HEIGHT / 2 - 20;

        var title = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase2.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        double diskRadius = 80;
        var disk = new Circle(centerX, centerY, diskRadius);
        disk.setFill(COLOR_SUN.deriveColor(0, 1, 1, 0.2));
        disk.setStroke(COLOR_SUN);
        disk.setStrokeWidth(2.5);

        var diskLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.flat.disk"), centerX, centerY - diskRadius - 15, 11, true, COLOR_HIGHLIGHT);

        var filamentX = centerX - 45;
        var filament = new QuadCurve(filamentX, centerY - 35, filamentX - 12, centerY, filamentX, centerY + 35);
        filament.setFill(Color.TRANSPARENT);
        filament.setStroke(COLOR_FILAMENT);
        filament.setStrokeWidth(3);
        var filamentLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.filament"), filamentX - 50, centerY, 10, false, Color.WHITE);

        var spot1 = new Ellipse(centerX + 30, centerY - 20, 18, 9);
        spot1.setFill(Color.TRANSPARENT);
        spot1.setStroke(Color.rgb(255, 170, 0));
        spot1.setStrokeWidth(2);
        var spot2 = new Ellipse(centerX + 40, centerY + 25, 12, 6);
        spot2.setFill(Color.TRANSPARENT);
        spot2.setStroke(Color.rgb(255, 170, 0));
        spot2.setStrokeWidth(2);

        var diskElements = List.of(disk, diskLabel);
        var featureElements = List.of(filament, filamentLabel, spot1, spot2);
        pane.getChildren().addAll(diskElements);
        pane.getChildren().addAll(featureElements);

        // Labels to the right of the disk - x is text center, so offset by half text width (~60px)
        var dataLabelX = centerX + diskRadius + 70;
        var dataLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.all.data"), dataLabelX, centerY - 10, 11, false, COLOR_SUCCESS);
        var dataLabel2 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.we.have"), dataLabelX, centerY + 8, 11, false, COLOR_SUCCESS);
        pane.getChildren().addAll(dataLabel, dataLabel2);

        var explainBox = new Rectangle(60, 235, 460, 85);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase2.explain1"), 290, 255, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase2.explain2"), 290, 275, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase2.explain3"), 290, 300, 12, false, COLOR_HIGHLIGHT);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            diskElements.forEach(n -> n.setOpacity(0));
            featureElements.forEach(n -> n.setOpacity(0));
            dataLabel.setOpacity(0);
            dataLabel2.setOpacity(0);
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();

        // Hide all elements at animation start to prevent flash on loop
        HelpAnimationController.hideAtStart(animation, diskElements);
        HelpAnimationController.hideAtStart(animation, featureElements);
        HelpAnimationController.hideAtStart(animation, dataLabel);
        HelpAnimationController.hideAtStart(animation, dataLabel2);
        HelpAnimationController.hideAtStart(animation, explainElements);

        var t = 0.3;

        HelpAnimationController.addFadeIn(animation, diskElements, t, 0.4);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, featureElements, t, 0.4);
        t += 0.5;

        HelpAnimationController.addFadeIn(animation, dataLabel, t, 0.3);
        HelpAnimationController.addFadeIn(animation, dataLabel2, t + 0.15, 0.3);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase3Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase3.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        double flatDiskY = 120;
        double flatDiskRadius = 50;
        var flatDisk = new Circle(120, flatDiskY, flatDiskRadius);
        flatDisk.setFill(COLOR_SUN.deriveColor(0, 1, 1, 0.2));
        flatDisk.setStroke(COLOR_SUN);
        flatDisk.setStrokeWidth(2);

        var filamentFlat = new QuadCurve(95, flatDiskY - 25, 88, flatDiskY, 95, flatDiskY + 25);
        filamentFlat.setFill(Color.TRANSPARENT);
        filamentFlat.setStroke(COLOR_FILAMENT);
        filamentFlat.setStrokeWidth(2);

        var spotFlat = new Ellipse(140, flatDiskY - 10, 12, 6);
        spotFlat.setFill(Color.TRANSPARENT);
        spotFlat.setStroke(Color.rgb(255, 170, 0));
        spotFlat.setStrokeWidth(1.5);

        var flatLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.flat.image"), 120, flatDiskY + flatDiskRadius + 18, 10, false, COLOR_TEXT_DIM);

        var flatElements = List.of(flatDisk, filamentFlat, spotFlat, flatLabel);
        pane.getChildren().addAll(flatElements);

        var arrow = createArrow(190, flatDiskY, 290, flatDiskY, COLOR_HIGHLIGHT);
        var arrowLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.mapping"), 240, flatDiskY - 15, 10, false, COLOR_HIGHLIGHT);
        pane.getChildren().addAll(arrow);
        pane.getChildren().add(arrowLabel);

        double sphereX = 420;
        double sphereY = 120;
        double sphereRadius = 70;

        var sphere = new Circle(sphereX, sphereY, sphereRadius);
        sphere.setFill(COLOR_SUN.deriveColor(0, 1, 1, 0.13));
        sphere.setStroke(COLOR_SUN);
        sphere.setStrokeWidth(2.5);

        for (var rx : new int[]{70, 50, 25}) {
            var longitude = new Ellipse(sphereX, sphereY, rx, sphereRadius);
            longitude.setFill(Color.TRANSPARENT);
            longitude.setStroke(COLOR_SUN.deriveColor(0, 1, 1, 0.2 + (70 - rx) * 0.005));
            longitude.setStrokeWidth(1);
            pane.getChildren().add(longitude);
        }
        for (var lat : new int[][]{{0, 15}, {-30, 12}, {30, 12}}) {
            var latitude = new Ellipse(sphereX, sphereY + lat[0], sphereRadius, lat[1]);
            latitude.setFill(Color.TRANSPARENT);
            latitude.setStroke(COLOR_SUN.deriveColor(0, 1, 1, 0.15));
            latitude.setStrokeWidth(1);
            pane.getChildren().add(latitude);
        }

        var filament3dX = sphereX - 50;
        var filament3d = new QuadCurve(filament3dX, sphereY - 35, filament3dX - 10, sphereY, filament3dX, sphereY + 35);
        filament3d.setFill(Color.TRANSPARENT);
        filament3d.setStroke(COLOR_FILAMENT);
        filament3d.setStrokeWidth(3);

        var spot3d = new Ellipse(sphereX + 25, sphereY - 15, 15, 8);
        spot3d.setFill(Color.TRANSPARENT);
        spot3d.setStroke(Color.rgb(255, 170, 0));
        spot3d.setStrokeWidth(2);

        var sphereLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.hemisphere"), sphereX, sphereY + sphereRadius + 18, 10, false, COLOR_SUCCESS);

        var sphereElements = new ArrayList<Node>();
        sphereElements.add(sphere);
        sphereElements.add(filament3d);
        sphereElements.add(spot3d);
        sphereElements.add(sphereLabel);
        pane.getChildren().addAll(sphereElements);

        var explainBox = new Rectangle(60, 235, 460, 85);
        explainBox.setFill(Color.rgb(40, 40, 50, 0.9));
        explainBox.setStroke(Color.rgb(100, 100, 120, 0.5));
        explainBox.setStrokeWidth(1);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase3.explain1"), 290, 255, 11, false, COLOR_TEXT);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase3.explain2"), 290, 275, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase3.explain3"), 290, 300, 12, false, COLOR_SUCCESS);

        var explainElements = List.of(explainBox, explain1, explain2, explain3);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            flatElements.forEach(n -> n.setOpacity(0));
            arrow.forEach(n -> n.setOpacity(0));
            arrowLabel.setOpacity(0);
            sphereElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();

        // Hide all elements at animation start to prevent flash on loop
        HelpAnimationController.hideAtStart(animation, flatElements);
        HelpAnimationController.hideAtStart(animation, arrow);
        HelpAnimationController.hideAtStart(animation, arrowLabel);
        HelpAnimationController.hideAtStart(animation, sphereElements);
        HelpAnimationController.hideAtStart(animation, explainElements);

        var t = 0.3;

        HelpAnimationController.addFadeIn(animation, flatElements, t, 0.4);
        t += 0.6;

        HelpAnimationController.addFadeIn(animation, arrow, t, 0.3);
        HelpAnimationController.addFadeIn(animation, arrowLabel, t, 0.3);
        t += 0.5;

        HelpAnimationController.addFadeIn(animation, sphereElements, t, 0.5);
        t += 0.7;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private HelpAnimationController.PhaseResult createPhase4Pane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        var centerX = DIAGRAM_WIDTH / 2;

        var title = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase4.title"), centerX, 25, 13, false, Color.gray(0.75));
        pane.getChildren().add(title);

        // Left side: side view showing true depth
        double sunX = 145;
        double sunY = 130;
        double sunRadius = 70;

        var sun = new Circle(sunX, sunY, sunRadius);
        sun.setFill(COLOR_SUN.deriveColor(0, 1, 1, 0.13));
        sun.setStroke(COLOR_SUN);
        sun.setStrokeWidth(2.5);

        // Filament starts at sun's edge and curves outward
        // QuadCurve with control point at (ctrlX, ctrlY) reaches maximum extent at t=0.5
        // At t=0.5: x = 0.25*startX + 0.5*ctrlX + 0.25*endX = startX - 0.5*(startX - ctrlX)
        // So if we want the curve to reach filamentExtent pixels from the sun, set control = startX - 2*extent
        var filamentX = sunX - sunRadius;
        double filamentExtent = 25;  // How far the filament actually extends from sun
        var filamentCtrlX = filamentX - 2 * filamentExtent;  // Control point for curve to reach filamentExtent
        var filamentSide = new QuadCurve(filamentX, sunY - 35, filamentCtrlX, sunY, filamentX, sunY + 35);
        filamentSide.setFill(Color.TRANSPARENT);
        filamentSide.setStroke(COLOR_FILAMENT);
        filamentSide.setStrokeWidth(3);

        // Depth line with connectors showing actual extent
        var depthLineY = sunY + 50;
        var actualLeftX = filamentX - filamentExtent;  // Where curve actually reaches
        var depthLine = new Line(actualLeftX, depthLineY, filamentX, depthLineY);
        depthLine.setStroke(COLOR_HIGHLIGHT);
        depthLine.setStrokeWidth(2);

        // Vertical connectors aligned with actual curve positions
        var depthConnector1 = new Line(actualLeftX, sunY, actualLeftX, depthLineY);
        depthConnector1.setStroke(COLOR_HIGHLIGHT.deriveColor(0, 1, 1, 0.5));
        depthConnector1.setStrokeWidth(1);
        depthConnector1.getStrokeDashArray().addAll(3.0, 3.0);

        var depthConnector2 = new Line(filamentX, sunY, filamentX, depthLineY);
        depthConnector2.setStroke(COLOR_HIGHLIGHT.deriveColor(0, 1, 1, 0.5));
        depthConnector2.setStrokeWidth(1);
        depthConnector2.getStrokeDashArray().addAll(3.0, 3.0);

        var depthLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.real.depth"), (actualLeftX + filamentX) / 2, depthLineY + 15, 10, true, COLOR_HIGHLIGHT);

        // Label below the sun, not inside (to avoid clutter)
        var sunSideLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.side.view"), sunX, sunY + sunRadius + 15, 10, false, COLOR_TEXT);

        var sideElements = List.of(sun, filamentSide, depthLine, depthConnector1, depthConnector2, depthLabel, sunSideLabel);
        pane.getChildren().addAll(sideElements);

        // Right side: 3D view showing flat appearance
        double sphereX = 435;
        double sphereY = 130;
        double sphereRadius = 70;

        var sphere = new Circle(sphereX, sphereY, sphereRadius);
        sphere.setFill(COLOR_SUN.deriveColor(0, 1, 1, 0.13));
        sphere.setStroke(COLOR_SUN);
        sphere.setStrokeWidth(2.5);

        for (var rx : new int[]{70, 50, 25}) {
            var longitude = new Ellipse(sphereX, sphereY, rx, sphereRadius);
            longitude.setFill(Color.TRANSPARENT);
            longitude.setStroke(COLOR_SUN.deriveColor(0, 1, 1, 0.15));
            longitude.setStrokeWidth(1);
            pane.getChildren().add(longitude);
        }

        var filament3dX = sphereX - 50;
        var filament3d = new QuadCurve(filament3dX, sphereY - 35, filament3dX - 10, sphereY, filament3dX, sphereY + 35);
        filament3d.setFill(Color.TRANSPARENT);
        filament3d.setStroke(COLOR_FILAMENT);
        filament3d.setStrokeWidth(3);

        // "(appears flat)" label positioned to the right of the sphere, pointing to the filament
        var flatOnSurfaceLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.appears.flat"), sphereX + sphereRadius + 10, sphereY, 10, false, COLOR_HIGHLIGHT);

        // Label below the sphere (not inside to avoid overlap with flatOnSurfaceLabel)
        var sphere3dLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.3d.view"), sphereX, sphereY + sphereRadius + 15, 10, false, COLOR_TEXT);

        var sphereElements = List.of(sphere, filament3d, flatOnSurfaceLabel, sphere3dLabel);
        pane.getChildren().addAll(sphereElements);

        // Dashed line between the two views with "≠" symbol above
        var arrowLine = new Line(sunX + sunRadius + 15, sunY, sphereX - sphereRadius - 15, sunY);
        arrowLine.setStroke(COLOR_TEXT_DIM);
        arrowLine.setStrokeWidth(2);
        arrowLine.getStrokeDashArray().addAll(8.0, 4.0);

        var notEqualSign = createDiagramText("≠", centerX, sunY - 20, 20, true, COLOR_WARNING);

        var comparisonElements = List.of(arrowLine, notEqualSign);
        pane.getChildren().addAll(comparisonElements);

        // Explanation box - compact layout
        var explainBox = new Rectangle(40, 230, 500, 85);
        explainBox.setFill(Color.rgb(50, 40, 45, 0.95));
        explainBox.setStroke(COLOR_WARNING.deriveColor(0, 1, 1, 0.6));
        explainBox.setStrokeWidth(1);
        explainBox.setArcWidth(8);
        explainBox.setArcHeight(8);

        var explain1 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase4.explain1"), 290, 248, 12, true, COLOR_WARNING);
        var explain2 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase4.explain2"), 290, 268, 11, false, COLOR_TEXT);
        var explain3 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase4.explain3"), 290, 286, 11, false, COLOR_TEXT);
        var explain4 = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.phase4.explain4"), 290, 306, 10, false, COLOR_HIGHLIGHT);

        var explainElements = List.of(explainBox, explain1, explain2, explain3, explain4);
        pane.getChildren().addAll(explainElements);

        Runnable resetElements = () -> {
            sideElements.forEach(n -> n.setOpacity(0));
            sphereElements.forEach(n -> n.setOpacity(0));
            comparisonElements.forEach(n -> n.setOpacity(0));
            explainElements.forEach(n -> n.setOpacity(0));
        };

        resetElements.run();

        var animation = new Timeline();

        // Hide all elements at animation start to prevent flash on loop
        HelpAnimationController.hideAtStart(animation, sideElements);
        HelpAnimationController.hideAtStart(animation, sphereElements);
        HelpAnimationController.hideAtStart(animation, comparisonElements);
        HelpAnimationController.hideAtStart(animation, explainElements);

        var t = 0.3;

        HelpAnimationController.addFadeIn(animation, sideElements, t, 0.5);
        t += 0.7;

        HelpAnimationController.addFadeIn(animation, sphereElements, t, 0.5);
        t += 0.5;

        HelpAnimationController.addFadeIn(animation, comparisonElements, t, 0.3);
        t += 0.5;

        HelpAnimationController.addFadeIn(animation, explainElements, t, 0.4);

        return new HelpAnimationController.PhaseResult(pane, animation, resetElements);
    }

    private List<Node> createArrow(double startX, double startY, double endX, double endY, Color color) {
        var angle = Math.atan2(endY - startY, endX - startX);
        var arrowSize = 10;

        // Line ends before the arrowhead tip to avoid overlap
        var lineEndX = endX - arrowSize * Math.cos(angle);
        var lineEndY = endY - arrowSize * Math.sin(angle);

        var line = new Line(startX, startY, lineEndX, lineEndY);
        line.setStroke(color);
        line.setStrokeWidth(2);

        var arrowHead = new Polygon(
                endX, endY,
                endX - arrowSize * Math.cos(angle - Math.PI / 6), endY - arrowSize * Math.sin(angle - Math.PI / 6),
                endX - arrowSize * Math.cos(angle + Math.PI / 6), endY - arrowSize * Math.sin(angle + Math.PI / 6)
        );
        arrowHead.setFill(color);

        return List.of(line, arrowHead);
    }
}
