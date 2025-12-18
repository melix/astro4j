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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;

/**
 * Provides animated help content explaining how spectroheliographic reconstruction works.
 * Shows 3 phases:
 * 1. A SER frame showing the spectrum with the curved spectral line
 * 2. Pixel extraction along the polynomial curve
 * 3. Multiple frames stacking to form the solar disk
 */
public class ReconstructionHelpContentProvider implements ImageHelpContentProvider {

    private static final double DIAGRAM_WIDTH = 450;
    private static final double DIAGRAM_HEIGHT = 300;
    private static final Duration PHASE_DURATION = Duration.seconds(5);
    private static final Duration FADE_DURATION = Duration.seconds(0.5);

    // Realistic spectrum colors - light continuum with dark absorption lines
    private static final Color COLOR_CONTINUUM = Color.rgb(180, 180, 180);
    private static final Color COLOR_HALPHA_LINE = Color.rgb(40, 40, 40);
    private static final Color COLOR_EXTRACTED = Color.rgb(255, 80, 60);
    private static final Color COLOR_TEXT = Color.rgb(204, 204, 204);
    private static final Color COLOR_HIGHLIGHT = Color.rgb(255, 204, 102);
    private static final Color COLOR_SUN = Color.rgb(255, 200, 100);

    private static final String I18N_BUNDLE = "image-help-reconstruction";

    private SequentialTransition animation;
    private Timeline reconstructionAnimation;

    public ReconstructionHelpContentProvider() {
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

        // Title
        var title = createText(msg("phase1.title"), DIAGRAM_WIDTH / 2, 25, 14, true);
        title.setFill(COLOR_HIGHLIGHT);

        // SER frame representation - horizontal spectrum (wide and short like real data)
        double frameX = 30;
        double frameY = 70;
        double frameW = 280;
        double frameH = 45;

        // Background (light continuum)
        var spectrumBg = new Rectangle(frameX, frameY, frameW, frameH);
        spectrumBg.setFill(COLOR_CONTINUUM);
        spectrumBg.setStroke(COLOR_TEXT);
        spectrumBg.setStrokeWidth(1);

        // Dark curved H-alpha absorption line (horizontal curve across the frame)
        var curvedLine = new QuadCurve(
                frameX + 5, frameY + frameH * 0.55,
                frameX + frameW / 2, frameY + frameH * 0.45,
                frameX + frameW - 5, frameY + frameH * 0.52
        );
        curvedLine.setFill(null);
        curvedLine.setStroke(COLOR_HALPHA_LINE);
        curvedLine.setStrokeWidth(4);

        // Secondary fainter absorption line below
        var secondaryLine = new QuadCurve(
                frameX + 5, frameY + frameH * 0.75,
                frameX + frameW / 2, frameY + frameH * 0.70,
                frameX + frameW - 5, frameY + frameH * 0.73
        );
        secondaryLine.setFill(null);
        secondaryLine.setStroke(Color.rgb(100, 100, 100));
        secondaryLine.setStrokeWidth(2);

        // Label for spectrum frame
        var frameLabel = createText(msg("phase1.frame"), frameX + frameW / 2, frameY + frameH + 20, 11, false);

        // Wavelength axis label (vertical for horizontal spectrum)
        var wavelengthLabel = createText("λ ↓", frameX + frameW + 20, frameY + frameH / 2, 10, false);
        wavelengthLabel.setFill(COLOR_TEXT);

        // H-alpha label with arrow
        var halphaLabel = createText("Hα", frameX - 25, frameY + frameH * 0.5, 11, true);
        halphaLabel.setFill(COLOR_EXTRACTED);
        var arrowToLine = createArrow(frameX - 15, frameY + frameH * 0.5, frameX + 5, frameY + frameH * 0.5, COLOR_EXTRACTED);

        // Explanation text below
        double textX = DIAGRAM_WIDTH / 2;
        double textY = frameY + frameH + 50;
        var text1 = createText(msg("phase1.text1"), textX, textY, 10, false);
        var text2 = createText(msg("phase1.text2"), textX, textY + 18, 10, false);
        var text3 = createText(msg("phase1.text3"), textX, textY + 36, 10, false);
        text3.setFill(COLOR_EXTRACTED);
        var text4 = createText(msg("phase1.text4"), textX, textY + 60, 10, false);
        var text5 = createText(msg("phase1.text5"), textX, textY + 78, 10, false);

        group.getChildren().addAll(
                title,
                spectrumBg, secondaryLine, curvedLine, frameLabel,
                wavelengthLabel, halphaLabel, arrowToLine,
                text1, text2, text3, text4, text5
        );

        return group;
    }

    private Group createPhase2() {
        var group = new Group();

        // Title
        var title = createText(msg("phase2.title"), DIAGRAM_WIDTH / 2, 25, 14, true);
        title.setFill(COLOR_HIGHLIGHT);

        // Show multiple horizontal frames being averaged (stacked vertically)
        double framesX = 30;
        double framesY = 50;
        double frameW = 180;
        double frameH = 30;

        // Show 3 overlapping frames representing "all frames"
        for (int i = 2; i >= 0; i--) {
            double fy = framesY + i * 8;
            var frame = new Rectangle(framesX, fy, frameW, frameH);
            frame.setFill(COLOR_CONTINUUM);
            frame.setStroke(COLOR_TEXT);
            frame.setOpacity(0.5 + i * 0.15);

            // Dark curved absorption line in each frame
            var miniLine = new QuadCurve(
                    framesX + 5, fy + frameH * 0.55,
                    framesX + frameW / 2, fy + frameH * 0.45,
                    framesX + frameW - 5, fy + frameH * 0.52
            );
            miniLine.setFill(null);
            miniLine.setStroke(COLOR_HALPHA_LINE);
            miniLine.setStrokeWidth(2);

            group.getChildren().addAll(frame, miniLine);
        }

        var framesLabel = createText(msg("phase2.average"), framesX + frameW / 2, framesY + frameH + 35, 9, false);

        // Arrow pointing to averaged result
        var arrow = createArrow(framesX + frameW + 10, framesY + frameH / 2 + 8, framesX + frameW + 40, framesY + frameH / 2 + 8, COLOR_HIGHLIGHT);

        // Averaged frame with clear polynomial
        double avgX = framesX + frameW + 55;
        double avgY = framesY + 8;
        var avgFrame = new Rectangle(avgX, avgY, frameW, frameH);
        avgFrame.setFill(COLOR_CONTINUUM);
        avgFrame.setStroke(COLOR_HIGHLIGHT);
        avgFrame.setStrokeWidth(2);

        // Clear polynomial curve on averaged frame (the detected line)
        var polynomial = new QuadCurve(
                avgX + 5, avgY + frameH * 0.55,
                avgX + frameW / 2, avgY + frameH * 0.45,
                avgX + frameW - 5, avgY + frameH * 0.52
        );
        polynomial.setFill(null);
        polynomial.setStroke(COLOR_HIGHLIGHT);
        polynomial.setStrokeWidth(3);

        var polyLabel = createText(msg("phase2.polynomial"), avgX + frameW / 2, avgY - 12, 10, true);
        polyLabel.setFill(COLOR_HIGHLIGHT);

        // Dashed line showing pixel shift option (slightly offset below)
        var shiftedLine = new QuadCurve(
                avgX + 5, avgY + frameH * 0.70,
                avgX + frameW / 2, avgY + frameH * 0.60,
                avgX + frameW - 5, avgY + frameH * 0.67
        );
        shiftedLine.setFill(null);
        shiftedLine.setStroke(COLOR_EXTRACTED);
        shiftedLine.setStrokeWidth(2);
        shiftedLine.getStrokeDashArray().addAll(4.0, 4.0);

        var shiftLabel = createText(msg("phase2.shift"), avgX + frameW / 2, avgY + frameH + 20, 9, false);
        shiftLabel.setFill(COLOR_EXTRACTED);

        // Explanation text below
        double textX = DIAGRAM_WIDTH / 2;
        double textY = 160;
        var text1 = createText(msg("phase2.text1"), textX, textY, 10, false);
        var text2 = createText(msg("phase2.text2"), textX, textY + 18, 10, false);
        var text3 = createText(msg("phase2.text3"), textX, textY + 36, 10, false);
        var text4 = createText(msg("phase2.text4"), textX, textY + 54, 10, false);

        group.getChildren().addAll(
                title,
                framesLabel, arrow,
                avgFrame, polynomial, polyLabel,
                shiftedLine, shiftLabel,
                text1, text2, text3, text4
        );

        return group;
    }

    private Group createPhase3() {
        var group = new Group();

        // Title
        var title = createText(msg("phase3.title"), DIAGRAM_WIDTH / 2, 25, 14, true);
        title.setFill(COLOR_HIGHLIGHT);

        // Left side: sensor frame showing spectrum
        // The spectrum band widens/narrows HORIZONTALLY as sun drifts across slit
        double frameX = 30;
        double frameY = 45;
        double frameW = 120;  // Width of sensor
        double frameH = 140;  // Height of sensor

        // Sensor background (dark - sky background)
        var sensorBg = new Rectangle(frameX, frameY, frameW, frameH);
        sensorBg.setFill(Color.rgb(30, 30, 30));
        sensorBg.setStroke(COLOR_TEXT);

        // Fixed height for spectrum band (spectral range)
        double bandHeight = 35;
        double bandY = frameY + (frameH - bandHeight) / 2;

        // The spectrum band - horizontal strip that grows/shrinks in WIDTH
        // Centered horizontally, fixed height
        var spectrumBand = new Rectangle(frameX + frameW / 2, bandY, 0, bandHeight);
        spectrumBand.setFill(COLOR_CONTINUUM);

        // Dark H-alpha absorption line (horizontal line within spectrum band)
        var halphaLine = new Rectangle(frameX + frameW / 2, bandY + bandHeight / 2 - 2, 0, 4);
        halphaLine.setFill(COLOR_HALPHA_LINE);

        // Second absorption line (fainter, above H-alpha)
        var secondLine = new Rectangle(frameX + frameW / 2, bandY + 8, 0, 2);
        secondLine.setFill(Color.rgb(80, 80, 80));

        var frameLabel = createText(msg("phase3.frames"), frameX + frameW / 2, frameY + frameH + 18, 9, false);

        // Arrow to sun
        var arrow = createArrow(frameX + frameW + 15, frameY + frameH / 2, frameX + frameW + 40, frameY + frameH / 2, COLOR_HIGHLIGHT);

        // Right side: Sun being reconstructed column by column
        double sunCenterX = 320;
        double sunCenterY = frameY + frameH / 2;
        double sunRadius = 65;

        // Background circle for the sun (dark, unfilled)
        var sunBackground = new Circle(sunCenterX, sunCenterY, sunRadius);
        sunBackground.setFill(Color.rgb(30, 30, 30));
        sunBackground.setStroke(COLOR_TEXT);

        // Clipping circle for the sun reconstruction
        var clipCircle = new Circle(sunCenterX, sunCenterY, sunRadius);

        // Create horizontal rows that will be revealed during animation (top to bottom)
        var sunLinesGroup = new Group();
        int numLines = 70;
        double lineHeight = 2.0 * sunRadius / numLines;
        for (int i = 0; i < numLines; i++) {
            double y = sunCenterY - sunRadius + (i * lineHeight);
            // Calculate width based on circle geometry (chord length)
            double dy = y + lineHeight / 2 - sunCenterY;
            double halfWidth = Math.sqrt(Math.max(0, sunRadius * sunRadius - dy * dy));

            var row = new Rectangle(sunCenterX - halfWidth, y, 2 * halfWidth, lineHeight + 0.5);
            // Vary brightness for realistic sun appearance (limb darkening)
            double distFromCenter = Math.abs(dy) / sunRadius;
            double brightness = 0.9 - 0.3 * distFromCenter * distFromCenter;
            row.setFill(COLOR_SUN.deriveColor(0, 1, brightness, 1));
            row.setOpacity(0); // Start invisible
            sunLinesGroup.getChildren().add(row);
        }
        sunLinesGroup.setClip(clipCircle);

        var sunLabel = createText(msg("phase3.result"), sunCenterX, sunCenterY + sunRadius + 18, 11, true);
        sunLabel.setFill(COLOR_HIGHLIGHT);

        // Explanation text on the right
        double textX = 410;
        var text1 = createText(msg("phase3.text1"), textX, frameY + 30, 10, false);
        var text2 = createText(msg("phase3.text2"), textX, frameY + 48, 10, false);
        var text3 = createText(msg("phase3.text3"), textX, frameY + 66, 10, false);

        // Note at bottom
        var note = createText(msg("phase3.note"), DIAGRAM_WIDTH / 2, DIAGRAM_HEIGHT - 10, 9, false);
        note.setFill(Color.rgb(170, 170, 170));

        group.getChildren().addAll(
                title,
                sensorBg, spectrumBand, halphaLine, secondLine, frameLabel,
                arrow,
                sunBackground, sunLinesGroup, sunLabel,
                text1, text2, text3,
                note
        );

        // Create the column-by-column reconstruction animation
        // Each frame = one column. Spectrum band widens then narrows HORIZONTALLY.
        reconstructionAnimation = new Timeline();
        double animDuration = 4.0; // seconds for full reconstruction
        double maxBandWidth = frameW * 0.9; // Maximum width of spectrum band
        double frameCenterX = frameX + frameW / 2;

        for (int i = 0; i < numLines; i++) {
            double time = (i / (double) numLines) * animDuration;

            // Calculate spectrum band WIDTH for this frame
            // At edges (limb): small width. At center (diameter): maximum width.
            double progress = i / (double) (numLines - 1); // 0 to 1
            double dx = (progress - 0.5) * 2; // -1 to +1
            // Chord length formula: 2 * sqrt(r² - d²), normalized
            double normalizedWidth = Math.sqrt(Math.max(0, 1 - dx * dx));
            double bandWidth = normalizedWidth * maxBandWidth;
            double bandX = frameCenterX - bandWidth / 2; // Center horizontally

            // Reveal each sun row (top to bottom)
            var sunRow = (Rectangle) sunLinesGroup.getChildren().get(i);
            reconstructionAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(time),
                            new KeyValue(sunRow.opacityProperty(), 1.0, Interpolator.DISCRETE))
            );

            // Animate spectrum band WIDTH and X position (centered horizontally)
            reconstructionAnimation.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(time),
                            new KeyValue(spectrumBand.xProperty(), bandX),
                            new KeyValue(spectrumBand.widthProperty(), bandWidth),
                            new KeyValue(halphaLine.xProperty(), bandX),
                            new KeyValue(halphaLine.widthProperty(), bandWidth),
                            new KeyValue(secondLine.xProperty(), bandX),
                            new KeyValue(secondLine.widthProperty(), bandWidth))
            );
        }

        // Hold at end
        reconstructionAnimation.getKeyFrames().add(
                new KeyFrame(Duration.seconds(animDuration + 1.5))
        );

        // Reset animation
        reconstructionAnimation.setOnFinished(e -> {
            // Reset all rows to invisible
            for (var node : sunLinesGroup.getChildren()) {
                ((Rectangle) node).setOpacity(0);
            }
            // Reset spectrum band (centered, zero width)
            spectrumBand.setX(frameCenterX);
            spectrumBand.setWidth(0);
            halphaLine.setX(frameCenterX);
            halphaLine.setWidth(0);
            secondLine.setX(frameCenterX);
            secondLine.setWidth(0);
        });

        return group;
    }

    private Node createSunDisk(double x, double y, double radius) {
        var gradient = new RadialGradient(
                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, COLOR_SUN.brighter()),
                new Stop(0.7, COLOR_SUN),
                new Stop(0.85, COLOR_SUN.darker()),
                new Stop(1.0, COLOR_SUN.darker().darker())
        );
        var circle = new Circle(x, y, radius);
        circle.setFill(gradient);
        return circle;
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

        // Fade to phase 3 and start reconstruction animation
        var fade2to3 = createFadeTransition(phase2, phase3);
        fade2to3.setOnFinished(e -> {
            if (reconstructionAnimation != null) {
                reconstructionAnimation.playFromStart();
            }
        });
        seq.getChildren().add(fade2to3);

        // Phase 3 display (longer to show full reconstruction)
        var phase3Pause = new PauseTransition(Duration.seconds(6));
        seq.getChildren().add(phase3Pause);

        // Fade back to phase 1 and stop reconstruction animation
        var fade3to1 = createFadeTransition(phase3, phase1);
        fade3to1.setOnFinished(e -> {
            if (reconstructionAnimation != null) {
                reconstructionAnimation.stop();
            }
        });
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
        if (reconstructionAnimation != null) {
            reconstructionAnimation.stop();
        }
    }

    private static String msg(String key) {
        return I18N.string(JSolEx.class, I18N_BUNDLE, key);
    }
}
