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

import javafx.application.Platform;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Handles progress updates from background threads and throttles UI updates
 * to avoid overwhelming the JavaFX application thread.
 * <p>
 * Supports tracking multiple concurrent progress operations, computing aggregate
 * progress, and rotating through leaf task descriptions for display.
 */
public final class ProgressHandler {
    private static final long UPDATE_INTERVAL_MS = 50;
    private static final long ROTATION_INTERVAL_MS = 2000;

    private final Consumer<ProgressSnapshot> uiUpdater;
    private final Thread updateThread;
    private final AtomicBoolean pendingUpdate = new AtomicBoolean(false);

    private final Map<ProgressOperation, Long> activeRoots = new ConcurrentHashMap<>();
    private final AtomicReference<List<LeafTask>> currentLeaves = new AtomicReference<>(List.of());
    private final AtomicInteger rotationIndex = new AtomicInteger(0);
    private final AtomicLong lastRotationTime = new AtomicLong(0);
    private final AtomicReference<String> completionMessage = new AtomicReference<>("");
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * Snapshot of progress state for UI updates.
     *
     * @param progress -1 for indeterminate, 0-1 for determinate progress
     * @param taskCount number of active tasks
     * @param currentTaskLabel label of the current task being displayed
     */
    public record ProgressSnapshot(
            double progress,
            int taskCount,
            String currentTaskLabel
    ) {
        public static ProgressSnapshot hidden() {
            return new ProgressSnapshot(0, 0, "");
        }
    }

    private record LeafTask(ProgressOperation operation, String taskPath, double progress) {}

    /**
     * Creates a new progress handler.
     *
     * @param uiUpdater the consumer that updates the UI with progress snapshot
     */
    public ProgressHandler(Consumer<ProgressSnapshot> uiUpdater) {
        this.uiUpdater = uiUpdater;
        this.updateThread = new Thread(this::updateLoop, "ProgressHandler");
        this.updateThread.setDaemon(true);
        this.updateThread.start();
    }

    /**
     * Registers a root progress operation for tracking.
     *
     * @param root the root progress operation
     */
    public void registerRoot(ProgressOperation root) {
        completionMessage.set(""); // Clear completion message when new work starts
        activeRoots.put(root, System.currentTimeMillis());
        dirty.set(true);
    }

    /**
     * Unregisters a root progress operation.
     *
     * @param root the root progress operation to remove
     */
    public void unregisterRoot(ProgressOperation root) {
        activeRoots.remove(root);
        dirty.set(true);
    }

    /**
     * Marks that an operation was updated and needs redisplay.
     */
    public void markDirty() {
        dirty.set(true);
    }

    /**
     * Sets a completion message that persists until the next operation starts.
     *
     * @param message the completion message to display
     */
    public void setCompletionMessage(String message) {
        completionMessage.set(message);
        dirty.set(true);
    }

    /**
     * Ensures an operation's root is registered for tracking.
     * Traverses up the parent chain to find the root.
     *
     * @param operation the operation to track
     */
    public void ensureTracked(ProgressOperation operation) {
        if (operation == null) {
            return;
        }
        // Find the root of this operation
        var current = operation;
        while (current.parent() != null) {
            current = current.parent();
        }
        // Register the root if not already tracked
        if (!activeRoots.containsKey(current)) {
            completionMessage.set(""); // Clear completion message when new work starts
            activeRoots.put(current, System.currentTimeMillis());
        }
        dirty.set(true);
    }

    /**
     * Returns a snapshot of all active root operations.
     * Used for building the progress tree tooltip.
     *
     * @return an unmodifiable set of active root operations
     */
    public Set<ProgressOperation> getActiveRoots() {
        return Set.copyOf(activeRoots.keySet());
    }

    /**
     * Hides the progress indicator by clearing all tracked operations and messages.
     */
    public void hide() {
        activeRoots.clear();
        currentLeaves.set(List.of());
        rotationIndex.set(0);
        completionMessage.set("");
        dirty.set(true);
    }

    /**
     * Closes the progress handler and stops the update thread.
     */
    public void close() {
        running.set(false);
        updateThread.interrupt();
    }

    private void updateLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(UPDATE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Check if rotation is needed
            long now = System.currentTimeMillis();
            if (now - lastRotationTime.get() >= ROTATION_INTERVAL_MS) {
                advanceRotation();
                lastRotationTime.set(now);
                dirty.set(true);
            }

            if (dirty.get() && pendingUpdate.compareAndSet(false, true)) {
                var snapshot = computeSnapshot();
                Platform.runLater(() -> {
                    uiUpdater.accept(snapshot);
                    dirty.set(false);
                    pendingUpdate.set(false);
                });
            }
        }
    }

    private void advanceRotation() {
        var leaves = currentLeaves.get();
        if (!leaves.isEmpty()) {
            rotationIndex.updateAndGet(idx -> (idx + 1) % leaves.size());
        }
    }

    private ProgressSnapshot computeSnapshot() {
        // If completion message is set, work is done - show it immediately
        var message = completionMessage.get();
        if (!message.isEmpty()) {
            activeRoots.clear();
            return new ProgressSnapshot(1.0, 0, message);
        }

        long now = System.currentTimeMillis();
        // Remove dead container roots (no children, never updated)
        // Give a grace period (1 second) to allow children to be added asynchronously
        activeRoots.entrySet().removeIf(entry -> {
            var root = entry.getKey();
            long registeredAt = entry.getValue();
            long age = now - registeredAt;
            // Don't remove roots registered less than 1 second ago
            if (age < 1000) {
                return false;
            }
            // Remove container roots with no children and progress 0 (children all GC'd)
            return root.children().isEmpty() && root.progress() == 0;
        });

        // Collect all leaf tasks from active roots
        var allLeaves = new ArrayList<LeafTask>();
        for (var root : activeRoots.keySet()) {
            collectLeafTasks(root, allLeaves);
        }

        // No tasks at all - hide progress
        if (allLeaves.isEmpty()) {
            return ProgressSnapshot.hidden();
        }

        // Filter to incomplete tasks for display rotation
        var incompleteLeaves = allLeaves.stream()
                .filter(leaf -> leaf.progress < 1.0)
                .toList();

        currentLeaves.set(List.copyOf(incompleteLeaves));

        // All tasks completed
        if (incompleteLeaves.isEmpty()) {
            // Clean up completed roots
            activeRoots.clear();
            // All done but no message - hide
            return ProgressSnapshot.hidden();
        }

        // Get current task for display (round-robin)
        int index = rotationIndex.get() % incompleteLeaves.size();
        var currentLeaf = incompleteLeaves.get(index);

        // Use -1 for indeterminate progress (active work in progress)
        return new ProgressSnapshot(
                -1,
                incompleteLeaves.size(),
                currentLeaf.taskPath
        );
    }

    private void collectLeafTasks(ProgressOperation operation, List<LeafTask> leaves) {
        // Skip completed operations and their children (they're stale)
        if (operation.progress() >= 1.0) {
            return;
        }
        var children = operation.children();
        if (children.isEmpty()) {
            // This is a leaf - include it if it has a meaningful task name
            // (operations with empty/null task names are likely dead containers)
            // Also skip root operations (no parent) with no children - they're just containers
            var task = operation.task();
            if (task != null && !task.isEmpty() && operation.parent() != null) {
                leaves.add(new LeafTask(operation, operation.taskPath(), operation.progress()));
            }
        } else {
            // Recurse into children, but track if we found any incomplete ones
            int sizeBeforeRecursion = leaves.size();
            for (var child : children) {
                collectLeafTasks(child, leaves);
            }
            // If all children were completed/skipped, treat this operation as a leaf
            // This handles the case where parent has only completed children
            if (leaves.size() == sizeBeforeRecursion) {
                var task = operation.task();
                if (task != null && !task.isEmpty()) {
                    leaves.add(new LeafTask(operation, operation.taskPath(), operation.progress()));
                }
            }
        }
    }
}
