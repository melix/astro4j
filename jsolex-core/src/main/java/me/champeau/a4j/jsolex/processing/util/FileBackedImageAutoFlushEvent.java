/*
 * Copyright 2023-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.util;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * One iteration of the {@code FileBackedImage} auto-flush daemon: System.gc()
 * + sleep + pressure check + (optional) sweep. {@code pressureRatio} captures
 * the value seen after the GC/sleep window; {@code triggered=false} means
 * pressure was below threshold and no sweep ran. Otherwise {@code candidateCount}
 * is the number of in-memory FBIs scanned and {@code flushedCount} is how many
 * met the idle-time threshold and were enqueued for write.
 */
@Name("me.champeau.a4j.jsolex.processing.util.FileBackedImageAutoFlush")
@Label("FileBackedImage auto-flush sweep")
@Category({"jsolex", "Memory"})
@StackTrace(false)
public class FileBackedImageAutoFlushEvent extends Event {
    @Label("Pressure ratio")
    public double pressureRatio;

    @Label("Triggered")
    public boolean triggered;

    @Label("Candidate count")
    public int candidateCount;

    @Label("Flushed count")
    public int flushedCount;
}
