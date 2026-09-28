/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.uiapi;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Toggle;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import me.champeau.a4j.jsolex.app.jfx.ProgressHandler;
import me.champeau.a4j.jsolex.server.ui.UiApiException;
import me.champeau.a4j.jsolex.server.ui.UiBounds;
import me.champeau.a4j.jsolex.server.ui.UiInspector;
import me.champeau.a4j.jsolex.server.ui.UiMatch;
import me.champeau.a4j.jsolex.server.ui.UiNode;
import me.champeau.a4j.jsolex.server.ui.UiQuery;
import me.champeau.a4j.jsolex.server.ui.UiState;
import me.champeau.a4j.jsolex.server.ui.UiWindow;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@link UiInspector} backed by the live JavaFX scene graph.
 */
public class JavaFxUiInspector implements UiInspector {
    private static final String WINDOW_ID_KEY = JavaFxUiInspector.class.getName() + ".windowId";
    private static final long FX_TIMEOUT_SECONDS = 10;

    private final Window mainWindow;
    private final Supplier<ProgressHandler.ProgressSnapshot> progress;
    private final AtomicInteger windowCounter = new AtomicInteger();

    /**
     * Creates an inspector.
     *
     * @param mainWindow the main window of the application, listed first
     * @param progress supplies the latest progress state
     */
    public JavaFxUiInspector(Window mainWindow, Supplier<ProgressHandler.ProgressSnapshot> progress) {
        this.mainWindow = mainWindow;
        this.progress = progress;
    }

    @Override
    public List<UiWindow> windows() {
        return onFxThread(() -> showingWindows().stream()
                .map(w -> new UiWindow(windowId(w), w instanceof Stage stage ? stage.getTitle() : null, w.getClass().getSimpleName(), w.isFocused(), windowBounds(w)))
                .toList());
    }

    @Override
    public UiNode tree(String windowId, int maxDepth) {
        return onFxThread(() -> toUiNode(rootOf(findWindow(windowId)), "", 0, maxDepth));
    }

    @Override
    public List<UiMatch> find(UiQuery query) {
        return onFxThread(() -> {
            var windows = query.window() == null ? showingWindows() : List.of(findWindow(query.window()));
            var matches = new ArrayList<UiMatch>();
            for (var window : windows) {
                var scene = window.getScene();
                if (scene != null && scene.getRoot() != null) {
                    collectMatches(windowId(window), scene.getRoot(), "", query, matches);
                }
            }
            return matches;
        });
    }

    @Override
    public byte[] snapshot(String windowId, String nodeRef) {
        Image image = onFxThread(() -> {
            var window = findWindow(windowId);
            if (nodeRef == null || nodeRef.isBlank()) {
                return sceneOf(window).snapshot(null);
            }
            return resolve(rootOf(window), nodeRef).snapshot(new SnapshotParameters(), null);
        });
        try (var out = new ByteArrayOutputStream()) {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UiApiException(500, "Unable to encode snapshot: " + e.getMessage());
        }
    }

    @Override
    public UiState state() {
        var snapshot = progress.get();
        var scale = onFxThread(() -> Screen.getPrimary().getOutputScaleX());
        return new UiState(snapshot.taskCount() > 0, snapshot.taskCount(), snapshot.currentTaskLabel(), snapshot.progress(), scale);
    }

    /**
     * Parses a node reference into child indices.
     *
     * @param ref the reference, child indices separated by slashes, empty for the root
     * @return the child indices
     * @throws UiApiException with status 400 if the reference is malformed
     */
    static List<Integer> parseRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return List.of();
        }
        var indices = new ArrayList<Integer>();
        for (var part : ref.split("/", -1)) {
            try {
                var index = Integer.parseInt(part);
                if (index < 0) {
                    throw invalidRef(ref);
                }
                indices.add(index);
            } catch (NumberFormatException e) {
                throw invalidRef(ref);
            }
        }
        return indices;
    }

    /**
     * Returns the reference of a child node.
     *
     * @param parentRef the reference of the parent
     * @param index the index of the child in its parent
     * @return the child reference
     */
    static String childRef(String parentRef, int index) {
        return parentRef.isEmpty() ? String.valueOf(index) : parentRef + "/" + index;
    }

    /**
     * Tells whether a node matches a query. Null criteria are ignored.
     *
     * @param query the query
     * @param id the node id
     * @param type the simple class name of the node
     * @param text the node text
     * @return true if all non-null criteria match
     */
    static boolean matches(UiQuery query, String id, String type, String text) {
        if (query.id() != null && !query.id().equals(id)) {
            return false;
        }
        if (query.type() != null && !query.type().equals(type)) {
            return false;
        }
        if (query.text() != null) {
            return text != null && text.toLowerCase(Locale.ROOT).contains(query.text().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    /**
     * Normalizes a displayed text: blank texts become null.
     *
     * @param text the text
     * @return the text, or null if blank
     */
    static String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private static UiApiException invalidRef(String ref) {
        return new UiApiException(400, "Invalid node reference: " + ref);
    }

    private static <T> T onFxThread(Supplier<T> action) {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                future.complete(action.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new UiApiException(503, "The JavaFX application thread did not respond within " + FX_TIMEOUT_SECONDS + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UiApiException(503, "Interrupted while waiting for the JavaFX application thread");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new UiApiException(500, String.valueOf(e.getCause()));
        }
    }

    private List<Window> showingWindows() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .sorted(Comparator.comparing(w -> w != mainWindow))
                .toList();
    }

    private String windowId(Window window) {
        var properties = window.getProperties();
        var id = properties.get(WINDOW_ID_KEY);
        if (id == null) {
            id = "w" + windowCounter.incrementAndGet();
            properties.put(WINDOW_ID_KEY, id);
        }
        return (String) id;
    }

    private Window findWindow(String windowId) {
        return showingWindows().stream()
                .filter(w -> windowId(w).equals(windowId))
                .findFirst()
                .orElseThrow(() -> new UiApiException(404, "No such window: " + windowId));
    }

    private static Scene sceneOf(Window window) {
        var scene = window.getScene();
        if (scene == null) {
            throw new UiApiException(404, "Window has no scene");
        }
        return scene;
    }

    private static Parent rootOf(Window window) {
        var root = sceneOf(window).getRoot();
        if (root == null) {
            throw new UiApiException(404, "Window has no root node");
        }
        return root;
    }

    private static Node resolve(Node root, String ref) {
        var node = root;
        for (var index : parseRef(ref)) {
            if (!(node instanceof Parent parent) || index >= parent.getChildrenUnmodifiable().size()) {
                throw new UiApiException(404, "No such node: " + ref);
            }
            node = parent.getChildrenUnmodifiable().get(index);
        }
        return node;
    }

    private static UiNode toUiNode(Node node, String ref, int depth, int maxDepth) {
        var children = new ArrayList<UiNode>();
        if ((maxDepth < 0 || depth < maxDepth) && node instanceof Parent parent) {
            var nodes = parent.getChildrenUnmodifiable();
            for (int i = 0; i < nodes.size(); i++) {
                var child = nodes.get(i);
                if (child.isVisible()) {
                    children.add(toUiNode(child, childRef(ref, i), depth + 1, maxDepth));
                }
            }
        }
        return new UiNode(ref, node.getId(), node.getClass().getSimpleName(), textOf(node), List.copyOf(node.getStyleClass()), node.isDisabled(), node.isFocused(), selectedOf(node), nodeBounds(node), visibleBounds(node), children);
    }

    private static void collectMatches(String windowId, Node node, String ref, UiQuery query, List<UiMatch> matches) {
        if (matches(query, node.getId(), node.getClass().getSimpleName(), textOf(node))) {
            matches.add(new UiMatch(windowId, toUiNode(node, ref, 0, 0)));
        }
        if (node instanceof Parent parent) {
            var nodes = parent.getChildrenUnmodifiable();
            for (int i = 0; i < nodes.size(); i++) {
                var child = nodes.get(i);
                if (child.isVisible()) {
                    collectMatches(windowId, child, childRef(ref, i), query, matches);
                }
            }
        }
    }

    private static String textOf(Node node) {
        return normalizeText(switch (node) {
            case Labeled labeled -> labeled.getText();
            case TextInputControl input -> input.getText();
            case Text text -> text.getText();
            case ComboBoxBase<?> comboBox -> comboBox.getValue() == null ? null : comboBox.getValue().toString();
            case ChoiceBox<?> choiceBox -> choiceBox.getValue() == null ? null : choiceBox.getValue().toString();
            default -> null;
        });
    }

    private static UiBounds nodeBounds(Node node) {
        var bounds = node.localToScreen(node.getBoundsInLocal());
        if (bounds == null) {
            return null;
        }
        return new UiBounds(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
    }

    private static Boolean selectedOf(Node node) {
        return switch (node) {
            case CheckBox checkBox -> checkBox.isSelected();
            case Toggle toggle -> toggle.isSelected();
            default -> null;
        };
    }

    private static UiBounds visibleBounds(Node node) {
        var visible = node.localToScreen(node.getBoundsInLocal());
        if (visible == null) {
            return null;
        }
        for (var ancestor = node.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getClip() != null || ancestor.getStyleClass().contains("viewport") || ancestor instanceof ScrollPane) {
                var clip = ancestor.getClip() != null
                        ? ancestor.localToScreen(ancestor.getClip().getBoundsInParent())
                        : ancestor.localToScreen(ancestor.getLayoutBounds());
                if (clip == null) {
                    continue;
                }
                var minX = Math.max(visible.getMinX(), clip.getMinX());
                var minY = Math.max(visible.getMinY(), clip.getMinY());
                var maxX = Math.min(visible.getMaxX(), clip.getMaxX());
                var maxY = Math.min(visible.getMaxY(), clip.getMaxY());
                if (maxX <= minX || maxY <= minY) {
                    return null;
                }
                visible = new BoundingBox(minX, minY, maxX - minX, maxY - minY);
            }
        }
        return new UiBounds(visible.getMinX(), visible.getMinY(), visible.getWidth(), visible.getHeight());
    }

    private static UiBounds windowBounds(Window window) {
        return new UiBounds(window.getX(), window.getY(), window.getWidth(), window.getHeight());
    }
}
