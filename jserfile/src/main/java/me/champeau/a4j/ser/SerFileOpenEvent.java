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
package me.champeau.a4j.ser;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Emitted when a {@link SerFileReader} maps a SER file. Pair with
 * {@link SerFileCloseEvent} via {@code readerId} to track reader
 * lifetimes and concurrent mapping count.
 */
@Name("me.champeau.a4j.ser.SerFileOpen")
@Label("SER file open")
@Category({"jsolex", "I/O"})
@StackTrace(true)
public class SerFileOpenEvent extends Event {
    @Label("Path")
    public String path;

    @Label("Reader id")
    public long readerId;

    @Label("Frame count")
    public int frameCount;

    @Label("Mapped bytes")
    public long mappedBytes;
}
