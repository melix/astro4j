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
package me.champeau.a4j.jsolex.app;

import me.champeau.a4j.jsolex.processing.sun.SolexVideoProcessor;

import java.io.File;
import java.time.Duration;

public class JSolEx {
    public static void main(String[] args) {
        long sd = System.nanoTime();
        SolexVideoProcessor processor = new SolexVideoProcessor(
                new File("/media/cchampeau/USB/Capture/12_11_20.ser"),
                new File("/tmp/out")
        );
        processor.process();
        long dur = System.nanoTime() - sd;
        Duration duration = Duration.ofNanos(dur);
        System.out.println("Duration: " + duration.toMillis() + " ms");
    }
}
