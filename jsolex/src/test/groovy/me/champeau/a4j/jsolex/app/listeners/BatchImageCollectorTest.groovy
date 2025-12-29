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
package me.champeau.a4j.jsolex.app.listeners

import me.champeau.a4j.jsolex.app.jfx.CandidateImageDescriptor
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind
import me.champeau.a4j.jsolex.processing.util.ImageWrapper
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import spock.lang.Specification

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

class BatchImageCollectorTest extends Specification {

    def "addFile adds files to correct sequence number"() {
        given:
        def filesByIndex = new ConcurrentHashMap<Integer, List<File>>()
        def collector = new BatchImageCollector(
            new ConcurrentHashMap<String, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>(),
            filesByIndex,
            new ReentrantReadWriteLock()
        )
        def file1 = new File("/tmp/file1.png")
        def file2 = new File("/tmp/file2.png")

        when:
        collector.addFile(1, file1)
        collector.addFile(1, file2)
        collector.addFile(2, new File("/tmp/file3.png"))

        then:
        filesByIndex.get(1).size() == 2
        filesByIndex.get(1).contains(file1)
        filesByIndex.get(1).contains(file2)
        filesByIndex.get(2).size() == 1
    }

    def "addImageDescriptor adds descriptors to correct sequence number"() {
        given:
        def imagesByIndex = new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>()
        def collector = new BatchImageCollector(
            new ConcurrentHashMap<String, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<ImageWrapper>>(),
            imagesByIndex,
            new ConcurrentHashMap<Integer, List<File>>(),
            new ReentrantReadWriteLock()
        )
        def descriptor1 = new CandidateImageDescriptor(GeneratedImageKind.RECONSTRUCTION, "Image 1", Path.of("/tmp/img1.png"), 0.0d)
        def descriptor2 = new CandidateImageDescriptor(GeneratedImageKind.RECONSTRUCTION, "Image 2", Path.of("/tmp/img2.png"), 1.5d)

        when:
        collector.addImageDescriptor(1, descriptor1)
        collector.addImageDescriptor(1, descriptor2)

        then:
        imagesByIndex.get(1).size() == 2
        imagesByIndex.get(1).get(0).title() == "Image 1"
        imagesByIndex.get(1).get(1).title() == "Image 2"
    }

    def "addImageByLabel adds images to correct label"() {
        given:
        def imagesByLabel = new ConcurrentHashMap<String, List<ImageWrapper>>()
        def collector = new BatchImageCollector(
            imagesByLabel,
            new ConcurrentHashMap<Integer, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>(),
            new ConcurrentHashMap<Integer, List<File>>(),
            new ReentrantReadWriteLock()
        )
        def image1 = createTestImage(100, 100)
        def image2 = createTestImage(200, 200)

        when:
        collector.addImageByLabel("continuum", image1)
        collector.addImageByLabel("continuum", image2)
        collector.addImageByLabel("doppler", createTestImage(50, 50))

        then:
        imagesByLabel.get("continuum").size() == 2
        imagesByLabel.get("doppler").size() == 1
    }

    def "addImageByIndex adds images to correct sequence number"() {
        given:
        def imageWrappersByIndex = new ConcurrentHashMap<Integer, List<ImageWrapper>>()
        def collector = new BatchImageCollector(
            new ConcurrentHashMap<String, List<ImageWrapper>>(),
            imageWrappersByIndex,
            new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>(),
            new ConcurrentHashMap<Integer, List<File>>(),
            new ReentrantReadWriteLock()
        )
        def image1 = createTestImage(100, 100)
        def image2 = createTestImage(200, 200)

        when:
        collector.addImageByIndex(5, image1)
        collector.addImageByIndex(5, image2)
        collector.addImageByIndex(10, createTestImage(50, 50))

        then:
        imageWrappersByIndex.get(5).size() == 2
        imageWrappersByIndex.get(10).size() == 1
    }

    def "withReadLock executes operation with read lock"() {
        given:
        def imagesByLabel = new ConcurrentHashMap<String, List<ImageWrapper>>()
        def collector = new BatchImageCollector(
            imagesByLabel,
            new ConcurrentHashMap<Integer, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>(),
            new ConcurrentHashMap<Integer, List<File>>(),
            new ReentrantReadWriteLock()
        )
        collector.addImageByLabel("test", createTestImage(100, 100))

        when:
        def result = collector.withReadLock { collector.getImagesByLabel().size() }

        then:
        result == 1
    }

    def "withWriteLock executes operation with write lock"() {
        given:
        def imagesByLabel = new ConcurrentHashMap<String, List<ImageWrapper>>()
        def collector = new BatchImageCollector(
            imagesByLabel,
            new ConcurrentHashMap<Integer, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>(),
            new ConcurrentHashMap<Integer, List<File>>(),
            new ReentrantReadWriteLock()
        )

        when:
        collector.withWriteLock {
            imagesByLabel.put("manual", [createTestImage(100, 100)])
        }

        then:
        imagesByLabel.get("manual").size() == 1
    }

    def "concurrent writes are thread-safe"() {
        given:
        def filesByIndex = new ConcurrentHashMap<Integer, List<File>>()
        def collector = new BatchImageCollector(
            new ConcurrentHashMap<String, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<ImageWrapper>>(),
            new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>(),
            filesByIndex,
            new ReentrantReadWriteLock()
        )
        def executor = Executors.newFixedThreadPool(10)
        def latch = new CountDownLatch(100)

        when:
        100.times { i ->
            executor.submit {
                try {
                    collector.addFile(i % 10, new File("/tmp/file${i}.png"))
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        then:
        def totalFiles = filesByIndex.values().sum { it.size() } ?: 0
        totalFiles == 100
    }

    def "getters return underlying maps"() {
        given:
        def imagesByLabel = new ConcurrentHashMap<String, List<ImageWrapper>>()
        def imageWrappersByIndex = new ConcurrentHashMap<Integer, List<ImageWrapper>>()
        def imagesByIndex = new ConcurrentHashMap<Integer, List<CandidateImageDescriptor>>()
        def filesByIndex = new ConcurrentHashMap<Integer, List<File>>()
        def collector = new BatchImageCollector(
            imagesByLabel,
            imageWrappersByIndex,
            imagesByIndex,
            filesByIndex,
            new ReentrantReadWriteLock()
        )

        expect:
        collector.getImagesByLabel().is(imagesByLabel)
        collector.getImageWrappersByIndex().is(imageWrappersByIndex)
        collector.getImagesByIndex().is(imagesByIndex)
        collector.getFilesByIndex().is(filesByIndex)
    }

    private static ImageWrapper32 createTestImage(int width, int height) {
        def data = new float[height][width]
        return new ImageWrapper32(width, height, data, [:])
    }
}
