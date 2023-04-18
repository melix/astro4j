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

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Stores metadata about the image found in
 * the SER file header.
 * @param observer the observer
 * @param instrument the instrument
 * @param telescope the telescope
 * @param localDateTime the local date time
 * @param utcDateTime the UTC timestamp
 */
public record ImageMetadata(
        String observer,
        String instrument,
        String telescope,
        LocalDateTime localDateTime,
        ZonedDateTime utcDateTime
) {
    /**
     * Determines if the SER file has timestamps
     * for individual frames.
     * @return true if timestamps are present for each frame
     */
    public boolean hasTimestamps() {
        return localDateTime != null;
    }
}
