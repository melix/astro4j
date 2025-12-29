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

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;

import java.util.Collection;

/**
 * Builds a tree-like view of progress operations for display in a tooltip.
 */
public final class ProgressTreeBuilder {

    private ProgressTreeBuilder() {
    }

    private static String message(String key) {
        return I18N.string(JSolEx.class, "messages", key);
    }

    /**
     * Builds a VBox containing a tree view of active (non-completed) operations.
     *
     * @param roots the root operations to display
     * @return a VBox containing the tree view
     */
    public static VBox buildTreeView(Collection<ProgressOperation> roots) {
        var vbox = new VBox(2);
        vbox.getStyleClass().add("progress-tree-tooltip");
        var activeRoots = roots.stream()
                .filter(ProgressTreeBuilder::hasActiveWork)
                .toList();
        if (activeRoots.isEmpty()) {
            var emptyLabel = new Label(message("all.operations.complete"));
            emptyLabel.getStyleClass().add("pending");
            vbox.getChildren().add(emptyLabel);
        } else {
            for (var root : activeRoots) {
                buildNode(root, vbox, 0);
            }
        }
        return vbox;
    }

    /**
     * Checks if an operation or any of its descendants has active (non-completed) work
     * and has a valid task name.
     */
    private static boolean hasActiveWork(ProgressOperation op) {
        var task = op.task();
        var hasValidTask = task != null && !task.isEmpty();
        if (op.progress() < 1.0 && hasValidTask) {
            return true;
        }
        for (var child : op.children()) {
            if (hasActiveWork(child)) {
                return true;
            }
        }
        return false;
    }

    private static void buildNode(ProgressOperation op, VBox parent, int depth) {
        var progress = op.progress();
        var task = op.task();

        // Skip completed operations or operations without a task name
        if (progress >= 1.0 || task == null || task.isEmpty()) {
            // Still process children - they may have valid names
            for (var child : op.children()) {
                buildNode(child, parent, depth);
            }
            return;
        }

        var progressPercent = (int) (progress * 100);
        var indent = "  ".repeat(depth);
        var text = progressPercent > 0
                ? indent + "\u25CF " + task + " (" + progressPercent + "%)"
                : indent + "\u25CF " + task;

        var label = new Label(text);
        if (progressPercent > 0) {
            label.getStyleClass().add("in-progress");
        } else {
            label.getStyleClass().add("pending");
        }
        parent.getChildren().add(label);

        for (var child : op.children()) {
            buildNode(child, parent, depth + 1);
        }
    }
}
