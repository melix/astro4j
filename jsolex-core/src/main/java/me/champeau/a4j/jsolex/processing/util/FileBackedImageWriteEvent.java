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

/**
 * Per-image disk write triggered by {@code FileBackedImage}. {@code trigger}
 * names the caller path: {@code CONSTRUCTOR} (FBI created under pressure),
 * {@code AUTOFLUSH} (the periodic daemon picked an idle FBI), {@code SOFTREF}
 * (the soft-ref cleaner observed a cleared reference), {@code BATCH} (the
 * mass {@link FileBackedImage#flushImages()} call), or {@code RECONSTRUCT_END}
 * (end-of-reconstruction explicit flush). {@code skipped=true} means the call
 * short-circuited because the image was already on disk.
 */
@Name("me.champeau.a4j.jsolex.processing.util.FileBackedImageWrite")
@Label("FileBackedImage write")
@Category({"jsolex", "Memory"})
public class FileBackedImageWriteEvent extends Event {
    @Label("Trigger")
    public String trigger;

    @Label("Bytes written")
    public long bytes;

    @Label("Skipped")
    public boolean skipped;
}
