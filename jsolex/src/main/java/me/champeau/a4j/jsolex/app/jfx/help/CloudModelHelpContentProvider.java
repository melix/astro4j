/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.help;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.jfx.PageIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

import static me.champeau.a4j.jsolex.app.jfx.help.HelpDiagrams.COLOR_BLUE;
import static me.champeau.a4j.jsolex.app.jfx.help.HelpDiagrams.COLOR_HIGHLIGHT;
import static me.champeau.a4j.jsolex.app.jfx.help.HelpDiagrams.COLOR_RED;
import static me.champeau.a4j.jsolex.app.jfx.help.HelpDiagrams.COLOR_TEXT;
import static me.champeau.a4j.jsolex.app.jfx.help.HelpDiagrams.createText;
import static me.champeau.a4j.jsolex.app.jfx.help.HelpDiagrams.setCenteredText;

/**
 * Provides an animated diagram explaining the cloud model, in five phases:
 * 1. Absorbing material floating above the surface dims the light coming from below
 * 2. The cloud deepens the profile of the line
 * 3. The motion of the cloud shifts its absorption (Doppler effect)
 * 4. The agitation of the atoms broadens its absorption (temperature)
 * 5. The model is adjusted at each point, and the results are turned into maps
 */
public class CloudModelHelpContentProvider implements ImageHelpContentProvider {

    private static final double DIAGRAM_WIDTH = 450;
    private static final double DIAGRAM_HEIGHT = 300;
    private static final double PHASE_SECONDS = 6;
    private static final double FADE_SECONDS = 0.4;
    private static final int PHASES = 5;
    private static final int CURVE_POINTS = 120;
    private static final double MAX_OFFSET = 2.5;

    private static final Color COLOR_DIM = Color.rgb(150, 150, 150);
    private static final Color COLOR_LIGHT = Color.rgb(255, 220, 120);
    private static final Color COLOR_CLOUD = Color.rgb(120, 60, 50);
    private static final Color COLOR_BACKGROUND_CURVE = Color.rgb(150, 150, 150);
    private static final Color COLOR_ABSORBED = Color.rgb(200, 90, 70, 0.55);
    private static final Color COLOR_LEGEND_BACKGROUND = Color.rgb(43, 43, 43, 0.85);

    private static final String I18N_BUNDLE = "image-help-cloud";

    private Timeline animation;

    @Override
    public Node createContent() {
        var phases = List.of(createAbsorptionPhase(), createProfilePhase(), createVelocityPhase(), createTemperaturePhase(), createInversionPhase());
        var container = new StackPane();
        phases.forEach(phase -> container.getChildren().add(phase.pane()));
        container.setAlignment(Pos.CENTER);
        container.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        container.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        var time = new SimpleDoubleProperty();
        var indicator = new PageIndicator(PHASES, phase -> {
            if (animation != null) {
                animation.playFrom(Duration.seconds(phase * PHASE_SECONDS));
            }
        });
        time.addListener((obs, oldValue, newValue) -> update(phases, indicator, newValue.doubleValue()));
        update(phases, indicator, 0);
        if (animation != null) {
            animation.stop();
        }
        animation = createAnimation(time);
        var content = new VBox(10, container, indicator.node());
        content.setAlignment(Pos.CENTER);
        return content;
    }

    private static int phaseAt(double time) {
        return Math.min(PHASES - 1, (int) (time / PHASE_SECONDS));
    }

    private static void update(List<Phase> phases, PageIndicator indicator, double time) {
        var index = phaseAt(time);
        var local = time - index * PHASE_SECONDS;
        for (var i = 0; i < PHASES; i++) {
            phases.get(i).pane().setOpacity(i == index ? opacityAt(local) : 0);
        }
        indicator.select(index);
        phases.get(index).updater().accept(local / PHASE_SECONDS);
    }

    private static double opacityAt(double local) {
        if (local < FADE_SECONDS) {
            return local / FADE_SECONDS;
        }
        if (local > PHASE_SECONDS - FADE_SECONDS) {
            return (PHASE_SECONDS - local) / FADE_SECONDS;
        }
        return 1;
    }

    private static Timeline createAnimation(DoubleProperty time) {
        var total = PHASES * PHASE_SECONDS;
        var timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(time, 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.seconds(total), new KeyValue(time, total - 1e-6, Interpolator.LINEAR))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    private static Phase createAbsorptionPhase() {
        var pane = newPhase("phase1.title", "phase1.caption");
        var surfaceTop = 205.0;
        var surface = new Rectangle(20, surfaceTop, DIAGRAM_WIDTH - 40, 22);
        surface.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 210, 110)), new Stop(1, Color.rgb(200, 90, 30))));
        surface.setArcWidth(8);
        surface.setArcHeight(8);
        var surfaceLabel = createText(msg("phase1.surface"), 90, surfaceTop + 15, 10, true);
        surfaceLabel.setFill(Color.rgb(60, 30, 10));
        var cloudX = DIAGRAM_WIDTH / 2;
        var cloudY = 130.0;
        var cloud = new Ellipse(cloudX, cloudY, 70, 16);
        cloud.setFill(COLOR_CLOUD.deriveColor(0, 1, 1, 0.85));
        cloud.setStroke(COLOR_CLOUD.brighter());
        var cloudLabel = createText(msg("phase1.cloud"), cloudX + 150, cloudY + 4, 10, false);
        cloudLabel.setFill(COLOR_HIGHLIGHT);
        var eye = createEye(cloudX, 58);
        var observer = createText(msg("phase1.observer"), cloudX + 60, 62, 10, false);
        var photons = new ArrayList<Circle>();
        var group = new Group(surface, surfaceLabel);
        for (var i = 0; i < 14; i++) {
            var photon = new Circle(45 + i * 26.5, surfaceTop, 3.5, COLOR_LIGHT);
            photons.add(photon);
            group.getChildren().add(photon);
        }
        group.getChildren().addAll(cloud, cloudLabel, eye, observer);
        pane.getChildren().add(group);
        return new Phase(pane, progress -> {
            for (var i = 0; i < photons.size(); i++) {
                var photon = photons.get(i);
                var cycle = (progress * 3 + i * 0.37) % 1;
                var y = surfaceTop - cycle * (surfaceTop - 75);
                photon.setCenterY(y);
                var crossed = Math.abs(photon.getCenterX() - cloudX) < 70 && y < cloudY;
                photon.setOpacity(crossed ? 0.25 : 1);
                photon.setRadius(crossed ? 2.5 : 3.5);
            }
        });
    }

    private static Phase createProfilePhase() {
        var pane = newPhase("phase2.title", "phase2.caption");
        var plot = new Plot(60, 55, DIAGRAM_WIDTH - 40, 200);
        var profiles = plot.withCloudProfiles();
        pane.getChildren().addAll(plot.group, createLegend(plot, true));
        return new Phase(pane, progress -> {
            var strength = Math.min(1, progress / 0.6);
            profiles.show(x -> withCloud(x, 1.4 * strength, 0, 0.35));
        });
    }

    private static Phase createVelocityPhase() {
        var pane = newPhase("phase3.title", "phase3.caption");
        var plot = new Plot(60, 85, DIAGRAM_WIDTH - 40, 210);
        var profiles = plot.withCloudProfiles();
        var marker = new Line(0, plot.top, 0, plot.bottom);
        marker.getStrokeDashArray().addAll(4d, 4d);
        var cloud = new Ellipse(DIAGRAM_WIDTH / 2, 48, 30, 8);
        cloud.setFill(COLOR_CLOUD);
        var arrow = new Polygon();
        var velocity = createText("", 105, 52, 11, true);
        pane.getChildren().addAll(plot.group, marker, cloud, arrow, velocity, createLegend(plot, false));
        return new Phase(pane, progress -> {
            var speed = -15 * Math.sin(2 * Math.PI * progress);
            var shift = speed * 0.035;
            profiles.show(x -> withCloud(x, 1.4, shift, 0.3));
            var markerX = plot.toX(shift);
            marker.setStartX(markerX);
            marker.setEndX(markerX);
            var color = speed < 0 ? COLOR_BLUE : COLOR_RED;
            marker.setStroke(color);
            cloud.setCenterX(markerX);
            var tip = speed < 0 ? 38.0 : 58.0;
            var base = speed < 0 ? 50.0 : 46.0;
            var arrowX = markerX + 45;
            arrow.getPoints().setAll(arrowX, tip, arrowX - 6, base, arrowX + 6, base);
            arrow.setFill(color);
            arrow.setOpacity(Math.abs(speed) > 1 ? 1 : 0);
            var atRest = Math.abs(speed) < 0.5;
            var key = atRest ? "phase3.rest" : speed < 0 ? "phase3.towards" : "phase3.away";
            setCenteredText(velocity, String.format(Locale.US, msg(key), Math.abs(speed)), 105);
            velocity.setFill(atRest ? COLOR_TEXT : color);
            marker.setStroke(atRest ? COLOR_TEXT : color);
        });
    }

    private static Phase createTemperaturePhase() {
        var pane = newPhase("phase4.title", "phase4.caption");
        var plot = new Plot(60, 90, DIAGRAM_WIDTH - 40, 210);
        var profiles = plot.withCloudProfiles();
        var box = new Rectangle(40, 36, 90, 30);
        box.setFill(COLOR_CLOUD.deriveColor(0, 1, 1, 0.6));
        box.setArcWidth(10);
        box.setArcHeight(10);
        var atoms = new ArrayList<Circle>();
        var group = new Group(plot.group, box);
        for (var i = 0; i < 8; i++) {
            var atom = new Circle(50 + i * 10, 51, 2.5, COLOR_LIGHT);
            atoms.add(atom);
            group.getChildren().add(atom);
        }
        var temperature = createText("", DIAGRAM_WIDTH / 2 + 60, 56, 11, true);
        temperature.setFill(COLOR_HIGHLIGHT);
        group.getChildren().add(temperature);
        pane.getChildren().add(group);
        return new Phase(pane, progress -> {
            var heat = 0.5 - 0.5 * Math.cos(2 * Math.PI * progress);
            var width = 0.22 + 0.3 * heat;
            profiles.show(x -> withCloud(x, 1.4, 0, width));
            for (var i = 0; i < atoms.size(); i++) {
                var phase = 2 * Math.PI * (progress * (6 + 10 * heat) + i * 0.23);
                var amplitude = 2 + 10 * heat;
                atoms.get(i).setCenterX(50 + i * 10 + amplitude * 0.5 * Math.sin(phase * 1.3));
                atoms.get(i).setCenterY(51 + amplitude * 0.5 * Math.cos(phase));
            }
            var kelvins = Math.round((5000 + 10000 * heat) / 500) * 500;
            setCenteredText(temperature, String.format(Locale.US, msg("phase4.temperature"), kelvins), DIAGRAM_WIDTH / 2 + 60);
        });
    }

    private static Phase createInversionPhase() {
        var pane = newPhase("phase5.title", "phase5.caption");
        var plot = new Plot(40, 55, 270, 215);
        var model = plot.curve(COLOR_HIGHLIGHT, false);
        var points = new Group();
        for (var i = 0; i < 21; i++) {
            var x = -MAX_OFFSET + i * 2 * MAX_OFFSET / 20;
            var noise = 0.02 * Math.sin(i * 12.9898);
            points.getChildren().add(new Circle(plot.toX(x), plot.toY(withCloud(x, 1.2, 0.25, 0.35) + noise), 2.5, COLOR_TEXT));
        }
        var observedLabel = createText(msg("phase5.observed"), 225, plot.bottom - 28, 10, false);
        var modelLabel = createText(msg("phase5.model"), 225, plot.bottom - 12, 10, false);
        modelLabel.setFill(COLOR_HIGHLIGHT);
        var cells = new ArrayList<Rectangle>();
        var grid = new Group();
        var cellSize = 13.0;
        var gridLeft = 305.0;
        var gridTop = 80.0;
        for (var row = 0; row < 8; row++) {
            for (var column = 0; column < 8; column++) {
                var cell = new Rectangle(gridLeft + column * cellSize, gridTop + row * cellSize, cellSize - 1, cellSize - 1);
                cell.setFill(Color.rgb(45, 45, 45));
                cell.setStroke(Color.rgb(80, 80, 80));
                cells.add(cell);
                grid.getChildren().add(cell);
            }
        }
        var mapLabel = createText(msg("phase5.map"), gridLeft + 4 * cellSize, gridTop + 8 * cellSize + 16, 10, false);
        pane.getChildren().addAll(plot.group, points, observedLabel, modelLabel, grid, mapLabel);
        return new Phase(pane, progress -> {
            var fit = Interpolator.EASE_BOTH.interpolate(0d, 1d, Math.min(1, progress / 0.5));
            var opacity = 0.3 + 0.9 * fit;
            var shift = 0.25 * fit;
            var width = 0.6 - 0.25 * fit;
            plot.set(model, x -> withCloud(x, opacity, shift, width));
            var filled = progress < 0.5 ? 0 : (int) Math.min(cells.size(), (progress - 0.5) / 0.4 * cells.size());
            for (var i = 0; i < cells.size(); i++) {
                var row = i / 8;
                var column = i % 8;
                var speed = Math.sin(row * 0.9 + 0.5) * Math.cos(column * 0.7 - 1.2);
                cells.get(i).setFill(i < filled ? velocityColor(speed) : Color.rgb(45, 45, 45));
            }
        });
    }

    private static Color velocityColor(double speed) {
        var amount = Math.clamp(Math.abs(speed), 0, 1);
        return Color.rgb(247, 247, 247).interpolate(speed < 0 ? COLOR_BLUE : COLOR_RED, amount);
    }

    private static double background(double x) {
        return 1 - 0.65 * Math.exp(-(x * x) / 0.5);
    }

    private static double withCloud(double x, double opacity, double shift, double width) {
        var d = (x - shift) / width;
        var transmission = Math.exp(-opacity * Math.exp(-d * d));
        return background(x) * transmission + 0.12 * (1 - transmission);
    }

    /**
     * Creates the legend of a line profile plot, in its upper right corner.
     */
    private static Group createLegend(Plot plot, boolean withObserved) {
        var x = plot.right - 115;
        var y = plot.top + 34;
        var dash = new Line(x, y - 3, x + 16, y - 3);
        dash.setStroke(COLOR_BACKGROUND_CURVE);
        dash.setStrokeWidth(2);
        dash.getStrokeDashArray().addAll(6d, 4d);
        var backgroundLabel = new Text(x + 22, y, msg("phase2.background"));
        var patch = new Rectangle(x, y + 9, 16, 9);
        patch.setFill(COLOR_ABSORBED);
        var absorbedLabel = new Text(x + 22, y + 18, msg("phase3.absorbed"));
        var box = new Rectangle(x - 6, y - 12, 125, withObserved ? 50 : 36);
        box.setFill(COLOR_LEGEND_BACKGROUND);
        box.setArcWidth(6);
        box.setArcHeight(6);
        var legend = new Group(box, dash, backgroundLabel, patch, absorbedLabel);
        if (withObserved) {
            var line = new Line(x, y + 29, x + 16, y + 29);
            line.setStroke(COLOR_HIGHLIGHT);
            line.setStrokeWidth(2);
            legend.getChildren().addAll(line, new Text(x + 22, y + 32, msg("phase2.observed")));
        }
        for (var node : legend.getChildren()) {
            if (node instanceof Text text) {
                text.setFont(Font.font("System", FontWeight.NORMAL, 10));
                text.setFill(COLOR_TEXT);
            }
        }
        return legend;
    }

    private static Pane newPhase(String titleKey, String captionKey) {
        var title = createText(msg(titleKey), DIAGRAM_WIDTH / 2, 20, 14, true);
        title.setFill(COLOR_HIGHLIGHT);
        var caption = new Text(msg(captionKey));
        caption.setFont(Font.font("System", FontWeight.NORMAL, 11));
        caption.setFill(COLOR_TEXT);
        caption.setWrappingWidth(DIAGRAM_WIDTH - 40);
        caption.setTextAlignment(TextAlignment.CENTER);
        caption.setTextOrigin(VPos.TOP);
        caption.setX(20);
        caption.setY(DIAGRAM_HEIGHT - 48);
        var pane = new Pane(title, caption);
        pane.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        pane.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        return pane;
    }

    private static Group createEye(double x, double y) {
        var white = new Ellipse(x, y, 14, 7);
        white.setFill(Color.rgb(230, 230, 230));
        var iris = new Circle(x, y + 1, 4.5, Color.rgb(70, 110, 160));
        return new Group(white, iris);
    }

    @Override
    public String introduction() {
        return msg("introduction");
    }

    @Override
    public void onShown() {
        if (animation != null) {
            animation.playFromStart();
        }
    }

    @Override
    public void onHidden() {
        if (animation != null) {
            animation.stop();
        }
    }

    private static String msg(String key) {
        return I18N.string(JSolEx.class, I18N_BUNDLE, key);
    }

    private record Phase(Pane pane, DoubleConsumer updater) {
    }

    private record CloudProfiles(Plot plot, Polygon absorbed, Polyline observed) {
        void show(DoubleUnaryOperator profile) {
            plot.set(observed, profile);
            plot.shade(absorbed, CloudModelHelpContentProvider::background, profile);
        }
    }

    /**
     * A line profile plot: wavelength offset horizontally, intensity vertically.
     */
    private static final class Plot {
        private final double left;
        private final double top;
        private final double right;
        private final double bottom;
        private final Group group = new Group();

        private Plot(double left, double top, double right, double bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            var xAxis = new Line(left, bottom, right, bottom);
            var yAxis = new Line(left, top, left, bottom);
            xAxis.setStroke(COLOR_DIM);
            yAxis.setStroke(COLOR_DIM);
            var blue = createText(msg("plot.blue"), left + 20, bottom + 14, 10, false);
            blue.setFill(COLOR_BLUE);
            var red = createText(msg("plot.red"), right - 20, bottom + 14, 10, false);
            red.setFill(COLOR_RED);
            var wavelength = createText(msg("plot.wavelength"), (left + right) / 2, bottom + 14, 10, false);
            var brightness = createText(msg("plot.brightness"), left, top - 6, 10, false);
            brightness.setX(left - 10);
            group.getChildren().addAll(xAxis, yAxis, blue, red, wavelength, brightness);
        }

        /**
         * Adds the line without the cloud, the observed line and the light absorbed by the cloud between them.
         */
        private CloudProfiles withCloudProfiles() {
            var absorbed = shading();
            var background = curve(COLOR_BACKGROUND_CURVE, true);
            var observed = curve(COLOR_HIGHLIGHT, false);
            set(background, CloudModelHelpContentProvider::background);
            return new CloudProfiles(this, absorbed, observed);
        }

        private Polygon shading() {
            var polygon = new Polygon();
            polygon.setFill(COLOR_ABSORBED);
            group.getChildren().add(polygon);
            return polygon;
        }

        /**
         * Fills the area between two profiles.
         */
        private void shade(Polygon polygon, DoubleUnaryOperator upper, DoubleUnaryOperator lower) {
            var points = new Double[4 * CURVE_POINTS];
            for (var i = 0; i < CURVE_POINTS; i++) {
                var x = -MAX_OFFSET + i * 2 * MAX_OFFSET / (CURVE_POINTS - 1);
                points[2 * i] = toX(x);
                points[2 * i + 1] = toY(upper.applyAsDouble(x));
                var back = 2 * CURVE_POINTS + 2 * i;
                var xBack = MAX_OFFSET - i * 2 * MAX_OFFSET / (CURVE_POINTS - 1);
                points[back] = toX(xBack);
                points[back + 1] = toY(lower.applyAsDouble(xBack));
            }
            polygon.getPoints().setAll(points);
        }

        private Polyline curve(Color color, boolean dashed) {
            var curve = new Polyline();
            curve.setStroke(color);
            curve.setStrokeWidth(2);
            if (dashed) {
                curve.getStrokeDashArray().addAll(6d, 4d);
            }
            group.getChildren().add(curve);
            return curve;
        }

        private void set(Polyline curve, DoubleUnaryOperator profile) {
            var points = new Double[2 * CURVE_POINTS];
            for (var i = 0; i < CURVE_POINTS; i++) {
                var x = -MAX_OFFSET + i * 2 * MAX_OFFSET / (CURVE_POINTS - 1);
                points[2 * i] = toX(x);
                points[2 * i + 1] = toY(profile.applyAsDouble(x));
            }
            curve.getPoints().setAll(points);
        }

        private double toX(double offset) {
            return left + (offset + MAX_OFFSET) / (2 * MAX_OFFSET) * (right - left);
        }

        private double toY(double intensity) {
            return bottom - intensity * (bottom - top) / 1.1;
        }
    }
}
