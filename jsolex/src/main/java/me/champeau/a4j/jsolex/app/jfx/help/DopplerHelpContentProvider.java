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
package me.champeau.a4j.jsolex.app.jfx.help;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;

/**
 * Provides animated help content explaining how Doppler images are created.
 * Shows 3 phases:
 * 1. Two wing images (blue wing left, red wing right)
 * 2. RGB channel combination
 * 3. Final result showing rotation interpretation
 */
public class DopplerHelpContentProvider implements ImageHelpContentProvider {

    private static final double DIAGRAM_WIDTH = 450;
    private static final double DIAGRAM_HEIGHT = 280;
    private static final Duration PHASE_DURATION = Duration.seconds(4);
    private static final Duration FADE_DURATION = Duration.seconds(0.5);

    private static final Color COLOR_BLUE_WING = Color.rgb(80, 140, 255);
    private static final Color COLOR_RED_WING = Color.rgb(255, 100, 80);
    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);

    private static final String I18N_BUNDLE = "image-help-doppler";

    private SequentialTransition animation;

    public DopplerHelpContentProvider() {
    }

    @Override
    public Node createContent() {
        var phase1 = createPhase1();
        var phase2 = createPhase2();
        var phase3 = createPhase3();

        phase1.setOpacity(1.0);
        phase2.setOpacity(0.0);
        phase3.setOpacity(0.0);

        var container = new StackPane(phase1, phase2, phase3);
        container.setAlignment(Pos.CENTER);
        container.setMinSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);
        container.setMaxSize(DIAGRAM_WIDTH, DIAGRAM_HEIGHT);

        animation = createAnimation(phase1, phase2, phase3);

        return container;
    }

    private Group createPhase1() {
        var group = new Group();
        double centerY = DIAGRAM_HEIGHT / 2;

        // Left disk - Blue wing (toward us)
        double leftX = 100;
        var leftDisk = createSunDisk(leftX, centerY, 60, COLOR_BLUE_WING);
        var leftLabel = createText(msg("phase1.blue.wing"), leftX, centerY + 85, 11, false);
        // Arrow pointing LEFT (toward observer)
        var leftArrow = createArrow(leftX - 55, centerY, leftX - 80, centerY, COLOR_BLUE_WING);
        var leftApproach = createText(msg("phase1.approaching"), leftX - 67, centerY - 15, 10, false);
        leftApproach.setFill(COLOR_BLUE_WING);

        // Right disk - Red wing (away from us)
        double rightX = DIAGRAM_WIDTH - 100;
        var rightDisk = createSunDisk(rightX, centerY, 60, COLOR_RED_WING);
        var rightLabel = createText(msg("phase1.red.wing"), rightX, centerY + 85, 11, false);
        // Arrow pointing RIGHT (away from observer)
        var rightArrow = createArrow(rightX + 55, centerY, rightX + 80, centerY, COLOR_RED_WING);
        var rightRecede = createText(msg("phase1.receding"), rightX + 67, centerY - 15, 10, false);
        rightRecede.setFill(COLOR_RED_WING);

        // Title
        var title = createText(msg("phase1.title"), DIAGRAM_WIDTH / 2, 25, 14, true);
        title.setFill(COLOR_HIGHLIGHT);

        // East/West labels
        var eastLabel = createText(msg("phase1.east"), leftX, centerY - 75, 10, false);
        eastLabel.setFill(COLOR_TEXT);
        var westLabel = createText(msg("phase1.west"), rightX, centerY - 75, 10, false);
        westLabel.setFill(COLOR_TEXT);

        group.getChildren().addAll(
                leftDisk, leftLabel, leftArrow, leftApproach, eastLabel,
                rightDisk, rightLabel, rightArrow, rightRecede, westLabel,
                title
        );

        return group;
    }

    private Group createPhase2() {
        var group = new Group();
        double centerY = DIAGRAM_HEIGHT / 2;
        double centerX = DIAGRAM_WIDTH / 2;

        // Title
        var title = createText(msg("phase2.title"), centerX, 25, 14, true);
        title.setFill(COLOR_HIGHLIGHT);

        // RGB combination formula
        var formula = createText(msg("phase2.formula"), centerX, centerY - 60, 11, false);

        // Three channel boxes
        double boxY = centerY - 20;
        double boxSpacing = 100;

        // R channel (from red wing)
        var rBox = createChannelBox(centerX - boxSpacing, boxY, "R", COLOR_RED_WING);
        var rLabel = createText(msg("phase2.red.channel"), centerX - boxSpacing, boxY + 45, 9, false);

        // G channel (minimum)
        var gBox = createChannelBox(centerX, boxY, "G", Color.rgb(100, 180, 100));
        var gLabel = createText(msg("phase2.green.channel"), centerX, boxY + 45, 9, false);

        // B channel (from blue wing)
        var bBox = createChannelBox(centerX + boxSpacing, boxY, "B", COLOR_BLUE_WING);
        var bLabel = createText(msg("phase2.blue.channel"), centerX + boxSpacing, boxY + 45, 9, false);

        // Arrow pointing to result
        var arrow = createArrow(centerX, boxY + 70, centerX, boxY + 95, COLOR_TEXT);

        // Result preview - small gradient disk
        var resultDisk = createDopplerDisk(centerX, centerY + 80, 35);
        var resultLabel = createText(msg("phase2.result"), centerX, centerY + 130, 10, false);

        group.getChildren().addAll(
                title, formula,
                rBox, rLabel,
                gBox, gLabel,
                bBox, bLabel,
                arrow, resultDisk, resultLabel
        );

        return group;
    }

    private Group createPhase3() {
        var group = new Group();
        double centerY = DIAGRAM_HEIGHT / 2 - 10;
        double centerX = DIAGRAM_WIDTH / 2;

        // Title
        var title = createText(msg("phase3.title"), centerX, 25, 14, true);
        title.setFill(COLOR_HIGHLIGHT);

        // Doppler result disk
        var disk = createDopplerDisk(centerX, centerY, 55);

        // Labels for interpretation
        var blueLabel = createText(msg("phase3.blue.meaning"), centerX - 110, centerY, 11, false);
        blueLabel.setFill(COLOR_BLUE_WING);
        var redLabel = createText(msg("phase3.red.meaning"), centerX + 110, centerY, 11, false);
        redLabel.setFill(COLOR_RED_WING);

        // Notes about DEC vs RA scans
        var note1 = createText(msg("phase3.note1"), centerX, centerY + 80, 10, false);
        note1.setFill(Color.rgb(170, 170, 170));
        var note2 = createText(msg("phase3.note2"), centerX, centerY + 95, 10, false);
        note2.setFill(Color.rgb(170, 170, 170));

        // Examples
        var examples = createText(msg("phase3.examples"), centerX, centerY + 115, 10, false);
        examples.setFill(COLOR_HIGHLIGHT);

        group.getChildren().addAll(
                title, disk,
                blueLabel, redLabel,
                note1, note2, examples
        );

        return group;
    }

    private Node createSunDisk(double x, double y, double radius, Color color) {
        var gradient = new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, color.brighter()),
                new Stop(0.7, color),
                new Stop(1.0, color.darker())
        );
        var circle = new Circle(x, y, radius);
        circle.setFill(gradient);
        return circle;
    }

    private Node createDopplerDisk(double x, double y, double radius) {
        // Simple horizontal gradient from blue (left/East) to red (right/West)
        var gradient = new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, COLOR_BLUE_WING),
                new Stop(0.35, COLOR_BLUE_WING.darker()),
                new Stop(0.5, Color.gray(0.4)),
                new Stop(0.65, COLOR_RED_WING.darker()),
                new Stop(1, COLOR_RED_WING)
        );
        var disk = new Circle(x, y, radius);
        disk.setFill(gradient);
        return disk;
    }

    private Group createChannelBox(double x, double y, String label, Color color) {
        var group = new Group();

        var box = new Rectangle(x - 25, y - 20, 50, 40);
        box.setFill(color.deriveColor(0, 1, 1, 0.3));
        box.setStroke(color);
        box.setStrokeWidth(2);
        box.setArcWidth(8);
        box.setArcHeight(8);

        var text = new Text(label);
        text.setFont(Font.font("System", FontWeight.BOLD, 18));
        text.setFill(color);
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y + 6);

        group.getChildren().addAll(box, text);
        return group;
    }

    private Group createArrow(double startX, double startY, double endX, double endY, Color color) {
        var group = new Group();

        var line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(2);

        double angle = Math.atan2(endY - startY, endX - startX);
        double arrowSize = 8;

        var arrowHead = new Polygon(
                endX, endY,
                endX - arrowSize * Math.cos(angle - Math.PI / 6), endY - arrowSize * Math.sin(angle - Math.PI / 6),
                endX - arrowSize * Math.cos(angle + Math.PI / 6), endY - arrowSize * Math.sin(angle + Math.PI / 6)
        );
        arrowHead.setFill(color);

        group.getChildren().addAll(line, arrowHead);
        return group;
    }

    private Text createText(String content, double x, double y, double size, boolean bold) {
        var text = new Text(content);
        text.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        text.setFill(COLOR_TEXT);
        text.setTextAlignment(TextAlignment.CENTER);
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y);
        return text;
    }

    private SequentialTransition createAnimation(Group phase1, Group phase2, Group phase3) {
        var seq = new SequentialTransition();

        // Phase 1 display
        seq.getChildren().add(new PauseTransition(PHASE_DURATION));

        // Fade to phase 2
        var fade1to2 = createFadeTransition(phase1, phase2);
        seq.getChildren().add(fade1to2);

        // Phase 2 display
        seq.getChildren().add(new PauseTransition(PHASE_DURATION));

        // Fade to phase 3
        var fade2to3 = createFadeTransition(phase2, phase3);
        seq.getChildren().add(fade2to3);

        // Phase 3 display
        seq.getChildren().add(new PauseTransition(PHASE_DURATION));

        // Fade back to phase 1
        var fade3to1 = createFadeTransition(phase3, phase1);
        seq.getChildren().add(fade3to1);

        seq.setCycleCount(Timeline.INDEFINITE);

        return seq;
    }

    private SequentialTransition createFadeTransition(Node from, Node to) {
        var fadeOut = new FadeTransition(FADE_DURATION, from);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        var fadeIn = new FadeTransition(FADE_DURATION, to);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        var seq = new SequentialTransition(fadeOut);
        fadeOut.setOnFinished(e -> fadeIn.play());

        return new SequentialTransition(fadeOut, fadeIn);
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
}
