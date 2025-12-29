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

import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock

class BatchProgressTrackerTest extends Specification {

    def "markCompleted increments count and returns new value"() {
        given:
        def tracker = createTracker(totalItems: 10)

        when:
        def count1 = tracker.markCompleted(1)
        def count2 = tracker.markCompleted(2)
        def count3 = tracker.markCompleted(3)

        then:
        count1 == 1
        count2 == 2
        count3 == 3
        tracker.getCompletedCount() == 3
    }

    def "markCompleted is idempotent for same sequence number"() {
        given:
        def tracker = createTracker(totalItems: 10)

        when:
        tracker.markCompleted(1)
        tracker.markCompleted(1)
        tracker.markCompleted(1)

        then:
        tracker.getCompletedCount() == 1
    }

    def "markError tracks error sequence numbers"() {
        given:
        def tracker = createTracker(totalItems: 10)

        when:
        tracker.markCompleted(1)
        tracker.markCompleted(2)
        tracker.markError(2)

        then:
        tracker.hasErrors()
        tracker.getSuccessCount() == 1
    }

    def "getSuccessCount returns completed minus errors"() {
        given:
        def tracker = createTracker(totalItems: 10)

        when:
        tracker.markCompleted(1)
        tracker.markCompleted(2)
        tracker.markCompleted(3)
        tracker.markError(2)
        tracker.markError(3)

        then:
        tracker.getCompletedCount() == 3
        tracker.getSuccessCount() == 1
    }

    def "isAllProcessed returns true when all items completed"() {
        given:
        def tracker = createTracker(totalItems: 3)

        expect:
        !tracker.isAllProcessed()

        when:
        tracker.markCompleted(0)
        tracker.markCompleted(1)

        then:
        !tracker.isAllProcessed()

        when:
        tracker.markCompleted(2)

        then:
        tracker.isAllProcessed()
    }

    def "hasErrors returns false when no errors"() {
        given:
        def tracker = createTracker(totalItems: 5)

        when:
        tracker.markCompleted(0)
        tracker.markCompleted(1)

        then:
        !tracker.hasErrors()
    }

    def "tryMarkBatchFinished returns true only once"() {
        given:
        def tracker = createTracker(totalItems: 5)

        expect:
        tracker.tryMarkBatchFinished()
        !tracker.tryMarkBatchFinished()
        !tracker.tryMarkBatchFinished()
    }

    def "getProgress calculates fraction correctly"() {
        given:
        def tracker = createTracker(totalItems: 4)

        expect:
        tracker.getProgress() == 0.0d

        when:
        tracker.markCompleted(0)

        then:
        tracker.getProgress() == 0.25d

        when:
        tracker.markCompleted(1)
        tracker.markCompleted(2)

        then:
        tracker.getProgress() == 0.75d

        when:
        tracker.markCompleted(3)

        then:
        tracker.getProgress() == 1.0d
    }

    def "getTotalItems returns configured total"() {
        given:
        def tracker = createTracker(totalItems: 42)

        expect:
        tracker.getTotalItems() == 42
    }

    def "concurrent operations are thread-safe"() {
        given:
        def tracker = createTracker(totalItems: 100)
        def executor = Executors.newFixedThreadPool(10)
        def latch = new CountDownLatch(100)

        when:
        100.times { i ->
            executor.submit {
                try {
                    tracker.markCompleted(i)
                    if (i % 5 == 0) {
                        tracker.markError(i)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        then:
        tracker.getCompletedCount() == 100
        tracker.isAllProcessed()
        tracker.hasErrors()
        tracker.getSuccessCount() == 80  // 100 - 20 errors (0, 5, 10, ..., 95)
    }

    private BatchProgressTracker createTracker(Map<String, Object> config = [:]) {
        def totalItems = config.totalItems ?: 10
        def completed = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>())
        def errors = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>())
        def batchFinished = new AtomicBoolean(false)
        def dataLock = new ReentrantReadWriteLock()

        return new BatchProgressTracker(completed, errors, batchFinished, totalItems, dataLock)
    }
}
