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
package me.champeau.a4j.jsolex.app.jfx.onboarding;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.jfx.FormattedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.label;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.message;
import static me.champeau.a4j.jsolex.app.jfx.onboarding.OnboardingUi.text;

/**
 * A tour of the main window: each step darkens the window except the zones it describes,
 * and explains them in a callout.
 */
public final class GuidedTour {
    private static final double PADDING = 4;
    private static final double GAP = 16;
    private static final double MARGIN = 8;
    private static final double ARROW = 9;
    private static final double CORNER = 14;
    private static final double BUBBLE_WIDTH = 360;
    private static final double INVITE_WIDTH = 360;
    private static final Color SHADE = Color.rgb(20, 24, 33, 0.72);
    private static final Color RING = Color.web("#007bff");
    private static final Duration TRANSITION = Duration.millis(280);

    /**
     * Where the callout is shown, relative to the first zone of a step.
     */
    public enum Placement {
        LEFT, RIGHT, ABOVE, BELOW
    }

    /**
     * A step of the tour.
     *
     * @param key the key of the texts of the step
     * @param placement where the callout is shown
     * @param targets the zones to highlight, the first one being used to place the callout
     * @param onEnter called when the step is shown
     * @param onLeave called when the step is left
     */
    public record Step(String key, Placement placement, List<Node> targets, Runnable onEnter, Runnable onLeave) {
        /**
         * Creates a step. Missing targets are ignored.
         *
         * @param key the key of the texts of the step
         * @param placement where the callout is shown
         * @param targets the zones to highlight
         * @return the step
         */
        public static Step of(String key, Placement placement, Node... targets) {
            return new Step(key, placement, Stream.of(targets).filter(Objects::nonNull).toList(), () -> {
            }, () -> {
            });
        }

        /**
         * Returns a copy of this step which runs actions when it is shown and left.
         *
         * @param enter called when the step is shown
         * @param leave called when the step is left
         * @return the step
         */
        public Step withActions(Runnable enter, Runnable leave) {
            return new Step(key, placement, targets, enter, leave);
        }
    }

    private final StackPane root;
    private final List<Step> steps;
    private final Pane layer = new Pane();
    private final Group shade = new Group();
    private final Polygon arrow = new Polygon();
    private final VBox bubble = new VBox(8);
    private final Group callout = new Group(arrow, bubble);
    private final DoubleProperty progress = new SimpleDoubleProperty(1);
    private final InvalidationListener resizeListener = obs -> Platform.runLater(this::redraw);
    private final EventHandler<KeyEvent> keyHandler = this::onKeyPressed;
    private List<Bounds> from = List.of();
    private List<Bounds> to = List.of();
    private Timeline animation;
    private int current = -1;

    private GuidedTour(StackPane root, List<Step> steps) {
        this.root = root;
        this.steps = steps;
        layer.setPickOnBounds(true);
        arrow.setFill(Color.WHITE);
        bubble.getStyleClass().add("tour-bubble");
        bubble.setPrefWidth(BUBBLE_WIDTH);
        bubble.setMaxWidth(BUBBLE_WIDTH);
        layer.getChildren().addAll(shade, callout);
        progress.addListener(obs -> drawShade());
    }

    /**
     * Shows a card which invites the user to take the tour. The card does not block the window.
     *
     * @param root the root of the main window
     * @param onAccept called when the user accepts
     */
    public static void offer(StackPane root, Runnable onAccept) {
        var decline = new Button(message("tour.invite.decline"));
        decline.getStyleClass().add("default-button");
        var accept = new Button(message("tour.invite.accept"));
        accept.getStyleClass().add("primary-button");
        var buttons = new HBox(8, decline, accept);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        var card = new VBox(8, label("tour.invite.title", "tour-title"), label("tour.invite.text"), buttons);
        card.getStyleClass().add("tour-invite");
        card.setPrefWidth(INVITE_WIDTH);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(card, new Insets(0, 16, 48, 0));
        decline.setOnAction(e -> root.getChildren().remove(card));
        accept.setOnAction(e -> {
            root.getChildren().remove(card);
            onAccept.run();
        });
        root.getChildren().add(card);
    }

    /**
     * Starts the tour. Steps whose first zone is not visible are skipped.
     *
     * @param root the root of the main window
     * @param steps the steps
     */
    public static void start(StackPane root, List<Step> steps) {
        var tour = new GuidedTour(root, steps);
        tour.open();
    }

    private void open() {
        root.getChildren().add(layer);
        root.applyCss();
        root.layout();
        root.widthProperty().addListener(resizeListener);
        root.heightProperty().addListener(resizeListener);
        root.getScene().addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        if (!move(1)) {
            close();
        }
    }

    private void close() {
        leaveCurrent();
        stopAnimation();
        root.widthProperty().removeListener(resizeListener);
        root.heightProperty().removeListener(resizeListener);
        root.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        root.getChildren().remove(layer);
    }

    private void onKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE -> close();
            case RIGHT -> next();
            case LEFT -> move(-1);
            default -> {
                return;
            }
        }
        event.consume();
    }

    private void next() {
        if (!move(1)) {
            close();
        }
    }

    private boolean move(int direction) {
        for (var index = current + direction; index >= 0 && index < steps.size(); index += direction) {
            if (isAvailable(index)) {
                enter(index);
                return true;
            }
        }
        return false;
    }

    private void enter(int index) {
        leaveCurrent();
        current = index;
        var step = steps.get(index);
        step.onEnter().run();
        root.applyCss();
        root.layout();
        var zones = zonesOf(step);
        if (zones.isEmpty()) {
            close();
            return;
        }
        showStep(step, zones);
    }

    private void leaveCurrent() {
        if (current >= 0) {
            steps.get(current).onLeave().run();
        }
    }

    private List<Bounds> zonesOf(Step step) {
        var zones = new ArrayList<Bounds>();
        for (var target : step.targets()) {
            var zone = zoneOf(target);
            if (zone.isEmpty() && zones.isEmpty()) {
                return List.of();
            }
            zone.ifPresent(zones::add);
        }
        return zones;
    }

    private Optional<Bounds> zoneOf(Node node) {
        if (node.getScene() != root.getScene()) {
            return Optional.empty();
        }
        for (var n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return Optional.empty();
            }
        }
        var bounds = layer.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        var minX = Math.max(MARGIN, bounds.getMinX() - PADDING);
        var minY = Math.max(MARGIN / 2, bounds.getMinY() - PADDING);
        var maxX = Math.min(layer.getWidth() - MARGIN / 2, bounds.getMaxX() + PADDING);
        var maxY = Math.min(layer.getHeight() - MARGIN / 2, bounds.getMaxY() + PADDING);
        if (maxX - minX < 2 * MARGIN || maxY - minY < 2 * MARGIN) {
            return Optional.empty();
        }
        return Optional.of(new BoundingBox(minX, minY, maxX - minX, maxY - minY));
    }

    private void showStep(Step step, List<Bounds> zones) {
        stopAnimation();
        var first = to.isEmpty();
        from = first ? zones : currentZones();
        to = zones;
        fillBubble(step);
        placeCallout(step.placement(), zones.getFirst());
        if (first) {
            progress.set(1);
            drawShade();
        } else {
            progress.set(0);
            animation = new Timeline(new KeyFrame(TRANSITION, new KeyValue(progress, 1, Interpolator.EASE_BOTH)));
            animation.play();
        }
        var fade = new FadeTransition(TRANSITION, callout);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    private void redraw() {
        if (current < 0 || layer.getParent() == null) {
            return;
        }
        root.layout();
        var zones = zonesOf(steps.get(current));
        if (zones.isEmpty()) {
            return;
        }
        stopAnimation();
        from = zones;
        to = zones;
        progress.set(1);
        drawShade();
        placeCallout(steps.get(current).placement(), zones.getFirst());
    }

    private List<Bounds> currentZones() {
        var t = progress.get();
        var zones = new ArrayList<Bounds>();
        for (var i = 0; i < to.size(); i++) {
            zones.add(i < from.size() ? interpolate(from.get(i), to.get(i), t) : to.get(i));
        }
        return zones;
    }

    private static Bounds interpolate(Bounds a, Bounds b, double t) {
        return new BoundingBox(
                a.getMinX() + (b.getMinX() - a.getMinX()) * t,
                a.getMinY() + (b.getMinY() - a.getMinY()) * t,
                a.getWidth() + (b.getWidth() - a.getWidth()) * t,
                a.getHeight() + (b.getHeight() - a.getHeight()) * t);
    }

    private void drawShade() {
        var zones = currentZones();
        Shape mask = new Rectangle(layer.getWidth(), layer.getHeight());
        var rings = new ArrayList<Node>();
        for (var zone : zones) {
            mask = Shape.subtract(mask, roundedRectangle(zone));
            var ring = roundedRectangle(zone);
            ring.setFill(Color.TRANSPARENT);
            ring.setStroke(RING);
            ring.setStrokeWidth(3);
            rings.add(ring);
        }
        mask.setFill(SHADE);
        shade.getChildren().setAll(mask);
        shade.getChildren().addAll(rings);
    }

    private static Rectangle roundedRectangle(Bounds bounds) {
        var rectangle = new Rectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
        rectangle.setArcWidth(CORNER);
        rectangle.setArcHeight(CORNER);
        return rectangle;
    }

    private void fillBubble(Step step) {
        var content = new ArrayList<Node>();
        content.add(label("tour." + step.key() + ".title", "tour-title"));
        for (var paragraph : message("tour." + step.key() + ".text").split("\n\n")) {
            content.add(FormattedText.parse(paragraph, text -> styledText(text, "tour-text"), text -> styledText(text, "tour-text-bold")));
        }
        var count = text(String.format(message("tour.count"), countBefore(current) + 1, countBefore(steps.size())), "tour-count");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var footer = new HBox(8, count, spacer);
        footer.setAlignment(Pos.CENTER_LEFT);
        if (current > firstAvailable()) {
            var previous = new Button(message("tour.previous"));
            previous.getStyleClass().add("default-button");
            previous.setOnAction(e -> move(-1));
            footer.getChildren().add(previous);
        }
        var last = current == lastAvailable();
        var next = new Button(message(last ? "tour.finish" : "tour.next"));
        next.getStyleClass().add("primary-button");
        next.setDefaultButton(true);
        next.setOnAction(e -> next());
        footer.getChildren().add(next);
        content.add(footer);
        if (!last) {
            var quit = new Hyperlink(message("tour.quit"));
            quit.setOnAction(e -> close());
            content.add(quit);
        }
        bubble.getChildren().setAll(content);
    }

    private static Text styledText(String content, String styleClass) {
        var text = new Text(content);
        text.getStyleClass().add(styleClass);
        return text;
    }

    private int countBefore(int index) {
        return (int) IntStream.range(0, index).filter(this::isAvailable).count();
    }

    private int firstAvailable() {
        return IntStream.range(0, steps.size()).filter(this::isAvailable).findFirst().orElse(-1);
    }

    private int lastAvailable() {
        return IntStream.range(0, steps.size()).filter(this::isAvailable).reduce((first, second) -> second).orElse(-1);
    }

    private boolean isAvailable(int index) {
        var targets = steps.get(index).targets();
        return index == current || !targets.isEmpty() && zoneOf(targets.getFirst()).isPresent();
    }

    private void placeCallout(Placement placement, Bounds target) {
        bubble.applyCss();
        var width = BUBBLE_WIDTH;
        var height = bubble.prefHeight(width);
        bubble.resize(width, height);
        var x = switch (placement) {
            case RIGHT -> target.getMaxX() + GAP;
            case LEFT -> target.getMinX() - width - GAP;
            case BELOW -> target.getMinX() + Math.min(target.getWidth() / 2, 260) - 40;
            case ABOVE -> target.getCenterX() - width / 2;
        };
        var y = switch (placement) {
            case RIGHT, LEFT -> target.getCenterY() - height / 2;
            case BELOW -> target.getMaxY() + GAP;
            case ABOVE -> target.getMinY() - height - GAP;
        };
        x = clamp(x, MARGIN, layer.getWidth() - width - MARGIN);
        y = clamp(y, MARGIN, layer.getHeight() - height - MARGIN);
        bubble.relocate(x, y);
        switch (placement) {
            case RIGHT -> pointArrow(x, arrowAlong(target.getCenterY(), y, height), -1, true);
            case LEFT -> pointArrow(x + width, arrowAlong(target.getCenterY(), y, height), 1, true);
            case BELOW -> pointArrow(arrowAlong(target.getCenterX(), x, width), y, -1, false);
            case ABOVE -> pointArrow(arrowAlong(target.getCenterX(), x, width), y + height, 1, false);
        }
    }

    private static double arrowAlong(double center, double start, double length) {
        return clamp(center, start + 2 * ARROW, start + length - 2 * ARROW);
    }

    private void pointArrow(double x, double y, int direction, boolean horizontal) {
        var tip = direction * ARROW;
        if (horizontal) {
            arrow.getPoints().setAll(x, y - ARROW, x + tip, y, x, y + ARROW);
        } else {
            arrow.getPoints().setAll(x - ARROW, y, x, y + tip, x + ARROW, y);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
