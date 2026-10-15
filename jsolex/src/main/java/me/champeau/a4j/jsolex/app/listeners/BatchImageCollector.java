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

import me.champeau.a4j.jsolex.app.jfx.CandidateImageDescriptor;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Thread-safe collector for batch processing images and files.
 * Provides synchronized access to shared batch collections.
 */
final class BatchImageCollector {

    private final Map<String, List<ImageWrapper>> imagesByLabel;
    private final Map<String, List<Object>> valuesByLabel;
    private final Map<String, List<Integer>> valueIndicesByLabel;
    private final Map<Integer, List<ImageWrapper>> imageWrappersByIndex;
    private final Map<Integer, List<CandidateImageDescriptor>> imagesByIndex;
    private final Map<Integer, List<File>> filesByIndex;
    private final ReentrantReadWriteLock dataLock;

    BatchImageCollector(
            Map<String, List<ImageWrapper>> imagesByLabel,
            Map<String, List<Object>> valuesByLabel,
            Map<String, List<Integer>> valueIndicesByLabel,
            Map<Integer, List<ImageWrapper>> imageWrappersByIndex,
            Map<Integer, List<CandidateImageDescriptor>> imagesByIndex,
            Map<Integer, List<File>> filesByIndex,
            ReentrantReadWriteLock dataLock) {
        this.imagesByLabel = imagesByLabel;
        this.valuesByLabel = valuesByLabel;
        this.valueIndicesByLabel = valueIndicesByLabel;
        this.imageWrappersByIndex = imageWrappersByIndex;
        this.imagesByIndex = imagesByIndex;
        this.filesByIndex = filesByIndex;
        this.dataLock = dataLock;
    }

    /**
     * Creates a collector from a batch processing context.
     */
    static BatchImageCollector fromContext(BatchProcessingContext context) {
        return new BatchImageCollector(
                context.imagesByLabel(),
                context.valuesByLabel(),
                context.valueIndicesByLabel(),
                context.imageWrappersByIndex(),
                context.imagesByIndex(),
                context.filesByIndex(),
                context.dataLock()
        );
    }

    /**
     * Adds a file to the collection for a given sequence number.
     */
    void addFile(int sequenceNumber, File file) {
        dataLock.writeLock().lock();
        try {
            filesByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(file);
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Adds an image descriptor to the collection for a given sequence number.
     */
    void addImageDescriptor(int sequenceNumber, CandidateImageDescriptor descriptor) {
        dataLock.writeLock().lock();
        try {
            imagesByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(descriptor);
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Adds the outputs of the script executed on one file. They are all added under the
     * same lock, so that when files are processed in parallel, the lists of every label
     * remain in the same file order. Images are wrapped via {@link FileBackedImage}
     * so their pixel data can spill to disk under memory pressure — batch-scoped retention
     * across many files would otherwise pin every output image in heap.
     */
    void addScriptResult(int sequenceNumber, Map<String, ImageWrapper> images, Map<String, Object> values) {
        var backed = new LinkedHashMap<String, FileBackedImage>();
        images.forEach((label, image) -> backed.put(label, FileBackedImage.wrap(image)));
        dataLock.writeLock().lock();
        try {
            backed.forEach((label, image) -> {
                imagesByLabel.computeIfAbsent(label, unused -> new ArrayList<>()).add(image);
                imageWrappersByIndex.computeIfAbsent(sequenceNumber, unused -> new ArrayList<>()).add(image);
            });
            values.forEach((label, value) -> {
                valuesByLabel.computeIfAbsent(label, unused -> new ArrayList<>()).add(value);
                valueIndicesByLabel.computeIfAbsent(label, unused -> new ArrayList<>()).add(sequenceNumber);
            });
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Executes a read operation with the read lock held.
     */
    <T> T withReadLock(Supplier<T> operation) {
        dataLock.readLock().lock();
        try {
            return operation.get();
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Executes a write operation with the write lock held.
     */
    void withWriteLock(Runnable operation) {
        dataLock.writeLock().lock();
        try {
            operation.run();
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Returns the images by label map. Caller must hold the appropriate lock.
     */
    Map<String, List<ImageWrapper>> getImagesByLabel() {
        return imagesByLabel;
    }

    /**
     * Returns the values by label map. Caller must hold the appropriate lock.
     */
    Map<String, List<Object>> getValuesByLabel() {
        return valuesByLabel;
    }

    /**
     * Returns the values by label, without the values produced by the files whose sequence
     * number is in {@code discarded}, so that they stay aligned with the images of the same
     * files. Caller must hold the appropriate lock.
     */
    Map<String, List<Object>> valuesByLabelExcluding(Set<Integer> discarded) {
        var result = new LinkedHashMap<String, List<Object>>();
        valuesByLabel.forEach((label, values) -> {
            var indices = valueIndicesByLabel.get(label);
            var kept = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                if (!discarded.contains(indices.get(i))) {
                    kept.add(values.get(i));
                }
            }
            result.put(label, kept);
        });
        return result;
    }

    /**
     * Returns the image wrappers by index map. Caller must hold the appropriate lock.
     */
    Map<Integer, List<ImageWrapper>> getImageWrappersByIndex() {
        return imageWrappersByIndex;
    }

    /**
     * Returns the candidate image descriptors by index map. Caller must hold the appropriate lock.
     */
    Map<Integer, List<CandidateImageDescriptor>> getImagesByIndex() {
        return imagesByIndex;
    }

    /**
     * Returns the files by index map. Caller must hold the appropriate lock.
     */
    Map<Integer, List<File>> getFilesByIndex() {
        return filesByIndex;
    }
}
