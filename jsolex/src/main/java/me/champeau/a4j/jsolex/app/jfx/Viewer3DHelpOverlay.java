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
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
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
import javafx.scene.shape.QuadCurve;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;

/**
 * Overlay for 3D viewers showing a help button.
 * When clicked, displays explanatory information about the view.
 * This overlay should not be included in exports.
 */
public class Viewer3DHelpOverlay extends AbstractHelpOverlay {

    private static final double DIAGRAM_WIDTH = 550;
    private static final double DIAGRAM_HEIGHT = 300;
    private static final Duration FADE_DURATION = Duration.seconds(0.8);
    private static final Duration DISPLAY_DURATION = Duration.seconds(4);

    private final String i18nBundle;
    private final boolean showDiagram;
    private SequentialTransition diagramAnimation;

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
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }
    }

    @Override
    protected void onPopupShown() {
        startDiagramAnimation();
    }

    @Override
    protected void onPopupHidden() {
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }
    }

    private void startDiagramAnimation() {
        if (diagramAnimation != null) {
            diagramAnimation.playFromStart();
        }
    }

    private void stopDiagramAnimation() {
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }
    }

    @Override
    protected StackPane createHelpPopup() {
        var titleLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "help.title"));
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        var helpText = I18N.string(JSolEx.class, i18nBundle, "help.text");
        var textFlow = parseFormattedText(helpText);
        textFlow.setMaxWidth(680);

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
            var diagram = createScalableDiagram(createProjectionDiagram(), this::createProjectionDiagram, this::startDiagramAnimation, this::stopDiagramAnimation);
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
        scrollPane.setMaxHeight(600);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        var content = createContentPane(scrollPane, 750, 650);
        return wrapInOverlay(content);
    }

    private Node createProjectionDiagram() {
        var diagramPane = new StackPane();
        diagramPane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        diagramPane.setStyle("-fx-background-color: #1a1a24; -fx-background-radius: 8;");

        var sideView = createSideViewPane();
        var view3d = create3DViewPane();

        sideView.setOpacity(1.0);
        view3d.setOpacity(0.0);

        diagramPane.getChildren().addAll(view3d, sideView);

        diagramAnimation = createDiagramAnimation(sideView, view3d);

        var titleLabel = new Label(I18N.string(JSolEx.class, i18nBundle, "help.diagram.title"));
        titleLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));
        titleLabel.setTextFill(Color.gray(0.6));

        var container = new VBox(8, diagramPane, titleLabel);
        container.setAlignment(Pos.CENTER);

        return container;
    }

    private Pane createSideViewPane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        double centerX = DIAGRAM_WIDTH / 2;
        double centerY = DIAGRAM_HEIGHT / 2;
        double sunRadius = 100;

        var sideViewLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.side.view"), centerX, 25, 13, false, Color.gray(0.75));

        var observerLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.observer"), 55, centerY - 70, 11, false, Color.rgb(102, 136, 255));

        var eye = new Ellipse(55, centerY, 18, 12);
        eye.setFill(Color.TRANSPARENT);
        eye.setStroke(Color.rgb(102, 136, 255));
        eye.setStrokeWidth(2.5);
        var pupil = new Circle(55, centerY, 5, Color.rgb(102, 136, 255));

        var sunSide = new Circle(centerX + 60, centerY, sunRadius);
        sunSide.setFill(Color.rgb(255, 102, 51, 0.13));
        sunSide.setStroke(Color.rgb(255, 102, 51));
        sunSide.setStrokeWidth(2.5);
        var sunLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.sun"), centerX + 60, centerY + sunRadius + 20, 11, false, Color.rgb(255, 102, 51));

        double filamentX = centerX + 60 - sunRadius;
        var filament = new QuadCurve(filamentX, centerY - 50, filamentX - 50, centerY, filamentX, centerY + 50);
        filament.setFill(Color.TRANSPARENT);
        filament.setStroke(Color.rgb(204, 51, 51));
        filament.setStrokeWidth(4);
        var filamentLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.filament"), filamentX - 60, centerY - 10, 11, false, Color.rgb(204, 51, 51));

        for (int dy : new int[]{-50, -25, 0, 25, 50}) {
            double endX = (dy == -50 || dy == 50) ? filamentX : filamentX - 20;
            var line = new Line(90, centerY + dy, endX, centerY + dy);
            line.setStroke(Color.rgb(102, 136, 255, 0.5));
            line.setStrokeWidth(1);
            line.getStrokeDashArray().addAll(4.0, 4.0);
            pane.getChildren().add(line);
        }

        // QuadCurve's midpoint is at (filamentX - 25, centerY) due to Bezier math
        var depthLine = new Line(filamentX - 25, centerY, filamentX, centerY);
        depthLine.setStroke(Color.rgb(68, 255, 68));
        depthLine.setStrokeWidth(1.5);
        depthLine.getStrokeDashArray().addAll(3.0, 3.0);
        var depthLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.depth"), filamentX - 12, centerY + 12, 10, false, Color.rgb(68, 255, 68));

        pane.getChildren().addAll(
                sideViewLabel, observerLabel, eye, pupil,
                sunSide, sunLabel, filament, filamentLabel,
                depthLine, depthLabel
        );

        return pane;
    }

    private Pane create3DViewPane() {
        var pane = new Pane();
        pane.setPrefSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        double centerX = DIAGRAM_WIDTH / 2;
        double centerY = DIAGRAM_HEIGHT / 2;
        double sunRadius = 100;

        var view3dLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.3d.view"), centerX, 25, 13, false, Color.gray(0.75));

        var sun3d = new Circle(centerX, centerY, sunRadius);
        sun3d.setFill(Color.rgb(255, 102, 51, 0.13));
        sun3d.setStroke(Color.rgb(255, 102, 51));
        sun3d.setStrokeWidth(2.5);

        for (int rx : new int[]{100, 72, 40}) {
            var longitude = new Ellipse(centerX, centerY, rx, sunRadius);
            longitude.setFill(Color.TRANSPARENT);
            longitude.setStroke(Color.rgb(255, 102, 51, 0.2 + (100 - rx) * 0.003));
            longitude.setStrokeWidth(1);
            pane.getChildren().add(longitude);
        }
        for (int[] lat : new int[][]{{0, 22}, {-40, 18}, {40, 18}}) {
            var latitude = new Ellipse(centerX, centerY + lat[0], sunRadius, lat[1]);
            latitude.setFill(Color.TRANSPARENT);
            latitude.setStroke(Color.rgb(255, 102, 51, 0.15));
            latitude.setStrokeWidth(1);
            pane.getChildren().add(latitude);
        }

        double filamentX = centerX - 72;
        var filament3d = new QuadCurve(filamentX, centerY - 50, filamentX - 14, centerY, filamentX, centerY + 50);
        filament3d.setFill(Color.TRANSPARENT);
        filament3d.setStroke(Color.rgb(204, 51, 51));
        filament3d.setStrokeWidth(4);

        var filament3dLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.filament"), filamentX - 60, centerY - 25, 11, true, Color.rgb(204, 51, 51));
        var flatLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.appears.flat"), filamentX - 60, centerY - 8, 10, false, Color.rgb(255, 204, 102));

        var spot1 = new Ellipse(centerX + 45, centerY - 30, 22, 11);
        spot1.setFill(Color.TRANSPARENT);
        spot1.setStroke(Color.rgb(255, 170, 0));
        spot1.setStrokeWidth(2.5);
        var spot2 = new Ellipse(centerX + 55, centerY + 35, 16, 8);
        spot2.setFill(Color.TRANSPARENT);
        spot2.setStroke(Color.rgb(255, 170, 0));
        spot2.setStrokeWidth(2.5);

        var surfaceLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.surface.features"), centerX + 115, centerY - 35, 11, true, Color.rgb(68, 255, 68));
        var scaleLabel = createDiagramText(I18N.string(JSolEx.class, i18nBundle, "help.diagram.scale.preserved"), centerX + 115, centerY - 18, 10, false, Color.rgb(68, 255, 68));

        pane.getChildren().addAll(
                view3dLabel, sun3d, filament3d, filament3dLabel, flatLabel,
                spot1, spot2, surfaceLabel, scaleLabel
        );

        return pane;
    }

    private SequentialTransition createDiagramAnimation(Pane sideView, Pane view3d) {
        sideView.setScaleX(1.0);
        view3d.setScaleX(0.0);

        var rotateTo3d = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(sideView.scaleXProperty(), 1.0),
                        new KeyValue(sideView.opacityProperty(), 1.0),
                        new KeyValue(view3d.scaleXProperty(), 0.0),
                        new KeyValue(view3d.opacityProperty(), 0.0)
                ),
                new KeyFrame(FADE_DURATION.divide(2),
                        new KeyValue(sideView.scaleXProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(sideView.opacityProperty(), 0.5),
                        new KeyValue(view3d.scaleXProperty(), 0.0),
                        new KeyValue(view3d.opacityProperty(), 0.5)
                ),
                new KeyFrame(FADE_DURATION,
                        new KeyValue(sideView.scaleXProperty(), 0.0),
                        new KeyValue(sideView.opacityProperty(), 0.0),
                        new KeyValue(view3d.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(view3d.opacityProperty(), 1.0)
                )
        );

        var rotateToSide = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(view3d.scaleXProperty(), 1.0),
                        new KeyValue(view3d.opacityProperty(), 1.0),
                        new KeyValue(sideView.scaleXProperty(), 0.0),
                        new KeyValue(sideView.opacityProperty(), 0.0)
                ),
                new KeyFrame(FADE_DURATION.divide(2),
                        new KeyValue(view3d.scaleXProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(view3d.opacityProperty(), 0.5),
                        new KeyValue(sideView.scaleXProperty(), 0.0),
                        new KeyValue(sideView.opacityProperty(), 0.5)
                ),
                new KeyFrame(FADE_DURATION,
                        new KeyValue(view3d.scaleXProperty(), 0.0),
                        new KeyValue(view3d.opacityProperty(), 0.0),
                        new KeyValue(sideView.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(sideView.opacityProperty(), 1.0)
                )
        );

        var pauseOnSide = new PauseTransition(DISPLAY_DURATION);
        var pauseOn3d = new PauseTransition(DISPLAY_DURATION);

        var animation = new SequentialTransition(
                pauseOnSide,
                rotateTo3d,
                pauseOn3d,
                rotateToSide
        );
        animation.setCycleCount(SequentialTransition.INDEFINITE);

        return animation;
    }

}
