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
package me.champeau.a4j.jsolex.processing.event;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ProgressOperation {
    private final static boolean ENSURE_VALID_PROGRESS = Boolean.getBoolean("jsolex.debug.progress");
    private final static Cleaner CLEANER = Cleaner.create();

    private final ProgressOperation parent;
    private final Consumer<? super ProgressOperation> onChildrenFinished;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<WeakReference<ProgressOperation>> children = new ArrayList<>();
    private final long createdAt;

    private String task;
    private double progress;

    private ProgressOperation(ProgressOperation parent, String task, Consumer<? super ProgressOperation> onChildrenFinished) {
        this.parent = parent;
        this.task = task;
        this.onChildrenFinished = onChildrenFinished;
        this.createdAt = System.currentTimeMillis();
    }

    public static ProgressOperation root(String task, Consumer<? super ProgressOperation> onChildrenFinished) {
        return new ProgressOperation(null, task, onChildrenFinished);
    }

    public ProgressOperation update(double progress) {
        lock.lock();
        try {
            this.progress = ensureValidProgress(progress);
        } finally {
            lock.unlock();
        }
        return this;
    }

    private static double ensureValidProgress(double progress) {
        if (ENSURE_VALID_PROGRESS && (progress < 0.0 || progress > 1.0)) {
            throw new IllegalArgumentException("Progress must be between 0.0 and 1.0, got: " + progress);
        }
        return Math.clamp(progress, 0.0, 1.0);
    }

    public ProgressOperation update(double progress, String task) {
        lock.lock();
        try {
            this.progress = ensureValidProgress(progress);
            this.task = task;
        } finally {
            lock.unlock();
        }
        return this;
    }

    public String task() {
        return task;
    }

    /**
     * Returns the full task path including all parent tasks, separated by " / ".
     * For example: "Parent task / Child task / Current task"
     * Empty task names are skipped in the path.
     */
    public String taskPath() {
        if (parent == null) {
            return task;
        }
        var parentPath = parent.taskPath();
        var currentTask = task != null ? task : "";
        if (parentPath == null || parentPath.isEmpty()) {
            return currentTask;
        }
        if (currentTask.isEmpty()) {
            return parentPath;
        }
        return parentPath + " / " + currentTask;
    }

    public double progress() {
        return progress;
    }

    public ProgressOperation complete() {
        progress = 1;
        return this;
    }

    public ProgressOperation complete(String details) {
        progress = 1;
        task = details;
        return this;
    }

    public ProgressOperation parent() {
        return parent;
    }

    public ProgressOperation createChild(String task) {
        var child = new ProgressOperation(this, task, onChildrenFinished);
        CLEANER.register(child, this::cleanupChildren);
        lock.lock();
        try {
            children.add(new WeakReference<>(child));
        } finally {
            lock.unlock();
        }
        return child;
    }

    private void cleanupChildren() {
        lock.lock();
        try {
            if (children.removeIf(ref -> ref.get() == null)) {
                onChildrenFinished.accept(this);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean hasNoChild() {
        lock.lock();
        try {
            return children.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public List<ProgressOperation> children() {
        lock.lock();
        try {
            return children.stream()
                    .map(WeakReference::get)
                    .filter(Objects::nonNull)
                    .toList();
        } finally {
            lock.unlock();
        }
    }
}
