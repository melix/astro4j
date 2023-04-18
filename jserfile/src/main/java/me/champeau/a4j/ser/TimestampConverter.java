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
import java.util.Optional;

public abstract class TimestampConverter {
    private TimestampConverter() {

    }

    /**
     * Converts the long value timestamp found in a SER file
     * into a local date.
     * @param timestamp the timestamp value as found in a SER file
     * @return its local date time representation
     */
    public static Optional<LocalDateTime> of(long timestamp) {
        if (timestamp <= 0) {
            return Optional.empty();
        }
        LocalDateTime localDateTime = LocalDateTime.of(1, 1, 1, 0, 0, 0, 0);
        // Looping to avoid number overflow
        for (int i = 0; i < 100; i++) {
            localDateTime = localDateTime.plusNanos(timestamp);
        }
        return Optional.of(localDateTime);
    }

}
