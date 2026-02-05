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

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Collection;
import java.util.List;

/**
 * Common controller for managing multi-phase help animations.
 * This class handles the animation lifecycle, phase transitions, and element resets
 * consistently across all help overlays.
 */
public class HelpAnimationController {

    private static final Duration FADE_DURATION = Duration.seconds(0.5);

    private SequentialTransition diagramAnimation;
    private final List<Timeline> phaseAnimations;
    private final List<Runnable> phaseResetters;
    private final List<Pane> phases;
    private final HBox phaseIndicators;
    private final Duration displayDuration;
    private int currentPhase = 0;

    /**
     * Result record for creating a phase, containing the pane, animation, and reset function.
     */
    public record PhaseResult(Pane pane, Timeline animation, Runnable resetElements) {}

    public HelpAnimationController(List<Timeline> phaseAnimations,
                                   List<Runnable> phaseResetters,
                                   List<Pane> phases,
                                   HBox phaseIndicators,
                                   Duration displayDuration) {
        this.phaseAnimations = phaseAnimations;
        this.phaseResetters = phaseResetters;
        this.phases = phases;
        this.phaseIndicators = phaseIndicators;
        this.displayDuration = displayDuration;
    }

    /**
     * Starts the animation from the beginning.
     * Resets all phases before starting.
     */
    public void start() {
        currentPhase = 0;

        // Stop any running animations first
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }
        phaseAnimations.forEach(Timeline::stop);

        // Reset ALL phases before showing anything - this is critical!
        for (var resetter : phaseResetters) {
            resetter.run();
        }

        // Set phase visibility
        if (phases != null) {
            for (var i = 0; i < phases.size(); i++) {
                phases.get(i).setOpacity(i == 0 ? 1.0 : 0.0);
            }
        }

        // Reset indicators
        if (phaseIndicators != null) {
            for (var i = 0; i < phaseIndicators.getChildren().size(); i++) {
                var dot = (Circle) phaseIndicators.getChildren().get(i);
                dot.setFill(i == 0 ? Color.WHITE : Color.gray(0.4));
            }
        }

        // Start first phase animation
        if (!phaseAnimations.isEmpty()) {
            phaseAnimations.getFirst().playFromStart();
        }

        // Start the main animation
        if (diagramAnimation != null) {
            diagramAnimation.playFromStart();
        }
    }

    /**
     * Stops all animations.
     */
    public void stop() {
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }
        phaseAnimations.forEach(Timeline::stop);
    }

    /**
     * Switches directly to a specific phase.
     */
    public void switchToPhase(int targetPhase) {
        if (targetPhase == currentPhase) {
            return;
        }

        var phaseCount = phases.size();

        // Stop current animations
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }
        phaseAnimations.forEach(Timeline::stop);

        // Reset target phase elements BEFORE showing the phase
        phaseResetters.get(targetPhase).run();

        // Hide current phase, show target phase
        phases.get(currentPhase).setOpacity(0.0);
        phases.get(targetPhase).setOpacity(1.0);

        // Update indicators
        for (var j = 0; j < phaseCount; j++) {
            var dot = (Circle) phaseIndicators.getChildren().get(j);
            dot.setFill(j == targetPhase ? Color.WHITE : Color.gray(0.4));
        }

        // Update current phase
        currentPhase = targetPhase;

        // Play the target phase's animation
        phaseAnimations.get(targetPhase).playFromStart();

        // Restart the main animation from the target phase
        restartAnimationFromPhase(targetPhase);
    }

    /**
     * Gets the current phase index.
     */
    public int getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Sets the diagram animation. Called after the animation is created.
     */
    public void setDiagramAnimation(SequentialTransition animation) {
        this.diagramAnimation = animation;
    }

    /**
     * Creates the main sequential animation that cycles through all phases.
     */
    public SequentialTransition createDiagramAnimation() {
        var animation = new SequentialTransition();
        var phaseCount = phases.size();

        for (var i = 0; i < phaseCount; i++) {
            var nextPhase = (i + 1) % phaseCount;

            // Pause on current phase
            var pause = new PauseTransition(displayDuration);

            // Fade out current
            var fadeOut = new FadeTransition(FADE_DURATION, phases.get(i));
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            // Reset next phase elements BEFORE fade in starts
            fadeOut.setOnFinished(e -> phaseResetters.get(nextPhase).run());

            // Fade in next
            var fadeIn = new FadeTransition(FADE_DURATION, phases.get(nextPhase));
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            // When fade completes, update indicators and start next phase animation
            fadeIn.setOnFinished(e -> {
                currentPhase = nextPhase;
                for (var j = 0; j < phaseCount; j++) {
                    var dot = (Circle) phaseIndicators.getChildren().get(j);
                    dot.setFill(j == nextPhase ? Color.WHITE : Color.gray(0.4));
                }
                phaseAnimations.get(nextPhase).playFromStart();
            });

            animation.getChildren().addAll(pause, fadeOut, fadeIn);

            // Add extra pause at end of cycle
            if (i == phaseCount - 1) {
                var endOfCyclePause = new PauseTransition(Duration.seconds(1));
                animation.getChildren().add(endOfCyclePause);
            }
        }

        // Don't use INDEFINITE - manually restart to ensure proper reset
        animation.setCycleCount(1);
        animation.setOnFinished(e -> {
            // Reset phase 0 before restarting
            currentPhase = 0;

            // Reset ALL phase elements before showing phase 0
            for (var resetter : phaseResetters) {
                resetter.run();
            }

            for (var j = 0; j < phaseCount; j++) {
                phases.get(j).setOpacity(j == 0 ? 1.0 : 0.0);
                var dot = (Circle) phaseIndicators.getChildren().get(j);
                dot.setFill(j == 0 ? Color.WHITE : Color.gray(0.4));
            }
            phaseAnimations.getFirst().playFromStart();
            animation.playFromStart();
        });

        this.diagramAnimation = animation;
        return animation;
    }

    private void restartAnimationFromPhase(int startPhase) {
        if (diagramAnimation != null) {
            diagramAnimation.stop();
        }

        var phaseCount = phases.size();
        var newAnimation = new SequentialTransition();

        for (var i = 0; i < phaseCount; i++) {
            var phaseIdx = (startPhase + i) % phaseCount;
            var nextPhase = (phaseIdx + 1) % phaseCount;

            // Pause on current phase
            var pause = new PauseTransition(displayDuration);

            // Fade out current
            var fadeOut = new FadeTransition(FADE_DURATION, phases.get(phaseIdx));
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            // Reset next phase elements BEFORE fade in starts
            fadeOut.setOnFinished(e -> phaseResetters.get(nextPhase).run());

            // Fade in next
            var fadeIn = new FadeTransition(FADE_DURATION, phases.get(nextPhase));
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            // When fade completes, update indicators and start next phase animation
            fadeIn.setOnFinished(e -> {
                currentPhase = nextPhase;
                for (var j = 0; j < phaseCount; j++) {
                    var dot = (Circle) phaseIndicators.getChildren().get(j);
                    dot.setFill(j == nextPhase ? Color.WHITE : Color.gray(0.4));
                }
                phaseAnimations.get(nextPhase).playFromStart();
            });

            newAnimation.getChildren().addAll(pause, fadeOut, fadeIn);

            // Add extra pause at end of cycle
            if (phaseIdx == phaseCount - 1) {
                var endOfCyclePause = new PauseTransition(Duration.seconds(1));
                newAnimation.getChildren().add(endOfCyclePause);
            }
        }

        // Don't use INDEFINITE - manually restart to ensure proper reset
        newAnimation.setCycleCount(1);
        newAnimation.setOnFinished(e -> {
            currentPhase = 0;
            // Reset ALL phase elements before showing phase 0
            for (var resetter : phaseResetters) {
                resetter.run();
            }
            for (var j = 0; j < phaseCount; j++) {
                phases.get(j).setOpacity(j == 0 ? 1.0 : 0.0);
                var dot = (Circle) phaseIndicators.getChildren().get(j);
                dot.setFill(j == 0 ? Color.WHITE : Color.gray(0.4));
            }
            phaseAnimations.getFirst().playFromStart();
            newAnimation.playFromStart();
        });

        diagramAnimation = newAnimation;
        diagramAnimation.play();
    }

    /**
     * Adds a fade-in animation for a single node.
     * This method ensures proper animation reset by:
     * 1. Adding an initial keyframe at 0.01s to set opacity to 0 (prevents flash)
     * 2. Adding a keyframe at startTime to confirm opacity is 0
     * 3. Adding a keyframe at startTime + fadeDuration to fade to 1
     * <p>
     * IMPORTANT: The initial 0.01s keyframe is critical - it ensures the animation
     * takes control of opacity from the very start, preventing any flash if there's
     * a gap between the resetter running and the first visible keyframe.
     *
     * @param animation the timeline to add keyframes to
     * @param node the node to animate
     * @param startTime the time (in seconds) when the fade-in should start (must be > 0)
     * @param fadeDuration the duration (in seconds) of the fade-in effect
     */
    public static void addFadeIn(Timeline animation, Node node, double startTime, double fadeDuration) {
        // Add initial keyframe at 0.01s to ensure opacity is 0 from the very start
        // This prevents any "flash" during the gap between animation start and startTime
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(0.01),
                new KeyValue(node.opacityProperty(), 0)
        ));
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(startTime),
                new KeyValue(node.opacityProperty(), 0)
        ));
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(startTime + fadeDuration),
                new KeyValue(node.opacityProperty(), 1, Interpolator.EASE_OUT)
        ));
    }

    /**
     * Adds a fade-in animation for multiple nodes, all starting at the same time.
     *
     * @param animation the timeline to add keyframes to
     * @param nodes the nodes to animate
     * @param startTime the time (in seconds) when the animation should start (must be > 0)
     * @param fadeDuration the duration (in seconds) of the fade-in effect
     */
    public static void addFadeIn(Timeline animation, Collection<? extends Node> nodes, double startTime, double fadeDuration) {
        for (var node : nodes) {
            addFadeIn(animation, node, startTime, fadeDuration);
        }
    }

    /**
     * Adds a staggered fade-in animation for multiple nodes, each starting after a delay.
     *
     * @param animation the timeline to add keyframes to
     * @param nodes the nodes to animate
     * @param startTime the time (in seconds) when the first node should start animating
     * @param staggerDelay the delay (in seconds) between each node's animation start
     * @param fadeDuration the duration (in seconds) of each fade-in effect
     * @return the time when the last animation completes
     */
    public static double addStaggeredFadeIn(Timeline animation, List<? extends Node> nodes, double startTime, double staggerDelay, double fadeDuration) {
        for (var i = 0; i < nodes.size(); i++) {
            addFadeIn(animation, nodes.get(i), startTime + i * staggerDelay, fadeDuration);
        }
        return startTime + (nodes.size() - 1) * staggerDelay + fadeDuration;
    }

    /**
     * Ensures a node is hidden (opacity 0) at the start of the animation.
     * Use this for elements that don't fade in until later in the animation,
     * to prevent any flash at animation start.
     *
     * @param animation the timeline to add the keyframe to
     * @param node the node to hide
     */
    public static void hideAtStart(Timeline animation, Node node) {
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(0.01),
                new KeyValue(node.opacityProperty(), 0)
        ));
    }

    /**
     * Ensures multiple nodes are hidden (opacity 0) at the start of the animation.
     *
     * @param animation the timeline to add keyframes to
     * @param nodes the nodes to hide
     */
    public static void hideAtStart(Timeline animation, Collection<? extends Node> nodes) {
        for (var node : nodes) {
            hideAtStart(animation, node);
        }
    }

    /**
     * Adds a fade-in animation for a single node to a custom target opacity.
     * This is useful for elements that should fade in to less than full opacity.
     *
     * @param animation the timeline to add keyframes to
     * @param node the node to animate
     * @param startTime the time (in seconds) when the fade-in should start (must be > 0)
     * @param fadeDuration the duration (in seconds) of the fade-in effect
     * @param targetOpacity the target opacity (0.0 to 1.0)
     */
    public static void addFadeInTo(Timeline animation, Node node, double startTime, double fadeDuration, double targetOpacity) {
        // Add initial keyframe at 0.01s to ensure opacity is 0 from the very start
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(0.01),
                new KeyValue(node.opacityProperty(), 0)
        ));
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(startTime),
                new KeyValue(node.opacityProperty(), 0)
        ));
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(startTime + fadeDuration),
                new KeyValue(node.opacityProperty(), targetOpacity, Interpolator.EASE_OUT)
        ));
    }

    /**
     * Adds a fade-out animation for a single node.
     *
     * @param animation the timeline to add keyframes to
     * @param node the node to animate
     * @param startTime the time (in seconds) when the fade-out should start
     * @param fadeDuration the duration (in seconds) of the fade-out effect
     */
    public static void addFadeOut(Timeline animation, Node node, double startTime, double fadeDuration) {
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(startTime),
                new KeyValue(node.opacityProperty(), 1)
        ));
        animation.getKeyFrames().add(new KeyFrame(
                Duration.seconds(startTime + fadeDuration),
                new KeyValue(node.opacityProperty(), 0, Interpolator.EASE_IN)
        ));
    }

    /**
     * Adds a fade-out animation for multiple nodes, all starting at the same time.
     *
     * @param animation the timeline to add keyframes to
     * @param nodes the nodes to animate
     * @param startTime the time (in seconds) when the animation should start
     * @param fadeDuration the duration (in seconds) of the fade-out effect
     */
    public static void addFadeOut(Timeline animation, Collection<? extends Node> nodes, double startTime, double fadeDuration) {
        for (var node : nodes) {
            addFadeOut(animation, node, startTime, fadeDuration);
        }
    }
}
