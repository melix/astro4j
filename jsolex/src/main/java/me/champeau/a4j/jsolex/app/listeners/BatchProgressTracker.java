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
package me.champeau.a4j.jsolex.app.listeners;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe tracker for batch processing progress.
 * Manages completion status, error tracking, and batch finished state.
 */
final class BatchProgressTracker {

    private final Set<Integer> completed;
    private final Set<Integer> errors;
    private final AtomicBoolean batchFinished;
    private final int totalItems;
    private final ReentrantReadWriteLock dataLock;

    BatchProgressTracker(
            Set<Integer> completed,
            Set<Integer> errors,
            AtomicBoolean batchFinished,
            int totalItems,
            ReentrantReadWriteLock dataLock) {
        this.completed = completed;
        this.errors = errors;
        this.batchFinished = batchFinished;
        this.totalItems = totalItems;
        this.dataLock = dataLock;
    }

    /**
     * Creates a tracker from a batch processing context.
     */
    static BatchProgressTracker fromContext(BatchProcessingContext context) {
        return new BatchProgressTracker(
                context.progress(),
                context.errors(),
                context.batchFinished(),
                context.items().size(),
                context.dataLock()
        );
    }

    /**
     * Marks an item as completed and returns the new completion count.
     */
    int markCompleted(int sequenceNumber) {
        dataLock.writeLock().lock();
        try {
            completed.add(sequenceNumber);
            return completed.size();
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Marks an item as having an error.
     */
    void markError(int sequenceNumber) {
        dataLock.writeLock().lock();
        try {
            errors.add(sequenceNumber);
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Returns the current completion count.
     */
    int getCompletedCount() {
        dataLock.readLock().lock();
        try {
            return completed.size();
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Returns the number of successful completions (completed minus errors).
     */
    int getSuccessCount() {
        dataLock.readLock().lock();
        try {
            return completed.size() - errors.size();
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Checks if all items have been processed (completed or errored).
     */
    boolean isAllProcessed() {
        dataLock.readLock().lock();
        try {
            return completed.size() == totalItems;
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Checks if there are any errors.
     */
    boolean hasErrors() {
        dataLock.readLock().lock();
        try {
            return !errors.isEmpty();
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Atomically attempts to mark the batch as finished.
     * Returns true if this call transitioned the batch to finished state.
     */
    boolean tryMarkBatchFinished() {
        return batchFinished.compareAndSet(false, true);
    }

    /**
     * Returns the total number of items in the batch.
     */
    int getTotalItems() {
        return totalItems;
    }

    /**
     * Calculates the progress as a fraction (0.0 to 1.0).
     */
    double getProgress() {
        return (double) getCompletedCount() / totalItems;
    }
}
