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
package me.champeau.a4j.math.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class MoveToDiskStress {
    record Outcome(int moved, long wrongPixels, long failures) {
    }

    private MoveToDiskStress() {
    }

    private static boolean isUnexpected(float value, int y) {
        return value != 0 && value % 1000 != y;
    }

    static Outcome run(int planes, int width, int height, int writers, int generations) throws Exception {
        var images = new ArrayList<ImagePlane>();
        for (int i = 0; i < planes; i++) {
            images.add(ImagePlane.allocate(width, height));
        }
        var failures = new AtomicLong();
        var started = new CountDownLatch(writers);
        var pool = Executors.newFixedThreadPool(writers + 2);
        try {
            for (int w = 0; w < writers; w++) {
                int writer = w;
                pool.submit(() -> {
                    var row = new float[width];
                    started.countDown();
                    try {
                        for (int generation = 1; generation <= generations; generation++) {
                            for (var image : images) {
                                for (int y = writer; y < height; y += writers) {
                                    var value = (float) (generation * 1000 + y);
                                    if ((y & 1) == 0) {
                                        Arrays.fill(row, value);
                                        image.setRow(y, row);
                                    } else {
                                        for (int x = 0; x < width; x++) {
                                            image.set(x, y, value);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                    }
                });
            }
            for (int r = 0; r < 2; r++) {
                pool.submit(() -> {
                    var row = new float[width];
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            for (var image : images) {
                                for (int y = 0; y < height; y += 7) {
                                    image.getRow(y, row);
                                    if (isUnexpected(row[0], y) || isUnexpected(row[width - 1], y) || isUnexpected(image.get(y % width, y), y)) {
                                        failures.incrementAndGet();
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                    }
                });
            }
            started.await();
            int moved = 0;
            for (var image : images) {
                if (image.moveToDisk()) {
                    moved++;
                }
            }
            pool.shutdown();
            Thread.sleep(50);
            pool.shutdownNow();
            if (!pool.awaitTermination(120, TimeUnit.SECONDS)) {
                failures.incrementAndGet();
            }
            long wrong = 0;
            var row = new float[width];
            for (var image : images) {
                for (int y = 0; y < height; y++) {
                    image.getRow(y, row);
                    var expected = (float) (generations * 1000 + y);
                    for (int x = 0; x < width; x++) {
                        if (row[x] != expected) {
                            wrong++;
                        }
                    }
                }
            }
            return new Outcome(moved, wrong, failures.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
