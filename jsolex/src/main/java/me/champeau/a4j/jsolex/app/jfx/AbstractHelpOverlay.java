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
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.processing.util.AnimatedGifWriter;

import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Abstract base class for help overlays with an animated help button.
 * Provides a "?" button that shows an attention animation the first time
 * a user sees it (per viewer, per release).
 */
public abstract class AbstractHelpOverlay extends StackPane {

    private static final String HELP_ICON = "?";
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    protected static final double BUTTON_SIZE = 32;
    protected static final String WINDOW_BUTTON_STYLE = "-fx-background-color: transparent; " +
            "-fx-text-fill: rgba(255, 255, 255, 0.6); " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0;";
    protected static final String WINDOW_BUTTON_HOVER_STYLE = "-fx-background-color: rgba(255, 255, 255, 0.1); " +
            "-fx-background-radius: 14; " +
            "-fx-text-fill: white; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0;";

    private final String viewerId;
    private StackPane helpPopup;
    private Timeline attentionAnimation;
    private Button attentionButton;
    private Circle attentionRipple;

    protected AbstractHelpOverlay(String viewerId) {
        this.viewerId = viewerId;
        setStyle("-fx-background-color: transparent;");
        // Ensure overlay fills its parent container
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // Don't capture mouse events on the transparent overlay itself
        setPickOnBounds(false);

        // Handle scene changes: stop animations when removed, restart when re-added
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopAttentionAnimation();
            } else if (oldScene == null) {
                maybeRestartAttentionAnimation();
            }
        });
    }

    /**
     * Stops the attention animation and resets visual state.
     */
    private void stopAttentionAnimation() {
        if (attentionAnimation != null) {
            attentionAnimation.stop();
            attentionAnimation = null;
        }
        if (attentionButton != null) {
            attentionButton.setScaleX(1.0);
            attentionButton.setScaleY(1.0);
        }
        if (attentionRipple != null) {
            attentionRipple.setOpacity(0);
        }
    }

    /**
     * Restarts the attention animation if it hasn't been seen yet.
     */
    private void maybeRestartAttentionAnimation() {
        if (attentionButton != null && attentionRipple != null
                && attentionAnimation == null
                && !Configuration.getInstance().isHelpAnimationSeen(viewerId)) {
            attentionAnimation = createAttentionAnimation(attentionButton, attentionRipple);
            attentionAnimation.play();
        }
    }

    /**
     * Stops all animations and releases resources.
     * Called automatically when the overlay is removed from the scene.
     * Subclasses should override this to stop their own animations.
     */
    public void dispose() {
        stopAttentionAnimation();
        onPopupHidden();
    }

    /**
     * Creates a standalone button that can be added separately from the overlay.
     * The button triggers the popup when clicked.
     */
    public Node createStandaloneButton() {
        var button = new Button(HELP_ICON);
        button.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setFont(Font.font("System", FontWeight.BOLD, 16));
        button.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.6); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 16; " +
                "-fx-cursor: hand;"
        );
        button.setOnMouseEntered(e ->
                button.setStyle(
                        "-fx-background-color: rgba(0, 0, 0, 0.8); " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 16; " +
                        "-fx-cursor: hand;"
                )
        );
        button.setOnMouseExited(e ->
                button.setStyle(
                        "-fx-background-color: rgba(0, 0, 0, 0.6); " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 16; " +
                        "-fx-cursor: hand;"
                )
        );

        // Create ripple circle for wave effect
        var ripple = new Circle(BUTTON_SIZE / 2);
        ripple.setFill(Color.TRANSPARENT);
        ripple.setStroke(Color.rgb(255, 60, 60));
        ripple.setStrokeWidth(3);
        ripple.setOpacity(0);
        ripple.setMouseTransparent(true);

        this.attentionButton = button;
        this.attentionRipple = ripple;

        // Container to hold button and ripple
        var container = new StackPane(ripple, button);
        container.setMaxSize(BUTTON_SIZE * 2, BUTTON_SIZE * 2);

        // Enable caching for hardware-accelerated animation (avoids expensive repaints)
        button.setCache(true);
        button.setCacheHint(CacheHint.SPEED);
        ripple.setCache(true);
        ripple.setCacheHint(CacheHint.SPEED);

        // Check if animation should be shown
        var config = Configuration.getInstance();
        if (!config.isHelpAnimationSeen(viewerId)) {
            attentionAnimation = createAttentionAnimation(button, ripple);
            attentionAnimation.play();
        }

        button.setOnAction(e -> {
            if (attentionAnimation != null) {
                attentionAnimation.stop();
                attentionAnimation = null;
                button.setScaleX(1.0);
                button.setScaleY(1.0);
                ripple.setOpacity(0);
                config.setHelpAnimationSeen(viewerId);
            }
            setMouseTransparent(false);
            showPopup();
        });

        StackPane.setAlignment(container, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(container, new Insets(15));
        return container;
    }

    private StackPane getOrCreateHelpPopup() {
        if (helpPopup == null) {
            helpPopup = createHelpPopup();
            helpPopup.setVisible(false);
            StackPane.setAlignment(helpPopup, Pos.CENTER);
            getChildren().addFirst(helpPopup);
        }
        return helpPopup;
    }

    /**
     * Creates the help popup content. Subclasses must implement this.
     * @return the popup StackPane
     */
    protected abstract StackPane createHelpPopup();

    /**
     * Called when the popup is shown. Subclasses can override to start animations.
     */
    protected void onPopupShown() {
    }

    /**
     * Called when the popup is hidden. Subclasses can override to stop animations.
     */
    protected void onPopupHidden() {
    }

    private Timeline createAttentionAnimation(Button button, Circle ripple) {
        var animation = new Timeline();

        // Animation: ripple appears first, then button bumps, then pause
        var ripplePeak = 0.15;
        var bumpPeak = 0.25;
        var bumpEnd = 0.5;
        var pauseDuration = 0.5;
        var cycleDuration = bumpEnd + pauseDuration;

        // At end of each cycle, check if another instance marked animation as seen
        Runnable checkIfSeen = () -> {
            if (Configuration.getInstance().isHelpAnimationSeen(viewerId)) {
                animation.stop();
                button.setScaleX(1.0);
                button.setScaleY(1.0);
                ripple.setOpacity(0);
            }
        };

        animation.getKeyFrames().addAll(
                // Start of cycle - everything at rest
                new KeyFrame(Duration.ZERO,
                        new KeyValue(button.scaleXProperty(), 1.0),
                        new KeyValue(button.scaleYProperty(), 1.0),
                        new KeyValue(ripple.opacityProperty(), 0),
                        new KeyValue(ripple.scaleXProperty(), 1.0),
                        new KeyValue(ripple.scaleYProperty(), 1.0)
                ),
                // Ripple appears first
                new KeyFrame(Duration.seconds(ripplePeak),
                        new KeyValue(ripple.opacityProperty(), 0.8, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleXProperty(), 1.3, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleYProperty(), 1.3, Interpolator.EASE_OUT)
                ),
                // Button bump peaks
                new KeyFrame(Duration.seconds(bumpPeak),
                        new KeyValue(button.scaleXProperty(), 1.2, Interpolator.EASE_OUT),
                        new KeyValue(button.scaleYProperty(), 1.2, Interpolator.EASE_OUT),
                        new KeyValue(ripple.opacityProperty(), 0.5),
                        new KeyValue(ripple.scaleXProperty(), 1.8),
                        new KeyValue(ripple.scaleYProperty(), 1.8)
                ),
                // Everything returns to rest
                new KeyFrame(Duration.seconds(bumpEnd),
                        new KeyValue(button.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(button.scaleYProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(ripple.opacityProperty(), 0, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleXProperty(), 2.2, Interpolator.EASE_OUT),
                        new KeyValue(ripple.scaleYProperty(), 2.2, Interpolator.EASE_OUT)
                ),
                // Pause - hold position until cycle ends, then check if should stop
                new KeyFrame(Duration.seconds(cycleDuration),
                        e -> checkIfSeen.run(),
                        new KeyValue(button.scaleXProperty(), 1.0),
                        new KeyValue(button.scaleYProperty(), 1.0),
                        new KeyValue(ripple.opacityProperty(), 0),
                        new KeyValue(ripple.scaleXProperty(), 1.0),
                        new KeyValue(ripple.scaleYProperty(), 1.0)
                )
        );

        animation.setCycleCount(50);

        return animation;
    }

    protected void showPopup() {
        getOrCreateHelpPopup().setVisible(true);
        onPopupShown();
    }

    protected void hidePopup() {
        getOrCreateHelpPopup().setVisible(false);
        setMouseTransparent(true); // Restore mouse transparency so pan/zoom works
        onPopupHidden();
    }

    /**
     * Creates a styled content pane with a close button.
     * @param scrollPane the scroll pane containing the content
     * @param normalWidth the normal width
     * @param normalHeight the normal height
     * @return a configured StackPane with window controls
     */
    protected StackPane createContentPane(ScrollPane scrollPane, double normalWidth, double normalHeight) {
        var closeButton = new Button("\u2715");
        closeButton.setFont(Font.font("System", FontWeight.NORMAL, 14));
        closeButton.setMinSize(28, 28);
        closeButton.setMaxSize(28, 28);
        closeButton.setStyle(WINDOW_BUTTON_STYLE);
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(WINDOW_BUTTON_STYLE));
        closeButton.setOnAction(e -> hidePopup());

        // Spacer to push button to the right
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var buttonBar = new HBox(4, spacer, closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8));

        // Use BorderPane for proper layout: button at top, scrollPane in center
        var borderPane = new BorderPane();
        borderPane.setTop(buttonBar);
        borderPane.setCenter(scrollPane);

        // Wrapper with styling
        var content = new StackPane(borderPane);
        content.setMaxWidth(normalWidth);
        content.setMaxHeight(normalHeight);
        content.setStyle(
                "-fx-background-color: rgba(30, 30, 40, 0.95); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: rgba(255, 255, 255, 0.2); " +
                "-fx-border-radius: 12; " +
                "-fx-border-width: 1;"
        );

        return content;
    }

    /**
     * Creates a styled content pane that sizes to its content with a maximum height.
     * Scrollbar only appears when content exceeds the max height.
     * @param scrollPane the scroll pane containing the content
     * @param maxWidth the maximum width
     * @param maxHeight the maximum height
     * @return a configured StackPane with window controls
     */
    protected StackPane createFlexibleContentPane(ScrollPane scrollPane, double maxWidth, double maxHeight) {
        var closeButton = new Button("\u2715");
        closeButton.setFont(Font.font("System", FontWeight.NORMAL, 14));
        closeButton.setMinSize(28, 28);
        closeButton.setMaxSize(28, 28);
        closeButton.setStyle(WINDOW_BUTTON_STYLE);
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(WINDOW_BUTTON_STYLE));
        closeButton.setOnAction(e -> hidePopup());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var buttonBar = new HBox(4, spacer, closeButton);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8));

        var borderPane = new BorderPane();
        borderPane.setTop(buttonBar);
        borderPane.setCenter(scrollPane);

        var content = new StackPane(borderPane);
        content.setMaxWidth(maxWidth);
        content.setMaxHeight(maxHeight);
        content.setStyle(
                "-fx-background-color: rgba(30, 30, 40, 0.95); " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: rgba(255, 255, 255, 0.2); " +
                "-fx-border-radius: 12; " +
                "-fx-border-width: 1;"
        );

        return content;
    }

    /**
     * Wraps a diagram node in a container with a maximize button.
     * When clicked, opens a fullscreen overlay showing the diagram scaled up.
     * @param diagram the diagram node to wrap
     * @param createDiagramCopy a supplier that creates a fresh copy of the diagram for the maximized view
     * @return a StackPane containing the diagram and maximize button
     */
    protected StackPane createScalableDiagram(Node diagram, Supplier<Node> createDiagramCopy) {
        return createScalableDiagram(diagram, createDiagramCopy, null);
    }

    /**
     * Wraps a diagram node in a container with a maximize button.
     * When clicked, opens a fullscreen overlay showing the diagram scaled up.
     * @param diagram the diagram node to wrap
     * @param createDiagramCopy a supplier that creates a fresh copy of the diagram for the maximized view
     * @param onMaximized optional callback invoked after the maximized view is shown (e.g., to start animations)
     * @return a StackPane containing the diagram and maximize button
     */
    protected StackPane createScalableDiagram(Node diagram, Supplier<Node> createDiagramCopy, Runnable onMaximized) {
        return createScalableDiagram(diagram, createDiagramCopy, onMaximized, null);
    }

    /**
     * Wraps a diagram node in a container with a maximize button.
     * When clicked, opens a fullscreen overlay showing the diagram scaled up.
     * @param diagram the diagram node to wrap
     * @param createDiagramCopy a supplier that creates a fresh copy of the diagram for the maximized view
     * @param onMaximized optional callback invoked after the maximized view is shown (e.g., to start animations)
     * @param onClosed optional callback invoked when the maximized view is closed (e.g., to stop animations)
     * @return a StackPane containing the diagram and maximize button
     */
    protected StackPane createScalableDiagram(Node diagram, Supplier<Node> createDiagramCopy, Runnable onMaximized, Runnable onClosed) {
        var maximizeButton = new Button("\u26F6");
        maximizeButton.setFont(Font.font("System", FontWeight.NORMAL, 12));
        maximizeButton.setMinSize(24, 24);
        maximizeButton.setMaxSize(24, 24);
        maximizeButton.setStyle(WINDOW_BUTTON_STYLE);
        maximizeButton.setOnMouseEntered(e -> maximizeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        maximizeButton.setOnMouseExited(e -> maximizeButton.setStyle(WINDOW_BUTTON_STYLE));

        maximizeButton.setOnAction(e -> showMaximizedDiagram(createDiagramCopy.get(), createDiagramCopy, onMaximized, onClosed));

        var container = new StackPane(diagram, maximizeButton);
        StackPane.setAlignment(maximizeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(maximizeButton, new Insets(4));

        return container;
    }

    private void showMaximizedDiagram(Node diagram, Supplier<Node> diagramSupplier, Runnable onShown, Runnable onClosed) {
        // Find the parent container to add the overlay to
        var parent = getParent();
        if (!(parent instanceof Pane parentPane)) {
            return;
        }

        var closeButton = new Button("\u2715");
        closeButton.setFont(Font.font("System", FontWeight.BOLD, 16));
        closeButton.setMinSize(36, 36);
        closeButton.setMaxSize(36, 36);
        closeButton.setStyle(WINDOW_BUTTON_STYLE);
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(WINDOW_BUTTON_HOVER_STYLE));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(WINDOW_BUTTON_STYLE));

        var diagramContainer = new StackPane(diagram, closeButton);
        StackPane.setAlignment(closeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(closeButton, new Insets(20));

        var overlay = new StackPane(diagramContainer);
        overlay.setStyle("-fx-background-color: rgba(20, 20, 30, 0.98);");

        // Compute scale factor based on available space after layout
        overlay.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (newBounds.getWidth() > 0 && newBounds.getHeight() > 0) {
                var diagramBounds = diagram.getBoundsInLocal();
                double padding = 120;
                var availableWidth = newBounds.getWidth() - padding;
                var availableHeight = newBounds.getHeight() - padding;
                var scaleX = availableWidth / diagramBounds.getWidth();
                var scaleY = availableHeight / diagramBounds.getHeight();
                var scale = Math.min(scaleX, scaleY);
                diagram.setScaleX(scale);
                diagram.setScaleY(scale);
            }
        });

        if (onShown != null) {
            onShown.run();
        }

        // Hide the popup while showing maximized view
        if (helpPopup != null) {
            helpPopup.setVisible(false);
        }

        Runnable closeOverlay = () -> {
            parentPane.getChildren().remove(overlay);
            // Restore popup visibility
            if (helpPopup != null) {
                helpPopup.setVisible(true);
            }
            if (onClosed != null) {
                onClosed.run();
            }
        };

        closeButton.setOnAction(e -> closeOverlay.run());
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                closeOverlay.run();
            }
        });

        // Undocumented keyboard shortcut: Ctrl+S to export animation as GIF
        var saveShortcut = new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN);
        overlay.setOnKeyPressed(e -> {
            if (saveShortcut.match(e)) {
                e.consume();
                exportDiagramToGif(diagramSupplier, overlay);
            }
        });
        overlay.setFocusTraversable(true);

        // Ensure overlay fills the parent
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(overlay, Pos.CENTER);
        parentPane.getChildren().add(overlay);
        overlay.toFront();
        overlay.requestFocus();
    }

    private void exportDiagramToGif(Supplier<Node> diagramSupplier, Pane progressParent) {
        var fileChooser = new FileChooser();
        fileChooser.setTitle("Export Animation as GIF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GIF files", "*.gif"));
        fileChooser.setInitialFileName("help_animation.gif");

        var window = progressParent.getScene() != null ? progressParent.getScene().getWindow() : null;
        if (window == null) {
            return;
        }

        var file = fileChooser.showSaveDialog(window);
        if (file == null) {
            return;
        }

        // Create a fresh diagram in an off-screen scene for capture
        captureAnimationToGif(diagramSupplier, file, progressParent);
    }

    private void captureAnimationToGif(Supplier<Node> diagramSupplier, File outputFile, Pane progressParent) {
        // Show progress indicator FIRST on the visible overlay
        var fps = 10;
        var durationSeconds = 40;
        var totalFrames = fps * durationSeconds;
        var delayMs = 1000 / fps;

        var progressLabel = new Label("Preparing export...");
        progressLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        progressLabel.setTextFill(Color.WHITE);
        progressLabel.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.8); " +
                "-fx-padding: 10 20 10 20; " +
                "-fx-background-radius: 6;"
        );
        progressParent.getChildren().add(progressLabel);
        StackPane.setAlignment(progressLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(progressLabel, new Insets(0, 0, 50, 0));

        // Create a fresh diagram for capture
        var freshDiagram = diagramSupplier.get();

        // Find the actual diagram pane (first child if it's a VBox container)
        Region diagramToCapture;
        if (freshDiagram instanceof VBox vbox && !vbox.getChildren().isEmpty()
                && vbox.getChildren().getFirst() instanceof Region r) {
            diagramToCapture = r;
        } else if (freshDiagram instanceof Region r) {
            diagramToCapture = r;
        } else {
            progressLabel.setText("Export failed: invalid diagram");
            return;
        }

        var width = (int) diagramToCapture.getPrefWidth();
        var height = (int) diagramToCapture.getPrefHeight();

        // Remove from current parent and place in capture container
        if (diagramToCapture.getParent() instanceof Pane parentPane) {
            parentPane.getChildren().remove(diagramToCapture);
        }

        var capturePane = new StackPane(diagramToCapture);
        capturePane.setStyle("-fx-background-color: #1a1a24;");
        capturePane.setMinSize(width, height);
        capturePane.setMaxSize(width, height);
        capturePane.setPrefSize(width, height);

        // Create off-screen stage positioned outside visible area
        var captureScene = new Scene(capturePane, width, height);
        var captureStage = new Stage();
        captureStage.initStyle(StageStyle.UNDECORATED);
        captureStage.setScene(captureScene);
        captureStage.setX(-2000);
        captureStage.setY(-2000);
        captureStage.show();

        // Force layout
        capturePane.applyCss();
        capturePane.layout();

        var frames = new ArrayList<BufferedImage>();
        var params = new SnapshotParameters();
        params.setFill(Color.rgb(26, 26, 36));

        // Create WritableImage with EXACT dimensions
        var snapshotImage = new WritableImage(width, height);

        var captureTimeline = new Timeline();
        for (var i = 0; i < totalFrames; i++) {
            var frameIndex = i;
            captureTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis((long) i * delayMs),
                    e -> {
                        progressLabel.setText("Capturing frame " + (frameIndex + 1) + "/" + totalFrames);
                        // Snapshot the capture pane with exact dimensions
                        capturePane.snapshot(params, snapshotImage);
                        frames.add(SwingFXUtils.fromFXImage(snapshotImage, null));
                    }
            ));
        }

        captureTimeline.setOnFinished(e -> {
            captureStage.close();
            progressLabel.setText("Writing GIF...");

            new Thread(() -> {
                try {
                    writeGif(frames, outputFile, delayMs);
                    Platform.runLater(() -> {
                        progressLabel.setText("Export complete!");
                        var removeDelay = new Timeline(new KeyFrame(Duration.seconds(2), evt ->
                            progressParent.getChildren().remove(progressLabel)
                        ));
                        removeDelay.play();
                    });
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        progressLabel.setText("Export failed!");
                        var removeDelay = new Timeline(new KeyFrame(Duration.seconds(3), evt ->
                            progressParent.getChildren().remove(progressLabel)
                        ));
                        removeDelay.play();
                    });
                }
            }).start();
        });

        captureTimeline.play();
    }

    private void writeGif(List<BufferedImage> frames, File outputFile, int delayMs) throws IOException {
        if (frames.isEmpty()) {
            return;
        }

        try (var outputStream = new FileImageOutputStream(outputFile);
             var writer = new AnimatedGifWriter(outputStream, BufferedImage.TYPE_INT_RGB, delayMs, true)) {
            for (var frame : frames) {
                // Convert to RGB if needed
                if (frame.getType() != BufferedImage.TYPE_INT_RGB) {
                    var rgbImage = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
                    rgbImage.createGraphics().drawImage(frame, 0, 0, null);
                    writer.writeToSequence(rgbImage);
                } else {
                    writer.writeToSequence(frame);
                }
            }
        }
    }

    /**
     * Wraps the content pane in a semi-transparent overlay that closes on click outside.
     * @param content the content pane to wrap
     * @return the overlay StackPane
     */
    protected StackPane wrapInOverlay(StackPane content) {
        var overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                hidePopup();
            }
        });
        return overlay;
    }

    /**
     * Creates centered diagram text.
     */
    protected Text createDiagramText(String content, double x, double y, double size, boolean bold, Color color) {
        var text = new Text(content);
        text.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        text.setFill(color);
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y);
        return text;
    }

    /**
     * Parses text with **bold** markdown markers into a TextFlow.
     */
    protected TextFlow parseFormattedText(String text) {
        List<Node> nodes = new ArrayList<>();
        text = text.replace("\\n", "\n");
        var matcher = BOLD_PATTERN.matcher(text);
        var lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                nodes.add(createText(text.substring(lastEnd, matcher.start())));
            }
            nodes.add(createBoldText(matcher.group(1)));
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            nodes.add(createText(text.substring(lastEnd)));
        }

        var textFlow = new TextFlow(nodes.toArray(new Node[0]));
        textFlow.setLineSpacing(4);
        return textFlow;
    }

    private Text createText(String content) {
        var text = new Text(content);
        text.setFill(Color.rgb(220, 220, 220));
        text.setFont(Font.font("System", FontWeight.NORMAL, 13));
        return text;
    }

    private Text createBoldText(String content) {
        var text = new Text(content);
        text.setFill(Color.rgb(255, 200, 100));
        text.setFont(Font.font("System", FontWeight.BOLD, 13));
        return text;
    }
}
