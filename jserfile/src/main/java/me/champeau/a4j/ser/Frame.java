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
package me.champeau.a4j.ser;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * A single frame data.
 *
 * @param number    the frame number (starting from 0)
 * @param data      the binary data of the frame. The buffer is a read-only view of
 *                  the underlying memory-mapped file region; it is invalidated by
 *                  the next {@link SerFileReader#nextFrame()},
 *                  {@link SerFileReader#seekFrame(int)} or
 *                  {@link SerFileReader#close()} call. Callers that need to retain
 *                  the bytes must copy them explicitly. The buffer is direct
 *                  (not heap-backed): {@link ByteBuffer#array()} is unsupported.
 * @param timestamp the timestamp
 */
public record Frame(int number,
                    ByteBuffer data,
                    Optional<ZonedDateTime> timestamp) {
}
