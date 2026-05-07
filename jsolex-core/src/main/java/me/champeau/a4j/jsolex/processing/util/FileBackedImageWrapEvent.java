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
 * Per-call event for {@link FileBackedImage#wrap(ImageWrapper)}. Captures
 * whether the wrapper was reused from the cache and whether this call
 * triggered an in-line {@code flushImages()} because the heap was under
 * pressure. Stack trace disabled — we don't need the call site, just
 * counts and rates.
 */
@Name("me.champeau.a4j.jsolex.processing.util.FileBackedImageWrap")
@Label("FileBackedImage wrap")
@Category({"jsolex", "Memory"})
@StackTrace(false)
public class FileBackedImageWrapEvent extends Event {
    @Label("Cached")
    public boolean cached;

    @Label("Triggered flush")
    public boolean triggeredFlush;
}
